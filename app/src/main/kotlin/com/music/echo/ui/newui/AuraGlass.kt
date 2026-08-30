package iad1tya.echo.music.ui.newui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.ui.component.LocalGlassEffectConfig
import iad1tya.echo.music.utils.isLocalMediaId
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

// ── Floating action buttons: the mini player's cover-blur skin (owner directive 2026-08-30) ───────
//
// "los BOTONES FLOTANTES los quiero con el mismo blur que le pusiste al mini reproductor" — the
// owner is asking for the pill's own technique, NOT for a new haze surface. The mini pill's
// "Desenfoque" is the CURRENT TRACK'S COVER, decoded once at 128×128 and blurred once with the
// NATIVE `Modifier.blur(30.dp)` under a frost tint (AuraShell.kt → AuraGroundLayer /
// AuraCoverGround). That technique works on every OEM — it is the same `Modifier.blur` the
// expanded player's covers use — and its cost is one static bitmap per track, zero per-frame work.
//
// ## Why this lives in AuraGlass.kt but does NOT touch haze
// Registry row 196 forbids adding hazeChild surfaces while the native sig-11 audit is open: every
// additional sampler multiplies the re-recorded source layer's cost on One UI 8.5. This skin never
// calls haze/hazeSource/hazeChild — it paints its OWN backdrop (the cover), so there is nothing to
// sample. A FAB is also 52–56 dp against the pill's full width: sampling the screen behind a
// floating button would be the most expensive blur in the app for the least visible one.
//
// ## Thermal contract (AGENTS rule 7)
// ONE 128×128 decode per track — byte for byte the request `AuraCoverGround` makes
// (AuraPlayer.kt:2419: `.size(128, 128).allowHardware(false).crossfade(false)`), so it shares the
// same Coil memory-cache slot the pill and the bloom already fill: for a track whose ground has
// resolved, this costs NO decode at all. The blur rasterizes once per bitmap; a static image under
// `Modifier.blur` does not re-run the RenderEffect on frames where nothing invalidates it, and a
// FAB neither scrolls nor animates per frame. No animation is attached to the skin — the buttons
// keep exactly the motion they already had.

/**
 * The pure gate of the cover-blur skin, extracted for the test. Mirrors [auraFloatingScrimAlpha]'s
 * discipline: the decision is a function of facts, so it can be pinned without a device.
 *
 * · `globalEnabled` — the Liquid Glass master switch (owner directive 2026-08-29 made the stored
 *   switch the governor; MainActivity folds Performance Mode's veto into `globalEnabled` before
 *   providing it, so OFF here already means "the user turned it off OR Performance Mode is on").
 * · `hasRemoteCover` — the pill's own rule: a local track has no remote cover to blur, and below
 *   API 31 `Modifier.blur` is a no-op (drawing an UNBLURRED cover there would be a different
 *   style, not a degraded one — the pill drops the cover below API 31 too).
 */
internal fun fabBlurSkinActive(globalEnabled: Boolean, hasRemoteCover: Boolean): Boolean =
    globalEnabled && hasRemoteCover

/**
 * The ONE resolver of the skin's decision chain: the reactive `LocalGlassEffectConfig` read (the
 * master switch + Performance Mode veto, provided by MainActivity) folded with the current track's
 * remote cover through [fabBlurSkinActive]. Both [AuraFabCoverSkin] and [fabBlurSkinOn] call it,
 * so what paints and what the ink-adapters see is the SAME fact from the SAME origin — the
 * registry's row-40 lesson ("when two sites compare the same fact, they must derive it from the
 * same origin with the same predicate").
 *
 * Null = no skin: switch off, nothing playing, a local track, or API < 31.
 */
@Composable
private fun currentFabCoverUrl(): String? {
    // Switch OFF → bail BEFORE subscribing to anything: with the Liquid Glass master switch off
    // the five skinned buttons across the app cost zero reads, zero subscriptions, zero work.
    if (!LocalGlassEffectConfig.current.globalEnabled) return null
    val playerConnection = LocalPlayerConnection.current ?: return null
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coverUrl = mediaMetadata
        ?.takeIf { !it.id.isLocalMediaId() }
        ?.thumbnailUrl
        ?.takeIf { it.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    return if (fabBlurSkinActive(globalEnabled = true, hasRemoteCover = coverUrl != null)) coverUrl else null
}

/** Blur radius of the FAB skin, in dp. The pill's `auraPillRecipe` keeps the sheet's `coverBlur`
 *  (34–52 dp per style); 30 dp is the radius the mini pill's glass look was tuned to and reads as
 *  the same frost at a button's scale. */
internal val FabSkinBlur = 30.dp

/**
 * The frost drawn OVER the blurred cover — the SAME `GroundRaised.copy(alpha = 0.72f)` plate the
 * shell chrome freezes to mid-scroll (registry row 196) and the detail bars wear. It is what
 * makes light ink on a bright sleeve legible: the blurred cover colours the skin, the tint holds
 * the contrast. */
internal val FabSkinTint: Color get() = AuraPalette.GroundRaised.copy(alpha = 0.72f)

/**
 * Whether the skin is actually PAINTING right now — for call sites that must adapt INK to the new
 * ground (the accent FAB's glyph: [AuraPalette.OnAccent] is dark ink for the teal gradient, but the
 * skin's frost is a dark plate, so that glyph must switch to [AuraPalette.Teal] while the skin is
 * on). Reads the SAME resolver [currentFabCoverUrl] the skin reads — one origin for the whole
 * decision chain (registry row 40's lesson), so the ink and the plate can never disagree: they
 * recompose off the same state in the same frame.
 *
 * True only when the Liquid Glass switch is on AND the current track has a remote cover — when the
 * skin is not painting, the button is today's, and so is its ink.
 */
@Composable
fun fabBlurSkinOn(): Boolean = currentFabCoverUrl() != null

/**
 * The mini player's cover-blur skin, as the FIRST CHILD of a floating action button's Box.
 * [BoxScope] receiver on purpose: the skin sizes itself with [BoxScope.matchParentSize], so it can
 * only be mounted from inside a Box — a plain `Modifier.fillMaxSize` here would inflate a
 * wrap-content pill to the incoming maximum width (the documented reason `matchParentSize` exists),
 * and the receiver makes that misuse impossible to write.
 *
 * [shape] is the button's own shape (the one its `clip` uses) — the skin draws the SAME hairline the
 * button ships ([AuraPalette.SurfaceLine], 1 dp) on top of its frost. It has to: a node's
 * `background`/`border` draw UNDER its children, so the button's own hairline is covered by this
 * full-bounds skin — without the redraw the frost would arrive edge-less, and the pill the owner
 * named as the reference keeps its hairline OVER its frost (the pill's border sits in its own
 * modifier chain and its children never reach the edge). With the skin OFF nothing here runs and
 * the button's own hairline is the visible one, exactly as today.
 *
 * ## What it draws, bottom to top
 * 1. The button's OWN ground, untouched underneath (opaque [AuraPalette.FloatingFill] /
 *    [AuraPalette.SurfaceFill] / [AuraPalette.PlayButtonGradient] — whatever that button ships).
 *    The skin composites OVER it, so a cover that fails to decode can never open a transparency
 *    hole — the pill's own discipline (an opaque fill under every layer).
 * 2. The current track's cover, one 128×128 decode (byte for byte the pill's ground request, so it
 *    SHARES the Coil memory-cache slot the pill and the bloom already fill — a resolved track
 *    costs NO extra decode), cropped to the button, blurred [FabSkinBlur] with the NATIVE
 *    `Modifier.blur` — the same blur the expanded player's covers use, on every OEM.
 * 3. The [FabSkinTint] frost — the same `GroundRaised.copy(0.72f)` plate the shell chrome freezes
 *    to mid-scroll, which holds light ink legible over a bright sleeve — plus the hairline above.
 *
 * The button keeps its own `clip(shape)` — it cuts the skin with it (no second clip, no double-cut
 * radius), its clickable, and its motion. `contentAlignment` and the sibling order are the caller's:
 * the skin contributes NOTHING to the Box's size (matchParentSize), so the button's layout is
 * decided by its real content exactly as today.
 *
 * ## Toggle — OFF (and no-cover) is byte-identical
 * [LocalGlassEffectConfig.globalEnabled] governs (the REACTIVE master switch MainActivity provides
 * — the stored Liquid Glass key AND the Performance Mode veto, folded together; flipping the switch
 * in Ajustes flips the FABs in the same frame, no restart, no poll). OFF → this composes NOTHING:
 * today's plate, hairline, gradient, glyph, pixel for pixel. The same holds when there is no remote
 * cover to blur — nothing playing, a local track, or API < 31 (where `Modifier.blur` is a no-op and
 * drawing an UNBLURRED cover would be a different style, not a degraded one): the pill gates its
 * scrim and its cover TOGETHER for exactly this reason ("drawing the scrim anyway would only
 * darken the ground for nothing"), and so does the skin — no cover, no frost, the button untouched.
 *
 * ## Thermal contract (AGENTS rule 7)
 * One static 128×128 bitmap per track, `remember`-keyed by URL. No animation is attached — the
 * buttons keep exactly the motion they already had, and nothing here invalidates per frame. The
 * blur rasterizes once per bitmap; a FAB neither scrolls nor samples the screen (registry row 196:
 * NOT a haze surface — nothing behind it is recorded or re-blurred by scrolling).
 */
@Composable
fun BoxScope.AuraFabCoverSkin(shape: androidx.compose.ui.graphics.Shape) {
    val context = LocalContext.current
    val coverUrl = currentFabCoverUrl() ?: return
    Box(modifier = Modifier.matchParentSize()) {
        // Byte for byte the request the mini pill's ground makes (AuraPlayer.kt `AuraCoverGround`:
        // 128×128, no hardware, no crossfade) — same Coil memory-cache slot, no second decode.
        val request = remember(context, coverUrl) {
            ImageRequest.Builder(context)
                .data(coverUrl)
                .size(128, 128)
                .allowHardware(false)
                .crossfade(false)
                .build()
        }
        // Bottom layer: the blurred cover. Unclipped on purpose — the parent button's own
        // `clip(shape)` already cuts this layer to the pill/circle.
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(FabSkinBlur),
        )
        // Top layer: the frost tint OVER the cover (the pill's scrim stacking — cover first, then
        // the plate that keeps light ink legible on a bright sleeve), carrying the hairline the
        // button's own chain drew under this full-bounds child.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FabSkinTint)
                .border(1.dp, AuraPalette.SurfaceLine, shape),
        )
    }
}

