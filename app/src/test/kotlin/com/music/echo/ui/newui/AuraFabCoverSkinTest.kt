package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the FAB cover-blur skin's pure gate (owner directive 2026-08-30: "los BOTONES FLOTANTES los
 * quiero con el mismo blur que le pusiste al mini reproductor").
 *
 * The composable itself cannot be composed on the JVM, so what is pinned here is the DECISION it
 * makes — the same discipline as [auraFloatingScrimAlpha]'s test:
 *  · the Liquid Glass master switch is the only governor of the skin (the 2026-08-29 owner
 *    directive made the stored switch the sole authority; MainActivity folds the Performance Mode
 *    veto into globalEnabled before providing it);
 *  · the pill's own no-cover rules propagate: a local track has no remote cover, and below API 31
 *    `Modifier.blur` is a no-op, so a cover drawn there would be an UNBLURRED cover — a different
 *    style, not a degraded one. The pill drops the cover in both cases; so must the skin.
 */
class AuraFabCoverSkinTest {

    @Test
    fun `glass OFF means the button is byte-identical to today - no cover, no tint`() {
        assertEquals(false, fabBlurSkinActive(globalEnabled = false, hasRemoteCover = true))
        assertEquals(false, fabBlurSkinActive(globalEnabled = false, hasRemoteCover = false))
    }

    @Test
    fun `glass ON draws the cover only when there is a remote one to blur`() {
        assertEquals(true, fabBlurSkinActive(globalEnabled = true, hasRemoteCover = true))
        // Local track / nothing playing / API < 31: the flat tint plate alone, never a raw cover.
        assertEquals(false, fabBlurSkinActive(globalEnabled = true, hasRemoteCover = false))
    }

    @Test
    fun `the blur radius is the mini pill's own 30dp frost`() {
        // The owner asked for "el mismo blur que le pusiste al mini reproductor" — the radius is
        // part of that ask. 30dp is what the pill's glass look was tuned to; drifting it would make
        // the buttons a different frost than the pill they are matching.
        assertEquals(30.dp, FabSkinBlur)
    }
}
