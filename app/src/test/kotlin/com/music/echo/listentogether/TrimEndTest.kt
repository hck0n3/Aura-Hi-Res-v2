package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Row 744 (P2-1): a speed trim ends when its gap is closed, not at the next 15 s heartbeat. Left on for 15 s, a trim computed to
 * close the gap in [SyncCorrection.CONVERGE_SECONDS] (10 s) closed 1.5× the gap and overshot to the other side.
 */
class TrimEndTest {
    /** What is left of [errorMs] after playing [durationMs] at [speed] relative to the host. */
    private fun residual(errorMs: Long, speed: Float, durationMs: Long): Double = errorMs + (speed - 1.0) * durationMs

    @Test
    fun anUncappedTrimLastsTheConvergeWindow() {
        val speed = SyncCorrection.trimSpeed(100L)
        assertEquals((SyncCorrection.CONVERGE_SECONDS * 1000).toDouble(), SyncCorrection.trimDurationMs(100L, speed).toDouble(), 2.0)
    }

    @Test
    fun aCappedTrimLastsUntilTheGapIsClosed() {
        // 300 ms at the 2 % cap: 15 s.
        val speed = SyncCorrection.trimSpeed(300L)
        assertEquals(1f - SyncCorrection.MAX_TRIM, speed, 1e-6f)
        assertEquals(15_000.0, SyncCorrection.trimDurationMs(300L, speed).toDouble(), 2.0)
    }

    @Test
    fun theTrimClosesTheGapWithoutOvershootingInEitherDirection() {
        for (error in listOf(61L, 80L, 150L, 200L, 250L, 600L, 1_000L, -61L, -150L, -400L, -1_000L)) {
            val speed = SyncCorrection.trimSpeed(error)
            val left = residual(error, speed, SyncCorrection.trimDurationMs(error, speed))
            assertTrue("error $error left $left ms", abs(left) <= 1.0)
        }
    }

    @Test
    fun keepingTheTrimForAWholeHeartbeatWouldOvershoot() {
        // The defect this fixes: 200 ms trimmed for 15 s ends 100 ms on the other side.
        val speed = SyncCorrection.trimSpeed(200L)
        assertTrue(residual(200L, speed, 15_000L) < -SyncCorrection.IN_SYNC_MS)
    }

    @Test
    fun noTrimMeansNoDuration() {
        assertEquals(0L, SyncCorrection.trimDurationMs(500L, 1f))
    }
}
