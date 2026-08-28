package iad1tya.echo.music.ui.component

import kotlin.math.pow

/**
 * Pure visual curves of the player bottom-sheet flip, extracted verbatim from BottomSheet.kt,
 * Player.kt and MainActivity.kt (HALLAZGO-027, RONDA 3 FASE 1).
 *
 * The flip used to read [BottomSheetState.progress] in the COMPOSITION phase, recomposing the
 * shell and the whole player body once per animation frame. The reads now happen inside
 * graphicsLayer lambdas at draw time; these functions are the contract those lambdas evaluate,
 * characterized by BottomSheetVisualsTest so a curve change is a deliberate, tested edit.
 */
object BottomSheetVisuals {

    /**
     * Sheet background fade-in: sqrt curve that starts at progress 0.1 (the floor keeps the
     * leading frames fully transparent) and clamps to opaque well before the top.
     */
    fun backgroundAlpha(progress: Float): Float =
        (1.4f * (progress.coerceAtLeast(0.1f) - 0.1f).pow(0.5f)).coerceIn(0f, 1f)

    /** Full-player content fade-in: 4x speed, starts at progress 0.15. */
    fun contentAlpha(progress: Float): Float =
        ((progress - 0.15f) * 4).coerceIn(0f, 1f)

    /** Mini-player fade-out: 4x speed, fully gone by progress 0.25. */
    fun miniAlpha(progress: Float): Float =
        1f - (progress * 4).coerceAtMost(1f)

    /**
     * Gate for the canvas artwork layer of the classic player: opens just above progress 0.01 so
     * the layer is not composed for the invisible leading frames of the flip.
     */
    fun canvasVisible(progress: Float): Boolean = progress > 0.01f

    /** Nav-bar slide offset: linear map of clamped progress over the slide distance, in px. */
    fun slideOffsetPx(progress: Float, slideDistancePx: Float): Float =
        slideDistancePx * progress.coerceIn(0f, 1f)
}
