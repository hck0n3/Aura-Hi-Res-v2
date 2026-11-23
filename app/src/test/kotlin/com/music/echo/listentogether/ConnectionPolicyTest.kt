package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 744: the connection upkeep of [ListenTogetherClient] — liveness watch (P1-9), room window (P2-9), a room that is gone. */
class ConnectionPolicyTest {
    @Test
    fun anyFrameSinceThePingResetsTheCount() {
        assertEquals(0, ConnectionPolicy.unansweredAfter(previous = 1, heardSincePing = true))
        assertEquals(2, ConnectionPolicy.unansweredAfter(previous = 1, heardSincePing = false))
    }

    @Test
    fun oneLostPongIsNotADeadSocketButTwoInARowAre() {
        var unanswered = ConnectionPolicy.unansweredAfter(0, heardSincePing = false)
        assertFalse(ConnectionPolicy.socketLooksDead(unanswered))
        unanswered = ConnectionPolicy.unansweredAfter(unanswered, heardSincePing = true)
        unanswered = ConnectionPolicy.unansweredAfter(unanswered, heardSincePing = false)
        assertFalse("the count restarted after an answer", ConnectionPolicy.socketLooksDead(unanswered))
        unanswered = ConnectionPolicy.unansweredAfter(unanswered, heardSincePing = false)
        assertTrue(ConnectionPolicy.socketLooksDead(unanswered))
    }

    @Test
    fun insideARoomTheAttemptsAreNotCountedOnlyTheTimeSinceTheDrop() {
        // P2-9: 5 attempts (~31 s) used to end the room for a phone in Doze.
        assertFalse(ConnectionPolicy.shouldGiveUp(inRoom = true, attempts = 5, maxAttempts = 5, droppedAtMs = 0L, nowMs = 31_000L))
        assertFalse(ConnectionPolicy.shouldGiveUp(inRoom = true, attempts = 40, maxAttempts = 5, droppedAtMs = 0L, nowMs = 9 * 60_000L))
        assertTrue(
            ConnectionPolicy.shouldGiveUp(
                inRoom = true,
                attempts = 12,
                maxAttempts = 5,
                droppedAtMs = 1_000L,
                nowMs = 1_000L + ConnectionPolicy.ROOM_RESUME_WINDOW_MS,
            ),
        )
    }

    @Test
    fun outsideARoomTheAttemptBudgetStays() {
        assertFalse(ConnectionPolicy.shouldGiveUp(inRoom = false, attempts = 4, maxAttempts = 5, droppedAtMs = 0L, nowMs = 0L))
        assertTrue(ConnectionPolicy.shouldGiveUp(inRoom = false, attempts = 5, maxAttempts = 5, droppedAtMs = 0L, nowMs = 31_000L))
    }

    @Test
    fun aRefusedUpgradeIsReadFromOkHttpsMessageAnywhereInTheCauseChain() {
        val refused = java.net.ProtocolException("Expected HTTP 101 response but was '404 Not Found'")
        assertEquals(404, ConnectionPolicy.refusedUpgradeStatus(refused))
        assertEquals(404, ConnectionPolicy.refusedUpgradeStatus(IllegalStateException("wrapped", refused)))
        assertEquals(400, ConnectionPolicy.refusedUpgradeStatus(java.net.ProtocolException("Expected HTTP 101 response but was '400 '")))
        assertNull("no network is a reason to retry", ConnectionPolicy.refusedUpgradeStatus(java.net.UnknownHostException("no address")))
        assertNull(ConnectionPolicy.refusedUpgradeStatus(java.io.IOException("timeout")))
    }

    @Test
    fun onlyAMissingRoomOrAMalformedCodeIsFinal() {
        assertTrue(ConnectionPolicy.roomIsGone(404))
        assertTrue(ConnectionPolicy.roomIsGone(400))
        assertFalse("a busy server is worth another try", ConnectionPolicy.roomIsGone(503))
        assertFalse(ConnectionPolicy.roomIsGone(429))
    }

    @Test
    fun theRoomWindowOutlastsAnEmptyAuraRoom() {
        // docs/worker/rooms keeps a room nobody is connected to for EMPTY_ROOM_MS = 5 min.
        assertTrue(ConnectionPolicy.ROOM_RESUME_WINDOW_MS >= 5 * 60_000L)
    }
}
