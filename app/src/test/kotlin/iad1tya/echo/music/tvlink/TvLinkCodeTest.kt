package iad1tya.echo.music.tvlink

import java.security.SecureRandom
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 907: the code a TV shows and the end-to-end seal of «Iniciar sesión en la TV». */
class TvLinkCodeTest {
    private val code = TvLinkCode.generate(SecureRandom())

    @Test
    fun `a code is 25 letters of the pairing alphabet, typed with a dash every five`() {
        assertEquals(TvLinkCode.LENGTH, code.length)
        assertTrue(code.none { it in "01ILO" })
        val pretty = TvLinkCode.pretty(code)
        assertEquals(29, pretty.length)
        assertEquals(code, TvLinkCode.normalize(pretty))
        assertEquals(code, TvLinkCode.normalize(" " + pretty.lowercase() + " "))
        assertEquals(pretty, TvLinkCode.typing(code))
        assertEquals("ABCDE-FG", TvLinkCode.typing("abcdefg"))
        assertNull("a short code is not a code", TvLinkCode.normalize(code.dropLast(1)))
        assertNull("a pairing code (20) is not a TV code", TvLinkCode.normalize("ABCD-EFGH-JKMN-PQRS-TUVW"))
    }

    @Test
    fun `the QR link opens in Aura and only a full code counts`() {
        val link = TvLinkCode.link(code)
        assertTrue(link.startsWith("echomusic://tvlogin?c="))
        assertEquals(code, TvLinkCode.fromLink(link))
        assertNull(TvLinkCode.fromLink("echomusic://tvlogin?c=ABCDE"))
        assertNull(TvLinkCode.fromLink("echomusic://pair?code=" + TvLinkCode.pretty(code)))
        assertNull(TvLinkCode.fromLink("https://evil.example/tvlogin?c=" + code))
    }

    @Test
    fun `the relay id is a 32-hex hash that never contains the code or the key`() {
        val id = TvLinkCode.relayId(code)
        assertTrue(Regex("^[0-9a-f]{32}$").matches(id))
        assertEquals(id, TvLinkCode.relayId(code))
        assertNotEquals(id, TvLinkCode.relayId(TvLinkCode.generate()))
        assertFalse(id.contains(code.lowercase()))
        val keyHex = TvLinkCode.key(code).joinToString("") { "%02x".format(it) }
        assertFalse("the id is not the key", keyHex.startsWith(id))
        assertEquals(32, TvLinkCode.key(code).size)
    }

    @Test
    fun `a sealed session opens only with the same code and the same purpose`() {
        val session = TvLinkSession("SAPISID=abc; SID=def", "visitor", "sync-id", "samsung SM-S938B")
        val blob = TvLinkSeal.seal(code, TvLinkSeal.SESSION, TvLinkPayload.session(session))
        assertTrue("base64 only, as the relay accepts", Regex("^[A-Za-z0-9+/=]+$").matches(blob))
        assertFalse("the blob does not carry the cookie in clear", String(Base64.getDecoder().decode(blob), Charsets.ISO_8859_1).contains("SAPISID"))
        val opened = TvLinkSeal.open(code, TvLinkSeal.SESSION, blob)
        assertNotNull(opened)
        assertEquals(session, TvLinkPayload.parseSession(opened!!))
        assertNull("another code cannot open it", TvLinkSeal.open(TvLinkCode.generate(), TvLinkSeal.SESSION, blob))
        assertNull("a session is never read as a hello", TvLinkSeal.open(code, TvLinkSeal.HELLO, blob))
        val bytes = Base64.getDecoder().decode(blob)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull("an altered blob is refused", TvLinkSeal.open(code, TvLinkSeal.SESSION, Base64.getEncoder().encodeToString(bytes)))
        assertNull("garbage is refused", TvLinkSeal.open(code, TvLinkSeal.SESSION, "not base64!"))
        assertNotEquals("a fresh nonce every time", blob, TvLinkSeal.seal(code, TvLinkSeal.SESSION, TvLinkPayload.session(session)))
    }

    @Test
    fun `the hello names the device and nothing else`() {
        val blob = TvLinkSeal.seal(code, TvLinkSeal.HELLO, TvLinkPayload.hello("Sony BRAVIA 4K"))
        val hello = TvLinkPayload.parseHello(TvLinkSeal.open(code, TvLinkSeal.HELLO, blob)!!)
        assertEquals(TvLinkHello("Sony BRAVIA 4K"), hello)
        assertEquals("Android", TvLinkPayload.deviceLabel("  "))
        assertEquals("ab", TvLinkPayload.deviceLabel("a\nb"))
        assertEquals(60, TvLinkPayload.deviceLabel("x".repeat(200)).length)
    }

    @Test
    fun `a session without a cookie or of another version is refused, and it never prints its values`() {
        assertNull(TvLinkPayload.parseSession("""{"v":1,"cookie":""}""".toByteArray()))
        assertNull(TvLinkPayload.parseSession("""{"v":2,"cookie":"SAPISID=x"}""".toByteArray()))
        assertNull(TvLinkPayload.parseSession("nope".toByteArray()))
        val s = TvLinkSession("SAPISID=secret", "v", "d", "phone")
        assertFalse(s.toString().contains("secret"))
        assertArrayEquals(TvLinkPayload.session(s), TvLinkPayload.session(s.copy()))
    }
}
