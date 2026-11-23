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
 * Row 716 (fresh-eyes review of rows 700/711): one follow tap is one state for the whole channel, and the tap's
 * guard covers the committed write.
 */
class ArtistFollowTapCommitTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 23, 12, 0)
    private val earlier: LocalDateTime = now.minusDays(2)

    private fun row(id: String) = ArtistEntity(id = id, name = "Artist", channelId = id.removePrefix("MPLA"))

    // ---- one queue per channel -----------------------------------------------------------------

    @Test
    fun `every spelling of a channel shares one account-call queue`() {
        assertEquals("UCx", ArtistSyncPolicy.accountCallKey("UCx", "UCx"))
        assertEquals("UCx", ArtistSyncPolicy.accountCallKey("MPLAUCx", null))
        assertEquals("a divergent channelId does not split the queue", "UCx", ArtistSyncPolicy.accountCallKey("UCx", "UCother"))
        assertEquals("UCx", ArtistSyncPolicy.accountCallKey("MPLAUCx", "UCother"))
    }

    @Test
    fun `a row whose id is not a channel is keyed by its channel id, or by itself`() {
        assertEquals("UCx", ArtistSyncPolicy.accountCallKey("LAabcdefgh", "UCx"))
        assertEquals("UCx", ArtistSyncPolicy.accountCallKey("LAabcdefgh", "MPLAUCx"))
        assertEquals("LAabcdefgh", ArtistSyncPolicy.accountCallKey("LAabcdefgh", null))
        assertEquals("LAabcdefgh", ArtistSyncPolicy.accountCallKey("LAabcdefgh", " "))
    }

    // ---- one tap, every spelling -----------------------------------------------------------------

    @Test
    fun `a re-follow follows every spelling and leaves no alias unfollow behind`() {
        // The review's case: UCx was unfollowed (marker + the old ytmSyncedAt = a pending UNSUBSCRIBE), and the
        // user follows again from a screen opened under MPLAUCx.
        val unfollowedAlias = row("UCx").copy(unfollowedByUserAt = earlier, ytmSyncedAt = earlier.minusDays(1))
        val tapped = row("MPLAUCx").copy(unfollowedByUserAt = earlier)
        assertTrue(ArtistSyncPolicy.mayUnsubscribe(unfollowedAlias, now))

        val plan = ArtistSyncPolicy.followTapPlan(listOf(unfollowedAlias, tapped), "MPLAUCx", shownFollowed = false, hasFallback = true)
        assertNotNull(plan)
        assertEquals("both spellings are followed, the tapped one last", listOf("UCx", "MPLAUCx"), plan!!.toggle.map { it.id })
        assertFalse("the tapped row exists", plan.insertFallback)

        val after = plan.toggle.map { it.localToggleLike() }
        after.forEach {
            assertNotNull("${it.id} followed", it.followedByUserAt)
            assertNull("${it.id} keeps no unfollow marker", it.unfollowedByUserAt)
            assertFalse("${it.id}: the upload pass would unsubscribe the account", ArtistSyncPolicy.mayUnsubscribe(it, now))
        }
    }

    @Test
    fun `an unfollow unfollows every spelling that carries the follow, the tapped one last`() {
        val a = row("UCx").copy(followedByUserAt = earlier)
        val b = row("MPLAUCx").copy(followedByUserAt = earlier)
        val plan = ArtistSyncPolicy.followTapPlan(listOf(a, b), "UCx", shownFollowed = true, hasFallback = true)!!
        assertEquals(listOf("MPLAUCx", "UCx"), plan.toggle.map { it.id })
        assertFalse(plan.insertFallback)
        plan.toggle.map { it.localToggleLike() }.forEach { assertNull(it.followedByUserAt) }
    }

    @Test
    fun `a follow with no stored row creates the tapped id's row`() {
        val plan = ArtistSyncPolicy.followTapPlan(emptyList(), "UCx", shownFollowed = false, hasFallback = true)!!
        assertTrue(plan.toggle.isEmpty())
        assertTrue(plan.insertFallback)
        assertFalse(ArtistSyncPolicy.followTapPlan(emptyList(), "UCx", shownFollowed = false, hasFallback = false)!!.insertFallback)
    }

    @Test
    fun `a tap against a state the button no longer shows is dropped`() {
        val followed = row("UCx").copy(followedByUserAt = earlier)
        assertNull("shown «Suscribirse», stored followed", ArtistSyncPolicy.followTapPlan(listOf(followed), "UCx", false, true))
        assertNull("shown «Suscrito», stored not followed", ArtistSyncPolicy.followTapPlan(listOf(row("UCx")), "UCx", true, true))
        assertNotNull("no shown state: the menus' plain toggle", ArtistSyncPolicy.followTapPlan(listOf(followed), "UCx", null, true))
    }

    // ---- wiring (no Android SDK in the unit job, so pinned as source text) -----------------------

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the shared tap waits for its committed write and reads inside it`() {
        val toggle = main("utils/ArtistFollowToggle.kt")
        assertTrue("suspends until committed", toggle.contains("db.withTransaction {"))
        assertFalse("the fire-and-forget transaction came back", toggle.contains("db.transaction {"))
        assertTrue(
            "rows read inside the transaction",
            toggle.contains("val rows = artistEntitiesByIdsInTransaction(ArtistSyncPolicy.aliasIds(artistId))"),
        )
        assertFalse("a read before the transaction came back", toggle.contains("getArtistEntitiesByIds("))
        assertTrue(toggle.contains("ArtistSyncPolicy.followTapPlan(rows, artistId, shownFollowed, hasFallback = fallback != null)"))
        val dao = main("db/DatabaseDao.kt")
        assertTrue(dao.contains("fun artistEntitiesByIdsInTransaction(ids: List<String>): List<ArtistEntity>"))
    }

    @Test
    fun `the library artist menu is the shared alias-aware tap, one at a time`() {
        val menu = main("ui/menu/ArtistMenu.kt")
        assertTrue(menu.contains("database.isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(artist.id))"))
        assertTrue(menu.contains("if (followInFlight.compareAndSet(false, true)) {"))
        assertTrue(menu.contains("database.toggleArtistFollow(artist.id, artist.artist, shownFollowed = shownFollowed)"))
        assertTrue(menu.contains("context.applicationScope.launch(Dispatchers.IO) {"))
        assertFalse("its own alias-blind toggle came back", menu.contains(".toggleLike("))
        assertFalse(menu.contains("artist.artist.followedByUserAt != null) stringResource"))
    }

    @Test
    fun `every artist follow tap in the app goes through the shared toggle`() {
        val offenders = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "ArtistFollowToggle.kt" && it.name != "ArtistEntity.kt" }
            .filter { "toggleLike(database::confirmArtistUnsubscribed)" in it.readText() || "toggleLike(env.database::confirmArtistUnsubscribed)" in it.readText() || "toggleLike(db::confirmArtistUnsubscribed)" in it.readText() }
            .map { it.name }
            .toList()
        assertTrue("artist toggles outside toggleArtistFollow: $offenders", offenders.isEmpty())
    }
}
