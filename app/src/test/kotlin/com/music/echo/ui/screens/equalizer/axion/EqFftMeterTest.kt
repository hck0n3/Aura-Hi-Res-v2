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

/** Row 770: the EQ meter's per-frame loop leaves once paused and decayed, instead of ticking every vsync at zero. */
class EqFftMeterLoopTest {
    private val root: java.io.File by lazy {
        generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .firstOrNull { java.io.File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val screen: String by lazy {
        java.io.File(root, "app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqScreen.kt").readText()
    }

    @Test
    fun `the loop settles only when paused and silent`() {
        val epsilon = EQ_FFT_ZERO_EPSILON
        assertTrue(eqFftMeterSettled(playing = false, bars = FloatArray(24), peak = 0f))
        assertFalse(eqFftMeterSettled(playing = true, bars = FloatArray(24), peak = 0f))
        assertFalse(eqFftMeterSettled(playing = false, bars = FloatArray(24) { epsilon * 4f }, peak = 0f))
        assertFalse(eqFftMeterSettled(playing = false, bars = FloatArray(24), peak = 0.3f))
    }

    @Test
    fun `the frame loop has an exit condition`() {
        val loop = screen.substringAfter("LaunchedEffect(enabled, playing) {").substringBefore("return snapshot")
        assertTrue(loop.contains("withFrameNanos"))
        assertTrue(loop.contains("while (isActive && !settled)"))
        assertTrue(loop.contains("settled = eqFftMeterSettled(playing, smoothed, peakOut)"))
        assertFalse(loop.contains("while (isActive) {\n            withFrameNanos"))
    }

    /** Row 770 (F-007 pattern): the crossfade swaps the ExoPlayer; the meter's listener follows the live one. */
    @Test
    fun `the meter listener re-attaches when the service swaps the player`() {
        assertTrue(screen.contains("playerConnection?.service?.playerFlow"))
        assertTrue(screen.contains("DisposableEffect(playerConnection, livePlayer) {"))
        assertTrue(screen.contains("val player = livePlayer ?: playerConnection?.player"))
        assertFalse(screen.contains("DisposableEffect(playerConnection) {\n        val player = playerConnection?.player"))
    }

    /** Row 770: the FFT gain indicator subtracts the band-boost headroom only while auto headroom is on. */
    @Test
    fun `the expected output gain follows the auto headroom switch`() {
        val gains = listOf(6.0, -3.0, 2.0)
        org.junit.Assert.assertEquals(0.0, eqFftExpectedGainDb(false, true, -2.0, gains), 0.0)
        org.junit.Assert.assertEquals(0.0, eqFftExpectedGainDb(false, false, -2.0, gains), 0.0)
        org.junit.Assert.assertEquals(-2.0, eqFftExpectedGainDb(true, false, -2.0, gains), 0.0)
        org.junit.Assert.assertEquals(
            iad1tya.echo.music.eq.audio.headroomPreampDb(-2.0, gains),
            eqFftExpectedGainDb(true, true, -2.0, gains),
            0.0,
        )
        org.junit.Assert.assertEquals(-9.0, eqFftExpectedGainDb(true, true, -2.0, gains), 1e-9)
        assertTrue(screen.contains("rememberPreference(iad1tya.echo.music.constants.AutoHeadroomEnabledKey, false)"))
        assertTrue(screen.contains("val expectedGainDb = remember(preamp, bandGains, enabled, autoHeadroom) {"))
    }
}
