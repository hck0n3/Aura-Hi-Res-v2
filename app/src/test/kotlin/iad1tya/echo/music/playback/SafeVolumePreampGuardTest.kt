package iad1tya.echo.music.playback

import java.io.File
import kotlin.math.abs
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
    fun `the native chain ends in a limiter and a clamp that cannot exceed full scale`() {
        // Row 911 replaced Superpowered's limiter (-3 dB threshold, -0.3 dBFS ceiling) and the 0.97 tanh soft clip with
        // a -1 dBTP true-peak limiter and a hard clamp; what this guard protects (row 679) is unchanged: both stages run
        // after Safe Volume and nothing leaves at or above full scale.
        val cpp = File(root, "app/src/main/cpp/superpowered/SuperpoweredBridge.cpp").readText()
        val header = File(root, "app/src/main/cpp/superpowered/AuraTruePeakLimiter.h").readText()
        val process = cpp.substringAfter("// SAFE VOLUME — its own stage")
        val limiter = process.indexOf("processor->truePeakLimiter->process(workBuffer, num_frames, channels);")
        assertTrue("the limiter runs after Safe Volume", limiter > 0)
        assertTrue(header.contains("constexpr float kTpCeilingDbtp = -1.0f;"))
        val clamp = process.indexOf("if (x > kClamp) workBuffer[i] = kClamp;")
        assertTrue("the clamp runs after the limiter", clamp > limiter)
        assertTrue(process.contains("else if (x < -kClamp) workBuffer[i] = -kClamp;"))
        assertTrue("Safe Volume's own gain stays capped at +12 dB", cpp.contains("const float kMaxSafeVolumeGain = 4.0f;"))
        // The clamp value: whatever comes in, the magnitude never reaches 1.0 (0 dBFS).
        assertTrue(header.contains("constexpr float kTpSafetyClamp = 0.989f;"))
        for (x in listOf(0.98f, 1.0f, 1.5f, 4.0f, 40.0f, 1e6f, -1e6f)) {
            val out = x.coerceIn(-0.989f, 0.989f)
            assertTrue("$x -> $out", abs(out) < 1.0f)
        }
    }
}
