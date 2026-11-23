package iad1tya.echo.music.playback

import java.io.File
import kotlin.math.abs
import kotlin.math.tanh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 679 (owner 2026-11-23): the EQ preamp no longer shrinks Safe Volume's gain, so raising the preamp raises the
 * volume. What keeps that from hard-clipping is the native chain after both stages — the limiter and the soft clip —
 * which this test pins by reading the source (the native code itself is not edited).
 */
class SafeVolumePreampGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `safe volume's gain does not depend on the preamp anywhere in playback`() {
        val svc = main("playback/MusicService.kt")
        assertTrue(svc.contains("internal fun safeVolumeAppliedGain(baseGain: Float): Float = baseGain"))
        File(root, "app/src/main/kotlin/com/music/echo/playback").listFiles()!!.filter { it.extension == "kt" }.forEach {
            assertFalse("${it.name} trims Safe Volume by the preamp again", it.readText().contains("safeVolumeGainWithEqPreamp("))
        }
    }

    @Test
    fun `a preamp change still reaches the processor live through the profile flow`() {
        val svc = main("playback/MusicService.kt")
        val flow = svc.substringAfter("combine(eqProfileRepository.activeProfile, eqProfileRepository.unsavedProfile) { active, unsaved ->")
            .substringBefore("equalizerService.disable()")
        assertTrue(flow.contains("equalizerService.applyProfile(profile)"))
    }

    @Test
    fun `the native chain ends in a limiter and a soft clip that cannot exceed full scale`() {
        val cpp = File(root, "app/src/main/cpp/superpowered/SuperpoweredBridge.cpp").readText()
        val process = cpp.substringAfter("// SAFE VOLUME — its own stage")
        assertTrue("the limiter runs after Safe Volume", process.indexOf("processor->limiter->process(") > 0)
        assertTrue(cpp.contains("limiter->ceilingDb = -0.3f;"))
        assertTrue(cpp.contains("limiter->thresholdDb = -3.0f;"))
        assertTrue(process.indexOf("float out = 0.97f + std::tanh(absX - 0.97f) * 0.03f;") > process.indexOf("processor->limiter->process("))
        assertTrue("Safe Volume's own gain stays capped at +12 dB", cpp.contains("const float kMaxSafeVolumeGain = 4.0f;"))
        // The same soft-clip formula, in float: whatever comes in, the magnitude never goes past 1.0 (0 dBFS).
        for (x in listOf(0.98f, 1.0f, 1.5f, 4.0f, 40.0f, 1e6f)) {
            val out = 0.97f + tanh(abs(x) - 0.97f) * 0.03f
            assertTrue("$x -> $out", out <= 1.0f)
        }
    }
}
