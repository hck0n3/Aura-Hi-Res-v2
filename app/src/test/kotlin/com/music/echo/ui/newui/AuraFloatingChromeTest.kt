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

    // Owner directive 2026-08-29 (S26 Ultra / One UI 8.5): with the Liquid Glass master switch
    // ON the owner ACCEPTS the sharp, un-blurred background — he asked for the translucent
    // look on Samsung, so the light veil is kept even without window blur.

    @Test
    fun `forced glass keeps the light veil on a no-blur OEM`() {
        assertEquals(0.22f, auraFloatingScrimAlpha(windowBlurSupported = false, glassForced = true))
        assertEquals(0.22f, auraDialogDimAmount(windowBlurSupported = false, glassForced = true))
    }

    @Test
    fun `forced glass changes nothing when window blur actually works`() {
        assertEquals(0.22f, auraFloatingScrimAlpha(windowBlurSupported = true, glassForced = true))
        assertEquals(0.22f, auraDialogDimAmount(windowBlurSupported = true, glassForced = true))
    }

    @Test
    fun `glass OFF keeps the HALLAZGO-034 opaque fallback on a no-blur OEM`() {
        // Default glassForced = false must be byte-identical to the pre-2026-08-29 behavior:
        // this is the path every user with the switch off takes, on every OEM.
        assertEquals(0.88f, auraFloatingScrimAlpha(windowBlurSupported = false, glassForced = false))
        assertEquals(0.60f, auraDialogDimAmount(windowBlurSupported = false, glassForced = false))
    }
}
