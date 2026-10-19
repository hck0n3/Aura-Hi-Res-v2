package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19 (fifth round): «el carrusel está muy pequeño en modo plegable» and «el diseño de Inicio lo sigo viendo muy
 * básico». The numbers behind the wide carousel, the first-name greeting, the quick-access columns, and source guards for the
 * wiring (there is no Android SDK in the unit job, so what cannot be rendered is pinned as text).
 */
class AuraHomeLayoutTest {

    @Test
    fun `the unfolded carousel card is bigger than the phone card and follows the pane`() {
        val phoneWidth = 340f
        val phoneHeight = 260f
        for ((pane, window) in listOf(841 to 700, 904 to 760, 1100 to 800)) {
            val size = auraWideHeroCardSize(pane.dp, window)
            assertTrue("at $pane dp the card is ${size.width.value} wide", size.width.value > phoneWidth)
            assertTrue("at $pane dp the card is ${size.height.value} high", size.height.value > phoneHeight)
        }
        val narrow = auraWideHeroCardSize(840.dp, 700)
        val wider = auraWideHeroCardSize(1000.dp, 700)
        assertTrue("the card grows with the pane", wider.width.value > narrow.width.value)
    }

    @Test
    fun `the card stays inside its bounds on every window`() {
        for (pane in listOf(560, 645, 700, 841, 1000, 1280, 1600)) {
            for (window in listOf(480, 600, 700, 900)) {
                val size = auraWideHeroCardSize(pane.dp, window)
                assertTrue("width $pane/$window", size.width.value in 320f..520f)
                assertTrue("height $pane/$window = ${size.height.value}", size.height.value in 240f..400f)
                val cap = maxOf(window * AURA_WIDE_HERO_MAX_HEIGHT_FRACTION, 240f)
                assertTrue("a short window never gets a card taller than ${cap}: ${size.height.value}", size.height.value <= cap + 0.01f)
            }
        }
    }

    @Test
    fun `the greeting uses the first name only when there is a real one`() {
        assertEquals("Roberto", auraFirstName("Roberto Pérez"))
        assertEquals("María", auraFirstName("  María  "))
        assertNull(auraFirstName(null))
        assertNull(auraFirstName(""))
        assertNull(auraFirstName("   "))
        assertNull(auraFirstName("Guest"))
        assertNull(auraFirstName("guest"))
        assertNull("an e-mail address is not a name", auraFirstName("alguien@correo.com"))
    }

    @Test
    fun `quick access is two columns on a phone and one row of four on a wide pane`() {
        assertEquals(2, auraQuickAccessColumns(360.dp - 42.dp))
        assertEquals(2, auraQuickAccessColumns(430.dp - 42.dp))
        assertEquals(4, auraQuickAccessColumns(841.dp - 42.dp))
        assertEquals(4, auraQuickAccessColumns(1100.dp))
    }

    // ── source guards ─────────────────────────────────────────────────────────────────────────────────────────────

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun res(path: String) = File(root, "app/src/main/res/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `Inicio opens on the quick access and the hero, before the podcasts and the recents shelf`() {
        val home = main("ui/newui/AuraHomeScreen.kt")
        val quick = home.indexOf("key = \"aura_home_quick_access\"")
        val hero = home.indexOf("key = \"aura_quick_picks\"")
        val podcasts = home.indexOf("key = \"aura_home_podcasts\"")
        val recent = home.indexOf("key = \"aura_home_recent\"")
        assertTrue(quick > 0 && hero > 0 && podcasts > 0 && recent > 0)
        assertTrue("el acceso rápido va primero", quick < hero)
        assertTrue("el héroe va antes que los podcasts y los recientes", hero < podcasts && hero < recent)
        assertTrue(home.contains("AuraQuickAccess("))
    }

    @Test
    fun `quick access reaches the user's own places and the shuffle button cancels while it loads`() {
        val layout = main("ui/newui/AuraHomeLayout.kt")
        for (route in listOf("auto_playlist/liked", "auto_playlist/downloaded", "\"history\"")) {
            assertTrue("falta $route", layout.contains(route))
        }
        assertTrue(layout.contains("AuraIcons.Shuffle"))
        val home = main("ui/newui/AuraHomeScreen.kt")
        assertTrue("pulsarlo mientras carga cancela la elección (UI_INVENTORY §10)", home.contains("running.cancel()"))
        assertTrue(home.contains("viewModel.getRandomItem()"))
        // The counts are read in the block, so a finished download does not recompose the whole Home.
        assertTrue(layout.contains("LocalDownloadUtil.current.downloads.collectAsState()"))
        assertFalse(home.contains("LocalDownloadUtil"))
    }

    @Test
    fun `song rows of Inicio go through the two-column helper and the greeting carries the first name`() {
        val home = main("ui/newui/AuraHomeScreen.kt")
        assertEquals("los cuatro bloques de filas de canciones", 4, Regex("AuraRowsInColumns\\(").findAll(home).count())
        assertTrue(main("ui/newui/AuraHomeLayout.kt").contains("AURA_ROWS_TWO_COLUMNS_MIN_WIDTH"))
        assertTrue(home.contains("auraGreetingFor(accountName)"))
    }

    @Test
    fun `the new strings exist in English and Spanish`() {
        assertTrue(res("values/echo_strings.xml").contains("name=\"home_quick_surprise_hint\""))
        assertTrue(res("values-es/echo_strings.xml").contains("name=\"home_quick_surprise_hint\""))
    }
}
