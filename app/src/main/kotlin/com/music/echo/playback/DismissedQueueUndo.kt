package iad1tya.echo.music.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import iad1tya.echo.music.devicesync.RemotePlayback
import iad1tya.echo.music.playback.queues.ListQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Registry row 733 (UX audit P22B) — the decisions behind «Cola cerrada · Deshacer». Pure: no Android, unit-tested.
 *
 * Swiping the player sheet down past the threshold stops playback and wipes the whole queue (AuraPlayer `onDismiss`).
 * The gesture stays exactly as it was; what is new is a short window in which the wiped queue can be put back.
 */
object DismissedQueueUndoRule {
    /** How long the «Deshacer» stays up (audit P22B: «~6 s»). Chosen, not measured. */
    const val UNDO_WINDOW_MS = 6_000L

    /** A tap that lands right as the window closes still counts (main-thread scheduling, a slow frame). */
    const val TAP_GRACE_MS = 1_000L

    /**
     * Offer the undo only for a real local queue. Never while another device is controlled (the local player only holds
     * the mirror of that device — rows 657-664/681-686) nor for a Listen Together guest (the queue belongs to the room):
     * both paths stay exactly as they were.
     */
    fun canOffer(itemCount: Int, controllingAnotherDevice: Boolean, listenTogetherGuest: Boolean): Boolean =
        itemCount > 0 && !controllingAnotherDevice && !listenTogetherGuest

    /**
     * Put the queue back only if nothing took its place: the player is still empty (he did not start something else in
     * the meantime — that would be overwritten), the window is still open, and the same two modes are still off.
     */
    fun canRestore(
        nowElapsed: Long,
        expiresAtElapsed: Long,
        playerItemCount: Int,
        controllingAnotherDevice: Boolean,
        listenTogetherGuest: Boolean,
    ): Boolean =
        nowElapsed <= expiresAtElapsed + TAP_GRACE_MS &&
            playerItemCount == 0 &&
            !controllingAnotherDevice &&
            !listenTogetherGuest

    /** What is left of the window when the snackbar is (re)shown; 0 = already over, drop it instead of showing it. */
    fun remainingMs(nowElapsed: Long, expiresAtElapsed: Long): Long = (expiresAtElapsed - nowElapsed).coerceAtLeast(0L)
}

/** What the shell needs to show the snackbar. The token ties the answer to THIS dismiss and no other. */
class DismissedQueueOffer(val token: Long, val expiresAtElapsed: Long)

/**
 * Holds the snapshot of the queue the swipe-down wiped, for [DismissedQueueUndoRule.UNDO_WINDOW_MS] only. Main thread only
 * (the dismiss gesture, the snackbar answer and the expiry are all posted there).
 *
 * The restore goes through [MusicService.playQueue] with `isRestore = true, playWhenReady = false` — the same call the
 * persisted queue is restored with at start — so the queue comes back PAUSED at the same song and position, its title and
 * context (no-repeat memory) included, and nothing new runs: no resume offer, no shuffle reset.
 */
object DismissedQueueUndo {
    private const val TAG = "DismissedQueueUndo"

    private class Snapshot(
        val token: Long,
        val expiresAtElapsed: Long,
        val title: String?,
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val contextId: String?,
        val shuffleModeEnabled: Boolean,
        val repeatMode: Int,
    )

    private val _offer = MutableStateFlow<DismissedQueueOffer?>(null)
    val offer: StateFlow<DismissedQueueOffer?> = _offer.asStateFlow()

    private var snapshot: Snapshot? = null
    private var lastToken = 0L
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private fun listenTogetherGuest(service: MusicService): Boolean =
        runCatching { service.listenTogetherManager.shouldBlockPlaybackChanges() }.getOrDefault(false)

    /** Call from the dismiss gesture BEFORE the queue is wiped. Does nothing in remote-control or guest mode. */
    fun capture(service: MusicService) {
        val player = service.player
        val count = player.mediaItemCount
        if (!DismissedQueueUndoRule.canOffer(count, RemotePlayback.active, listenTogetherGuest(service))) return
        val items = runCatching { (0 until count).map { player.getMediaItemAt(it) } }.getOrNull() ?: return
        drop() // a newer dismiss replaces any older offer
        val token = ++lastToken
        val expiresAt = SystemClock.elapsedRealtime() + DismissedQueueUndoRule.UNDO_WINDOW_MS
        snapshot = Snapshot(
            token = token,
            expiresAtElapsed = expiresAt,
            title = service.queueTitle,
            items = items,
            index = player.currentMediaItemIndex.coerceIn(0, items.lastIndex),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            contextId = service.currentQueue.contextId,
            shuffleModeEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        _offer.value = DismissedQueueOffer(token, expiresAt)
        // One delayed message, not a poll: frees the snapshot even if no screen ever answers the offer.
        mainHandler.postDelayed({ drop(token) }, DismissedQueueUndoRule.UNDO_WINDOW_MS + DismissedQueueUndoRule.TAP_GRACE_MS)
        // Counts only (AGENTS.md rule 4).
        Timber.tag(TAG).i("QUEUE_UNDO offered items=%d", items.size)
    }

    /** «Deshacer». Returns true when the queue was put back. */
    fun restore(service: MusicService, token: Long): Boolean {
        val snap = snapshot?.takeIf { it.token == token } ?: return false
        drop(token)
        val player = service.player
        if (!DismissedQueueUndoRule.canRestore(
                nowElapsed = SystemClock.elapsedRealtime(),
                expiresAtElapsed = snap.expiresAtElapsed,
                playerItemCount = player.mediaItemCount,
                controllingAnotherDevice = RemotePlayback.active,
                listenTogetherGuest = listenTogetherGuest(service),
            )
        ) {
            Timber.tag(TAG).i("QUEUE_UNDO refused items_now=%d", player.mediaItemCount)
            return false
        }
        // Set before the items land, so playQueue's own shuffle handling orders the queue that comes back.
        if (player.repeatMode != snap.repeatMode) player.repeatMode = snap.repeatMode
        if (player.shuffleModeEnabled != snap.shuffleModeEnabled) player.shuffleModeEnabled = snap.shuffleModeEnabled
        service.playQueue(
            queue = ListQueue(
                title = snap.title,
                items = snap.items,
                startIndex = snap.index,
                position = snap.positionMs,
                contextId = snap.contextId,
            ),
            playWhenReady = false,
            isRestore = true,
        )
        Timber.tag(TAG).i("QUEUE_UNDO restored items=%d", snap.items.size)
        return true
    }

    /** The offer lapsed or was swiped away: forget the snapshot. A [token] of a newer dismiss is left alone. */
    fun drop(token: Long? = null) {
        val current = snapshot
        if (token != null && current != null && current.token != token) return
        snapshot = null
        _offer.value = null
    }
}
