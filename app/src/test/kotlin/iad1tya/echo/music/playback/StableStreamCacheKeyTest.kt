package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec test for the stable stream cache key rule (MusicService.stableStreamCacheKeyFactory).
 * googlevideo rotates URL params per resolve; the cache identity must be videoId+itag so the
 * SAME bytes land under ONE key across resolves (owner directive 2026-08-29: listening must
 * save data). The production factory reads android.net.Uri (not testable on plain JVM), so this
 * test pins the KEY RULE itself with a pure query-param parser mirroring Uri.getQueryParameter
 * semantics. If the production key format ever changes, this mirror must change with it.
 */
class StableStreamCacheKeyTest {

    /** Mirror of Uri.getQueryParameter for the test (decoding not needed for these params). */
    private fun queryParam(url: String, name: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val k = pair.substringBefore('=')
            if (k == name) return pair.substringAfter('=', "")
        }
        return null
    }

    /** Mirror of the production rule (see stableStreamCacheKeyFactory in MusicService). */
    private fun keyOf(url: String, specKey: String? = null): String? {
        val host = url.substringAfter("//", "").substringBefore('/', "").substringAfter('@').let { h ->
            if (h.contains(':')) h.substringBefore(':') else h
        }
        val isGoogleVideo = host.endsWith("googlevideo.com")
        if (!isGoogleVideo) return specKey ?: url
        val videoId = queryParam(url, "id")
        val itag = queryParam(url, "itag")
        return if (!videoId.isNullOrBlank()) {
            buildString {
                append("yt-stream-")
                append(videoId)
                if (!itag.isNullOrBlank()) append("-").append(itag)
            }
        } else {
            specKey ?: url
        }
    }

    @Test
    fun rotatedUrlsForSameSongCollapseToSameKey() {
        val resolve1 = "https://rr3---sn-x.googlevideo.com/videoplayback?c=ANDROID_VR&expire=111&id=abcVIDEO123&itag=251&ip=1.2.3.4&signature=SIGAAA"
        val resolve2 = "https://rr5---sn-y.googlevideo.com/videoplayback?c=ANDROID_VR&expire=222&id=abcVIDEO123&itag=251&ip=5.6.7.8&signature=SIGBBB"
        assertEquals("yt-stream-abcVIDEO123-251", keyOf(resolve1))
        assertEquals("yt-stream-abcVIDEO123-251", keyOf(resolve2))
        assertEquals(keyOf(resolve1), keyOf(resolve2))
    }

    @Test
    fun differentItagsGetDistinctKeys() {
        val opus = "https://x.googlevideo.com/videoplayback?id=abcVIDEO123&itag=251"
        val aac = "https://x.googlevideo.com/videoplayback?id=abcVIDEO123&itag=140"
        assertEquals("yt-stream-abcVIDEO123-251", keyOf(opus))
        assertEquals("yt-stream-abcVIDEO123-140", keyOf(aac))
    }

    @Test
    fun nonGoogleVideoFallsBackToSpecKeyOrUrl() {
        val qobuz = "https://streaming-qobuz.example.com/v1/file.flac?token=T1"
        assertEquals("my-custom-key", keyOf(qobuz, specKey = "my-custom-key"))
        assertEquals(qobuz, keyOf(qobuz))
    }

    @Test
    fun googleVideoWithoutIdFallsBackToUrlKey() {
        val weird = "https://x.googlevideo.com/videoplayback?itag=251"
        assertEquals(weird, keyOf(weird))
    }

    @Test
    fun differentSongsNeverShareAKey() {
        val a = "https://x.googlevideo.com/videoplayback?id=videoAAA&itag=251"
        val b = "https://x.googlevideo.com/videoplayback?id=videoBBB&itag=251"
        assert(keyOf(a) != keyOf(b))
    }
}
