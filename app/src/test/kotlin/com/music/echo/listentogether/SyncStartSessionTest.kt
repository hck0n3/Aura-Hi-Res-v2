package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The room state a synchronized start (W2) leaves on this device, message by message (rows 742-743). */
class SyncStartSessionTest {
    private val session =
        ListenTogetherSession(
            ListenTogetherClient(
                clientVersion = "test",
                userAgent = "AuraHiRes/test (JVM)",
                elapsedRealtime = { System.nanoTime() / 1_000_000 },
            ),
            tokenStore = null,
        )

    private fun deliver(type: String, payload: Any?) {
        val onMessage = ListenTogetherSession::class.java.getDeclaredMethod("onMessage", String::class.java, Any::class.java)
        onMessage.isAccessible = true
        onMessage.invoke(session, type, payload)
    }

    private val next = TrackInfo(id = "next", duration = 200_000L)
    private val syncWait = BufferWaitPayload(trackId = "next", waitingFor = listOf("host", "me"), syncStart = true, startPosition = 0L)
    private val close = PlaybackActionPayload(action = PlaybackActions.PLAY, trackId = "next", position = 0L, capturedAtServerTime = 10_400L, startAtServerTime = 10_400L)

    @Test
    fun theBarrierHoldsTheTrackThenTheClosingPlayNamesTheStart() {
        deliver(MessageTypes.BUFFER_WAIT, syncWait)
        val held = session.state.value.syncStart!!
        assertEquals("next", held.trackId)
        assertEquals(0L, held.startAtServerTime)
        // the server sends the barrier before the track: the track keeps the hold
        deliver(MessageTypes.SYNC_PLAYBACK, PlaybackActionPayload(action = PlaybackActions.CHANGE_TRACK, trackId = "next", trackInfo = next))
        assertEquals(held, session.state.value.syncStart)
        deliver(MessageTypes.SYNC_PLAYBACK, close)
        deliver(MessageTypes.BUFFER_COMPLETE, BufferCompletePayload(trackId = "next"))
        val timed = session.state.value.syncStart!!
        assertEquals(10_400L, timed.startAtServerTime)
        assertEquals(held.revision, timed.revision)
        // and the room's timeline is the start position at the start time
        assertTrue(session.state.value.isPlaying)
        assertEquals(10_400L, session.state.value.lastActionServerTime)
    }

    @Test
    fun aSynchronizedWaitIsAnnouncedForAsLongAsItLasts() {
        deliver(MessageTypes.BUFFER_WAIT, syncWait)
        Thread.sleep(BUFFER_WAIT_INDICATOR_MAX_MS + 300)
        // everyone really waits, so «Esperando a X» is not cut at 2 s (row 669 cuts only the old barrier, which nobody hears)
        assertFalse(session.state.value.bufferWaitIndicatorExpired)
        assertEquals(listOf("host", "me"), session.state.value.waitingFor)
    }

    @Test
    fun aSynchronizedWaitWhoseEndNeverArrivesStopsBeingAnnounced() {
        // cut off during the barrier: the server drops this device from it and its buffer_complete goes only to the others
        deliver(MessageTypes.BUFFER_WAIT, syncWait)
        Thread.sleep(SYNC_START_HOLD_MAX_MS + 500)
        assertNull(session.state.value.syncStart)
        assertTrue(session.state.value.bufferWaitIndicatorExpired)
        assertTrue(session.state.value.waitingForNames.isEmpty())
    }

    @Test
    fun aBarrierThatEndsWithoutAStartOrAPauseLetsGo() {
        deliver(MessageTypes.BUFFER_WAIT, syncWait)
        deliver(MessageTypes.BUFFER_COMPLETE, BufferCompletePayload(trackId = "next"))
        assertNull(session.state.value.syncStart)
        deliver(MessageTypes.BUFFER_WAIT, syncWait)
        deliver(MessageTypes.SYNC_PLAYBACK, PlaybackActionPayload(action = PlaybackActions.PAUSE, position = 0L))
        assertNull(session.state.value.syncStart)
    }

    @Test
    fun anOldStyleBarrierHoldsNoOne() {
        deliver(MessageTypes.BUFFER_WAIT, BufferWaitPayload(trackId = "next", waitingFor = listOf("me")))
        assertNull(session.state.value.syncStart)
        deliver(MessageTypes.SYNC_PLAYBACK, PlaybackActionPayload(action = PlaybackActions.PLAY, position = 5_000L, capturedAtServerTime = 9_000L))
        assertNull(session.state.value.syncStart)
    }

    @Test
    fun theHostMeasuresItsDriftAgainstTheStartTheRoomNamed() {
        deliver(MessageTypes.ROOM_CREATED, RoomCreatedPayload(roomCode = "ABC234", userId = "host", sessionToken = "host.s"))
        deliver(MessageTypes.BUFFER_WAIT, syncWait)
        deliver(MessageTypes.SYNC_PLAYBACK, close)
        assertEquals(SentAnchor(position = 0L, serverTime = 10_400L, playing = true), session.lastSentAnchor)
    }

    @Test
    fun startingMarksOnlyTheHoldThatStarted() {
        deliver(MessageTypes.BUFFER_WAIT, syncWait)
        deliver(MessageTypes.SYNC_PLAYBACK, close)
        val timed = session.state.value.syncStart!!
        session.markSyncStartStarted(timed.copy(revision = timed.revision + 1))
        assertFalse("a hold that was replaced is not touched", session.state.value.syncStart!!.started)
        session.markSyncStartStarted(timed)
        assertTrue(session.state.value.syncStart!!.started)
        assertFalse(syncStartHolds(session.state.value.syncStart, "next"))
    }
}
