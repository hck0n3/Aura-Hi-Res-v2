package iad1tya.echo.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ten providers can only answer the question they are asked.
 *
 * 🔴 OWNER REPORT (2026-09-16): *"cada vez que busco letras de alguna canción la mayoría de las veces dice
 * que no hay letras, y con 10 proveedores… no lo veo correcto"*.
 *
 * All ten were being asked — and all ten were being asked for the raw YouTube title, which no lyrics
 * database indexes. These pin the cleaning, and more importantly they pin what must NOT be cleaned: a
 * live or acoustic take is a different recording with different words, and silently searching for the
 * studio version would put the wrong lyrics on screen, which is worse than none.
 */
class LyricsQueryTest {
    private fun clean(raw: String) = LyricsQuery.cleanTitle(raw)

    @Test
    fun `upload tags that describe the video are removed`() {
        assertEquals("Blinding Lights", clean("Blinding Lights (Official Video)"))
        assertEquals("Shape of You", clean("Shape of You (Official Music Video)"))
        assertEquals("Someone Like You", clean("Someone Like You (Official Audio)"))
        assertEquals("Flowers", clean("Flowers (Lyrics)"))
        assertEquals("Lose Yourself", clean("Lose Yourself (Lyric Video)"))
        assertEquals("Titi Me Pregunto", clean("Titi Me Pregunto (Visualizer)"))
        assertEquals("Numb", clean("Numb [4K]"))
        assertEquals("Bohemian Rhapsody", clean("Bohemian Rhapsody (Remastered 2011)"))
    }

    @Test
    fun `spanish upload tags too`() {
        assertEquals("La Bachata", clean("La Bachata [Video Oficial]"))
        assertEquals("Provenza", clean("Provenza (Audio Oficial)"))
    }

    @Test
    fun `a trailing tag without brackets is removed`() {
        assertEquals("Yesterday", clean("Yesterday - Official Video"))
        assertEquals("Creep", clean("Creep | Official Audio"))
    }

    @Test
    fun `a different recording keeps its name`() {
        // THE important half. These are not noise: they are the identity of a take whose words differ.
        assertEquals("Hotel California (Live)", clean("Hotel California (Live)"))
        assertEquals("Layla (Acoustic)", clean("Layla (Acoustic)"))
        assertEquals("Faded (Restrung)", clean("Faded (Restrung)"))
        assertEquals("Blue Monday (Remix)", clean("Blue Monday (Remix)"))
        assertEquals("Live and Let Die", clean("Live and Let Die"))
    }

    @Test
    fun `a clean title is returned untouched`() {
        assertEquals("Smells Like Teen Spirit", clean("Smells Like Teen Spirit"))
        assertEquals("99 Problems", clean("99 Problems"))
    }

    @Test
    fun `stripping never yields an empty query`() {
        // A query of "" matches nothing at every provider, so the original has to survive.
        assertEquals("(Audio)", clean("(Audio)"))
        assertEquals("(Official Video)", clean("(Official Video)"))
    }

    @Test
    fun `the lead artist is taken, without breaking a name that contains a comma`() {
        assertEquals("Eminem", LyricsQuery.primaryArtist("Eminem feat. Rihanna"))
        assertEquals("Eminem", LyricsQuery.primaryArtist("Eminem, Rihanna"))
        assertEquals("Daft Punk", LyricsQuery.primaryArtist("Daft Punk & Pharrell Williams"))
        // "Tyler, The Creator" is ONE artist. Splitting it gives someone else entirely.
        assertEquals("Tyler, The Creator", LyricsQuery.primaryArtist("Tyler, The Creator"))
    }

    @Test
    fun `a raw retry is only worth it when cleaning changed something`() {
        assertTrue(
            LyricsQuery.isWorthRetryingRaw(
                rawTitle = "Song (Official Video)",
                rawArtist = "A, B",
                cleanedTitle = "Song",
                cleanedArtist = "A",
            ),
        )
        assertFalse(
            "an unchanged query must not be sent to all ten providers twice",
            LyricsQuery.isWorthRetryingRaw(
                rawTitle = "Song",
                rawArtist = "A",
                cleanedTitle = "Song",
                cleanedArtist = "A",
            ),
        )
    }
}
