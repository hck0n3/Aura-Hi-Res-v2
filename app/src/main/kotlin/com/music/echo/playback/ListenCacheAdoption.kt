package iad1tya.echo.music.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.SimpleCache
import timber.log.Timber

/**
 * Fila 505 — «si la canción ya está en caché cuando le doy a descargar sin conexión, que la tome de ahí y aparezca
 * descargada, para que no repita el proceso, no gaste doble almacenamiento ni más datos» (dueño, 2026-10-29).
 *
 * Una canción que se ESCUCHÓ entera vive en la caché de escucha (`playerCache`, clave `yt-stream-<id>-<itag>`); una
 * descarga vive en la caché de descargas (`downloadCache`, clave = el id). Eran dos copias sin relación: el botón de
 * descargar volvía a bajar la canción desde la red aunque cada byte ya estuviera en el móvil. Aquí se COPIA la copia
 * completa de una caché a la otra (en disco, sin red) y se anota su longitud; el descargador de Media3 encuentra
 * entonces «todos los bytes ya están» y marca la descarga como completada sin abrir la red.
 *
 * Reglas (por el historial #28 / #40 / #57: mezclar bytes de dos contenedores bajo la misma clave rompe la reproducción):
 *  · solo se adopta una copia COMPLETA de escucha (longitud conocida y todos los bytes);
 *  · solo si la caché de descargas NO tiene ya ningún tramo de esa canción (nada que mezclar);
 *  · solo si el itag de la copia es el que la base de datos describe (`FormatEntity`), o la base no tiene formato aún;
 *  · se verifica al terminar (los mismos bytes completos que exige la reproducción sin red) y, si algo falla, se borra
 *    lo copiado: se vuelve exactamente a la descarga normal por red. Nunca deja una copia a medias.
 * No se borra la copia de escucha (podría estar sonando ahora mismo): la expulsa el límite de caché cuando toque.
 */
internal object ListenCacheAdoption {
    private const val TAG = "DOWNLOAD"

    /** The itag a listen-cache key ends with (`yt-stream-<id>-251` → 251), or null. */
    fun itagOf(key: String): Int? {
        val songId = StreamCacheKeys.songIdOf(key) ?: return null
        val tail = key.removePrefix(StreamCacheKeys.PREFIX).removePrefix(songId)
        return tail.removePrefix("-").toIntOrNull()
    }

    /**
     * Pure choice among the COMPLETE listen keys of one song: the one whose itag is [dbItag], else (only when the database
     * knows no format yet) the first. A database that names an itag none of the keys has means «the bytes are not the
     * ones the app describes»: null.
     */
    fun pickKey(completeKeys: List<String>, dbItag: Int?): String? {
        if (completeKeys.isEmpty()) return null
        if (dbItag == null) return completeKeys.sorted().first()
        return completeKeys.firstOrNull { itagOf(it) == dbItag }
    }

    /** True when [songId] already has every byte in [downloadCache] (nothing to adopt, the download completes by itself). */
    fun alreadyComplete(downloadCache: SimpleCache, songId: String): Boolean =
        OfflineIntegrity.isFullyOnDisk(downloadCache, songId)

    /**
     * Copies the complete listen copy of [songId] into [downloadCache]. Returns true when the song now has every byte in the
     * download cache (including «it already had»). Blocking disk work: call off the main thread. Never throws.
     */
    fun adopt(downloadCache: SimpleCache, playerCache: SimpleCache, songId: String, dbItag: Int?): Boolean {
        if (songId.isBlank()) return false
        if (alreadyComplete(downloadCache, songId)) return true
        // Anything already under the download key (a partial download) could be of another container: leave it to Media3.
        if (runCatching { downloadCache.getCachedSpans(songId).isNotEmpty() }.getOrDefault(true)) return false
        val complete = StreamCacheKeys.keysOf(playerCache.keys, songId)
            .filter { OfflineIntegrity.isFullyOnDisk(playerCache, it) }
        val listenKey = pickKey(complete, dbItag) ?: return false
        val length = ContentMetadata.getContentLength(playerCache.getContentMetadata(listenKey))
        if (length <= 0) return false
        return try {
            val listenReader = CacheDataSource.Factory()
                .setCache(playerCache)
                .setCacheKeyFactory { listenKey }
                .createDataSource() // no upstream: a byte that is not cached is an error, never a network call
            val writer = CacheDataSource.Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory { listenReader }
                .createDataSource()
            val spec = DataSpec.Builder()
                .setUri(songId.toUri())
                .setKey(songId)
                .setPosition(0)
                .setLength(length)
                .build()
            CacheWriter(writer, spec, ByteArray(COPY_BUFFER), null).cache()
            val mutations = ContentMetadataMutations()
            ContentMetadataMutations.setContentLength(mutations, length)
            downloadCache.applyContentMetadataMutations(songId, mutations)
            val ok = OfflineIntegrity.isFullyOnDisk(downloadCache, songId)
            if (!ok) rollback(downloadCache, songId)
            Timber.tag(TAG).i("LISTEN_ADOPT ok=%b bytes=%d", ok, length)
            ok
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            rollback(downloadCache, songId)
            Timber.tag(TAG).w("LISTEN_ADOPT failed: %s", iad1tya.echo.music.utils.privacySafeSummary(e))
            false
        }
    }

    private fun rollback(downloadCache: SimpleCache, songId: String) {
        runCatching { downloadCache.removeResource(songId) }
    }

    private const val COPY_BUFFER = 128 * 1024
}
