package iad1tya.echo.music.listentogether

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A3 (rows 742-743): with Aura's rooms server W2 every device, the host too, waits paused at the start position and starts at the
 * server's start time; with Metrolist's server or an older Aura server nothing holds and nothing is timed.
 */
@OptIn(ExperimentalSerializationApi::class)
class SyncStartTest {
    // ── when play() is called ──────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun onTimeItWaitsForTheStartTimeLessItsOwnOutputLatency() {
        assertEquals(SyncStartTiming(400L, 0L), syncStartTiming(10_400L, 10_000L, 0, 0L))
        // Bluetooth: play() 180 ms earlier, so the sound leaves the headphones at the start time (row 262)
        assertEquals(SyncStartTiming(220L, 30_000L), syncStartTiming(10_400L, 10_000L, OutputLatency.BLUETOOTH_MS, 30_000L))
    }

    @Test
    fun aLittleLateItStartsNowFromWhereTheRoomAlreadyIs() {
        assertEquals(SyncStartTiming(0L, 100L), syncStartTiming(10_000L, 10_100L, 0, 0L))
        assertEquals(SyncStartTiming(0L, 30_280L), syncStartTiming(10_000L, 10_100L, OutputLatency.BLUETOOTH_MS, 30_000L))
    }

    @Test
    fun nothingIsTimedWithoutAStartTimeAClockOrWhenTooLate() {
        // an older Aura server and Metrolist's never send a start time: today's behaviour
        assertNull(syncStartTiming(0L, 10_000L, 0, 0L))
        // an uncalibrated clock cannot time anything
        assertNull(syncStartTiming(10_400L, null, 0, 0L))
        // further in the past than the server's lead: follow the room as before
        assertNull(syncStartTiming(10_000L, 10_000L + SYNC_START_LEAD_MS + 1, 0, 0L))
    }

    @Test
    fun aWaitLongerThanTheServersLeadIsAClockErrorAndIsNotWaitedOut() {
        assertEquals(SyncStartTiming(SYNC_START_LEAD_MS, 0L), syncStartTiming(20_000L, 10_000L, 0, 0L))
    }

    // ── who holds ──────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private val hold = SyncStart(trackId = "next", position = 0L, revision = 3)

    @Test
    fun aHoldHoldsOnlyItsOwnTrackAndOnlyUntilItStarts() {
        assertTrue(syncStartHolds(hold, "next"))
        assertTrue(syncStartHolds(hold.copy(startAtServerTime = 99L), "next"))
        assertFalse("the song still playing is not stopped by the next one's barrier", syncStartHolds(hold, "current"))
        assertFalse(syncStartHolds(hold.copy(started = true), "next"))
        assertFalse(syncStartHolds(null, "next"))
        assertTrue("no room track yet: still held", syncStartHolds(hold, null))
    }

    @Test
    fun onlyASynchronizedBarrierHolds() {
        assertEquals(
            SyncStart(trackId = "next", position = 1_234L, revision = 4),
            syncStartAfterBufferWait(BufferWaitPayload(trackId = "next", waitingFor = listOf("a"), syncStart = true, startPosition = 1_234L), 4),
        )
        assertNull(syncStartAfterBufferWait(BufferWaitPayload(trackId = "next", waitingFor = listOf("a")), 4))
    }

    @Test
    fun aBarrierThatEndsWithoutAStartTimeHoldsNoOne() {
        assertNull(syncStartAfterBufferComplete(hold))
        val timed = hold.copy(startAtServerTime = 99L)
        assertEquals(timed, syncStartAfterBufferComplete(timed))
        assertNull(syncStartAfterBufferComplete(null))
    }

    @Test
    fun theClosingPlayNamesTheStartAndOtherCommandsEndOrKeepTheHold() {
        val close = PlaybackActionPayload(action = PlaybackActions.PLAY, trackId = "next", position = 0L, startAtServerTime = 10_400L)
        assertEquals(hold.copy(startAtServerTime = 10_400L), syncStartAfterCommand(hold, close, 9))
        // someone who arrived during the barrier never saw it: the closing play is a hold of its own
        assertEquals(SyncStart("next", 0L, revision = 9, startAtServerTime = 10_400L), syncStartAfterCommand(null, close, 9))
        assertNull("an ordinary play means the room moved on", syncStartAfterCommand(hold, PlaybackActionPayload(action = PlaybackActions.PLAY), 9))
        assertNull(syncStartAfterCommand(hold, PlaybackActionPayload(action = PlaybackActions.PAUSE), 9))
        val sameTrack = PlaybackActionPayload(action = PlaybackActions.CHANGE_TRACK, trackId = "next", trackInfo = TrackInfo(id = "next"))
        assertEquals(hold, syncStartAfterCommand(hold, sameTrack, 9))
        val otherTrack = PlaybackActionPayload(action = PlaybackActions.CHANGE_TRACK, trackId = "else", trackInfo = TrackInfo(id = "else"))
        assertNull(syncStartAfterCommand(hold, otherTrack, 9))
        assertEquals(hold, syncStartAfterCommand(hold, PlaybackActionPayload(action = PlaybackActions.SEEK, position = 5L), 9))
        assertEquals(hold, syncStartAfterCommand(hold, PlaybackActionPayload(action = PlaybackActions.SYNC_QUEUE), 9))
    }

    // ── the wire: optional both ways ───────────────────────────────────────────────────────────────────────────────────────────

    private val json = MessageCodec(compressionEnabled = false, json = true)

    @Test
    fun theNewFieldsAreOptionalOnAurasJsonWire() {
        val sent =
            Json.parseToJsonElement(
                json.encode(MessageTypes.PLAYBACK_ACTION, PlaybackActionPayload(action = PlaybackActions.CHANGE_TRACK, hostPlaying = true)).decodeToString(),
            ).jsonObject["payload"]!!.jsonObject
        assertEquals("true", sent["hostPlaying"]!!.jsonPrimitive.content)
        val unsaid =
            Json.parseToJsonElement(json.encode(MessageTypes.PLAYBACK_ACTION, PlaybackActionPayload(action = PlaybackActions.PAUSE)).decodeToString())
                .jsonObject["payload"]!!.jsonObject
        // null is not a boolean: the server treats this app's pause exactly like an older app's
        assertEquals(JsonNull, unsaid["hostPlaying"])

        val (closeType, closeBytes) =
            json.decode(
                """{"type":"sync_playback","payload":{"action":"play","trackId":"next","position":0,"serverTime":10000,"capturedAtServerTime":10400,"startAtServerTime":10400}}"""
                    .encodeToByteArray(),
            )
        assertEquals(10_400L, (json.decodePayload(closeType, closeBytes) as PlaybackActionPayload).startAtServerTime)

        val (waitType, waitBytes) =
            json.decode("""{"type":"buffer_wait","payload":{"trackId":"next","waitingFor":["a"],"syncStart":true,"startPosition":30000}}""".encodeToByteArray())
        val wait = json.decodePayload(waitType, waitBytes) as BufferWaitPayload
        assertTrue(wait.syncStart)
        assertEquals(30_000L, wait.startPosition)

        // an older Aura server: no new keys, no hold, no start time
        val (oldType, oldBytes) = json.decode("""{"type":"buffer_wait","payload":{"trackId":"next","waitingFor":["a"]}}""".encodeToByteArray())
        assertFalse((json.decodePayload(oldType, oldBytes) as BufferWaitPayload).syncStart)
        val (oldPlayType, oldPlayBytes) =
            json.decode("""{"type":"sync_playback","payload":{"action":"play","position":5,"capturedAtServerTime":9}}""".encodeToByteArray())
        assertEquals(0L, (json.decodePayload(oldPlayType, oldPlayBytes) as PlaybackActionPayload).startAtServerTime)
    }

    @Test
    fun nothingNewReachesMetrolistsProtobufWire() {
        // field 1 "play" (0A 04 70 6C 61 79) and field 3 = 5 (18 05), and nothing else: the extensions stay off the wire unless set
        val bytes = ProtoBuf { encodeDefaults = false }.encodeToByteArray(PlaybackActionPayload.serializer(), PlaybackActionPayload(action = "play", position = 5L))
        assertArrayEquals(byteArrayOf(0x0A, 0x04, 0x70, 0x6C, 0x61, 0x79, 0x18, 0x05), bytes)
        // and a Metrolist buffer_wait, which never carries them, decodes as the old barrier
        val wait = ProtoBuf.decodeFromByteArray(BufferWaitPayload.serializer(), ProtoBuf.encodeToByteArray(BufferWaitPayload.serializer(), BufferWaitPayload("t", listOf("a"))))
        assertFalse(wait.syncStart)
    }
}
