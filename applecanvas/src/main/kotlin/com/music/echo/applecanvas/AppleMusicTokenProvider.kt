package iad1tya.echo.music.applecanvas

import iad1tya.echo.music.canvas.AppleCanvasDiagnostics
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Apple Music web player's public developer token, the key every animated-cover request needs.
 *
 * Row 508 (owner 2026-10-29: *«las portadas animadas de los artistas y de los álbumes ya no salen»*). What this file did:
 *  · cached the extracted token FOREVER (a long-lived process would hold a dead one), with no way to drop it when Apple
 *    answered 401/403 — the next lookups all failed the same way until the app was restarted;
 *  · found the token only in `/assets/index-<hash>.js` and took the FIRST thing shaped like a JWT in it — Apple moves the
 *    key between bundles and the file can hold other JWT-looking strings;
 *  · fell back, on any failure, to a token that EXPIRED on 2026-06-17, so a single failed extraction meant no cover for
 *    10 minutes, silently.
 * Now: the token is kept until its own `exp`; every script of the page is tried (the `index` ones first, at most
 * [MAX_SCRIPTS]); only an ES256 web-player token that has not expired is accepted; [invalidate] (rate-limited) drops a
 * token Apple rejected; and every outcome is reported through [AppleCanvasDiagnostics].
 */
internal object AppleMusicTokenProvider {
    private var cachedToken: String? = null
    private var cachedExpiryMs = 0L
    private val mutex = Mutex()

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
    }

    suspend fun getToken(): String {
        return mutex.withLock {
            val now = System.currentTimeMillis()
            cachedToken?.let { token ->
                if (now < cachedExpiryMs) return@withLock token
            }
            cachedToken = null
            cachedExpiryMs = 0L
            // A failed extraction costs the page and several multi-MB bundles: after one, leave Apple alone for a while.
            if (lastFailureAtMs != 0L && now - lastFailureAtMs < RETRY_AFTER_MS) return@withLock FALLBACK_TOKEN
            try {
                val page = httpClient.get(PLAYER_URL).bodyAsText()
                val scripts = SCRIPT_SRC.findAll(page).map { it.groupValues[1] }.distinct().toList()
                    .sortedBy { if (it.contains("index")) 0 else 1 }
                    .take(MAX_SCRIPTS)
                if (scripts.isEmpty()) throw IllegalStateException("no scripts in the web player page")
                var found: String? = null
                for (uri in scripts) {
                    val body = httpClient.get("$PLAYER_URL$uri").bodyAsText()
                    found = TOKEN_REGEX.findAll(body).map { it.value }.firstOrNull { isUsable(it, now) }
                    if (found != null) break
                }
                val token = found ?: throw IllegalStateException("no usable token in ${scripts.size} scripts")
                cachedToken = token
                cachedExpiryMs = expiryOf(token, now)
                lastFailureAtMs = 0L
                AppleCanvasDiagnostics.log("APPLE_TOKEN live scripts=${scripts.size}")
                token
            } catch (e: Exception) {
                e.printStackTrace()
                // A cancelled lookup (track skipped, screen off) says nothing about Apple's page: no back-off for it.
                if (e !is kotlinx.coroutines.CancellationException) {
                    lastFailureAtMs = System.currentTimeMillis()
                    AppleCanvasDiagnostics.log("APPLE_TOKEN failed ${e.javaClass.simpleName} (fallback token served; expired=${isExpired(FALLBACK_TOKEN, now)})")
                }
                FALLBACK_TOKEN
            }
        }
    }

    /**
     * Apple answered 401/403 to a request made with the cached token: drop it so the next lookup extracts again. At most
     * once every [INVALIDATE_EVERY_MS] — a token that is rejected even when fresh must not turn every lookup into a download
     * of the whole web player.
     */
    suspend fun invalidate() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastInvalidateAtMs < INVALIDATE_EVERY_MS) return@withLock
            lastInvalidateAtMs = now
            cachedToken = null
            cachedExpiryMs = 0L
            lastFailureAtMs = 0L
            AppleCanvasDiagnostics.log("APPLE_TOKEN dropped (rejected by Apple)")
        }
    }

    /** An ES256 web-player token, readable, that has not expired. */
    private fun isUsable(jwt: String, now: Long): Boolean {
        val payload = payloadOf(jwt) ?: return false
        if (!payload.contains("\"iss\"")) return false
        return !isExpired(jwt, now)
    }

    private fun isExpired(jwt: String, now: Long): Boolean {
        val exp = expSeconds(jwt) ?: return false
        return exp * 1000L <= now
    }

    private fun expiryOf(jwt: String, now: Long): Long {
        val exp = expSeconds(jwt) ?: return now + DEFAULT_TTL_MS
        return (exp * 1000L - EXPIRY_SKEW_MS).coerceAtLeast(now + MIN_TTL_MS)
    }

    private fun payloadOf(jwt: String): String? = runCatching {
        val parts = jwt.split(".")
        if (parts.size < 3) null else String(java.util.Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
    }.getOrNull()

    private fun expSeconds(jwt: String): Long? =
        payloadOf(jwt)?.let { EXP_CLAIM.find(it)?.groupValues?.get(1)?.toLongOrNull() }

    private const val PLAYER_URL = "https://beta.music.apple.com"
    private val SCRIPT_SRC = Regex("""src="(/assets/[^"]+\.js)"""")
    // «eyJhbGciOiJFUzI1NiI» = {"alg":"ES256…: the web player's own tokens, not any JWT-shaped string in a bundle.
    private val TOKEN_REGEX = Regex("""eyJhbGciOiJFUzI1NiI[A-Za-z0-9\-_=]*\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
    private val EXP_CLAIM = Regex(""""exp"\s*:\s*(\d+)""")
    private const val MAX_SCRIPTS = 4
    private const val EXPIRY_SKEW_MS = 5 * 60 * 1000L
    private const val MIN_TTL_MS = 60 * 1000L
    private const val DEFAULT_TTL_MS = 60 * 60 * 1000L
    private const val INVALIDATE_EVERY_MS = 10 * 60 * 1000L

    // Last-resort token (public by design: it is the Apple Music web player's own). It EXPIRED on 2026-06-17, so it only
    // helps if Apple still honours it; the requests that use it fail gracefully (no cover), and say so in the log.
    private const val FALLBACK_TOKEN =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"

    private var lastFailureAtMs = 0L
    private var lastInvalidateAtMs = 0L
    private const val RETRY_AFTER_MS = 10 * 60 * 1000L
}
