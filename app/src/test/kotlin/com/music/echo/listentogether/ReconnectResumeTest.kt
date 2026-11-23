package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 744 (P1-8): `reconnected` used to be ignored, so after a drop a guest kept a stale room and a host never told the room what it
 * did while offline. And a `reconnect` the server refused left the room on screen forever.
 */
class ReconnectResumeTest {
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

    private fun event(e: ListenTogetherEvent) {
        val onEvent = ListenTogetherSession::class.java.getDeclaredMethod("onEvent", ListenTogetherEvent::class.java)
        onEvent.isAccessible = true
        onEvent.invoke(session, e)
    }

    private val track = TrackInfo(id = "b", duration = 200_000L)

    private fun joinAsGuest() =
        deliver(
            MessageTypes.JOIN_APPROVED,
            JoinApprovedPayload(roomCode = "ROOM1", userId = "me", sessionToken = "t", state = RoomState(currentTrack = TrackInfo(id = "a"))),
        )

    @Test
    fun aGuestTakesTheRoomAsItIsNowAfterReconnecting() {
        joinAsGuest()
        deliver(
            MessageTypes.RECONNECTED,
            ReconnectedPayload(
                roomCode = "ROOM1",
                userId = "me",
                isHost = false,
                state =
                    RoomState(
                        roomCode = "ROOM1",
                        users = listOf(UserInfo(userId = "host", isHost = true, isConnected = true), UserInfo(userId = "me", isConnected = true)),
                        currentTrack = track,
                        isPlaying = true,
                        position = 42_000L,
                        lastUpdate = 9_000L,
                        queue = listOf(TrackInfo(id = "b"), TrackInfo(id = "c")),
                    ),
            ),
        )
        val s = session.state.value
        assertTrue(s.inRoom)
        assertFalse(s.isHost)
        assertEquals("b", s.currentTrack?.id)
        assertTrue(s.isPlaying)
        assertEquals(42_000L, s.position)
        assertEquals("the position is anchored to the server's now", 9_000L, s.lastActionServerTime)
        assertEquals(listOf("b", "c"), s.queue.map { it.id })
        assertEquals(2, s.members.size)
        assertEquals(1, s.reconnectRevision)
    }

    @Test
    fun aColdStartResumeRebuildsTheRoomAndTheHostRole() {
        deliver(
            MessageTypes.RECONNECTED,
            ReconnectedPayload(roomCode = "ROOM2", userId = "me", isHost = true, state = RoomState(roomCode = "ROOM2", hostId = "me")),
        )
        val s = session.state.value
        assertEquals("ROOM2", s.roomCode)
        assertEquals("me", s.selfUserId)
        assertTrue(s.isHost)
        assertEquals(1, s.reconnectRevision)
    }

    @Test
    fun aRefusedResumeEndsTheRoomInsteadOfLeavingItOnScreen() {
        joinAsGuest()
        event(ListenTogetherEvent.Connected(serverVersion = "x", compressionEnabled = false, resumed = true))
        deliver(MessageTypes.ERROR, ErrorPayload(code = "bad_token", message = ""))
        val s = session.state.value
        assertFalse(s.inRoom)
        assertEquals("bad_token", s.error)
    }

    @Test
    fun anErrorAfterTheResumeWasConfirmedIsJustAnError() {
        joinAsGuest()
        event(ListenTogetherEvent.Connected(serverVersion = "x", compressionEnabled = false, resumed = true))
        deliver(MessageTypes.SYNC_STATE, SyncStatePayload(currentTrack = track, isPlaying = true, position = 1_000L, lastUpdate = 2_000L))
        deliver(MessageTypes.ERROR, ErrorPayload(code = "stale_track", message = ""))
        assertTrue(session.state.value.inRoom)
        assertEquals("stale_track", session.state.value.error)
    }

    @Test
    fun anErrorOnAFreshSocketDoesNotEndTheRoom() {
        joinAsGuest()
        event(ListenTogetherEvent.Connected(serverVersion = "x", compressionEnabled = false, resumed = false))
        deliver(MessageTypes.ERROR, ErrorPayload(code = "invalid_payload", message = ""))
        assertTrue(session.state.value.inRoom)
    }

    @Test
    fun rateLimitingNeverCountsAsARefusedResume() {
        joinAsGuest()
        event(ListenTogetherEvent.Connected(serverVersion = "x", compressionEnabled = false, resumed = true))
        deliver(MessageTypes.ERROR, ErrorPayload(code = "rate_limited", message = ""))
        assertTrue(session.state.value.inRoom)
        assertNull(session.state.value.error)
    }
}
