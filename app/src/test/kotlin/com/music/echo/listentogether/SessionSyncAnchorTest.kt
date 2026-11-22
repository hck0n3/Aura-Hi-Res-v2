package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The room position and the server time it is anchored to must move together (c0ec786, b8a8f09):
 * sync_state anchors to its lastUpdate when it has one, and a queue update touches neither.
 * Also the bridge's cap of the extrapolated position to the track length (0fa3acf, f9f2c7e).
 */
class SessionSyncAnchorTest {
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

    private val track = TrackInfo(id = "a", duration = 200_000L)

    @Test
    fun syncStateAnchorsThePositionToItsLastUpdate() {
        deliver(MessageTypes.SYNC_STATE, SyncStatePayload(currentTrack = track, isPlaying = true, position = 42_000L, lastUpdate = 1_000L))
        assertEquals(42_000L, session.state.value.position)
        assertEquals(1_000L, session.state.value.lastActionServerTime)
    }

    @Test
    fun syncStateWithoutLastUpdateKeepsThePreviousAnchor() {
        deliver(MessageTypes.SYNC_STATE, SyncStatePayload(currentTrack = track, isPlaying = true, position = 42_000L, lastUpdate = 1_000L))
        deliver(MessageTypes.SYNC_STATE, SyncStatePayload(currentTrack = track, isPlaying = true, position = 50_000L, lastUpdate = 0L))
        assertEquals(50_000L, session.state.value.position)
        assertEquals(1_000L, session.state.value.lastActionServerTime)
    }

    @Test
    fun aQueueUpdateMovesNeitherThePositionNorItsAnchor() {
        deliver(
            MessageTypes.PLAYBACK_ACTION,
            PlaybackActionPayload(action = PlaybackActions.SEEK, position = 30_000L, capturedAtServerTime = 5_000L),
        )
        deliver(
            MessageTypes.PLAYBACK_ACTION,
            PlaybackActionPayload(action = PlaybackActions.SYNC_QUEUE, queue = listOf(track, TrackInfo(id = "b")), capturedAtServerTime = 9_000L),
        )
        assertEquals(30_000L, session.state.value.position)
        assertEquals(5_000L, session.state.value.lastActionServerTime)
        assertEquals(listOf("a", "b"), session.state.value.queue.map { it.id })
    }

    private fun cap(position: Long, track: TrackInfo?, localDurationMs: Long): Long {
        val bridge = ListenTogetherPlaybackBridge(session)
        val method =
            ListenTogetherPlaybackBridge::class.java.getDeclaredMethod(
                "capToTrack",
                java.lang.Long.TYPE,
                TrackInfo::class.java,
                java.lang.Long.TYPE,
            )
        method.isAccessible = true
        return method.invoke(bridge, position, track, localDurationMs) as Long
    }

    @Test
    fun theCapUsesTheRoomDurationAndFallsBackToTheLocalOne() {
        assertEquals(199_500L, cap(500_000L, track, 0L))
        assertEquals(179_500L, cap(500_000L, TrackInfo(id = "a", duration = 0L), 180_000L))
        assertEquals(500_000L, cap(500_000L, TrackInfo(id = "a", duration = 0L), Long.MIN_VALUE + 1))
        assertEquals(10_000L, cap(10_000L, track, 0L))
    }
}
