package iad1tya.echo.music.eq.autoeq

import iad1tya.echo.music.eq.EqResponse
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 915: the safety transform of the AUTOMATIC correction — boosts within the cap, nothing corrected above
 * ~10 kHz, the curve below unchanged — measured on the same RBJ maths the engine plays ([EqResponse]).
 */
class AutoEqSafetyTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }
    private val catalogue by lazy {
        GZIPInputStream(File(root, "app/src/main/assets/${AutoEqOfflineCatalog.ASSET_NAME}").inputStream())
            .bufferedReader().useLines { AutoEqOfflineCatalog.parse(it) }
    }

    private val sr = 48_000.0
    private fun db(bands: List<ParametricEQBand>, f: Double) = EqResponse.combinedMagnitudeDb(bands, f, sr)
    private val grid = (0..240).map { 20.0 * 1000.0.pow(it / 240.0) }
    private val above = AutoEqSafety.FIT_HZ.filter { it >= AutoEqSafety.FADE_END_HZ }

    private val hd650 = AutoEqParser.parse(
        """
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
        """.trimIndent(),
    )

    private fun profile(vararg bands: AutoEqBand) = AutoEqProfile(0.0, bands.toList())

    @Test fun `the weight fades in octaves from 10 kHz to 14_1 kHz`() {
        assertEquals(1.0, AutoEqSafety.highFrequencyWeight(3_000.0), 0.0)
        assertEquals(1.0, AutoEqSafety.highFrequencyWeight(10_000.0), 0.0)
        assertEquals(0.5, AutoEqSafety.highFrequencyWeight(10_000.0 * 2.0.pow(0.25)), 1e-3)
        assertEquals(0.0, AutoEqSafety.highFrequencyWeight(14_142.0), 0.0)
        assertEquals(0.0, AutoEqSafety.highFrequencyWeight(18_000.0), 0.0)
    }

    @Test fun `a boost above the cap is brought down to it`() {
        val safe = AutoEqSafety.safeBands(profile(AutoEqBand(100.0, 10.0, 1.0)))
        assertEquals(6.0, EqResponse.peakBoostDb(safe), 0.06)
        val three = AutoEqSafety.safeBands(profile(AutoEqBand(100.0, 10.0, 1.0)), maxBoostDb = 3.0)
        assertEquals(3.0, EqResponse.peakBoostDb(three), 0.06)
    }

    @Test fun `overlapping boosts are capped as they add up, keeping the curve's shape`() {
        val p = profile(AutoEqBand(1_000.0, 5.0, 1.0), AutoEqBand(1_200.0, 5.0, 1.0), AutoEqBand(300.0, -4.0, 1.0))
        val original = p.toAutoBands()
        assertTrue(EqResponse.peakBoostDb(original) > 8.0)
        val safe = AutoEqSafety.safeBands(p)
        assertTrue(EqResponse.peakBoostDb(safe) <= 6.05)
        // Uniform scaling: the cut shrinks by the same factor as the boosts (the shape is the same, only gentler).
        val k = EqResponse.peakBoostDb(safe) / EqResponse.peakBoostDb(original)
        for (f in listOf(100.0, 300.0, 1_000.0, 3_000.0)) assertEquals(db(original, f) * k, db(safe, f), 0.25)
    }

    @Test fun `a correction under the cap and below 10 kHz is left exactly as AutoEq wrote it`() {
        val p = profile(AutoEqBand(3_000.0, 4.0, 2.0), AutoEqBand(200.0, -3.0, 0.7), AutoEqBand(105.0, 2.0, 0.7, FilterType.LSC))
        val safe = AutoEqSafety.safeBands(p)
        val original = p.toAutoBands()
        assertEquals(original, safe.take(original.size))
        for (f in grid.filter { it <= 6_000.0 }) assertEquals(db(original, f), db(safe, f), 0.1)
    }

    @Test fun `boosts above 10 kHz fade out`() {
        val p = profile(AutoEqBand(12_000.0, 5.0, 2.0), AutoEqBand(16_000.0, 6.0, 2.0), AutoEqBand(3_000.0, 3.0, 2.0))
        val safe = AutoEqSafety.safeBands(p)
        // The 16 kHz peak is gone, the 12 kHz one is weighed down, the 3 kHz one is untouched.
        assertTrue(safe.none { it.filterType == FilterType.PK && it.frequency == 16_000.0 })
        val at12 = safe.single { it.filterType == FilterType.PK && it.frequency == 12_000.0 }
        assertEquals(5.0 * AutoEqSafety.highFrequencyWeight(12_000.0), at12.gain, 1e-9)
        assertEquals(3.0, safe.single { it.frequency == 3_000.0 }.gain, 1e-9)
        for (f in above) assertTrue("$f Hz: ${db(safe, f)}", abs(db(safe, f)) <= 1.0)
        assertEquals(3.0, db(safe, 3_000.0), 0.3)
    }

    @Test fun `the HD 650 correction keeps its curve below 6 kHz and stops above 14 kHz`() {
        val original = hd650.toAutoBands()
        val safe = AutoEqSafety.safeBands(hd650)
        val k = 1.0.coerceAtMost(6.0 / EqResponse.peakBoostDb(original))
        assertTrue(EqResponse.peakBoostDb(safe) <= 6.05)
        for (f in grid.filter { it <= 6_000.0 }) assertEquals("$f Hz", db(original, f) * k, db(safe, f), 0.3)
        for (f in above) assertTrue("$f Hz: ${db(safe, f)}", abs(db(safe, f)) <= 0.6)
        // Without the fade the original lifts or cuts more than that up there.
        assertTrue(above.any { abs(db(original, it)) > 1.0 })
    }

    @Test fun `common headphones from the bundled catalogue stay safe`() {
        val cases = listOf(
            "Sony WH-1000XM4" to "oratory1990",
            "Samsung Galaxy Buds2 Pro" to "oratory1990",
            "Apple AirPods Pro" to "crinacle",
            "Sony WF-1000XM4" to "oratory1990",
        )
        for ((name, source) in cases) {
            val entry = catalogue.single { it.name == name && it.source == source }
            val original = entry.toProfile().toAutoBands()
            val safe = AutoEqSafety.safeBands(entry.toProfile())
            val k = 1.0.coerceAtMost(6.0 / EqResponse.peakBoostDb(original))
            assertTrue(name, EqResponse.peakBoostDb(safe) <= 6.05)
            // 0.8 dB: the fade shelves start acting below 10 kHz (WF-1000XM4, a +3.7 dB shelf at 10 kHz: 0.71 dB at 6 kHz).
            for (f in grid.filter { it <= 6_000.0 }) assertEquals("$name $f Hz", db(original, f) * k, db(safe, f), 0.8)
            for (f in above) assertTrue("$name $f Hz: ${db(safe, f)}", abs(db(safe, f)) <= 1.5)
        }
    }

    @Test fun `the preamp seam never cuts twice`() {
        val bands = AutoEqSafety.safeBands(hd650)
        // «Headroom automático» on: the processor removes the cascade's peak itself (effectivePreampDb), so 0 here.
        assertEquals(0.0, AutoEqSafety.devicePreampDb(bands, autoHeadroomOn = true), 0.0)
        // Off: −(highest boost of the whole cascade), manual bands included.
        val manual = listOf(ParametricEQBand(60.0, 3.0, 1.0, FilterType.PK, true))
        assertEquals(-EqResponse.peakBoostDb(bands + manual), AutoEqSafety.devicePreampDb(bands + manual, false), 1e-9)
        assertTrue(AutoEqSafety.devicePreampDb(bands, false) <= -5.0)
    }
}
