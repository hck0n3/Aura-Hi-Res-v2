package iad1tya.echo.music.playback

import kotlin.math.PI
import kotlin.math.cos

/**
 * The pure part of the sleep timer (UX audit P22C, registry row 734): the volume fade before it stops, and the
 * once-a-second countdown of the player's chip. Pure so a JVM test pins it.
 *
 * The timer used to cut the music dead at the deadline. It now fades the volume out over the last [FADE_MS] and only
 * then pauses, as a radio's sleep button does. Heat/battery (AGENTS rule 7): the service still sleeps until
 * `deadline - FADE_MS` in one coroutine; the fade is [STEPS] volume writes, once, at the very end.
 */
object SleepTimerMath {
    /** Length of the fade-out before the timer pauses. */
    const val FADE_MS = 10_000L

    /** Volume writes during the fade (one every 250 ms over the full [FADE_MS]). */
    const val STEPS = 40

    /** The one-tap presets of the player menu, in minutes. */
    val PRESET_MINUTES = listOf(15, 30, 45, 60)

    /** What the «+» of an active timer adds, in minutes. */
    const val EXTEND_MINUTES = 15

    /** How long to sleep before the fade starts for a deadline [leftMs] away; 0 = start fading now. */
    fun waitBeforeFadeMs(leftMs: Long): Long = (leftMs - FADE_MS).coerceAtLeast(0L)

    /**
     * Gain at [step] (1..[steps]) as a fraction of the volume the fade started from: equal-power cosine, so the
     * loudness falls evenly to the ear, reaching exactly 0 on the last step.
     */
    fun stepGain(step: Int, steps: Int = STEPS): Float {
        val p = step.coerceIn(0, steps).toDouble() / steps
        return cos(p * PI / 2.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * How long to wait after step [step] (1..[steps]) so the remaining steps share the [leftMs] left until the
     * deadline evenly. A late wake-up (doze) shortens the rest of the fade instead of running past the deadline.
     */
    fun stepDelayMs(leftMs: Long, step: Int, steps: Int = STEPS): Long {
        val remainingSteps = (steps - step + 1).coerceAtLeast(1)
        return (leftMs / remainingSteps).coerceAtLeast(0L)
    }

    /** Whole seconds shown for [leftMs] — rounded UP, so the chip reads 0:01 until the very end, never 0:00 early. */
    fun shownSeconds(leftMs: Long): Long = if (leftMs <= 0L) 0L else (leftMs + 999L) / 1000L

    /** «m:ss», or «h:mm:ss» from an hour up. */
    fun format(leftMs: Long): String {
        val total = shownSeconds(leftMs)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /**
     * Delay until the shown second changes for a deadline [leftMs] away (1..1000 ms): the chip wakes once per second,
     * on the second, and not more.
     */
    fun nextTickDelayMs(leftMs: Long): Long = if (leftMs <= 0L) 1_000L else ((leftMs - 1L) % 1000L) + 1L
}
