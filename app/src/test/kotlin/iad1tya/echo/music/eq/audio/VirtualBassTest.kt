package iad1tya.echo.music.eq.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 920: «Graves para altavoces pequeños». The maths is checked on the JVM mirror ([AuraVirtualBass]) and the mirror
 * is pinned to the native header; the wiring (phone speaker only, never in Fidelidad, last before the protection and
 * the true-peak limiter, counted by the automatic headroom, vetoes offload) is pinned on the sources.
 */
class VirtualBassTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun source(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()
    private val header by lazy { source("app/src/main/cpp/superpowered/AuraVirtualBass.h") }
    private val bridge by lazy { source("app/src/main/cpp/superpowered/SuperpoweredBridge.cpp") }

    private val sr = 48_000
    private fun db(v: Double) = 20.0 * log10(v)

    private fun stereo(frames: Int, gen: (Int) -> Double) = FloatArray(frames * 2) { gen(it / 2).toFloat() }

    private fun runStage(input: FloatArray, channels: Int = 2, on: Boolean = true, block: Int = 480): FloatArray {
        val x = input.copyOf()
        val vb = AuraVirtualBass(sr)
        vb.requested = on
        val frames = x.size / channels
        var pos = 0
        while (pos < frames) {
            val n = minOf(block, frames - pos)
            val chunk = x.copyOfRange(pos * channels, (pos + n) * channels)
            vb.process(chunk, n, channels)
            chunk.copyInto(x, pos * channels)
            pos += n
        }
        return x
    }

    /** Amplitude of [hz] in channel 0, Hann-windowed, over the second half (filters and envelope settled). */
    private fun amplitude(x: FloatArray, hz: Double, channels: Int = 2): Double {
        val frames = x.size / channels
        val start = frames / 2
        val n = frames - start
        var re = 0.0
        var im = 0.0
        for (i in 0 until n) {
            val w = 0.5 - 0.5 * cos(2 * PI * i / (n - 1))
            val v = x[(start + i) * channels] * w
            re += v * cos(2 * PI * hz * i / sr)
            im -= v * sin(2 * PI * hz * i / sr)
        }
        return 2 * sqrt(re * re + im * im) / (n * 0.5)
    }

    private fun peak(x: FloatArray, from: Int = x.size / 4): Double {
        var p = 0.0
        for (i in from until x.size) p = maxOf(p, abs(x[i].toDouble()))
        return p
    }

    @Test
    fun `the JVM mirror and the native header keep the same constants`() {
        for (line in listOf(
            "constexpr float kVbCutoffHz = 150.0f;",
            "constexpr float kVbLowFloorHz = 40.0f;",
            "constexpr float kVbHarmonicTopHz = 500.0f;",
            "constexpr float kVbSecondHarmonic = 0.5f;",
            "constexpr float kVbThirdHarmonic = 0.35f;",
            "constexpr float kVbReleaseMs = 200.0f;",
            "constexpr float kVbRampMs = 30.0f;",
            "constexpr float kVbGate = 1.0e-5f;",
            "constexpr float kVbMaxExtraDb = 2.0f;",
            "constexpr int kVbMaxChannels = 8;",
            "const double q1 = 0.54119610014619698, q2 = 1.3065629648763766, qb = 0.70710678118654752;",
            "const double t2 = 2.0 * u * u - 1.0;",
            "const double t3 = (4.0 * u * u - 3.0) * u;",
            "harmonics = envelope * (kVbSecondHarmonic * t2 + kVbThirdHarmonic * t3);",
            "harmonics = harmonicLp.run(0, harmonicHp.run(0, harmonics));",
            "const double low = lowHp.run(0, lowLp[1].run(0, lowLp[0].run(0, mono)));",
            "const double wet = speakerHp[1].run(ch, speakerHp[0].run(ch, x)) + harmonics;",
            "frame[ch] = (float) (x + m * (wet - x));",
        )) assertTrue(line, header.contains(line))
        assertEquals(VirtualBassConstants.MAX_EXTRA_DB, VirtualBass.MAX_EXTRA_DB, 0.0)
        // Original code: credits the method, no third-party licence to carry.
        assertTrue(header.contains("Larsen & Aarts"))
        assertTrue(header.contains("it ports no third-party code"))
    }

    @Test
    fun `the bass below the cutoff is removed from the speaker path`() {
        for ((hz, minCutDb) in listOf(40.0 to 35.0, 60.0 to 25.0, 100.0 to 12.0)) {
            val x = stereo(sr * 2) { 0.5 * sin(2 * PI * hz * it / sr) }
            val y = runStage(x)
            val cut = db(amplitude(x, hz) / amplitude(y, hz))
            assertTrue("$hz Hz cut $cut dB", cut >= minCutDb)
        }
    }

    @Test
    fun `its 2nd and 3rd harmonics are added, and almost nothing else`() {
        for (hz in listOf(80.0, 100.0, 120.0)) {
            val x = stereo(sr * 2) { 0.5 * sin(2 * PI * hz * it / sr) }
            val y = runStage(x)
            val fundamental = amplitude(x, hz)
            val second = db(amplitude(y, 2 * hz) / fundamental)
            val third = db(amplitude(y, 3 * hz) / fundamental)
            assertTrue("$hz Hz: 2f at $second dB", second in -14.0..-3.0)
            assertTrue("$hz Hz: 3f at $third dB", third in -14.0..-6.0)
            // The input has none of them.
            assertTrue(amplitude(x, 2 * hz) / fundamental < 1e-3)
            for (k in 4..5) assertTrue("$hz Hz: ${k}f", db(amplitude(y, k * hz) / fundamental) < -30.0)
        }
    }

    @Test
    fun `the voice and everything above the bass are untouched`() {
        for (hz in listOf(1000.0, 3000.0, 10_000.0)) {
            val x = stereo(sr) { 0.5 * sin(2 * PI * hz * it / sr) }
            val y = runStage(x)
            assertEquals("$hz Hz", 0.0, db(amplitude(y, hz) / amplitude(x, hz)), 0.05)
        }
    }

    @Test
    fun `its peak growth stays under the headroom the automatic headroom takes off`() {
        var worst = -100.0
        var hz = 20.0
        while (hz <= 400.0) {
            val f = hz
            val x = stereo(sr * 2) { 0.99 * sin(2 * PI * f * it / sr) }
            worst = maxOf(worst, db(peak(runStage(x)) / peak(x)))
            hz += 5.0
        }
        val rnd = Random(920)
        repeat(60) {
            val fb = 30 + 120 * rnd.nextDouble()
            val fm = 150 + 3000 * rnd.nextDouble()
            val ab = rnd.nextDouble()
            val pb = 2 * PI * rnd.nextDouble()
            val raw = FloatArray(sr * 2 * 2) { idx ->
                val i = idx / 2
                (ab * sin(2 * PI * fb * i / sr + pb) + (1 - ab) * sin(2 * PI * fm * i / sr + idx % 2)).toFloat()
            }
            val norm = 0.99 / peak(raw, 0)
            val x = FloatArray(raw.size) { (raw[it] * norm).toFloat() }
            worst = maxOf(worst, db(peak(runStage(x)) / peak(x)))
        }
        assertTrue("worst peak growth $worst dB", worst <= VirtualBass.MAX_EXTRA_DB)
    }

    @Test
    fun `with the true-peak limiter after it nothing leaves above the ceiling`() {
        // A full-scale band-limited bass square (odd harmonics up to 2 kHz) plus a vocal-range tone: the stage pushes it
        // over full scale, the -1 dBTP limiter that follows in the chain brings it back.
        val x = stereo(sr * 2) { i ->
            var v = 0.0
            var k = 1
            while (80.0 * k < 2000.0) { v += sin(2 * PI * 80.0 * k * i / sr) / k; k += 2 }
            0.75 * v + 0.2 * sin(2 * PI * 1000.0 * i / sr)
        }
        val norm = 0.99 / peak(x, 0)
        val input = FloatArray(x.size) { (x[it] * norm).toFloat() }
        val y = runStage(input)
        assertTrue("the stage alone goes over: ${db(peak(y))}", peak(y) > peak(input))
        val limiter = AuraTruePeakLimiter(sr, 2)
        val meter = TruePeakMeter(2)
        var pos = 0
        val frames = y.size / 2
        while (pos < frames) {
            val n = minOf(480, frames - pos)
            val chunk = y.copyOfRange(pos * 2, (pos + n) * 2)
            limiter.process(chunk, n)
            if (pos > sr / 2) meter.process(chunk, n)
            pos += n
        }
        assertTrue("true peak ${db(meter.peakLinear)} dBTP", db(meter.peakLinear) <= TruePeakLimiterConstants.CEILING_DBTP + 0.05)
    }

    @Test
    fun `off is a bit-exact copy, silence stays silence, and switching glides back to bit-exact`() {
        val x = stereo(sr / 2) { 0.5 * sin(2 * PI * 70.0 * it / sr) + 0.2 * sin(2 * PI * 2000.0 * it / sr) }
        val off = runStage(x, on = false)
        assertTrue(off.contentEquals(x))
        val silence = runStage(FloatArray(sr * 2))
        assertTrue(silence.all { it == 0f })
        val mono = runStage(FloatArray(sr) { (0.5 * sin(2 * PI * 60.0 * it / sr)).toFloat() }, channels = 1)
        assertTrue(mono.all { it.isFinite() })
        // On, then off: after the 30 ms ramp the stage stops and the output is the input again, exactly.
        val vb = AuraVirtualBass(sr)
        vb.requested = true
        val a = x.copyOf()
        vb.process(a, a.size / 2, 2)
        vb.requested = false
        val ramp = x.copyOf()
        vb.process(ramp, ramp.size / 2, 2)
        assertFalse(vb.wanted())
        val after = x.copyOf()
        vb.process(after, after.size / 2, 2)
        assertTrue(after.contentEquals(x))
        // The ramp never steps: adjacent output samples stay near the signal's own slope (≈ 0.057 per sample here).
        var maxStep = 0.0
        for (i in 2 until ramp.size step 2) maxStep = maxOf(maxStep, abs(ramp[i] - ramp[i - 2]).toDouble())
        assertTrue("step $maxStep", maxStep < 0.1)
    }

    @Test
    fun `it runs only on the phone speaker`() {
        for (category in OutputCategory.values()) {
            assertEquals(category.name, category == OutputCategory.PHONE_SPEAKER, VirtualBass.allowedOn(category))
        }
    }

    @Test
    fun `the native chain runs it after Safe Volume and before the speaker protection and the limiter`() {
        assertTrue(bridge.contains("#include \"AuraVirtualBass.h\""))
        assertTrue(bridge.contains("virtualBass = new aura::VirtualBass(samplerate);"))
        assertTrue(bridge.contains("if (virtualBass) { delete virtualBass; virtualBass = nullptr; }"))
        assertTrue(bridge.contains("(processor && processor->virtualBass && processor->virtualBass->wanted());"))
        assertTrue(bridge.contains("if (processor->virtualBass) processor->virtualBass->reset();"))
        assertTrue(bridge.contains("Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setVirtualBass(JNIEnv *env, jobject thiz, jlong ptr, jboolean enabled) {"))
        val chain = bridge.substringAfter("// SAFE VOLUME — its own stage").substringBefore("if (encoding != 4 && workBuffer) {")
        val stage = chain.indexOf("if (processor->virtualBass) processor->virtualBass->process(workBuffer, num_frames, channels);")
        val protection = chain.indexOf("if (processor->activeSpeakerBassProtect && processor->speakerBassShelf) {")
        val limiter = chain.indexOf("processor->truePeakLimiter->process(workBuffer, num_frames, channels);")
        assertTrue("inside the chain, after Safe Volume", stage > 0)
        assertTrue("before the speaker protection", protection > stage)
        assertTrue("the true-peak limiter stays last", limiter > stage)
        // Not in the chain-off branch: off the chain the audio is never touched by it.
        val chainOff = bridge.substringAfter("} else if (workBuffer && processor && processor->truePeakLimiter) {").substringBefore("if (encoding != 4 && workBuffer) {")
        assertFalse(chainOff.contains("virtualBass"))
    }

    @Test
    fun `the processor publishes it as set, restores it, and counts it in the automatic headroom`() {
        val src = source("app/src/main/kotlin/com/music/echo/eq/audio/CustomEqualizerAudioProcessor.kt")
        assertTrue(src.contains("private external fun setVirtualBass(ptr: Long, enabled: Boolean)"))
        val push = src.substringAfter("private fun pushToneStages(ptr: Long) {").substringBefore("\n    }\n")
        assertTrue("published outside the equalizer gate, like the speaker protection", push.contains("setVirtualBass(ptr, virtualBassEnabled)"))
        assertEquals("one native call site (pushToneStages)", 1, Regex("""setVirtualBass\(ptr,""").findAll(src).count())
        val headroom = src.substringAfter("private fun effectivePreampDb(profile: ParametricEQ): Double {").substringBefore("\n    }\n")
        assertTrue(headroom.contains("if (virtualBassEnabled) VirtualBass.MAX_EXTRA_DB else 0.0"))
        val apply = src.substringAfter("fun applyVirtualBass(enabled: Boolean) {").substringBefore("\n    }\n")
        assertTrue(apply.contains("if (autoHeadroom && this.enabled) currentProfile?.let { applyProfile(it) }"))
    }

    @Test
    fun `the service gates it by the phone speaker and by Fidelidad, from events only`() {
        val service = source("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val mastering = service.substringAfter("private fun applyMasteringFromPrefs() {").substringBefore("\n    }\n")
        assertTrue(mastering.contains("FidelityMode.Stage.VIRTUAL_BASS, virtualBassHint, fidelity,"))
        assertTrue(mastering.contains("VirtualBass.allowedOn(iad1tya.echo.music.eq.audio.OutputClassifier.detect(this))"))
        assertTrue(mastering.contains("it.applyVirtualBass(virtualBass)"))
        assertTrue(iad1tya.echo.music.playback.audio.FidelityMode.Stage.VIRTUAL_BASS in iad1tya.echo.music.playback.audio.FidelityMode.BYPASSED)
        // Its switch, read with the shared default, and the offload veto (an offloaded stream never reaches the stage).
        assertTrue(service.contains("it[iad1tya.echo.music.constants.VirtualBassEnabledKey] ?: iad1tya.echo.music.constants.VirtualBassDefault }"))
        assertTrue(service.contains("(it[iad1tya.echo.music.constants.VirtualBassEnabledKey] ?: iad1tya.echo.music.constants.VirtualBassDefault)"))
        val keys = source("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertTrue(keys.contains("val VirtualBassEnabledKey = booleanPreferencesKey(\"mastering_virtual_bass_enabled\")"))
        assertTrue(keys.contains("const val VirtualBassDefault = false"))
        val sound = source("app/src/main/kotlin/com/music/echo/ui/screens/settings/SoundSettings.kt")
        assertTrue(sound.contains("iad1tya.echo.music.constants.VirtualBassEnabledKey,\n            defaultValue = iad1tya.echo.music.constants.VirtualBassDefault,"))
        assertTrue(sound.contains("Text(stringResource(R.string.sound_virtual_bass_title))"))
        val player = source("app/src/main/kotlin/com/music/echo/ui/screens/settings/PlayerSettings.kt")
        assertTrue(player.contains("stereoWidth != 1f || virtualBassEnabled,"))
        for (res in listOf("app/src/main/res/values/strings.xml", "app/src/main/res/values-es/strings.xml")) {
            val xml = source(res)
            for (name in listOf("sound_virtual_bass_title", "sound_virtual_bass_desc", "sound_virtual_bass_suggested")) {
                assertTrue("$res: $name", xml.contains("<string name=\"$name\">"))
            }
        }
        // No logging in the stage (rule 4) and nothing per frame outside the audio buffer (rule 7).
        for (logger in listOf("LOGI", "LOGE", "__android_log_print", "printf")) assertFalse(logger, header.contains(logger))
    }
}
