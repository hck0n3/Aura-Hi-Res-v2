package iad1tya.echo.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins HALLAZGO-034: window blur support must be decided by the OEM framework config, not
 * by the API level alone. Samsung ships `config_windowBlurEnabled=false`, so on its devices
 * every `setBackgroundBlurRadius` / `FLAG_BLUR_BEHIND` request is a silent no-op and the
 * translucent frost plates must fall back to an opaque composite instead.
 *
 * H2 (BETA-012 verdict, owner's S26 Ultra "transparente sin blur"): One UI does not expose
 * `config_windowBlurEnabled` as a framework resource at all, so the lookup returns null —
 * and null must mean UNSUPPORTED on Samsung, not the AOSP default.
 */
class WindowBlurTest {

    @Test
    fun `below API 31 window blur is never supported, whatever the OEM config says`() {
        assertFalse(windowBlurDecision(30, frameworkConfigEnabled = true, manufacturer = "Google"))
        assertFalse(windowBlurDecision(30, frameworkConfigEnabled = false, manufacturer = "samsung"))
        assertFalse(windowBlurDecision(30, frameworkConfigEnabled = null, manufacturer = "Google"))
    }

    @Test
    fun `on API 31+ an unreadable config falls back to the AOSP default (enabled) for non-Samsung`() {
        assertTrue(windowBlurDecision(31, frameworkConfigEnabled = null, manufacturer = "Google"))
        assertTrue(windowBlurDecision(36, frameworkConfigEnabled = null, manufacturer = "OnePlus"))
        assertTrue(windowBlurDecision(36, frameworkConfigEnabled = null, manufacturer = ""))
    }

    @Test
    fun `on API 31+ an unreadable config means UNSUPPORTED on Samsung (One UI hides the resource)`() {
        assertFalse(windowBlurDecision(31, frameworkConfigEnabled = null, manufacturer = "samsung"))
        assertFalse(windowBlurDecision(36, frameworkConfigEnabled = null, manufacturer = "samsung"))
        assertFalse(windowBlurDecision(36, frameworkConfigEnabled = null, manufacturer = "SAMSUNG"))
    }

    @Test
    fun `on API 31+ an explicit OEM config decides, whatever the manufacturer`() {
        assertFalse(windowBlurDecision(31, frameworkConfigEnabled = false, manufacturer = "Google"))
        assertFalse(windowBlurDecision(36, frameworkConfigEnabled = false, manufacturer = "samsung"))
        assertTrue(windowBlurDecision(31, frameworkConfigEnabled = true, manufacturer = "Google"))
        // A Samsung build that explicitly exposes the config as true is honored.
        assertTrue(windowBlurDecision(36, frameworkConfigEnabled = true, manufacturer = "samsung"))
    }
}
