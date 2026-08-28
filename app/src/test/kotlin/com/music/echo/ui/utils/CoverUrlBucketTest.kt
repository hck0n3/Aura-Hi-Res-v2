package iad1tya.echo.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cache audit H2 (HALLAZGO-037): the same logical cover was requested in up to 6 different
 * sizes (128/256/384/544/640/1200), and every size produces a distinct googleusercontent URL =
 * a distinct Coil disk-cache entry, so one cover could be downloaded 6 times. The bucket pins
 * the new rule: two canonical sizes only — 544 for list surfaces, 1200 for the player — which
 * are exactly the variants the DB and the queue already persist/use.
 */
class CoverUrlBucketTest {

    @Test
    fun zeroAndNegativeSizesStayUntouched() {
        assertEquals(0, canonicalCoverSize(0))
        assertEquals(-1, canonicalCoverSize(-1))
    }

    @Test
    fun listSizesBucketTo544() {
        assertEquals(544, canonicalCoverSize(128))
        assertEquals(544, canonicalCoverSize(256))
        assertEquals(544, canonicalCoverSize(384))
        assertEquals(544, canonicalCoverSize(544))
        assertEquals(544, canonicalCoverSize(640))
        assertEquals(544, canonicalCoverSize(800))
    }

    @Test
    fun playerSizesBucketTo1200() {
        assertEquals(1200, canonicalCoverSize(1000))
        assertEquals(1200, canonicalCoverSize(1200))
        assertEquals(1200, canonicalCoverSize(1600))
    }

    @Test
    fun resizeRewritesLh3UrlToCanonicalBucket() {
        val raw = "https://lh3.googleusercontent.com/abc123"
        assertEquals("$raw=w544-h544-p-l90-rj", raw.resize(640, 640))
        assertEquals("$raw=w1200-h1200-p-l90-rj", raw.resize(1200, 1200))
    }

    @Test
    fun resizeRewritesExistingSizeSuffixToCanonicalBucket() {
        val raw = "https://lh3.googleusercontent.com/abc123=w256-h256-p-l90-rj"
        assertEquals("https://lh3.googleusercontent.com/abc123=w544-h544-p-l90-rj", raw.resize(384, 384))
    }

    @Test
    fun resizeKeepsYouTubeThumbnailsOnHqdefault() {
        val raw = "https://i.ytimg.com/vi/xyz/maxresdefault.jpg"
        assertEquals("https://i.ytimg.com/vi/xyz/hqdefault.jpg", raw.resize(1200, 1200))
    }

    @Test
    fun resizeKeepsChannelAvatarsRaw() {
        val raw = "https://yt3.ggpht.com/token-s100-c-k-c0x00ffffff-no-rj"
        assertEquals(raw, raw.resize(544, 544))
    }
}
