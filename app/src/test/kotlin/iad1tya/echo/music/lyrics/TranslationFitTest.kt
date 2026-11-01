package iad1tya.echo.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** Registry row 561 — a short translation is never cached or saved as if it were complete. */
class TranslationFitTest {
    @Test
    fun `an answer with every line is complete, more lines too`() {
        assertEquals(TranslationFit.COMPLETE, translationFit(30, 30))
        assertEquals(TranslationFit.COMPLETE, translationFit(31, 30))
    }

    @Test
    fun `an answer at least 90 percent complete is shown once but not saved`() {
        assertEquals(TranslationFit.PARTIAL_USABLE, translationFit(27, 30))
        assertEquals(TranslationFit.PARTIAL_USABLE, translationFit(29, 30))
    }

    @Test
    fun `a clearly short answer is an error the user can see`() {
        assertEquals(TranslationFit.TOO_SHORT, translationFit(26, 30))
        assertEquals(TranslationFit.TOO_SHORT, translationFit(0, 30))
    }
}
