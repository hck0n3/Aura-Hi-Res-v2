package iad1tya.echo.music.utils

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppleMusicTokenProvider {
    // Refetch a little before the real `exp` so we never hand out a token that
    // expires mid-request.
    private const val EXPIRY_SKEW_MS = 5 * 60 * 1000L // 5 minutes
    // TTL used only when a freshly extracted token has no parseable `exp` claim.
    private const val DEFAULT_TTL_MS = 60 * 60 * 1000L // 1 hour

    // The extracted (or fallback) Apple Music developer JWT, plus the epoch-millis
    // instant at which the cached copy must be considered stale and refetched.
    private var cachedToken: String? = null
    private var cachedTokenExpiryMs: Long = 0L
    private val mutex = Mutex()

    // Failure memory (battery/radio audit 2026-10-20). Every failed extraction costs the web player's HTML page AND its
    // multi-MB index.js bundle, and a failure used to be forgotten at once: each album page without a description (and
    // every other caller) re-downloaded both, 11 times in the owner's log, to fail the same way. After a failure the
    // provider now serves the fallback straight away until [AppleTokenRetryPolicy] says it is worth trying again.
    private var consecutiveFailures = 0
    private var nextAttemptAtMs = 0L

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
    }

    // Self-healing is handled by the TTL/`exp`-derived expiry window below (see
    // [computeExpiryMs]): a stale token is dropped and re-extracted on the next
    // [getToken], and the fallback token is deliberately never cached. In addition,
    // [invalidate] lets the API call site drop a token that Apple revoked before its
    // `exp` (a 401/403), so the very next [getToken] re-extracts instead of serving the
    // dead token for the rest of its TTL window.
    suspend fun getToken(): String {
        return mutex.withLock {
            // Reuse the cached token only while it is still within its validity window.
            cachedToken?.let { token ->
                if (System.currentTimeMillis() < cachedTokenExpiryMs) {
                    return@withLock token
                }
            }
            // Expired (or never fetched): clear and (re)extract.
            cachedToken = null
            cachedTokenExpiryMs = 0L

            if (System.currentTimeMillis() < nextAttemptAtMs) return@withLock FALLBACK_TOKEN

            try {
                val token = extract(System.currentTimeMillis())
                cachedToken = token
                cachedTokenExpiryMs = computeExpiryMs(token)
                consecutiveFailures = 0
                nextAttemptAtMs = 0L
                token
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    consecutiveFailures++
                    nextAttemptAtMs = System.currentTimeMillis() + AppleTokenRetryPolicy.retryAfterMs(consecutiveFailures)
                    timber.log.Timber.w(
                        "Apple Music token: fetch failed (animated covers fall back, next try in %d min): %s [%s]",
                        AppleTokenRetryPolicy.retryAfterMs(consecutiveFailures) / 60_000L, privacySafeSummary(e),
                        // Only the step and counters of [extract] (page / scripts / token) — never a URL or a body.
                        (e as? IllegalStateException)?.message.orEmpty(),
                    )
                }
                // Fallback token if live extraction fails. It is deliberately NOT
                // cached, so the very next call re-attempts extraction and the
                // provider self-heals once Apple's web player is parseable again.
                // If the fallback is itself expired the caller just gets a 401 ->
                // "no album description" (handled gracefully upstream), never a
                // crash and never a permanently poisoned cache.
                FALLBACK_TOKEN
            }
        }
    }

    /**
     * Row 508: looks for the token in the page itself (percent-encoded in a `<meta>` tag, as the web player used to carry it)
     * and then in the scripts the page loads, on the beta host and the regular one — the old code read only
     * `/assets/index-<hash>.js` and failed for good when Apple moved the token (owner's log 2026-10-27:
     * «Apple Music token: fetch failed» three times in 40 s). Only an ES256 token that has not expired is taken.
     */
    private suspend fun extract(now: Long): String {
        var pagesRead = 0
        var scriptsListed = 0
        for (host in PLAYER_HOSTS) {
            val page = fetchText(host) ?: continue
            pagesRead++
            findToken(page, now)?.let { return it }
            runCatching { java.net.URLDecoder.decode(page, "UTF-8") }.getOrNull()
                ?.let { decoded -> findToken(decoded, now)?.let { return it } }
            val scripts = SCRIPT_SRC.findAll(page).map { it.groupValues[1] }.map { src ->
                when {
                    src.startsWith("http") -> src
                    src.startsWith("//") -> "https:$src"
                    else -> "$host$src"
                }
            }.distinct().toList().sortedBy { if (it.contains("index")) 0 else 1 }.take(MAX_SCRIPTS)
            scriptsListed += scripts.size
            for (url in scripts) {
                val body = fetchText(url) ?: continue
                findToken(body, now)?.let { return it }
            }
            if (scripts.isNotEmpty()) break
        }
        val step = when {
            pagesRead == 0 -> "page"
            scriptsListed == 0 -> "scripts"
            else -> "token"
        }
        throw IllegalStateException("no usable token (step=$step pages=$pagesRead scripts=$scriptsListed)")
    }

    private suspend fun fetchText(url: String): String? = try {
        httpClient.get(url).bodyAsText()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun findToken(text: String, now: Long): String? =
        TOKEN_REGEX.findAll(text).map { it.value }.firstOrNull { jwt ->
            val payload = runCatching {
                val parts = jwt.split(".")
                if (parts.size < 3) null else String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            }.getOrNull()
            payload != null && payload.contains("\"iss\"") &&
                (parseJwtExpSeconds(jwt)?.let { it * 1000L > now } ?: true)
        }

    private val PLAYER_HOSTS = listOf("https://beta.music.apple.com", "https://music.apple.com")
    private val SCRIPT_SRC = Regex("""src="((?:https?:)?(?://|/)[^"]*\.js)"""")
    private val TOKEN_REGEX = Regex("""eyJhbGciOiJFUzI1NiI[A-Za-z0-9\-_=]*\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
    private const val MAX_SCRIPTS = 4

    /**
     * Drop the currently cached token so the next [getToken] re-extracts a fresh one.
     * Intended for the API call site to invoke when Apple rejects the cached token with
     * a 401/403 (revoked before its `exp`). Best-effort and safe to call at any time:
     * if no token is cached this is a no-op, and a concurrent [getToken] simply refetches.
     */
    suspend fun invalidate() {
        mutex.withLock {
            // A LIVE token that Apple revoked is a reason to extract again NOW. A 401 for the (expired) fallback token is the
            // expected answer while extraction keeps failing: it must not undo the back-off (owner's log 2026-10-27: three
            // failed downloads of the web player in 40 s, each «next try in 5 min»).
            if (cachedToken != null) {
                consecutiveFailures = 0
                nextAttemptAtMs = 0L
            }
            cachedToken = null
            cachedTokenExpiryMs = 0L
        }
    }

    /**
     * Derive a cache-expiry instant (epoch millis) from a JWT's `exp` claim, minus a
     * safety skew. Falls back to a short TTL when the token can't be parsed. Best-effort:
     * any failure yields the default TTL rather than throwing.
     */
    private fun computeExpiryMs(jwt: String): Long {
        val now = System.currentTimeMillis()
        val expSeconds = parseJwtExpSeconds(jwt) ?: return now + DEFAULT_TTL_MS
        val expiryMs = expSeconds * 1000L - EXPIRY_SKEW_MS
        // If the freshly extracted token is already at/past its skewed expiry (device
        // clock skew or a stale asset), don't pin it for long — allow a near-immediate
        // refetch on the next call instead of caching a dead token for the process life.
        return if (expiryMs <= now) now else expiryMs
    }

    /**
     * Decode the JWT payload (second dot-separated segment, base64url) and read the
     * numeric `exp` claim (seconds since epoch). Returns null on any malformed input.
     */
    private fun parseJwtExpSeconds(jwt: String): Long? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val payloadJson = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val expMatch = Regex(""""exp"\s*:\s*(\d+)""").find(payloadJson) ?: return null
            expMatch.groupValues[1].toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    // Last-resort token used only when live extraction fails. Never cached; treated as
    // best-effort. May be expired, in which case requests fail gracefully with no
    // description rather than crashing.
    private const val FALLBACK_TOKEN =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"
}

/**
 * How long to leave Apple's web player alone after [consecutiveFailures] failed token extractions: 5 min, 15, 45, then
 * an hour at most. A real change on Apple's side takes longer than that to matter to a description, and a blip heals at
 * the first retry. Pure, so a guard test pins the curve (battery/radio audit 2026-10-20).
 */
internal object AppleTokenRetryPolicy {
    private const val BASE_MS = 5 * 60 * 1000L
    private const val MAX_MS = 60 * 60 * 1000L

    fun retryAfterMs(consecutiveFailures: Int): Long {
        if (consecutiveFailures <= 0) return 0L
        var delayMs = BASE_MS
        repeat(consecutiveFailures - 1) {
            delayMs = (delayMs * 3).coerceAtMost(MAX_MS)
        }
        return delayMs.coerceAtMost(MAX_MS)
    }
}
