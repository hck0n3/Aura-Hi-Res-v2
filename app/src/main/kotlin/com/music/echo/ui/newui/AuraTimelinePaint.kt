package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import iad1tya.echo.music.ui.component.PlayerSliderActivePaint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * The player's timeline in the cover's colours.
 *
 * 🔴 Dueño (2026-10-09): *"la barra de tiempo del reproductor también quiero que tenga las combinaciones de
 * colores de la portada de una manera premium y animada"*.
 *
 * The PLAYED part is a gradient through every light-carrying colour of the cover
 * ([AuraPalette.ProgressSpectrum]: up to five hues plus its white, each ≥ 3:1 on the ground), repeating
 * seamlessly and flowing slowly towards the play head; a soft glow in the colour under the play head
 * breathes with it. The inactive rail, the buffered segment, the thumb and the slider's semantics/touch
 * target are untouched — this only replaces the one `drawLine` of the played part.
 *
 * ## Heat / battery (AGENTS rule 7)
 *  · No clock of its own: it reads [AuraAmbientMotion.phase], the app's single 15 fps ambient clock, which
 *    already stops in background, Performance Mode, a hot device, a low-tier device, battery saver and with
 *    system animations off. Stopped clock = no invalidation = a still (but still coloured) timeline.
 *  · Only while PLAYING: a paused timeline draws at the phase it stopped on and reads no state at all.
 *  · Draw phase only, in the track's own layer (`PlayerSlider.activePaintLayer`): a step re-records the
 *    track canvas — never a recomposition, never the player's layer.
 *  · Per step: one gradient + one radial-gradient object. No bitmap, no readback.
 */
@Stable
internal class AuraTimelinePaint(private val animated: Boolean) : PlayerSliderActivePaint {

    // Where a paused timeline stays. Sampled without read observation: constructing this object happens in
    // composition, which must not subscribe to a 15 fps clock.
    private var frozenPhase: Float = Snapshot.withoutReadObservation { AuraAmbientMotion.phase }

    // The looped stops, rebuilt only when the palette hands over a different list (a track change).
    private var loopSource: List<Color>? = null
    private var loop: List<Color> = emptyList()

    override fun drawPlayed(scope: DrawScope, start: Offset, end: Offset, strokeWidth: Float, trackWidth: Float) {
        val spectrum = AuraPalette.ProgressSpectrum
        if (spectrum !== loopSource) {
            loopSource = spectrum
            loop = timelineLoopStops(spectrum)
        }
        val stops = loop
        val phase = if (animated) AuraAmbientMotion.phase.also { frozenPhase = it } else frozenPhase
        val period = timelineGradientPeriod(trackWidth)
        val shift = timelineFlowShift(phase, period)
        with(scope) {
            if (abs(end.x - start.x) >= 0.5f) {
                drawLine(
                    brush = Brush.linearGradient(
                        colors = stops,
                        start = Offset(start.x + shift, start.y),
                        end = Offset(start.x + shift + period, start.y),
                        tileMode = TileMode.Repeated,
                    ),
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
            // The play head's glow: the colour the gradient has right there, breathing slowly.
            val glow = timelineColorAt(stops, (end.x - start.x - shift) / period)
            val pulse = if (animated) 0.5f + 0.5f * sin(2f * PI.toFloat() * phase * GLOW_PULSES_PER_CYCLE) else 0.5f
            val radius = strokeWidth * GLOW_RADIUS_FACTOR
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = GLOW_ALPHA_BASE + GLOW_ALPHA_PULSE * pulse), Color.Transparent),
                    center = end,
                    radius = radius,
                ),
                radius = radius,
                center = end,
            )
        }
    }
}

/** How many gradient periods flow past per ambient cycle (42 s): one every 14 s — a drift, not a scroll. */
internal const val TIMELINE_FLOW_CYCLES = 3

/** The gradient's period as a fraction of the rail: the whole spectrum shows across ¾ of the bar. */
internal const val TIMELINE_PERIOD_FRACTION = 0.75f

private const val GLOW_PULSES_PER_CYCLE = 12f
private const val GLOW_RADIUS_FACTOR = 1.9f
private const val GLOW_ALPHA_BASE = 0.26f
private const val GLOW_ALPHA_PULSE = 0.12f

/** The stops of a seamlessly repeating gradient: the spectrum closed back on its first colour. Pure. */
internal fun timelineLoopStops(spectrum: List<Color>): List<Color> = when (spectrum.size) {
    0 -> listOf(AuraPalette.Teal, AuraPalette.Blue, AuraPalette.Teal)
    1 -> listOf(spectrum[0], spectrum[0], spectrum[0])
    else -> spectrum + spectrum[0]
}

/** Length of one gradient period, px. Pure. */
internal fun timelineGradientPeriod(trackWidth: Float): Float =
    (trackWidth * TIMELINE_PERIOD_FRACTION).coerceAtLeast(1f)

/** Shift of the gradient at [phase] (0..1), px, in 0 until [period]; integer cycles → seamless loop. Pure. */
internal fun timelineFlowShift(phase: Float, period: Float): Float {
    val turns = phase * TIMELINE_FLOW_CYCLES
    return (turns - floor(turns)) * period
}

/** The colour of the looped gradient [stops] at [u] periods (any real; wraps). Pure. */
internal fun timelineColorAt(stops: List<Color>, u: Float): Color {
    if (stops.isEmpty()) return Color.Transparent
    if (stops.size == 1) return stops[0]
    val wrapped = u - floor(u)
    val x = wrapped * (stops.size - 1)
    val i = x.toInt().coerceIn(0, stops.size - 2)
    val f = (x - i).coerceIn(0f, 1f)
    // On a stop, the stop itself (an Oklab round trip could move it by a bit).
    return when {
        f < 1e-4f -> stops[i]
        f > 1f - 1e-4f -> stops[i + 1]
        else -> lerp(stops[i], stops[i + 1], f)
    }
}
