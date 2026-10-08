package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqBandType
import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FactoryPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class EqConstantsTest {

    @Test
    fun hasTenFrequenciesAndQ() {
        assertEquals(EqConstants.BAND_COUNT, EqConstants.FREQUENCIES.size)
        assertEquals(EqConstants.BAND_COUNT, EqConstants.FREQUENCY_LABELS.size)
        assertEquals(10, EqConstants.BAND_COUNT)
        assertEquals(31.5, EqConstants.FREQUENCIES.first(), 0.001)
        assertEquals(16000.0, EqConstants.FREQUENCIES.last(), 0.001)
        // ISO octave centers: index 3 = 250 Hz, index 5 = 1 kHz reference.
        assertEquals(250.0, EqConstants.FREQUENCIES[3], 0.001)
        assertEquals(1000.0, EqConstants.FREQUENCIES[5], 0.001)
        // Musical octave Q for a 10-band graphic EQ.
        assertEquals(1.414, EqConstants.Q, 0.0001)
    }

    @Test
    fun everyPresetHasTenGains() {
        FactoryPreset.entries.forEach { preset ->
            assertEquals(
                "${preset.name} must have ${EqConstants.BAND_COUNT} gains",
                EqConstants.BAND_COUNT,
                preset.gains.size,
            )
        }
    }

    @Test
    fun flatIsAllZero() {
        assertEquals(List(10) { 0f }, FactoryPreset.FLAT.gains.toList())
    }

    @Test
    fun arSoundCurveMatchesOwnerSpec() {
        assertEquals(
            listOf(9.0f, 0f, 0f, -3.0f, -3.0f, -2.0f, 1.0f, 1.0f, 2.0f, 2.0f),
            FactoryPreset.AR_SOUND.gains.toList()
        )
        assertEquals("AR-SOUND", FactoryPreset.AR_SOUND.displayName)
    }

    @Test
    fun arSoundV2CurveMatchesOwnerSpec() {
        assertEquals(
            listOf(9.0f, 6.0f, 1.0f, -1.0f, -2.0f, 0f, 1.0f, 0f, 2.0f, 3.0f),
            FactoryPreset.AR_SOUND_V2.gains.toList()
        )
        assertEquals("AR-SOUND V2", FactoryPreset.AR_SOUND_V2.displayName)
    }

    @Test
    fun auraHiResV3IsTheHouseCurve() {
        // Fila 357 (owner 2026-10-08): "de predeterminado dejas Aura Hi-Res v3".
        assertEquals(
            listOf(3.5f, 3.5f, 1.0f, -1.0f, -0.5f, 0f, 1.0f, 1.5f, 2.0f, 3.0f),
            FactoryPreset.AURA_HI_RES_V3.gains.toList()
        )
        assertEquals("Aura Hi-Res v3", FactoryPreset.AURA_HI_RES_V3.displayName)
    }

    @Test
    fun theRetiredPresetsAreGoneAndOnlyV3SaysAuraHiRes() {
        // Fila 357: "elimina todos los que digan Aura Hi-Res, exceptuando Aura Hi-Res v3; también Analog Tape,
        // Olive-Welti, Diffuse Field, Vocal Presence, Sub-Bass Rumble, Cinematic Warmth, Sparkle & Detail,
        // Reference Natural" + "también elimina el preset Tube Amp, Vinyl, Harman IE".
        val names = FactoryPreset.entries.map { it.displayName }
        assertEquals(listOf("Aura Hi-Res v3"), names.filter { it.contains("Aura Hi-Res", ignoreCase = true) })
        listOf(
            "Analog Tape", "Olive-Welti", "Diffuse Field", "Vocal Presence", "Sub-Bass Rumble",
            "Cinematic Warmth", "Sparkle & Detail", "Reference Neutral", "Aura Hi-Res", "Aura Hi-Res v2",
            "Tube Amp", "Vinyl", "Harman IE",
        ).forEach { retired ->
            org.junit.Assert.assertFalse(retired, retired in names)
            org.junit.Assert.assertTrue(retired, retired in iad1tya.echo.music.eq.data.RETIRED_PRESET_GAINS)
        }
    }

    @Test
    fun anEqStillOnARetiredCurveIsRecognisedButAUserCurveIsNot() {
        iad1tya.echo.music.eq.data.RETIRED_PRESET_GAINS.values.forEach { curve ->
            org.junit.Assert.assertTrue(iad1tya.echo.music.eq.data.isRetiredPresetCurve(curve.copyOf()))
        }
        org.junit.Assert.assertTrue(
            iad1tya.echo.music.eq.data.isRetiredPresetCurve(iad1tya.echo.music.eq.data.AURA_HI_RES_V2_PREVIOUS_GAINS.copyOf())
        )
        FactoryPreset.entries.forEach { preset ->
            org.junit.Assert.assertFalse(preset.name, iad1tya.echo.music.eq.data.isRetiredPresetCurve(preset.gains.copyOf()))
        }
        val tuned = FactoryPreset.AURA_HI_RES_V3.gains.copyOf().also { it[0] += 1f }
        org.junit.Assert.assertFalse(iad1tya.echo.music.eq.data.isRetiredPresetCurve(tuned))
    }

    @Test
    fun everyPresetGainStaysWithinRange() {
        FactoryPreset.entries.forEach { preset ->
            preset.gains.forEach { gain ->
                org.junit.Assert.assertTrue(
                    "${preset.name} gain $gain must be within [${EqConstants.GAIN_MIN}, ${EqConstants.GAIN_MAX}]",
                    gain in EqConstants.GAIN_MIN..EqConstants.GAIN_MAX,
                )
            }
        }
    }

    @Test
    fun bandTypeFromCodeMapsCorrectly() {
        assertEquals(EqBandType.PEAK, EqBandType.fromCode(0))
        assertEquals(EqBandType.LOW_SHELF, EqBandType.fromCode(1))
        assertEquals(EqBandType.HIGH_SHELF, EqBandType.fromCode(2))
        assertEquals(EqBandType.PEAK, EqBandType.fromCode(99))
    }
}
