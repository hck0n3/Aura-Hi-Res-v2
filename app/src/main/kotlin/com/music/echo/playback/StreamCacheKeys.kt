package iad1tya.echo.music.playback

/**
 * The listen-cache's stable key format for YouTube streams: `yt-stream-<videoId>-<itag>`.
 *
 * googlevideo URLs expire and rotate, so keying cached LISTEN bytes by URL would scatter one song
 * across dozens of dead keys. The stable key keeps every quality (itag) of a song under a
 * predictable prefix, so replaying the same song+quality serves the bytes already on disk.
 *
 * Single source of truth for BUILD ([build]) and PARSE ([songIdOf]): the factory in MusicService
 * and the cache-list/purge consumers must agree byte-for-byte or the listen-cache becomes
 * invisible again (the 2026-08-29 audit: keys were built here but every consumer still looked
 * them up by mediaId, so "En caché" showed empty while gigabytes sat on disk).
 */
object StreamCacheKeys {

    const val PREFIX = "yt-stream-"

    fun build(videoId: String, itag: String?): String = buildString {
        append(PREFIX)
        append(videoId)
        if (!itag.isNullOrBlank()) append("-").append(itag)
    }

    /**
     * The song id (videoId) a listen-cache key belongs to, or null for any other key (mediaId keys,
     * URL-keyed non-googlevideo resources). Video ids come from a 64-symbol alphabet that
     * includes '-', so the itag is only stripped when the last dash-segment is numeric —
     * `yt-stream-<id-with-dashes>` with no itag parses to the full remainder.
     */
    fun songIdOf(key: String): String? {
        if (!key.startsWith(PREFIX)) return null
        val rest = key.removePrefix(PREFIX)
        if (rest.isEmpty()) return null
        val lastSegment = rest.substringAfterLast('-', "")
        return if (lastSegment.toIntOrNull() != null) {
            rest.removeSuffix("-$lastSegment").takeIf { it.isNotEmpty() }
        } else {
            rest
        }
    }

    /**
     * True for a listen-cache key written by the OLD scheme that keyed on the rotating `o-XXXX` stream id (`yt-stream-o-…`):
     * unreachable now, safe to purge. A real videoId is exactly 11 characters; the companion VIDEO keys
     * (`yt-stream-yt-video-<id>-<itag>`) are 17 and are NOT orphans — the one-shot purge used to match them too
     * (`songIdOf` is `yt-video-<id>`, not 11 long), so it would have deleted every cached video on a device that ran it.
     */
    fun isLegacyRotatingKey(key: String): Boolean {
        val songId = songIdOf(key) ?: return false
        return songId.length != 11 && !songId.startsWith("yt-video-")
    }

    /** True when [key] is a listen-cache key belonging to [songId] (any cached quality). */
    fun belongsTo(key: String, songId: String): Boolean = songIdOf(key) == songId

    /**
     * ALL listen-cache keys of [songId], as present in [keys] (a live snapshot such as
     * `playerCache.keys`). This is the purge side of the 2026-08-29 stable-key design: the bytes of a
     * streamed song live under `yt-stream-<videoId>-<itag>`, NOT under the mediaId — so every purge
     * that removed `removeResource(mediaId)` left the real bytes orphaned on disk (invisible to the
     * app, never served again, only collectable by the LRU evictor or a full cache wipe). Callers that
     * need to drop a song's listen bytes must drop EVERY key returned here.
     */
    fun keysOf(keys: Set<String>, songId: String): Set<String> =
        if (songId.isBlank()) emptySet() else keys.filterTo(mutableSetOf()) { belongsTo(it, songId) }
}
