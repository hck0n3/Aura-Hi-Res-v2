package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.Candidate
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.Interest
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.Signal
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry rows 813/814: phantom-subscription signals and the user-selected, throttled, cancellable batch. */
class PhantomSubscriptionPolicyTest {
    private fun row(id: String, name: String = id, liked: Int = 0, playlists: Int = 0, library: Int = 0, plays: Int = 0) =
        ArtistInterestRow(id, name, liked, playlists, library, plays)

    private fun candidate(id: String, suggested: Boolean, pending: Boolean = false, name: String = id) = Candidate(
        id = id,
        name = name,
        thumbnailUrl = null,
        channelId = null,
        verdict = PhantomSubscriptionPolicy.verdict(if (suggested) Interest() else Interest(likedSongs = 1)),
        removalPending = pending,
    )

    // ---- signals (row 814) ----

    @Test
    fun `no evidence at all is suggested with every signal`() {
        val v = PhantomSubscriptionPolicy.verdict(Interest())
        assertTrue(v.suggested)
        assertEquals(Signal.entries.toSet(), v.signals)
    }

    @Test
    fun `any single piece of real evidence keeps a subscription out of the suggestions`() {
        listOf(
            Interest(likedSongs = 1),
            Interest(playlistSongs = 1),
            Interest(librarySongs = 1),
            Interest(plays = PhantomSubscriptionPolicy.FEW_PLAYS_MAX + 1),
        ).forEach { assertFalse(it.toString(), PhantomSubscriptionPolicy.verdict(it).suggested) }
    }

    @Test
    fun `a few plays still count as no real listening`() {
        val v = PhantomSubscriptionPolicy.verdict(Interest(plays = PhantomSubscriptionPolicy.FEW_PLAYS_MAX))
        assertTrue(v.suggested)
        assertTrue(Signal.FEW_PLAYS in v.signals)
        assertEquals(2, PhantomSubscriptionPolicy.FEW_PLAYS_MAX)
    }

    @Test
    fun `evidence is summed over every spelling of the channel and the exact same name`() {
        val rows = listOf(
            row("UCabc", liked = 1),
            row("MPLAUCabc", plays = 2),
            row("LAlocal1", name = "Artist X", playlists = 3),
            row("UCother", name = "Artist XY", library = 9),
        )
        val interest = PhantomSubscriptionPolicy.interestFor("MPLAUCabc", "artist x", rows)
        assertEquals(Interest(likedSongs = 1, playlistSongs = 3, librarySongs = 0, plays = 2), interest)
    }

    @Test
    fun `a local-files artist of the same name with liked songs keeps the subscription out of the suggestions`() {
        // LocalSongScanner gives on-device songs their own artist row (isLocal = 1, a non-YouTube id): its evidence
        // reaches the account subscription only through the exact-name bridge.
        val rows = listOf(row("local_artist_42", name = "Artist X", liked = 12, playlists = 3, plays = 40))
        val interest = PhantomSubscriptionPolicy.interestFor("UCabc", "Artist X", rows)
        assertFalse(PhantomSubscriptionPolicy.verdict(interest).suggested)
    }

    @Test
    fun `a blank name bridges nothing`() {
        val rows = listOf(row("UCzzz", name = " ", liked = 5))
        assertEquals(Interest(), PhantomSubscriptionPolicy.interestFor("UCabc", "  ", rows))
    }

    @Test
    fun `removal is pending only for an owed unfollow with no follow left`() {
        val now = LocalDateTime.now()
        val owed = ArtistEntity(id = "UCa", name = "a", unfollowedByUserAt = now, ytmSyncedAt = now)
        val confirmed = ArtistEntity(id = "UCa", name = "a", unfollowedByUserAt = now, ytmSyncedAt = null)
        val followedAlias = ArtistEntity(id = "MPLAUCa", name = "a", followedByUserAt = now, ytmSyncedAt = now)
        assertTrue(PhantomSubscriptionPolicy.removalPending(listOf(owed)))
        assertFalse(PhantomSubscriptionPolicy.removalPending(listOf(confirmed)))
        assertFalse(PhantomSubscriptionPolicy.removalPending(listOf(owed, followedAlias)))
        assertFalse(PhantomSubscriptionPolicy.removalPending(emptyList()))
        assertFalse(PhantomSubscriptionPolicy.removalPending(listOf(owed.copy(isLocal = true))))
    }

    // ---- selection and planning (row 813) ----

    @Test
    fun `select suggested takes only suggested rows that are not already pending`() {
        val list = listOf(candidate("UC1", true), candidate("UC2", false), candidate("UC3", true, pending = true))
        assertEquals(setOf("UC1"), PhantomSubscriptionPolicy.suggestedSelection(list))
    }

    @Test
    fun `suggested rows come first, then by name`() {
        val list = listOf(
            candidate("UC1", false, name = "a"),
            candidate("UC2", true, name = "z"),
            candidate("UC3", true, name = "B"),
        )
        assertEquals(listOf("UC3", "UC2", "UC1"), PhantomSubscriptionPolicy.screenOrder(list).map { it.id })
    }

    @Test
    fun `the plan holds only selected ids, in screen order, never a blocked one, one per channel`() {
        val ordered = listOf("UC1", "UC2", "MPLAUC2", "UC3", "UC4")
        val plan = PhantomSubscriptionPolicy.planBatch(
            ordered = ordered,
            selected = setOf("UC4", "UC2", "MPLAUC2", "UC3", "UCnotlisted"),
            blocked = setOf("UC3"),
        )
        assertEquals(listOf("UC2", "UC4"), plan)
        assertTrue(PhantomSubscriptionPolicy.planBatch(ordered, emptySet(), emptySet()).isEmpty())
    }

    // ---- the batch (row 813) ----

    @Test
    fun `the batch calls only the planned ids, spaced, with no pause before the first or after the last`() = runBlocking {
        val called = mutableListOf<String>()
        val pauses = mutableListOf<Long>()
        val progress = mutableListOf<Triple<Int, Int, Int>>()
        val result = PhantomSubscriptionPolicy.runBatch(
            plan = listOf("UC1", "UC2", "UC3"),
            spacingMs = PhantomSubscriptionPolicy.UNSUBSCRIBE_SPACING_MS,
            unsubscribe = { called += it; it != "UC2" },
            pause = { pauses += it },
            shouldStop = { false },
            onProgress = { d, r, f -> progress += Triple(d, r, f) },
        )
        assertEquals(listOf("UC1", "UC2", "UC3"), called)
        assertEquals(listOf(600L, 600L), pauses)
        assertEquals(listOf("UC1", "UC3"), result.removed)
        assertEquals(listOf("UC2"), result.failed)
        assertTrue(result.notAttempted.isEmpty())
        assertFalse(result.cancelled)
        assertEquals(listOf(Triple(1, 1, 0), Triple(2, 1, 1), Triple(3, 2, 1)), progress)
    }

    @Test
    fun `stopping leaves the rest not attempted and calls nothing more`() = runBlocking {
        val called = mutableListOf<String>()
        var stop = false
        val result = PhantomSubscriptionPolicy.runBatch(
            plan = listOf("UC1", "UC2", "UC3"),
            spacingMs = 600L,
            unsubscribe = { called += it; true },
            pause = { stop = true },
            shouldStop = { stop },
            onProgress = { _, _, _ -> },
        )
        assertEquals(listOf("UC1"), called)
        assertEquals(listOf("UC1"), result.removed)
        assertEquals(listOf("UC2", "UC3"), result.notAttempted)
        assertTrue(result.cancelled)
    }

    @Test
    fun `an exception fails that id only`() = runBlocking {
        val result = PhantomSubscriptionPolicy.runBatch(
            plan = listOf("UC1", "UC2"),
            spacingMs = 0L,
            unsubscribe = { if (it == "UC1") error("boom") else true },
            pause = {},
            shouldStop = { false },
            onProgress = { _, _, _ -> },
        )
        assertEquals(listOf("UC2"), result.removed)
        assertEquals(listOf("UC1"), result.failed)
    }

    @Test
    fun `an empty plan does nothing`() = runBlocking {
        var calls = 0
        val result = PhantomSubscriptionPolicy.runBatch(emptyList(), 600L, { calls++; true }, {}, { false }, { _, _, _ -> })
        assertEquals(0, calls)
        assertTrue(result.removed.isEmpty() && result.failed.isEmpty() && result.notAttempted.isEmpty())
    }
}
