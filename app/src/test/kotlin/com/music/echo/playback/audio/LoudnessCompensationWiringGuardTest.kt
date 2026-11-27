package iad1tya.echo.music.playback.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 918 — «Graves y agudos a volumen bajo», wiring as a source guard (no Android runtime here): the switch reaches the
 * samples (no placebo), «Modo Fidelidad» and the offload gate know about it, the volume is followed by events only
 * (rule 7), and the switch, the service and the native chain share one set of defaults.
 */
class LoudnessCompensationWiringGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun source(path: String): String =
        File(root, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    private val service by lazy { source("app/src/main/kotlin/com/music/echo/playback/MusicService.kt") }
    private val processor by lazy { source("app/src/main/kotlin/com/music/echo/eq/audio/CustomEqualizerAudioProcessor.kt") }
    private val bridge by lazy { source("app/src/main/cpp/superpowered/SuperpoweredBridge.cpp") }
    private val controller by lazy { source("app/src/main/kotlin/com/music/echo/playback/audio/LowVolumeLoudness.kt") }

    @Test
    fun `keys are stable and the switch is off by default everywhere`() {
        val keys = source("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertTrue(keys.contains("val LoudnessCompensationEnabledKey = booleanPreferencesKey(\"loudness_compensation_enabled\")"))
        assertTrue(keys.contains("const val LoudnessCompensationDefault = false"))
        assertTrue(keys.contains("val LoudnessCompensationIntensityKey = floatPreferencesKey(\"loudness_compensation_intensity\")"))
        assertTrue(keys.contains("const val LoudnessCompensationIntensityDefault = 1f"))
        // Every read pairs the key with its one default constant.
        val mainRoot = File(root, "app/src/main/kotlin")
        mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "PreferenceKeys.kt" }.forEach { f ->
            val text = f.readText()
            Regex("""LoudnessCompensationEnabledKey(\]|,)""").findAll(text).forEach { m ->
                val window = text.substring(m.range.first, minOf(text.length, m.range.last + 160))
                assertTrue("${f.name}: $window", window.contains("LoudnessCompensationDefault"))
            }
            Regex("""LoudnessCompensationIntensityKey(\]|,)""").findAll(text).forEach { m ->
                val window = text.substring(m.range.first, minOf(text.length, m.range.last + 160))
                assertTrue("${f.name}: $window", window.contains("LoudnessCompensationIntensityDefault"))
            }
        }
        val sound = source("app/src/main/kotlin/com/music/echo/ui/screens/settings/SoundSettings.kt")
        assertTrue(sound.contains("defaultValue = iad1tya.echo.music.constants.LoudnessCompensationDefault,"))
        assertTrue(sound.contains("defaultValue = iad1tya.echo.music.constants.LoudnessCompensationIntensityDefault,"))
        val item = sound.substringAfter("title = { Text(stringResource(R.string.loudness_comp_title)) },").substringBefore("onClick =")
        assertTrue("the switch says when «Modo Fidelidad» pauses it", item.contains("FidelityPausedNote(fidelityMode)"))
    }

    @Test
    fun `the service gates it through the Fidelidad table and keeps offload off while it is on`() {
        val apply = service.substringAfter("private fun applyLoudnessCompensationFromPrefs() {").substringBefore("\n    }\n")
        assertTrue(apply.contains("FidelityMode.Stage.LOUDNESS_COMPENSATION, loudnessCompHint, fidelityModeHint,"))
        assertTrue(apply.contains("lowVolumeLoudness.update(on, loudnessIntensityHint)"))
        val fidelity = service.substringAfter("private fun applyFidelityMode(on: Boolean) {").substringBefore("setupLoudnessEnhancer()")
        assertTrue(fidelity.contains("applyLoudnessCompensationFromPrefs()"))
        assertTrue(FidelityMode.isBypassed(FidelityMode.Stage.LOUDNESS_COMPENSATION, fidelityOn = true))
        // Offload hands the still-encoded stream to the DSP: the stage would be a placebo.
        val gate = service.substringBefore("AudioOffloadGate.allowOffload(").takeLast(4000)
        assertTrue(gate.contains("(it[iad1tya.echo.music.constants.LoudnessCompensationEnabledKey] ?: iad1tya.echo.music.constants.LoudnessCompensationDefault)"))
        // Output changes, new players and the end of the service.
        assertEquals(2, Regex("""lowVolumeLoudness\.onOutputChanged\(\)""").findAll(service).count())
        assertTrue(service.contains("eqProcessor.applyLoudnessCompensation(loudnessGains)"))
        assertTrue(service.contains("playerEqProcessors.values.forEach { it.applyLoudnessCompensation(gains) }"))
        assertTrue(service.contains("if (lowVolumeLoudnessDelegate.isInitialized()) lowVolumeLoudness.release()"))
    }

    @Test
    fun `the volume is followed by events only`() {
        assertTrue(controller.contains("const val VOLUME_CHANGED_ACTION = \"android.media.VOLUME_CHANGED_ACTION\""))
        // Registered only while the switch is on, removed when it goes off.
        val update = controller.substringAfter("fun update(on: Boolean, intensity: Float) {").substringBefore("\n    }\n")
        assertTrue(update.contains("if (on && !registered) {"))
        assertTrue(update.contains("} else if (!on && registered) {"))
        for (poll in listOf("postDelayed", "Timer(", "while (", "delay(", "scheduleAtFixedRate")) {
            assertFalse("no polling: $poll", controller.contains(poll))
        }
    }

    @Test
    fun `the processor publishes it from pushToneStages with the automatic headroom's trim`() {
        assertTrue(processor.contains("private external fun setLoudnessCompensation(ptr: Long, bassDb: Float, trebleDb: Float, trimDb: Float)"))
        assertEquals(1, Regex("""(?<!fun )setLoudnessCompensation\(\s*ptr""").findAll(processor).count())
        val push = processor.substringAfter("private fun pushToneStages(ptr: Long) {").substringBefore("\n    }\n")
        assertTrue(push.contains("LoudnessCompensation.trimDb(loudness, autoHeadroom).toFloat(),"))
        // The trim follows the headroom switch even with the equalizer off.
        val headroom = processor.substringAfter("fun applyAutoHeadroom(enabled: Boolean) {").substringBefore("\n    }\n")
        assertTrue(headroom.contains("if (isInitialized && ptr != 0L) pushToneStages(ptr)"))
    }

    @Test
    fun `the native chain runs the stage whenever it is engaged, outside the equalizer and before the limiter`() {
        assertTrue(bridge.contains("#include \"AuraLoudnessCompensation.h\""))
        assertTrue(
            bridge.contains(
                "Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setLoudnessCompensation(" +
                    "JNIEnv *env, jobject thiz, jlong ptr, jfloat bassDb, jfloat trebleDb, jfloat trimDb)",
            ),
        )
        // The engaged stage alone makes the chain run (one more «or» of runChain; row 920 adds the psychoacoustic bass after it).
        val runChain = bridge.substringAfter("bool runChain = runEq ||").substringBefore(";")
        assertTrue(runChain.contains("(processor && processor->loudness && processor->loudness->engaged())"))
        assertTrue(bridge.contains("if (loudness) loudness->setTarget(s.loudnessBass, s.loudnessTreble, s.loudnessTrim);"))
        val process = bridge.substringAfter("bool runChain = runEq ||")
        val call = process.indexOf("processor->loudness->process(workBuffer, num_frames, channels);")
        assertTrue(call > 0)
        assertTrue("after the equalizer block", call > process.indexOf("processor->deEsser->process(workBuffer, num_frames, channels);"))
        assertTrue("before the limiter", call < process.indexOf("processor->truePeakLimiter->process(workBuffer, num_frames, channels);"))
        val flush = bridge.substringAfter("processor->flushPending.exchange(false, std::memory_order_acq_rel)) {").substringBefore("\n    }")
        assertTrue(flush.contains("if (processor->loudness) processor->loudness->clearState();"))
        // Nothing of the native side names the mode: Fidelidad is applied from the playback side only (row 902).
        assertFalse(source("app/src/main/cpp/superpowered/AuraLoudnessCompensation.h").contains("idelity"))
    }
}
