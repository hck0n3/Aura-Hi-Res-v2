package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FactoryPreset
import iad1tya.echo.music.eq.data.FilterType
import iad1tya.echo.music.eq.data.ParametricEQBand
import iad1tya.echo.music.eq.data.eqFactoryPresetMatch
import iad1tya.echo.music.eq.data.isRetiredPresetCurve
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 912: «Aura Referencia» — neutral with a little body, no fixed treble boost — is the factory sound of FRESH
 * installs only; "Aura Hi-Res v3" stays in the list and nobody who already has the app is moved.
 */
class AuraReferenciaPresetTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private val ref = FactoryPreset.AURA_REFERENCIA

    private fun graphic(gains: FloatArray) = EqConstants.FREQUENCIES.mapIndexed { i, f ->
        ParametricEQBand(
            frequency = f,
            gain = gains[i].toDouble(),
            q = EqConstants.Q,
            filterType = when (i) { 0 -> FilterType.LSC; EqConstants.BAND_COUNT - 1 -> FilterType.HSC; else -> FilterType.PK },
        )
    }

    @Test
    fun `the curve is neutral with a small low shelf and nothing in the treble`() {
        assertEquals("Aura Referencia", ref.displayName)
        assertEquals(listOf(1.5f, 1.0f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), ref.gains.toList())
        // Nothing above 125 Hz: no boost where a lossy stream keeps its artefacts and sibilance.
        for (i in 3 until EqConstants.BAND_COUNT) assertEquals(0f, ref.gains[i], 0f)
        val bands = graphic(ref.gains)
        assertTrue("peak boost ${EqResponse.peakBoostDb(bands)} dB", EqResponse.peakBoostDb(bands) <= 2.5)
        for (f in listOf(2_000.0, 4_000.0, 8_000.0, 12_000.0, 16_000.0)) {
            assertTrue("$f Hz", kotlin.math.abs(EqResponse.combinedMagnitudeDb(bands, f, 48_000.0)) < 0.1)
        }
        val v3Boost = EqResponse.peakBoostDb(graphic(FactoryPreset.AURA_HI_RES_V3.gains))
        assertTrue("less boost than v3 ($v3Boost dB)", EqResponse.peakBoostDb(bands) < v3Boost)
    }

    @Test
    fun `it selects its own chip and v3 stays in the list`() {
        assertEquals(ref, eqFactoryPresetMatch(ref.gains.copyOf()))
        assertEquals(FactoryPreset.AURA_HI_RES_V3, eqFactoryPresetMatch(FactoryPreset.AURA_HI_RES_V3.gains.copyOf()))
        assertEquals("the new preset is the LAST entry", FactoryPreset.entries.last(), ref)
        assertFalse(isRetiredPresetCurve(ref.gains.copyOf()))
    }

    @Test
    fun `only a fresh install is moved, once, from the v3 seed`() {
        val keys = File(root, "app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt").readText()
        assertTrue(keys.contains("val AuraReferenciaSeedV1AppliedKey = booleanPreferencesKey(\"aura_referencia_seed_v1_applied\")"))
        val app = File(root, "app/src/main/kotlin/com/music/echo/App.kt").readText()
        // The fresh-install seed still writes v3 first; the new seed runs after the install origin is known.
        val seedV2 = app.substringAfter("private suspend fun migrateAudioDefaultsV2(").substringBefore("private suspend fun migrateEqPreampDefaultV3(")
        assertTrue(seedV2.contains("val gains = iad1tya.echo.music.eq.data.FactoryPreset.AURA_HI_RES_V3.gains"))
        val call = "applyAuraReferenciaSeedV1(settings, installOrigin == iad1tya.echo.music.utils.InstallOrigin.FRESH)"
        assertTrue(app.contains(call))
        assertTrue(app.indexOf(call) > app.indexOf("val installOrigin = if (originAlreadyResolved) {"))
        assertTrue(app.indexOf(call) > app.indexOf("migrateRetiredPresetsToV3(settings)\n"))
        val fn = app.substringAfter("private suspend fun applyAuraReferenciaSeedV1(").substringBefore("\n    /**")
        assertTrue("updates and restores are only stamped", fn.contains("val done = if (!freshInstall) {\n            true"))
        assertTrue(fn.contains("val seeded = iad1tya.echo.music.eq.data.FactoryPreset.AURA_HI_RES_V3.gains"))
        assertTrue(fn.contains("val target = iad1tya.echo.music.eq.data.FactoryPreset.AURA_REFERENCIA.gains"))
        assertFalse("the preamp is not touched", fn.contains("preamp"))
        assertFalse("the EQ switch is not touched", fn.contains("putBoolean(\"enabled\""))
    }
}
