package iad1tya.echo.music.ui.newui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 899 (plan phase 7, AGENTS rule 7): decorative loops stop when the music is paused, the app is off screen
 * or the element is not shown. Pure parts by value; the wiring (which compiles and looks right either way) by source.
 */
class LoopsStopOnPauseGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `a stopped loop resumes from where it was and always advances`() {
        assertEquals(28_000, auraLoopRemainingMs(0f, 28_000))
        assertEquals(14_000, auraLoopRemainingMs(0.5f, 28_000))
        assertEquals(1, auraLoopRemainingMs(1f, 28_000))
        assertEquals(1, auraLoopRemainingMs(1.5f, 90_000))
        assertEquals(90_000, auraLoopRemainingMs(-0.2f, 90_000))
    }

    @Test
    fun `the loop helper only runs while the music plays and motion is allowed`() {
        val helper = src("ui/newui/AuraLoopPhase.kt")
        assertTrue(helper.contains("LaunchedEffect(running, periodMs) {\n        if (!running) return@LaunchedEffect"))
        assertTrue(helper.contains("connection?.isEffectivelyPlaying?.collectAsState()"))
        assertTrue(helper.contains("ReducedMotion.musicLoopRuns(shown = true, playing = playing, reduced = rememberReducedMotion())"))
    }

    @Test
    fun `the player ground no longer drifts or spins with the music paused`() {
        val player = src("ui/newui/AuraPlayer.kt")
        val cover = player.substringAfter("private fun AuraCoverGround(").substringBefore("private fun AuraCoverGroundImage(")
        val lobes = player.substringAfter("private fun AuraLobeGround(").substringBefore("private class AuraLobeRaster")
        for ((name, body) in listOf("spin" to cover, "drift" to lobes)) {
            assertFalse("$name: an infinite transition cannot be paused", body.contains("rememberInfiniteTransition("))
            assertTrue(name, body.contains("rememberAuraLoopPhase(running = rememberMusicLoopRuns()"))
        }
        // Row 771: the reduced-motion gate stays out of AuraPlayer.kt itself.
        assertFalse(player.contains("rememberReducedMotion"))
    }

    @Test
    fun `the ambient mode glow stops with the music paused`() {
        val glow = src("ui/screens/ambient/AmbientGlowBackground.kt")
        assertFalse(glow.contains("rememberInfiniteTransition("))
        assertTrue(glow.contains("rememberMusicLoopRuns()"))
    }

    @Test
    fun `the app-wide ambient clock stops with the music paused`() {
        val ambient = src("ui/newui/AuraAmbientMotion.kt")
        assertTrue(ambient.contains("LaunchedEffect(highPerformanceMode, deviceHot, lowTier, playing, lifecycle)"))
        assertTrue(ambient.contains("if (highPerformanceMode || deviceHot || lowTier || !playing) return@LaunchedEffect"))
        assertTrue("off screen it still parks", ambient.contains("lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)"))
    }

    @Test
    fun `paused lyrics stop asking for every display frame`() {
        val lyrics = src("ui/component/Lyrics.kt")
        val ticker = lyrics.substringAfter("val tick = {").substringBefore("LaunchedEffect(isSeeking, lastPreviewTime)")
        assertTrue(ticker.contains("} else if (!playerConnection.player.isPlaying) {\n"))
        assertTrue(ticker.indexOf("else if (!playerConnection.player.isPlaying)") < ticker.indexOf("withFrameMillis { tick() }"))
        val metro = src("ui/component/MetroLyrics.kt")
        assertTrue(metro.contains("if (!playerConnection.player.isPlaying) delay(120)\n                withFrameMillis {"))
    }
}
