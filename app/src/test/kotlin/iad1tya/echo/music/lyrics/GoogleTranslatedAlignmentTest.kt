package iad1tya.echo.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec mirror for the keyless chain's GOOGLE step (owner directive 2026-08-29: translations must
 * be Google-Translate-based). The production parsers live in LyricsTranslationHelper.kt as
 * top-level internal functions; this test pins BOTH response shapes VERIFIED LIVE on 2026-08-29:
 *
 *  · dict-chrome-ex translate_a/t GET:
 *      ["hola mundo"]                          (sl known)
 *      [["Hola Mundo","en"]]                    (sl=auto → pair; tested with Cyrillic input)
 *      ["verso uno\n", "verso dos"]             (sentence-split slots, concatenated in order)
 *  · gtx translate_a/single POST:
 *      [[["primera línea de letra\n","first line of lyrics\n",...],[...]],null,"en",...]
 *
 * If Google changes a shape, the parsers must change WITH this mirror.
 */
class GoogleTranslatedAlignmentTest {

    // ── parseGoogleTResponse (dict-chrome-ex) ──────────────────────────────────────────────

    @Test
    fun `plain single-string shape parses line-aligned`() {
        val body = """["verso uno\nverso dos\nverso tres"]"""
        assertEquals(listOf("verso uno", "verso dos", "verso tres"), parseGoogleTResponse(body))
    }

    @Test
    fun `auto-detect pair shape takes the text slot`() {
        val body = """[["Hola Mundo","en"]]"""
        assertEquals(listOf("Hola Mundo"), parseGoogleTResponse(body))
    }

    @Test
    fun `multi-slot sentence split concatenates in order`() {
        // Verified live with mixed punctuation: Google may answer several array slots, each a
        // translated fragment; the full text is the concatenation, never just the first slot.
        val body = """["hola ","mundo"]"""
        assertEquals(listOf("hola mundo"), parseGoogleTResponse(body))
    }

    @Test
    fun `html sorry-page body rejects as null`() {
        // The bot-block answer starts with '<' — production checks it before parsing, but the
        // parser must also be safe if handed one directly.
        assertNull(parseGoogleTResponse("<html><head><title>Sorry...</title>"))
    }

    @Test
    fun `empty array rejects as null`() {
        assertNull(parseGoogleTResponse("[]"))
    }

    @Test
    fun `garbage rejects as null without throwing`() {
        assertNull(parseGoogleTResponse("not json at all"))
    }

    // ── parseGtxSingleResponse (gtx single, POST path) ─────────────────────────────────────

    @Test
    fun `gtx segments concatenate with trailing newline markers preserved`() {
        // Real shape from the live probe (3 input lines): each segment carries its own "\n".
        val body = """[[["primera línea de letra\n","first line of lyrics\n",null,null,3],["segunda línea aquí\n","second line here\n",null,null,3],["y un tercero","and a third one",null,null,3]],null,"en",null,null,null,null,[]]"""
        assertEquals(
            listOf("primera línea de letra", "segunda línea aquí", "y un tercero"),
            parseGtxSingleResponse(body),
        )
    }

    @Test
    fun `gtx empty segment list rejects as null`() {
        assertNull(parseGtxSingleResponse("""[[],null,"en"]"""))
    }

    @Test
    fun `gtx html body rejects as null`() {
        assertNull(parseGtxSingleResponse("""<html><head></head><body>429</body></html>"""))
    }
}
