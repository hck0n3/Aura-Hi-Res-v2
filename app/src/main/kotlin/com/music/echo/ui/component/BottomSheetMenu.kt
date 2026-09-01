

package iad1tya.echo.music.ui.component

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.LocalAuraFloatingChrome
import iad1tya.echo.music.ui.newui.LocalOverlayHazeState
import iad1tya.echo.music.ui.newui.auraFloatingContainerColor
import iad1tya.echo.music.ui.newui.auraFloatingContentColor
import iad1tya.echo.music.ui.newui.auraFloatingScrimColor
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.newui.shellGlass
import iad1tya.echo.music.ui.newui.shellGlassStyle

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
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
 * The menu overlay host (owner directive 2026-08-31, "sí-o-sí" glass on One UI 8.5): when the
 * shell haze source exists AND the premium skin is on, menus render as an IN-WINDOW overlay —
 * a SIBLING of the haze source, never a descendant (haze 1.7.2's nested-hierarchy filter drops a
 * descendant's areas silently: the blur would be off-invisible; verified against the author's
 * guidance in haze#695). As a sibling reading [LocalShellHazeState], the panel samples the SAME
 * content the nav bar samples — REAL liquid glass for the big menus on Samsung, where the window
 * blur the system offers to dialogs is compiled out.
 *
 * Everything ModalBottomSheet gave for free is paid by hand here: the scrim (tap to dismiss), the
 * back gesture ([androidx.activity.compose.BackHandler] wraps the caller), the IME
 * ([Modifier.imePadding]) and the navigation bar insets. No source / no premium → the classic
 * ModalBottomSheet path, byte-identical to before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    val focusManager = LocalFocusManager.current
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    // LOGICAL TRANSPARENCY (owner 2026-08-31: "tiene que ver la parte de atrás DEL REPRODUCTOR,
    // no la de inicio"): overlays sample the PLAYER's own source when opened from the player,
    // the shell's NavHost source over normal screens — resolved by MainActivity.
    val shellHazeState = LocalOverlayHazeState.current

    if (premium && shellHazeState != null) {
        // THE IN-WINDOW GLASS PATH (sí-o-sí): full-screen Box OVER the screen content, sibling
        // of the haze source; the panel itself is the hazeChild (shellGlass IS the surface — no
        // background under it) sliding up over the scrim.
        val visible = state.isVisible
        // BACK FIX (owner 2026-08-31: "cuando hago hacia atrás no se ocultan"): the Compose
        // BackHandler was losing the priority battle with the player sheet's own back handling.
        // A callback registered DIRECTLY on the dispatcher is added AFTER everything else in
        // composition → guaranteed top priority; this is exactly how ModalBottomSheet itself
        // did it (and that always worked). Enabled only while visible; removed on dispose.
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
                            // AUDIT P4: clip BEFORE the glass — a clip AFTER the hazeChild is
                            // interior (it trims the content, not the effect draw), leaving the
                            // panel with square top corners over a rounded-content sheet.
                            .clip(AuraShapes.Sheet)
                            .shellGlass(shellHazeState)
                            .imePadding()
                            .navigationBarsPadding(),
                    ) {
                        // Drag handle — TOP CENTER (owner 2026-08-31: "lo dejaste del lado derecho,
                        // tiene que estar en la parte de arriba centrado"): a Column child wraps
                        // its width to the content, so the handle sat at the start (right-edge
                        // feel in RTL-free layouts was the Row below). fillMaxWidth centers it.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AuraPalette.OnGround.copy(alpha = 0.28f)),
                            )
                        }
                        CompositionLocalProvider(LocalAuraFloatingChrome provides true) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sheetColor = if (premium) auraFloatingContainerColor() else background
    val onSheet = if (premium) auraFloatingContentColor() else MaterialTheme.colorScheme.onSurface

    AnimatedBottomSheet(
        isVisible = state.isVisible,
        onDismissRequest = {
            focusManager.clearFocus()
            state.isVisible = false
        },
        sheetState = sheetState,
        shape = if (premium) AuraShapes.Sheet else BottomSheetDefaults.ExpandedShape,
        containerColor = sheetColor,
        contentColor = onSheet,
        scrimColor = if (premium) auraFloatingScrimColor() else BottomSheetDefaults.ScrimColor,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
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
                    .padding(horizontal = 20.dp),
            ) {
                state.content(this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    var lastContent by remember { mutableStateOf(content) }

    LaunchedEffect(content) {
        if (isVisible) {
            lastContent = content
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    if (!sheetState.isVisible && !isVisible) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
    ) {
        iad1tya.echo.music.ui.newui.AuraFrostWindowIfPremium()
        lastContent()
    }
}
