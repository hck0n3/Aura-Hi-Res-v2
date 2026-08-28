package iad1tya.echo.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the bottom-sheet flip curves extracted verbatim from BottomSheet.kt,
 * Player.kt and MainActivity.kt into BottomSheetVisuals (HALLAZGO-027, RONDA 3 FASE 1).
 *
 * The flip itself was moved off the composition phase (progress reads now happen inside
 * graphicsLayer lambdas at draw time), so these numbers are the stable contract the draw phase
 * evaluates. Pinned facts: the sheet background fades in with a sqrt curve starting at progress
 * 0.1 and clamps to opaque at 1; the full-player content fades in 4x faster starting at 0.15;
 * the mini-player fades out 4x faster and is fully gone by progress 0.25; the canvas artwork
 * gate opens just above progress 0.01; and the nav-bar slide offset is a linear 0..1 mapping of
 * progress over the slide distance, clamped at both ends.
 */
class BottomSheetVisualsTest {

    // ------------------------------------------------------------------ background

    @Test
    fun backgroundAlphaIsZeroUntilProgressPointOne() {
        assertEquals(0f, BottomSheetVisuals.backgroundAlpha(0f), 0.0001f)
        assertEquals(0f, BottomSheetVisuals.backgroundAlpha(0.05f), 0.0001f)
        assertEquals(0f, BottomSheetVisuals.backgroundAlpha(0.1f), 0.0001f)
    }

    @Test
    fun backgroundAlphaClampsToOpaqueAtFullProgress() {
        // 1.4 * sqrt(0.9) = 1.328... clamps to 1 well before progress 1.
        assertEquals(1f, BottomSheetVisuals.backgroundAlpha(1f), 0.0001f)
        assertEquals(1f, BottomSheetVisuals.backgroundAlpha(0.7f), 0.0001f)
    }

    @Test
    fun backgroundAlphaRisesOnSqrtCurve() {
        // progress 0.35 -> 1.4 * sqrt(0.25) = exactly 0.7.
        assertEquals(0.7f, BottomSheetVisuals.backgroundAlpha(0.35f), 0.0001f)
    }

    // ------------------------------------------------------------------ content

    @Test
    fun contentAlphaIsZeroUntilProgressPointOneFive() {
        assertEquals(0f, BottomSheetVisuals.contentAlpha(0f), 0.0001f)
        assertEquals(0f, BottomSheetVisuals.contentAlpha(0.15f), 0.0001f)
    }

    @Test
    fun contentAlphaIsOpaqueFromProgressPointFour() {
        assertEquals(1f, BottomSheetVisuals.contentAlpha(0.4f), 0.0001f)
        assertEquals(1f, BottomSheetVisuals.contentAlpha(1f), 0.0001f)
    }

    @Test
    fun contentAlphaMidpoint() {
        // (0.275 - 0.15) * 4 = exactly 0.5.
        assertEquals(0.5f, BottomSheetVisuals.contentAlpha(0.275f), 0.0001f)
    }

    // ------------------------------------------------------------------ mini

    @Test
    fun miniAlphaIsOpaqueWhenCollapsed() {
        assertEquals(1f, BottomSheetVisuals.miniAlpha(0f), 0.0001f)
    }

    @Test
    fun miniAlphaIsFullyGoneByProgressPointTwoFive() {
        assertEquals(0f, BottomSheetVisuals.miniAlpha(0.25f), 0.0001f)
        assertEquals(0f, BottomSheetVisuals.miniAlpha(1f), 0.0001f)
    }

    @Test
    fun miniAlphaMidpoint() {
        // 1 - 0.1 * 4 = exactly 0.6.
        assertEquals(0.6f, BottomSheetVisuals.miniAlpha(0.1f), 0.0001f)
    }

    // ------------------------------------------------------------------ canvas gate

    @Test
    fun canvasGateOpensJustAboveProgressOnePercent() {
        assertFalse(BottomSheetVisuals.canvasVisible(0f))
        assertFalse(BottomSheetVisuals.canvasVisible(0.01f))
        assertTrue(BottomSheetVisuals.canvasVisible(0.011f))
        assertTrue(BottomSheetVisuals.canvasVisible(1f))
    }

    // ------------------------------------------------------------------ nav slide

    @Test
    fun slideOffsetIsLinearOverProgressAndClamped() {
        assertEquals(0f, BottomSheetVisuals.slideOffsetPx(0f, 120f), 0.0001f)
        assertEquals(60f, BottomSheetVisuals.slideOffsetPx(0.5f, 120f), 0.0001f)
        assertEquals(120f, BottomSheetVisuals.slideOffsetPx(1f, 120f), 0.0001f)
        // Overshoot/undershoot progress clamps to the 0..1 range.
        assertEquals(0f, BottomSheetVisuals.slideOffsetPx(-0.5f, 120f), 0.0001f)
        assertEquals(120f, BottomSheetVisuals.slideOffsetPx(1.5f, 120f), 0.0001f)
    }
}
