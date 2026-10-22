package iad1tya.echo.music.utils.webplayer

/**
 * Pure reading of the googlevideo `/videoplayback` URL that YouTube's own web player requested inside the age-gate WebView
 * (see [EmbeddedPlayerWebView]). Everything here is plain string work so it is checked on the JVM: no Android classes, no
 * logging, and nothing leaves this file that could carry the URL into the shared log (AGENTS.md rule 4).
 */
internal object CapturedStream {
    /** Standard adaptive audio-only itags (AAC mp4 and opus webm). */
    val AUDIO_ITAGS: Set<String> = setOf("139", "140", "141", "171", "172", "249", "250", "251", "256", "258")

    private val OPUS_ITAGS = setOf("249", "250", "251", "171", "172")

    /** Query parameters of [url], decoded; a URL that does not parse gives an empty map. */
    fun query(url: String): Map<String, String> {
        val q = url.substringAfter('?', "").substringBefore('#')
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull { part ->
            val k = part.substringBefore('=', "")
            if (k.isEmpty()) null else k to java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }.toMap()
    }

    /** True for the request that carries AUDIO only: a known audio itag, or an `audio/…` mime in the URL. */
    fun isAudioOnly(url: String): Boolean {
        val q = query(url)
        return q["itag"] in AUDIO_ITAGS || q["mime"]?.startsWith("audio/", ignoreCase = true) == true
    }

    /** `audio/webm; codecs="opus"` / `audio/mp4; codecs="mp4a.40.2"`: what the rest of the app expects in a Format. */
    fun mimeTypeOf(url: String): String {
        val q = query(url)
        val itag = q["itag"]
        return when {
            itag in OPUS_ITAGS -> "audio/webm; codecs=\"opus\""
            itag != null && itag in AUDIO_ITAGS -> "audio/mp4; codecs=\"mp4a.40.2\""
            else -> (q["mime"] ?: "audio/mp4") + if (q["mime"]?.contains("webm") == true) "; codecs=\"opus\"" else "; codecs=\"mp4a.40.2\""
        }
    }

    fun itagOf(url: String): Int = query(url)["itag"]?.toIntOrNull() ?: 0

    fun contentLengthOf(url: String): Long? = query(url)["clen"]?.toLongOrNull()

    fun durationMsOf(url: String): String? = query(url)["dur"]?.toDoubleOrNull()?.let { (it * 1000).toLong().toString() }

    /** Seconds the URL stays valid, from its `expire` epoch; at least 60 so a clock skew never yields an instantly stale URL. */
    fun expiresInSeconds(url: String, nowEpochSeconds: Long): Int {
        val expire = query(url)["expire"]?.toLongOrNull() ?: return DEFAULT_EXPIRES_SECONDS
        return (expire - nowEpochSeconds).coerceIn(60L, 6L * 60 * 60).toInt()
    }

    /**
     * The headers to replay: what the page sent, without the ones that must come from the player that streams the bytes
     * (a stale `Range`) — the cookie header is never in a WebView subresource request, but it is dropped all the same.
     */
    fun replayHeaders(captured: Map<String, String>): Map<String, String> =
        captured.filterKeys { !it.equals("Range", true) && !it.equals("Cookie", true) }

    /** Cookies of a `Cookie:` string, one `name=value` each: WebView's CookieManager.setCookie takes ONE cookie per call. */
    fun splitCookies(cookie: String): List<String> =
        cookie.split(';').map { it.trim() }.filter { it.contains('=') && !it.startsWith("=") }

    const val DEFAULT_EXPIRES_SECONDS = 1800
}

/**
 * How long a failed age-gate WebView attempt for a song is remembered, so a song that cannot be unlocked is not retried (and
 * does not spin a WebView again) on every skip/prefetch/retry.
 */
internal class AgeWebViewFailureMemory(private val ttlMs: Long = TTL_MS) {
    private val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun recentlyFailed(videoId: String, nowMs: Long): Boolean {
        val at = failedAt[videoId] ?: return false
        if (nowMs - at >= ttlMs) {
            failedAt.remove(videoId)
            return false
        }
        return true
    }

    fun recordFailure(videoId: String, nowMs: Long) {
        if (failedAt.size > MAX_ENTRIES) failedAt.clear()
        failedAt[videoId] = nowMs
    }

    fun recordSuccess(videoId: String) {
        failedAt.remove(videoId)
    }

    companion object {
        const val TTL_MS = 10 * 60 * 1000L
        private const val MAX_ENTRIES = 200
    }
}
