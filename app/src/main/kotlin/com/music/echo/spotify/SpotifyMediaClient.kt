/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.spotify

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import iad1tya.echo.music.constants.SpotifyAccessTokenExpiresAtKey
import iad1tya.echo.music.constants.SpotifyAccessTokenKey
import iad1tya.echo.music.constants.SpotifySpDcKey
import iad1tya.echo.music.utils.dataStore
import java.util.concurrent.atomic.AtomicReference

/**
 * Spotify lyrics + Canvas client — VERIFIED against SimpMusic v2.0.0 (core/service/spotify).
 *
 * Both features ride the same three-legged auth a logged-in Spotify web player has:
 *  1. a personal access token minted from the user's sp_dc cookie (already handled by [SpotifyAuth],
 *     the same TOTP flow the Spotify import uses — no new auth surface, no new login);
 *  2. an ANONYMOUS client token from clienttoken.spotify.com (no login; identifies the "player");
 *  3. the searchTracks persisted query on api-partner.spotify.com to resolve the Aura/YouTube track
 *     to a Spotify track id.
 *
 * Then lyrics come from spclient.wg.spotify.com/color-lyrics and Canvas from
 * spclient.wg.spotify.com/canvaz-cache (a protobuf POST, parsed with a minimal wire reader so no
 * new serialization backend enters the build).
 *
 * The application context is ATTACHED once from App.onCreate (same binding pattern as
 * QobuzHiRes.attach) because the lyrics provider runs as a static object without DI.
 *
 * Nothing here logs titles, artists or tokens — the user's identifiers never reach a log.
 */
object SpotifyMediaClient {
    private const val USER_AGENT_WEB =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36"
    private const val USER_AGENT_SPOTIFY_IOS = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"
    private const val SEARCH_HASH =
        "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"

    @Volatile
    private var appContext: Context? = null

    /** App.onCreate binds the process-wide context (same pattern as QobuzHiRes.attach). */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /** Client token + its expiry (epoch ms). Anonymous, shared by every feature. */
    private data class ClientToken(val token: String, val expiresAtMs: Long)

    private val clientTokenCache = AtomicReference<ClientToken?>(null)

    /** In-memory personal token cache so back-to-back songs don't re-run the TOTP dance. */
    private val personalTokenCache = AtomicReference<Pair<String, Long>?>(null)

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = true
        }
    }

    /**
     * True when the user has a Spotify session (sp_dc cookie) — lyrics/canvas are logged-in features
     * in SimpMusic too; without sp_dc the personal token is anonymous and both endpoints refuse it.
     */
    suspend fun hasSpotifySession(): Boolean {
        val context = appContext ?: return false
        return context.dataStore.data.first()[SpotifySpDcKey].orEmpty().isNotBlank()
    }

    /** Anonymous client token (SimpMusic getClientToken → clienttoken.spotify.com/v1/clienttoken). */
    suspend fun clientToken(): String? {
        clientTokenCache.get()?.let { cached ->
            if (cached.expiresAtMs > System.currentTimeMillis() + 60_000L) return cached.token
        }
        return runCatching {
            // VERIFIED body from SimpMusic's SpotifyClientBody (client_version 1.2.62.476.g2ad6e7f3,
            // the web player's public client_id).
            val body = """{"client_data":{"client_version":"1.2.62.476.g2ad6e7f3","client_id":"d8a5ed958d274c2e8ee717e6a4b0971d","js_sdk_data":{"device_brand":"Apple","device_model":"unknown","os":"macos","os_version":"10.15.7","device_id":"4fd0c748-b282-4927-9658-6d51a24e58b7","device_type":"computer"}}}"""
            val response = withContext(Dispatchers.IO) {
                client.post("https://clienttoken.spotify.com/v1/clienttoken") {
                    contentType(ContentType.Application.Json)
                    headers {
                        append("Accept", "application/json")
                        append("User-Agent", USER_AGENT_WEB)
                    }
                    setBody(body)
                }
            }
            val parsed = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val granted = parsed["granted_token"]?.jsonObject
            val token = granted?.get("token")?.jsonPrimitive?.content
            val expiresIn = granted?.get("expires_after_seconds")?.jsonPrimitive?.content?.toLongOrNull()
            if (token.isNullOrBlank()) return null
            clientTokenCache.set(
                ClientToken(token, System.currentTimeMillis() + (expiresIn ?: 3600) * 1000L),
            )
            token
        }.getOrNull()
    }

    /**
     * Personal access token for the logged-in user. Reuses the import's persisted token when it is
     * still fresh, then falls back to [SpotifyAuth] (TOTP) — the exact flow SimpMusic's
     * LyricsCanvasRepository runs before every lyrics/canvas call. Null when the user has no
     * sp_dc session or it is dead.
     */
    suspend fun personalToken(): String? {
        personalTokenCache.get()?.let { (token, expiresAt) ->
            if (expiresAt > System.currentTimeMillis() + 60_000L) return token
        }
        val context = appContext ?: return null
        return runCatching {
            val prefs = context.dataStore.data.first()
            val stored = prefs[SpotifyAccessTokenKey].orEmpty()
            val storedExpiry = prefs[SpotifyAccessTokenExpiresAtKey] ?: 0L
            val token: String
            val expiresAt: Long
            if (stored.isNotBlank() && storedExpiry > System.currentTimeMillis() + 60_000L) {
                token = stored
                expiresAt = storedExpiry
            } else {
                val spDc = prefs[SpotifySpDcKey].orEmpty()
                if (spDc.isBlank()) return null
                val refreshed = SpotifyAuth.fetchAccessToken(spDc).getOrNull() ?: return null
                if (refreshed.isAnonymous) return null
                token = refreshed.accessToken
                expiresAt = refreshed.accessTokenExpirationTimestampMs
            }
            personalTokenCache.set(token to expiresAt)
            token
        }.getOrNull()
    }

    /** A resolved Spotify track: its id plus the duration SimpMusic duration-matches against. */
    data class SpotifyTrackMatch(val trackId: String, val durationMs: Long?)

    /**
     * searchTracks persisted query (SimpMusic searchSpotifyTrack): resolves "title artist" to a
     * Spotify track id. Duration-matches the results when [durationSeconds] is known (SimpMusic
     * requires abs(item.duration - duration) < 1 before falling back to the first result), else
     * the first result.
     */
    suspend fun searchTrack(
        query: String,
        durationSeconds: Int?,
        personalToken: String,
        clientToken: String,
    ): SpotifyTrackMatch? = runCatching {
        val variables =
            """{"searchTerm":"${query.replace("\\", "\\\\").replace("\"", "\\\"")}","offset":0,"limit":3,"numberOfTopResults":3,"includeAudiobooks":true,"includePreReleases":false}"""
        val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$SEARCH_HASH"}}"""
        val response = withContext(Dispatchers.IO) {
            client.get("https://api-partner.spotify.com/pathfinder/v1/query") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $personalToken")
                    append("Client-Token", clientToken)
                    append("User-Agent", USER_AGENT_WEB)
                }
                parameter("operationName", "searchTracks")
                parameter("variables", variables)
                parameter("extensions", extensions)
            }
        }
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val items = root["data"]?.jsonObject
            ?.get("searchV2")?.jsonObject
            ?.get("tracksV2")?.jsonObject
            ?.get("items")?.jsonArray
            ?: return null
        val parsed = items.mapNotNull { itemWrapper ->
            val data = itemWrapper.jsonObject["item"]?.jsonObject?.get("data")?.jsonObject
                ?: return@mapNotNull null
            val id = data["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val durationMs = data["duration"]?.jsonObject
                ?.get("totalMilliseconds")?.jsonPrimitive?.content?.toLongOrNull()
            SpotifyTrackMatch(id, durationMs)
        }
        when {
            parsed.isEmpty() -> null
            durationSeconds != null && durationSeconds > 0 ->
                parsed.firstOrNull { match ->
                    match.durationMs != null &&
                        kotlin.math.abs(match.durationMs / 1000.0 - durationSeconds) < 1.0
                } ?: parsed.first()
            else -> parsed.first()
        }
    }.getOrNull()

    /** One synced-lyrics line from spclient color-lyrics (SimpMusic SpotifyLyricsResponse.Line). */
    data class SpotifyLyricsLine(val startTimeMs: Long, val endTimeMs: Long, val words: String)

    /**
     * Fetches the Spotify color-lyrics for [trackId] (SimpMusic getSpotifyLyrics:
     * spclient.wg.spotify.com/color-lyrics/v2/track/{id}?format=json&vocalRemoval=false&market=from_token).
     * Returns null when the track has no lyrics on Spotify.
     */
    suspend fun getLyrics(
        trackId: String,
        personalToken: String,
        clientToken: String,
    ): List<SpotifyLyricsLine>? = runCatching {
        val response = withContext(Dispatchers.IO) {
            client.get("https://spclient.wg.spotify.com/color-lyrics/v2/track/$trackId") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $personalToken")
                    append("Client-Token", clientToken)
                    append("App-platform", "WebPlayer")
                    append("User-Agent", USER_AGENT_WEB)
                }
                parameter("format", "json")
                parameter("vocalRemoval", "false")
                parameter("market", "from_token")
            }
        }
        // Owner report 2026-09-14 ("letras de Spotify se activan pero no funcionan"): a refused request
        // used to look exactly like "this song has no lyrics". Log the status (no user data) so the
        // feedback log tells the two apart.
        if (response.status.value !in 200..299) {
            timber.log.Timber.tag("Spotify").w("color-lyrics HTTP %d", response.status.value)
            return null
        }
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val lyrics = root["lyrics"]?.jsonObject ?: return null
        val lines = lyrics["lines"]?.jsonArray ?: return null
        lines.mapNotNull { line ->
            val obj = line.jsonObject
            val start = obj["startTimeMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val end = obj["endTimeMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val words = obj["words"]?.jsonPrimitive?.content ?: return@mapNotNull null
            SpotifyLyricsLine(start, end, words)
        }.sortedBy { it.startTimeMs }.ifEmpty { null }
    }.getOrNull()

    /** A Spotify Canvas: the looping video plus its thumbnail (SimpMusic CanvasResult). */
    data class SpotifyCanvas(val canvasUrl: String, val thumbUrl: String?)

    /**
     * Fetches the Canvas for [trackId] (SimpMusic getSpotifyCanvas:
     * spclient.wg.spotify.com/canvaz-cache/v0/canvases — an application/protobuf POST). The
     * response is parsed with a minimal protobuf wire reader so no new serialization backend
     * enters the build. Only .mp4 canvases are treated as playable videos (SimpMusic's isVideo
     * = canvasUrl.contains(".mp4")).
     */
    suspend fun getCanvas(
        trackId: String,
        personalToken: String,
        clientToken: String,
    ): SpotifyCanvas? = runCatching {
        val body = encodeCanvasBody("spotify:track:$trackId")
        val bytes: ByteArray = withContext(Dispatchers.IO) {
            val response = client.post("https://spclient.wg.spotify.com/canvaz-cache/v0/canvases") {
                contentType(ContentType.parse("application/protobuf"))
                headers {
                    append("Accept", "application/protobuf")
                    append("Authorization", "Bearer $personalToken")
                    append("Client-Token", clientToken)
                    append("User-Agent", USER_AGENT_SPOTIFY_IOS)
                }
                setBody(body)
            }
            response.body()
        }
        decodeCanvasResponse(bytes)
    }.getOrNull()

    // ── Minimal protobuf codec for the Canvas endpoints ───────────────────
    // message CanvasRequest { repeated Track tracks = 1; message Track { string track_uri = 1; } }
    // message CanvasResponse { repeated Canvas canvases = 1; message Canvas { string id = 1;
    //   string canvas_url = 2; string track_uri = 5; Artist artist = 6; string other_id = 9;
    //   string canvas_uri = 11; repeated Thumb thumbs = 13; } }

    private fun encodeCanvasBody(trackUri: String): ByteArray {
        fun varint(value: Int): ByteArray {
            var v = value
            val out = ArrayList<Byte>(5)
            do {
                var b = (v and 0x7F).toByte()
                v = v ushr 7
                if (v != 0) b = (b.toInt() or 0x80).toByte()
                out.add(b)
            } while (v != 0)
            return out.toByteArray()
        }
        val uriBytes = trackUri.toByteArray(Charsets.UTF_8)
        // Track submessage: field 1 (track_uri), wire 2 → 0x0A, len, bytes
        val trackMsg = byteArrayOf(0x0A.toByte()) + varint(uriBytes.size) + uriBytes
        // Top: field 1 (tracks), wire 2 → 0x0A, len, trackMsg
        return byteArrayOf(0x0A.toByte()) + varint(trackMsg.size) + trackMsg
    }

    private fun decodeCanvasResponse(bytes: ByteArray): SpotifyCanvas? {
        val reader = ProtoReader(bytes)
        var canvasUrl: String? = null
        var thumbUrl: String? = null
        while (reader.hasMore()) {
            val (field, wireType) = reader.readTag() ?: break
            if (field == 1 && wireType == 2) {
                val canvasReader = ProtoReader(reader.readBytes() ?: continue)
                var url: String? = null
                val thumbs = ArrayList<Pair<Int, String>>() // (area, url)
                while (canvasReader.hasMore()) {
                    val (cf, cw) = canvasReader.readTag() ?: break
                    when {
                        cf == 2 && cw == 2 -> url = String(canvasReader.readBytes() ?: ByteArray(0), Charsets.UTF_8)
                        cf == 13 && cw == 2 -> {
                            val thumbReader = ProtoReader(canvasReader.readBytes() ?: continue)
                            var h = 0
                            var w = 0
                            var u: String? = null
                            while (thumbReader.hasMore()) {
                                val (tf, tw) = thumbReader.readTag() ?: break
                                when {
                                    tf == 1 && tw == 0 -> h = thumbReader.readVarint()?.toInt() ?: 0
                                    tf == 2 && tw == 0 -> w = thumbReader.readVarint()?.toInt() ?: 0
                                    tf == 3 && tw == 2 -> u = String(thumbReader.readBytes() ?: ByteArray(0), Charsets.UTF_8)
                                    else -> thumbReader.skip(tw)
                                }
                            }
                            if (u != null) thumbs.add((h + w) to u)
                        }
                        else -> canvasReader.skip(cw)
                    }
                }
                if (url != null && canvasUrl == null) {
                    canvasUrl = url
                    // Largest thumb wins (SimpMusic: maxByOrNull { height + width }).
                    thumbUrl = thumbs.maxByOrNull { it.first }?.second ?: thumbs.firstOrNull()?.second
                }
            } else {
                reader.skip(wireType)
            }
        }
        return canvasUrl?.takeIf { it.isNotBlank() }?.let { SpotifyCanvas(it, thumbUrl) }
    }

    /** Tiny protobuf wire reader: varints, tags, length-delimited fields, skipping. */
    private class ProtoReader(private val data: ByteArray) {
        private var pos = 0
        fun hasMore() = pos < data.size
        fun readVarint(): Long? {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (pos >= data.size) return null
                val b = data[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            return null
        }

        /** Returns (fieldNumber, wireType) or null at end. */
        fun readTag(): Pair<Int, Int>? {
            val tag = readVarint() ?: return null
            return ((tag ushr 3).toInt()) to ((tag and 0x7).toInt())
        }

        fun readBytes(): ByteArray? {
            val len = readVarint()?.toInt() ?: return null
            if (len < 0 || pos + len > data.size) return null
            val out = data.copyOfRange(pos, pos + len)
            pos += len
            return out
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> pos += 8
                2 -> readBytes()
                5 -> pos += 4
            }
        }
    }
}
