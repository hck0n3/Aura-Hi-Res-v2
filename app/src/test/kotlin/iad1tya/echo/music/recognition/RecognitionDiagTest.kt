package iad1tya.echo.music.recognition

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 642: the recognizer's one-line-per-attempt diagnostic (numbers and categories only) classifies a «No match». */
class RecognitionDiagTest {

    @Test
    fun levelsAreDbfsAndSilenceIsFloored() {
        assertEquals(0, RecognitionDiag.levelDb(32768.0))
        assertEquals(-6, RecognitionDiag.levelDb(16384.0))
        assertEquals(-99, RecognitionDiag.levelDb(0.0))
        assertEquals(-99, RecognitionDiag.levelDb(-5.0))
    }

    @Test
    fun errorsAreClassifiedWithoutLoggingTheirText() {
        assertEquals("network", RecognitionDiag.errorCategory("java.net.SocketTimeoutException: Read timed out"))
        assertEquals("network", RecognitionDiag.errorCategory("Unable to resolve host \"amp.shazam.com\""))
        assertEquals("limit", RecognitionDiag.errorCategory("HTTP 429 Too Many Requests"))
        assertEquals("resample", RecognitionDiag.errorCategory("Failed to resample audio: x"))
        assertEquals("format", RecognitionDiag.errorCategory("Invalid audio format for fingerprint generation"))
        assertEquals("fingerprint", RecognitionDiag.errorCategory("Failed to generate fingerprint: y"))
        assertEquals("service", RecognitionDiag.errorCategory("something else entirely"))
        assertEquals("service", RecognitionDiag.errorCategory(null))
    }

    @Test
    fun aSilentCaptureIsNotBlamedOnTheCatalogue() {
        assertEquals("silent-capture", RecognitionDiag.verdict("nomatch", -70))
        assertEquals("nomatch", RecognitionDiag.verdict("nomatch", -20))
        assertEquals("match", RecognitionDiag.verdict("match", -90))
        assertEquals("error", RecognitionDiag.verdict("error", -10))
    }

    @Test
    fun theLineCarriesOnlyNumbersAndCategories() {
        val line = RecognitionDiag.line(
            source = RecognitionDiag.sourceName(9), recordedMs = 12_040, peakDb = -9, rmsDb = -31,
            windows = listOf(RecognitionDiag.window(3_000, "nomatch", 412), RecognitionDiag.window(5_000, "network", 3_100)),
            result = "error", elapsedMs = 13_000,
        )
        assertEquals(
            "RECOGNITION_DIAG src=unprocessed recordedMs=12040 peakDb=-9 rmsDb=-31 windows=2 [3s:nomatch:412ms,5s:network:3100ms] result=error elapsedMs=13000",
            line,
        )
    }

    @Test
    fun theServiceWritesTheLineAndNeverTheMessage() {
        val src = File("src/main/kotlin/com/music/echo/recognition/MusicRecognitionService.kt").readText()
        assertTrue(src.contains("RecognitionDiag.line("))
        assertTrue(src.contains("RecognitionDiag.errorCategory(outcome.message)"))
        // the raw error text must never be passed to the logger
        assertFalse(Regex("""Timber[^\n]*outcome\.message""").containsMatchIn(src))
    }
}
