package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins HALLAZGO-034 H1: on devices whose framework cannot blur (Samsung), the veil behind
 * premium floating plates must go nearly opaque — a light translucent scrim over sharp,
 * un-blurred content is exactly the "transparente sin blur" the owner reported.
 */
class AuraFloatingChromeTest {

    @Test
    fun `scrim stays a light veil when window blur actually works`() {
        assertEquals(0.22f, auraFloatingScrimAlpha(windowBlurSupported = true))
        assertEquals(0.22f, auraDialogDimAmount(windowBlurSupported = true))
    }

    @Test
    fun `scrim and dim go nearly opaque when the framework cannot blur`() {
        val scrim = auraFloatingScrimAlpha(windowBlurSupported = false)
        val dim = auraDialogDimAmount(windowBlurSupported = false)
        // Background visibility ≈ (1 - dim) * (1 - scrim) must be a single-digit percent.
        assert(scrim >= 0.85f)
        assert(dim >= 0.5f)
        assert((1f - dim) * (1f - scrim) < 0.10f)
    }
}
