package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fila 355 (dueño 2026-10-08): "no me gusta cómo se ve el corte al final de las portadas". */
class AuraHeroFadeTest {

    @Test
    fun `the hero is opaque at the top and fully transparent at the very bottom`() {
        val stops = heroDissolveStops(HERO_DISSOLVE_FRACTION)
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(1f, stops.first().second.alpha, 0f)
        assertEquals(1f, stops.last().first, 1e-6f)
        assertEquals(0f, stops.last().second.alpha, 1e-6f)
        // Stays opaque until the dissolve starts, then only ever gets more transparent.
        assertEquals(1f - HERO_DISSOLVE_FRACTION, stops[1].first, 1e-6f)
        assertEquals(1f, stops[1].second.alpha, 1e-6f)
        stops.toList().zipWithNext().forEach { (a, b) ->
            assertTrue(b.first >= a.first)
            assertTrue(b.second.alpha <= a.second.alpha + 1e-6f)
        }
    }

    @Test
    fun `every full-bleed hero dissolves instead of ending on a flat colour`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        listOf("AuraArtistScreen.kt", "AuraAlbumScreen.kt", "AuraOnlinePlaylistScreen.kt").forEach { name ->
            val text = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/$name").readText()
            assertTrue("$name: hero must use auraHeroDissolve()", text.contains(".auraHeroDissolve()"))
            assertTrue(
                "$name: the hero scrim must not end on an opaque flat Ground (that was the visible cut)",
                !text.contains("colors = listOf(Color.Transparent, AuraPalette.Ground),"),
            )
        }
    }
}
