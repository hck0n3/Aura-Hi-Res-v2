package iad1tya.echo.music.utils.cipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureCipherTest {

    private val cipher = "s=AOq0QJ8w%3D%3DabcDEF&sp=sig&url=https%3A%2F%2Frr1---sn-x.googlevideo.com%2Fvideoplayback%3Fexpire%3D1%26itag%3D251"

    @Test
    fun `a signatureCipher is split into the obfuscated signature, the parameter name and the base url`() {
        val parts = SignatureCipher.parse(cipher)!!
        assertEquals("AOq0QJ8w==abcDEF", parts.s)
        assertEquals("sig", parts.sp)
        assertEquals("https://rr1---sn-x.googlevideo.com/videoplayback?expire=1&itag=251", parts.url)
    }

    @Test
    fun `the parameter name defaults to signature and a cipher without s or url is rejected`() {
        assertEquals("signature", SignatureCipher.parse("s=abc&url=https%3A%2F%2Fx.test%2Fv%3Fa%3D1")!!.sp)
        assertNull(SignatureCipher.parse("sp=sig&url=https%3A%2F%2Fx.test%2Fv"))
        assertNull(SignatureCipher.parse("s=abc&sp=sig"))
        assertNull(SignatureCipher.parse(""))
    }

    @Test
    fun `the solved signature is appended under the asked name and percent-encoded`() {
        val parts = SignatureCipher.parse(cipher)!!
        val url = SignatureCipher.buildUrl(parts, "AB=cd+e/f")
        assertEquals("https://rr1---sn-x.googlevideo.com/videoplayback?expire=1&itag=251&sig=AB%3Dcd%2Be%2Ff", url)
        // A base url without a query string starts one.
        assertEquals("https://x.test/v?signature=zz", SignatureCipher.buildUrl(SignatureCipher.Parts("s", "signature", "https://x.test/v"), "zz"))
    }

    @Test
    fun `a literal plus survives decoding and non-ascii text round-trips through utf-8`() {
        assertEquals("a+b c", SignatureCipher.percentDecode("a+b%20c"))
        assertEquals("ñ€", SignatureCipher.percentDecode(SignatureCipher.percentEncode("ñ€")))
        assertEquals("100%", SignatureCipher.percentDecode("100%"))
        assertEquals("%zz", SignatureCipher.percentDecode("%zz"))
    }

    @Test
    fun `the signature timestamp is read from the player the way yt-dlp reads it`() {
        assertEquals(20352, PlayerSts.parse("a=1;b:{signatureTimestamp:20352,x:1};"))
        assertEquals(20352, PlayerSts.parse("signatureTimestamp : 20352"))
        assertEquals(20111, PlayerSts.parse("...sts:20111,..."))
        // signatureTimestamp wins over a stray sts.
        assertEquals(20352, PlayerSts.parse("sts:11111;signatureTimestamp:20352"))
        assertNull(PlayerSts.parse("no timestamp here, sts:123"))
        assertNull(PlayerSts.parse("xsts:20352"))
    }
}
