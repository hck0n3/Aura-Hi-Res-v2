package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 716: the sync paths may neither resurrect «Suscrito» over the user's unfollow (another spelling of the
 * channel, a read that started before the tap) nor write back a row copy read before a network call.
 */
class ArtistSyncStaleWriteGuardTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 23, 12, 0)
    private val earlier: LocalDateTime = now.minusHours(1)

    private fun row(id: String) = ArtistEntity(id = id, name = "Artist")

    // ---- the read-back and the other spellings -----------------------------------------------------

    /** The user's unfollow, still owed to the account (the account was last seen holding the channel). */
    private fun owed(id: String) = row(id).copy(unfollowedByUserAt = earlier, ytmSyncedAt = earlier.minusDays(1))

    @Test
    fun `a pending unfollow is an unfollow still owed to the account`() {
        assertTrue(ArtistSyncPolicy.hasPendingUnfollow(owed("UCx")))
        assertFalse(ArtistSyncPolicy.hasPendingUnfollow(owed("UCx").copy(followedByUserAt = now)))
        assertFalse(ArtistSyncPolicy.hasPendingUnfollow(row("UCx")))
        // Row 716 review: confirmArtistUnsubscribed clears ytmSyncedAt but keeps the marker (row 150(d)). A
        // delivered unsubscribe must not block the other spelling's «Suscrito» for ever.
        assertFalse("a confirmed unsubscribe is not pending", ArtistSyncPolicy.hasPendingUnfollow(owed("UCx").copy(ytmSyncedAt = null)))
    }

    @Test
    fun `another spelling's pending unfollow is seen under both spellings`() {
        assertTrue(ArtistSyncPolicy.aliasHasPendingUnfollow("MPLAUCx", mapOf("UCx" to owed("UCx"))))
        assertTrue(ArtistSyncPolicy.aliasHasPendingUnfollow("UCx", mapOf("MPLAUCx" to owed("MPLAUCx"))))
        assertFalse("its own marker is the CASE's business, not an alias", ArtistSyncPolicy.aliasHasPendingUnfollow("UCx", mapOf("UCx" to owed("UCx"))))
        assertFalse("not a channel: no alias", ArtistSyncPolicy.aliasHasPendingUnfollow("MPLAxyz", mapOf("xyz" to owed("xyz"))))
        assertFalse(
            "a re-followed spelling is no pending unfollow",
            ArtistSyncPolicy.aliasHasPendingUnfollow("UCx", mapOf("MPLAUCx" to owed("MPLAUCx").copy(followedByUserAt = now))),
        )
        assertFalse(
            "a delivered unsubscribe on the other spelling does not block this one",
            ArtistSyncPolicy.aliasHasPendingUnfollow("UCx", mapOf("MPLAUCx" to owed("MPLAUCx").copy(ytmSyncedAt = null))),
        )
    }

    @Test
    fun `the read-back does not follow a spelling over another spelling's unfollow`() {
        val listed = ArtistSyncPolicy.afterRemoteSubscriptionSeen(row("MPLAUCx"), now, aliasPendingUnfollow = true)
        assertNull("«Suscrito» resurrected over the user's unfollow", listed.followedByUserAt)
        assertEquals(now, listed.ytmSyncedAt)
        assertFalse("nothing for the uploader either way", ArtistSyncPolicy.maySubscribe(listed))
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(listed, now))

        val plain = ArtistSyncPolicy.afterRemoteSubscriptionSeen(row("MPLAUCx"), now)
        assertEquals("unchanged without an alias unfollow", now, plain.followedByUserAt)
        val kept = ArtistSyncPolicy.afterRemoteSubscriptionSeen(row("UCx").copy(followedByUserAt = earlier), now, aliasPendingUnfollow = true)
        assertEquals("an existing follow is never removed by a read", earlier, kept.followedByUserAt)
        val own = ArtistSyncPolicy.afterRemoteSubscriptionSeen(row("UCx").copy(unfollowedByUserAt = earlier), now)
        assertNotNull("its own unfollow is still re-armed (the retry)", own.unfollowedByUserAt)
        assertTrue(ArtistSyncPolicy.mayUnsubscribe(own, now))
    }

    // ---- wiring (no Android SDK in the unit job, so pinned as source text) -----------------------

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun query(dao: String, fn: String) = dao.substringBefore("fun $fn(").substringAfterLast("@Query(")

    @Test
    fun `the account read-back query skips a spelling whose alias carries the unfollow`() {
        val q = query(main("db/DatabaseDao.kt"), "markArtistsSubscribedOnYtm")
        assertTrue(q.contains("WHEN unfollowedByUserAt IS NOT NULL THEN followedByUserAt"))
        assertTrue(q.contains("spelling.unfollowedByUserAt IS NOT NULL AND spelling.followedByUserAt IS NULL"))
        assertTrue("only an unfollow still owed to the account blocks", q.contains("AND spelling.ytmSyncedAt IS NOT NULL"))
        // Both branches compare spelling.id to an expression of the outer row, so the primary-key index is used.
        assertTrue(q.contains("(substr(artist.id, 1, 2) = 'UC' AND spelling.id = 'MPLA' || artist.id)"))
        assertTrue(q.contains("OR (substr(artist.id, 1, 6) = 'MPLAUC' AND spelling.id = substr(artist.id, 5))"))
        assertFalse("an expression on the indexed column came back", q.contains("'MPLA' || spelling.id"))
    }

    @Test
    fun `the sync writes only the columns it means to, against the current row`() {
        val dao = main("db/DatabaseDao.kt")
        val refresh = query(dao, "refreshSyncedArtistMetadata")
        for (column in listOf("followedByUserAt =", "ytmSyncedAt =", "unfollowedByUserAt =")) {
            assertFalse("the metadata refresh writes $column", refresh.contains(column))
        }
        assertTrue(refresh.contains("bookmarkedAt = CASE WHEN unfollowedByUserAt IS NOT NULL THEN NULL"))
        val channel = query(dao, "setArtistChannelIdIfMissing")
        assertTrue(channel.contains("UPDATE artist SET channelId = :channelId WHERE id = :id AND (channelId IS NULL OR channelId = '')"))

        val sync = main("utils/SyncUtils.kt")
        val page = sync.substringAfter("private suspend fun insertArtistsPage(").substringBefore("\n    }\n")
        assertFalse("a whole-row copy is written back again", page.contains("updateArtists("))
        assertTrue(page.contains("refreshSyncedArtistMetadata(it.id, it.name, it.thumbnailUrl, it.channelId, now)"))
        assertTrue(page.contains("ArtistSyncPolicy.aliasHasPendingUnfollow(row.id, spellings)"))
        assertTrue("read inside the insert's transaction", page.contains("val spellings = artistEntitiesByIdsInTransaction("))

        val upload = main("utils/LibraryUploadSync.kt")
        assertFalse("a stale artist copy is written back again", upload.contains("database.update(artist.copy("))
        assertEquals(2, Regex("""database\.setArtistChannelIdIfMissing\(""").findAll(upload).count())
        assertTrue(upload.contains("if (current == null || !ArtistSyncPolicy.maySubscribe(current)) continue"))
        assertTrue(upload.contains("if (current == null || ArtistSyncPolicy.refuseUnsubscribe(current, LocalDateTime.now()) != null) continue"))
        assertTrue(upload.contains("if (database.isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(artist.id)).first()) {"))
        assertTrue(upload.contains("database.retireSupersededUnfollow(artist.id)"))
        // the re-read sits right before each account call
        val subscribe = upload.substringAfter("if (current == null || !ArtistSyncPolicy.maySubscribe(current)) continue")
        assertTrue(subscribe.indexOf("YouTube.subscribeChannel(channelId, true)") in 0..2000)
        val unsubscribe = upload.substringAfter("database.retireSupersededUnfollow(artist.id)")
        assertTrue(unsubscribe.indexOf("YouTube.subscribeChannel(channelId, false)") in 0..600)
    }
}
