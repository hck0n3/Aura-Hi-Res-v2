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

        val tokenUrl = buildString {
            append(TOKEN_URL)
            append("?reason=transport")
            append("&productType=web-player")
            append("&totp=$totp")
            append("&totpServer=$totp")
            append("&totpVer=${nuance.v}")
        }

        val headers = if (cookieHeader != null) mapOf("Cookie" to cookieHeader) else emptyMap()

        val body = withContext(Dispatchers.IO) {
            httpGet(tokenUrl, headers)
        }

        val token = try {
            json.decodeFromString<SpotifyInternalToken>(body)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // A malformed body from the token endpoint can only mean the TOTP/secret pair was
            // rejected — the cached secret may be stale (audit FASE 3 #1).
            invalidateNuanceCache()
            throw e
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
        throw Spotify.SpotifyException(
            503,
            "Failed to fetch TOTP secret from gist after $NUANCE_MAX_RETRIES attempts (capped at 30s)",
        )
    }

    /** One atomic cache slot: (secret, cachedAtMs) — see [fetchNuance] for why it is a single field. */
    @Volatile private var cachedNuance: Pair<Nuance, Long>? = null

    /** Drops the cached secret — called when the token mint REJECTS our TOTP (see [requestToken]). */
    private fun invalidateNuanceCache() {
        cachedNuance = null
    }

    private suspend fun fetchNuanceOnce(): Nuance = withContext(Dispatchers.IO) {
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
        nuances.maxByOrNull { it.v }
            ?: throw Spotify.SpotifyException(500, "No nuance data found in gist")
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
