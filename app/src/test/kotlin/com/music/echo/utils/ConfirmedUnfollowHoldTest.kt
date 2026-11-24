package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Row 798 (owner decision H15, 2026-11-24; closes the open point of row 716). A confirmed unsubscribe keeps its
 * marker (row 150(d)), and without a way to tell it from a failed one the read-back re-armed it the day the user
 * re-subscribed on YouTube itself, and the upload pass undid his subscription. `unfollowConfirmedAt` tells them
 * apart: a confirmed unfollow is never retried; a failed one still is.
 */
class ConfirmedUnfollowHoldTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 24, 12, 0)

    /** A followed artist the account holds, then unfollowed by a tap. */
    private fun unfollowedSubscription(at: LocalDateTime = now.minusDays(2)) = ArtistEntity(
        id = "UCx",
        name = "Artist",
        followedByUserAt = at.minusDays(10),
        ytmSyncedAt = at.minusDays(10),
    ).localToggleLike().copy(unfollowedByUserAt = at)

    /** What YouTube's accepted call writes back: keyed on the marker the call delivered. */
    private fun confirmedAsDelivered(row: ArtistEntity) = ArtistSyncPolicy.afterConfirmedUnsubscribe(row, row.unfollowedByUserAt!!)

    @Test
    fun `a confirmed unsubscribe is never re-armed when the user re-subscribes on YouTube within the hold`() {
        val confirmed = confirmedAsDelivered(unfollowedSubscription())
        assertTrue(ArtistSyncPolicy.isConfirmedUnfollow(confirmed))
        assertNull(confirmed.ytmSyncedAt)
        assertNotNull("the marker stays (row 150(d))", confirmed.unfollowedByUserAt)

        val readBack = ArtistSyncPolicy.afterRemoteSubscriptionSeen(confirmed, now)
        assertSame("inside the hold the row is left exactly as it was", confirmed, readBack)
        assertEquals(ArtistSyncPolicy.UnsubscribeRefusal.CONFIRMED, ArtistSyncPolicy.refuseUnsubscribe(readBack.copy(ytmSyncedAt = now), now))
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(readBack, now))
        assertFalse(ArtistSyncPolicy.hasPendingUnfollow(readBack.copy(ytmSyncedAt = now)))
    }

    @Test
    fun `after the hold the account's subscription is read back as a follow`() {
        val confirmed = confirmedAsDelivered(unfollowedSubscription(at = now.minusDays(31)))
        val readBack = ArtistSyncPolicy.afterRemoteSubscriptionSeen(confirmed, now)
        assertEquals(now, readBack.followedByUserAt)
        assertEquals(now, readBack.ytmSyncedAt)
        assertNull(readBack.unfollowedByUserAt)
        assertNull(readBack.unfollowConfirmedAt)
        assertFalse(ArtistSyncPolicy.mayUnsubscribe(readBack, now))
        assertFalse("in sync: nothing to push either", ArtistSyncPolicy.maySubscribe(readBack))
    }

    @Test
    fun `a failed unsubscribe is still retried`() {
        val failed = unfollowedSubscription()
        assertFalse(ArtistSyncPolicy.isConfirmedUnfollow(failed))
        val readBack = ArtistSyncPolicy.afterRemoteSubscriptionSeen(failed, now)
        assertTrue("the retry of an unsubscribe that never landed is gone", ArtistSyncPolicy.mayUnsubscribe(readBack, now))
    }

    @Test
    fun `only the unfollow YouTube confirmed counts as confirmed`() {
        val confirmed = confirmedAsDelivered(unfollowedSubscription())
        // A NEWER marker (unfollowed again later) is a new instruction, unconfirmed until its own call succeeds.
        assertFalse(ArtistSyncPolicy.isConfirmedUnfollow(confirmed.copy(unfollowedByUserAt = now)))
        // A re-follow clears it, and so does the next unfollow tap.
        val reFollowed = confirmed.localToggleLike()
        assertNull(reFollowed.unfollowConfirmedAt)
        assertNull(reFollowed.unfollowedByUserAt)
        assertNull(reFollowed.localToggleLike().unfollowConfirmedAt)
        assertNull(confirmed.markFollowedByUser(now).unfollowConfirmedAt)
        // The write-back never confirms a row the user followed again meanwhile.
        val followed = ArtistEntity(id = "UCy", name = "A", followedByUserAt = now)
        assertSame(followed, ArtistSyncPolicy.afterConfirmedUnsubscribe(followed, now))
    }

    @Test
    fun `an unfollow tapped again while the call was in flight is not confirmed by it`() {
        // The upload pass delivered T1; meanwhile the user followed and unfollowed again (T2), and the live call for
        // T2 failed. YouTube's acceptance of T1 must not stamp T2 as confirmed, or T2 would never be retried.
        val delivered = unfollowedSubscription(at = now.minusHours(2))
        val deliveredAt = delivered.unfollowedByUserAt!!
        val tappedAgain = delivered.localToggleLike().localToggleLike().copy(unfollowedByUserAt = now.minusHours(1), ytmSyncedAt = now)
        val writeBack = ArtistSyncPolicy.afterConfirmedUnsubscribe(tappedAgain, deliveredAt)
        assertSame(tappedAgain, writeBack)
        assertFalse(ArtistSyncPolicy.isConfirmedUnfollow(writeBack))
        assertTrue("the newer unfollow is still owed to the account", ArtistSyncPolicy.mayUnsubscribe(writeBack, now))
    }

    @Test
    fun `the hold is the same 30 days as the pending-unfollow window`() {
        assertEquals(30L, ArtistSyncPolicy.PENDING_UNFOLLOW_TTL_DAYS)
        assertEquals(now.minusDays(30), ArtistSyncPolicy.confirmedUnfollowHoldSince(now))
    }

    // ---- wiring (no Android SDK in the unit job, so pinned as source text) -----------------------

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    private fun query(dao: String, fn: String) = dao.substringBefore("fun $fn(").substringAfterLast("@Query(")

    private val confirmedSql = "unfollowConfirmedAt IS NULL OR unfollowConfirmedAt < unfollowedByUserAt"

    @Test
    fun `the SQL writes the confirmation only after a confirmed call and never re-arms it`() {
        val dao = main("db/DatabaseDao.kt")
        val confirm = query(dao, "confirmArtistUnsubscribed")
        assertTrue(confirm.contains("UPDATE artist SET ytmSyncedAt = NULL, unfollowConfirmedAt = unfollowedByUserAt"))
        // Keyed on the marker the call delivered: a newer unfollow is a separate, undelivered instruction.
        assertTrue(confirm.contains("AND unfollowedByUserAt <= :unfollowedAt"))
        assertFalse("an unbounded batch confirmation would stamp a newer, undelivered unfollow", dao.contains("confirmArtistsUnsubscribedBySync"))
        assertTrue(query(dao, "artistsPendingUnsubscribe").contains(confirmedSql))
        assertTrue(query(dao, "countPendingUnsubscribes").contains(confirmedSql))
        val readBack = query(dao, "markArtistsSubscribedOnYtm")
        assertTrue(readBack.contains("AND unfollowConfirmedAt >= unfollowedByUserAt AND unfollowedByUserAt >= :holdSince"))
        assertTrue(readBack.contains("AND unfollowConfirmedAt >= unfollowedByUserAt AND unfollowedByUserAt < :holdSince"))
        assertTrue(dao.contains("suspend fun markArtistsSubscribedOnYtm(ids: List<String>, now: java.time.LocalDateTime, holdSince: java.time.LocalDateTime)"))
    }

    @Test
    fun `the upload pass confirms what YouTube accepted and both read-backs pass the hold`() {
        val upload = main("utils/LibraryUploadSync.kt")
        assertTrue(upload.contains("val deliveredAt = current.unfollowedByUserAt ?: continue"))
        assertTrue(upload.contains("runCatching { database.confirmArtistUnsubscribed(artist.id, deliveredAt) }"))
        assertFalse(upload.contains("confirmArtistsUnsubscribedBySync"))
        assertTrue(upload.contains("database.markArtistsSubscribedOnYtm(it, now, ArtistSyncPolicy.confirmedUnfollowHoldSince(now))"))
        assertTrue(main("utils/SyncUtils.kt").contains("database.markArtistsSubscribedOnYtm(chunk, now, ArtistSyncPolicy.confirmedUnfollowHoldSince(now))"))
        assertTrue(main("viewmodels/BackupRestoreViewModel.kt").contains("unfollowConfirmedAt = existing?.unfollowConfirmedAt,"))
        // The live tap still writes back through the same DAO function (rows 106/107).
        assertTrue(main("utils/ArtistFollowToggle.kt").contains("toggleLike(db::confirmArtistUnsubscribed)"))
    }

    @Test
    fun `the column ships with its migration and exported schema`() {
        val database = main("db/MusicDatabase.kt")
        assertTrue(database.contains("    version = 46,"))
        assertTrue(database.contains("AutoMigration(from = 45, to = 46),"))
        val schema = JSONObject(File(root, "app/schemas/iad1tya.echo.music.db.InternalDatabase/46.json").readText())
            .getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val artist = (0 until entities.length()).map { entities.getJSONObject(it) }.first { it.getString("tableName") == "artist" }
        val fields = artist.getJSONArray("fields")
        val column = (0 until fields.length()).map { fields.getJSONObject(it) }.firstOrNull { it.getString("columnName") == "unfollowConfirmedAt" }
        assertNotNull(column)
        assertEquals("INTEGER", column!!.getString("affinity"))
        assertFalse("nullable: an AutoMigration adds it without a default", column.optBoolean("notNull", false))
        assertTrue(artist.getString("createSql").contains("`unfollowConfirmedAt` INTEGER, PRIMARY KEY(`id`)"))
    }
}
