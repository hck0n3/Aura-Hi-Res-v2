package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 796: a guest re-applying the room's last state (heartbeat, realign, recheck) without a calibrated server clock or an anchor
 * must not take the room to be where its last command was — that jumped the guest back by up to 15 s on every heartbeat.
 */
class RoomPositionTest {
    private val receipt = RoomTimelineReceipt(trackId = "A", position = 30_000L, anchorServerTime = 1_000_000L, playing = true, receivedAtMs = 50_000L)

    @Test
    fun withACalibratedClockTheServersTimeDecides() {
        assertEquals(
            45_000L,
            roomPositionNow("A", 30_000L, playing = true, anchorServerTime = 1_000_000L, serverNow = 1_015_000L, receipt = receipt, nowMs = 99_999L),
        )
    }

    @Test
    fun beforeTheNewSocketsFirstPongTheGuestCountsFromWhenItReceivedTheTimeline() {
        // Fifteen seconds after the command arrived, on a socket whose clock was reset by a reconnect and has not calibrated yet.
        assertEquals(45_000L, roomPositionNow("A", 30_000L, playing = true, anchorServerTime = 1_000_000L, serverNow = null, receipt = receipt, nowMs = 65_000L))
    }

    @Test
    fun onceTheClockCalibratesAgainItTakesOver() {
        val before = roomPositionNow("A", 30_000L, playing = true, anchorServerTime = 1_000_000L, serverNow = null, receipt = receipt, nowMs = 60_000L)
        val after = roomPositionNow("A", 30_000L, playing = true, anchorServerTime = 1_000_000L, serverNow = 1_010_200L, receipt = receipt, nowMs = 60_000L)
        assertEquals(40_000L, before)
        assertEquals(40_200L, after)
    }

    @Test
    fun aCommandWithNoCaptureTimeIsCountedFromItsArrival() {
        val unanchored = receipt.copy(anchorServerTime = 0L)
        assertEquals(37_000L, roomPositionNow("A", 30_000L, playing = true, anchorServerTime = 0L, serverNow = 2_000_000L, receipt = unanchored, nowMs = 57_000L))
    }

    @Test
    fun aPausedRoomOrAReceiptForAnotherTimelineFallsBackToTheRawPosition() {
        assertEquals(30_000L, roomPositionNow("A", 30_000L, playing = false, anchorServerTime = 1_000_000L, serverNow = 1_015_000L, receipt = receipt, nowMs = 65_000L))
        assertEquals(31_000L, roomPositionNow("A", 31_000L, playing = true, anchorServerTime = 1_000_000L, serverNow = null, receipt = receipt, nowMs = 65_000L))
        assertEquals(30_000L, roomPositionNow("A", 30_000L, playing = true, anchorServerTime = 1_000_000L, serverNow = null, receipt = receipt.copy(playing = false), nowMs = 65_000L))
        assertEquals(30_000L, roomPositionNow("A", 30_000L, playing = true, anchorServerTime = 1_000_000L, serverNow = null, receipt = null, nowMs = 65_000L))
    }

    @Test
    fun aReceiptForAnotherTrackWithTheSamePositionAndAnchorIsNotUsed() {
        // change_track(B, 0, anchor 0) three minutes ago, then change_track(C, 0, anchor 0): C starts from the top, not 3 min in.
        val trackB = RoomTimelineReceipt(trackId = "B", position = 0L, anchorServerTime = 0L, playing = true, receivedAtMs = 10_000L)
        assertEquals(0L, roomPositionNow("C", 0L, playing = true, anchorServerTime = 0L, serverNow = null, receipt = trackB, nowMs = 190_000L))
        assertEquals(180_000L, roomPositionNow("B", 0L, playing = true, anchorServerTime = 0L, serverNow = null, receipt = trackB, nowMs = 190_000L))
    }

    @Test
    fun aServerTimeBehindTheAnchorNeverMovesThePositionBack() {
        assertEquals(30_000L, roomPositionNow("A", 30_000L, playing = true, anchorServerTime = 1_000_000L, serverNow = 999_000L, receipt = receipt, nowMs = 65_000L))
    }

    @Test
    fun theBridgeRecordsTheReceiptAndCorrectsWithIt() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val bridge = File(root, "app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt").readText()
        assertTrue(bridge.contains("roomReceipt = RoomTimelineReceipt(track?.id, position, anchorServerTime, playing, android.os.SystemClock.elapsedRealtime())"))
        assertTrue("a new track is a new timeline", bridge.contains("receipt == null || receipt.trackId != track?.id || receipt.position != position ||"))
        val apply = bridge.substringAfter("private suspend fun applyTransport(").substringBefore("private suspend fun playTrack(")
        assertTrue(apply.contains("val roomPosition = roomPositionNow(\n            trackId = roomTrack?.id,"))
        assertTrue(apply.contains("receipt = roomReceipt,"))
        assertTrue("leaving the room forgets it", bridge.contains("trackGuard.reset()\n                    roomReceipt = null"))
    }
}
