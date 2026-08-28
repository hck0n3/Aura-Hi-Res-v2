package iad1tya.echo.music.eq

import iad1tya.echo.music.eq.data.EqConstants
import iad1tya.echo.music.eq.data.FACTORY_PRESET_MATCH_TOLERANCE_DB
import iad1tya.echo.music.eq.data.FactoryPreset
import iad1tya.echo.music.eq.data.eqFactoryPresetMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the preset-selection match rule (registry #181). The EQ screen freezes this selection
 * while a finger drags a fader, so the rule itself must stay exactly as the UI shipped it:
 * per-band tolerance strictly below 0.5 dB, winner by canonical enum order.
 */
class EqFactoryPresetMatchTest {

    @Test
    fun exactPresetGainsMatchThemselves() {
        FactoryPreset.entries.forEach { preset ->
            val match = eqFactoryPresetMatch(preset.gains.copyOf())
                ?: throw AssertionError("${preset.name} gains must match some preset")
            // Winner is the FIRST preset within tolerance in canonical order — it may precede
            // the preset the gains were copied from, but it must never come after it.
            assertTrue(
                "${match.name} matched before ${preset.name}",
                match.ordinal <= preset.ordinal,
            )
            assertTrue(
                "${match.name} must be within tolerance of the input",
                match.gains.indices.all {
                    kotlin.math.abs(preset.gains[it] - match.gains[it]) < FACTORY_PRESET_MATCH_TOLERANCE_DB
                },
            )
        }
    }

    @Test
    fun flatMatchesAllZeroGains() {
        assertEquals(FactoryPreset.FLAT, eqFactoryPresetMatch(FloatArray(EqConstants.BAND_COUNT)))
    }

    @Test
    fun auraHiResExactGainsSelectAuraHiRes() {
        assertEquals(
            FactoryPreset.AURA_HI_RES,
            eqFactoryPresetMatch(FactoryPreset.AURA_HI_RES.gains.copyOf()),
        )
    }

    @Test
    fun smallDragTickKeepsTheSelection() {
        // The first drag tick moves a single band by a fraction of a dB; the selection must NOT
        // flip on that (this is what used to toggle the description box mid-drag — #181).
        val gains = FactoryPreset.AURA_HI_RES.gains.copyOf()
        gains[0] += 0.4f
        assertEquals(FactoryPreset.AURA_HI_RES, eqFactoryPresetMatch(gains))
    }

    @Test
    fun toleranceIsStrictlyBelowHalfDb() {
        val gains = FactoryPreset.AURA_HI_RES.gains.copyOf()
        gains[0] += FACTORY_PRESET_MATCH_TOLERANCE_DB
        // Exactly 0.5 dB off is NOT a match (strict <), and no other preset covers band 0 at 6.5.
        assertNull(eqFactoryPresetMatch(gains))
    }

    @Test
    fun singleBandBeyondToleranceDeselects() {
        val gains = FactoryPreset.AURA_HI_RES.gains.copyOf()
        gains[9] = 0f
        assertTrue(eqFactoryPresetMatch(gains) != FactoryPreset.AURA_HI_RES)
    }

    @Test
    fun wrongBandCountNeverMatches() {
        assertNull(eqFactoryPresetMatch(FloatArray(0)))
        assertNull(eqFactoryPresetMatch(FloatArray(EqConstants.BAND_COUNT - 1)))
        assertNull(eqFactoryPresetMatch(FloatArray(EqConstants.BAND_COUNT + 1)))
    }
}
