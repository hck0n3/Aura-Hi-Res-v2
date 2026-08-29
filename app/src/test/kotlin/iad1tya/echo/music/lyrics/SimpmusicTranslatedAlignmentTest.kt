package iad1tya.echo.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec test for the keyless translation chain's SimpMusic community step
 * (LyricsTranslationHelper.simpmusicTranslatedLines). The production function performs a live
 * HTTP call, so this test pins its PURE alignment logic by exercising the same parsing rules the
 * function applies: a [MM:SS.mm] timestamp prefix is stripped per line, and a payload is only
 * usable when the resulting line count matches the requested non-empty line count. If the
 * alignment rule in production ever changes, this mirror must change with it.
 */
class SimpmusicTranslatedAlignmentTest {

    private val tsRegex = """^\[\d{2}:\d{2}\.\d{2}\]\s*""".toRegex()

    /** Mirror of the production clean/align rule (see simpmusicTranslatedLines). */
    private fun align(lyric: String, nonEmptyCount: Int): List<String>? {
        val lines = lyric.split("\n").map { it.trim() }
        val clean = lines.map { l -> l.replace(tsRegex, "") }
        return if (clean.size == nonEmptyCount && clean.any { it.isNotBlank() }) clean else null
    }

    @Test
    fun plainLyricMatchingCountIsAccepted() {
        val lyric = "hola\nmundo\ncanción"
        assertEquals(listOf("hola", "mundo", "canción"), align(lyric, 3))
    }

    @Test
    fun timestampedSyncedLyricIsStrippedAndAccepted() {
        val lyric = "[00:01.23] hola\n[00:05.67] mundo\n[00:09.00] canción"
        assertEquals(listOf("hola", "mundo", "canción"), align(lyric, 3))
    }

    @Test
    fun countMismatchIsRejectedAsNull() {
        // The API may return a translation whose line count differs (merged verses) — accepting it
        // would shift every subsequent translated line onto the wrong original.
        assertNull(align("hola\nmundo", 3))
    }

    @Test
    fun emptyPayloadRejectsAsNull() {
        assertNull(align("", 1))
    }

    @Test
    fun trailingBlankLinesStillMustMatch() {
        // A trailing newline must not create a phantom extra line silently accepted.
        val lyric = "hola\nmundo\n"
        assertNull(align(lyric, 2))
    }

    @Test
    fun timestampInsideLineIsPreservedNotStripped() {
        // Only a LEADING timestamp is a sync marker; a bracketed time reference inside the sung
        // text is lyric content and must survive.
        val lyric = "hola [00:01.23] mundo"
        assertEquals(listOf("hola [00:01.23] mundo"), align(lyric, 1))
    }
}
