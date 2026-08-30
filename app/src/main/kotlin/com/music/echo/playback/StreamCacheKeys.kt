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

    /** True when [key] is a listen-cache key belonging to [songId] (any cached quality). */
    fun belongsTo(key: String, songId: String): Boolean = songIdOf(key) == songId
}
