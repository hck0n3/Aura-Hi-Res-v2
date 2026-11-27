package iad1tya.echo.music.eq.audio

import iad1tya.echo.music.eq.EqResponse
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FactoryPreset
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 911: «Headroom automático» lowers the preamp by the highest boost of the WHOLE combined curve — AutoEQ + manual
 * bands + the Tidal signature while it sounds — so the curve never lifts the signal above the user's own preamp.
 */
class AutoHeadroomTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    /** The graphic EQ as the EQ screen builds it (band 0 low shelf, band 9 high shelf, the rest peaks). */
    private fun graphic(gains: FloatArray) = EqConstants.FREQUENCIES.mapIndexed { i, f ->
        ParametricEQBand(
            frequency = f,
            gain = gains[i].toDouble(),
            q = EqConstants.Q,
            filterType = when (i) { 0 -> FilterType.LSC; EqConstants.BAND_COUNT - 1 -> FilterType.HSC; else -> FilterType.PK },
        )
    }

    /** Highest level (dB) the curve + preamp reach anywhere in 20 Hz - 20 kHz. */
    private fun peakOut(preamp: Double, bands: List<ParametricEQBand>): Double =
        (0..480).maxOf { i -> preamp + EqResponse.combinedMagnitudeDb(bands, 20.0 * 1000.0.pow(i / 480.0), 48_000.0) }

    @Test
    fun `a flat curve without the Tidal signature keeps the user's preamp`() {
        assertEquals(2.5, autoHeadroomPreampDb(2.5, graphic(FactoryPreset.FLAT.gains), tidalSignatureOn = false), 1e-9)
    }

    @Test
    fun `the Tidal signature alone takes its own shelves out of the preamp`() {
        val p = autoHeadroomPreampDb(0.0, emptyList(), tidalSignatureOn = true)
        assertTrue("$p", p in -1.7..-1.3)
    }

    @Test
    fun `with every curve the post-curve level never passes the user's preamp, Tidal included`() {
        for (preset in FactoryPreset.entries) for (tidal in listOf(false, true)) {
            val bands = graphic(preset.gains)
            val effective = autoHeadroomPreampDb(2.5, bands, tidal)
            val chain = if (tidal) bands + TIDAL_SIGNATURE_MODEL_BANDS else bands
            val out = peakOut(effective, chain)
            assertTrue("${preset.name} tidal=$tidal: $out dB", out <= 2.5 + 0.05)
        }
    }

    @Test
    fun `the house curve plus the Tidal shelf needs more headroom than either alone`() {
        val v3 = graphic(FactoryPreset.AURA_HI_RES_V3.gains)
        val both = 2.5 - autoHeadroomPreampDb(2.5, v3, true)
        val eqOnly = 2.5 - autoHeadroomPreampDb(2.5, v3, false)
        assertTrue("both $both, eq only $eqOnly", both > eqOnly)
    }

    @Test
    fun `the Tidal model has the native stage's frequencies and gains`() {
        val cpp = File(root, "app/src/main/cpp/superpowered/SuperpoweredBridge.cpp").readText()
        listOf(
            "tidalLowShelf->frequency = 60.0f;", "tidalLowShelf->slope = 0.5f;", "tidalLowShelf->decibel = 1.0f;",
            "tidalMidParam->frequency = 250.0f;", "tidalMidParam->octave = 1.0f;", "tidalMidParam->decibel = -0.5f;",
            "tidalHighMidParam->frequency = 3000.0f;", "tidalHighMidParam->octave = 1.2f;", "tidalHighMidParam->decibel = 0.5f;",
            "tidalDeEsser->decibel = 0.0f;",
            "tidalHighShelf->frequency = 12000.0f;", "tidalHighShelf->slope = 0.5f;", "tidalHighShelf->decibel = 1.5f;",
        ).forEach { assertTrue(it, cpp.contains(it)) }
        assertEquals(listOf(60.0, 250.0, 3000.0, 12000.0), TIDAL_SIGNATURE_MODEL_BANDS.map { it.frequency })
        assertEquals(listOf(1.0, -0.5, 0.5, 1.5), TIDAL_SIGNATURE_MODEL_BANDS.map { it.gain })
        assertEquals(listOf(FilterType.LSC, FilterType.PK, FilterType.PK, FilterType.HSC), TIDAL_SIGNATURE_MODEL_BANDS.map { it.filterType })
    }

    @Test
    fun `the processor uses it, gated like the native stage`() {
        val src = File(root, "app/src/main/kotlin/com/music/echo/eq/audio/CustomEqualizerAudioProcessor.kt").readText()
        val fn = src.substringAfter("private fun effectivePreampDb(profile: ParametricEQ): Double {").substringBefore("\n    }\n")
        assertTrue(fn.contains("if (!autoHeadroom) return profile.preamp"))
        assertTrue(fn.contains("EqBypass.toneStages("))
        assertTrue(fn.contains("return autoHeadroomPreampDb(profile.preamp, active, tidalSounds)"))
        val tidal = src.substringAfter("fun applyTidalSimulation(enabled: Boolean) {").substringBefore("\n    }\n")
        assertTrue(tidal.contains("if (autoHeadroom && this.enabled) currentProfile?.let { applyProfile(it) }"))
    }
}
