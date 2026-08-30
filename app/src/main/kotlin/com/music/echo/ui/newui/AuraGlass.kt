package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

/**
 * Publisher side of [LocalShellScrollActive].
 *
 * SYNCHRONOUS STATE (row 196, BETA-026 recurrence): the bus used to be pull-only
 * (`isActive()` polled every 50ms by each chrome surface) and that window was MORTAL — a short
 * flick produces 1–3 scroll frames, and ONE full-screen 18dp RenderEffect pass over the
 * re-recorded NavHost source layer is enough to sig-11 the One UI 8.5 RenderThread. So the
 * aggregated result now lives in ONE global [androidx.compose.runtime.MutableState]:
 * [ScrollStateBusReporter] writes it during composition (reactively — see there) and every
 * consumer — nav bar, mini pill, global top bar, and the haze SOURCE Box in MainActivity —
 * reads it as ordinary Compose state. The drag-in edge propagates in the SAME frame, zero
 * coroutines, zero polling.
 *
 * The provider set is still kept for registry/lifecycle bookkeeping (who is composed), but it
 * is no longer the read path: [active] is.
 */
object ShellScrollBus {
    private val states = CopyOnWriteArraySet<() -> Boolean>()

    /** The single source of truth: true while ANY composed screen scrollable is mid-gesture/fling. */
    val active = androidx.compose.runtime.mutableStateOf(false)

    fun register(provider: () -> Boolean) {
        states.add(provider)
    }

    fun unregister(provider: () -> Boolean) {
        states.remove(provider)
    }

    /**
     * Aggregates one reporter's live read with every OTHER registered provider. Called from
     * [ScrollStateBusReporter]'s composition — and reading the other providers' state-reading
     * lambdas there subscribes that composition to THEIR scroll states too, which is what keeps
     * the aggregate self-healing with zero polling: any other composed screen's scroll edge
     * recomposes this trivial reporter and rewrites the one global state.
     */
    fun publish(scrolling: Boolean) {
        active.value = scrolling || states.any { it() }
    }

    /** Legacy read kept for compatibility — the state IS the bus now. */
    fun isActive(): Boolean = active.value

    /** Re-aggregate from the providers that remain composed — called after one leaves composition. */
    fun recompute() {
        active.value = states.any { it() }
    }
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

/**
 * Publishes a scroll state to the [ShellScrollBus] while composed — screens put it next to their Lazy lists.
 *
 * SYNCHRONOUS WRITE (row 196, BETA-026 recurrence): the reporter no longer merely registers the
 * lambda for a 50ms poll to find — it writes the aggregate DURING COMPOSITION. This works and is
 * reactive because the call-sites pass lambdas that READ COMPOSE STATE (`{ listState.isScrollInProgress }`):
 * calling `isScrolling()` in the body of this composable subscribes this composition to
 * `isScrollInProgress`, so the moment the gesture starts, `isScrollInProgress` flips true, THIS
 * composable recomposes (it is the only thing that does — the lambda is otherwise a cheap read),
 * and [ShellScrollBus.active] is set true IN THE SAME FRAME the scroll does. Every consumer
 * reading [ShellScrollBus.active] (nav bar, mini pill, global top bar, the haze source Box)
 * recomposes in that same frame pass. The old 50ms poll needed 1–3 scroll frames to notice a
 * flick — and ONE frame of an 18dp RenderEffect over the re-recorded NavHost layer is the sig-11.
 */
@Composable
fun ScrollStateBusReporter(isScrolling: () -> Boolean) {
    // Read in the composition body: with a `{ listState.isScrollInProgress }` lambda this
    // subscribes to the scroll state itself — the write below is not a one-shot, it re-runs on
    // every gesture edge. Screen call-sites must keep passing state-reading lambdas (they all do).
    val scrolling = isScrolling()
    ShellScrollBus.publish(scrolling)
    // Keyed on Unit, NOT on isScrolling: the call-sites pass a fresh lambda instance on every
    // screen recomposition (the literal `{ listState.isScrollInProgress }` captures listState),
    // so keying on the lambda would unregister/re-register the provider on EVERY screen
    // recomposition — and the dispose-side recompute() below would briefly drop this provider
    // from the aggregate MID-GESTURE, un-freezing the chrome while the source still mutates.
    // Unit ties register/unregister to the reporter's real composition lifetime (enter/leave),
    // which is the only moment the set's membership — and therefore the aggregate — truly changes.
    DisposableEffect(Unit) {
        ShellScrollBus.register(isScrolling)
        // Leaving composition: this reporter's provider must stop counting. Re-aggregate from
        // the remaining registered providers — the ones still composed — so the bus never stays
        // stuck true after the scrolling screen goes away (e.g. navigating away mid-fling).
        onDispose {
            ShellScrollBus.unregister(isScrolling)
            ShellScrollBus.recompute()
        }
    }
}
