package iad1tya.echo.music.ui.newui

import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import timber.log.Timber
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

/**
 * The in-window floating-dialog host (owner directive 2026-09-01: "ese mismo estilo que sale
 * cuando toco el de búsqueda del reproductor... también para la ventana de listas con IA, crear
 * lista, importar lista (flotante y en medio)"). The EXACT construction the quick-search and
 * the 3-dot menu got — one visual language for every floating surface:
 *
 * - IN-WINDOW overlay (no separate window): the panel samples [LocalOverlayHazeState] — the
 *   PLAYER's own source when opened from the player, the shell's NavHost source otherwise — so
 *   the glass shows what is actually behind it (logical transparency).
 * - [center] = true → a floating CENTERED card (small dialogs: create/import playlist, AI
 *   playlist); false → a bottom sheet with the top-center drag handle (the search/menu look).
 * - Back: a callback registered DIRECTLY on the dispatcher (top priority — the mechanism that
 *   fixed the overlays not closing on back).
 * - IME: imePadding on the panel; the scrim dismisses on outside tap.
 *
 * THE MINI-PLAYER GLASS (owner 2026-09-14): with an [AuraOverlayHostState] the panel is PORTED to the
 * activity-level outlet, a sibling of the haze source, so its shellGlass really blurs what is behind
 * — the same film, noise, tint and hairline as the mini pill. Without a host it composes in place,
 * where the glass is a no-op and the double plate carries it (the 2026-09-04 contrast step).
 *
 * WITHOUT a haze source (glass OFF / classic shell): the panel composes with the premium frost
 * (auraFloatingContainerColor) — the same translucent family, never a hard window. Returns
 * without composing anything when the premium skin is OFF (callers keep their classic window).
 */
@Composable
fun AuraInWindowDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    center: Boolean = true,
    // OWNER REPORT (2026-09-04): tapping the scrim while the AI playlist was generating closed
    // the dialog and silently killed the generation. Hosts that must NOT dismiss on outside tap
    // while busy (AiPlaylistDialog) pass false here.
    dismissOnOutsideTap: Boolean = true,
    // Bottom panels only: false wraps the content (short menus, the audio output card) instead of
    // taking 85 % of the screen.
    fullHeight: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    if (!(skin.enabled && skin.darkGround)) return

    val ported = rememberAuraOverlayPortal {
        AuraInWindowDialogPanel(visible, onDismiss, modifier, center, dismissOnOutsideTap, fullHeight, ported = true, content)
    }
    if (!ported) {
        AuraInWindowDialogPanel(visible, onDismiss, modifier, center, dismissOnOutsideTap, fullHeight, ported = false, content)
    }
}

@Composable
private fun AuraInWindowDialogPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier,
    center: Boolean,
    dismissOnOutsideTap: Boolean,
    fullHeight: Boolean,
    ported: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val overlayHazeState = LocalOverlayHazeState.current

    // STALE-CLOSURE FIX (audit 2026-09-04): the scrim's pointerInput(Unit) and the back callback
    // captured the onDismiss lambda of the composition where they STARTED (dialog opening,
    // busy == false). A recomposition (busy → true) never restarted them, so a tap during the
    // generation ran the OLD lambda and closed + reset the dialog — the owner's report. The
    // rememberUpdatedState pair below keeps both entry points executing the CURRENT lambdas.
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentDismissOnOutsideTap by rememberUpdatedState(dismissOnOutsideTap)

    // REMOTE DIAGNOSIS (2026-09-04, the "phantom dialog" report): a device app.log must be able
    // to answer "did the in-window dialog actually compose?" — the phantom case was an open but
    // unreadable panel. Facts only, no user data (regla 4).
    LaunchedEffect(visible, overlayHazeState != null, center, ported) {
        if (visible) {
            Timber.i(
                "InWindowDialog: composed visible, source=%s, center=%s, ported=%s",
                if (overlayHazeState != null) "haze" else "none", center, ported,
            )
        }
    }

    // Back: dispatcher-registered callback — top priority while visible.
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
        ?.onBackPressedDispatcher
    DisposableEffect(visible) {
        val callback = object : OnBackPressedCallback(visible) {
            override fun handleOnBackPressed() {
                focusManager.clearFocus()
                currentOnDismiss()
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    val shape = if (center) AuraShapes.Card else AuraShapes.Sheet

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(auraFloatingScrimColor())
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                        if (currentDismissOnOutsideTap) currentOnDismiss()
                    }
                },
        ) {
          // KEYBOARD (owner report 2026-09-14: "se estiran hasta abajo cuando se abre el teclado"): the
          // IME inset used to be padding INSIDE the panel, so the card grew down to the keyboard. It is
          // now taken off the area the panel is placed in: the card keeps its size and moves above it.
          Box(modifier = Modifier.fillMaxSize().imePadding()) {
            AnimatedVisibility(
                visible = visible,
                enter = if (center) {
                    scaleIn(initialScale = 0.92f, animationSpec = tween(220)) +
                        fadeIn(animationSpec = tween(180))
                } else {
                    slideInVertically(animationSpec = tween(220)) { it } +
                        fadeIn(animationSpec = tween(180))
                },
                exit = if (center) {
                    fadeOut(animationSpec = tween(160))
                } else {
                    slideOutVertically(animationSpec = tween(200)) { it } +
                        fadeOut(animationSpec = tween(160))
                },
                modifier = if (center) {
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .then(if (fullHeight) Modifier.fillMaxHeight(fraction = 0.85f) else Modifier)
                },
            ) {
                Column(
                    modifier = Modifier
                        .then(
                            when {
                                center -> Modifier
                                fullHeight -> Modifier.fillMaxSize()
                                else -> Modifier.fillMaxWidth()
                            },
                        )
                        // Clip BEFORE the glass (audit P4 — an interior clip leaves square corners).
                        .clip(shape)
                        // A tap on the panel must not reach the scrim's dismiss detector.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .then(
                            when {
                                // PORTED: a sibling of the haze source — the glass renders, so this is
                                // the mini pill's exact material (shellGlass film + hairline). The one
                                // plate underneath only shows during the first frame before sampling.
                                overlayHazeState != null && ported -> Modifier
                                    .background(AuraPalette.FloatingFill)
                                    .shellGlass(overlayHazeState)
                                // IN PLACE (no host): descendant of the source, glass is a no-op —
                                // PLATE UNDER THE GLASS, double-composited for contrast (row 197/204,
                                // the 2026-09-04 "phantom dialog" report).
                                overlayHazeState != null -> Modifier
                                    .background(AuraPalette.FloatingFill)
                                    .background(AuraPalette.FloatingFill)
                                    .shellGlass(overlayHazeState)
                                // No source: the premium frost family — translucent, never hard.
                                else -> Modifier.background(auraFloatingContainerColor())
                            },
                        )
                        .border(1.dp, AuraPalette.SurfaceLine, shape)
                        .then(if (center) Modifier else Modifier.navigationBarsPadding()),
                ) {
                    if (!center) {
                        // Drag handle — TOP CENTER (the owner's centered-handle directive).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 32.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AuraPalette.OnGround.copy(alpha = 0.28f)),
                            )
                        }
                    }
                    content()
                }
            }
          }
        }
    }
}
