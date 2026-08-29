package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * REAL glass for the new shell — the 2026 premium surface (owner-approved package "A1").
 *
 * ## What this is, and what it deliberately is NOT
 * The shell's chrome (nav bar + mini pill) has been opaque on [AuraPalette.Ground] since the redesign.
 * The "liquid glass" the owner rejected TWICE was a different thing: the CLASSIC mini's
 * `LIQUID_GLASS` style (MiniPlayerBackgroundStyleKey) — a full backdrop-SAMPLING shader with drifting
 * speculars, written unrequested by the 0.6.127 migration. [AuraGlass] does not resurrect that: it
 * uses haze's `hazeChild` with the render's OWN film language (SurfaceFill alpha + hairline +
 * GroundRaised tint), so the result reads as the design's frosted pill, now with the REAL content
 * behind it — which is exactly what the reference render's `.mi` pill asks for.
 *
 * ## Thermal / battery contract (AuraBloom.kt's rule, applied to glass)
 * `hazeChild` samples the hazeSource layer ONCE per frame the chrome is visible — that is the cost,
 * and it is why this file exists instead of sprinkling `Modifier.hazeChild` anywhere:
 *  · ONLY the two always-visible chrome surfaces opt in (nav bar, mini pill). Neither scrolls, so
 *    the sampled content is usually static while idle — the cheap case in haze's own benchmarks.
 *  · No glass on scrolling lists, lyrics, or per-frame animated surfaces (the +29–45% frame-time
 *    cases in haze's published numbers). Content scrolls BEHIND the bar; the bar itself never does.
 *  · The style pins `blurRadius = 18.dp` (below haze's 20.dp default) and `noise = 0.12` — subtle,
 *    not a showcase.
 *  · API gating: haze's RenderEffect path is a no-op below API 31 on Android 12- devices; the
 *    `fallbackTint` below keeps the bar legible there instead of silently transparent.
 */

/** The single haze source for the shell chrome. Provided by MainActivity's content Box. */
val LocalShellHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * The shell chrome's glass style: the render's frosted film — GroundRaised tinted at the render's
 * own SurfaceFill strength over a modest blur, with the design's hairline kept by the caller.
 * `fallbackTint` = the same opaque GroundRaised, so sub-API-31 devices see today's bar, not a hole.
 */
@Composable
fun shellGlassStyle(): HazeStyle = HazeStyle(
    backgroundColor = AuraPalette.GroundRaised,
    blurRadius = 18.dp,
    noiseFactor = 0.12f,
    tints = listOf(HazeTint(AuraPalette.GroundRaised.copy(alpha = 0.72f))),
    fallbackTint = HazeTint(AuraPalette.GroundRaised),
)

/**
 * Marks the CONTENT that the shell chrome samples. Put it on the Box that hosts the scaffold body —
 * NOT on the nav bar. Null-safe: when no source is provided (e.g. a preview) this is a no-op.
 */
fun Modifier.shellHazeSource(state: HazeState?): Modifier =
    if (state == null) this else haze(state)

/**
 * Applies the chrome glass to a surface (nav bar / mini pill), sampling whatever [shellHazeSource]
 * marked. Call from a @Composable (it reads [shellGlassStyle]). Null-safe: without a source the
 * caller keeps its own opaque ground, so previews and sub-API-31 devices render today's look.
 */
@Composable
fun Modifier.shellGlass(state: HazeState?): Modifier {
    if (state == null) return this
    val style = shellGlassStyle()
    return hazeChild(state = state, style = style)
}
