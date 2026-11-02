package iad1tya.echo.music.playback

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.datasource.cache.SimpleCache
import iad1tya.echo.music.utils.exportedFileUriExists
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.parseExportedFileUriMap

/**
 * Row 638 (owner 2026-11-02: «en modo offline me muestra canciones y no puedo reproducir ninguna»).
 *
 * ONE answer to «does this song play with ZERO network?», the same one the stream resolver applies (a complete download, a complete
 * copy in the listen cache, or an exported file), so a list can say it BEFORE the user taps instead of failing after a wait. Rows
 * that do not play offline are drawn dimmed — never hidden (hiding is losing: the song is still in the library and plays again as
 * soon as there is a network).
 *
 * [LocalOfflinePlayable] is `null` while online (nothing is dimmed) and the set of playable song ids while offline.
 */
val LocalOfflinePlayable = staticCompositionLocalOf<Set<String>?> { null }

internal object OfflinePlayable {
    /** Pure. The union of the three sources the resolver serves with no network. */
    fun playableIds(downloadComplete: Set<String>, listenComplete: Set<String>, exported: Set<String>): Set<String> =
        HashSet<String>(downloadComplete.size + listenComplete.size + exported.size).apply {
            addAll(downloadComplete)
            addAll(listenComplete)
            addAll(exported)
        }

    /**
     * Pure. True when a row for [songId] must be drawn as «not available offline»: only while offline ([playable] != null), never
     * for a local file (always playable), and only for a real id (a blank id is not judged).
     */
    fun isDimmed(songId: String, playable: Set<String>?): Boolean =
        playable != null && songId.isNotBlank() && !songId.isLocalMediaId() && songId !in playable

    /**
     * Pure. The song a listen-cache key can serve with no network, or null. Mirrors `fullyCachedListenUri`: a stream key counts only
     * with a numeric itag (that is what the resolver needs to build the replay URI), the companion VIDEO keys are not audio, and a
     * plain 11-character key is the legacy mediaId-keyed entry.
     */
    fun listenKeyOwner(key: String): String? {
        if (key.startsWith(StreamCacheKeys.PREFIX)) {
            val id = StreamCacheKeys.songIdOf(key) ?: return null
            if (id.startsWith("yt-video-")) return null
            if (key.substringAfterLast('-').toIntOrNull() == null) return null
            return id
        }
        return key.takeIf { it.length == 11 && !it.contains('/') && !it.contains(':') }
    }

    /**
     * Reads the three sources. Local metadata reads only — no network — over a few thousand keys at most; callers run it on
     * Dispatchers.IO and only while offline. [exportedRaw] is the persisted `ExportedFileUrisKey` text.
     */
    fun compute(
        downloadCache: SimpleCache,
        playerCache: SimpleCache,
        exportedRaw: String,
        context: android.content.Context,
    ): Set<String> {
        val downloaded = runCatching {
            downloadCache.keys.filterTo(HashSet()) { !isVideoDownloadId(it) && OfflineIntegrity.isFullyOnDisk(downloadCache, it) }
        }.getOrDefault(emptySet())
        val listened = runCatching {
            playerCache.keys.mapNotNullTo(HashSet()) { key ->
                listenKeyOwner(key)?.takeIf { OfflineIntegrity.isFullyOnDisk(playerCache, key) }
            }
        }.getOrDefault(emptySet())
        val exported = runCatching {
            parseExportedFileUriMap(exportedRaw).filter { (_, uri) -> exportedFileUriExists(context, uri) }.keys
        }.getOrDefault(emptySet())
        return playableIds(downloaded, listened, exported)
    }
}
