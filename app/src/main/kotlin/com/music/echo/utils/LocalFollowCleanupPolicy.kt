package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import java.time.LocalDateTime

/**
 * Registry row 905 (plan, phase 9: «Herramienta para limpiar artistas que no siguió el usuario»). The «Revisar
 * suscripciones» screen (rows 813/814) already cleans the ACCOUNT. What it could not see: artists Aura itself shows as
 * followed that never reached the account — the follows an import (Spotify, Tidal, Deezer) wrote here and the user
 * never confirmed («esperan confirmación», `ImportFollowsAwaitingConfirmKey`, row 646). They read
 * «Suscrito» everywhere in Aura and, once the user confirms the import list or turns the upload switch on, they are the
 * ones that would be subscribed on the account.
 *
 * Pure: which of those Aura can offer to remove, how a selection becomes the rows to clear, and how «Deshacer» puts
 * them back. The signals:
 *  - [importedAwaiting]: an id is offered only while it is BOTH in the awaiting list and in the record the importers write
 *    (`ImportFollowsWrittenByImportKey`). The awaiting list alone is no proof: its one-time move (`ImportFollowsPendingHeldV1Key`)
 *    also files the onboarding picks, follows made signed out and a restore's follows. A follow TAP takes the id out of
 *    both (`onUserFollowedArtist` in AccountPush), so a channel the user followed by hand is never offered;
 *  - «Solo en Aura» (`ImportFollowsLocalOnlyKey`) is the user's own answer «keep it here»: never offered;
 *  - a channel the account already holds (`ytmSyncedAt`) or with an unfollow of the user's own (`unfollowedByUserAt`) is
 *    not local-only: the account part of the screen and the existing machinery own it.
 *
 * The removal only takes the follow out of AURA: it clears `followedByUserAt` and `bookmarkedAt` (what the unfollow tap
 * clears locally) and writes no unfollow marker, so nothing can ever reach the account from it (the shape «incidental,
 * never touched» of [ArtistEntity]). That is also what makes it undoable: [restored] writes back the two values.
 */
object LocalFollowCleanupPolicy {
    /** One channel Aura shows as followed only because an import said so. */
    data class Candidate(
        /** The followed spelling shown on screen (the first in id order: stable between reads). */
        val id: String,
        val name: String,
        val thumbnailUrl: String?,
        /** Every followed spelling of the channel (`UC…` / `MPLAUC…`): all of them are cleared together. */
        val followedIds: List<String>,
        /** Row 814's local evidence: «Probablemente no la hiciste tú» when there is none at all. */
        val verdict: PhantomSubscriptionPolicy.Verdict,
    )

    /** One cleared row as it was before: what «Deshacer» writes back. Ids and timestamps only. */
    data class Snapshot(val id: String, val bookmarkedAt: LocalDateTime?, val followedByUserAt: LocalDateTime?)

    /**
     * The awaiting ids an import itself wrote: what [eligible], [candidates] and [cleared] take as `awaiting`. An id the
     * one-time move or a restore put in [awaiting] is not in [writtenByImport], and is never offered.
     */
    fun importedAwaiting(awaiting: Set<String>, writtenByImport: Set<String>): Set<String> = awaiting intersect writtenByImport

    /**
     * [spellings] are the stored rows of ONE channel (every id of [ArtistSyncPolicy.aliasIds]). True when the channel is
     * followed in Aura only because of an import the user never confirmed: some spelling is followed, every followed
     * spelling is in [awaiting] (already [importedAwaiting]), none is a local-file artist, no spelling is in [localOnly], and no spelling is
     * on the account or carries the user's own unfollow.
     */
    fun eligible(spellings: List<ArtistEntity>, awaiting: Set<String>, localOnly: Set<String>): Boolean {
        val followed = spellings.filter { it.followedByUserAt != null }
        if (followed.isEmpty()) return false
        if (followed.any { it.isLocal || it.id !in awaiting }) return false
        val ids = spellings.flatMap { ArtistSyncPolicy.aliasIds(it.id) }.toSet()
        if (ids.any { it in localOnly }) return false
        return spellings.none { it.ytmSyncedAt != null || it.unfollowedByUserAt != null }
    }

    /**
     * The screen's list: [rows] are the stored rows of every spelling of every awaiting id; [interest] is
     * `DatabaseDao.artistInterestRows` (row 814). One candidate per channel, suggested first, then by name.
     */
    fun candidates(
        rows: List<ArtistEntity>,
        awaiting: Set<String>,
        localOnly: Set<String>,
        interest: List<ArtistInterestRow>,
    ): List<Candidate> = rows
        .distinctBy { it.id }
        .groupBy { ArtistSyncPolicy.channelKey(it.id) }
        .values
        .filter { eligible(it, awaiting, localOnly) }
        .map { spellings ->
            val followed = spellings.filter { it.followedByUserAt != null }.sortedBy { it.id }
            val shown = followed.first()
            Candidate(
                id = shown.id,
                name = shown.name,
                thumbnailUrl = shown.thumbnailUrl ?: followed.firstNotNullOfOrNull { it.thumbnailUrl },
                followedIds = followed.map { it.id },
                verdict = PhantomSubscriptionPolicy.verdict(PhantomSubscriptionPolicy.interestFor(shown.id, shown.name, interest)),
            )
        }
        .sortedWith(compareBy<Candidate> { !it.verdict.suggested }.thenBy { it.name.lowercase() })

    /** «Seleccionar todas». */
    fun selectAll(candidates: List<Candidate>): Set<String> = candidates.map { it.id }.toSet()

    /** «Seleccionar las sugeridas»: only the ones with no listening evidence at all (row 814's four signals). */
    fun suggestedSelection(candidates: List<Candidate>): Set<String> =
        candidates.filter { it.verdict.suggested }.map { it.id }.toSet()

    /** What the preview lists and the removal clears: ONLY the selected candidates, in screen order. */
    fun plan(candidates: List<Candidate>, selected: Set<String>): List<Candidate> = candidates.filter { it.id in selected }

    /**
     * The row with its follow taken out of Aura, or null when it must stay as it is. [spellings] are re-read right before
     * the write (inside the same transaction), with [awaiting]/[localOnly] read again too: a follow tap made since the
     * list was read took the id out of [awaiting], and a channel that reached the account meanwhile is no longer
     * local-only — neither is touched.
     */
    fun cleared(row: ArtistEntity, spellings: List<ArtistEntity>, awaiting: Set<String>, localOnly: Set<String>): ArtistEntity? {
        if (row.followedByUserAt == null || !eligible(spellings, awaiting, localOnly)) return null
        return row.copy(followedByUserAt = null, bookmarkedAt = null)
    }

    /** What [cleared] changed, for «Deshacer». */
    fun snapshotOf(row: ArtistEntity): Snapshot = Snapshot(row.id, row.bookmarkedAt, row.followedByUserAt)

    /**
     * «Deshacer»: the row as it was before the removal — only while it is still exactly what the removal left (not
     * followed, nothing for the account). A follow tap, an unfollow or an account read-back since then wins.
     */
    fun restored(current: ArtistEntity, snapshot: Snapshot): ArtistEntity? {
        if (current.id != snapshot.id) return null
        if (current.followedByUserAt != null || current.unfollowedByUserAt != null || current.ytmSyncedAt != null) return null
        return current.copy(bookmarkedAt = snapshot.bookmarkedAt ?: current.bookmarkedAt, followedByUserAt = snapshot.followedByUserAt)
    }
}
