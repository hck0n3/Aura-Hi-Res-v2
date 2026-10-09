package iad1tya.echo.music.utils.lastfm

import iad1tya.echo.music.models.lastfm.Authentication
import iad1tya.echo.music.models.lastfm.LastFmError
import iad1tya.echo.music.models.lastfm.LovedTracksResponse
import iad1tya.echo.music.models.lastfm.TokenResponse
import iad1tya.echo.music.models.lastfm.TopArtistsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.MessageDigest

object LastFM {
    var sessionKey: String? = null

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest { url("https://ws.audioscrobbler.com/2.0/") }
            expectSuccess = false
        }
    }

    private fun Map<String, String>.apiSig(secret: String): String {
        val sorted = toSortedMap()
        val toHash = sorted.entries.joinToString("") { it.key + it.value } + secret
        val digest = MessageDigest.getInstance("MD5").digest(toHash.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun HttpRequestBuilder.lastfmParams(
        method: String,
        apiKey: String,
        secret: String,
        sessionKey: String? = null,
        extra: Map<String, String> = emptyMap(),
        format: String = "json"
    ) {
        contentType(ContentType.Application.FormUrlEncoded)
        userAgent("AuraHiRes (https://github.com/hck0n3)")
        val paramsForSig = mutableMapOf(
            "method" to method,
            "api_key" to apiKey
        ).apply {
            sessionKey?.let { put("sk", it) }
            putAll(extra)
        }
        val apiSig = paramsForSig.apiSig(secret)
        setBody(FormDataContent(Parameters.build {
            paramsForSig.forEach { (k, v) -> append(k, v) }
            append("api_sig", apiSig)
            append("format", format)
        }))
    }

    // OAuth methods (kept for backward compatibility)
    suspend fun getToken() = runCatching {
        client.post {
            lastfmParams(
                method = "auth.getToken",
                apiKey = API_KEY,
                secret = SECRET
            )
        }.body<TokenResponse>()
    }

    suspend fun getSession(token: String) = runCatching {
        client.post {
            lastfmParams(
                method = "auth.getSession",
                apiKey = API_KEY,
                secret = SECRET,
                extra = mapOf("token" to token)
            )
        }.body<Authentication>()
    }

    fun getAuthUrl(token: String): String {
        return "https://www.last.fm/api/auth/?api_key=$API_KEY&token=$token"
    }

    // Mobile session authentication
    suspend fun getMobileSession(username: String, password: String) = runCatching {
        val response = client.post {
            lastfmParams(
                method = "auth.getMobileSession",
                apiKey = API_KEY,
                secret = SECRET,
                extra = mapOf("username" to username, "password" to password)
            )
            parameter("format", "json")
        }

        val responseText = response.bodyAsText()
        if (responseText.contains("\"error\"")) {
            val error = json.decodeFromString<LastFmError>(responseText)
            throw LastFmException(error.error, error.message)
        }
        json.decodeFromString<Authentication>(responseText)
    }

    class LastFmException(val code: Int, override val message: String) : Exception(message) {
        override fun toString(): String = "LastFmException(code=$code, message=$message)"
    }

    /**
     * HALLAZGO-020 (FASE 22): invalidate the session server-side (auth.logout), then drop the local
     * key. Best-effort by design: the local key is cleared up front so logout always succeeds from
     * the user's perspective even offline — a stale server session can at most scrobble nothing.
     */
    suspend fun logout() {
        val key = sessionKey
        sessionKey = null
        if (key != null && isInitialized()) {
            runCatching {
                client.post {
                    lastfmParams(
                        method = "auth.logout",
                        apiKey = API_KEY,
                        secret = SECRET,
                        sessionKey = key
                    )
                    parameter("format", "json")
                }
            }
        }
    }

    /**
     * Last.fm reports API failures as HTTP 200 with an `"error"` field in the JSON body (the same shape
     * getMobileSession already string-matches), so a completed POST proves nothing on its own. Validate
     * the payload first, then the status line, and throw so the enclosing runCatching yields a failure.
     */
    private suspend fun HttpResponse.validateLastFmWrite(method: String) {
        val responseText = bodyAsText()
        if (responseText.contains("\"error\"")) {
            val parsed = runCatching { json.decodeFromString<LastFmError>(responseText) }.getOrNull()
            throw if (parsed != null) {
                LastFmException(parsed.error, parsed.message)
            } else {
                LastFmException(-1, "$method failed: $responseText")
            }
        }
        if (!status.isSuccess()) {
            throw LastFmException(status.value, "$method failed with HTTP ${status.value} ${status.description}")
        }
    }

    suspend fun updateNowPlaying(
        artist: String, track: String,
        album: String? = null, trackNumber: Int? = null, duration: Int? = null
    ) = runCatching {
        client.post {
            lastfmParams(
                method = "track.updateNowPlaying",
                apiKey = API_KEY,
                secret = SECRET,
                sessionKey = sessionKey!!,
                extra = buildMap {
                    put("artist", artist)
                    put("track", track)
                    album?.let { put("album", it) }
                    trackNumber?.let { put("trackNumber", it.toString()) }
                    duration?.let { put("duration", it.toString()) }
                }
            )
            parameter("format", "json")
        }.validateLastFmWrite("track.updateNowPlaying")
    }

    suspend fun scrobble(
        artist: String, track: String, timestamp: Long,
        album: String? = null, trackNumber: Int? = null, duration: Int? = null
    ) = runCatching {
        client.post {
            lastfmParams(
                method = "track.scrobble",
                apiKey = API_KEY,
                secret = SECRET,
                sessionKey = sessionKey!!,
                extra = buildMap {
                    put("artist[0]", artist)
                    put("track[0]", track)
                    put("timestamp[0]", timestamp.toString())
                    album?.let { put("album[0]", it) }
                    trackNumber?.let { put("trackNumber[0]", it.toString()) }
                    duration?.let { put("duration[0]", it.toString()) }
                }
            )
            parameter("format", "json")
        }.validateLastFmWrite("track.scrobble")
    }


    suspend fun setLoveStatus(
        artist: String, track: String, love: Boolean
    ) = runCatching {
        val method = if (love) "track.love" else "track.unlove"
        client.post {
            lastfmParams(
                method = method,
                apiKey = API_KEY,
                secret = SECRET,
                sessionKey = sessionKey!!,
                extra = buildMap {
                    put("artist", artist)
                    put("track", track)
                }
            )
            parameter("format", "json")
        }.validateLastFmWrite(method)
    }

    // ── Public UNSIGNED read endpoints (taste import) ───────────────────────────────────────────────────
    // Plain GET + format=json, NO api_sig / sk: these only READ public listening data and must never touch the
    // signed write path (scrobble/love/session). Each returns emptyList() on any failure — never throws.

    /**
     * The user's most-played artists over [period] (public data). Returns (artistName, playcount) pairs.
     * `playcount` arrives as a JSON string, so non-numeric entries are dropped. emptyList() on any failure.
     */
    suspend fun getTopArtists(
        username: String,
        limit: Int = 100,
        period: String = "6month",
    ): List<Pair<String, Int>> {
        if (!isInitialized() || username.isBlank()) return emptyList()
        return runCatching {
            val body = client.get {
                userAgent("AuraHiRes (https://github.com/hck0n3)")
                parameter("method", "user.getTopArtists")
                parameter("user", username)
                parameter("api_key", API_KEY)
                parameter("period", period)
                parameter("limit", limit.toString())
                parameter("format", "json")
            }.bodyAsText()
            json.decodeFromString<TopArtistsResponse>(body).topartists?.artist.orEmpty()
                .mapNotNull { a ->
                    val name = a.name.trim()
                    val count = a.playcount.trim().toIntOrNull()
                    if (name.isBlank() || count == null) null else name to count
                }
        }.getOrDefault(emptyList())
    }

    /**
     * The artists behind the user's loved/❤ tracks (public data). Returns one artist NAME per loved track
     * (duplicates kept so a frequently-loved artist accumulates weight upstream). emptyList() on any failure.
     */
    suspend fun getLovedTracks(
        username: String,
        limit: Int = 200,
    ): List<String> {
        if (!isInitialized() || username.isBlank()) return emptyList()
        return runCatching {
            val body = client.get {
                userAgent("AuraHiRes (https://github.com/hck0n3)")
                parameter("method", "user.getLovedTracks")
                parameter("user", username)
                parameter("api_key", API_KEY)
                parameter("limit", limit.toString())
                parameter("format", "json")
            }.bodyAsText()
            json.decodeFromString<LovedTracksResponse>(body).lovedtracks?.track.orEmpty()
                .mapNotNull { it.artist?.name?.trim()?.takeIf { n -> n.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    /**
     * Fila 356 — an artist's top tags (public data, unsigned GET): what Last.fm listeners call their music
     * ("cumbia", "salsa", "christian", "reggaeton", "rock en español"…), with Last.fm's 0-100 weight.
     * emptyList() = Last.fm answered and has no tags / no such artist (a definitive miss, cache it); null = the
     * request failed (transient — never cache). Never throws.
     */
    suspend fun getArtistTopTags(artist: String): List<Pair<String, Int>>? {
        if (!isInitialized() || artist.isBlank()) return null
        return runCatching {
            val response = client.get {
                userAgent("AuraHiRes (https://github.com/hck0n3)")
                parameter("method", "artist.getTopTags")
                parameter("artist", artist)
                parameter("autocorrect", "1")
                parameter("api_key", API_KEY)
                parameter("format", "json")
            }
            // Last.fm answers its own errors as JSON (status 200 or 4xx); a 5xx is an outage.
            if (response.status.value >= 500) return@runCatching null
            val root = json.parseToJsonElement(response.bodyAsText()) as? kotlinx.serialization.json.JsonObject
                ?: return@runCatching null
            // Error 6 = "The artist you supplied could not be found": definitive. Any other error is transient.
            val error = (root["error"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            if (error != null) return@runCatching if (error == 6) emptyList() else null
            val tags = (root["toptags"] as? kotlinx.serialization.json.JsonObject)?.get("tag")
            val list = when (tags) {
                is kotlinx.serialization.json.JsonArray -> tags
                is kotlinx.serialization.json.JsonObject -> listOf(tags)
                else -> emptyList()
            }
            list.mapNotNull { el ->
                val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val name = (o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()
                val count = (o["count"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.toIntOrNull() ?: 0
                if (name.isNullOrBlank()) null else name to count
            }
        }.getOrNull()
    }

    /**
     * Fila 361 — how many Last.fm listeners an artist has (public data, unsigned GET). 0 = Last.fm does not
     * know the artist (error 6, a definitive answer); null = the request failed (transient). Never throws.
     * Used to tell real artists from anonymous uploads (AI-generated channels have no listeners).
     */
    suspend fun getArtistListeners(artist: String): Long? {
        if (!isInitialized() || artist.isBlank()) return null
        return runCatching {
            val response = client.get {
                userAgent("AuraHiRes (https://github.com/hck0n3)")
                parameter("method", "artist.getInfo")
                parameter("artist", artist)
                parameter("autocorrect", "1")
                parameter("api_key", API_KEY)
                parameter("format", "json")
            }
            if (response.status.value >= 500) return@runCatching null
            val root = json.parseToJsonElement(response.bodyAsText()) as? kotlinx.serialization.json.JsonObject
                ?: return@runCatching null
            val error = (root["error"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            if (error != null) return@runCatching if (error == 6) 0L else null
            val stats = ((root["artist"] as? kotlinx.serialization.json.JsonObject)?.get("stats")
                as? kotlinx.serialization.json.JsonObject) ?: return@runCatching 0L
            (stats["listeners"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.toLongOrNull() ?: 0L
        }.getOrNull()
    }

    // API keys passed from the app module (loaded from BuildConfig / gradle secrets)
    private var API_KEY = ""
    private var SECRET = ""

    /**
     * Initialize LastFM with API credentials
     * @param apiKey LastFM API key
     * @param secret LastFM secret key
     */
    fun initialize(apiKey: String, secret: String) {
        API_KEY = apiKey
        SECRET = secret
    }

    fun isInitialized(): Boolean = API_KEY.isNotEmpty() && SECRET.isNotEmpty()

    const val DEFAULT_SCROBBLE_DELAY_PERCENT = 0.5f
    const val DEFAULT_SCROBBLE_MIN_SONG_DURATION = 30
    const val DEFAULT_SCROBBLE_DELAY_SECONDS = 180
}
