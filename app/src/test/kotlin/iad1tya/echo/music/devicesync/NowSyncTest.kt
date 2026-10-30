package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 534 — the other device's timeline follows this one: when a jump is worth telling, and how the time reads. */
class NowSyncTest {
    @Test
    fun `normal playback is not a jump, a drag of the bar is`() {
        // published at 10 s, 5 s later it should be at 15 s
        assertFalse(NowSync.jumped(10_000, 0, true, 15_000, 5_000))
        assertFalse("un pequeño retraso de red no cuenta", NowSync.jumped(10_000, 0, true, 16_500, 5_000))
        assertTrue("saltó adelante", NowSync.jumped(10_000, 0, true, 60_000, 5_000))
        assertTrue("saltó atrás", NowSync.jumped(100_000, 0, true, 3_000, 5_000))
    }

    @Test
    fun `while paused the position must not move at all`() {
        assertFalse(NowSync.jumped(42_000, 0, false, 42_000, 60_000))
        assertTrue(NowSync.jumped(42_000, 0, false, 90_000, 60_000))
    }

    @Test
    fun `the estimated position runs on with the server clock and stops when paused`() {
        val playing = NowState(listOf(NowItem("a", "t", "x", "")), 0, 10_000, true, "d", "Móvil", 1_000_000, 200_000)
        assertEquals(15_000L, playing.estimatedPositionMs(1_005_000))
        assertEquals("un reloj ligeramente atrasado no da posiciones negativas", 10_000L, playing.estimatedPositionMs(999_000))
        assertEquals(10_000L, playing.copy(playing = false).estimatedPositionMs(1_500_000))
    }

    @Test
    fun `times read as m ss and h mm ss`() {
        assertEquals("0:00", clock(0))
        assertEquals("0:09", clock(9_400))
        assertEquals("3:05", clock(185_000))
        assertEquals("1:02:03", clock(3_723_000))
        assertEquals("0:00", clock(-5))
    }
}
