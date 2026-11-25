package iad1tya.echo.music.ui.newui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import iad1tya.echo.music.ui.utils.LocalContentPaneWidth

/**
 * The window measured once at the root (see AuraWindowInfo.kt). `null` = nobody provided it (a preview, another
 * activity): [auraWindowInfo] then measures the window where it is asked, exactly as the call sites did before.
 */
val LocalAuraWindowInfo = compositionLocalOf<AuraWindowInfo?> { null }

/**
 * Measures the window ONCE and gives it to everything below. Goes at the root of setContent.
 *
 * Event-driven, nothing per frame: the size comes from the window's own state (it changes on a fold, a rotation, a
 * resize of a multi-window pane) and the hinges from the window manager's layout flow, which the Compose adaptive library
 * already collects for `currentWindowAdaptiveInfoV2()` (it emits only when the fold or posture changes). Only the
 * composables that read [LocalAuraWindowInfo] recompose when it changes.
 *
 * Why the root value is the same value the call sites used to read themselves: `LocalWindowInfo.containerSize` is the
 * activity's window metrics wherever it is read (dialogs included: Compose unwraps the context to the activity), and
 * `LocalConfiguration` / `LocalDensity` are never overridden below the root in this app.
 */
@Composable
fun ProvideAuraWindowInfo(content: @Composable () -> Unit) {
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    val hinges = remember(posture) {
        posture.hingeList.map {
            AuraHinge(
                left = it.bounds.left,
                top = it.bounds.top,
                right = it.bounds.right,
                bottom = it.bounds.bottom,
                isFlat = it.isFlat,
                isVertical = it.isVertical,
                isSeparating = it.isSeparating,
                isOccluding = it.isOccluding,
            )
        }
    }
    val info = measureAuraWindow(hinges)
    CompositionLocalProvider(LocalAuraWindowInfo provides info, content = content)
}

/** The current window: the root's measurement, or (unprovided) a measurement right here with no hinge information. */
@Composable
fun auraWindowInfo(): AuraWindowInfo = LocalAuraWindowInfo.current ?: measureAuraWindow(emptyList())

/**
 * Width (dp) of the pane the caller is laid out in: the content pane when the split player takes the rest of the
 * window ([LocalContentPaneWidth]), otherwise the whole window ([AuraWindowInfo.widthDp]).
 */
@Composable
fun auraPaneWidthDp(): Float = LocalContentPaneWidth.current?.value ?: auraWindowInfo().widthDp

@Composable
private fun measureAuraWindow(hinges: List<AuraHinge>): AuraWindowInfo {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    return AuraWindowInfo(
        widthDp = size.width / density,
        heightDp = size.height / density,
        configWidthDp = configuration.screenWidthDp,
        configHeightDp = configuration.screenHeightDp,
        posture = auraPostureOf(hinges),
        hinges = hinges,
    )
}
