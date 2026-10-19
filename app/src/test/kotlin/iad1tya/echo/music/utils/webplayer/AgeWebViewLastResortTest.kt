package iad1tya.echo.music.utils.webplayer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19: «una solución funcional sí o sí». The WebView that lets YouTube's own web player (signed in) fetch an
 * age-restricted song is the last resort. These tests pin what makes it safe: it runs for the age case only, one at a time,
 * with a memory of failures, a real cookie injection, and no user data in the shared log.
 */
class AgeWebViewLastResortTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun file(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private val utils = "app/src/main/kotlin/com/music/echo/utils"
    private val ytPlayer: String by lazy { file("$utils/YTPlayerUtils.kt") }
    private val resolver: String by lazy { file("$utils/webplayer/EmbeddedPlayerUrlResolver.kt") }
    private val webView: String by lazy { file("$utils/webplayer/EmbeddedPlayerWebView.kt") }

    private val audioUrl =
        "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1790000000&itag=251&clen=3456789&dur=212.481&mime=audio%2Fwebm&c=WEB&sig=x"

    @Test
    fun `only the audio request is recognised, by itag or by mime`() {
        assertTrue(CapturedStream.isAudioOnly(audioUrl))
        assertTrue(CapturedStream.isAudioOnly("https://x.googlevideo.com/videoplayback?mime=audio%2Fmp4&itag=999"))
        assertFalse(CapturedStream.isAudioOnly("https://x.googlevideo.com/videoplayback?itag=137&mime=video%2Fmp4"))
        assertFalse(CapturedStream.isAudioOnly("https://x.googlevideo.com/videoplayback"))
    }

    @Test
    fun `format fields come from the url`() {
        assertEquals(251, CapturedStream.itagOf(audioUrl))
        assertEquals(3456789L, CapturedStream.contentLengthOf(audioUrl))
        assertEquals("212481", CapturedStream.durationMsOf(audioUrl))
        assertTrue(CapturedStream.mimeTypeOf(audioUrl).startsWith("audio/webm"))
        assertTrue(CapturedStream.mimeTypeOf("https://x/videoplayback?itag=140").startsWith("audio/mp4"))
        assertNull(CapturedStream.contentLengthOf("https://x/videoplayback?itag=140"))
    }

    @Test
    fun `expiry is clamped and never already stale`() {
        assertEquals(60, CapturedStream.expiresInSeconds("https://x/videoplayback?expire=100", 1_000))
        assertEquals(1000, CapturedStream.expiresInSeconds("https://x/videoplayback?expire=2000", 1_000))
        assertEquals(6 * 60 * 60, CapturedStream.expiresInSeconds("https://x/videoplayback?expire=999999999999", 1_000))
        assertEquals(CapturedStream.DEFAULT_EXPIRES_SECONDS, CapturedStream.expiresInSeconds("https://x/videoplayback", 1_000))
    }

    @Test
    fun `replayed headers never carry a range or a cookie`() {
        val out = CapturedStream.replayHeaders(mapOf("Range" to "bytes=0-", "cookie" to "a=b", "User-Agent" to "ua", "Origin" to "https://www.youtube.com"))
        assertEquals(setOf("User-Agent", "Origin"), out.keys)
    }

    @Test
    fun `the cookie header is split into one cookie per call`() {
        assertEquals(listOf("SID=1", "HSID=2", "__Secure-3PSID=3"), CapturedStream.splitCookies("SID=1; HSID=2;  __Secure-3PSID=3; ;"))
        assertTrue(CapturedStream.splitCookies("").isEmpty())
    }

    @Test
    fun `a failed song is remembered for a while and forgotten after success or expiry`() {
        val memory = AgeWebViewFailureMemory(ttlMs = 1_000)
        assertFalse(memory.recentlyFailed("a", 0))
        memory.recordFailure("a", 100)
        assertTrue(memory.recentlyFailed("a", 500))
        assertFalse(memory.recentlyFailed("a", 1_200))
        memory.recordFailure("b", 0)
        memory.recordSuccess("b")
        assertFalse(memory.recentlyFailed("b", 1))
    }

    @Test
    fun `the WebView is used for the age gate of a signed-in account and nothing else`() {
        val fn = ytPlayer.substringAfter("private suspend fun finishWithEmbeddedFallback").substringBefore("private suspend fun resolvePlaybackData")
        assertTrue(fn.contains("result.isSuccess || !ageGated || YouTube.cookie.isNullOrBlank()) return result"))
        assertTrue(ytPlayer.contains("finishWithEmbeddedFallback(videoId, retryResult, ageGated.get())"))
        assertTrue(ytPlayer.contains("finishWithEmbeddedFallback(videoId, firstAttempt, ageGated.get())"))
        assertTrue("el resultado reproduce con las cabeceras capturadas", fn.contains("fallbackRequestHeaders = resolved.requestHeaders"))
    }

    @Test
    fun `one WebView at a time, with a failure memory and a timeout`() {
        assertTrue(resolver.contains("Mutex()") && resolver.contains("gate.withLock"))
        assertTrue(resolver.contains("failures.recentlyFailed") && resolver.contains("failures.recordFailure"))
        assertTrue(resolver.contains("TIMEOUT_MS"))
        assertTrue(webView.contains("withTimeout(timeoutMs)") && webView.contains("NonCancellable"))
    }

    @Test
    fun `the session reaches the page and the watch page is the one loaded`() {
        assertTrue(webView.contains("CapturedStream.splitCookies(cookie)"))
        assertTrue(webView.contains("watch?v=%s"))
        assertFalse("el embed no desbloquea +18", webView.contains("/embed/%s"))
        assertTrue("sin sesión (login de Google) falla rápido", webView.contains("accounts.google.com"))
    }

    @Test
    fun `no title, id, url, header or cookie reaches the shared log`() {
        for ((name, src) in listOf("resolver" to resolver, "webview" to webView)) {
            for (line in src.lines().filter { it.contains("Timber.") }) {
                assertFalse("$name registra el id: $line", line.contains("\$videoId") || line.contains("\${videoId"))
                assertFalse("$name registra la url/cabeceras: $line", Regex("streamUrl|requestHeaders|headers|cookie\\)|\\burl\\b", RegexOption.IGNORE_CASE).containsMatchIn(line.substringAfter("Timber.")))
            }
        }
    }
}
