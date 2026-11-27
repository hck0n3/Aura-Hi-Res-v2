package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.audio.TIDAL_SIGNATURE_MODEL_BANDS
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 921 — the level match of «Comparar A/B» (EqLevelMatch): the pink-noise level change of the curve, in dB.
 * Expected values come from an independent integral (the RBJ cookbook low/high shelf written out here, 20 000
 * log-spaced points) and were cross-checked with a Python port (200 000 points): +6 dB shelf at 100 Hz = +2.0962 dB.
 */
class EqLevelMatchTest {

    private fun shelf(f0: Double, gainDb: Double, type: FilterType) =
        ParametricEQBand(frequency = f0, gain = gainDb, q = EqResponse.DEFAULT_Q, filterType = type, enabled = true)

    /** |H(f)|² of an RBJ shelf with slope S = 1 (Q 1.414), straight from the cookbook, independent of EqResponse. */
    private fun shelfPower(f0: Double, gainDb: Double, low: Boolean, f: Double, fs: Double): Double {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * f0 / fs
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0) // S = 1
        val t = 2 * sqrt(a) * alpha
        val b: DoubleArray
        val d: DoubleArray
        if (low) {
            b = doubleArrayOf(a * ((a + 1) - (a - 1) * c + t), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - t))
            d = doubleArrayOf((a + 1) + (a - 1) * c + t, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - t)
        } else {
            b = doubleArrayOf(a * ((a + 1) + (a - 1) * c + t), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - t))
            d = doubleArrayOf((a + 1) - (a - 1) * c + t, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - t)
        }
        val w = 2 * PI * f / fs
        fun pow2(p: DoubleArray): Double {
            val re = p[0] + p[1] * cos(w) + p[2] * cos(2 * w)
            val im = -(p[1] * sin(w) + p[2] * sin(2 * w))
            return re * re + im * im
        }
        return pow2(b) / pow2(d)
    }

    private fun referencePinkDb(power: (Double) -> Double): Double {
        val n = 20_000
        val lo = ln(20.0)
        val hi = ln(20_000.0)
        var sum = 0.0
        for (i in 0 until n) sum += power(exp(lo + (hi - lo) * (i + 0.5) / n))
        return 10 * log10(sum / n)
    }

    @Test
    fun `a flat curve needs no level match`() {
        assertEquals(0.0, EqLevelMatch.pinkLevelDb(emptyList()), 0.0)
        val zeros = listOf(
            shelf(31.5, 0.0, FilterType.LSC),
            ParametricEQBand(frequency = 1000.0, gain = 0.0, q = 1.414, filterType = FilterType.PK),
            shelf(16_000.0, 0.0, FilterType.HSC),
        )
        assertEquals(0.0, EqLevelMatch.pinkLevelDb(zeros), 1e-9)
        assertEquals(0.0, EqLevelMatch.referenceGainDb(zeros, tidalSignatureOn = false, effectivePreampDb = 0.0), 1e-9)
        // A disabled band does not sound, so it does not count.
        val off = listOf(shelf(100.0, 12.0, FilterType.LSC).copy(enabled = false))
        assertEquals(0.0, EqLevelMatch.pinkLevelDb(off), 0.0)
    }

    @Test
    fun `a +6 dB bass shelf matches the pink-noise integral`() {
        val fs = 48_000.0
        val level = EqLevelMatch.pinkLevelDb(listOf(shelf(100.0, 6.0, FilterType.LSC)), fs)
        val reference = referencePinkDb { f -> shelfPower(100.0, 6.0, low = true, f = f, fs = fs) }
        assertEquals(reference, level, 0.001)
        assertEquals(2.0962, level, 0.001)
        // Sanity: an ideal +6 dB brick wall below 100 Hz covers log2(5) of the ~10 octaves: 2.29 dB. The real shelf
        // reaches its full gain only below ~50 Hz, so it is a little less, and far from the +6 dB peak boost.
        assertTrue(level in 1.5..2.29)
    }

    @Test
    fun `cuts and treble boosts are matched the same way`() {
        val fs = 48_000.0
        val cut = EqLevelMatch.pinkLevelDb(listOf(shelf(100.0, -6.0, FilterType.LSC)), fs)
        assertEquals(referencePinkDb { shelfPower(100.0, -6.0, true, it, fs) }, cut, 0.001)
        assertEquals(-0.9324, cut, 0.001)
        val treble = EqLevelMatch.pinkLevelDb(listOf(shelf(8_000.0, 6.0, FilterType.HSC)), fs)
        assertEquals(referencePinkDb { shelfPower(8_000.0, 6.0, false, it, fs) }, treble, 0.001)
        assertEquals(1.3012, treble, 0.001)
    }

    @Test
    fun `the reference gain adds the preamp and the Tidal signature while it sounds`() {
        val bass = listOf(shelf(100.0, 6.0, FilterType.LSC))
        val curve = EqLevelMatch.pinkLevelDb(bass)
        // The preamp A plays with (the user's, or the automatic headroom's) adds straight on top.
        assertEquals(curve - 6.0, EqLevelMatch.referenceGainDb(bass, false, effectivePreampDb = -6.0), 1e-9)
        assertEquals(-3.0, EqLevelMatch.referenceGainDb(emptyList(), false, effectivePreampDb = -3.0), 1e-9)
        // The Tidal signature is part of A while it sounds.
        val tidal = EqLevelMatch.pinkLevelDb(TIDAL_SIGNATURE_MODEL_BANDS)
        assertTrue(tidal > 0.0)
        assertEquals(tidal, EqLevelMatch.referenceGainDb(emptyList(), true, 0.0), 1e-9)
        assertEquals(EqLevelMatch.pinkLevelDb(bass + TIDAL_SIGNATURE_MODEL_BANDS), EqLevelMatch.referenceGainDb(bass, true, 0.0), 1e-9)
    }

    @Test
    fun `the reference gain stays inside the preamp range`() {
        val huge = listOf(shelf(1_000.0, 18.0, FilterType.LSC))
        assertEquals(9.0, EqLevelMatch.referenceGainDb(huge, false, effectivePreampDb = 9.0), 0.0)
        assertEquals(-20.0, EqLevelMatch.referenceGainDb(emptyList(), false, effectivePreampDb = -30.0), 0.0)
    }
}
