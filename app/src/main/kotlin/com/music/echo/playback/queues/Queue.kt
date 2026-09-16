

package iad1tya.echo.music.playback.queues

import androidx.media3.common.MediaItem
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    /**
     * The queue was started by a "Shuffle" button, so playback must ENABLE shuffle mode once the items
     * land — not merely pre-scramble the list. Lives on the interface (not just ListQueue) because the
     * online-playlist screen starts a YouTubePlaylistQueue, and gating on ListQueue alone left those
     * Shuffle buttons bypassing the whole enhanced-shuffle system (frozen scramble, no memory).
     */
    val startShuffled: Boolean get() = false

    /**
     * Persistent Enhanced Shuffle bucket for this queue ("PL:<id>", "AP:liked", "AL:<id>", "AR:<id>"…),
     * or null when the queue has no memory (raw radio, search results).
     *
     * Lives on the INTERFACE, not just on ListQueue: the service used to read it as
     * `(queue as? ListQueue)?.contextId`, so any screen that legitimately started a different queue type
     * silently lost its no-repeat memory, and the only workaround was to convert the screen to a
     * ListQueue — which changes what plays after the queue ends. Declaring it here lets every queue type
     * carry a bucket while keeping its own continuation behaviour.
     */
    val contextId: String? get() = null

    /**
     * Song ids that must count as already heard when this shuffle lap starts (Continue, not Start over).
     * Filled by the screen from persistent shuffle memory ∪ [iad1tya.echo.music.db.entities.SongEntity.totalPlayTime]
     * so songs heard with Aleatorio mejorado OFF still sit behind the unplayed remainder.
     */
    val seedPlayedIds: Set<String> get() = emptySet()

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        /**
         * Re-anchors [mediaItemIndex] after a filter shrinks [items].
         *
         * Both filters used to `copy(items = …)` and KEEP the old index, so removing any item before it
         * shifted the whole queue: playback started on a DIFFERENT song than the one that was requested.
         * The worst case was a single-song play whose seed sat at index 0 — drop the seed and index 0 now
         * points at the radio's NEXT track, i.e. "it plays something unrelated". Note the video filter is
         * ORed with Data Saver in MusicService, so this fired for a setting users don't read as "filter".
         *
         * The anchor is the item the index pointed at. If the anchor itself was filtered out we fall back
         * to 0 (the caller's preloadItem, when present, still pins the right song regardless).
         */
        private fun reanchor(filtered: List<MediaItem>): Status {
            if (filtered.isEmpty()) return copy(items = filtered, mediaItemIndex = 0)
            val anchor = items.getOrNull(mediaItemIndex)
                ?: return copy(items = filtered, mediaItemIndex = 0)

            // Match the same OCCURRENCE, not merely the same id: a queue may legitimately hold the same
            // song twice, and anchoring on the FIRST copy rewinds playback to the earlier one.
            val occurrence = items.take(mediaItemIndex).count { it.mediaId == anchor.mediaId }
            var seen = 0
            filtered.forEachIndexed { i, item ->
                if (item.mediaId == anchor.mediaId) {
                    if (seen == occurrence) {
                        return copy(items = filtered, mediaItemIndex = i)
                    }
                    seen++
                }
            }

            // The anchor itself was filtered out. Land on the first SURVIVOR that followed it rather than
            // on index 0: restarting the queue from the top is a rewind the user never asked for, and on a
            // restore it silently replays songs already heard.
            val survivorIds = filtered.mapTo(HashSet()) { it.mediaId }
            val nextSurvivor = items.drop(mediaItemIndex + 1).firstOrNull { it.mediaId in survivorIds }
            val fallback = nextSurvivor
                ?.let { n -> filtered.indexOfFirst { it.mediaId == n.mediaId } }
                ?.takeIf { it >= 0 }
                ?: 0
            return copy(items = filtered, mediaItemIndex = fallback)
        }

        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) reanchor(items.filterExplicit()) else this

        /**
         * Hides video songs from the queue.
         *
         * 🔴 OWNER REPORT (2026-09-16): *"cuando doy clic sobre los video no hace nada"* — in the search
         * results, in Tendencias, on an artist's page and in Vídeos exportados alike, with no player, no
         * error and no message. This filter was the whole reason.
         *
         * A queue seeded from a video IS videos: the tapped item, and usually most of the radio behind it.
         * Filtering the list therefore removed **the very item the user asked for**, and when it removed
         * everything, `playQueue` hit `if (initialStatus.items.isEmpty()) return@launch` and gave up in
         * silence. The setting reads as "hide videos from lists", so nothing on screen explained it — and
         * it is ORed with Data Saver in [iad1tya.echo.music.playback.MusicService], which turns it on for a
         * user who never asked to filter anything while the lists KEEP showing videos (the search screen
         * only consults `HideVideoSongsKey`). Visible, tappable, dead.
         *
         * [protectAnchor] is the fix, and it belongs to user-initiated queues only: the item at
         * [mediaItemIndex] — what the tap chose — always survives, so the tap always plays something, while
         * the rest of the queue is still filtered and the preference keeps meaning what it says. Automatic
         * radio / related appends pass false and behave exactly as before.
         *
         * The decision itself lives in [QueueFilters] so it can be tested without a device; this function
         * only maps it back onto [items].
         */
        fun filterVideoSongs(
            disableVideos: Boolean = false,
            protectAnchor: Boolean = false,
        ) = if (disableVideos) {
            reanchor(
                QueueFilters
                    .keepIndicesHidingVideos(
                        isVideoSong = items.map { it.metadata?.isVideoSong == true },
                        anchorIndex = mediaItemIndex,
                        protectAnchor = protectAnchor,
                    ).map { items[it] },
            )
        } else {
            this
        }

        /**
         * Automatic radio / related / mood appends: keep only items YouTube Music tagged with a
         * [iad1tya.echo.music.models.MediaMetadata.musicVideoType] (ATV songs + official music videos).
         * Drops tutorials / how-tos / non-music uploads that arrive with a null type.
         */
        fun filterNonMusicForAutoQueue(enabled: Boolean = true) =
            if (enabled) reanchor(items.filterNonMusicForAutoQueue(true)) else this
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }

/** See [Queue.Status.filterNonMusicForAutoQueue]. */
fun List<MediaItem>.filterNonMusicForAutoQueue(enabled: Boolean = true) =
    if (enabled) {
        filter { it.metadata?.musicVideoType != null }
    } else {
        this
    }

/**
 * The pure decision behind [Queue.Status.filterVideoSongs], kept apart from media3.
 *
 * A `MediaItem` cannot be built in a plain JVM test (its builder parses an `android.net.Uri`), so a filter
 * expressed directly over `List<MediaItem>` is only ever exercised on a device — which is how this one
 * shipped able to empty a queue without a single test noticing. Reduced to "which indices survive", the
 * rule is ordinary data and [iad1tya.echo.music.playback.queues.QueueFilterAnchorTest] can hold it.
 */
object QueueFilters {
    /**
     * Indices of [isVideoSong] to KEEP when hiding video songs.
     *
     * With [protectAnchor], [anchorIndex] survives even when it is a video: it is the item the user tapped,
     * and a preference about what to HIDE must never decide that the tap plays nothing. Everything else is
     * filtered either way, so the queue behind the tapped video still honours the setting.
     *
     * Out-of-range or negative [anchorIndex] values protect nothing rather than throwing — a queue whose
     * index does not point into its own items is already degenerate, and the caller re-anchors afterwards.
     */
    fun keepIndicesHidingVideos(
        isVideoSong: List<Boolean>,
        anchorIndex: Int,
        protectAnchor: Boolean,
    ): List<Int> {
        val protectedIndex = if (protectAnchor) anchorIndex else -1
        return isVideoSong.indices.filter { index ->
            index == protectedIndex || !isVideoSong[index]
        }
    }
}
