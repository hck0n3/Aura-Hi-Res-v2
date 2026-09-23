package iad1tya.echo.music.playback

import java.util.Collections

/**
 * Process-lifetime memory of song ids whose video fallback ladder ([VideoModeCoordinator]) already
 * exhausted every source this session. Consulted by [iad1tya.echo.music.models.MediaMetadata.hasCompatibleVideo]
 * so a "switch to video" control isn't offered again for an id the app already proved incompatible —
 * including for a fresh [com.music.innertube.models.SongItem] search/request result that has no local
 * DB row (and therefore no persisted `videoFormatIncompatible` column) to read from yet.
 *
 * Session-only by design: unlike the DB column (which survives a restart for songs the app already
 * knows locally), this is a cheap synchronous Set check with no disk I/O, mirroring the non-blocking
 * read pattern `PrefsBridge.peek` already uses elsewhere for the same reason (never block a composition
 * or a search-result mapping on disk).
 */
object VideoCompatibility {
    private val incompatibleIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun markIncompatible(id: String) {
        incompatibleIds.add(id)
    }

    fun isKnownIncompatible(id: String): Boolean = id in incompatibleIds
}
