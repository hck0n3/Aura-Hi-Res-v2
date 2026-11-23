package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 734 (UX audit P22C): the sleep timer fades out over its last 10 s and its chip ticks once a second. */
class SleepTimerMathTest {

    @Test
    fun `the service sleeps until ten seconds before the deadline, then fades`() {
        assertEquals(10_000L, SleepTimerMath.FADE_MS)
        assertEquals(15 * 60_000L - 10_000L, SleepTimerMath.waitBeforeFadeMs(15 * 60_000L))
        assertEquals(0L, SleepTimerMath.waitBeforeFadeMs(10_000L))
        assertEquals(0L, SleepTimerMath.waitBeforeFadeMs(3_000L))
        assertEquals(0L, SleepTimerMath.waitBeforeFadeMs(-5L))
    }

    @Test
    fun `the fade starts at full volume, falls steadily and ends in silence`() {
        assertEquals(1f, SleepTimerMath.stepGain(0), 1e-6f)
        assertEquals(0f, SleepTimerMath.stepGain(SleepTimerMath.STEPS), 1e-6f)
        var previous = 1f
        for (step in 1..SleepTimerMath.STEPS) {
            val gain = SleepTimerMath.stepGain(step)
            assertTrue("step $step", gain < previous)
            assertTrue(gain in 0f..1f)
            previous = gain
        }
        // Out-of-range steps never go above full volume or below silence.
        assertEquals(1f, SleepTimerMath.stepGain(-3), 1e-6f)
        assertEquals(0f, SleepTimerMath.stepGain(SleepTimerMath.STEPS + 5), 1e-6f)
    }

    @Test
    fun `the steps share the time left, so the fade ends at the deadline`() {
        assertEquals(250L, SleepTimerMath.stepDelayMs(10_000L, 1))
        // Woke late with 4 s left at step 21: the 20 remaining steps share those 4 s.
        assertEquals(200L, SleepTimerMath.stepDelayMs(4_000L, 21))
        assertEquals(500L, SleepTimerMath.stepDelayMs(500L, SleepTimerMath.STEPS))
        assertEquals(0L, SleepTimerMath.stepDelayMs(-1L, 3))
    }

    @Test
    fun `the chip shows whole seconds rounded up`() {
        assertEquals("0:00", SleepTimerMath.format(0L))
        assertEquals("0:01", SleepTimerMath.format(1L))
        assertEquals("1:00", SleepTimerMath.format(59_001L))
        assertEquals("1:01", SleepTimerMath.format(61_000L))
        assertEquals("15:00", SleepTimerMath.format(15 * 60_000L))
        assertEquals("1:00:00", SleepTimerMath.format(3_600_000L))
        assertEquals("1:30:05", SleepTimerMath.format(5_405_000L))
    }

    @Test
    fun `the chip wakes once per second, exactly when the shown second changes`() {
        assertEquals(500L, SleepTimerMath.nextTickDelayMs(1_500L))
        assertEquals(1_000L, SleepTimerMath.nextTickDelayMs(1_000L))
        assertEquals(1L, SleepTimerMath.nextTickDelayMs(1_001L))
        for (left in listOf(1L, 999L, 1_000L, 1_001L, 59_999L, 3_600_000L)) {
            val delay = SleepTimerMath.nextTickDelayMs(left)
            assertTrue("never more than once a second's worth of wake-ups", delay in 1L..1_000L)
            assertEquals(SleepTimerMath.shownSeconds(left) - 1, SleepTimerMath.shownSeconds(left - delay))
        }
    }

    @Test
    fun `the menu offers the four one-tap lengths the audit asked for`() {
        assertEquals(listOf(15, 30, 45, 60), SleepTimerMath.PRESET_MINUTES)
        assertEquals(15, SleepTimerMath.EXTEND_MINUTES)
    }
}
