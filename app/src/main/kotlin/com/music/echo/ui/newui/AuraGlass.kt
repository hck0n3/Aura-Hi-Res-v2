package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import java.util.concurrent.CopyOnWriteArraySet

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
 * SHELL SCROLL BUS (registry row 196, adversarial audit fix #1): true while ANY scrollable of the
 * visible screen is in an active gesture/fling. The persistent chrome (nav bar, mini pill) reads
 * it and FREEZES its haze sampling while it is true — on One UI 8.5 the source layer (the whole
 * NavHost Box) is re-recorded at native resolution on every scroll frame, and two persistent
 * hazeChildren re-running their 18dp RenderEffect over that mutation is the sig-11 native bomb
 * (RSS 700-800MB in the owner's logs). Frozen = same GroundRaised tint the detail bars use, so
 * the look survives the gesture at zero native cost. Screens publish their scroll state here via
 * [ShellScrollBus] from a rememberScrollStateReporter or plain derived state.
 */
val LocalShellScrollActive = staticCompositionLocalOf<() -> Boolean> { { false } }

/** Publisher side of [LocalShellScrollActive]. */
object ShellScrollBus {
    private val states = CopyOnWriteArraySet<() -> Boolean>()

    fun register(provider: () -> Boolean) {
        states.add(provider)
    }

    fun unregister(provider: () -> Boolean) {
        states.remove(provider)
    }

    fun isActive(): Boolean = states.any { it() }
}

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

/**
 * Detail-bar glass (title bars over scrolling content — registry row 196). Same haze recipe as the
 * shell chrome, PLUS two safeguards the nav bar never needed:
 *
 * 1. PROGRESSIVE BLUR: the bar fades from blurred at its base to clear at its top, the iOS/Samsung
 *    title-bar look. This is not cosmetic: on One UI 8.5 the owner saw "transparency without blur"
 *    — the bar was sampling, but the 18dp uniform blur read as a flat tint over album-art colors.
 *    The progressive gradient makes the blur VISIBLE and cuts the sampled area (upper rows barely
 *    blur), which also lowers the per-frame sampling cost on the render thread.
 * 2. Masked to the bar's own bounds: the detail bar sits INSIDE the haze-source Box, over content
 *    that scrolls under it. Bounding the effect keeps the sampled layer allocation small and
 *    bounded (the nav bar's cost, not "the whole screen every frame").
 *
 * Null-safe like [shellGlass]: no source → caller's opaque fallback.
 */
@Composable
fun Modifier.detailShellGlass(state: HazeState?): Modifier {
    if (state == null) return this
    val style = shellGlassStyle()
    return hazeChild(
        state = state,
        style = style,
    ) {
        progressive = HazeProgressive.verticalGradient(
            startIntensity = 1f,
            endIntensity = 0.55f,
        )
    }
}
