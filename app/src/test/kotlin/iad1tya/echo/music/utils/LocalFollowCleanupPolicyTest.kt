package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 905: which local-only follows the cleanup offers, what a removal writes, and «Deshacer». */
class LocalFollowCleanupPolicyTest {
    private val t0: LocalDateTime = LocalDateTime.of(2026, 11, 1, 10, 0)
    private val t1: LocalDateTime = t0.plusDays(3)

    private fun artist(
        id: String,
        name: String = id,
        followed: LocalDateTime? = t0,
        bookmarked: LocalDateTime? = t0,
        synced: LocalDateTime? = null,
        unfollowed: LocalDateTime? = null,
        isLocal: Boolean = false,
    ) = ArtistEntity(
        id = id,
        name = name,
        bookmarkedAt = bookmarked,
        followedByUserAt = followed,
        ytmSyncedAt = synced,
        unfollowedByUserAt = unfollowed,
        isLocal = isLocal,
    )

    private fun interest(id: String, name: String = id, liked: Int = 0, plays: Int = 0) =
        ArtistInterestRow(id, name, liked, 0, 0, plays)

    // ---- what is offered ----

    @Test
    fun `an imported follow the user never confirmed is offered`() {
        val rows = listOf(artist("UCa", "Alpha"))
        val found = LocalFollowCleanupPolicy.candidates(rows, awaiting = setOf("UCa"), localOnly = emptySet(), interest = emptyList())
        assertEquals(listOf("UCa"), found.map { it.id })
        assertEquals(listOf("UCa"), found.single().followedIds)
        assertTrue("no listening evidence at all: suggested", found.single().verdict.suggested)
    }

    @Test
    fun `a follow that is not in the awaiting list is a hand follow and is never offered`() {
        // A follow tap takes the id out of the awaiting list (AccountPush.onUserFollowedArtist).
        val rows = listOf(artist("UCa"), artist("UCb"))
        val found = LocalFollowCleanupPolicy.candidates(rows, awaiting = setOf("UCb"), localOnly = emptySet(), interest = emptyList())
        assertEquals(listOf("UCb"), found.map { it.id })
    }

    @Test
    fun `a channel with one hand-followed spelling is never offered, even if another spelling was imported`() {
        val spellings = listOf(artist("UCa"), artist("MPLAUCa"))
        assertFalse(LocalFollowCleanupPolicy.eligible(spellings, awaiting = setOf("MPLAUCa"), localOnly = emptySet()))
        assertTrue(LocalFollowCleanupPolicy.eligible(spellings, awaiting = setOf("MPLAUCa", "UCa"), localOnly = emptySet()))
    }

    @Test
    fun `«Solo en Aura» is the user's own answer and is never offered, under any spelling`() {
        val spellings = listOf(artist("MPLAUCa"))
        assertFalse(LocalFollowCleanupPolicy.eligible(spellings, awaiting = setOf("MPLAUCa"), localOnly = setOf("UCa")))
        assertFalse(LocalFollowCleanupPolicy.eligible(spellings, awaiting = setOf("MPLAUCa"), localOnly = setOf("MPLAUCa")))
    }

    @Test
    fun `a channel on the account, with the user's own unfollow, not followed or a local file is never offered`() {
        val awaiting = setOf("UCa")
        assertFalse(LocalFollowCleanupPolicy.eligible(listOf(artist("UCa", synced = t1)), awaiting, emptySet()))
        assertFalse(LocalFollowCleanupPolicy.eligible(listOf(artist("UCa", followed = null, unfollowed = t1)), awaiting, emptySet()))
        assertFalse(LocalFollowCleanupPolicy.eligible(listOf(artist("UCa", followed = null)), awaiting, emptySet()))
        assertFalse(LocalFollowCleanupPolicy.eligible(listOf(artist("UCa", isLocal = true)), awaiting, emptySet()))
        // Another spelling already on the account: the account part of the screen owns the channel.
        assertFalse(LocalFollowCleanupPolicy.eligible(listOf(artist("UCa"), artist("MPLAUCa", followed = null, synced = t1)), awaiting, emptySet()))
    }

    @Test
    fun `one candidate per channel, with every followed spelling, suggested first then by name`() {
        val rows = listOf(
            artist("MPLAUCz", "Zeta"), artist("UCz", "Zeta"),
            artist("UCb", "beta"),
            artist("UCa", "Alpha"),
        )
        val awaiting = setOf("MPLAUCz", "UCz", "UCb", "UCa")
        val found = LocalFollowCleanupPolicy.candidates(rows, awaiting, emptySet(), interest = listOf(interest("UCa", "Alpha", liked = 2)))
        assertEquals(listOf("UCb", "MPLAUCz", "UCa"), found.map { it.id })
        assertEquals(listOf("MPLAUCz", "UCz"), found.first { it.name == "Zeta" }.followedIds)
        assertFalse("liked songs keep it out of the suggestions", found.last().verdict.suggested)
    }

    // ---- selection ----

    @Test
    fun `select all, select the suggested and the plan keep only what the user selected`() {
        val rows = listOf(artist("UCa", "Alpha"), artist("UCb", "Beta"))
        val found = LocalFollowCleanupPolicy.candidates(rows, setOf("UCa", "UCb"), emptySet(), listOf(interest("UCb", "Beta", plays = 9)))
        assertEquals(setOf("UCa", "UCb"), LocalFollowCleanupPolicy.selectAll(found))
        assertEquals(setOf("UCa"), LocalFollowCleanupPolicy.suggestedSelection(found))
        assertEquals(listOf("UCb"), LocalFollowCleanupPolicy.plan(found, setOf("UCb", "UCgone")).map { it.id })
        assertTrue(LocalFollowCleanupPolicy.plan(found, emptySet()).isEmpty())
    }

    // ---- the write ----

    @Test
    fun `a removal clears the follow in Aura only and writes nothing that could reach the account`() {
        val row = artist("UCa")
        val after = LocalFollowCleanupPolicy.cleared(row, listOf(row), setOf("UCa"), emptySet())
        assertNotNull(after)
        after!!
        assertNull(after.followedByUserAt)
        assertNull(after.bookmarkedAt)
        assertNull("no unfollow marker: nothing for the account", after.unfollowedByUserAt)
        assertNull(after.ytmSyncedAt)
        assertFalse(ArtistSyncPolicy.maySubscribe(after))
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(after))
        assertEquals(row.name, after.name)
    }

    @Test
    fun `a follow tap made after the list was read wins over the removal`() {
        // The tap took the id out of the awaiting list, which the removal reads again right before writing.
        val row = artist("UCa")
        assertNull(LocalFollowCleanupPolicy.cleared(row, listOf(row), awaiting = emptySet(), localOnly = emptySet()))
        // So does the account read-back that reached it meanwhile.
        val synced = artist("UCa", synced = t1)
        assertNull(LocalFollowCleanupPolicy.cleared(synced, listOf(synced), setOf("UCa"), emptySet()))
    }

    // ---- undo ----

    @Test
    fun `undo puts back exactly what the removal cleared`() {
        val row = artist("UCa", followed = t0, bookmarked = t0.minusDays(1))
        val snapshot = LocalFollowCleanupPolicy.snapshotOf(row)
        val after = LocalFollowCleanupPolicy.cleared(row, listOf(row), setOf("UCa"), emptySet())!!
        val back = LocalFollowCleanupPolicy.restored(after, snapshot)
        assertEquals(row, back)
    }

    @Test
    fun `undo never overrides what happened since`() {
        val snapshot = LocalFollowCleanupPolicy.Snapshot("UCa", bookmarkedAt = t0, followedByUserAt = t0)
        assertNull("followed again by hand", LocalFollowCleanupPolicy.restored(artist("UCa", followed = t1), snapshot))
        assertNull("unfollowed by the user", LocalFollowCleanupPolicy.restored(artist("UCa", followed = null, unfollowed = t1), snapshot))
        assertNull("now on the account", LocalFollowCleanupPolicy.restored(artist("UCa", followed = null, synced = t1), snapshot))
        assertNull("another row", LocalFollowCleanupPolicy.restored(artist("UCb", followed = null), snapshot))
    }
}
