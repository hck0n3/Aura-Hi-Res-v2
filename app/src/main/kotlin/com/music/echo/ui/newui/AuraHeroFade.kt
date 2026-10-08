package iad1tya.echo.music.ui.newui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Fila 355 — the full-bleed hero cover (artist, album, YouTube playlist) DISSOLVES into the page instead of
 * ending on a line.
 *
 * 🔴 Dueño (2026-10-08, captura de Alex Zurdo con una flecha en el borde inferior de la foto): *"no me gusta
 * cómo se ve el corte al final de las portadas… arréglalo para todos los tipos de portadas que se muestran
 * con ese estilo"*.
 *
 * Why there was a line: the hero ended on an OPAQUE scrim of flat [AuraPalette.Ground], while the page right
 * under it is Ground PLUS the animated cover-colour bloom ([auraScreenBackground]), strongest exactly where a
 * square hero ends (lobes sit in the top two thirds of the screen). Flat colour above, tinted and moving
 * colour below = a visible step, and it shifts as the bloom drifts.
 *
 * The fix is a mask, not another colour: the hero's lower part fades to TRANSPARENT (DstIn over an offscreen
 * layer), so whatever the page shows — Ground, bloom, its animation — continues through the end of the image
 * with nothing to match. Eased (smoothstep) so there is no visible start of the ramp either. One offscreen
 * layer of the hero's size, redrawn only when the hero redraws: no per-frame work of its own.
 */
internal fun Modifier.auraHeroDissolve(fadeFraction: Float = HERO_DISSOLVE_FRACTION): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val brush = Brush.verticalGradient(*heroDissolveStops(fadeFraction))
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.DstIn)
        }
    }

/** Share of the hero's height, from the bottom, that dissolves into the page. */
internal const val HERO_DISSOLVE_FRACTION = 0.45f

/** How dark the hero's own scrim gets at the bottom now that the page, not a flat colour, ends it. */
internal const val HERO_SCRIM_ALPHA = 0.55f

/** Mask stops: opaque down to (1 - fraction), then a smoothstep ramp to fully transparent at the bottom. */
internal fun heroDissolveStops(fadeFraction: Float, steps: Int = 8): Array<Pair<Float, Color>> {
    val f = fadeFraction.coerceIn(0.05f, 1f)
    val start = 1f - f
    val ramp = (0..steps).map { i ->
        val t = i / steps.toFloat()
        val eased = t * t * (3f - 2f * t)
        (start + f * t) to Color.Black.copy(alpha = 1f - eased)
    }
    return (listOf(0f to Color.Black) + ramp).toTypedArray()
}
