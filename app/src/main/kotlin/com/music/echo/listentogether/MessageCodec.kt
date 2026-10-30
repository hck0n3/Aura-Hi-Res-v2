/*
 * Ported from SimpMusic (which ported it from Metrolist, GPL-3.0 — the same licence as this project).
 * Metrolist Project (C) 2026 — Licensed under GPL-3.0 | See git history for contributors
 */
package iad1tya.echo.music.listentogether

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Turns a payload into the bytes that go on the socket, and back.
 *
 * Wire format, exactly as Metrolist, SimpMusic and the servers speak it: the payload is
 * protobuf-encoded, gzipped when it is worth it, then wrapped in an [Envelope] carrying the message
 * type and a `compressed` flag — and the envelope itself is protobuf.
 *
 * (Upstream SimpMusic uses okio's GzipSink/GzipSource; this app has no okio on the app-module
 * classpath by intent, so the same streams come from java.util.zip — the gzip bytes are identical.)
 */
@OptIn(ExperimentalSerializationApi::class)
internal class MessageCodec(
    private val compressionEnabled: Boolean = true,
    /**
     * Aura's own rooms (aura-rooms Worker) speak JSON text frames: `{"type":…,"payload":{…}}` with the
     * very same field names as these classes. Everything above this codec — which payload class a type
     * decodes to, the byte arrays handed around — stays identical; only the serialisation changes, and
     * the "bytes" of a payload are then the UTF-8 of its JSON. Never gzipped.
     */
    private val json: Boolean = false,
) {
    /**
     * `encodeDefaults = false` is not a preference — it is what proto3 means.
     *
     * A proto3 field holding its default value is simply absent from the wire, which is what
     * `protoc` emits and what Go's `proto.Marshal` emits, so this is the setting that makes the
     * "same bytes as protoc" claim in Protocol.kt actually true. With `true`, encoding any payload
     * carrying a null message field — `PlaybackActionPayload.trackInfo` on every play, pause, seek
     * and volume command — threw `'null' is not supported for optional properties in ProtoBuf`
     * instead, so no transport command could be sent at all.
     */
    private val proto = ProtoBuf { encodeDefaults = false }

    /** Defaults ARE written (a 0 position or an empty queue is information for the JSON server); unknown keys are ignored. */
    private val wireJson = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = true }

    private fun <T> enc(serializer: KSerializer<T>, value: T): ByteArray =
        if (json) wireJson.encodeToString(serializer, value).encodeToByteArray() else proto.encodeToByteArray(serializer, value)

    /** Encodes one message into a complete frame. */
    fun encode(
        msgType: String,
        payload: Any?,
    ): ByteArray {
        val payloadBytes = payload?.let { encodePayload(it) } ?: ByteArray(0)
        if (json) {
            val body = if (payloadBytes.isEmpty()) JsonObject(emptyMap()) else wireJson.parseToJsonElement(payloadBytes.decodeToString())
            return JsonObject(mapOf("type" to JsonPrimitive(msgType), "payload" to body)).toString().encodeToByteArray()
        }
        // Below the threshold gzip reliably makes the frame LARGER — its header alone is 10 bytes,
        // and most of these messages are a handful of fields.
        val compress = compressionEnabled && payloadBytes.size > COMPRESSION_THRESHOLD
        val body = if (compress) gzip(payloadBytes) else payloadBytes
        return proto.encodeToByteArray(
            Envelope.serializer(),
            Envelope(type = msgType, payload = body, compressed = compress),
        )
    }

    /**
     * Unwraps a frame into its type and its still-encoded payload.
     *
     * Decompression failure returns the payload untouched rather than throwing: the flag is set by
     * the sender, and a frame we cannot inflate is more likely mislabelled than fatal.
     */
    fun decode(data: ByteArray): Pair<String, ByteArray> {
        if (json) {
            val root = wireJson.parseToJsonElement(data.decodeToString()).jsonObject
            val type = root["type"]?.jsonPrimitive?.contentOrNull ?: ""
            return type to (root["payload"]?.toString() ?: "{}").encodeToByteArray()
        }
        val envelope = proto.decodeFromByteArray(Envelope.serializer(), data)
        val body = if (envelope.compressed) gunzip(envelope.payload) ?: envelope.payload else envelope.payload
        return envelope.type to body
    }

    /**
     * Decodes a payload once its type is known.
     *
     * An unknown type returns null instead of throwing — Metrolist may add message types before we
     * do, and a client that dies on an unrecognised frame cannot share a room with a newer one.
     * `chat` is deliberately in this list even though Aura draws no chat UI: a Metrolist or
     * SimpMusic user in the room MAY send it, and dropping it silently is what upstream does.
     */
    fun decodePayload(
        msgType: String,
        payloadBytes: ByteArray,
    ): Any? =
        when (msgType) {
            MessageTypes.CREATE_ROOM -> decode(CreateRoomPayload.serializer(), payloadBytes)
            MessageTypes.JOIN_ROOM -> decode(JoinRoomPayload.serializer(), payloadBytes)
            MessageTypes.APPROVE_JOIN -> decode(ApproveJoinPayload.serializer(), payloadBytes)
            MessageTypes.REJECT_JOIN -> decode(RejectJoinPayload.serializer(), payloadBytes)
            MessageTypes.PLAYBACK_ACTION, MessageTypes.SYNC_PLAYBACK ->
                decode(PlaybackActionPayload.serializer(), payloadBytes)
            MessageTypes.BUFFER_READY -> decode(BufferReadyPayload.serializer(), payloadBytes)
            MessageTypes.KICK_USER -> decode(KickUserPayload.serializer(), payloadBytes)
            MessageTypes.TRANSFER_HOST -> decode(TransferHostPayload.serializer(), payloadBytes)
            MessageTypes.PING -> decode(PingPayload.serializer(), payloadBytes)
            MessageTypes.PONG -> decode(PongPayload.serializer(), payloadBytes)
            MessageTypes.RECONNECT -> decode(ReconnectPayload.serializer(), payloadBytes)
            MessageTypes.SUGGEST_TRACK -> decode(SuggestTrackPayload.serializer(), payloadBytes)
            MessageTypes.APPROVE_SUGGESTION -> decode(ApproveSuggestionPayload.serializer(), payloadBytes)
            MessageTypes.REJECT_SUGGESTION -> decode(RejectSuggestionPayload.serializer(), payloadBytes)

            MessageTypes.ROOM_CREATED -> decode(RoomCreatedPayload.serializer(), payloadBytes)
            MessageTypes.JOIN_REQUEST -> decode(JoinRequestPayload.serializer(), payloadBytes)
            MessageTypes.JOIN_APPROVED -> decode(JoinApprovedPayload.serializer(), payloadBytes)
            MessageTypes.JOIN_REJECTED -> decode(JoinRejectedPayload.serializer(), payloadBytes)
            MessageTypes.USER_JOINED -> decode(UserJoinedPayload.serializer(), payloadBytes)
            MessageTypes.USER_LEFT -> decode(UserLeftPayload.serializer(), payloadBytes)
            MessageTypes.BUFFER_WAIT -> decode(BufferWaitPayload.serializer(), payloadBytes)
            MessageTypes.BUFFER_COMPLETE -> decode(BufferCompletePayload.serializer(), payloadBytes)
            MessageTypes.ERROR -> decode(ErrorPayload.serializer(), payloadBytes)
            MessageTypes.HOST_CHANGED -> decode(HostChangedPayload.serializer(), payloadBytes)
            MessageTypes.KICKED -> decode(KickedPayload.serializer(), payloadBytes)
            MessageTypes.SYNC_STATE -> decode(SyncStatePayload.serializer(), payloadBytes)
            MessageTypes.RECONNECTED -> decode(ReconnectedPayload.serializer(), payloadBytes)
            MessageTypes.USER_RECONNECTED -> decode(UserReconnectedPayload.serializer(), payloadBytes)
            MessageTypes.USER_DISCONNECTED -> decode(UserDisconnectedPayload.serializer(), payloadBytes)
            MessageTypes.SUGGESTION_RECEIVED -> decode(SuggestionReceivedPayload.serializer(), payloadBytes)
            MessageTypes.SUGGESTION_APPROVED -> decode(SuggestionApprovedPayload.serializer(), payloadBytes)
            MessageTypes.SUGGESTION_REJECTED -> decode(SuggestionRejectedPayload.serializer(), payloadBytes)

            MessageTypes.SERVER_CAPABILITIES -> decode(ServerCapabilities.serializer(), payloadBytes)

            else -> null
        }

    private fun encodePayload(payload: Any): ByteArray =
        when (payload) {
            is CreateRoomPayload -> enc(CreateRoomPayload.serializer(), payload)
            is JoinRoomPayload -> enc(JoinRoomPayload.serializer(), payload)
            is LeaveRoomPayload -> ByteArray(0)
            is ApproveJoinPayload -> enc(ApproveJoinPayload.serializer(), payload)
            is RejectJoinPayload -> enc(RejectJoinPayload.serializer(), payload)
            is PlaybackActionPayload -> enc(PlaybackActionPayload.serializer(), payload)
            is PingPayload -> enc(PingPayload.serializer(), payload)
            is BufferReadyPayload -> enc(BufferReadyPayload.serializer(), payload)
            is KickUserPayload -> enc(KickUserPayload.serializer(), payload)
            is TransferHostPayload -> enc(TransferHostPayload.serializer(), payload)
            is SuggestTrackPayload -> enc(SuggestTrackPayload.serializer(), payload)
            is ApproveSuggestionPayload -> enc(ApproveSuggestionPayload.serializer(), payload)
            is RejectSuggestionPayload -> enc(RejectSuggestionPayload.serializer(), payload)
            is ReconnectPayload -> enc(ReconnectPayload.serializer(), payload)
            is ClientCapabilities -> enc(ClientCapabilities.serializer(), payload)
            else -> ByteArray(0)
        }

    private fun <T> decode(
        serializer: kotlinx.serialization.DeserializationStrategy<T>,
        bytes: ByteArray,
    ): T? =
        runCatching {
            if (json) wireJson.decodeFromString(serializer, bytes.decodeToString()) else proto.decodeFromByteArray(serializer, bytes)
        }.getOrNull()

    private fun gzip(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray? =
        runCatching {
            GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        }.getOrNull()

    companion object {
        /** Gzip below this size costs more bytes than it saves. */
        const val COMPRESSION_THRESHOLD = 100
    }
}
