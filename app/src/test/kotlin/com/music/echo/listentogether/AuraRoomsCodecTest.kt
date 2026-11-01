package iad1tya.echo.music.listentogether

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aura's own rooms (docs/worker/rooms) speak the SAME payload classes as the Metrolist protocol, but as
 * JSON text: `{"type":…,"payload":{…}}`. These pin the two things the aura-rooms Worker depends on: the
 * field NAMES on the wire (they are the Kotlin property names) and that the JSON mode round-trips every
 * payload the room screen uses. The Worker side is pinned by docs/worker/rooms/test/rooms.test.mjs.
 */
class AuraRoomsCodecTest {
    private val codec = MessageCodec(compressionEnabled = false, json = true)

    private fun wire(
        type: String,
        payload: Any?,
    ) = Json.parseToJsonElement(codec.encode(type, payload).decodeToString()).jsonObject

    @Test
    fun theFrameIsTypeAndPayloadWithTheKotlinFieldNames() {
        val frame = wire(MessageTypes.JOIN_ROOM, JoinRoomPayload(roomCode = "ABC234", username = "Beto"))
        assertEquals(MessageTypes.JOIN_ROOM, frame["type"]!!.jsonPrimitive.content)
        val payload = frame["payload"]!!.jsonObject
        assertEquals("ABC234", payload["roomCode"]!!.jsonPrimitive.content)
        assertEquals("Beto", payload["username"]!!.jsonPrimitive.content)
    }

    @Test
    fun theSessionTokenTravelsAsSessionToken() {
        val payload = wire(MessageTypes.RECONNECT, ReconnectPayload(sessionToken = "USER.secret"))["payload"]!!.jsonObject
        assertEquals("USER.secret", payload["sessionToken"]!!.jsonPrimitive.content)
    }

    @Test
    fun transportCommandsKeepZeroesAndTheTrack() {
        val track = TrackInfo(id = "abc12345678", title = "T", artist = "A", duration = 200_000L)
        val payload =
            wire(
                MessageTypes.PLAYBACK_ACTION,
                PlaybackActionPayload(action = PlaybackActions.CHANGE_TRACK, trackId = track.id, position = 0L, trackInfo = track),
            )["payload"]!!.jsonObject
        // A position of 0 must be written: the JSON server reads a missing position as "not given".
        assertEquals("0", payload["position"]!!.jsonPrimitive.content)
        assertEquals("abc12345678", payload["trackInfo"]!!.jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun payloadlessMessagesSendAnEmptyObject() {
        val frame = wire(MessageTypes.LEAVE_ROOM, LeaveRoomPayload())
        assertTrue(frame["payload"]!!.jsonObject.isEmpty())
    }

    @Test
    fun whatTheServerSendsDecodesIntoTheSamePayloadClasses() {
        val text =
            """{"type":"join_approved","payload":{"roomCode":"ABC234","userId":"U1","sessionToken":"U1.s",
               "state":{"roomCode":"ABC234","hostId":"H","users":[{"userId":"H","username":"Ana","isHost":true,"isConnected":true}],
               "currentTrack":null,"isPlaying":true,"position":10300,"lastUpdate":99,"volume":1,"queue":[],"revision":4},
               "somethingNew":1}}"""
        val (type, bytes) = codec.decode(text.encodeToByteArray())
        assertEquals(MessageTypes.JOIN_APPROVED, type)
        val approved = codec.decodePayload(type, bytes) as JoinApprovedPayload
        assertEquals("U1.s", approved.sessionToken)
        val state = approved.state!!
        assertEquals("Ana", state.users.single().username)
        assertEquals(10_300L, state.position)
        assertEquals(4L, state.revision)
        assertTrue(state.isPlaying)
    }

    @Test
    fun theServerGreetingAndPongDecode() {
        val caps = """{"type":"server_capabilities","payload":{"supportsProtobuf":false,"supportsCompression":false,"serverVersion":"aura-rooms-1"}}"""
        val (capsType, capsBytes) = codec.decode(caps.encodeToByteArray())
        val decodedCaps = codec.decodePayload(capsType, capsBytes) as ServerCapabilities
        assertEquals("aura-rooms-1", decodedCaps.serverVersion)

        val pong = """{"type":"pong","payload":{"clientTime":5,"serverReceiveTime":100,"serverSendTime":101,"sequence":2}}"""
        val (pongType, pongBytes) = codec.decode(pong.encodeToByteArray())
        val decodedPong = codec.decodePayload(pongType, pongBytes) as PongPayload
        assertEquals(101L, decodedPong.serverSendTime)
        assertEquals(2L, decodedPong.sequence)
    }

    @Test
    fun anUnknownTypeIsNullNotAnException() {
        val (type, bytes) = codec.decode("""{"type":"chat","payload":{"text":"hola"}}""".encodeToByteArray())
        assertEquals("chat", type)
        assertEquals(null, codec.decodePayload(type, bytes))
    }

    @Test
    fun theAddressDependsOnWhatTheUserIsDoing() {
        val base = "wss://aura-rooms.example.workers.dev/"
        assertEquals(
            "wss://aura-rooms.example.workers.dev/rooms/create",
            ListenTogetherClient.auraUrl(base, ListenTogetherClient.RoomTarget.Create),
        )
        val join = ListenTogetherClient.auraUrl(base, ListenTogetherClient.RoomTarget.Join(" abc234 "))
        assertEquals("wss://aura-rooms.example.workers.dev/rooms/join?code=ABC234", join)
        assertNotNull(ListenTogetherClient.AURA_ROOMS_URL)
    }
}
