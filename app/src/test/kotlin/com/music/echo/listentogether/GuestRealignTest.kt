package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A late guest realigns to the room when its audio becomes READY and when its clock calibrates, without undoing a local pause. */
class GuestRealignTest {
    @Test
    fun aPlayingRoomRealignsTheGuest() {
        assertTrue(shouldRealignGuest(roomPlaying = true, pausedLocally = false, causedByOwnJump = false))
    }

    @Test
    fun aPausedRoomOrALocalPauseIsLeftAlone() {
        assertFalse(shouldRealignGuest(roomPlaying = false, pausedLocally = false, causedByOwnJump = false))
        // row 647 (5): a guest who paused this device stays paused
        assertFalse(shouldRealignGuest(roomPlaying = true, pausedLocally = true, causedByOwnJump = false))
    }

    @Test
    fun theReadyCausedByTheRealignsOwnJumpDoesNotRealignAgain() {
        assertFalse(shouldRealignGuest(roomPlaying = true, pausedLocally = false, causedByOwnJump = true))
    }

    @Test
    fun aJumpMadeByTheClockCalibrationRealignMarksItsOwnReady() {
        val gate = GuestRealignGate()
        assertTrue(gate.shouldRealign(afterBuffering = false, nowMs = 0L, roomPlaying = true, pausedLocally = false))
        gate.onRealigned(seeked = true)
        // the READY that jump causes is skipped, even long after (spacing is not what stops it)
        assertFalse(gate.shouldRealign(afterBuffering = true, nowMs = 60_000L, roomPlaying = true, pausedLocally = false))
        // and only that one
        assertTrue(gate.shouldRealign(afterBuffering = true, nowMs = 120_000L, roomPlaying = true, pausedLocally = false))
    }

    @Test
    fun aReadyRealignThatJumpsSkipsTheReadyItCauses() {
        val gate = GuestRealignGate()
        assertTrue(gate.shouldRealign(afterBuffering = true, nowMs = 0L, roomPlaying = true, pausedLocally = false))
        gate.onRealigned(seeked = true)
        assertFalse(gate.shouldRealign(afterBuffering = true, nowMs = 60_000L, roomPlaying = true, pausedLocally = false))
    }

    @Test
    fun readyEdgesAreSpacedButTheClockCalibrationIsNot() {
        val gate = GuestRealignGate()
        assertTrue(gate.shouldRealign(afterBuffering = true, nowMs = 10_000L, roomPlaying = true, pausedLocally = false))
        gate.onRealigned(seeked = false)
        assertFalse(gate.shouldRealign(afterBuffering = true, nowMs = 10_000L + REALIGN_MIN_SPACING_MS - 1, roomPlaying = true, pausedLocally = false))
        assertTrue(gate.shouldRealign(afterBuffering = false, nowMs = 10_000L + 1, roomPlaying = true, pausedLocally = false))
        gate.onRealigned(seeked = false)
        assertTrue(gate.shouldRealign(afterBuffering = true, nowMs = 10_001L + REALIGN_MIN_SPACING_MS, roomPlaying = true, pausedLocally = false))
    }

    @Test
    fun leavingTheRoomForgetsThePendingSkipAndTheSpacing() {
        val gate = GuestRealignGate()
        assertTrue(gate.shouldRealign(afterBuffering = true, nowMs = 0L, roomPlaying = true, pausedLocally = false))
        gate.onRealigned(seeked = true)
        gate.reset()
        assertTrue(gate.shouldRealign(afterBuffering = true, nowMs = 1L, roomPlaying = true, pausedLocally = false))
    }

    @Test
    fun theGateNeverUndoesALocalPause() {
        val gate = GuestRealignGate()
        assertFalse(gate.shouldRealign(afterBuffering = false, nowMs = 0L, roomPlaying = true, pausedLocally = true))
        assertFalse(gate.shouldRealign(afterBuffering = true, nowMs = 0L, roomPlaying = false, pausedLocally = false))
    }

    @Test
    fun theClockBecomingReadyIsSignalledToTheBridge() {
        val session =
            ListenTogetherSession(
                ListenTogetherClient(
                    clientVersion = "test",
                    userAgent = "AuraHiRes/test (JVM)",
                    elapsedRealtime = { System.nanoTime() / 1_000_000 },
                ),
                tokenStore = null,
            )
        val onEvent = ListenTogetherSession::class.java.getDeclaredMethod("onEvent", ListenTogetherEvent::class.java)
        onEvent.isAccessible = true
        assertEquals(0, session.clockReadyCount.value)
        onEvent.invoke(session, ListenTogetherEvent.ClockReady)
        onEvent.invoke(session, ListenTogetherEvent.ClockReady)
        assertEquals(2, session.clockReadyCount.value)
    }
}
