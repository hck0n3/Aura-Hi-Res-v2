package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 722 — the platform LoudnessEnhancer was created on the player's audio session and always left
 * disabled. It never shaped the sound, but its creation gated the whole normalization setup: when the effect
 * could not be built, or the session id was still unset, the gains were never written. Source-text checks on
 * purpose: the effect and the audio session only exist on a device.
 */
class LoudnessEnhancerRemovedGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private val playbackDir = File(root, "app/src/main/kotlin/com/music/echo/playback")

    private val service by lazy {
        val f = File(playbackDir, "MusicService.kt")
        assertTrue("playback/MusicService.kt moved: update this test", f.isFile)
        f.readText()
    }

    /** The member body that starts at [signature], up to its closing brace at class-member indentation. */
    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("signature not found: $signature", start >= 0)
        val end = src.indexOf("\n    }\n", start)
        assertTrue("end of body not found: $signature", end > start)
        return src.substring(start, end)
    }

    @Test
    fun `no playback file creates, keeps or imports the platform LoudnessEnhancer`() {
        assertTrue("playback/ moved: update this test", playbackDir.isDirectory)
        // Whole words only: setupLoudnessEnhancer (the normalization entry point) must not count.
        val enhancer = Regex("""\bLoudnessEnhancer\b|\bloudnessEnhancer\b|releaseLoudnessEnhancer""")
        val offenders = playbackDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && enhancer.containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()
        assertTrue("the dead LoudnessEnhancer is back in: $offenders", offenders.isEmpty())
    }

    @Test
    fun `normalization setup still writes the gains and no longer waits for an audio session`() {
        val setup = body(service, "internal fun setupLoudnessEnhancer() {")
        assertTrue(setup.contains("NormalizationGainAudioProcessor.gain = targetGain"))
        assertTrue(setup.contains("TruePeakLimiterAudioProcessor.loudnessMakeup = targetMakeup"))
        assertTrue(setup.contains("NormalizationGainAudioProcessor.gain = 1.0f"))
        assertTrue(setup.contains("isPlayingLoudnessFrozen(currentMediaId, lastNormalizedId)"))
        assertFalse("the gate on the audio session id belonged to the removed effect", setup.contains("audioSessionId"))
    }
}
