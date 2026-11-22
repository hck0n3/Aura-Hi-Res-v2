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
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Low-rate waveform energy for the full-player ground. The Compose side samples via [withFrameNanos]
 * at most [AURA_RHYTHM_MAX_FPS] times a second — never a busy loop and never while Performance Mode
 * is on (callers pass [captureEnabled] = false).
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
    val view = LocalView.current
    LaunchedEffect(captureEnabled, active, playing) {
        if (!captureEnabled || !active) {
            level.floatValue = 0f
            return@LaunchedEffect
        }
        var smoothed = 0f
        var settled = false
        // Battery/heat (audit 2026-10-20): this loop asked for a frame every display refresh for as long as the player
        // was open — also while PAUSED, when the target is 0 and nothing changes (120 main-thread wakeups a second to
        // write the same 0f). Once paused and decayed to nothing it now ends; the effect is keyed on [playing], so it
        // starts again the moment playback does.
        // Battery/heat (owner 2026-11-22): while PLAYING it still woke every vsync (120 Hz) for a hub level that moves
        // ~20 times a second. It now runs at most [AURA_RHYTHM_MAX_FPS], applies the same per-panel-frame smoothing for
        // every panel frame it skipped, and writes [level] only when the quantized step the ground reads changes.
        val panelHz = view.display?.refreshRate?.takeIf { it > 0f } ?: 60f
        // Half a panel frame short of the cap, so the frame request lands between two vsyncs and the next one is taken.
        val pauseMs = (1000f / AURA_RHYTHM_MAX_FPS - 500f / panelHz).coerceAtLeast(0f).toLong()
        var lastFrameNanos = 0L
        while (isActive && !settled) {
            withFrameNanos { frameNanos ->
                val frames = if (lastFrameNanos == 0L) 1f else auraRhythmElapsedFrames(frameNanos - lastFrameNanos, panelHz)
                lastFrameNanos = frameNanos
                val target = if (playing) hubLevel else 0f
                // Fast attack, slower release — reads as rhythm without jitter.
                val rate = if (target > smoothed) 0.35f else 0.12f
                smoothed += (target - smoothed) * (1f - (1f - rate).pow(frames))
                val next = smoothed.coerceIn(0f, 1f)
                if (quantizeAuraRhythmIntensity(next) != quantizeAuraRhythmIntensity(level.floatValue)) level.floatValue = next
                settled = auraRhythmSettled(playing, smoothed)
            }
            if (!settled && pauseMs > 0) delay(pauseMs)
        }
        if (settled) level.floatValue = 0f
    }

    return level
}

/**
 * Below this the smoothed level is invisible: [quantizeAuraRhythmIntensity] rounds to the bucket-0 step until the level
 * reaches half a bucket (1/64 ≈ 0.0156), and this is a third of that. Pure and JVM-testable (battery audit 2026-10-20).
 */
const val AURA_RHYTHM_SETTLED_EPSILON = 0.005f

/**
 * Update cap of the rhythm smoothing loop: above the ~20 hub updates a second, half a 120 Hz panel. 30 read as a stepped
 * pulse on 90/120 Hz panels (HALLAZGO-062).
 */
const val AURA_RHYTHM_MAX_FPS = 60

/** Longest gap the smoothing catches up on in one frame, so the first frame after a resume from background eases in. */
const val AURA_RHYTHM_MAX_GAP_NANOS = 100_000_000L

/** Panel frames elapsed in [gapNanos], at least 1 and clamped to [AURA_RHYTHM_MAX_GAP_NANOS]. Pure and JVM-testable. */
fun auraRhythmElapsedFrames(gapNanos: Long, panelHz: Float): Float =
    (gapNanos.coerceAtMost(AURA_RHYTHM_MAX_GAP_NANOS) * panelHz / 1_000_000_000f).coerceAtLeast(1f)

/** Paused AND decayed to nothing: the per-frame smoothing loop has nothing left to animate and must stop asking for frames. */
fun auraRhythmSettled(playing: Boolean, smoothed: Float): Boolean = !playing && smoothed < AURA_RHYTHM_SETTLED_EPSILON

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
