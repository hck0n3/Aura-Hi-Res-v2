package iad1tya.echo.music.eq.autoeq

import iad1tya.echo.music.eq.EqResponse
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqParserTest {

    private val sample = """
        Preamp: -6.7 dB
        Filter 1: ON PK Fc 21 Hz Gain 5.5 dB Q 0.70
        Filter 2: ON PK Fc 105 Hz Gain -2.1 dB Q 1.41
        Filter 3: ON PK Fc 3000 Hz Gain 4.0 dB Q 2.00
    """.trimIndent()

    /** The real AutoEq file: results/oratory1990/over-ear/Sennheiser HD 650/Sennheiser HD 650 ParametricEQ.txt. */
    private val hd650 = """
        Preamp: -6.1 dB
        Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
        Filter 2: ON PK Fc 8800 Hz Gain 5.1 dB Q 1.42
        Filter 3: ON PK Fc 118 Hz Gain -3.1 dB Q 0.50
        Filter 4: ON PK Fc 37 Hz Gain 0.7 dB Q 3.96
        Filter 5: ON PK Fc 3169 Hz Gain -1.7 dB Q 3.89
        Filter 6: ON HSC Fc 10000 Hz Gain -2.1 dB Q 0.70
        Filter 7: ON PK Fc 1227 Hz Gain -1.2 dB Q 2.53
        Filter 8: ON PK Fc 2055 Hz Gain 1.2 dB Q 3.23
        Filter 9: ON PK Fc 587 Hz Gain 0.4 dB Q 1.19
        Filter 10: ON PK Fc 5332 Hz Gain -1.1 dB Q 5.75
    """.trimIndent()

    @Test fun parsesPreamp() {
        assertEquals(-6.7, AutoEqParser.parse(sample).preampDb, 1e-9)
    }

    @Test fun parsesAllBands() {
        val p = AutoEqParser.parse(sample)
        assertEquals(3, p.bands.size)
        assertEquals(21.0, p.bands[0].fc, 1e-9)
        assertEquals(5.5, p.bands[0].gainDb, 1e-9)
        assertEquals(0.70, p.bands[0].q, 1e-9)
        assertEquals(FilterType.PK, p.bands[0].type)
        assertEquals(3000.0, p.bands[2].fc, 1e-9)
    }

    @Test fun ignoresGarbageLines() {
        val p = AutoEqParser.parse("nonsense\nFilter X: OFF\nPreamp: not a number")
        assertEquals(0.0, p.preampDb, 1e-9)
        assertTrue(p.bands.isEmpty())
    }

    @Test fun parsesTheShelvesOfTheRealHd650Profile() {
        val p = AutoEqParser.parse(hd650)
        assertEquals(-6.1, p.preampDb, 1e-9)
        assertEquals(10, p.bands.size)
        val lsc = p.bands[0]
        assertEquals(FilterType.LSC, lsc.type)
        assertEquals(105.0, lsc.fc, 1e-9)
        assertEquals(6.4, lsc.gainDb, 1e-9)
        assertEquals(0.70, lsc.q, 1e-9)
        val hsc = p.bands[5]
        assertEquals(FilterType.HSC, hsc.type)
        assertEquals(10000.0, hsc.fc, 1e-9)
        assertEquals(-2.1, hsc.gainDb, 1e-9)
        assertEquals(8, p.bands.count { it.type == FilterType.PK })
    }

    @Test fun toAutoBandsKeepsEveryFilterWithItsType() {
        val bands = AutoEqParser.parse(hd650).toAutoBands()
        assertEquals(10, bands.size)
        assertEquals(FilterType.LSC, bands[0].filterType)
        assertEquals(FilterType.HSC, bands[5].filterType)
        // Peaks keep AutoEq's Q untouched; shelves get the engine-slope equivalent.
        assertEquals(1.42, bands[1].q, 1e-9)
        assertEquals(autoEqShelfQToEngineQ(0.70, 6.4), bands[0].q, 1e-12)
        assertTrue(bands.all { it.enabled })
    }

    @Test fun shelfQConversionGivesTheHd650LowShelfSlope() {
        val engineQ = autoEqShelfQToEngineQ(0.70, 6.4)
        val slope = EqResponse.shelfSlope(engineQ)
        assertEquals(0.98, slope, 0.01)
        assertEquals(slope * EqResponse.DEFAULT_Q, engineQ, 1e-12)
    }

    @Test fun shelfQConversionFallsBackToFullSlopeWhenTheEngineCannotExpressIt() {
        // Q 2 asks for S > 1, which the engine clamps to 1: the conversion returns that slope, not garbage.
        assertEquals(EqResponse.DEFAULT_Q, autoEqShelfQToEngineQ(2.0, 6.0), 1e-12)
    }

    @Test fun convertedLowShelfMatchesAutoEqsShelf() {
        val band = AutoEqParser.parse(hd650).toAutoBands()[0]
        for (f in doubleArrayOf(30.0, 105.0, 300.0, 10_000.0)) {
            val expected = autoEqRbjShelfDb(lowShelf = true, fc = 105.0, gainDb = 6.4, q = 0.70, f = f)
            assertEquals("at $f Hz", expected, EqResponse.bandMagnitudeDb(band, f, FS), 0.05)
        }
    }

    @Test fun convertedHighShelfMatchesAutoEqsShelf() {
        val band = AutoEqParser.parse(hd650).toAutoBands()[5]
        for (f in doubleArrayOf(30.0, 1_000.0, 10_000.0, 16_000.0)) {
            val expected = autoEqRbjShelfDb(lowShelf = false, fc = 10_000.0, gainDb = -2.1, q = 0.70, f = f)
            assertEquals("at $f Hz", expected, EqResponse.bandMagnitudeDb(band, f, FS), 0.05)
        }
    }

    @Test fun unconvertedShelfQDoesNotMatch() {
        // Guards the conversion itself: AutoEq's Q 0.70 fed raw to the engine is a much gentler shelf (S ≈ 0.5).
        val raw = ParametricEQBand(105.0, 6.4, 0.70, FilterType.LSC)
        val expected = autoEqRbjShelfDb(lowShelf = true, fc = 105.0, gainDb = 6.4, q = 0.70, f = 300.0)
        assertTrue(abs(expected - EqResponse.bandMagnitudeDb(raw, 300.0, FS)) > 0.05)
    }

    /** AutoEq's own shelf (autoeq/peq.py LowShelf/HighShelf): RBJ with A = 10^(g/40), alpha = sin(w0) / (2Q). */
    private fun autoEqRbjShelfDb(lowShelf: Boolean, fc: Double, gainDb: Double, q: Double, f: Double): Double {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / FS
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val t = 2.0 * sqrt(a) * alpha
        val sign = if (lowShelf) 1.0 else -1.0
        val b0 = a * ((a + 1) - sign * (a - 1) * cosW0 + t)
        val b1 = sign * 2.0 * a * ((a - 1) - sign * (a + 1) * cosW0)
        val b2 = a * ((a + 1) - sign * (a - 1) * cosW0 - t)
        val a0 = (a + 1) + sign * (a - 1) * cosW0 + t
        val a1 = -sign * 2.0 * ((a - 1) + sign * (a + 1) * cosW0)
        val a2 = (a + 1) + sign * (a - 1) * cosW0 - t
        val w = 2.0 * PI * f / FS
        val num = hypot(b0 + b1 * cos(w) + b2 * cos(2 * w), -(b1 * sin(w) + b2 * sin(2 * w)))
        val den = hypot(a0 + a1 * cos(w) + a2 * cos(2 * w), -(a1 * sin(w) + a2 * sin(2 * w)))
        return 20.0 * log10(num / den)
    }

    private companion object {
        const val FS = 48_000.0
    }
}
