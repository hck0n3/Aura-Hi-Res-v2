package iad1tya.echo.music.ui.screens.equalizer.axion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins HALLAZGO-055: the EQ meter loop must recognize a spectrum that decayed to visual
 * silence so it can publish the [EqFftSnapshot.Zero] singleton instead of allocating a new
 * snapshot (FloatArray copy) on every frame — an allocation that never compares equal and
 * therefore invalidated the meter subtree every frame while idle.
 */
class EqFftMeterTest {

    private val epsilon = EQ_FFT_ZERO_EPSILON

    @Test
    fun `an all-zero spectrum with zero peak is converged`() {
        assertTrue(eqFftConvergedToZero(FloatArray(24), peak = 0f, epsilon = epsilon))
    }

    @Test
    fun `residue below the visual quantum still counts as converged`() {
        val bars = FloatArray(24) { epsilon * 0.5f }
        assertTrue(eqFftConvergedToZero(bars, peak = epsilon * 0.9f, epsilon = epsilon))
    }

    @Test
    fun `any bar above the quantum is not converged`() {
        val bars = FloatArray(24)
        bars[10] = epsilon * 2f
        assertFalse(eqFftConvergedToZero(bars, peak = 0f, epsilon = epsilon))
    }

    @Test
    fun `a live peak is not converged even with flat bars`() {
        assertFalse(eqFftConvergedToZero(FloatArray(24), peak = 0.2f, epsilon = epsilon))
    }

    @Test
    fun `empty spectrum converges only through the peak`() {
        assertTrue(eqFftConvergedToZero(FloatArray(0), peak = 0f, epsilon = epsilon))
        assertFalse(eqFftConvergedToZero(FloatArray(0), peak = 1f, epsilon = epsilon))
    }
}
