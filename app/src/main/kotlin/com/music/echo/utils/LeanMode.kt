package iad1tya.echo.music.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import iad1tya.echo.music.constants.HighPerformanceModeKey

/**
 * "Lean" = the app's Performance / Ultra-performance mode ([HighPerformanceModeKey]).
 *
 * Owner 2026-10-15: «verifica cómo se comportará en dispositivos de gama muy baja… optimiza la app al máximo para ese
 * tipo de dispositivos que la app detecte que son de gama baja… sin importar que se tenga que arriesgar la apariencia
 * en ese modo de rendimiento o de ultra rendimiento… sin afectar nada de lo que ya tenemos para gama alta».
 *
 * The detection already exists: the mode is switched ON by itself on a LOW-tier device (≤ 3 GB of reported RAM, the
 * low-RAM flag, TV / car) the first time the app runs, and the user can turn it on or off in Ajustes. Every new
 * low-end saving hangs on THIS one switch — never on the device tier directly — so that:
 *  · a high-end device (mode off) runs byte-for-byte what it ran before;
 *  · the user's choice is respected in both directions (a weak device whose owner turned the mode off keeps the full
 *    look; a strong device whose owner turned it on gets the lean path);
 *  · there is exactly one place to read when a lean branch is wrong.
 *
 * Two readers, same value:
 *  · [isOn] — non-blocking, usable ANYWHERE (an image URL helper, a navigation transition, a service). It reads the
 *    process-wide DataStore snapshot ([PrefsBridge]); before the very first emission (cold start) it answers false,
 *    i.e. the normal path, which is the safe side.
 *  · [rememberLeanMode] — for composables: reactive, so flipping the setting re-draws the screen live.
 */
object LeanMode {
    fun isOn(): Boolean = PrefsBridge.peek(HighPerformanceModeKey) == true
}

/** Reactive "lean mode" for composables. See [LeanMode]. */
@Composable
fun rememberLeanMode(): Boolean {
    val on by rememberPreference(HighPerformanceModeKey, false)
    return on
}
