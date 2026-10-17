package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 371-372 (owner 2026-10-15): «el botón de cast sigue fallando… a veces cambia la canción» and
 * «la barra es alta antes de que empiece y luego delgada».
 *
 * Neither gives an error and both compile, which is how they came back three times:
 *
 *  · the expanded player sheet keeps its collapsed strips (the mini pill, the queue bar) pre-composed,
 *    invisible and ON TOP of the player — so every live button in them answers taps meant for what is
 *    visible underneath (Cast, the queue header);
 *  · a `null` buffered extent makes the DEFAULT timeline draw Material's native, thicker track.
 */
class InvisibleStripTouchGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(name: String): String {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/ui/newui/$name")
        assertTrue("$name se movió — actualiza este test para seguirlo", f.isFile)
        return f.readText()
    }

    @Test
    fun `every button of the mini pill answers only while the pill is the visible mini player`() {
        val shell = source("AuraShell.kt")
        // like (wide only), prev, play, next and home
        assertEquals(
            "los cinco botones de la píldora deben llevar `interactive = gesturesEnabled`",
            5,
            Regex("interactive = gesturesEnabled,").findAll(shell).count() - 1, // minus the glass recipe's own
        )
        assertTrue(shell.contains("if (gesturesEnabled) Modifier.shieldTapsFromAncestorDrags() else Modifier"))
        assertTrue(shell.contains("if (gesturesEnabled) {\n                            Modifier.unstealableClick"))
    }

    @Test
    fun `the interactive glass pointer input is off while the pill is invisible`() {
        val shell = source("AuraShell.kt")
        val glass = shell.indexOf("Modifier.liquidGlassInteractive(")
        assertTrue(glass > 0)
        assertTrue(shell.substring(glass, glass + 900).contains("interactive = gesturesEnabled"))
    }

    @Test
    fun `the queue bar buttons answer only while the queue is collapsed`() {
        val queue = source("AuraQueue.kt")
        assertTrue(queue.contains("interactive = state.isCollapsed,"))
        assertTrue(queue.contains("enabled = enabled && interactive"))
    }

    @Test
    fun `the Cast button is shielded from the sheet drag`() {
        val player = source("AuraPlayer.kt")
        val cast = player.indexOf("CastButton(\n")
        assertTrue(cast > 0)
        assertTrue(player.substring(cast, cast + 3000).contains(".shieldTapsFromAncestorDrags()"))
    }

    @Test
    fun `the timeline never hands the default style a null buffered extent`() {
        val player = source("AuraPlayer.kt")
        val at = player.indexOf("bufferedFraction = if (songFullyCached")
        assertTrue(at > 0)
        val block = player.substring(at, at + 420)
        assertFalse("`null` dibuja el riel nativo, más grueso", Regex("else \\{\\s*null\\s*\\}").containsMatchIn(block))
        assertTrue(block.contains("0f"))
    }

    @Test
    fun `a wide window with the new UI never gets the side rail, it gets the floating bar and the split player`() {
        // Registry row 377: the rail was the wrong answer to "no se parece a la normal".
        val main = File(repoRoot, "app/src/main/kotlin/com/music/echo/MainActivity.kt").readText()
        assertTrue(main.contains("val showRail = !newUiShell &&"))
        assertTrue(main.contains("val auraWideBottom = newUiShell && isWideLayout"))
        assertTrue(main.contains("AuraWideNavCapsule("))
        assertTrue(main.contains("Modifier.align(Alignment.BottomEnd).width(widePaneWidth)"))
        // Registry row 384 (beta4 → beta5): the Box that holds the host and the capsule sits in the Scaffold's
        // bottomBar slot (loose constraints). Without filling the window it collapsed to ~pane width and both
        // `align(BottomEnd)` and `align(BottomCenter)` aligned inside that, not inside the window.
        assertTrue(main.contains("Box(modifier = if (auraWideBottom) Modifier.fillMaxSize() else Modifier) {"))
        assertTrue(main.contains(".then(if (auraWideBottom) Modifier.fillMaxWidth() else Modifier)"))
        // Registry row 386: Spotify's two cards are a layout-free frame drawn over the screen and the pane. Margins on the
        // player sheet itself would misplace its collapsed strip (its position is computed from the whole window).
        assertTrue(main.contains("AuraSplitCardsFrame("))
        val shell = source("AuraShell.kt")
        val capsule = shell.substring(shell.indexOf("fun AuraWideNavCapsule("))
        // Registry row 387: the capsule wears the mini pill's real liquid glass, behind the same switch.
        assertTrue(capsule.contains("liquidGlassInteractive("))
        assertTrue(capsule.contains("glass.interactive && glass.globalEnabled && isGlassSupported()"))
        // The player must size itself by its own pane, not by the window, while it is the right pane.
        val player = source("AuraPlayer.kt")
        assertTrue(player.contains("val isWideLayout = rememberIsWideShell() && !inSplitPane"))
        assertTrue(player.contains("&& !inSplitPane\n"))
    }
}
