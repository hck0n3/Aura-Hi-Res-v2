package iad1tya.echo.music.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide "is the UI on screen" flag, for work that has no Compose lifecycle of its own
 * (battery/heat audit 2026-10-20).
 *
 * Compose already pauses its FRAME clock when the activity stops (screen off, another app on top), but
 * a ViewModel loop or a `LaunchedEffect { while (true) { delay(..) } }` is not driven by frames and keeps
 * running for as long as the composition exists — which is the whole life of the process while music plays.
 * Anything that only exists to feed a visible screen should park on this instead of ticking with the screen
 * off. Fed by `MainActivity.onStart` / `onStop`, next to `AuraVisualizerHub.setAppVisible`.
 *
 * Starts `false`: until the activity reports STARTED there is no UI to feed. The service, the workers and the
 * playback path never read this — audio keeps going exactly as before.
 */
object AppForeground {
    private val _visible = MutableStateFlow(false)

    /** True between `MainActivity.onStart` and `onStop`. */
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun set(visible: Boolean) {
        _visible.value = visible
    }
}
