package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 791 (owner 2026-11-23): the loudness "measurement" was dead — `NormalizationGainAudioProcessor` is an inert
 * stub that never commits a value — yet MusicService polled it every 5 s of playback. The reader is gone; the
 * stored column is still read for levelling, and the stub's isActive() stays false.
 */
class DeadLoudnessMeasurementGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `the dead measurement reader is not polled from the playback tick`() {
        val svc = main("playback/MusicService.kt")
        assertFalse(svc.contains("maybeApplyMeasuredLoudness"))
        assertFalse(svc.contains("measuredAppliedForId"))
    }

    @Test
    fun `the measurer is still an inert stub and the stored value is still read`() {
        assertTrue(main("eq/audio/NormalizationGainAudioProcessor.kt").contains("override fun isActive(): Boolean = false"))
        assertTrue(main("eq/audio/AudioGain.kt").contains("loudnessDb ?: perceptualLoudnessDb ?: measuredLoudnessDb ?: DEFAULT_UNKNOWN_LOUDNESS_DB"))
    }
}
