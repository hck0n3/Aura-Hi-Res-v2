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
