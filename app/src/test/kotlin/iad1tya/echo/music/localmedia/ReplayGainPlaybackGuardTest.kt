package iad1tya.echo.music.localmedia

import iad1tya.echo.music.eq.audio.dbToLinear
import iad1tya.echo.music.eq.audio.effectiveLoudnessDb
import iad1tya.echo.music.eq.audio.loudnessMakeupDb
import iad1tya.echo.music.eq.audio.normalizationMultiplier
import java.io.File
import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 884: the ReplayGain read by the scan (rows 724-725) reaches the playback volume through the existing
 * levelling path (Volumen seguro), which the user can switch off; nothing new sits in the EQ.
 */
class ReplayGainPlaybackGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    /** The dB the player applies for a stored loudness: attenuation × makeup, as `applyAndFreezeLoudness` multiplies them. */
    private fun appliedDb(loudnessDb: Double): Double {
        val linear = normalizationMultiplier(loudnessDb, enabled = true) * dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true))
        return 20 * log10(linear.toDouble())
    }

    @Test
    fun `a track gain plays 4 dB above its ReplayGain target, within the player's 12 dB limits`() {
        for (gain in listOf(-10.0, -6.5, -4.0, 0.0, 3.2, 7.9)) {
            val loudness = effectiveLoudnessDb(replayGainToLoudnessDb(gain), null)
            assertEquals("gain $gain", (gain + 4).coerceIn(-12.0, 12.0), appliedDb(loudness), 0.01)
        }
        // Clipping protection: the boost never exceeds +12 dB (row 152) and loud masters are cut by at most 12 dB.
        assertEquals(12.0, appliedDb(effectiveLoudnessDb(replayGainToLoudnessDb(20.0), null)), 0.01)
        assertEquals(-12.0, appliedDb(effectiveLoudnessDb(replayGainToLoudnessDb(-30.0), null)), 0.01)
    }

    @Test
    fun `the tag goes from the scan to the volume the user hears`() {
        val scanner = File(root, "app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt").readText()
        assertTrue(scanner.contains("loudnessDb = cachedReplayGainLoudness(replayGain, track.id, track.sizeBytes, track.modifiedEpochSeconds),"))
        assertTrue(scanner.contains("loudnessDb = replayGainFromTags(tags)?.let(::replayGainToLoudnessDb),"))
        val service = File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
        val setup = service.substringAfter("internal fun setupLoudnessEnhancer() {").substringBefore("private fun openAudioEffectSession()")
        assertTrue(setup.contains("dataStore.data.map { it[SafeVolumeEnabledKey] ?: true }.first()"))
        assertTrue(setup.contains("database.format(currentMediaId).first()"))
        assertTrue(setup.contains("format?.loudnessDb, format?.perceptualLoudnessDb, format?.measuredLoudnessDb,"))
        assertTrue(setup.contains("if (safeVol) safeVolumeAppliedGain(targetGain * targetMakeup) else 1f,"))
        assertTrue("switched off = unity gain", setup.contains("playerEqProcessors[player]?.applySafeVolume(false, 1f)"))
    }
}
