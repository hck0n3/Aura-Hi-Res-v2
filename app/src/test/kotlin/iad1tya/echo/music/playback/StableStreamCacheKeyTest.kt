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
 *
 * STABLE IDENTITY FIX (2026-09-04): these tests use REALISTIC googlevideo URLs — the `id=`
 * param is an opaque o-XXXX STREAM identifier that ROTATES on every resolve (verified live:
 * same video, two resolves 2s apart, different `id=`). The stable identity is the mediaId
 * (dataSpec.key, = the videoId placed by MediaItemExt.setCustomCacheKey) + the `itag` param
 * (stable and quality-distinct). The pre-fix tests used fabricated URLs where `id=` held the
 * videoId — they pinned the false premise that made every replay re-download (row 206).
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
        val itag = queryParam(url, "itag")
        val urlStreamId = queryParam(url, "id")
        return when {
            !specKey.isNullOrBlank() -> buildString {
                append("yt-stream-")
                append(specKey)
                if (!itag.isNullOrBlank()) append("-").append(itag)
            }
            urlStreamId.isNullOrBlank() -> url
            else -> buildString {
                append("yt-stream-")
                append(urlStreamId)
                if (!itag.isNullOrBlank()) append("-").append(itag)
            }
        }
    }

    @Test
    fun rotatedUrlsForSameSongCollapseToSameKey() {
        // REALISTIC rotation: same videoId (specKey = mediaId), the `id=` o-XXXX changes.
        val resolve1 = "https://rr3---sn-x.googlevideo.com/videoplayback?c=ANDROID_VR&expire=111&id=o-AEPtfEexF9dDFZivvNUG0c62&itag=251&ip=1.2.3.4&signature=SIGAAA"
        val resolve2 = "https://rr5---sn-y.googlevideo.com/videoplayback?c=ANDROID_VR&expire=222&id=o-AJs0tH3Yu5T8J7I_II7b-Cs&itag=251&ip=5.6.7.8&signature=SIGBBB"
        assertEquals("yt-stream-abcVIDEO123-251", keyOf(resolve1, specKey = "abcVIDEO123"))
        assertEquals("yt-stream-abcVIDEO123-251", keyOf(resolve2, specKey = "abcVIDEO123"))
        assertEquals(keyOf(resolve1, specKey = "abcVIDEO123"), keyOf(resolve2, specKey = "abcVIDEO123"))
    }

    @Test
    fun differentItagsGetDistinctKeys() {
        val opus = "https://x.googlevideo.com/videoplayback?id=o-ROT1&itag=251"
        val aac = "https://x.googlevideo.com/videoplayback?id=o-ROT2&itag=140"
        assertEquals("yt-stream-abcVIDEO123-251", keyOf(opus, specKey = "abcVIDEO123"))
        assertEquals("yt-stream-abcVIDEO123-140", keyOf(aac, specKey = "abcVIDEO123"))
    }

    @Test
    fun nonGoogleVideoFallsBackToSpecKeyOrUrl() {
        val qobuz = "https://streaming-qobuz.example.com/v1/file.flac?token=T1"
        assertEquals("my-custom-key", keyOf(qobuz, specKey = "my-custom-key"))
        assertEquals(qobuz, keyOf(qobuz))
    }

    @Test
    fun videoModeWithoutSpecKeyKeepsLegacyRotatingKey() {
        // Video-mode deliberately passes key=null (VideoModeCoordinator): no custom key → the
        // legacy o-XXXX-based key, so video replay-cache behavior is unchanged by the fix.
        val noKey = "https://x.googlevideo.com/videoplayback?id=o-ROTATING&itag=137"
        assertEquals("yt-stream-o-ROTATING-137", keyOf(noKey))
    }

    @Test
    fun googleVideoWithoutIdFallsBackToUrlKey() {
        val weird = "https://x.googlevideo.com/videoplayback?itag=251"
        assertEquals(weird, keyOf(weird))
    }

    @Test
    fun differentSongsNeverShareAKey() {
        val a = "https://x.googlevideo.com/videoplayback?id=o-ROT&itag=251"
        val b = "https://x.googlevideo.com/videoplayback?id=o-ROT&itag=251"
        assert(keyOf(a, specKey = "videoAAA") != keyOf(b, specKey = "videoBBB"))
    }
}
