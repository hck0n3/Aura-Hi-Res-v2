package iad1tya.echo.music.dislike

import android.os.SystemClock
import iad1tya.echo.music.playback.DismissedQueueUndoRule
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Registry row 803 (owner 2026-11-24) — «Menos de esto» marks the song AND its primary artist (row 721), for good and
 * synced to the other devices, so it is answered with «Se recomendará menos a X · Deshacer» instead of a bare Toast.
 * Pure: no Android, unit-tested.
 */
object SoftDislikeUndoRule {
    /** Same window as «Cola cerrada · Deshacer» (row 733). Chosen, not measured. */
    const val UNDO_WINDOW_MS = DismissedQueueUndoRule.UNDO_WINDOW_MS

    /**
     * What «Deshacer» may take back: only the marks THIS tap added (song first, artist second). A song or artist marked
     * before — an earlier «Menos de esto», or one synced from another device — keeps its mark.
     */
    fun addedByTap(before: DislikeStore.Disliked, songId: String, artistKey: String?): Pair<String?, String?> =
        Pair(songId.takeIf { it !in before.softSongs }, artistKey?.takeIf { it !in before.softArtists })
}

/** One «Menos de esto» the shell offers to undo. [artistName] is only shown, never logged (AGENTS.md rule 4). */
class SoftDislikeOffer(
    val token: Long,
    val expiresAtElapsed: Long,
    val artistName: String?,
    val songId: String?,
    val artistKey: String?,
) {
    /** False when the tap added nothing new: the message is shown without a «Deshacer» that would do nothing. */
    val canUndo: Boolean get() = songId != null || artistKey != null
}

/**
 * Holds the latest «Menos de esto» for the shell's snackbar (MainActivity). The song menus are bottom sheets that close
 * right after the tap, so the answer has to live outside them. A newer tap replaces the older offer.
 */
object SoftDislikeUndo {
    private val lastToken = AtomicLong(0L)
    private val _offer = MutableStateFlow<SoftDislikeOffer?>(null)
    val offer: StateFlow<SoftDislikeOffer?> = _offer.asStateFlow()

    /** Call right after `softDislikeSongAndArtist`, with the soft sets read just before it. */
    fun offer(before: DislikeStore.Disliked, songId: String, artistKey: String?, artistName: String?) {
        val (song, artist) = SoftDislikeUndoRule.addedByTap(before, songId, artistKey)
        _offer.value = SoftDislikeOffer(
            token = lastToken.incrementAndGet(),
            expiresAtElapsed = SystemClock.elapsedRealtime() + SoftDislikeUndoRule.UNDO_WINDOW_MS,
            artistName = artistName,
            songId = song,
            artistKey = artist,
        )
    }

    /** «Deshacer»: removes what that tap added. Ignored if the offer was already answered or replaced. */
    suspend fun undo(store: DislikeStore, token: Long) {
        val current = _offer.value?.takeIf { it.token == token } ?: return
        if (!_offer.compareAndSet(current, null)) return
        current.songId?.let { store.undoSoftDislikeSong(it) }
        current.artistKey?.let { store.undoSoftDislikeArtist(it) }
    }

    /** The snackbar closed without «Deshacer». A [token] of a newer tap is left alone. */
    fun drop(token: Long) {
        val current = _offer.value?.takeIf { it.token == token } ?: return
        _offer.compareAndSet(current, null)
    }
}
