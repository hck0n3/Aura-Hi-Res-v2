package iad1tya.echo.music.eq.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 911: the end of the native chain is a TRUE-PEAK lookahead limiter at -1 dBTP (it replaced Superpowered's
 * sample-peak limiter and the tanh soft clip). The maths is checked on the JVM mirror ([AuraTruePeakLimiter]) and the
 * mirror is pinned to the native header, so neither can drift.
 */
class TruePeakLimiterTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private val header by lazy { File(root, "app/src/main/cpp/superpowered/AuraTruePeakLimiter.h").readText() }
    private val bridge by lazy { File(root, "app/src/main/cpp/superpowered/SuperpoweredBridge.cpp").readText() }

    private fun db(v: Double) = 20.0 * log10(v)

    /** Independent reference: 16x reconstruction with a long (128-tap) windowed sinc, far finer than the limiter's. */
    private fun referenceTruePeak(x: FloatArray, channels: Int, channel: Int): Double {
        val n = x.size / channels
        var peak = 0.0
        for (i in 64 until n - 64) {
            for (f in 0 until 16) {
                val t0 = i + f / 16.0
                var y = 0.0
                for (k in i - 63..i + 64) {
                    val t = t0 - k
                    val s = if (abs(t) < 1e-12) 1.0 else sin(PI * t) / (PI * t)
                    val w = 0.42 + 0.5 * cos(PI * t / 64) + 0.08 * cos(2 * PI * t / 64)
                    y += x[k * channels + channel] * s * w
                }
                if (abs(y) > peak) peak = abs(y)
            }
        }
        return peak
    }

    private fun sine(frames: Int, sampleRate: Int, hz: Double, amp: Double, phase: Double, channels: Int) =
        FloatArray(frames * channels) { idx ->
            val i = idx / channels
            val ch = idx % channels
            ((if (ch == 0) amp else amp * 0.9) * sin(2 * PI * hz * i / sampleRate + phase)).toFloat()
        }

    @Test
    fun `below the ceiling the limiter is a bit-exact delay`() {
        val lim = AuraTruePeakLimiter(48_000, 2)
        val input = sine(4800, 48_000, 1000.0, 0.5, 0.0, 2)
        val out = input.copyOf()
        var off = 0
        while (off < 4800) {
            val chunk = out.copyOfRange(off * 2, (off + 480) * 2)
            lim.process(chunk, 480)
            chunk.copyInto(out, off * 2)
            off += 480
        }
        val lat = lim.latencyFrames
        assertTrue("latency ${lat} frames stays under 2 ms at 48 kHz", lat < 96)
        for (i in lat until 4800) for (c in 0 until 2) {
            assertEquals("frame $i ch $c", input[(i - lat) * 2 + c], out[i * 2 + c], 0f)
        }
    }

    @Test
    fun `loud sines never leave above -1 dBTP, including the peak that falls between samples`() {
        val sr = 48_000
        for (hz in listOf(50.0, 1000.0, 5000.0, 11025.0, 12000.0, 15000.0, 18000.0)) {
            for (phase in listOf(0.0, PI / 4)) {
                val lim = AuraTruePeakLimiter(sr, 2)
                val x = sine(sr / 2, sr, hz, 1.2, phase, 2) // +1.6 dBTP in
                lim.process(x, sr / 2)
                val tail = x.copyOfRange(sr / 4 * 2, sr / 4 * 2 + 4000 * 2)
                val tp = db(referenceTruePeak(tail, 2, 0))
                assertTrue("$hz Hz phase $phase: $tp dBTP", tp <= -1.0 + 0.05)
                assertTrue("$hz Hz phase $phase: not over-limited ($tp dBTP)", tp >= -1.0 - 0.3)
            }
        }
    }

    @Test
    fun `12 kHz at 45 degrees - samples at -4 dBFS but true peak at 0 - is caught`() {
        // The classic inter-sample over: a sample-peak limiter sees -4 dBFS and does nothing.
        val sr = 48_000
        val x = FloatArray(sr / 2 * 2) { idx -> sin(2 * PI * 12_000 * (idx / 2) / sr + PI / 4).toFloat() }
        var samplePeak = 0f
        x.forEach { samplePeak = maxOf(samplePeak, abs(it)) }
        assertTrue(db(samplePeak.toDouble()) < -2.9)
        val lim = AuraTruePeakLimiter(sr, 2)
        lim.process(x, sr / 2)
        val tp = db(referenceTruePeak(x.copyOfRange(sr / 4 * 2, sr / 4 * 2 + 4000 * 2), 2, 0))
        assertTrue("$tp dBTP", tp <= -0.95)
    }

    @Test
    fun `the gain is linked so the stereo image never moves`() {
        val sr = 48_000
        val frames = sr / 2
        val x = FloatArray(frames * 2) { idx -> if (idx % 2 == 0) (1.5 * sin(2 * PI * 440 * (idx / 2) / sr)).toFloat() else (0.3 * sin(2 * PI * 440 * (idx / 2) / sr)).toFloat() }
        val input = x.copyOf()
        val lim = AuraTruePeakLimiter(sr, 2)
        lim.process(x, frames)
        val lat = lim.latencyFrames
        for (i in frames / 2 until frames) {
            val inL = input[(i - lat) * 2]
            val inR = input[(i - lat) * 2 + 1]
            if (abs(inL) > 0.5f && abs(inR) > 0.1f) {
                assertEquals("L and R reduced by the same gain at $i", x[i * 2] / inL, x[i * 2 + 1] / inR, 1e-4f)
            }
        }
    }

    @Test
    fun `after a loud burst the gain releases back to exactly 1`() {
        val sr = 48_000
        val n = sr * 2
        val x = FloatArray(n) { i -> ((if (i in 4801 until 9600) 1.5 else 0.3) * sin(2 * PI * 440 * i / sr)).toFloat() }
        val input = x.copyOf()
        val lim = AuraTruePeakLimiter(sr, 1)
        lim.process(x, n)
        val lat = lim.latencyFrames
        var peak = 0f
        x.forEach { peak = maxOf(peak, abs(it)) }
        assertTrue("burst limited: ${db(peak.toDouble())} dBFS", db(peak.toDouble()) <= -1.0 + 0.01)
        // 100 ms release: back to a bit-exact copy well within two seconds.
        for (i in n - 4800 until n) assertEquals(input[i - lat], x[i], 0f)
        assertEquals(1.0, lim.lastGain, 0.0)
    }

    /** Runs [x] through [lim] in uneven blocks, the chain switched on or off per block by [chainOn]. */
    private fun runToggled(lim: AuraTruePeakLimiter, x: FloatArray, channels: Int, chainOn: (block: Int, frame: Int) -> Boolean) {
        val sizes = intArrayOf(480, 97, 1024, 7, 333, 960)
        val frames = x.size / channels
        var off = 0
        var block = 0
        while (off < frames) {
            val f = minOf(sizes[block % sizes.size], frames - off)
            val chunk = x.copyOfRange(off * channels, (off + f) * channels)
            if (chainOn(block, off)) lim.process(chunk, f) else lim.passThrough(chunk, f)
            chunk.copyInto(x, off * channels)
            off += f
            block++
        }
    }

    @Test
    fun `switching the chain on and off mid-song neither drops nor repeats a sample`() {
        // Review of row 911: with the chain off the audio skipped the limiter's ~1.8 ms delay, so every edge either
        // played ~87 zeros (off to on) or jumped over the frames still in the line (on to off). Now both sides have the
        // same latency: below the ceiling the output is the input delayed by exactly latencyFrames, across every edge.
        val sr = 48_000
        val input = sine(sr * 2, sr, 1000.0, 0.5, 0.3, 2)
        val x = input.copyOf()
        val lim = AuraTruePeakLimiter(sr, 2)
        runToggled(lim, x, 2) { block, _ -> block % 3 != 1 }
        val lat = lim.latencyFrames
        for (i in lat until sr * 2) for (c in 0 until 2) {
            assertEquals("frame $i ch $c", input[(i - lat) * 2 + c], x[i * 2 + c], 0f)
        }
    }

    @Test
    fun `when the chain stops the frames it left in the delay are still limited, then the copy is bit-exact`() {
        val sr = 48_000
        val n = sr * 3
        val raw = sine(n, sr, 440.0, 0.8, 0.0, 2)
        val x = raw.copyOf()
        // First second: the chain runs with +3.5 dB of gain in front of the limiter; then it is switched off.
        for (i in 0 until sr * 2) x[i] *= 1.5f
        val lim = AuraTruePeakLimiter(sr, 2)
        runToggled(lim, x, 2) { _, frame -> frame < sr }
        val lat = lim.latencyFrames
        for (i in sr until sr + lat) {
            assertTrue("drained frame $i stays under the ceiling", abs(x[i * 2]) <= lim.ceiling.toFloat() + 1e-6f)
        }
        for (i in lat until n) {
            if (raw[(i - lat) * 2] != 0f) assertTrue("no hole of zeros at frame $i", x[i * 2] != 0f)
        }
        // The gain releases smoothly (100 ms one-pole), then the chain-off audio is an exact delayed copy.
        for (i in n - sr / 2 until n) for (c in 0 until 2) {
            assertEquals("frame $i ch $c", raw[(i - lat) * 2 + c], x[i * 2 + c], 0f)
        }
        assertTrue(lim.isIdle)
    }

    @Test
    fun `with the chain off loud audio is never limited`() {
        // Modo Fidelidad (rows 902-903): a hot master above -1 dBTP passes untouched, only delayed.
        val sr = 48_000
        val input = sine(sr, sr, 12_000.0, 0.999, PI / 4, 2)
        val x = input.copyOf()
        val lim = AuraTruePeakLimiter(sr, 2)
        lim.passThrough(x, sr)
        val lat = lim.latencyFrames
        for (i in lat until sr) for (c in 0 until 2) assertEquals(input[(i - lat) * 2 + c], x[i * 2 + c], 0f)
        assertTrue(lim.isIdle)
    }

    @Test
    fun `the native limiter keeps the chain-off path of the mirror`() {
        assertTrue(header.contains("void passThrough(float* data, int frames, int channels) { run(data, frames, channels, false); }"))
        assertTrue(header.contains("void process(float* data, int frames, int channels) { run(data, frames, channels, true); }"))
        assertTrue(header.contains("if (framesSinceLoud < kTpTaps && (chainOn || sinceChain <= kTpTaps - 1 - kTpCentre)) {"))
        assertTrue(header.contains("if (!chainOn && sinceChain > latency && g == 1.0 && r == 1.0 && hold >= 1.0) {"))
    }

    @Test
    fun `the JVM mirror and the native header keep the same constants`() {
        assertTrue(header.contains("constexpr float kTpCeilingDbtp = -1.0f;"))
        assertTrue(header.contains("constexpr float kTpLookaheadMs = 1.5f;"))
        assertTrue(header.contains("constexpr float kTpReleaseMs = 100.0f;"))
        assertTrue(header.contains("constexpr float kTpSafetyClamp = 0.989f;"))
        assertTrue(header.contains("constexpr int kTpOversample = 4;"))
        assertTrue(header.contains("constexpr int kTpTaps = 32;"))
        assertTrue(header.contains("constexpr int kTpCentre = 15;"))
        assertTrue(header.contains("const double w = 0.42 + 0.5 * std::cos(pi * x) + 0.08 * std::cos(2.0 * pi * x);"))
        assertTrue(header.contains("static constexpr int kMaxLookahead = 2048;"))
        assertEquals(-1.0, TruePeakLimiterConstants.CEILING_DBTP, 0.0)
        assertEquals(1.5, TruePeakLimiterConstants.LOOKAHEAD_MS, 0.0)
        assertEquals(100.0, TruePeakLimiterConstants.RELEASE_MS, 0.0)
        assertEquals(0.989f, TruePeakLimiterConstants.SAFETY_CLAMP, 0f)
        assertEquals(4, TruePeakLimiterConstants.OVERSAMPLE)
        assertEquals(32, TruePeakLimiterConstants.TAPS)
        assertEquals(15, TruePeakLimiterConstants.CENTRE)
        assertEquals(2048, AuraTruePeakLimiter.MAX_LOOKAHEAD)
    }

    @Test
    fun `the native chain ends in the true-peak limiter and a hard clamp under full scale`() {
        assertTrue(bridge.contains("#include \"AuraTruePeakLimiter.h\""))
        val chain = bridge.substringAfter("// SAFE VOLUME — its own stage").substringBefore("if (encoding != 4 && workBuffer) {")
        val lim = chain.indexOf("processor->truePeakLimiter->process(workBuffer, num_frames, channels);")
        val clamp = chain.indexOf("const float kClamp = aura::kTpSafetyClamp;")
        assertTrue("the limiter runs after Safe Volume, inside the chain", lim > 0)
        assertTrue("the hard clamp runs after the limiter", clamp > lim)
        assertFalse("the tanh soft clip is gone", bridge.contains("std::tanh(absX - 0.97f)"))
        assertFalse("Superpowered's sample-peak limiter is gone", bridge.contains("processor->limiter->process("))
        // With the chain off the audio still goes through the limiter's delay line, and nothing resets it on the edge.
        assertTrue(bridge.contains("} else if (workBuffer && processor && processor->truePeakLimiter) {"))
        assertTrue(bridge.contains("processor->truePeakLimiter->passThrough(workBuffer, num_frames, channels);"))
        assertFalse(bridge.contains("chainRanLastBlock"))
        // The clamp can never let a sample out at or above full scale.
        assertTrue(TruePeakLimiterConstants.SAFETY_CLAMP < 1.0f)
        // Safe Volume's own native cap is unchanged (row 152).
        assertTrue(bridge.contains("const float kMaxSafeVolumeGain = 4.0f;"))
    }
}
