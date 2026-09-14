package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqResponseTest {
    private val fs = 48_000.0

    private fun band(freq: Double, gain: Double, q: Double = EqResponse.DEFAULT_Q, type: FilterType = FilterType.PK) =
        ParametricEQBand(frequency = freq, gain = gain, q = q, filterType = type, enabled = true)

    @Test
    fun peakingBandHitsItsGainAtTheCentre() {
        assertEquals(4.0, EqResponse.bandMagnitudeDb(band(1000.0, 4.0), 1000.0, fs), 0.01)
        assertEquals(-3.0, EqResponse.bandMagnitudeDb(band(250.0, -3.0), 250.0, fs), 0.01)
    }

    @Test
    fun shelvesReachTheirGainFarFromTheCorner() {
        assertEquals(3.0, EqResponse.bandMagnitudeDb(band(125.0, 3.0, type = FilterType.LSC), 20.0, fs), 0.1)
        assertEquals(2.0, EqResponse.bandMagnitudeDb(band(8000.0, 2.0, type = FilterType.HSC), 20_000.0, fs), 0.15)
    }

    @Test
    fun defaultQShelfIsTheHistoricSlopeOne() {
        assertEquals(1.0, EqResponse.shelfSlope(EqResponse.DEFAULT_Q), 1e-9)
        assertTrue(EqResponse.shelfSlope(0.3) < 1.0)
    }

    @Test
    fun lowerShelfQMakesAGentlerShelf() {
        val steep = EqResponse.bandMagnitudeDb(band(1000.0, 6.0, q = 1.414, type = FilterType.LSC), 500.0, fs)
        val gentle = EqResponse.bandMagnitudeDb(band(1000.0, 6.0, q = 0.3, type = FilterType.LSC), 500.0, fs)
        assertTrue("gentle $gentle vs steep $steep", gentle < steep)
    }

    @Test
    fun lowPassCutsAboveItsCutoff() {
        val lp = band(1000.0, 0.0, q = 0.707, type = FilterType.LPQ)
        assertEquals(0.0, EqResponse.bandMagnitudeDb(lp, 50.0, fs), 0.1)
        assertTrue(EqResponse.bandMagnitudeDb(lp, 10_000.0, fs) < -30.0)
    }

    @Test
    fun peakBoostFindsTheHighestPointOfTheCascade() {
        val curve = listOf(band(62.5, 4.0), band(8000.0, 3.0), band(250.0, -1.0))
        assertEquals(4.0, EqResponse.peakBoostDb(curve, fs), 0.2)
        assertEquals(0.0, EqResponse.peakBoostDb(listOf(band(1000.0, -6.0)), fs), 1e-9)
    }
}
