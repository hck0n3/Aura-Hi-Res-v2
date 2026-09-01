package iad1tya.echo.music.ui.component

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.LocalAuraFloatingChrome
import iad1tya.echo.music.ui.newui.LocalOverlayHazeState
import iad1tya.echo.music.ui.newui.auraFloatingContainerColor
import iad1tya.echo.music.ui.newui.auraFloatingContentColor
import iad1tya.echo.music.ui.newui.auraFloatingScrimColor
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.newui.shellGlass

val LocalBottomSheetPageState = compositionLocalOf { BottomSheetPageState() }

@Stable
class BottomSheetPageState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

/**
 * The page overlay host (owner directive 2026-08-31: "ese mismo efecto de los 3 puntos,
 * recontrúyelo tal cual para la búsqueda") — the EXACT same in-window glass pattern BottomSheetMenu
 * got: when the shell haze source exists AND the premium skin is on, the page (the player's
 * quick-search and every other hosted page) renders as an IN-WINDOW overlay, a SIBLING of the
 * haze source reading [LocalShellHazeState] — REAL sampled liquid glass on One UI 8.5, where the
 * system's window blur for dialogs is compiled out. The panel is the hazeChild itself (shellGlass
 * IS the surface), clip BEFORE the glass (an interior clip after it leaves square corners — audit
 * P4), BackHandler/imePadding/navigationBarsPadding paid by hand (what ModalBottomSheet did for
 * free), slide+fade timings identical to the menu. No source / no premium → the classic
 * ModalBottomSheet path, byte-identical to before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPage(
    modifier: Modifier = Modifier,
    state: BottomSheetPageState,
    background: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation),
) {
    val focusManager = LocalFocusManager.current
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    // LOGICAL TRANSPARENCY (owner 2026-08-31): sample the player when opened from the player.
    val shellHazeState = LocalOverlayHazeState.current

    if (premium && shellHazeState != null) {
        // THE IN-WINDOW GLASS PATH — same construction as BottomSheetMenu's 3-dot overlay.
        val visible = state.isVisible
        // BACK FIX (owner 2026-08-31): direct dispatcher callback — top priority over the
        // player sheet's back handling; same mechanism ModalBottomSheet used.
        val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
            ?.onBackPressedDispatcher
        androidx.compose.runtime.DisposableEffect(visible) {
            val callback = object : androidx.activity.OnBackPressedCallback(visible) {
                override fun handleOnBackPressed() {
                    focusManager.clearFocus()
                    state.dismiss()
                }
            }
            backDispatcher?.addCallback(callback)
            onDispose { callback.remove() }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(180)),
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(auraFloatingScrimColor())
                    .pointerInput(Unit) {
                        detectTapGestures { focusManager.clearFocus(); state.dismiss() }
                    },
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(animationSpec = tween(220)) { it } +
                        fadeIn(animationSpec = tween(180)),
                    exit = slideOutVertically(animationSpec = tween(200)) { it } +
                        fadeOut(animationSpec = tween(160)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = 0.85f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            // AUDIT P4: clip BEFORE the glass (an interior clip leaves the
                            // effect drawing with square corners over a rounded-content panel).
                            .clip(AuraShapes.Sheet)
                            .shellGlass(shellHazeState)
                            .imePadding()
                            .navigationBarsPadding(),
                    ) {
                        // Drag handle — TOP CENTER (owner 2026-08-31): same fix as the menu.
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
                        CompositionLocalProvider(LocalAuraFloatingChrome provides true) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp),
                            ) {
                                state.content(this)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // CLASSIC PATH — byte-identical to before any of this.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetColor = if (premium) auraFloatingContainerColor() else background
    val onSheet = if (premium) auraFloatingContentColor() else MaterialTheme.colorScheme.onSurface

    AnimatedBottomSheet(
        isVisible = state.isVisible,
        onDismissRequest = {
            focusManager.clearFocus()
            state.isVisible = false
        },
        sheetState = sheetState,
        shape = if (premium) AuraShapes.Sheet else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = sheetColor,
        contentColor = onSheet,
        scrimColor = if (premium) auraFloatingScrimColor() else Color.Black.copy(alpha = 0.32f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (premium) AuraPalette.OnGround.copy(alpha = 0.28f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
            )
        },
        modifier = modifier.fillMaxHeight(),
    ) {
        CompositionLocalProvider(LocalAuraFloatingChrome provides premium) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                state.content(this)
            }
        }
    }
}
