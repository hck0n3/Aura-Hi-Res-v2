package iad1tya.echo.music.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 334 (plan C4): per-tick / per-frame state is read in the SMALL scope that draws it, never at the
 * top of a large screen. A re-hoist compiles and looks fine, but recomposes ~1,800 lines twice a second
 * (Aura player, on every screen while music plays) or a whole EQ column per display frame.
 */
class RecompositionScopeGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(path: String) = File(repoRoot, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun auraPlayerReadsPositionOnlyInsideTheControls() {
        val src = source("ui/newui/AuraPlayer.kt")
        val controlsAt = src.indexOf("val controlsContent: @Composable (Boolean) -> Unit")
        assertTrue(controlsAt > 0)
        val declarations = Regex("val effectivePosition = ").findAll(src).map { it.range.first }.toList()
        assertTrue(declarations.isNotEmpty())
        assertTrue("effectivePosition must be derived inside controlsContent", declarations.all { it > controlsAt })
    }

    @Test
    fun eqScreenNeverReadsTheFftStateAtTopLevel() {
        val src = source("ui/screens/equalizer/axion/AxionEqScreen.kt")
        assertFalse(src.contains("by rememberEqFftMeter("))
        assertTrue(src.contains("EqFftMeterLive("))
        assertTrue(src.contains("derivedStateOf"))
    }
}
