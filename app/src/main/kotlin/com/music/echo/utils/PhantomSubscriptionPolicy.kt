package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

/**
 * One artist row's local listening evidence, as `DatabaseDao.artistInterestRows` reads it (registry row 814).
 * [playlistSongs] counts only the user's OWN playlists (the predicate of `artistPageLibrarySongs`, row 673).
 */
data class ArtistInterestRow(
    val artistId: String,
    val artistName: String,
    val likedSongs: Int,
    val playlistSongs: Int,
    val librarySongs: Int,
    val plays: Int,
)

/**
 * Registry rows 813/814 — «Revisar suscripciones» (owner 2026-11-24). For months an old bug subscribed the owner to
 * artists by itself (rows 646, 700, 711, 716). This is the pure half of the review tool: which account subscriptions
 * look like ones the user never made, and how a user-made selection becomes a throttled, cancellable batch.
 *
 * Nothing here decides to unsubscribe anything: [planBatch] only keeps what the user explicitly selected, and the
 * batch's `unsubscribe` is the caller's — the ViewModel passes the manual unfollow path (`toggleArtistFollow`).
 */
object PhantomSubscriptionPolicy {
    /** One account write every ~600 ms at most (owner task G1). */
    const val UNSUBSCRIBE_SPACING_MS = 600L

    /** «Few plays»: this many local plays or fewer still counts as no real listening (owner-tunable). */
    const val FEW_PLAYS_MAX = 2

    /** How long one unsubscribe may take to be confirmed by the account before it is reported as failed. */
    const val CONFIRM_TIMEOUT_MS = 30_000L

    /**
     * Row 829: a confirmed unfollow ([removalConfirmed]) the account still lists this long after it is taken as one
     * YouTube did not apply (not list lag), and the user may send it again.
     */
    const val RESEND_AFTER_CONFIRMED_HOURS = 24L

    enum class Signal { NO_LIKED_SONGS, NOT_IN_YOUR_PLAYLISTS, NOT_IN_LIBRARY, FEW_PLAYS }

    data class Interest(val likedSongs: Int = 0, val playlistSongs: Int = 0, val librarySongs: Int = 0, val plays: Int = 0)

    /** [suggested] = «Probablemente no la hiciste tú»: every signal points away from a real follow. */
    data class Verdict(val signals: Set<Signal>, val suggested: Boolean)

    /**
     * The evidence for one account subscription: every local row of the same channel ([ArtistSyncPolicy.aliasIds])
     * plus rows with exactly the same name (case-insensitive, the bridge `artistPageLibrarySongs` uses). A name
     * bridge can only ADD evidence, i.e. make a suggestion less likely — the safe direction.
     */
    fun interestFor(subscriptionId: String, subscriptionName: String, rows: List<ArtistInterestRow>): Interest {
        val ids = ArtistSyncPolicy.aliasIds(subscriptionId).toSet()
        val name = subscriptionName.trim().lowercase()
        val matching = rows.filter { row ->
            row.artistId in ids || (name.isNotEmpty() && row.artistName.trim().lowercase() == name)
        }
        return Interest(
            likedSongs = matching.sumOf { it.likedSongs },
            playlistSongs = matching.sumOf { it.playlistSongs },
            librarySongs = matching.sumOf { it.librarySongs },
            plays = matching.sumOf { it.plays },
        )
    }

    fun verdict(interest: Interest): Verdict {
        val signals = buildSet {
            if (interest.likedSongs == 0) add(Signal.NO_LIKED_SONGS)
            if (interest.playlistSongs == 0) add(Signal.NOT_IN_YOUR_PLAYLISTS)
            if (interest.librarySongs == 0) add(Signal.NOT_IN_LIBRARY)
            if (interest.plays <= FEW_PLAYS_MAX) add(Signal.FEW_PLAYS)
        }
        return Verdict(signals, suggested = signals.size == Signal.entries.size)
    }

    /**
     * The user already unfollowed this channel in Aura and the account has not confirmed it yet: the existing retry
     * (`LibraryUploadSync.flushPendingUnsubscribes`) owns it, and a follow tap would only re-follow it, so the row is
     * not selectable. Same shape as `DatabaseDao.artistsPendingUnsubscribe`.
     */
    fun removalPending(localRows: List<ArtistEntity>): Boolean =
        localRows.none { it.followedByUserAt != null } &&
            localRows.any { !it.isLocal && ArtistSyncPolicy.hasPendingUnfollow(it) }

    /**
     * Row 798: YouTube already confirmed the user's unfollow of [subscriptionId] inside its 30-day hold, yet the
     * account still lists it (list lag). The read-back leaves that row as it is (`markArtistsSubscribedOnYtm` with
     * `holdSince`) and no spelling reads «Suscrito», so the unfollow tap has nothing to toggle: the row is not
     * selectable. [localRows] are the stored rows of every spelling of the channel.
     */
    fun removalConfirmed(subscriptionId: String, localRows: List<ArtistEntity>, now: LocalDateTime): Boolean {
        val own = localRows.firstOrNull { it.id == subscriptionId } ?: return false
        return !own.isLocal && localRows.none { it.followedByUserAt != null } &&
            ArtistSyncPolicy.isConfirmedUnfollow(own) &&
            !own.unfollowedByUserAt!!.isBefore(ArtistSyncPolicy.confirmedUnfollowHoldSince(now))
    }

    /** Row 829: confirmed unfollows older than this, still listed by the account, may be sent again ([resendAllowed]). */
    fun resendSince(now: LocalDateTime): LocalDateTime = now.minusHours(RESEND_AFTER_CONFIRMED_HOURS)

    /**
     * Row 829: [removalConfirmed], and the confirmed unfollow is more than [RESEND_AFTER_CONFIRMED_HOURS] old — the
     * account still listing it is no longer list lag, so the user may select it and send the unfollow again. The age
     * is that of `unfollowConfirmedAt`, which holds the unfollow's own time (row 798), not the moment of the answer.
     */
    fun resendAllowed(subscriptionId: String, localRows: List<ArtistEntity>, now: LocalDateTime): Boolean {
        if (!removalConfirmed(subscriptionId, localRows, now)) return false
        val confirmedAt = localRows.first { it.id == subscriptionId }.unfollowConfirmedAt ?: return false
        return confirmedAt.isBefore(resendSince(now))
    }

    /**
     * Row 829: the hold the batch's account read-back (`markArtistsSubscribedOnYtm`) runs with for [candidate]. For a
     * resend it is [resendSince], so the still-listed confirmed row reads «Suscrito» again and the manual unfollow tap
     * sends it again; otherwise it is the 30-day hold every other read-back uses (row 798).
     */
    fun readBackHoldSince(candidate: Candidate, now: LocalDateTime): LocalDateTime =
        if (candidate.resendAllowed) resendSince(now) else ArtistSyncPolicy.confirmedUnfollowHoldSince(now)

    /** One account subscription on the review screen. */
    data class Candidate(
        val id: String,
        val name: String,
        val thumbnailUrl: String?,
        val channelId: String?,
        val verdict: Verdict,
        val removalPending: Boolean,
        val removalConfirmed: Boolean = false,
        val resendAllowed: Boolean = false,
    ) {
        /**
         * Not selectable: its removal is already pending ([removalPending]) or confirmed ([removalConfirmed]) — unless
         * that confirmation is old enough to send again ([resendAllowed], row 829).
         */
        val blocked: Boolean get() = removalPending || (removalConfirmed && !resendAllowed)
    }

    /**
     * Ids of the suggested rows that can be selected: what «Seleccionar las sugeridas» selects. A resend
     * ([Candidate.resendAllowed]) is never bulk-selected: it needs its own tap (row 829).
     */
    fun suggestedSelection(candidates: List<Candidate>): Set<String> =
        candidates.filter { it.verdict.suggested && !it.blocked && !it.removalConfirmed }.map { it.id }.toSet()

    /** Screen order: suggested first, then by name. */
    fun screenOrder(candidates: List<Candidate>): List<Candidate> =
        candidates.sortedWith(compareBy<Candidate> { !it.verdict.suggested }.thenBy { it.name.lowercase() })

    /**
     * The batch, in screen order: ONLY ids the user selected, never a [blocked] one (removal already pending), one
     * per channel (`UC…` and `MPLAUC…` are one subscription).
     */
    fun planBatch(ordered: List<String>, selected: Set<String>, blocked: Set<String>): List<String> =
        ordered.filter { it in selected && it !in blocked }.distinctBy { ArtistSyncPolicy.channelKey(it) }

    data class BatchResult(
        val removed: List<String>,
        val failed: List<String>,
        val notAttempted: List<String>,
        val cancelled: Boolean,
    )

    /**
     * Runs [plan] one id at a time, [spacingMs] apart (no pause before the first or after the last). [shouldStop]
     * is checked before each id and after each pause: once true, the rest is [BatchResult.notAttempted]. An
     * exception from [unsubscribe] is a failure of that id; cancellation propagates.
     */
    suspend fun runBatch(
        plan: List<String>,
        spacingMs: Long,
        unsubscribe: suspend (String) -> Boolean,
        pause: suspend (Long) -> Unit,
        shouldStop: () -> Boolean,
        onProgress: (done: Int, removed: Int, failed: Int) -> Unit,
    ): BatchResult {
        val removed = ArrayList<String>()
        val failed = ArrayList<String>()
        for ((index, id) in plan.withIndex()) {
            if (shouldStop()) return BatchResult(removed, failed, plan.drop(index), cancelled = true)
            if (index > 0) {
                pause(spacingMs)
                if (shouldStop()) return BatchResult(removed, failed, plan.drop(index), cancelled = true)
            }
            val ok = try {
                unsubscribe(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (ok) removed.add(id) else failed.add(id)
            onProgress(index + 1, removed.size, failed.size)
        }
        return BatchResult(removed, failed, emptyList(), cancelled = false)
    }
}
