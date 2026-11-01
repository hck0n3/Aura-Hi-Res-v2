package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 518 — owner 2026-10-29: «cualquier apartado que entro, el título me tapa el contenido de las playlist».
 * The Explorar destinations draw their own top bar over the list, and the shell reserves no bar height on a route that is
 * not top-level, so each of them has to reserve it itself.
 */
class ExploreTopBarClearanceGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    @Test
    fun `every Explorar destination that overlays its own bar reserves the bar's height`() {
        for (name in listOf("YouTubeBrowseScreen", "MoodAndGenresScreen", "NewReleaseScreen", "BrowseScreen")) {
            val s = File(root, "app/src/main/kotlin/com/music/echo/ui/screens/$name.kt").readText()
            assertTrue("$name dibuja su barra encima", s.contains("AuraTopAppBar("))
            assertTrue(
                "$name debe reservar AppBarHeight en el contentPadding",
                Regex("""contentPadding = LocalPlayerAwareWindowInsets\.current\s*\.add\([^)]*AppBarHeight[^)]*\)\)\s*\.asPaddingValues\(\)""")
                    .containsMatchIn(s),
            )
        }
    }
}
