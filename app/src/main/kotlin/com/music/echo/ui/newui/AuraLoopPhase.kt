package iad1tya.echo.music.ui.newui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.utils.ReducedMotion
import iad1tya.echo.music.utils.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Registry row 899 (plan phase 7, AGENTS rule 7): a decorative 0..1 loop that STOPS when it has nothing to follow.
 *
 * `rememberInfiniteTransition` cannot be paused: it asked for a frame on every vsync for as long as it was composed,
 * also with the music paused. This one runs only while [running] is true; set to false it stops where it is (no jump)
 * and starts again from there. Like every frame-driven animation it also stops with the app off screen (Compose's frame
 * clock pauses on ON_STOP) and when its composable leaves the screen.
 */
@Composable
internal fun rememberAuraLoopPhase(running: Boolean, periodMs: Int): State<Float> {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(running, periodMs) {
        if (!running) return@LaunchedEffect
        while (isActive) {
            phase.animateTo(1f, tween(auraLoopRemainingMs(phase.value, periodMs), easing = LinearEasing))
            phase.snapTo(0f)
        }
    }
    return phase.asState()
}

/** Pure, for tests: what is left of one [periodMs] cycle from [phase], at least 1 ms so the loop always advances. */
internal fun auraLoopRemainingMs(phase: Float, periodMs: Int): Int =
    ((1f - phase.coerceIn(0f, 1f)) * periodMs).roundToInt().coerceAtLeast(1)

/**
 * True while a music-driven decoration may move: music is audible (cast-aware) and the system does not ask for reduced
 * motion («Quitar animaciones» or battery saver, row 771). No player connection = nothing playing.
 */
@Composable
internal fun rememberMusicLoopRuns(): Boolean {
    val connection = LocalPlayerConnection.current
    val playing by (connection?.isEffectivelyPlaying?.collectAsState() ?: remember { mutableStateOf(false) })
    return ReducedMotion.musicLoopRuns(shown = true, playing = playing, reduced = rememberReducedMotion())
}
