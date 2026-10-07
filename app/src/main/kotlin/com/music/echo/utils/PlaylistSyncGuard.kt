package iad1tya.echo.music.utils

import java.time.Duration
import java.time.LocalDateTime

/**
 * Pure decisions of the playlist down-sync that keep a local edit from being undone (row 343).
 * See [RemotePlaylistEdits] for the removal side.
 */
object PlaylistSyncGuard {

    /**
     * How long after a local edit the down-sync leaves the local copy alone. A removal reaches YouTube in
     * a second or two; this only has to outlive that, plus a slow network.
     */
    const val RECENT_LOCAL_EDIT_SECONDS = 90L

    /**
     * True when the sync must NOT rebuild the local playlist from the remote copy it just fetched:
     * the playlist was edited locally while that copy was downloading, or very recently. An empty local
     * copy is always filled (a freshly imported playlist must never be left empty).
     */
    fun standDown(
        localEmpty: Boolean,
        editedBeforeFetch: LocalDateTime?,
        editedNow: LocalDateTime?,
        now: LocalDateTime,
    ): Boolean {
        if (localEmpty) return false
        if (editedNow != editedBeforeFetch) return true
        if (editedNow == null) return false
        val age = Duration.between(editedNow, now).seconds
        return age in 0 until RECENT_LOCAL_EDIT_SECONDS
    }

    /**
     * Entries whose setVideoId is missing locally but known remotely, as (index, setVideoId). Only valid
     * when local and remote hold the same songs in the same order — the caller checks that.
     */
    fun setVideoIdBackfill(local: List<String?>, remote: List<String?>): List<Pair<Int, String>> =
        local.indices.mapNotNull { i ->
            val remoteValue = remote.getOrNull(i)
            if (local[i] == null && remoteValue != null) i to remoteValue else null
        }
}
