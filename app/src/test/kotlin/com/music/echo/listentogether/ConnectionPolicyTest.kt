package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 744: the connection upkeep of [ListenTogetherClient] — liveness watch (P1-9). */
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
}
