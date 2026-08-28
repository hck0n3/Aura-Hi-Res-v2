package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.C
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Low-rate waveform energy for the full-player ground. The Compose side only samples once per
 * frame via [withFrameNanos] — never a busy loop and never while Performance Mode is on (callers
 * pass [captureEnabled] = false).
 *
 * Does not touch the audio path / Superpowered / EQ. The capture is observation-only.
 *
 * HALLAZGO-027 (RONDA 3, second pass): [captureEnabled] gates the capture itself and is meant to
 * be STABLE across bottom-sheet flips (high-perf / casting). The sheet's expanded state is passed
 * separately as [active] and only gates the per-frame smoothing loop. The first version folded
 * `isExpanded` into the capture gate, so EVERY flip built and tore down a [Visualizer] on the
 * main thread — a binder call into AudioFlinger that inserts and removes an effect in the live
 * audio chain mid-playback, the prime suspect for the micro-cuts and gesture jank the owner
 * reported on BETA-005. Capture now lives as long as the player sheet exists.
 *
 * HALLAZGO-048 (RONDA 3): this composable no longer owns a [android.media.audiofx.Visualizer].
 * [AuraVisualizerHub] is the single process-wide owner; rhythm registers as a [AuraVisualizerHub
 * .Consumer] and reads [AuraVisualizerHub.rhythmLevel]. Entering/leaving the EQ screen flips a
 * boolean in the hub instead of attaching/releasing a second Visualizer on the live audio chain —
 * the audible cuts the owner reported when opening/closing the equalizer.
 */
@Composable
fun rememberAuraRhythmLevel(
    audioSessionId: Int,
    captureEnabled: Boolean,
    active: Boolean,
    playing: Boolean,
): State<Float> {
    val level = remember { mutableFloatStateOf(0f) }
    val hubLevel by AuraVisualizerHub.rhythmLevel.collectAsState()

    DisposableEffect(captureEnabled, audioSessionId) {
        if (captureEnabled && audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0) {
            AuraVisualizerHub.setConsumer(
                AuraVisualizerHub.Consumer.RHYTHM,
                active = true,
                sessionId = audioSessionId,
            )
        }
        onDispose {
            AuraVisualizerHub.setConsumer(
                AuraVisualizerHub.Consumer.RHYTHM,
                active = false,
                sessionId = audioSessionId,
            )
        }
    }

    // [active] (sheet expanded) gates the loop, NOT the capture: while collapsed no state is
    // written per frame, so nothing recomposes and no frame callbacks tick behind the mini-player.
    LaunchedEffect(captureEnabled, active, playing) {
        if (!captureEnabled || !active) {
            level.floatValue = 0f
            return@LaunchedEffect
        }
        var smoothed = 0f
        while (isActive) {
            withFrameNanos {
                val target = if (playing) hubLevel else 0f
                // Fast attack, slower release — reads as rhythm without jitter.
                smoothed += (target - smoothed) * if (target > smoothed) 0.35f else 0.12f
                level.floatValue = smoothed.coerceIn(0f, 1f)
            }
        }
    }

    return level
}

/**
 * Buckets for [quantizeAuraRhythmIntensity].
 *
 * HALLAZGO-062 (2026-08-27): raised 8 → 32 after the owner's comparison against the old code
 * (vc951, "todas las animaciones eran fluidas"). With 8 buckets the ground pulsed in visible
 * ~3.5%-of-intensity steps; the old app applied the raw level every frame and read as continuous.
 * 32 buckets keep adjacent steps under 1% of intensity — visually continuous — while composition
 * still only runs on bucket crossings and stays scoped to the ground layer (the derivedStateOf
 * reader in AuraPlayer), never the whole player tree like the old code did.
 */
const val AURA_RHYTHM_STEPS = 32

/** Max rhythm boost over the base ground intensity (owner: animated ground by song rhythm). */
const val AURA_RHYTHM_GAIN = 0.28f

/**
 * Quantized rhythm→intensity mapping for the player ground (HALLAZGO-027, second pass).
 *
 * The raw rhythm level changes every frame while music plays. Reading it in the composition
 * phase recomposed the entire player tree once per frame — jank while expanded plus a permanent
 * battery/heat tax (AGENTS rule: nothing sampling per frame while music plays). Quantizing to
 * [steps] buckets lets composition readers (via `derivedStateOf`) recompose only when the level
 * crosses a bucket boundary; with the default 32 buckets adjacent buckets differ by under 1% of
 * intensity, invisible on bloom/wash/lobes.
 *
 * Pure and deterministic — unit-testable without Compose.
 */
fun quantizeAuraRhythmIntensity(level: Float, steps: Int = AURA_RHYTHM_STEPS): Float {
    require(steps > 0) { "steps must be positive" }
    val quantized = (level.coerceIn(0f, 1f) * steps).roundToInt() / steps.toFloat()
    return 1f + AURA_RHYTHM_GAIN * quantized
}
