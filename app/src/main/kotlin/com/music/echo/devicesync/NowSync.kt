package iad1tya.echo.music.devicesync

import kotlin.math.abs

/** Row 534 — pure rules for publishing what this device plays, so the others' timeline follows it (a seek, a pause, a skip). */
object NowSync {
    /** How far the real position may stray from "where it should be by now" before it counts as a jump worth telling the others. */
    const val DRIFT_TOLERANCE_MS = 2_500L

    /**
     * True when the position moved by something other than normal playback: the user dragged the bar, a skip landed mid-song, a seek
     * came from another device. [lastPositionMs] was published at [lastAtMs] (local wall clock); while playing the position should
     * have advanced by the time elapsed since; paused, it should not have moved at all.
     */
    fun jumped(lastPositionMs: Long, lastAtMs: Long, wasPlaying: Boolean, positionMs: Long, nowMs: Long): Boolean {
        val expected = if (wasPlaying) lastPositionMs + (nowMs - lastAtMs).coerceAtLeast(0L) else lastPositionMs
        return abs(positionMs - expected) > DRIFT_TOLERANCE_MS
    }
}
