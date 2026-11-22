package iad1tya.echo.music.listentogether

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The buffer barrier is answered only when the ROOM's track is loaded and READY, and every barrier gets its own answer. */
class BufferBarrierReadyTest {
    @Test
    fun theRoomsTrackLoadedAndReadyIsReady() {
        assertTrue(bufferBarrierReady("a", "a", Player.STATE_READY))
    }

    @Test
    fun theRoomsTrackStillBufferingIsNotReady() {
        assertFalse(bufferBarrierReady("a", "a", Player.STATE_BUFFERING))
        assertFalse(bufferBarrierReady("a", "a", Player.STATE_IDLE))
        assertFalse(bufferBarrierReady("a", "a", Player.STATE_ENDED))
    }

    @Test
    fun thePreviousSongReadyIsNotReadyForTheNewOne() {
        // the old code answered from bufferedPercentage of whatever was loaded: the previous song counted
        assertFalse(bufferBarrierReady("new", "old", Player.STATE_READY))
        assertFalse(bufferBarrierReady("new", null, Player.STATE_READY))
    }

    @Test
    fun noRoomTrackIsNeverReady() {
        assertFalse(bufferBarrierReady(null, null, Player.STATE_READY))
        assertFalse(bufferBarrierReady("", "", Player.STATE_READY))
    }

    @Test
    fun aSecondBarrierWithTheSameWaitingListIsANewRevision() {
        val session =
            ListenTogetherSession(
                ListenTogetherClient(
                    clientVersion = "test",
                    userAgent = "AuraHiRes/test (JVM)",
                    elapsedRealtime = { System.nanoTime() / 1_000_000 },
                ),
                tokenStore = null,
            )
        val onMessage = ListenTogetherSession::class.java.getDeclaredMethod("onMessage", String::class.java, Any::class.java)
        onMessage.isAccessible = true
        onMessage.invoke(session, MessageTypes.BUFFER_WAIT, BufferWaitPayload(trackId = "a", waitingFor = listOf("me")))
        onMessage.invoke(session, MessageTypes.BUFFER_WAIT, BufferWaitPayload(trackId = "a", waitingFor = listOf("me")))
        assertEquals(listOf("me"), session.state.value.waitingFor)
        assertEquals(2, session.state.value.bufferWaitRevision)
        // the barrier names its track: that is what this device answers about, even before the change_track arrives
        assertEquals("a", session.state.value.bufferWaitTrackId)
    }

    private val waiting =
        ListenTogetherState(
            members = listOf(RoomMember("u1", "Long", isHost = false, isConnected = true, isBuffering = true)),
            waitingFor = listOf("u1"),
            bufferWaitRevision = 3,
        )

    @Test
    fun theWaitIndicatorExpiresWhileTheSameBarrierIsOpen() {
        assertEquals(listOf("Long"), waiting.waitingForNames)
        val expired = waiting.expireBufferWaitIndicator(3)
        assertEquals(emptyList<String>(), expired.waitingForNames)
        assertFalse(expired.members.single().isBuffering)
        // the bridge still sees who the barrier waits for
        assertEquals(listOf("u1"), expired.waitingFor)
    }

    @Test
    fun aNewerOrFinishedBarrierIsNotExpiredByAnOldTimer() {
        assertEquals(waiting, waiting.expireBufferWaitIndicator(2))
        val finished = waiting.copy(waitingFor = emptyList())
        assertEquals(finished, finished.expireBufferWaitIndicator(3))
    }
}
