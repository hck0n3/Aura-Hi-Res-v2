package iad1tya.echo.music.ui

import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.ui.newui.PlayerPlaylistRoute
import iad1tya.echo.music.ui.newui.PlayerPlaylistRoute.Door
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 445 and 671. Row 445 (owner 2026-10-18) added «Ir a la lista» to the player's hidden artist row; row 671 (owner
 * 2026-11-22) changed WHEN it shows: only while the song is in at least one of the user's playlists, wherever it plays from.
 */
class PlayerPlaylistRouteTest {
    private val saved = LocalDateTime.of(2026, 11, 1, 12, 0)

    private fun own(id: String) = PlaylistEntity(id = id, name = id, bookmarkedAt = saved)
    private fun ownUnbookmarked(id: String) = PlaylistEntity(id = id, name = id, bookmarkedAt = null)
    private fun savedYouTube(id: String) = PlaylistEntity(id = id, name = id, browseId = "PL$id", bookmarkedAt = saved)
    private fun removedYouTube(id: String) = PlaylistEntity(id = id, name = id, browseId = "PL$id", bookmarkedAt = null)

    @Test
    fun `your lists are exactly the ones the Library shows`() {
        assertTrue(PlayerPlaylistRoute.isUsersPlaylist(own("a")))
        assertFalse("an unbookmarked local row is not in the Library", PlayerPlaylistRoute.isUsersPlaylist(ownUnbookmarked("a")))
        assertTrue(PlayerPlaylistRoute.isUsersPlaylist(savedYouTube("b")))
        assertFalse("a removed YouTube list is a tombstone, not yours", PlayerPlaylistRoute.isUsersPlaylist(removedYouTube("c")))
        for (generated in listOf("AURA_AI_RECS", "LP_LIKED", "LP_DOWNLOADED")) {
            assertFalse("$generated is generated, not yours", PlayerPlaylistRoute.isUsersPlaylist(own(generated)))
        }
        assertEquals(Door.None, PlayerPlaylistRoute.door(listOf(own("AURA_AI_RECS"), ownUnbookmarked("x"))))
    }

    @Test
    fun `no list of yours means no chip`() {
        assertEquals(Door.None, PlayerPlaylistRoute.door(emptyList()))
        assertEquals("only browsed or removed YouTube lists", Door.None, PlayerPlaylistRoute.door(listOf(removedYouTube("c"), removedYouTube("d"))))
    }

    @Test
    fun `one list opens it, several ask which`() {
        assertEquals(Door.Open("local_playlist/a"), PlayerPlaylistRoute.door(listOf(own("a"), removedYouTube("c"))))
        assertEquals("a saved YouTube list opens its library page", Door.Open("local_playlist/b"), PlayerPlaylistRoute.door(listOf(savedYouTube("b"))))
        // Built once: the entity's default timestamps make two calls unequal.
        val a = own("a")
        val b = savedYouTube("b")
        assertEquals(Door.Choose(listOf(a, b)), PlayerPlaylistRoute.door(listOf(a, removedYouTube("c"), b)))
        assertEquals("the same row twice is one list", Door.Open("local_playlist/a"), PlayerPlaylistRoute.door(listOf(own("a"), own("a"))))
    }

    @Test
    fun `the chip follows the song's lists, not the playing context, and is wired to the player`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val player = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt").readText()
        assertFalse("the context rule is gone", player.contains("PlayerPlaylistRoute.of("))
        assertTrue(player.contains("database.playlistsContainingSong(meta.id)"))
        assertTrue(player.contains("when (val door = PlayerPlaylistRoute.door(songPlaylists)) {"))
        assertTrue(player.contains("navigateFromPlayer(door.route)"))
        assertTrue(player.contains("showSongPlaylistChooser = true"))
        assertTrue("the chooser lists only your lists", player.contains("items(items = PlayerPlaylistRoute.usersPlaylists(songPlaylists), key = { it.id })"))
        assertTrue("row 445: the chooser does not stack a page on itself", player.contains("navController.navigateOnce(PlayerPlaylistRoute.route(playlist.id))"))
        assertTrue(player.contains("R.string.go_to_playlist"))
        // Row 445: every door out of the player goes through the one helper that does not stack a page on itself.
        assertTrue(player.contains("navigateFromPlayer(\"album/\$browseId\")"))
        assertTrue(player.contains("navigateFromPlayer(\"artist/\$browseId\")"))
        val menu = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayerMenu.kt").readText()
        assertTrue(menu.contains("navController.navigateOnce(\"artist/\$browseId\")"))
        assertTrue(menu.contains("navController.navigateOnce(\"album/\$browseId\")"))
        assertTrue(File(root, "app/src/main/kotlin/com/music/echo/ui/newui/NavigateOnce.kt").readText().contains("fun NavController.navigateOnce"))
        // A reactive Room read (no polling), and no schema change: a plain JOIN over existing tables.
        val dao = File(root, "app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt").readText()
        assertTrue(dao.contains("fun playlistsContainingSong(songId: String): Flow<List<PlaylistEntity>>"))
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            assertTrue("$dir lacks go_to_playlist", File(root, "app/src/main/res/$dir/strings.xml").readText().contains("name=\"go_to_playlist\""))
        }
    }
}
