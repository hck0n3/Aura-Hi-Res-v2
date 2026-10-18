package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 424 (owner 2026-10-18, fifth report: «el botón de Cast hay que darle varias veces… haz más grande el
 * área del touch»). The button's hit area is [CAST_TOUCH_TARGET] (64 dp). Compose does not hit-test past a node's
 * bounds, so the inner button and BOTH players' outer boxes must use that one constant — a stale `size(48.dp)` on any of
 * them silently brings back "only the centre answers".
 */
class CastTouchTargetTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the button and both players share one touch target of at least 64 dp`() {
        val gms = src("app/src/gms/kotlin/com/music/echo/ui/component/CastButton.kt")
        val foss = src("app/src/foss/kotlin/com/music/echo/ui/component/CastButton.kt")
        assertTrue(gms.contains("val CAST_TOUCH_TARGET = 64.dp") && foss.contains("val CAST_TOUCH_TARGET = 64.dp"))
        assertTrue("el botón usa la constante", gms.contains(".size(CAST_TOUCH_TARGET)"))
        for (p in listOf("app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt", "app/src/main/kotlin/com/music/echo/ui/player/Player.kt")) {
            val s = src(p)
            val at = s.indexOf("CastButton(\n")
            assertTrue("$p: no encuentro el uso", at > 0)
            val block = s.substring(at, at + 2500).substringBefore("tintColor")
            assertTrue("$p: la caja exterior debe medir CAST_TOUCH_TARGET", block.contains(".size(CAST_TOUCH_TARGET)"))
            assertTrue("$p: ya no debe quedar un 48 dp", !block.contains(".size(48.dp)"))
        }
    }

    @Test
    fun `the title reserves room for the bigger target so long names never run under it`() {
        assertTrue(
            src("app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt")
                .contains("end = if (showInlineLyrics) 4.dp else CAST_TOUCH_TARGET"),
        )
    }
}
