package iad1tya.echo.music.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import iad1tya.echo.music.ui.newui.AuraFrostWindowIfPremium
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.auraFloatingContainerColor
import iad1tya.echo.music.ui.newui.auraFloatingContentColor
import iad1tya.echo.music.ui.newui.auraFloatingScrimColor
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin

// ──────────────────────────────────────────────────────────────────────────────────────────────────
// ONE FLOATING STYLE EVERYWHERE (owner 2026-09-13: "abro algunas ventanas y son sólidas feas"). Screens
// that still called Material's AlertDialog / ModalBottomSheet directly popped the stock grey Material
// window on top of the new UI. These drop-ins keep Material's parameter list, so a call site only
// changes its name, and with the new UI on they render the same Aura floating plate as DefaultDialog
// and BottomSheetMenu. With the new UI off they are the exact Material components.
// ──────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun AuraAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val skin = rememberAuraPanelSkin()
    if (!(skin.enabled && skin.darkGround)) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
        return
    }
    DefaultDialog(
        onDismiss = onDismissRequest,
        modifier = modifier,
        icon = icon,
        title = title,
        dismissOnOutsideTap = properties.dismissOnClickOutside,
        // Several of these open from inside menus/sheets: a window used to be the only way to layer above
        // them. The activity-level glass host does that now (and gives the mini player's glass);
        // DefaultDialog still falls back to a window when there is no host or it sits inside one.
        forceWindow = true,
        buttons = {
            dismissButton?.invoke()
            confirmButton()
        },
    ) {
        if (text != null) {
            CompositionLocalProvider(LocalContentColor provides AuraPalette.OnGroundMuted) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape,
        containerColor = if (premium) auraFloatingContainerColor() else containerColor,
        contentColor = if (premium) auraFloatingContentColor() else contentColor,
        tonalElevation = if (premium) 0.dp else tonalElevation,
        scrimColor = if (premium) auraFloatingScrimColor() else scrimColor,
        dragHandle = if (premium && dragHandle != null) {
            { BottomSheetDefaults.DragHandle(color = AuraPalette.OnGroundFaint) }
        } else {
            dragHandle
        },
        contentWindowInsets = contentWindowInsets,
        properties = properties,
    ) {
        AuraFrostWindowIfPremium()
        // A sheet WINDOW: dialogs opened from here must stay windows, the glass host draws under it.
        CompositionLocalProvider(iad1tya.echo.music.ui.newui.LocalInsideDialogWindow provides true) {
            content()
        }
    }
}
