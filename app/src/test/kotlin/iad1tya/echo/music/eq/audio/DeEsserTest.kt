package iad1tya.echo.music.eq.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 917: the de-esser is DYNAMIC and split-band. It used to be a 6.5 kHz bell cut of up to -5 dB driven by the
 * band's absolute level, so every loud, bright master lost its top whether or not it had sibilance. The maths is
 * checked on the JVM mirror ([AuraDeEsserMirror]) and the mirror is pinned to `AuraDeEsser.h`.
 */
class DeEsserTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private val header by lazy { File(root, "app/src/main/cpp/superpowered/AuraDeEsser.h").readText() }
    private val bridge by lazy { File(root, "app/src/main/cpp/superpowered/SuperpoweredBridge.cpp").readText() }

    private val sr = 48_000

    private fun db(v: Double) = 20.0 * log10(v)

    private fun stereo(frames: Int, gen: (Int) -> Double) = FloatArray(frames * 2) { idx ->
        val x = gen(idx / 2)
        (if (idx % 2 == 0) x else 0.9 * x).toFloat()
    }

    private fun tone(i: Int, hz: Double, amp: Double) = amp * sin(2 * PI * hz * i / sr)

    /** Music-like bed: bass, voice fundamental, mids and upper mids, loud (peaks near -1 dBFS). */
    private fun musicBed(i: Int) =
        tone(i, 80.0, 0.4) + tone(i, 220.0, 0.25) + tone(i, 1000.0, 0.15) + tone(i, 3000.0, 0.08)

    /** Seeded pink-ish noise (Paul Kellet's filter on white noise from an LCG). */
    private fun pink(frames: Int, amp: Double, seed: Long = 12345): DoubleArray {
        var s = seed
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
        return DoubleArray(frames) {
            s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val w = ((s ushr 8).toDouble() / 16777216.0) * 2 - 1
            b0 = 0.99886 * b0 + w * 0.0555179; b1 = 0.99332 * b1 + w * 0.0750759; b2 = 0.96900 * b2 + w * 0.1538520
            b3 = 0.86650 * b3 + w * 0.3104856; b4 = 0.55000 * b4 + w * 0.5329522; b5 = -0.7616 * b5 - w * 0.0168980
            val p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
            b6 = w * 0.115926
            p * amp
        }
    }

    private fun runChunks(d: AuraDeEsserMirror, data: FloatArray, channels: Int, chunk: Int, onChunk: () -> Unit = {}) {
        val frames = data.size / channels
        var off = 0
        while (off < frames) {
            val n = minOf(chunk, frames - off)
            d.process(data, off, n, channels)
            onChunk()
            off += n
        }
    }

    /** Amplitude of [hz] in channel [ch] over frames [from, to) (single-bin DFT). */
    private fun amplitude(x: FloatArray, channels: Int, ch: Int, hz: Double, from: Int, to: Int): Double {
        var re = 0.0; var im = 0.0
        for (i in from until to) {
            val v = x[i * channels + ch].toDouble()
            re += v * cos(2 * PI * hz * i / sr)
            im += v * sin(2 * PI * hz * i / sr)
        }
        return 2 * sqrt(re * re + im * im) / (to - from)
    }

    @Test
    fun `non-sibilant material comes out bit-identical, however loud`() {
        val input = stereo(sr) { musicBed(it) }
        val out = input.copyOf()
        val d = AuraDeEsserMirror(sr)
        runChunks(d, out, 2, 480)
        for (i in input.indices) assertEquals("sample $i", input[i], out[i], 0f)
        assertEquals(1.0, d.gain, 0.0)
    }

    @Test
    fun `a 7 kHz burst is lowered by at most 6 dB and the gain comes back to exactly 1`() {
        val burst = sr / 4 until sr / 2
        val input = stereo(sr) { i -> tone(i, 220.0, 0.2) + if (i in burst) tone(i, 7000.0, 0.3) else 0.0 }
        val out = input.copyOf()
        val d = AuraDeEsserMirror(sr)
        var minGain = 1.0
        runChunks(d, out, 2, 256) { minGain = minOf(minGain, d.gain) }
        assertTrue("max reduction ${db(minGain)} dB", db(minGain) >= -6.0 - 1e-9)
        assertTrue("a strong sibilant gets nearly the full reduction: ${db(minGain)} dB", db(minGain) <= -5.5)
        // Measured on the 7 kHz component itself, once the attack has settled.
        val from = burst.first + 4800
        val cut = db(amplitude(out, 2, 0, 7000.0, from, burst.last) / amplitude(input, 2, 0, 7000.0, from, burst.last))
        assertTrue("7 kHz cut $cut dB", cut <= -5.0 && cut >= -6.0)
        // Release: 80 ms, so 200 ms after the burst the gain is back to EXACTLY 1 and the samples are untouched again.
        assertEquals(1.0, d.gain, 0.0)
        val tail = (burst.last + sr / 5) * 2
        for (i in tail until input.size) assertEquals("sample $i after the release", input[i], out[i], 0f)
    }

    @Test
    fun `only the 5-9 kHz band moves - 1 kHz and 16 kHz stay put while the burst is cut`() {
        val burst = sr / 4 until sr * 3 / 4
        val gen = { i: Int ->
            tone(i, 1000.0, 0.15) + tone(i, 16000.0, 0.02) + if (i in burst) tone(i, 7000.0, 0.3) else 0.0
        }
        val input = stereo(sr) { gen(it) }
        val out = input.copyOf()
        runChunks(AuraDeEsserMirror(sr), out, 2, 512)
        val from = burst.first + 4800
        val to = burst.last
        val at = { hz: Double -> db(amplitude(out, 2, 0, hz, from, to) / amplitude(input, 2, 0, hz, from, to)) }
        assertTrue("7 kHz ${at(7000.0)} dB", at(7000.0) < -5.0)
        assertTrue("1 kHz ${at(1000.0)} dB", abs(at(1000.0)) < 0.1)
        assertTrue("16 kHz ${at(16000.0)} dB", abs(at(16000.0)) < 0.3)
        // The applied band itself: -3 dB points at exactly 5 and 9 kHz, 0 dB at its centre, at every rate.
        for (rate in listOf(44_100, 48_000, 96_000, 192_000)) {
            val b = AuraDeEsserMirror(rate).band
            assertEquals("$rate Hz", 1 / sqrt(2.0), b.magnitude(5000.0, rate.toDouble()), 1e-6)
            assertEquals("$rate Hz", 1 / sqrt(2.0), b.magnitude(9000.0, rate.toDouble()), 1e-6)
            val peak = (5000..9000 step 10).maxOf { b.magnitude(it.toDouble(), rate.toDouble()) }
            assertEquals("$rate Hz", 1.0, peak, 1e-4)
        }
    }

    @Test
    fun `detection is a ratio - the same material 20 dB louder gets the same reduction`() {
        val gains = listOf(0.01, 0.1).map { amp ->
            val x = pink(sr, amp)
            val data = FloatArray(sr * 2) { x[it / 2].toFloat() }
            val d = AuraDeEsserMirror(sr)
            val trace = mutableListOf<Double>()
            runChunks(d, data, 2, 256) { trace += d.gain }
            trace
        }
        assertTrue(gains[1].any { it < 1.0 })
        for (k in gains[0].indices) assertEquals("block $k", gains[0][k], gains[1][k], 1e-6)
        // Pink noise is far brighter than a mix (a stand-in for cymbals); it is lowered by no more than 3.5 dB.
        assertTrue("pink noise ${db(gains[1].min())} dB", db(gains[1].min()) >= -3.5)
    }

    @Test
    fun `channels are linked - a sibilant on the left lowers the same band on the right`() {
        val burst = sr / 4 until sr / 2
        val data = FloatArray(sr * 2) { idx ->
            val i = idx / 2
            val bed = tone(i, 220.0, 0.2)
            (if (idx % 2 == 0) bed + (if (i in burst) tone(i, 7000.0, 0.3) else 0.0) else bed + tone(i, 6500.0, 0.02)).toFloat()
        }
        val input = data.copyOf()
        runChunks(AuraDeEsserMirror(sr), data, 2, 480)
        val from = burst.first + 4800
        val right = db(amplitude(data, 2, 1, 6500.0, from, burst.last) / amplitude(input, 2, 1, 6500.0, from, burst.last))
        assertTrue("right channel 6.5 kHz $right dB", right < -4.0)
    }

    @Test
    fun `the block size does not change a single sample`() {
        val burst = sr / 4 until sr / 2
        val gen = { i: Int -> tone(i, 220.0, 0.2) + if (i in burst) tone(i, 7000.0, 0.3) else 0.0 }
        val a = stereo(sr) { gen(it) }
        val b = a.copyOf()
        runChunks(AuraDeEsserMirror(sr), a, 2, 480)
        runChunks(AuraDeEsserMirror(sr), b, 2, 37)
        for (i in a.indices) assertEquals("sample $i", a[i], b[i], 0f)
    }

    @Test
    fun `mono and multichannel work, more than 8 channels and low sample rates pass untouched`() {
        val mono = FloatArray(sr) { i -> (tone(i, 220.0, 0.2) + if (i > sr / 4) tone(i, 7000.0, 0.3) else 0.0).toFloat() }
        val monoIn = mono.copyOf()
        runChunks(AuraDeEsserMirror(sr), mono, 1, 480)
        assertTrue(mono.indices.any { mono[it] != monoIn[it] })

        val nine = FloatArray(4800 * 9) { idx -> tone(idx / 9, 7000.0, 0.3).toFloat() }
        val nineIn = nine.copyOf()
        runChunks(AuraDeEsserMirror(sr), nine, 9, 480)
        assertTrue(nine.contentEquals(nineIn))

        val low = FloatArray(16_000 * 2) { idx -> (0.3 * sin(2 * PI * 6000.0 * (idx / 2) / 16_000)).toFloat() }
        val lowIn = low.copyOf()
        val d16 = AuraDeEsserMirror(16_000)
        d16.process(low, 0, 16_000, 2)
        assertTrue(low.contentEquals(lowIn))
    }

    @Test
    fun `the mirror's constants are the header's`() {
        fun constant(name: String): String =
            Regex("""constexpr (?:double|int) $name = ([-0-9.e]+);""").find(header)?.groupValues?.get(1)
                ?: error("$name missing from AuraDeEsser.h")
        assertEquals(AuraDeEsserMirror.BAND_LOW_HZ, constant("kDsBandLowHz").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.BAND_HIGH_HZ, constant("kDsBandHighHz").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.KEY_HIGH_PASS_HZ, constant("kDsKeyHighPassHz").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.THRESHOLD_DB, constant("kDsThresholdDb").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.SLOPE, constant("kDsSlope").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.MAX_REDUCTION_DB, constant("kDsMaxReductionDb").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.ATTACK_MS, constant("kDsAttackMs").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.RELEASE_MS, constant("kDsReleaseMs").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.FLOOR_POWER, constant("kDsFloorPower").toDouble(), 0.0)
        assertEquals(AuraDeEsserMirror.CONTROL_FRAMES, constant("kDsControlFrames").toInt())
        assertTrue(header.contains("hpA.design(1, fs, kDsBandLowHz, 0.54119610014619698);"))
        assertTrue(header.contains("hpB.design(1, fs, kDsBandLowHz, 1.3065629648763766);"))
        assertTrue(header.contains("band.design(2, fs, std::atan(w0) * fs / pi, w0 / (wh - wl));"))
        assertTrue(header.contains("attack = 1.0 - std::exp(-2.0 / (fs * kDsAttackMs / 1000.0));"))
        assertTrue(header.contains("release = 1.0 - std::exp(-2.0 / (fs * kDsReleaseMs / 1000.0));"))
        assertTrue(header.contains("usable = fs >= 22050.0;"))
        assertTrue(header.contains("if (gain != 1.0) {"))
    }

    @Test
    fun `the bridge runs it inside the equalizer block and empties it on a flush`() {
        assertTrue(bridge.contains("#include \"AuraDeEsser.h\""))
        assertTrue(bridge.contains("deEsser = new aura::DeEsser(samplerate);"))
        val eqBlock = bridge.substringAfter("if (runEq) {").substringBefore("// SAFE VOLUME — its own stage")
        assertTrue(eqBlock.contains("processor->deEsser->process(workBuffer, num_frames, channels);"))
        val flush = bridge.substringAfter("processor->flushPending.exchange(false, std::memory_order_acq_rel)) {")
            .substringBefore("\n    }")
        assertTrue(flush.contains("if (processor->deEsser) processor->deEsser->reset();"))
        // The old fixed-frequency bell and its absolute-level detector are gone.
        assertTrue(!bridge.contains("deEsserDetector"))
        assertTrue(!bridge.contains("currentDeEsserDb"))
    }
}
