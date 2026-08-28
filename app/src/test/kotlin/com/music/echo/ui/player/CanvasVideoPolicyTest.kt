package iad1tya.echo.music.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cache audit H3 (HALLAZGO-037): the animated-cover (canvas) video used to force the highest
 * supported bitrate on HIGH-tier devices no matter the network — the heaviest variant of a
 * muted decorative loop downloading over mobile data. The policy pins the new rule: forced
 * highest bitrate only on HIGH tier AND unmetered network; on metered networks the canvas is
 * resolution-capped like LOW/ULTRA tiers.
 */
class CanvasVideoPolicyTest {

    @Test
    fun highestBitrateOnlyForHighTierOnUnmeteredNetwork() {
        assertTrue(canvasForceHighestBitrate(isHighTier = true, isMetered = false))
    }

    @Test
    fun meteredNetworkNeverForcesHighestBitrate() {
        assertFalse(canvasForceHighestBitrate(isHighTier = true, isMetered = true))
    }

    @Test
    fun nonHighTierNeverForcesHighestBitrate() {
        assertFalse(canvasForceHighestBitrate(isHighTier = false, isMetered = false))
        assertFalse(canvasForceHighestBitrate(isHighTier = false, isMetered = true))
    }

    @Test
    fun lowOrUltraTiersAlwaysCapResolution() {
        assertTrue(canvasCapResolution(isLowOrUltraTier = true, isMetered = false))
        assertTrue(canvasCapResolution(isLowOrUltraTier = true, isMetered = true))
    }

    @Test
    fun meteredNetworkCapsResolutionEvenOnHighTier() {
        assertTrue(canvasCapResolution(isLowOrUltraTier = false, isMetered = true))
    }

    @Test
    fun highTierOnUnmeteredNetworkKeepsFullResolution() {
        assertFalse(canvasCapResolution(isLowOrUltraTier = false, isMetered = false))
    }
}
