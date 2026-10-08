package iad1tya.echo.music.ui.newui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fila 360 (dueño 2026-10-08): "cada vez que entre a un álbum, playlist, single… que tenga el color de la
 * portada allí; a menos que esté en Inicio o en el reproductor: ahí el de lo que se está reproduciendo".
 */
class AuraCoverFocusTest {

    @After
    fun tearDown() {
        AuraCoverFocus.playerExpanded = false
    }

    private fun cover(url: String) = AuraCoverFocus.Cover(AuraCoverFocus.keyOf(url), url)

    @Test
    fun `the screen shown last owns the colours and a leaving screen never clears its successor`() {
        val albumA = Any()
        val albumB = Any()
        AuraCoverFocus.enter(cover("a.jpg"), albumA)
        AuraCoverFocus.enter(cover("b.jpg"), albumB) // album A -> album B
        AuraCoverFocus.leave(albumA) // A's composition is disposed after B entered
        assertEquals("b.jpg", AuraCoverFocus.active?.url)
        AuraCoverFocus.leave(albumB) // back to Inicio
        assertNull(AuraCoverFocus.active)
    }

    @Test
    fun `the open player always shows what is playing`() {
        val album = Any()
        AuraCoverFocus.enter(cover("a.jpg"), album)
        AuraCoverFocus.playerExpanded = true
        assertNull(AuraCoverFocus.active)
        AuraCoverFocus.playerExpanded = false
        assertEquals("a.jpg", AuraCoverFocus.active?.url)
        AuraCoverFocus.leave(album)
    }

    @Test
    fun `album, playlists and artist declare their cover, Inicio does not`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        fun src(name: String) = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/$name").readText()
        listOf("AuraAlbumScreen.kt", "AuraOnlinePlaylistScreen.kt", "AuraLocalPlaylistScreen.kt", "AuraArtistScreen.kt")
            .forEach { assertTrue(it, src(it).contains("AuraCoverFocusEffect(screenCover)")) }
        assertTrue(!src("AuraHomeScreen.kt").contains("AuraCoverFocusEffect("))
        assertTrue(src("AuraPlayer.kt").contains("AuraCoverFocus.playerExpanded = state.isExpanded"))
    }
}
