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
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val overlayHazeState = LocalOverlayHazeState.current
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround

    if (!premium) return

    // Back: dispatcher-registered callback — top priority while visible.
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
        ?.onBackPressedDispatcher
    DisposableEffect(visible) {
        val callback = object : OnBackPressedCallback(visible) {
            override fun handleOnBackPressed() {
                focusManager.clearFocus()
                onDismiss()
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

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
                    detectTapGestures { focusManager.clearFocus(); onDismiss() }
                },
        ) {
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
                        .fillMaxHeight(fraction = 0.85f)
                },
            ) {
                Column(
                    modifier = Modifier
                        .then(if (center) Modifier else Modifier.fillMaxSize())
                        // Clip BEFORE the glass (audit P4 — an interior clip leaves square corners).
                        .clip(if (center) AuraShapes.Card else AuraShapes.Sheet)
                        .then(
                            if (overlayHazeState != null) {
                                // PLATE UNDER THE GLASS (row 197/204 pattern, owner report
                                // 2026-09-04: "crear lista no funciona"). Every DefaultDialog
                                // caller (create/import playlist, AI playlist, ~44 more) is composed
                                // INSIDE the NavHost — a DESCENDANT of the shell haze source — and
                                // in haze 1.7.2 the descendant filter is a SILENT no-op: the glass
                                // paints nothing, and a glass-only panel left the dialog as floating
                                // glyphs over the scrim ("the dialog doesn't open"). The plate
                                // paints under the glass exactly like AuraFab does: when the glass
                                // renders (sibling callers like the cloud menu) it covers the
                                // plate; when it's a no-op, the plate IS the panel. Same material
                                // as the no-source fallback — no hole by construction.
                                Modifier
                                    .background(AuraPalette.FloatingFill)
                                    .shellGlass(overlayHazeState)
                            } else {
                                // No source: the premium frost family — translucent, never hard.
                                Modifier.background(auraFloatingContainerColor())
                            },
                        )
                        .then(if (center) Modifier else Modifier.navigationBarsPadding())
                        .imePadding(),
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
