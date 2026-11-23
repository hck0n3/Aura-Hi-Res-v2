package iad1tya.echo.music.utils

import iad1tya.echo.music.ui.newui.auraRhythmCaptureAllowed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 771: «Quitar animaciones» and the battery saver stop every decorative, music-driven loop — the ambient
 * background, the player's rhythm pulse (with its Visualizer capture) and the lyrics glow — through ONE helper.
 */
class ReducedMotionTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `either system signal reduces motion`() {
        assertFalse(ReducedMotion.reduce(systemAnimationsOff = false, powerSave = false))
        assertTrue(ReducedMotion.reduce(systemAnimationsOff = true, powerSave = false))
        assertTrue(ReducedMotion.reduce(systemAnimationsOff = false, powerSave = true))
        assertTrue(ReducedMotion.reduce(systemAnimationsOff = true, powerSave = true))
    }

    @Test
    fun `only an animator scale of zero means animations are off`() {
        assertTrue(ReducedMotion.animatorScaleOff(0f))
        assertFalse(ReducedMotion.animatorScaleOff(0.5f))
        assertFalse(ReducedMotion.animatorScaleOff(1f))
        assertFalse(ReducedMotion.animatorScaleOff(10f))
    }

    @Test
    fun `a music loop runs only while shown, playing and motion is allowed`() {
        assertTrue(ReducedMotion.musicLoopRuns(shown = true, playing = true, reduced = false))
        assertFalse(ReducedMotion.musicLoopRuns(shown = false, playing = true, reduced = false))
        assertFalse(ReducedMotion.musicLoopRuns(shown = true, playing = false, reduced = false))
        assertFalse(ReducedMotion.musicLoopRuns(shown = true, playing = true, reduced = true))
    }

    @Test
    fun `the rhythm capture needs the caller gate and no reduced motion`() {
        assertTrue(auraRhythmCaptureAllowed(captureEnabled = true, reducedMotion = false))
        assertFalse(auraRhythmCaptureAllowed(captureEnabled = true, reducedMotion = true))
        assertFalse(auraRhythmCaptureAllowed(captureEnabled = false, reducedMotion = false))
        assertFalse(auraRhythmCaptureAllowed(captureEnabled = false, reducedMotion = true))
    }

    @Test
    fun `the helper is event driven and includes the battery saver`() {
        val helper = src("utils/ReducedMotion.kt")
        assertTrue(helper.contains("val powerSave = rememberPowerSaveMode()"))
        assertTrue(helper.contains("Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)"))
        assertFalse("no polling loop in the helper", helper.contains("delay("))
    }

    @Test
    fun `the rhythm pulse and its capture follow the helper`() {
        val rhythm = src("ui/newui/AuraRhythm.kt")
        assertTrue(rhythm.contains("val capture = auraRhythmCaptureAllowed(captureEnabled, rememberReducedMotion())"))
        assertTrue(rhythm.contains("DisposableEffect(capture, audioSessionId)"))
        assertTrue(rhythm.contains("LaunchedEffect(capture, active, playing)"))
        // The gate lives in AuraRhythm, not in AuraPlayer (P22B owns the player dismiss gesture there).
        assertFalse(src("ui/newui/AuraPlayer.kt").contains("rememberReducedMotion"))
    }

    @Test
    fun `the ambient background reads the shared helper`() {
        val ambient = src("ui/newui/AuraAmbientMotion.kt")
        assertTrue(ambient.contains("ReducedMotion.systemAnimationsOff(context)"))
        assertTrue(ambient.contains("ReducedMotion.powerSave(context)"))
        assertFalse(ambient.contains("ANIMATOR_DURATION_SCALE"))
    }
}
