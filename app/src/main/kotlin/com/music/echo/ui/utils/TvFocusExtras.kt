package iad1tya.echo.music.ui.utils

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────────────────────────────────────────────────
// Plan «Fase 6.3» (row 897): the TV remote / D-pad on EVERY screen of the Aura UI.
//
// Everything here BUILDS ON TvUi.kt — the same ring ([tvFocusable]) and the same gate ([rememberIsTvOrCar]: a real
// TV / car head unit, no touchscreen, or the forced split view). There is no second ring and no second detection.
// Off that gate every helper returns its receiver unchanged, so a touch phone composes exactly what it did before.
// ──────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Inside an [AuraTvFocusScope]: whether the Aura primitives (`AuraIconButton`, `AuraRow`, `AuraMenuRow`, `AuraSwitch`,
 * `AuraChip`, `AuraPlayButton`) and the shared cover cards draw the [tvFocusable] ring THEMSELVES. `null` = no scope
 * above (the primitives draw nothing extra — the player, the shell and the screens that already place their own
 * rings stay byte-for-byte as they were).
 */
val LocalAuraTvRing = compositionLocalOf<Boolean?> { null }

/**
 * Turns the primitives' own focus ring on for [content] when the remote matters ([rememberIsTvOrCar]). ONE preference
 * read per scope (not one per row), and a nested scope reuses the outer value instead of reading it again.
 */
@Composable
fun AuraTvFocusScope(content: @Composable () -> Unit) {
    if (LocalAuraTvRing.current != null) {
        content()
        return
    }
    CompositionLocalProvider(LocalAuraTvRing provides rememberIsTvOrCar(), content = content)
}

/** The D-pad gate inside a scope (free), or [rememberIsTvOrCar] outside one. */
@Composable
fun rememberAuraTvFocus(): Boolean = LocalAuraTvRing.current ?: rememberIsTvOrCar()

/**
 * The primitives' ring: [tvFocusable] with [shape], only inside an [AuraTvFocusScope] on a remote device. If the
 * caller's modifier chain ALREADY holds a [tvFocusable] ring (the player and the shell pass one into `AuraIconButton`),
 * it adds nothing — one control, one ring, never a doubled halo or a doubled scale.
 */
@Composable
fun Modifier.auraTvRing(shape: Shape, scaleFocused: Float = 1f): Modifier {
    if (LocalAuraTvRing.current != true) return this
    val ring = Modifier.tvFocusable(enabled = true, shape = shape, scaleFocused = scaleFocused)
    return if (holdsElementOf(ring)) this else this.then(ring)
}

/**
 * True when this chain already holds an element of the same class as one of [probe]'s. Compares runtime classes taken
 * from a live [probe] (never a class name in text), so it survives R8 renaming the private ring element.
 */
internal fun Modifier.holdsElementOf(probe: Modifier): Boolean {
    val classes = probe.foldIn(emptySet<Class<*>>()) { acc, element -> acc + element.javaClass }
    if (classes.isEmpty()) return false
    return any { it.javaClass in classes }
}

/** Initial-focus retries: the target may not be attached on the first frame (a sheet animating in, a lazy list). */
internal const val TV_INITIAL_FOCUS_ATTEMPTS = 20
internal const val TV_INITIAL_FOCUS_RETRY_MS = 50L

/**
 * A remote device lands its focus HERE when this element enters the composition (or when [key] changes), so the user
 * does not have to guess a direction before anything lights up. Bounded: at most [TV_INITIAL_FOCUS_ATTEMPTS] tries
 * [TV_INITIAL_FOCUS_RETRY_MS] apart (one second), only at entry — never a loop while music plays. Put it in front of
 * the control's own clickable (it targets the next focus target in the chain or the first focusable child).
 */
@Composable
fun Modifier.tvInitialFocus(key: Any? = Unit): Modifier {
    if (!rememberAuraTvFocus()) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) { requestFocusWithRetry(requester) }
    return this.focusRequester(requester)
}

private suspend fun requestFocusWithRetry(requester: FocusRequester) {
    repeat(TV_INITIAL_FOCUS_ATTEMPTS) {
        if (runCatching { requester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) return
        delay(TV_INITIAL_FOCUS_RETRY_MS)
    }
}

/**
 * The in-window modal panels open now, oldest first. Composition runs on the main thread, so no lock. Pure, for test.
 */
internal class TvModalStack<T : Any> {
    private val open = ArrayList<T>()

    fun opened(panel: T) {
        open.add(panel)
    }

    /** Forgets [panel]; returns the panel that gets the focus back — the new top — only when [panel] WAS the top. */
    fun closed(panel: T): T? {
        val wasTop = open.lastOrNull() === panel
        open.remove(panel)
        return if (wasTop) open.lastOrNull() else null
    }
}

private val tvModalPanels = TvModalStack<() -> Unit>()

/** A D-pad / Tab move — the only focus changes a modal panel refuses to let out. Pure, for test. */
fun isDirectionalFocusMove(direction: FocusDirection): Boolean =
    direction == FocusDirection.Left || direction == FocusDirection.Right ||
        direction == FocusDirection.Up || direction == FocusDirection.Down ||
        direction == FocusDirection.Next || direction == FocusDirection.Previous

/**
 * For a sheet / dialog drawn IN the window (no dialog window of its own, so nothing stops the D-pad from walking
 * out of it onto the screen underneath the scrim): on a remote device the panel becomes a focus group that
 * (1) takes the focus into its first control when it opens and (2) keeps D-pad moves inside it. Back still closes it
 * (the panel's own back callback), and programmatic focus changes (clear on dismiss, a text field) are never
 * refused — so it is modal, not a trap. [initialFocus]: a control inside the panel to land on instead of its first
 * one — for a panel whose first control is a text field, which would raise the keyboard over the panel at once.
 * A panel opened OVER another one (a dialog from the ⋯ menu) takes the focus away and clears it when it closes; the
 * panel left on top then takes it back, or the next D-pad press would start from the screen under the scrim.
 */
@Composable
fun Modifier.tvModalFocusGroup(key: Any? = Unit, initialFocus: FocusRequester? = null): Modifier {
    if (!rememberAuraTvFocus()) return this
    val requester = remember { FocusRequester() }
    val currentInitialFocus by rememberUpdatedState(initialFocus)
    // Keyed by what the panel shows: a sheet that swaps its page in place (a menu opening its sub-menu) disposes the
    // focused row, so the focus is handed to the new page instead of being left on nothing.
    LaunchedEffect(key) { requestFocusWithRetry(initialFocus ?: requester) }
    // Only when a panel closes, never while one is open (bounded retry, nothing per frame).
    val scope = rememberCoroutineScope()
    DisposableEffect(requester) {
        val regainFocus: () -> Unit = { scope.launch { requestFocusWithRetry(currentInitialFocus ?: requester) } }
        tvModalPanels.opened(regainFocus)
        onDispose { tvModalPanels.closed(regainFocus)?.invoke() }
    }
    return this
        .focusRequester(requester)
        .focusProperties {
            onExit = { if (isDirectionalFocusMove(requestedFocusDirection)) cancelFocusChange() }
        }
        .focusGroup()
}

/**
 * The trailing button of a clickable row (the ⋯ of a song row) sits INSIDE the row's own focus target, so a D-pad
 * search from the row never finds it (row 826: «sus botones internos (tres puntos) no se alcanzan con la cruceta»).
 * This pair hands focus over by key instead: toward the trailing edge from the row goes into the trailing slot,
 * back toward the start from the slot first walks to the previous control INSIDE the slot (✕ then ↖ in Buscar) and
 * only returns to the row from the slot's first control. Every other key is left to the normal focus search, and a
 * slot with nothing focusable in it refuses the hand-over, so the key moves on as before.
 */
class TvRowTrailingFocus internal constructor(internal val focusManager: FocusManager) {
    internal val row = FocusRequester()
    internal val trailing = FocusRequester()
    internal var rowFocused = false
    internal var trailingHasFocus = false
    /** True only while [tvRowTrailingKeys] searches toward the start: the slot then refuses to let the focus out. */
    internal var searchingInSlot = false
    internal var exitRefused = false
}

/** Non-null only inside an [AuraTvFocusScope] on a remote device. */
@Composable
fun rememberTvRowTrailingFocus(): TvRowTrailingFocus? {
    if (LocalAuraTvRing.current != true) return null
    val focusManager = LocalFocusManager.current
    return remember(focusManager) { TvRowTrailingFocus(focusManager) }
}

/**
 * Toward the start from a control in the slot: moves to the previous control of the SAME slot. False when there is
 * none (the search found nothing, or found something outside the slot, which the slot refused) — the caller then
 * hands the focus back to the row.
 */
private fun TvRowTrailingFocus.moveWithinSlot(direction: FocusDirection): Boolean {
    searchingInSlot = true
    exitRefused = false
    val moved = try {
        runCatching { focusManager.moveFocus(direction) }.getOrDefault(false)
    } finally {
        searchingInSlot = false
    }
    return moved && !exitRefused
}

/** Does the trailing slot sit to the RIGHT of the row's start in [direction]? (Right-to-left mirrors the row.) Pure, for test. */
fun tvTrailingIsRight(direction: LayoutDirection): Boolean = direction != LayoutDirection.Rtl

/**
 * On the ROW, in front of its clickable: [keys] then [target]. [keys] reads arrows bubbling up from the row or its
 * trailing slot; [target] names the row's own focus target.
 */
fun Modifier.tvRowTrailingKeys(focus: TvRowTrailingFocus?, direction: LayoutDirection): Modifier {
    focus ?: return this
    val towardTrailing = if (tvTrailingIsRight(direction)) Key.DirectionRight else Key.DirectionLeft
    val towardStart = if (tvTrailingIsRight(direction)) Key.DirectionLeft else Key.DirectionRight
    val startMove = if (tvTrailingIsRight(direction)) FocusDirection.Left else FocusDirection.Right
    return this.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when {
            event.key == towardTrailing && focus.rowFocused ->
                runCatching { focus.trailing.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
            event.key == towardStart && focus.trailingHasFocus ->
                focus.moveWithinSlot(startMove) ||
                    runCatching { focus.row.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
            else -> false
        }
    }
}

/** On the row, IMMEDIATELY in front of its clickable (see [tvRowTrailingKeys]). */
fun Modifier.tvRowTarget(focus: TvRowTrailingFocus?): Modifier {
    focus ?: return this
    return this
        .focusRequester(focus.row)
        .onFocusChanged { focus.rowFocused = it.isFocused }
}

/**
 * The trailing slot of a row with a [TvRowTrailingFocus]: the slot's children laid out exactly as the row would lay
 * them ([spacing] between them, centred), plus the focus hand-over. Without [focus] it emits [content] directly.
 */
@Composable
fun TvRowTrailingSlot(focus: TvRowTrailingFocus?, spacing: Dp, content: @Composable () -> Unit) {
    if (focus == null) {
        content()
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier
            .focusRequester(focus.trailing)
            .onFocusChanged { focus.trailingHasFocus = it.hasFocus }
            // The slot is its own group, so a search toward the start tries its siblings first; while the key handler
            // searches, a move OUT of the slot is refused and reported (the handler then returns to the row instead).
            .focusProperties {
                onExit = {
                    if (focus.searchingInSlot) {
                        focus.exitRefused = true
                        cancelFocusChange()
                    }
                }
            }
            .focusGroup(),
    ) { content() }
}
