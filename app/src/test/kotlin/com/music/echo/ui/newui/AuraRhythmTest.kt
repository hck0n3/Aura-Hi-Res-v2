package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the rhythm→intensity quantization (HALLAZGO-027, RONDA 3 FASE 1 second
 * pass; bucket count raised by HALLAZGO-062). Pinned behaviour: base intensity is 1.0 with zero
 * rhythm and 1.28 at full rhythm (the owner's 0.28 max boost); input is clamped to 0..1; with
 * the default 32 buckets two levels inside the same bucket map to the SAME output (that is what
 * stops the per-frame recomposition of the player tree), adjacent buckets differ by under 1% of
 * intensity (so the ground pulse reads continuous, like the old code's raw-level pulse), and the
 * mapping is monotonic.
 */
class AuraRhythmTest {

    @Test
    fun zeroRhythmIsBaseIntensity() {
        assertEquals(1f, quantizeAuraRhythmIntensity(0f), 0.0001f)
    }

    @Test
    fun fullRhythmIsBasePlusGain() {
        assertEquals(1f + AURA_RHYTHM_GAIN, quantizeAuraRhythmIntensity(1f), 0.0001f)
        assertEquals(1.28f, quantizeAuraRhythmIntensity(1f), 0.0001f)
    }

    @Test
    fun inputIsClampedToUnitRange() {
        assertEquals(1f, quantizeAuraRhythmIntensity(-0.5f), 0.0001f)
        assertEquals(1.28f, quantizeAuraRhythmIntensity(1.7f), 0.0001f)
    }

    @Test
    fun midpointMapsExactly() {
        // 0.5 * 32 = 16 buckets -> 1 + 0.28 * 0.5.
        assertEquals(1.14f, quantizeAuraRhythmIntensity(0.5f), 0.0001f)
    }

    @Test
    fun levelsInsideOneBucketAreStable() {
        // 0.501 * 32 = 16.03 -> 16 and 0.515 * 32 = 16.48 -> 16: same bucket, same output. This
        // is the whole point — per-frame jitter inside a bucket must not notify composition readers.
        assertEquals(
            quantizeAuraRhythmIntensity(0.501f),
            quantizeAuraRhythmIntensity(0.515f),
            0.0f,
        )
    }

    @Test
    fun bucketCrossingsChangeTheOutput() {
        // 0.4 -> bucket 13 (1.11375), 0.6 -> bucket 19 (1.16625).
        assertEquals(1.11375f, quantizeAuraRhythmIntensity(0.4f), 0.0001f)
        assertEquals(1.16625f, quantizeAuraRhythmIntensity(0.6f), 0.0001f)
        assertTrue(quantizeAuraRhythmIntensity(0.6f) > quantizeAuraRhythmIntensity(0.4f))
    }

    @Test
    fun adjacentBucketsStayUnderOnePercentOfIntensity() {
        // HALLAZGO-062: el pulso del ground debe leerse continuo, como el del código viejo
        // (vc951): cada escalón suma como mucho 0.28 / 32 ≈ 0.875% de intensidad, nunca el
        // salto de ~3.5% que daban los 8 buckets.
        val step = AURA_RHYTHM_GAIN / AURA_RHYTHM_STEPS
        assertTrue("escalón mayor a 1% de intensidad", step < 0.01f)
        for (i in 0 until AURA_RHYTHM_STEPS) {
            val low = quantizeAuraRhythmIntensity(i / AURA_RHYTHM_STEPS.toFloat())
            val high = quantizeAuraRhythmIntensity((i + 1) / AURA_RHYTHM_STEPS.toFloat())
            assertEquals(step, high - low, 0.0005f)
        }
    }

    @Test
    fun mappingIsMonotonic() {
        var previous = quantizeAuraRhythmIntensity(0f)
        for (i in 1..20) {
            val current = quantizeAuraRhythmIntensity(i / 20f)
            assertTrue("intensity must never decrease as level rises", current >= previous)
            previous = current
        }
    }

    @Test
    fun singleStepCollapsesToTwoLevels() {
        assertEquals(1f, quantizeAuraRhythmIntensity(0.4f, steps = 1), 0.0001f)
        assertEquals(1.28f, quantizeAuraRhythmIntensity(0.6f, steps = 1), 0.0001f)
    }

    @Test
    fun twoStepsUseHalfBuckets() {
        // 0.24 * 2 = 0.48 -> 0; 0.26 * 2 = 0.52 -> 1 (half rounds up).
        assertEquals(1f, quantizeAuraRhythmIntensity(0.24f, steps = 2), 0.0001f)
        assertEquals(1.14f, quantizeAuraRhythmIntensity(0.26f, steps = 2), 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroStepsIsRejected() {
        quantizeAuraRhythmIntensity(0.5f, steps = 0)
    }
}
