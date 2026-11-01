package iad1tya.echo.music.reco

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Row 605: children's music is recognised by its own words, and ordinary titles that share a word are not. */
class KidsContentTest {
    @Test
    fun childrensMusicIsRecognisedByItsWords() {
        assertTrue(MusicStyle.isKidsContent("Canciones Cristianas para Niños Vol. 2"))
        assertTrue(MusicStyle.isKidsContent("Bible Songs For Kids"))
        assertTrue(MusicStyle.isKidsContent("Escuela Dominical - Cantos"))
        assertTrue(MusicStyle.isKidsContent("Música infantil cristiana"))
        assertTrue(MusicStyle.isKidsContent("Nursery Rhymes Collection"))
        assertTrue(MusicStyle.isKidsContent("Children's Songs of Praise"))
    }

    @Test
    fun ordinaryTitlesAreNotKidsMusic() {
        assertFalse(MusicStyle.isKidsContent("Baby"))
        assertFalse(MusicStyle.isKidsContent("Kids"))
        assertFalse(MusicStyle.isKidsContent("Children"))
        assertFalse(MusicStyle.isKidsContent("Lullaby"))
        assertFalse(MusicStyle.isKidsContent("Ave Maria Bach Gounod"))
        assertFalse(MusicStyle.isKidsContent(null))
    }

    @Test
    fun gateTurnsAwayKidsSongsOutsideAKidsContext() {
        val src = File("src/main/kotlin/com/music/echo/playback/ExactStyleGate.kt").readText()
        assertTrue("kids guard missing", src.contains("!contextKids && MusicStyle.isKidsContent(t.text)"))
    }
}
