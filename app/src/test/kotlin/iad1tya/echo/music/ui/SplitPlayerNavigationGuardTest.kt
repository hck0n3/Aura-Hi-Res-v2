package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 390-391 (owner 2026-10-15, plegable):
 *  · «cuando estoy en el reproductor y le doy ver álbum o artista, que lo muestre en pantalla dividida»;
 *  · «si el reproductor está abierto en pantalla dividida que no se minimice hasta que yo lo haga manualmente,
 *    porque cada vez que toco un botón de la barra de exploración se cierra»;
 *  · «el botón de Inicio de la barra de abajo no me manda al inicio».
 *
 * Every one of those compiles and "looks right" with a plain `collapseSoft()` after a navigate, so the rules are
 * pinned as source guards.
 */
class SplitPlayerNavigationGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun src(path: String): String {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path se movió — actualiza este test", f.isFile)
        return f.readText()
    }

    @Test
    fun `the sheet knows when it is the split pane and stays open when navigating`() {
        val sheet = src("ui/component/BottomSheet.kt")
        assertTrue(sheet.contains("var keepOpenWhenNavigating: Boolean = false"))
        assertTrue(sheet.contains("if (!keepOpenWhenNavigating) collapseSoft()"))
        // Back pops the LEFT pane first; only then it minimizes the player.
        assertTrue(sheet.contains("BackHandler(onBack = { if (state.splitBackHandler?.invoke() != true) state.collapseSoft() })"))
    }

    @Test
    fun `only the chevron and ambient mode still minimize the player outright`() {
        // Chevron (explicit minimize) — the only plain collapse left in the new player.
        assertEquals(1, Regex("""state\.collapseSoft\(\)""").findAll(src("ui/newui/AuraPlayer.kt")).count())
        // Ambient mode is a full-screen route: it keeps its collapse.
        val menu = src("ui/newui/AuraPlayerMenu.kt")
        assertEquals(1, Regex("""playerBottomSheetState\.collapseSoft\(\)""").findAll(menu).count())
        val ambient = menu.indexOf("navigate(\"ambient_mode\")")
        assertTrue(ambient > 0)
        assertTrue(menu.indexOf("playerBottomSheetState.collapseSoft()") in (ambient - 400) until ambient)
        // «Ver álbum» / «Ir al artista» go through the split-aware collapse.
        assertTrue(Regex("""navigate\("album/\${'$'}browseId"\)\s*\n\s*state\.collapseAfterNavigate\(\)""").containsMatchIn(src("ui/newui/AuraPlayer.kt")))
    }

    @Test
    fun `the bottom bar leaves the split player open and Inicio always reaches Inicio`() {
        val main = src("MainActivity.kt")
        assertTrue(main.contains("playerBottomSheetState.keepOpenWhenNavigating = splitActive"))
        assertTrue(main.contains("playerBottomSheetState.splitBackHandler = if (splitActive)"))
        val click = main.substring(main.indexOf("val onNavItemClick: (Screens, Boolean) -> Unit"))
        val body = click.substring(0, click.indexOf("if (!showRail"))
        assertTrue("la barra no debe cerrar el panel dividido", body.contains("playerBottomSheetState.collapseAfterNavigate()"))
        assertTrue(!body.contains("playerBottomSheetState.collapseSoft()"))
        // Inicio takes the ladder the mini player's shortcut already had (navigateAsTab alone restores the stack).
        assertTrue(body.contains("navController.goHomeAlways()"))
        assertTrue(body.contains("navController.navigateToTab(screen.route)"))
        assertTrue(!body.contains("navController.navigateAsTab(screen.route)"))
    }
}
