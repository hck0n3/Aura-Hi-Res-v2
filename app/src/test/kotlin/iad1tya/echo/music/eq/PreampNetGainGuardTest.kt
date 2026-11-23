package iad1tya.echo.music.eq

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 790 (owner 2026-11-23): the preamp the EQ screen shows is the gain the native chain applies. The front gain
 * used to be multiplied by a hidden 0.7943 (-2 dB), in playback and in the "Verify equalizer" headroom test alike.
 */
class PreampNetGainGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private val cpp: String by lazy {
        File(root, "app/src/main/cpp/superpowered/SuperpoweredBridge.cpp").readText()
    }

    @Test
    fun `playback applies the preamp as shown`() {
        assertTrue(cpp.contains("float frontGain = processor->activePreampMultiplier;"))
        assertTrue(cpp.contains("processor->staging.preampMultiplier = powf(10.0f, preampDb / 20.0f);"))
    }

    @Test
    fun `the headroom test measures the same front gain as playback`() {
        assertTrue(cpp.contains("const float frontGain = (float) std::pow(10.0, preampDb / 20.0);"))
    }

    @Test
    fun `no hidden trim is left on the front gain`() {
        assertFalse("hidden -2 dB trim is back", cpp.contains("0.7943f"))
    }

    @Test
    fun `a long mono block is limited in slices instead of skipping the limiter`() {
        assertFalse(cpp.contains("requiredSize * 2 <= MAX_BUFFER_SIZE"))
        val mono = cpp.substringAfter("} else if (processor->limiter && processor->limiter->enabled && channels == 1) {")
            .substringBefore("// Soft-clip final safety net")
        assertTrue(mono.contains("const int maxSliceFrames = MAX_BUFFER_SIZE / 2;"))
        assertTrue(mono.contains("processor->limiter->process(scratch, scratch, (unsigned int) n);"))
    }
}
