package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Registry row 766: a card without a thumbnail only builds an i.ytimg.com URL from a plausible 11-character video id.
 * 'aura-cover', album and playlist ids used to cost up to 3 useless 404s per card (maxres → sd → hq).
 */
class AuraCoverFallbackTest {
    @Test
    fun `default seed builds no ytimg url`() {
        assertNull(youtubeCoverOrFallback("aura-cover", null))
    }

    @Test
    fun `album browse id builds no ytimg url`() {
        assertNull(youtubeCoverOrFallback("MPREb_4pL8gzRtw1p", null))
    }

    @Test
    fun `playlist id builds no ytimg url`() {
        assertNull(youtubeCoverOrFallback("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI", null))
        assertNull(youtubeCoverOrFallback("OLAK5uy_kXBDbdzcH7c3cJUu4bLxNm-6ATaf5mJAE", null))
    }

    @Test
    fun `real video id builds the hqdefault url`() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            youtubeCoverOrFallback("dQw4w9WgXcQ", null),
        )
    }

    @Test
    fun `an existing thumbnail always wins`() {
        assertEquals("https://example.com/a.jpg", youtubeCoverOrFallback("aura-cover", "https://example.com/a.jpg"))
    }

    @Test
    fun `eleven characters with a space or a slash is not a video id`() {
        assertNull(youtubeCoverOrFallback("Hello World", null))
        assertNull(youtubeCoverOrFallback("abc/def/ghi", null))
    }
}
