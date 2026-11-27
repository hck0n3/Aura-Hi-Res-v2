package iad1tya.echo.music.eq.audio

import iad1tya.echo.music.eq.EqResponse
import iad1tya.echo.music.eq.data.FilterType
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 912: a band or preamp change GLIDES over 10 ms instead of stepping (no zipper / click), and the EQ biquads run
 * in double precision. The native code is pinned by reading it; the glide itself is checked on a JVM mirror of
 * `AuraBiquad::process` (same TDF-II, same per-frame linear coefficient step, same landing on the exact target).
 */
class EqGlideTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private val bridge by lazy { File(root, "app/src/main/cpp/superpowered/SuperpoweredBridge.cpp").readText() }

    /** Mirror of AuraBiquad (one channel). */
    private class Biquad(c: DoubleArray) {
        val cur = c.copyOf()
        var target = c.copyOf()
        var step = DoubleArray(5)
        var left = 0
        var s1 = 0.0
        var s2 = 0.0
        fun glideTo(c: DoubleArray, frames: Int) {
            if (c.contentEquals(target)) return
            target = c.copyOf()
            step = DoubleArray(5) { (c[it] - cur[it]) / frames }
            left = frames
        }
        fun process(x: FloatArray) {
            val ramp = minOf(left, x.size)
            val c = cur.copyOf()
            for (i in x.indices) {
                if (i < ramp) {
                    for (k in 0 until 5) c[k] += step[k]
                    if (i == left - 1) target.copyInto(c)
                }
                val y = c[0] * x[i] + s1
                s1 = c[1] * x[i] - c[3] * y + s2
                s2 = c[2] * x[i] - c[4] * y
                x[i] = y.toFloat()
            }
            if (ramp > 0) {
                if (ramp == left) { target.copyInto(cur); left = 0 } else { for (k in 0 until 5) cur[k] += step[k] * ramp; left -= ramp }
            }
        }
    }

    private fun periodPeaksDb(y: FloatArray, period: Int): List<Double> =
        (0 until y.size / period).map { p -> 20 * log10((p * period until (p + 1) * period).maxOf { abs(y[it]).toDouble() }) }

    @Test
    fun `a 24 dB band change glides instead of jumping`() {
        val fs = 48_000.0
        val from = EqResponse.coefficients(1000.0, 12.0, 1.414, FilterType.PK, fs)
        val to = EqResponse.coefficients(1000.0, -12.0, 1.414, FilterType.PK, fs)
        val glide = (fs * 10.0 / 1000.0).toInt()
        val x = FloatArray(48_000) { (0.2 * sin(2 * PI * 1000 * it / fs)).toFloat() }

        val smooth = x.copyOf()
        val bq = Biquad(from)
        smooth.toList().chunked(256).forEachIndexed { b, chunk ->
            if (b * 256 == 24_064) bq.glideTo(to, glide)
            val buf = chunk.toFloatArray()
            bq.process(buf)
            buf.copyInto(smooth, b * 256)
        }
        val stepped = x.copyOf()
        var st = Biquad(from)
        stepped.toList().chunked(256).forEachIndexed { b, chunk ->
            if (b * 256 == 24_064) st = Biquad(to).also { it.s1 = st.s1; it.s2 = st.s2 }
            val buf = chunk.toFloatArray()
            st.process(buf)
            buf.copyInto(stepped, b * 256)
        }
        fun largestJump(y: FloatArray) = periodPeaksDb(y, 48).drop(10).zipWithNext { a, b -> abs(b - a) }.maxOrNull()!!
        assertTrue("stepped: ${largestJump(stepped)} dB", largestJump(stepped) > 15.0)
        assertTrue("glided: ${largestJump(smooth)} dB per ms", largestJump(smooth) < 4.0)
        assertTrue(smooth.all { it.isFinite() })
        // It lands EXACTLY on the target: afterwards the response is the requested -12 dB.
        assertTrue(bq.cur.contentEquals(to) && bq.left == 0)
        val tail = periodPeaksDb(smooth, 48).takeLast(100).average()
        assertEquals(20 * log10(0.2) - 12.0, tail, 0.1)
    }

    @Test
    fun `every point between two stable filters is stable, so a glide can never blow up`() {
        // (a1, a2) is stable iff |a2| < 1 and |a1| < 1 + a2: a triangle, which is convex.
        fun stable(c: DoubleArray) = abs(c[4]) < 1.0 && abs(c[3]) < 1.0 + c[4]
        val fs = 192_000.0
        val bands = listOf(
            EqResponse.coefficients(31.5, 18.0, 1.414, FilterType.LSC, fs),
            EqResponse.coefficients(31.5, -18.0, 0.3, FilterType.PK, fs),
            EqResponse.coefficients(16_000.0, 18.0, 1.414, FilterType.HSC, fs),
            EqResponse.coefficients(8_000.0, 0.0, 10.0, FilterType.LPQ, fs),
            EqResponse.coefficients(20.0, 0.0, 0.7, FilterType.HPQ, fs),
            doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0),
        )
        for (a in bands) for (b in bands) {
            assertTrue(stable(a))
            for (k in 0..100) {
                val t = k / 100.0
                val mix = DoubleArray(5) { a[it] + (b[it] - a[it]) * t }
                assertTrue("t=$t", stable(mix))
            }
        }
    }

    @Test
    fun `the native EQ glides its bands and preamp and runs them in double`() {
        assertTrue(bridge.contains("static constexpr double kEqGlideMs = 10.0;"))
        assertTrue(bridge.contains("double b0 = 1.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0;"))
        assertTrue(bridge.contains("double z1[2] = {0.0, 0.0};"))
        assertTrue(bridge.contains("static void auraRbjCoefficients(double fs, float frequency, float gainDb, float qIn, int typeCode, double out[5]) {"))
        val apply = bridge.substringAfter("void consumeAndApplySnapshot() {").substringBefore("static float clampf(")
        assertTrue(apply.contains("eqBiquads[i].glideTo(c, glide);"))
        assertTrue("a band switched off glides to flat first", apply.contains("eqBiquads[i].glideTo(kIdentity, glide);"))
        assertTrue(apply.contains("preampGlideLeft = n;"))
        // The preamp still applies exactly what the screen shows (row 790), once the glide lands.
        assertTrue(bridge.contains("float frontGain = processor->activePreampMultiplier;"))
        assertTrue(bridge.contains("if (--processor->preampGlideLeft == 0) g = frontGain;"))
    }
}
