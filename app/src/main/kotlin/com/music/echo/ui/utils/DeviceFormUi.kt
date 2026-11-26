package iad1tya.echo.music.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import iad1tya.echo.music.constants.DeviceFormChoiceKey
import iad1tya.echo.music.ui.newui.auraWindowInfo
import iad1tya.echo.music.utils.DeviceForm
import iad1tya.echo.music.utils.DeviceFormChoice
import iad1tya.echo.music.utils.DeviceFormFactor
import iad1tya.echo.music.utils.DeviceFormResolution
import iad1tya.echo.music.utils.rememberEnumPreference

/** The user's «Tipo de aparato» and the form it resolves to for the current window (registry row 895). */
@Immutable
data class DeviceFormState(
    val choice: DeviceFormChoice,
    val detected: DeviceFormFactor,
    val form: DeviceFormFactor,
)

/**
 * THE one place the device form reaches the UI. `null` = nobody provided it (another activity, a preview): every reader
 * then answers exactly as before the setting existed.
 */
val LocalDeviceForm = compositionLocalOf<DeviceFormState?> { null }

/**
 * Resolves the form ONCE at the root (MainActivity's setContent, inside `ProvideAuraWindowInfo`, whose window it reads)
 * and gives it to everything below. Event-driven: it changes only when the choice, the fold or the window size changes.
 */
@Composable
fun ProvideDeviceForm(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTelevision = remember(context) { DeviceForm.isTelevision(context) }
    val isCar = remember(context) { DeviceForm.isCar(context) }
    val choice by rememberEnumPreference(DeviceFormChoiceKey, DeviceFormChoice.AUTO)
    val window = auraWindowInfo()
    val hasHinge = window.hinges.isNotEmpty()
    val wideWindow = isWideLayoutWindow(window.widthDp, window.heightDp)
    val state = remember(choice, isTelevision, isCar, hasHinge, wideWindow) {
        val detected = DeviceFormResolution.detect(isTelevision, isCar, hasHinge, wideWindow)
        DeviceFormState(choice, detected, DeviceFormResolution.effective(choice, detected))
    }
    CompositionLocalProvider(LocalDeviceForm provides state, content = content)
}

/**
 * The TV shell: the side rail (UI_INVENTORY §1.7) instead of the bottom bar / capsule, and the full-screen player with
 * «Pantalla». A real Android TV in «Automático», or any device set to «TV».
 */
@Composable
fun rememberIsTvShell(): Boolean =
    LocalDeviceForm.current?.let { DeviceFormResolution.usesTvShell(it.form) } ?: false
