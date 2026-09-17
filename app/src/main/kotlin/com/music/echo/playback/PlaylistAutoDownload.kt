package iad1tya.echo.music.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import iad1tya.echo.music.db.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Descarga automática de una lista entera, presente y futura.
 *
 * 🔴 Punto 3 del dueño (2026-09-17): *"agregar un interruptor (toggle) en el menú de cada lista de
 * reproducción. Si está activo, todo el contenido actual y cualquier canción que se agregue en el
 * futuro a esa lista se descargará automáticamente"*.
 *
 * ## La forma: reconciliar, no escuchar
 * "Cualquier canción que se agregue en el futuro" se puede hacer de dos maneras, y la tentadora es la
 * mala: enganchar un observador a la tabla y reaccionar a cada inserción. Las canciones entran a una
 * lista desde **muchos** sitios — el menú de una canción, el de varias, la importación de Spotify,
 * Tidal y Deezer, la bajada de la sincronización con YouTube, la generación con IA, arrastrar en la
 * pantalla de la lista — y un observador de tabla ve inserciones sueltas sin saber a qué operación
 * pertenecen: encolaría una descarga por fila en medio de una importación de 500 canciones, y cada
 * sitio nuevo que inserte tendría que acordarse de algo.
 *
 * Esto es un **reconciliador**: mira qué debería estar descargado y encola lo que falte. Es idempotente
 * — llamarlo dos veces no encola nada dos veces — así que puede invocarse desde cualquier sitio y en
 * cualquier momento sin coordinación. Los puntos de llamada son los tres momentos en que la respuesta
 * puede haber cambiado: al **encender** el interruptor, al **añadir** canciones a una lista, y al
 * terminar una **sincronización** (que es como entran las canciones que alguien añadió desde otro
 * aparato).
 *
 * ## Lo que NO hace, a propósito
 * No borra nada. Apagar el interruptor deja descargado lo que ya estaba: quitar ficheros que el usuario
 * puede estar usando sin conexión, porque tocó un interruptor que dice "descargar", sería una pérdida
 * de datos silenciosa. Para quitar descargas ya está el botón de cada canción y el del menú.
 */
object PlaylistAutoDownload {

    /**
     * Estados en los que una canción **ya está resuelta** y no hay que volver a encolarla.
     *
     * `FAILED` no está aquí a propósito: una descarga que falló (sin red, disco lleno, 403) SÍ debe
     * reintentarse en la siguiente reconciliación. `STOPPED` tampoco — es el estado de "en pausa",
     * y si el usuario reanuda las descargas tiene que seguir en la cola.
     */
    private val SETTLED = setOf(
        Download.STATE_COMPLETED,
        Download.STATE_DOWNLOADING,
        Download.STATE_QUEUED,
        Download.STATE_RESTARTING,
    )

    /**
     * Qué encolar: lo que está en la lista y no está ya resuelto.
     *
     * Núcleo puro para poder fijarlo por test — el fallo que hay que impedir no es un error, es
     * **encolar de más** (una tormenta de peticiones sobre una lista grande) o **de menos** (canciones
     * que el usuario cree descargadas y no están).
     *
     * @param inPlaylists todos los ids de las listas con descarga automática, con repetidos posibles
     *   (una canción puede estar en dos listas).
     * @param stateById el estado actual de descarga de cada id, tal como lo conoce `DownloadUtil`.
     */
    fun songsToEnqueue(inPlaylists: List<String>, stateById: Map<String, Int>): List<String> =
        inPlaylists
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .filter { stateById[it] !in SETTLED }
            .toList()

    /**
     * Encola lo que falte de TODAS las listas con descarga automática. Idempotente.
     *
     * @param stateById estado de descarga por id (de `DownloadUtil.downloads`), para no reencolar lo
     *   que ya está resuelto. Se pasa en vez de leerse aquí porque este objeto no debe depender de
     *   Hilt: lo llaman una pantalla, un DAO y un worker, y cada uno ya tiene su `DownloadUtil`.
     */
    suspend fun reconcile(
        context: Context,
        database: MusicDatabase,
        stateById: Map<String, Int>,
    ) = withContext(Dispatchers.IO) {
        val playlists = runCatching { database.autoDownloadPlaylists() }.getOrNull().orEmpty()
        if (playlists.isEmpty()) return@withContext
        val ids = playlists.flatMap { playlist ->
            runCatching { database.playlistSongIdsOnce(playlist.id) }.getOrNull().orEmpty()
        }
        val pending = songsToEnqueue(ids, stateById)
        if (pending.isEmpty()) return@withContext
        // Los títulos en UNA consulta y no una por canción: la notificación del sistema los muestra,
        // y un id sin título en la base (una fila recién insertada por la importación) cae al propio
        // id, que es lo que el resto de la app ya hace.
        val titles = runCatching { database.songTitlesFor(pending) }
            .getOrNull().orEmpty()
            .associate { it.id to it.title }
        Timber.tag("DOWNLOAD").i(
            "auto-download: %d listas, %d canciones por encolar",
            playlists.size,
            pending.size,
        )
        pending.forEach { songId ->
            runCatching {
                val request = DownloadRequest.Builder(songId, songId.toUri())
                    .setCustomCacheKey(songId)
                    .setData((titles[songId] ?: songId).toByteArray())
                    .build()
                DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
            }.onFailure { Timber.tag("DOWNLOAD").w(it, "auto-download: no se pudo encolar una canción") }
        }
    }
}
