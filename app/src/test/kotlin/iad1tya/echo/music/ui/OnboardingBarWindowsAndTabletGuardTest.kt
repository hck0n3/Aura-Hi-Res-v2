package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.newui.auraLibraryCellDp
import java.io.File
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 494-497 (owner 2026-10-21): the bottom bar must not show over the first-run onboarding, every floating
 * window is the same translucent plate as the ⋯ menus, the Biblioteca grids grow on a tablet, and an artist's page on a wide
 * pane uses the album / playlist top.
 */
class OnboardingBarWindowsAndTabletGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val mainDir get() = File(root, "app/src/main/kotlin/com/music/echo")

    private fun main(path: String) =
        File(mainDir, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    // ── 494 · the bar stays away from the first-run onboarding ─────────────────────────────────────────────────────────

    @Test
    fun `the navigation bar is hidden on every onboarding route, phone and wide`() {
        val src = main("MainActivity.kt")
        assertTrue(src.contains("currentRoute?.startsWith(\"onboarding_\") == true"))
        // The Spotify / YouTube Music import screens reached from the onboarding carry the flag.
        assertTrue(src.contains("navBackStackEntry?.arguments?.getBoolean(\"onboarding\") == true"))
        assertTrue("el teléfono", src.contains("val shouldShowNavigationBar = remember(currentRoute, navigationItemRoutes, inOnboarding) {\n                    !inOnboarding && ("))
        assertTrue("la ventana ancha", src.contains("val navBarShown = if (auraWideBottom) {\n                    !inOnboarding && currentRoute != \"update\""))
        // The routes it relies on are the real ones.
        val nav = main("ui/screens/NavigationBuilder.kt")
        listOf("onboarding_artists", "onboarding_genres", "onboarding_spotify", "onboarding_youtube").forEach {
            assertTrue(it, nav.contains("composable(\"$it\")"))
        }
    }

    // ── 495 · one floating material ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `no floating window container is painted with the opaque frost fill`() {
        val offenders = mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "AuraPalette.kt" }
            .flatMap { f ->
                f.readLines().withIndex()
                    .filter { (_, line) ->
                        val code = line.substringBefore("//")
                        (code.contains("containerColor") && code.contains("FrostFill")) ||
                            code.contains("background(AuraPalette.FrostFill")
                    }
                    .map { (i, _) -> "${f.name}:${i + 1}" }
            }.toList()
        assertEquals("ventanas con la losa opaca: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `dropdown menus wear the same plate and hairline as the sheets`() {
        listOf(
            "ui/newui/AuraStatsScreen.kt", "ui/newui/AuraLibraryTabs.kt", "ui/newui/AuraStyleFollow.kt",
            "ui/newui/AuraLibraryScreen.kt", "ui/newui/AuraCollectionScreens.kt",
        ).forEach {
            val s = main(it)
            assertTrue(it, s.contains("containerColor = AuraPalette.FloatingWindowPlate,"))
            assertTrue(it, s.contains("border = AuraMenuBorder,"))
        }
        listOf("ui/component/SortHeader.kt", "ui/component/ChipsRow.kt").forEach {
            assertTrue(it, main(it).contains("AuraMenuBorder"))
        }
        assertTrue(main("ui/newui/AuraFloatingChrome.kt").contains(".background(AuraPalette.FloatingWindowPlate, shape)"))
    }

    @Test
    fun `the Material sheet takes the sheet corner and the hairline of the glass sheets`() {
        val s = main("ui/component/AuraMaterialDialogs.kt")
        val sheet = s.substringAfter("fun AuraModalBottomSheet(")
        assertTrue(sheet.contains("iad1tya.echo.music.ui.newui.AuraShapes.Sheet"))
        assertTrue(sheet.contains("modifier.border(1.dp, AuraPalette.SurfaceLine, resolvedShape)"))
    }

    // ── 496 · the Biblioteca grids on a tablet ─────────────────────────────────────────────────────────────────────────

    /** What `GridCells.Adaptive(minSize)` does with a pane [widthDp] wide (gutters 21 dp both sides, 11 dp between). */
    private fun columns(widthDp: Float, big: Boolean): Int {
        val min = auraLibraryCellDp(widthDp, big)
        return floor((widthDp - 42f + 11f) / (min + 11f)).toInt().coerceAtLeast(1)
    }

    @Test
    fun `a phone and a Fold keep the sizes they had`() {
        assertEquals(150f, auraLibraryCellDp(411f, true), 0f)
        assertEquals(104f, auraLibraryCellDp(411f, false), 0f)
        assertEquals(176f, auraLibraryCellDp(673f, true), 0f)
        assertEquals(132f, auraLibraryCellDp(840f, false), 0f)
    }

    @Test
    fun `a tablet shows few big covers instead of seven or eight small ones`() {
        // A Galaxy Tab S12 Ultra: ~924 dp portrait, ~1480 dp landscape.
        assertEquals(3, columns(924f, true))
        assertEquals(5, columns(1480f, true))
        assertEquals(4, columns(924f, false))
        assertEquals(6, columns(1480f, false))
        // The card is bigger than the 176 dp it stopped at, in every tablet width.
        for (w in listOf(900f, 924f, 1000f, 1100f, 1300f, 1480f, 1700f)) {
            assertTrue("$w", auraLibraryCellDp(w, true) > 176f)
            assertTrue("$w", auraLibraryCellDp(w, false) > 132f)
            assertTrue("Grande > Pequeño en $w", auraLibraryCellDp(w, true) > auraLibraryCellDp(w, false))
        }
    }

    @Test
    fun `the cell is measured on the content pane, not on the whole screen`() {
        val s = main("ui/newui/AuraLibraryViewType.kt")
        assertTrue(s.contains("iad1tya.echo.music.ui.utils.LocalContentPaneWidth.current?.value"))
    }

    // ── 497 · the artist's top on a wide pane ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `an artist on a wide pane gets the album top, a narrow pane keeps the full-bleed hero`() {
        val s = main("ui/newui/AuraArtistScreen.kt")
        assertTrue(s.contains("val wideTop = maxWidth >= AURA_WIDE_HERO_MIN_WIDTH"))
        assertTrue(s.contains("AuraWideTop("))
        assertTrue("el héroe a sangre sigue para el panel estrecho", s.contains("AuraArtistHero("))
        assertTrue("el recorte en banner de 320 dp ya no existe", !s.contains("isWideHero") && !s.contains("Modifier.height(320.dp)"))
        // Nothing the hero had is lost: the background clip plays over the cover, the header is the same one in both placements.
        assertTrue(s.contains("coverOverlay = if (showArtistBackgroundVideo && clip != null)"))
        assertTrue(s.contains("artistHeader(true)") && s.contains("artistHeader(false)"))
        assertFalse(s.contains("aura_artist_hero\", contentType = \"aura_artist_hero\") {\n                    AuraArtistHero("))
    }
}
