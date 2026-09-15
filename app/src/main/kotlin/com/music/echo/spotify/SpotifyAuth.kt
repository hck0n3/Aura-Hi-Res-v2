/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.spotify

import iad1tya.echo.music.spotify.models.SpotifyInternalToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor

/**
 * Handles Spotify authentication using the web player's internal token endpoint.
 * Uses sp_dc cookies (extracted from WebView login) to obtain access tokens
 * without requiring a Spotify Developer Client ID.
 *
 * Token acquisition requires a TOTP (Time-based One-Time Password) generated
 * from a shared secret that Spotify rotates periodically. The secret and its
 * version are fetched from a community-maintained GitHub Gist.
 *
 * Reference: https://github.com/sonic-liberation/spotube-plugin-spotify
 */
object SpotifyAuth {
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val NUANCE_GIST_URL =
        "https://api.github.com/gists/22ed9c6ba463899e933427f7de1f0eef"

    /**
     * SimpMusic's secret source, and the one tried FIRST (owner 2026-09-15: "las letras de Spotify no
     * funcionan bien — clona cómo lo hace SimpMusic").
     *
     * It is `raw.githubusercontent.com`, not the GitHub **API**: the gist above is fetched through
     * api.github.com, which rate-limits UNAUTHENTICATED callers to 60 requests per hour **per IP** —
     * and mobile carriers put thousands of subscribers behind one NAT address, so that budget can be
     * spent by other people entirely. When it is, the secret fetch fails, no token is minted and
     * every Spotify lyric silently reports "no lyrics". raw.githubusercontent.com has no such quota.
     *
     * Format: `{"<version>": [<cipher bytes>], …}` — the derivation is in [secretFromCipherBytes].
     */
    private const val SECRET_DICT_URL =
        "https://raw.githubusercontent.com/xyloflake/spot-secrets-go/refs/heads/main/secrets/secretDict.json"

    /**
     * SimpMusic's built-in `TOTP_SECRET_V22`, the last resort when BOTH remote sources are
     * unreachable.
     *
     * Upstream ships it precisely so a network blip is not the end of the feature; this port had no
     * fallback at all and threw instead, which is the difference between "Spotify lyrics are off for
     * a minute" and "Spotify lyrics do not work". Spotify rotates these on a cadence of months, so a
     * baked-in copy is worth having even though it will eventually go stale — and when it does, the
     * two live sources above are what carry the feature.
     */
    internal val BUILTIN_SECRET_VERSION = 22
    internal val BUILTIN_SECRET_CIPHER =
        listOf(99, 101, 119, 123, 69, 120, 91, 123, 97, 74, 53, 48, 76, 102, 55, 69, 110, 54)
    /** Chrome/Windows UA: used by token requests AND by the login WebView (desktop UA fixes the
     *  login white-screen — accounts.spotify.com serves a broken SPA to system WebView UAs). */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    /** Gist retries + in-memory secret cache (see [fetchNuance]). */
    private const val NUANCE_MAX_RETRIES = 3
    private const val NUANCE_CACHE_MS = 48L * 60 * 60 * 1000 // 48h; the secret itself rotates on a Spotify-side schedule of months

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    @Serializable
    private data class Nuance(val s: String, val v: Int)

    @Serializable
    private data class GistFile(val content: String)

    @Serializable
    private data class GistFiles(val files: Map<String, GistFile>)

    @Serializable
    private data class ServerTimeResponse(val serverTime: Long)

    /**
     * Fetches an internal web-player access token using session cookies and TOTP.
     *
     * 1. Fetches the TOTP secret from the community Gist
     * 2. Gets the server time from Spotify
     * 3. Generates a 6-digit TOTP (SHA1, 30s interval)
     * 4. Calls /api/token with the TOTP and sp_dc cookie
     */
    suspend fun fetchAccessToken(
        spDc: String,
        spKey: String = "",
    ): Result<SpotifyInternalToken> = runCatching {
        val cookieHeader = buildString {
            append("sp_dc=$spDc")
            if (spKey.isNotEmpty()) {
                append("; sp_key=$spKey")
            }
        }
        // Logged-in token: reject an anonymous result (means the sp_dc cookie is invalid/expired).
        requestToken(cookieHeader = cookieHeader, allowAnonymous = false)
    }

    /**
     * Fetches an ANONYMOUS web-player access token (no sp_dc login). This is what the Spotify web
     * player uses when browsing while logged out, and it is sufficient to read PUBLIC playlists
     * (metadata + tracks) — enabling "import by link" for public playlists without a Spotify login.
     * It cannot read the user's own library (liked songs, followed artists, private playlists).
     */
    suspend fun fetchAnonymousAccessToken(): Result<SpotifyInternalToken> = runCatching {
        requestToken(cookieHeader = null, allowAnonymous = true)
    }

    private suspend fun requestToken(
        cookieHeader: String?,
        allowAnonymous: Boolean,
    ): SpotifyInternalToken {
        val nuance = fetchNuance()
        val serverTimeSec = fetchServerTime()
        val totp = generateTotp(nuance.s, serverTimeSec)

        val headers = if (cookieHeader != null) mapOf("Cookie" to cookieHeader) else emptyMap()

        fun tokenUrl(reason: String) = buildString {
            append(TOKEN_URL)
            append("?reason=$reason")
            append("&productType=web-player")
            append("&totp=$totp")
            append("&totpServer=$totp")
            append("&totpVer=${nuance.v}")
        }

        // `transport` first, then `init` — SimpMusic's own two-step, and it is not cosmetic: the mint
        // answers a `transport` request with a SHORT, unusable token often enough that upstream
        // checks the length (a real web-player token is 374 characters) and re-asks as `init`. This
        // port only ever tried `transport`, so those attempts ended as "Spotify session required"
        // and the lyrics provider reported nothing.
        val transportToken = parseToken(withContext(Dispatchers.IO) { httpGet(tokenUrl("transport"), headers) })
        // The length check only applies to a LOGGED-IN mint: an anonymous token is a different shape,
        // and re-asking for one would cost a request per call for nothing.
        val transportUsable = transportToken != null &&
            (cookieHeader == null || transportToken.accessToken.length == VALID_TOKEN_LENGTH)
        val token =
            if (transportUsable) {
                transportToken
            } else {
                parseToken(withContext(Dispatchers.IO) { httpGet(tokenUrl("init"), headers) })
                    ?: transportToken
            }
        if (token == null) {
            // A malformed body from the token endpoint can only mean the TOTP/secret pair was
            // rejected — the cached secret may be stale (audit FASE 3 #1).
            invalidateNuanceCache()
            throw Spotify.SpotifyException(500, "Token endpoint returned an unparseable body")
        }

        if (token.accessToken.isBlank() || (!allowAnonymous && token.isAnonymous)) {
            // The mint REJECTED our TOTP: either the sp_dc cookie is dead (the usual cause) or
            // Spotify rotated the secret while our 48h cache was fresh (rare — the gist history
            // shows zero rotations in 6.5 months). Drop the cache so the NEXT attempt re-fetches
            // a fresh secret instead of replaying a dead one until the TTL expires: that restores
            // the pre-cache self-healing (audit FASE 3 #1) at the cost of one extra gist fetch.
            invalidateNuanceCache()
            throw Spotify.SpotifyException(
                401,
                "Received anonymous token — sp_dc cookie is invalid or expired",
            )
        }

        return token
    }

    /** A web-player access token is 374 characters; anything else is the mint fobbing us off. */
    private const val VALID_TOKEN_LENGTH = 374

    private fun parseToken(body: String): SpotifyInternalToken? =
        try {
            json.decodeFromString<SpotifyInternalToken>(body)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }

    /**
     * Fetches the TOTP secret ("nuance") from the community Gist, with resilience (owner directive
     * 2026-09-03: "login con Spotify se cierra o no funciona").
     *
     * The Gist is the single point of failure for EVERY Spotify login: it is a third-party GitHub
     * gist that can rate-limit, lag or disappear transiently — and a login attempt used to die on
     * the FIRST blip (one attempt, no fallback). Now:
     *  - retries up to [NUANCE_MAX_RETRIES] with short backoff (GitHub rate-limits are often a
     *    30-second blip, not an outage);
     *  - on success the parsed secret is cached in memory for [NUANCE_CACHE_MS]: the secret rotates
     *    on a Spotify-side schedule of months, so a cached copy stays valid far longer than any
     *    login session, and a later Gist failure falls back to the cached copy instead of killing
     *    the login. The cache is process-memory only (never persisted — no extra secret at rest).
     */
    private suspend fun fetchNuance(): Nuance {
        // Fresh cache → use it without touching the network (the TTL bounds staleness; the secret's
        // own rotation cadence is months, so 48h is far inside its validity window).
        // SINGLE @Volatile Pair (audit FASE 3 #3): two separate volatiles allowed a torn write
        // (old secret + fresh timestamp) under a mid-rotation race; one atomic reference makes the
        // cache update all-or-nothing.
        cachedNuance?.let { (nuance, atMs) ->
            if (System.currentTimeMillis() - atMs < NUANCE_CACHE_MS) return nuance
        }
        // Bound the whole retry loop (audit FASE 3 #2): 3 attempts × (15s connect + 15s read)
        // + backoffs could hang the login spinner for ~50-95s on a dead gist; 30s caps the wait
        // while still covering the normal 1-2s fetch with generous margin.
        val fresh = withTimeoutOrNull(30_000L) {
            var lastError: Exception? = null
            repeat(NUANCE_MAX_RETRIES) { attempt ->
                try {
                    val nuance = fetchNuanceOnce()
                    cachedNuance = nuance to System.currentTimeMillis()
                    return@withTimeoutOrNull nuance
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastError = e
                    if (attempt < NUANCE_MAX_RETRIES - 1) delay(1_500L * (attempt + 1))
                }
            }
            null
        }
        if (fresh != null) return fresh
        // All attempts failed (or timed out) — the login can still work if a PREVIOUS attempt in
        // this process cached a usable secret (the Gist failing now says nothing about the
        // secret's validity: it was minted when the gist was last updated).
        cachedNuance?.let { (nuance, _) -> return nuance }
        // Neither remote source answered and nothing is cached. SimpMusic keeps a built-in secret for
        // exactly this and so do we now: a network blip must not be the difference between "Spotify
        // lyrics are off for a minute" and "Spotify lyrics do not work". It is NOT cached — the next
        // attempt goes back to the live sources rather than pinning a copy that will go stale.
        return Nuance(s = secretFromCipherBytes(BUILTIN_SECRET_CIPHER), v = BUILTIN_SECRET_VERSION)
    }

    /** One atomic cache slot: (secret, cachedAtMs) — see [fetchNuance] for why it is a single field. */
    @Volatile private var cachedNuance: Pair<Nuance, Long>? = null

    /** Drops the cached secret — called when the token mint REJECTS our TOTP (see [requestToken]). */
    private fun invalidateNuanceCache() {
        cachedNuance = null
    }

    private suspend fun fetchNuanceOnce(): Nuance = withContext(Dispatchers.IO) {
        // SimpMusic's source first (raw.githubusercontent, no API quota — see [SECRET_DICT_URL]),
        // then the gist this app has always used. Two independent sources, so one being down,
        // rate-limited or renamed is no longer the end of every Spotify token.
        runCatching { fetchFromSecretDict() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?.let { return@withContext it }
        fetchFromGist()
    }

    /** SimpMusic's `secretDict.json`: `{"<version>": [<cipher bytes>], …}`. */
    private fun fetchFromSecretDict(): Nuance {
        val body = httpGet(SECRET_DICT_URL, emptyMap())
        val dict = json.decodeFromString<Map<String, List<Int>>>(body)
        val newest = dict.entries
            .mapNotNull { entry -> entry.key.toIntOrNull()?.let { it to entry.value } }
            // Upstream takes the LAST entry; the highest version is the same choice without
            // depending on the file's key order surviving a JSON round trip.
            .maxByOrNull { it.first }
            ?: throw Spotify.SpotifyException(500, "secretDict has no usable versions")
        return Nuance(s = secretFromCipherBytes(newest.second), v = newest.first)
    }

    private fun fetchFromGist(): Nuance {
        val body = try {
            httpGet(NUANCE_GIST_URL, emptyMap())
        } catch (e: Exception) {
            throw Spotify.SpotifyException(
                503,
                "Failed to fetch TOTP secret from gist: ${e.message}",
            )
        }
        val gist = json.decodeFromString<GistFiles>(body)
        val nuancesJson = gist.files.values.firstOrNull()?.content
            ?: throw Spotify.SpotifyException(500, "Gist has no files")
        val nuances = json.decodeFromString<List<Nuance>>(nuancesJson)
        return nuances.maxByOrNull { it.v }
            ?: throw Spotify.SpotifyException(500, "No nuance data found in gist")
    }

    /**
     * SimpMusic's `SpotifyTotp.generateSecret`, verbatim in effect: XOR each cipher byte with
     * `(index % 33) + 9`, concatenate the results as DECIMAL TEXT, and base32 the ASCII bytes of
     * that text.
     *
     * Upstream runs the bytes through hex and then base64 before base32; both are exact round trips
     * (`hexToByteArray(toHexString(x)) == x`, and `base64ToBase32` decodes the base64 it was just
     * handed), so they are omitted here rather than reimplemented. The resulting secret is identical
     * — which is what matters, because a secret that differs by one byte mints no token at all.
     */
    internal fun secretFromCipherBytes(cipher: List<Int>): String {
        val transformed = cipher.mapIndexed { index, byte -> byte xor ((index % 33) + 9) }
        val joined = transformed.joinToString("")
        return base32Encode(joined.toByteArray(Charsets.US_ASCII)).trimEnd('=')
    }

    private fun base32Encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var bits = 0
        var value = 0
        for (byte in data) {
            value = (value shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                result.append(alphabet[(value shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) result.append(alphabet[(value shl (5 - bits)) and 0x1F])
        while (result.length % 8 != 0) result.append('=')
        return result.toString()
    }

    private suspend fun fetchServerTime(): Long = withContext(Dispatchers.IO) {
        val body = try {
            httpGet(SERVER_TIME_URL, emptyMap())
        } catch (e: Exception) {
            throw Spotify.SpotifyException(
                503,
                "Failed to fetch Spotify server time: ${e.message}",
            )
        }
        val response = json.decodeFromString<ServerTimeResponse>(body)
        response.serverTime
    }

    /**
     * Generates a 6-digit TOTP using HMAC-SHA1 (RFC 6238).
     * @param secret Base32-encoded shared secret
     * @param serverTimeSec Spotify server time in seconds since epoch
     */
    private fun generateTotp(secret: String, serverTimeSec: Long): String {
        val key = base32Decode(secret)
        val interval = 30L
        val timeStep = floor(serverTimeSec.toDouble() / interval).toLong()

        val timeBytes = ByteArray(8)
        var value = timeStep
        for (i in 7 downTo 0) {
            timeBytes[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(timeBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val code = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val otp = code % 1_000_000
        return otp.toString().padStart(6, '0')
    }

    private fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.uppercase().replace("=", "")

        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in cleaned) {
            val value = alphabet.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }

        return output.toByteArray()
    }

    private fun httpGet(urlString: String, extraHeaders: Map<String, String>): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Accept-Language", "en")
            for ((key, value) in extraHeaders) {
                connection.setRequestProperty(key, value)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Spotify.SpotifyException(
                    responseCode,
                    "HTTP $responseCode: $errorBody",
                )
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
