package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.newui.PlayerPlaylistRoute
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 445 (owner 2026-10-18): while a song plays from a playlist, the player's hidden artist row also offers
 * «Ir a la lista». Only a real playlist context leads somewhere.
 */
class PlayerPlaylistRouteTest {
    @Test
    fun `your own and followed playlists lead to their pages`() {
        assertEquals("local_playlist/abc123", PlayerPlaylistRoute.of("PL:abc123"))
        assertEquals("online_playlist/PLxyz", PlayerPlaylistRoute.of("OL:PLxyz"))
    }

    @Test
    fun `albums, requests, radios and nothing have no playlist page`() {
        assertNull(PlayerPlaylistRoute.of(null))
        assertNull(PlayerPlaylistRoute.of(""))
        assertNull(PlayerPlaylistRoute.of("AL:MPREb_x"))
        assertNull(PlayerPlaylistRoute.of("MR:whatever"))
        assertNull(PlayerPlaylistRoute.of("PL:"))
        assertNull(PlayerPlaylistRoute.of("OL:"))
        assertNull(PlayerPlaylistRoute.of("LIBRARY"))
    }

    @Test
    fun `the chip is wired to the player and its text exists in the main locales`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val player = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt").readText()
        assertTrue(player.contains("PlayerPlaylistRoute.of(playerConnection.shuffleContextId)"))
        assertTrue(player.contains("R.string.go_to_playlist"))
        // Every door out of the player goes through the one helper that does not stack a page on itself.
        assertTrue(player.contains("navigateFromPlayer(\"album/\$browseId\")"))
        assertTrue(player.contains("navigateFromPlayer(\"artist/\$browseId\")"))
        val menu = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayerMenu.kt").readText()
        assertTrue(menu.contains("navController.navigateOnce(\"artist/\$browseId\")"))
        assertTrue(menu.contains("navController.navigateOnce(\"album/\$browseId\")"))
        assertTrue(File(root, "app/src/main/kotlin/com/music/echo/ui/newui/NavigateOnce.kt").readText().contains("fun NavController.navigateOnce"))
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            assertTrue("$dir lacks go_to_playlist", File(root, "app/src/main/res/$dir/strings.xml").readText().contains("name=\"go_to_playlist\""))
        }
    }
}
