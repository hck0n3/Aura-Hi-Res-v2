package iad1tya.echo.music.utils

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Row 771: the ONE place that decides whether decorative, continuous motion may run. Two system signals stop it:
 *  · «Quitar animaciones» (Accessibility / Developer options): `Settings.Global.ANIMATOR_DURATION_SCALE == 0`.
 *  · The phone's battery saver (`PowerManager.isPowerSaveMode`).
 * Used by the ambient background clock, the player's rhythm pulse (and its Visualizer capture) and the lyrics glow.
 * Event-driven only (AGENTS rule 7): a ContentObserver and the power-save broadcast, never a poll.
 */
object ReducedMotion {
    /** Pure, for tests: any system reason to hold decorative motion still. */
    fun reduce(systemAnimationsOff: Boolean, powerSave: Boolean): Boolean = systemAnimationsOff || powerSave

    /** Pure, for tests: an animator duration scale of 0 is the system's «Quitar animaciones». */
    fun animatorScaleOff(scale: Float): Boolean = scale == 0f

    /** Pure, for tests: a music-driven decorative loop runs only while shown, playing and motion is allowed. */
    fun musicLoopRuns(shown: Boolean, playing: Boolean, reduced: Boolean): Boolean = shown && playing && !reduced

    fun systemAnimationsOff(context: Context): Boolean = runCatching {
        animatorScaleOff(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }.getOrDefault(false)

    fun powerSave(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }.getOrDefault(false)
}

/**
 * True while the system asks for reduced motion («Quitar animaciones») or the battery saver is on. Recomposes only
 * when one of the two changes.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val powerSave = rememberPowerSaveMode()
    val animationsOff = remember(context) { mutableStateOf(ReducedMotion.systemAnimationsOff(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animationsOff.value = ReducedMotion.systemAnimationsOff(context)
            }
        }
        runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        animationsOff.value = ReducedMotion.systemAnimationsOff(context)
        onDispose { runCatching { resolver.unregisterContentObserver(observer) } }
    }
    return ReducedMotion.reduce(animationsOff.value, powerSave)
}
