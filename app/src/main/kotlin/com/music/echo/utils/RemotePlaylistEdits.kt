package iad1tya.echo.music.utils

import com.music.innertube.YouTube
import com.music.innertube.utils.completed
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.PlaylistSongMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime

/**
 * Quitar canciones de una playlist sincronizada con YouTube — EN YOUTUBE también.
 *
 * 🔴 Dueño (2026-10-07, fila 343): *"cuando elimino algo de las listas de reproducción que está
 * sincronizada en YouTube lo elimino y al segundo vuelve a aparecer"*.
 *
 * ## Por qué volvía
 * YouTube solo borra una entrada de una playlist si recibe su `setVideoId` (el id de ESA entrada, no el
 * de la canción). Las dos pantallas de playlist lo buscaban en la tabla `set_video_id`, que nada en la app
 * rellena nunca: la búsqueda devolvía siempre null, la llamada a YouTube no se hacía y nada lo registraba.
 * La canción se borraba solo en el móvil; la siguiente sincronización (que corre al abrir la playlist) veía
 * que YouTube aún la tenía y la volvía a poner. La selección múltiple ni siquiera intentaba avisar a YouTube,
 * y las canciones añadidas desde la app no guardaban su `setVideoId`, así que el menú de canción también se
 * saltaba la llamada.
 *
 * ## La regla
 * Todo borrado pasa por [removeInBackground]: usa el `setVideoId` guardado en la fila
 * ([PlaylistSongMap.setVideoId], que la sincronización sí rellena) y, si falta, lo resuelve con UNA lectura
 * de la playlist en YouTube ([resolveSetVideoIds]). Además marca la playlist como editada
 * (`lastUpdateTime`), que es lo que la sincronización en curso mira para no pisar el borrado
 * ([SyncUtils] — `editedDuringFetch`).
 *
 * Corre en su propio ámbito: cerrar el menú o salir de la pantalla no cancela la llamada a YouTube.
 * El registro solo lleva recuentos — ni ids ni títulos (AGENTS regla 4).
 */
object RemotePlaylistEdits {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Pura: el `setVideoId` de cada entrada a borrar ([toRemove] = pares songId → setVideoId local), en
     * el mismo orden. El local gana; si falta, se toma una entrada de la copia remota ([remote], pares
     * songId → setVideoId en su orden) de la misma canción que no esté ya usada por otra fila — así una
     * canción repetida en la playlist no borra dos veces la misma entrada.
     */
    fun resolveSetVideoIds(
        toRemove: List<Pair<String, String?>>,
        remote: List<Pair<String, String?>>,
    ): List<String?> {
        val used = toRemove.mapNotNull { it.second }.toMutableSet()
        return toRemove.map { (songId, local) ->
            local ?: remote.firstOrNull { (id, svid) -> id == songId && svid != null && svid !in used }
                ?.second
                ?.also { used += it }
        }
    }

    /**
     * Borra [maps] (filas de UNA playlist local) en YouTube si esa playlist está sincronizada, sin
     * bloquear a quien llama. Llama a esto JUNTO al borrado local, no en su lugar.
     */
    fun removeInBackground(database: MusicDatabase, maps: List<PlaylistSongMap>) {
        val localPlaylistId = maps.firstOrNull()?.playlistId ?: return
        scope.launch {
            try {
                val playlist = database.playlist(localPlaylistId).first()?.playlist ?: return@launch
                // Stamp FIRST, before any network: a sync whose fetch is already in flight sees the edit and
                // stands down instead of rebuilding the playlist from a remote copy that still has the song.
                database.update(playlist.copy(lastUpdateTime = LocalDateTime.now()))
                val browseId = playlist.browseId ?: return@launch
                remove(browseId, maps)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("PLAYLIST_REMOTE_remove failed: %s", privacySafeSummary(e))
            }
        }
    }

    private suspend fun remove(browseId: String, maps: List<PlaylistSongMap>) {
        val remote = if (maps.any { it.setVideoId == null }) {
            YouTube.playlist(browseId).completed().getOrNull()?.songs
                ?.map { it.id to it.setVideoId }
                .orEmpty()
        } else {
            emptyList()
        }
        val setVideoIds = resolveSetVideoIds(maps.map { it.songId to it.setVideoId }, remote)
        var unresolved = 0
        var failed = 0
        maps.forEachIndexed { i, map ->
            val setVideoId = setVideoIds[i]
            if (setVideoId == null) {
                unresolved++
                return@forEachIndexed
            }
            YouTube.removeFromPlaylist(browseId, map.songId, setVideoId).onFailure { e ->
                if (e is CancellationException) throw e
                failed++
                Timber.w("PLAYLIST_REMOTE_remove failed: %s", privacySafeSummary(e))
            }
        }
        if (unresolved > 0 || failed > 0) {
            Timber.w(
                "PLAYLIST_REMOTE_remove incomplete: requested=%d unresolved=%d failed=%d",
                maps.size, unresolved, failed,
            )
        } else {
            Timber.i("PLAYLIST_REMOTE_remove ok: count=%d", maps.size)
        }
    }
}
