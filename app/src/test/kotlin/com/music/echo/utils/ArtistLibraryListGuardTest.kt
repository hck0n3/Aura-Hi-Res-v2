package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20 (second report): «quiero exclusivamente a lo que mi cuenta está suscrito … me salen
 * artistas y cuando me meto me sale que no estoy suscrito … es porque tengo una canción agregada de ese
 * artista a mi biblioteca».
 *
 * The Library «Artistas» tab had a «Biblioteca» sub-filter that listed every artist with a song in the
 * library; opening one of those correctly said «Suscribirse». The list is now `followedByUserAt` only
 * (the same column the artist screen's `isSubscribed` reads), and the subscription sync mirrors removals.
 */
class ArtistLibraryListGuardTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 20, 12, 0)
    private val old: LocalDateTime = now.minusDays(2)

    private fun row(id: String, followed: Boolean = true, synced: LocalDateTime? = old) = ArtistEntity(
        id = id,
        name = "A",
        channelId = id.removePrefix("MPLA"),
        followedByUserAt = if (followed) old else null,
        ytmSyncedAt = synced,
    )

    private fun gone(rows: List<ArtistEntity>, remote: Set<String>, complete: Boolean = true) =
        ArtistSyncPolicy.followsGoneFromAccount(rows, remote, complete, now)

    // ---- mirror of removals (pure decision) ------------------------------------------------------

    @Test
    fun `a follow the account no longer lists is cleared after a complete read`() {
        assertEquals(listOf("UCgone"), gone(listOf(row("UCgone"), row("UCkept")), setOf("UCkept")))
    }

    @Test
    fun `UC and MPLAUC spellings of a listed channel are kept`() {
        assertTrue(gone(listOf(row("UCabc")), setOf("MPLAUCabc")).isEmpty())
        assertTrue(gone(listOf(row("MPLAUCabc")), setOf("UCabc")).isEmpty())
    }

    @Test
    fun `an incomplete or empty read proves nothing`() {
        assertTrue(gone(listOf(row("UCgone")), setOf("UCother"), complete = false).isEmpty())
        assertTrue(gone(listOf(row("UCgone")), emptySet()).isEmpty())
    }

    @Test
    fun `follows the account never confirmed are not stale`() {
        // pending SUBSCRIBE / guest / onboarding follow: ytmSyncedAt is null
        assertTrue(gone(listOf(row("UCnew", synced = null)), setOf("UCother")).isEmpty())
    }

    @Test
    fun `a row stamped inside the grace window or after the read began is kept`() {
        assertTrue(gone(listOf(row("UCjust", synced = now.minusMinutes(2))), setOf("UCother")).isEmpty())
        assertTrue(gone(listOf(row("UCfuture", synced = now.plusMinutes(1))), setOf("UCother")).isEmpty())
    }

    @Test
    fun `queued unfollows local and private artists are never this path's business`() {
        assertTrue(gone(listOf(row("UCq").copy(unfollowedByUserAt = old)), setOf("UCother")).isEmpty())
        assertTrue(gone(listOf(row("LAabcdefgh").copy(isLocal = true)), setOf("UCother")).isEmpty())
        assertTrue(gone(listOf(row("FEmusic_library_privately_owned_artist_x")), setOf("UCother")).isEmpty())
        assertTrue(gone(listOf(row("UCnotfollowed", followed = false)), setOf("UCother")).isEmpty())
    }

    @Test
    fun `a row whose channel id is listed is kept even under another id`() {
        assertTrue(gone(listOf(row("UCtopic").copy(channelId = "UCreal")), setOf("UCreal")).isEmpty())
    }

    @Test
    fun `one key per channel`() {
        assertEquals(ArtistSyncPolicy.channelKey("UCabc"), ArtistSyncPolicy.channelKey("MPLAUCabc"))
        assertEquals("LAabcdefgh", ArtistSyncPolicy.channelKey("LAabcdefgh"))
    }

    // ---- wiring (no Android SDK in the unit job, so pinned as source text) -----------------------

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `no library list reads the every-artist-with-a-song query`() {
        // `DatabaseDao.artists(` / `artistsByCreateDateAsc` list artists that merely have a song in the library.
        for (f in listOf(
            "viewmodels/LibraryViewModels.kt",
            "ui/newui/AuraLibraryTabs.kt",
            "ui/screens/library/LibraryArtistsScreen.kt",
            "ui/screens/library/LibraryMixScreen.kt",
            "playback/MediaLibrarySessionCallback.kt",
        )) {
            val s = main(f)
            assertFalse("$f lista artistas por canción", Regex("""database\s*\.artists\(""").containsMatchIn(s))
            assertFalse("$f lista artistas por canción", s.contains("artistsByCreateDateAsc"))
            assertFalse("$f volvió al sub-filtro Biblioteca", s.contains("ArtistFilter.LIBRARY"))
        }
        assertTrue(main("viewmodels/LibraryViewModels.kt").contains("database.artistsBookmarked(sortType, descending)"))
        assertTrue(main("playback/MediaLibrarySessionCallback.kt").contains("artistsBookmarkedByCreateDateAsc()"))
    }

    @Test
    fun `every subscribed-list query is the deliberate-follow column and nothing else`() {
        val dao = main("db/DatabaseDao.kt")
        for (q in listOf("artistsBookmarkedByCreateDateAsc", "artistsBookmarkedByNameAsc", "artistsBookmarkedBySongCountAsc")) {
            val query = dao.substringBefore("fun $q()").substringAfterLast("@Query(")
            assertTrue("$q ya no filtra followedByUserAt", query.contains("WHERE followedByUserAt IS NOT NULL"))
            assertFalse("$q vuelve a leer bookmarkedAt", query.contains("bookmarkedAt IS NOT NULL"))
        }
        val byPlayTime = dao.substringBefore("fun artistsBookmarkedByPlayTimeAsc()").substringAfterLast("@Query(")
        assertTrue(byPlayTime.contains("WHERE followedByUserAt IS NOT NULL"))
    }

    @Test
    fun `the subscription sync mirrors removals only after a complete read`() {
        val sync = main("utils/SyncUtils.kt")
        val block = sync.substringAfter("ArtistSyncPolicy.followsGoneFromAccount(").substringBefore("}.onFailure")
        assertTrue(block.contains("readWasComplete = page.continuation == null"))
        assertTrue(block.contains("clearFollowsGoneFromAccount"))
    }

    @Test
    fun `the empty tab offers a sync and the sub-filter is gone from both screens`() {
        for (f in listOf("ui/newui/AuraLibraryTabs.kt", "ui/screens/library/LibraryArtistsScreen.kt")) {
            assertTrue("$f no usa el estado vacío de suscripciones", main(f).contains("rememberSubscribedArtistsEmpty"))
        }
        assertFalse(main("ui/screens/library/LibraryArtistsScreen.kt").contains("ArtistFilter"))
        assertFalse(main("ui/newui/AuraLibraryTabs.kt").contains("ArtistFilterKey"))
    }
}
