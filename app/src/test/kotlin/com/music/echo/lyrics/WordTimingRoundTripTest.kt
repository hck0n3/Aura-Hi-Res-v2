package iad1tya.echo.music.lyrics

import iad1tya.echo.music.betterlyrics.TTMLParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The karaoke chain, pinned end to end.
 *
 * Word-by-word highlighting only happens when a word's timing survives FOUR hops:
 *
 *   TTML from the provider → [TTMLParser.parseTTML] → [TTMLParser.toLRC] (which writes the timings
 *   into a `<word:start:end|…>` line under each verse) → [LyricsUtils.parseLyrics] (which reads that
 *   line back into `LyricsEntry.words`) → the exact sweep in `Lyrics.kt` (`hasWordTimings &&
 *   KARAOKE`).
 *
 * Every hop is in a different file and two are in a different module, and the whole thing degrades
 * SILENTLY: drop the timings anywhere and the lyrics still appear, still scroll, still highlight —
 * just line by line, with a synthetic sweep instead of the real one. No test fails, no crash, no log
 * line. The owner would have to notice that the highlight no longer lands on the syllable.
 *
 * So this asserts the contract itself rather than any one function: TTML in, real word timings out.
 * If someone "simplifies" `toLRC` into plain LRC, or changes the `<…>` shape on either side, this
 * fails immediately and names what broke.
 *
 * (Written 2026-09-15 while verifying — at the owner's request — whether this app was behind
 * SimpMusic on lyrics. It is not: the parser, the carrier format and the renderer are all here. What
 * was missing was anything stopping a future change from quietly unplugging them.)
 */
class WordTimingRoundTripTest {
    /** Two verses, the first with four word spans, the shape Apple-style TTML uses. */
    private val ttml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
          <body>
            <div>
              <p begin="00:10.000" end="00:13.000">
                <span begin="00:10.000" end="00:10.500">Never </span>
                <span begin="00:10.500" end="00:11.200">gonna </span>
                <span begin="00:11.200" end="00:11.900">give </span>
                <span begin="00:11.900" end="00:12.400">you</span>
              </p>
              <p begin="00:14.000" end="00:16.000">
                <span begin="00:14.000" end="00:15.000">up</span>
              </p>
            </div>
          </body>
        </tt>
        """.trimIndent()

    @Test
    fun `word timings survive the whole chain from TTML to a lyrics entry`() {
        val parsed = TTMLParser.parseTTML(ttml)
        assertEquals("TTML did not parse into two verses", 2, parsed.size)
        assertEquals("the first verse lost its word spans", 4, parsed[0].words.size)

        // The carrier: whatever shape toLRC uses, parseLyrics must be able to read it back.
        val lrc = TTMLParser.toLRC(parsed)
        val entries = LyricsUtils.parseLyrics(lrc)
        assertTrue("no lyrics entries came back", entries.isNotEmpty())

        val first = entries.first { it.text.contains("Never") }
        val words = first.words
        assertNotNull(
            "the first verse reached the UI with NO word timings — karaoke would fall back to the " +
                "synthetic sweep and nothing else would notice",
            words,
        )
        words!!
        assertEquals("a word was lost between toLRC and parseLyrics", 4, words.size)
        assertEquals("Never", words[0].text)

        // The times themselves, not just their count: a carrier that rounds or reorders them is a
        // highlight that drifts off the syllable, which is the whole point of word timing.
        assertEquals(10.0, words[0].startTime, 0.01)
        assertEquals(10.5, words[0].endTime, 0.01)
        assertEquals(11.9, words[3].startTime, 0.01)

        // Monotonic and non-overlapping, or the sweep jumps backwards mid-verse.
        words.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "word timings must not go backwards: '${a.text}' ends ${a.endTime}, " +
                    "'${b.text}' starts ${b.startTime}",
                b.startTime >= a.startTime,
            )
        }
    }

    @Test
    fun `a verse with no spans still yields a line, just without word timings`() {
        // The common case for LrcLib and Kugou: line-synced only. It must degrade to a normal line
        // rather than losing the verse, because that fallback is what most providers rely on.
        val lineOnly =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p begin="00:05.000" end="00:07.000">Plain line</p></div></body>
            </tt>
            """.trimIndent()

        val entries = LyricsUtils.parseLyrics(TTMLParser.toLRC(TTMLParser.parseTTML(lineOnly)))
        val entry = entries.firstOrNull { it.text.contains("Plain line") }
        assertNotNull("a verse without spans must still produce a line", entry)
        assertEquals(5_000L, entry!!.time)
    }
}
