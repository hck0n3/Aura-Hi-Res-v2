package iad1tya.echo.music.playback.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 902 — «Modo Fidelidad» wiring, as a source guard (no Android runtime here). Pins what makes the switch
 * real: every bypass is applied from the PLAYBACK side, the equalizer and Superpowered sources (`eq/`,
 * `app/src/main/cpp/`, AGENTS.md rule 6) know nothing about it, and every paused control says so.
 */
class FidelityModeWiringGuardTest {

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun source(path: String): String =
        File(repoRoot, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    private val service by lazy { source("app/src/main/kotlin/com/music/echo/playback/MusicService.kt") }

    @Test
    fun `the preference key name is stable and defaults off`() {
        val keys = source("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertEquals(
            1,
            Regex("""val FidelityModeKey = booleanPreferencesKey\("fidelity_mode"\)""").findAll(keys).count(),
        )
        assertTrue(service.contains("it[iad1tya.echo.music.constants.FidelityModeKey] ?: false"))
        val sound = source("app/src/main/kotlin/com/music/echo/ui/screens/settings/SoundSettings.kt")
        assertTrue(sound.contains("rememberPreference(FidelityModeKey, defaultValue = false)"))
    }

    @Test
    fun `the equalizer is detached, disabled and disabled again after every sink configure`() {
        assertTrue(service.contains("equalizerService.removeAudioProcessor(eq)\n                eq.disable()"))
        assertTrue(service.contains("playerEqProcessors.values.forEach { eq -> equalizerService.addAudioProcessor(eq) }"))
        assertTrue(service.contains("if (fidelityModeHint) eqProcessor.disable() else equalizerService.addAudioProcessor(eqProcessor)"))
        // The processor's own onConfigure restores its last profile: the sink must switch it off again.
        val afterConfigure = Regex(
            """if \(fidelityModeHint\) eqProcessor\.disable\(\)\s+logAudioPath\(inputFormat, delegateFormat""",
        )
        assertTrue("sink re-disable missing", afterConfigure.containsMatchIn(service))
    }

    @Test
    fun `every other stage goes through the gating table`() {
        for (stage in listOf(
            "Stage.SPATIAL", "Stage.TIDAL_SIGNATURE", "Stage.DE_ESSER", "Stage.GLUE_COMPRESSOR",
            "Stage.OUTPUT_DITHER", "Stage.SAFE_VOLUME",
        )) {
            assertTrue(stage, service.contains("FidelityMode.$stage"))
        }
        assertTrue(service.contains("FidelityMode.effectiveStereoWidth(stereoWidthHint, fidelity)"))
        // Speaker protection is KEPT: passed through untouched.
        assertTrue(service.contains("it.applyMastering(compressor, dither, onPhoneSpeaker)"))
        // Crossfade, the manual soft start, the 16-bit float takeover and tempo/pitch.
        assertTrue(service.contains("!(prefs[iad1tya.echo.music.constants.FidelityModeKey] ?: false),"))
        assertTrue(service.contains("if (fadeOnManualChangeHint && !fidelityModeHint &&"))
        assertTrue(service.contains("float32ProcessingHint && !fidelityModeHint &&"))
        assertTrue(service.contains("FidelityMode.mustResetPlaybackParameters(\n                fidelityModeHint,"))
        // setupLoudnessEnhancer's own read of the Safe Volume switch is gated too (unity gain).
        val setup = service.substringAfter("internal fun setupLoudnessEnhancer()").substringBefore("private fun openAudioEffectSession")
        assertTrue(setup.contains("FidelityMode.Stage.SAFE_VOLUME, it, fidelityModeHint"))
    }

    @Test
    fun `the equalizer and Superpowered sources are not touched by the mode`() {
        val zones = listOf("app/src/main/kotlin/com/music/echo/eq", "app/src/main/cpp")
        val offenders = zones.flatMap { zone ->
            File(repoRoot, zone).also { assertTrue("$zone moved", it.isDirectory) }
                .walkTopDown()
                // Sources only: the zone also holds the Superpowered static libraries (.a) and an archive.
                .filter { it.isFile && it.extension in setOf("kt", "cpp", "h", "txt", "sh") }
                .filter { Regex("""fidelity""", RegexOption.IGNORE_CASE).containsMatchIn(it.readText()) }
                .map { it.relativeTo(repoRoot).path }
                .toList()
        }
        assertTrue("Fidelidad must bypass from the playback side only: $offenders", offenders.isEmpty())
    }

    @Test
    fun `every paused control says so instead of silently doing nothing`() {
        val sound = source("app/src/main/kotlin/com/music/echo/ui/screens/settings/SoundSettings.kt")
        assertTrue(sound.contains("Text(stringResource(R.string.fidelity_mode_title))"))
        assertTrue(sound.contains("Text(stringResource(R.string.fidelity_mode_desc))"))
        // Equalizer, Safe Volume, auto headroom, stereo width, compressor, 32-bit processing and dither.
        assertEquals(7, Regex("""FidelityPausedNote\(fidelityMode\)""").findAll(sound).count())
        val player = source("app/src/main/kotlin/com/music/echo/ui/screens/settings/PlayerSettings.kt")
        assertEquals(2, Regex("""FidelityPausedNote\(fidelityMode\)""").findAll(player).count())
        val tempo = source("app/src/main/kotlin/com/music/echo/ui/menu/PlayerMenu.kt")
        assertTrue(tempo.contains("stringResource(R.string.fidelity_mode_tempo_locked)"))
        for (res in listOf("app/src/main/res/values/strings.xml", "app/src/main/res/values-es/strings.xml")) {
            val xml = source(res)
            for (name in listOf(
                "fidelity_mode_title", "fidelity_mode_desc", "fidelity_mode_paused", "fidelity_mode_tempo_locked",
                "fidelity_mode_eq_banner_title", "fidelity_mode_eq_banner_desc",
            )) {
                assertTrue("$res: $name", xml.contains("<string name=\"$name\">"))
            }
            // Decision A1 (row 815): no «YouTube Music» in visible text.
            assertTrue(res, !xml.substringAfter("<string name=\"fidelity_mode_title\">").contains("YouTube Music"))
        }
    }
}
