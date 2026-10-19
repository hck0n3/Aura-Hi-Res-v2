package iad1tya.echo.music.ui.component

import androidx.compose.ui.graphics.RectangleShape

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.widthIn
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.LocalAuraFloatingChrome
import iad1tya.echo.music.ui.newui.LocalAuraSpotlightSheet
import iad1tya.echo.music.ui.newui.LocalOverlayHazeState
import iad1tya.echo.music.ui.newui.auraFloatingContainerColor
import iad1tya.echo.music.ui.newui.auraFloatingContentColor
import iad1tya.echo.music.ui.newui.auraFloatingScrimColor
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.newui.floatingWindowGlass
import iad1tya.echo.music.ui.newui.rememberAuraSheetMaxHeight
import iad1tya.echo.music.ui.newui.AuraSheetHandle
import iad1tya.echo.music.ui.newui.auraSheetDragOffset

val LocalBottomSheetPageState = compositionLocalOf { BottomSheetPageState() }

@Stable
class BottomSheetPageState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    /** Full-screen page (the player's quick search) instead of the 85 % floating sheet. */
    var fullScreen by mutableStateOf(false)

    /**
     * Panel compacto estilo Spotlight de macOS (la búsqueda rápida del reproductor, dueño 2026-10-18): flota arriba,
     * centrado y con ancho máximo, mide su contenido y solo crece cuando hay resultados. Sustituye a [fullScreen] en
     * quien lo pida; el contenido lo sabe por [LocalAuraSpotlightSheet].
     */
    var spotlight by mutableStateOf(false)

    fun show(fullScreen: Boolean = false, spotlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
        this.fullScreen = fullScreen
        this.spotlight = spotlight
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
        if (state.spotlight) {
            AuraSpotlightHost(modifier = modifier, state = state, visible = visible, hazeState = shellHazeState)
            return
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
                val sheetMaxHeight = rememberAuraSheetMaxHeight()
                // Cerrar arrastrando la barrita (dueño, 2026-10-18). Estado propio de ESTA apertura.
                val drag = iad1tya.echo.music.ui.newui.rememberAuraSheetDragState()
                androidx.compose.runtime.LaunchedEffect(visible) { if (visible) drag.offsetPx = 0f }
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(animationSpec = tween(220)) { it } +
                        fadeIn(animationSpec = tween(180)),
                    exit = slideOutVertically(animationSpec = tween(200)) { it } +
                        fadeOut(animationSpec = tween(160)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .auraSheetDragOffset(drag)
                        // Plegable abierto / tableta: centrada con ancho máximo (ver AuraSheetMaxWidthDp).
                        // widthIn VA ANTES de fillMaxWidth: así fillMaxWidth llena el tope, no la ventana.
                        .widthIn(
                            max = (
                                if (state.fullScreen) iad1tya.echo.music.ui.newui.AuraFullScreenSheetMaxWidthDp
                                else iad1tya.echo.music.ui.newui.AuraSheetMaxWidthDp
                                ).dp,
                        )
                        .fillMaxWidth()
                        // A pantalla completa se llena de verdad (la búsqueda rápida la pide así, con
                        // el teclado arriba). En el resto el 85 % pasa a ser TOPE y no objetivo: una
                        // página con poco contenido sube solo lo que necesita. Ver
                        // [rememberAuraSheetMaxHeight].
                        .then(
                            if (state.fullScreen) {
                                Modifier.fillMaxHeight()
                            } else {
                                Modifier.heightIn(max = sheetMaxHeight)
                            },
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .then(
                                if (state.fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                            )
                            // AUDIT P4: clip BEFORE the glass (an interior clip leaves the
                            // effect drawing with square corners over a rounded-content panel).
                            .clip(if (state.fullScreen) RectangleShape else AuraShapes.Sheet)
                            // Same opaque floating plate as menus and dialogs (one material everywhere).
                            .background(AuraPalette.FloatingWindowFill)
                            .floatingWindowGlass(shellHazeState)
                            .then(if (state.fullScreen) Modifier.statusBarsPadding() else Modifier)
                            .imePadding()
                            .navigationBarsPadding(),
                    ) {
                        // Drag handle — TOP CENTER (owner 2026-08-31): same fix as the menu. Draggable down to close
                        // (owner 2026-10-18).
                        AuraSheetHandle(
                            state = drag,
                            onDismiss = { focusManager.clearFocus(); state.dismiss() },
                        )
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
        // CLÁSICO: sin `fillMaxHeight()`. Lo forzaba a pantalla casi completa aunque la página
        // tuviera dos filas — el mismo defecto que arriba, y la misma queja. Una `ModalBottomSheet`
        // de Material se ajusta sola a su contenido y se desplaza por dentro cuando no cabe.
        modifier = modifier,
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

/**
 * El panel compacto de la búsqueda rápida (estilo Spotlight de macOS; dueño, 2026-10-18: *«no me gusta cómo sale la
 * búsqueda, creo que la puedes hacer más compacta al estilo de la búsqueda en macOS»*). Antes era una hoja a pantalla
 * completa que, con la ventana ancha de un plegable, dejaba medio metro de nada debajo del campo.
 *
 * Flota arriba y centrada (ancho máximo 640 dp), mide lo que mide su contenido (campo + sugerencias; crece con los
 * resultados) y nunca pasa del 72 % de la pantalla ni se mete debajo del teclado. Sin barrita: se cierra tocando fuera,
 * con atrás o con la flecha del campo. Mismo material que el resto de láminas flotantes (placa opaca + cristal + línea).
 */
@Composable
private fun AuraSpotlightHost(
    modifier: Modifier,
    state: BottomSheetPageState,
    visible: Boolean,
    hazeState: dev.chrisbanes.haze.HazeState,
) {
    val focusManager = LocalFocusManager.current
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(140)),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(auraFloatingScrimColor())
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus(); state.dismiss() }
                },
        ) {
            // El área ya descuenta barra de estado y teclado: la hoja nunca queda tapada por el teclado.
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                val cap = minOf(maxHeight, (screenHeightDp * 0.72f).dp)
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    enter = slideInVertically(animationSpec = tween(200)) { -it / 4 } +
                        fadeIn(animationSpec = tween(160)),
                    exit = slideOutVertically(animationSpec = tween(160)) { -it / 4 } +
                        fadeOut(animationSpec = tween(120)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // widthIn VA ANTES de fillMaxWidth: así fillMaxWidth llena el tope, no la ventana.
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .heightIn(max = cap),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Clip ANTES del cristal (un recorte interior deja esquinas cuadradas bajo el efecto).
                            .clip(AuraShapes.Card)
                            .background(AuraPalette.FloatingWindowFill)
                            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
                            .floatingWindowGlass(hazeState)
                            // Un toque en el panel no debe llegar al cierre del fondo.
                            .pointerInput(Unit) { detectTapGestures { } },
                    ) {
                        CompositionLocalProvider(
                            LocalAuraFloatingChrome provides true,
                            LocalAuraSpotlightSheet provides true,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(top = 12.dp, bottom = 8.dp),
                            ) {
                                state.content(this)
                            }
                        }
                    }
                }
            }
        }
    }
}
