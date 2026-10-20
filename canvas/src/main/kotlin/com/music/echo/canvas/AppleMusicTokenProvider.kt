package iad1tya.echo.music.canvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object AppleMusicTokenProvider {
    private var cachedToken: String? = null
    private val mutex = Mutex()

    private val httpClient = HttpClient(OkHttp) {
        expectSuccess = true
    }

    suspend fun getToken(): String {
        return mutex.withLock {
            cachedToken?.let { return@withLock it }
            // Battery/radio (audit 2026-10-20): a failed extraction used to be forgotten at once, so EVERY canvas lookup of
            // a track (up to a dozen per song without a motion cover) re-downloaded the web player's HTML page and its
            // multi-MB index.js just to fail again. After a failure the fallback is served without touching the network
            // for [RETRY_AFTER_MS].
            if (lastFailureAtMs != 0L && System.currentTimeMillis() - lastFailureAtMs < RETRY_AFTER_MS) {
                return@withLock FALLBACK_TOKEN
            }
            try {
                val htmlResponse = httpClient.get("https://beta.music.apple.com")
                val htmlBody = htmlResponse.bodyAsText()
                val indexJsRegex = Regex("""src="(/assets/index-[^"]+\.js)"""")
                val match = indexJsRegex.find(htmlBody) ?: throw Exception("Could not find index.js")
                val indexJsUri = match.groupValues[1]

                val indexJsResponse = httpClient.get("https://beta.music.apple.com$indexJsUri")
                val indexJsBody = indexJsResponse.bodyAsText()

                val tokenRegex = Regex("""eyJ[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
                val tokenMatch = tokenRegex.find(indexJsBody) ?: throw Exception("Could not find token")
                
                val token = tokenMatch.value
                cachedToken = token
                lastFailureAtMs = 0L
                token
            } catch (e: Exception) {
                e.printStackTrace()
                // A cancelled lookup (track skipped, screen off) says nothing about Apple's page: no back-off for it.
                if (e !is kotlinx.coroutines.CancellationException) lastFailureAtMs = System.currentTimeMillis()
                FALLBACK_TOKEN
            }
        }
    }

    // Last-resort token (public by design: it is the Apple Music web player's own). Possibly expired, in which case the
    // requests that use it fail gracefully (no cover / no description), exactly as before.
    private const val FALLBACK_TOKEN =
        "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"

    private var lastFailureAtMs = 0L
    private const val RETRY_AFTER_MS = 10 * 60 * 1000L
}
