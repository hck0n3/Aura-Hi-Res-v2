

package iad1tya.echo.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.newui.AuraDialogWindowEffects
import iad1tya.echo.music.ui.newui.AuraFloatingSurface
import iad1tya.echo.music.ui.newui.AuraInWindowDialog
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.LocalOverlayHazeState
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable

import kotlinx.coroutines.delay

// ──────────────────────────────────────────────────────────────────────────────────────────────────
// "Interfaz nueva" — [DefaultDialog] / [ListDialog] (and their callers [ActionPromptDialog] /
// [TextFieldDialog]) are the ONE dialog chrome ~44 call sites share: song/album/playlist menus, every
// settings confirmation, "Crear playlist" and "Agregar a playlist" among them. Retinting it here — the
// same seam philosophy as `Material3SettingsGroup` for settings rows and `Items.kt` for song rows —
// carries every one of those dialogs into the redesign at once, including ones inside ALREADY-ported
// screens (AuraLibraryScreen, AuraPlayerMenu, AuraMigrationScreen…) that were still popping a plain
// Material3 sheet on top of the new dark shell.
//
// Premium path: frosted translucent plate ([AuraFloatingSurface]) so the dimmed UI behind shows
// through — same language as sheets/menus. Account flyout stays opaque ([FloatingFill]) in
// SettingDialoge on purpose.
//
// With the flag OFF every branch below reduces to the exact original values — this is a strict
// superset of the previous behaviour, never a redesign of it.
// ──────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    // OWNER REPORT (2026-09-04): the AI playlist generating dialog must not die on an outside
    // tap — passed through to the in-window host (the classic window branch already guards in
    // its own close handlers).
    dismissOnOutsideTap: Boolean = true,
    // Dialogs opened FROM another dialog window must stay windows (see the routing note below).
    forceWindow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    val iconTint = if (premium) AuraPalette.Teal
    else AlertDialogDefaults.iconContentColor
    val titleTint = if (premium) AuraPalette.OnGround
    else AlertDialogDefaults.titleContentColor
    val buttonTint = if (premium) AuraPalette.Teal
    else MaterialTheme.colorScheme.primary

    // THE UNIFIED FLOATING STYLE (owner directive 2026-09-01: "ese mismo estilo que sale cuando
    // toco el de búsqueda del reproductor... para que toda la apariencia tenga el mismo estilo"):
    // with the premium skin + a live overlay haze source, dialogs render as IN-WINDOW floating
    // cards — the quick-search/3-dot-menu construction (glass sampling the PLAYER when opened
    // from the player via LocalOverlayHazeState, dispatcher-priority back, centered card, IME
    // padding) — instead of a separate window whose blur One UI 8.5 kills. Every DefaultDialog
    // consumer (AI playlist, create/import playlist, settings menu...) inherits in one place.
    // No source / no premium → the exact Dialog+AuraFloatingSurface window that shipped before,
    // byte-identical.
    // forceWindow (owner report 2026-09-05: "abre pero la abre ATRÁS del reproductor"): a dialog
    // OPENED FROM INSIDE another dialog window (AddToPlaylistDialog's "Crear lista" is the known
    // case) must NOT become an in-window overlay — the overlay composes inside the NavHost,
    // UNDER the expanded player sheet, and the owner sees "nothing happens" until he backs out.
    // Children of dialog WINDOWS stay dialog WINDOWS: they layer above everything, like the
    // parent that opened them.
    val overlayHazeState = LocalOverlayHazeState.current
    // GLASS PORTAL (2026-09-14): with the activity-level host the overlay composes ABOVE the player
    // sheet and every menu, so the reason for forceWindow (an in-place overlay buried under them) is
    // gone — unless this dialog is opened from inside a real dialog window, which the host can't top.
    if (premium && overlayHazeState != null && (!forceWindow || iad1tya.echo.music.ui.newui.auraOverlayHostAvailable())) {
        AuraInWindowDialog(
            visible = true,
            onDismiss = onDismiss,
            dismissOnOutsideTap = dismissOnOutsideTap,
        ) {
            DialogBody(
                premium = premium,
                icon = icon,
                title = title,
                buttons = buttons,
                horizontalAlignment = horizontalAlignment,
                buttonTint = buttonTint,
                iconTint = iconTint,
                titleTint = titleTint,
                modifier = modifier,
                content = content,
            )
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(iad1tya.echo.music.ui.newui.LocalInsideDialogWindow provides true) {
            AuraDialogWindowEffects(enabled = premium)
            AuraFloatingSurface(
                modifier = Modifier.padding(24.dp),
                shape = if (premium) AuraShapes.Card else AlertDialogDefaults.shape,
            ) {
                DialogBody(
                    premium = premium,
                    icon = icon,
                    title = title,
                    buttons = buttons,
                    horizontalAlignment = horizontalAlignment,
                    buttonTint = buttonTint,
                    iconTint = iconTint,
                    titleTint = titleTint,
                    modifier = modifier,
                    content = content,
                )
            }
        }
    }
}

/** The dialog's visual body, shared by the in-window overlay path and the classic window path. */
@Composable
private fun DialogBody(
    premium: Boolean,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    buttonTint: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color,
    titleTint: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit,
) {
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = modifier.padding(24.dp)
            ) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides iconTint) {
                        Box(Modifier.align(Alignment.CenterHorizontally)) { icon() }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides titleTint) {
                        ProvideTextStyle(
                            if (premium) AuraType.SheetTitle else MaterialTheme.typography.headlineSmall,
                        ) {
                            Box(
                                Modifier.align(
                                    if (icon == null) Alignment.Start else Alignment.CenterHorizontally,
                                ),
                            ) { title() }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                CompositionLocalProvider(
                    LocalContentColor provides if (premium) AuraPalette.OnGround else LocalContentColor.current,
                ) {
                    content()
                }

                if (buttons != null) {
                    Spacer(Modifier.height(24.dp))
                    FlowRow(modifier = Modifier.align(Alignment.End)) {
                        CompositionLocalProvider(LocalContentColor provides buttonTint) {
                            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                                buttons()
                            }
                        }
                    }
                }
            }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = if (titleBar != null) {
            { Row { titleBar() } }
        } else if (title != null) {
            {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        } else null,
        buttons = {
            if (onReset != null) {
                Row(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { onReset() },
                        modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            if (onCancel != null) {
                TextButton(
                    onClick = { onCancel() },
                    modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }

            TextButton(
                onClick = { onConfirm() },
                modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    ) {
        content()
    }
}

@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val skin = rememberAuraPanelSkin()
    val premium = skin.enabled && skin.darkGround
    val baseScheme = MaterialTheme.colorScheme
    // Material3 ListItem defaults to opaque surface containers — that paints a flat card over the
    // frost plate and kills the premium look (MP3 song-picker and every other ListDialog).
    val listScheme = if (premium) {
        baseScheme.copy(
            surface = Color.Transparent,
            surfaceContainer = Color.Transparent,
            surfaceContainerHigh = Color.Transparent,
            surfaceContainerHighest = Color.Transparent,
            surfaceContainerLow = Color.Transparent,
            surfaceContainerLowest = Color.Transparent,
            surfaceVariant = Color.Transparent,
            background = Color.Transparent,
        )
    } else {
        baseScheme
    }

    // GLASS PORTAL (2026-09-14): "Añadir a lista" and every other list dialog get the mini player's
    // glass through the activity-level host; its children (Crear lista) port above it.
    if (premium && LocalOverlayHazeState.current != null && iad1tya.echo.music.ui.newui.auraOverlayHostAvailable()) {
        AuraInWindowDialog(visible = true, onDismiss = onDismiss) {
            MaterialTheme(colorScheme = listScheme) {
                CompositionLocalProvider(LocalContentColor provides AuraPalette.OnGround) {
                    LazyColumn(
                        modifier = modifier
                            .padding(vertical = 24.dp)
                            .heightIn(max = 560.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = content,
                    )
                }
            }
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(iad1tya.echo.music.ui.newui.LocalInsideDialogWindow provides true) {
            AuraDialogWindowEffects(enabled = premium)
            AuraFloatingSurface(
                modifier = Modifier.padding(24.dp),
                shape = if (premium) AuraShapes.Card else AlertDialogDefaults.shape,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = modifier
                        .padding(vertical = 24.dp)
                        .imePadding(),
                ) {
                    MaterialTheme(colorScheme = listScheme) {
                        CompositionLocalProvider(
                            LocalContentColor provides if (premium) AuraPalette.OnGround else LocalContentColor.current,
                        ) {
                            LazyColumn(content = content)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoLabel(
    text: String
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 8.dp)
) {
    Icon(
        painter = painterResource(id = R.drawable.info),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(4.dp)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(),
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (String) -> Unit = {},

    
    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,

    onDismiss: () -> Unit,
    autoDismiss: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
    // Children of dialog WINDOWS stay windows (the "Crear lista" behind the player report).
    forceWindow: Boolean = false,
) {
    val legacyFieldState = remember { mutableStateOf(initialTextFieldValue) }
    // New UI: the same rounded outlined field as every other floating window (AI playlist, add to
    // playlist, save preset) instead of the underline TextField.
    val fieldSkin = rememberAuraPanelSkin()
    val fieldColors = if (fieldSkin.enabled && fieldSkin.darkGround) {
        iad1tya.echo.music.ui.newui.auraFloatingTextFieldColors()
    } else {
        OutlinedTextFieldDefaults.colors()
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        forceWindow = forceWindow,
        icon = icon,
        title = title,
        buttons = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }

            val isValid = textFields?.all { isInputValid(it.second.text) }
                ?: isInputValid(legacyFieldState.value.text)

            TextButton(
                enabled = isValid,
                modifier = Modifier.tvFocusable(rememberIsTvOrCar(), scaleFocused = 1f),
                onClick = {
                    if (autoDismiss) onDismiss()
                    if (textFields != null && onDoneMultiple != null) {
                        onDoneMultiple(textFields.map { it.second.text })
                    } else {
                        onDone(legacyFieldState.value.text)
                    }
                }
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        }
    ) {
        Column(
            modifier = Modifier.weight(weight = 1f, fill = false)
        ) {
            if (textFields != null) {
                textFields.forEachIndexed { index, (label, value) ->
                    androidx.compose.material3.OutlinedTextField(
                        value = value,
                        onValueChange = { onTextFieldsChange?.invoke(index, it) },
                        placeholder = { Text(label) },
                        singleLine = singleLine,
                        maxLines = maxLines,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                            keyboardType = keyboardType
                        ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (onDoneMultiple != null) {
                                onDoneMultiple(textFields.map { it.second.text })
                                if (autoDismiss) onDismiss()
                            }
                        }
                    ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index < textFields.size - 1) 12.dp else 0.dp)
                            .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                    )
                }
            } else {
                androidx.compose.material3.OutlinedTextField(
                    value = legacyFieldState.value,
                    onValueChange = { legacyFieldState.value = it },
                    placeholder = placeholder,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                        keyboardType = keyboardType
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onDone(legacyFieldState.value.text)
                            if (autoDismiss) onDismiss()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            extraContent?.invoke()
        }
    }
}
