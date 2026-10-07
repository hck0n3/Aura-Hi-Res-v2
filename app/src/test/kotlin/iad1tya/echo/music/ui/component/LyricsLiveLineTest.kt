package iad1tya.echo.music.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 347 (plan C4): only the lyric lines that animate with time read the per-frame position. */
class LyricsLiveLineTest {

    @Test
    fun `only the active line and the one fading out are live`() {
        assertTrue(lyricsLineIsLive(index = 5, lineTime = 50_000, currentIndex = 5, currentTime = 50_000))
        assertTrue(lyricsLineIsLive(index = 4, lineTime = 45_000, currentIndex = 5, currentTime = 50_000))
        assertFalse(lyricsLineIsLive(index = 3, lineTime = 40_000, currentIndex = 5, currentTime = 50_000))
        assertFalse(lyricsLineIsLive(index = 6, lineTime = 55_000, currentIndex = 5, currentTime = 50_000))
        // A second line sharing the active timestamp (duet / background vocals) is active too.
        assertTrue(lyricsLineIsLive(index = 7, lineTime = 50_000, currentIndex = 5, currentTime = 50_000))
        // Before the first line nothing is live.
        assertFalse(lyricsLineIsLive(index = 0, lineTime = 10_000, currentIndex = -1, currentTime = null))
    }

    @Test
    fun `a frozen past line is fully sung and a frozen future line has nothing sung`() {
        val past = lyricsFrozenLinePosition(lineTime = 40_000, nextLineTime = 45_000, isPast = true)
        // Past every word and every 600 ms fade-out of that line.
        assertTrue(past > 45_000 + 600)
        val future = lyricsFrozenLinePosition(lineTime = 55_000, nextLineTime = 60_000, isPast = false)
        assertTrue(future < 55_000)
        // The last line (no next) still lands past its own time.
        assertTrue(lyricsFrozenLinePosition(lineTime = 200_000, nextLineTime = null, isPast = true) > 200_600)
    }
}
