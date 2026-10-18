package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.playlistimport.MusicRequestEra.Range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 435 (owner 2026-10-18): «reggaetón cristiano del 2004 a 2010 empieza bien y luego me pone música de años más
 * recientes». The years are a RANGE, they leave the request words, and they are enforced per song.
 */
class MusicRequestEraTest {
    private val now = 2026

    private fun intent(text: String) = MusicRequestIntent.parse(text, currentYear = now)

    @Test
    fun `a span of years is one range and its words leave the request`() {
        for (text in listOf(
            "reggaeton cristiano del 2004 a 2010",
            "reggaeton cristiano del 2004 al 2010",
            "reggaeton cristiano 2004-2010",
            "reggaeton cristiano entre 2004 y 2010",
            "reggaeton cristiano desde 2004 hasta 2010",
            "reggaeton cristiano 2004 a 2010",
        )) {
            val i = intent(text)
            assertEquals(text, Range(2004, 2010), i.eraRange)
            assertEquals(text, "reggaeton cristiano", i.cleanPrompt)
            assertTrue(text, i.checkable)
        }
    }

    @Test
    fun `two decades are one range and the second is not left behind as a genre`() {
        for (text in listOf("rock clasico de los 70 y 80", "rock clasico años 70 y 80", "rock clasico de los 70 y los 80", "rock clasico 70s y 80s", "rock clasico 70-80")) {
            val i = intent(text)
            assertEquals(text, Range(1970, 1989), i.eraRange)
            assertEquals(text, "rock clasico", i.cleanPrompt)
        }
        assertEquals(Range(1970, 1999), intent("rock de los 70s a 90s").eraRange)
    }

    @Test
    fun `one decade stays in the request, where the playlist matching already understands it`() {
        val i = intent("reggaeton de los 2000")
        assertEquals(Range(2000, 2009), i.eraRange)
        assertTrue(i.cleanPrompt.contains("2000"))
        val eighties = intent("rock años 80")
        assertEquals(Range(1980, 1989), eighties.eraRange)
        assertTrue(eighties.cleanPrompt.contains("80"))
        assertEquals(Range(2000, 2009), intent("pop 2000s").eraRange)
    }

    @Test
    fun `one year is that year, as it always was`() {
        val i = intent("merengue cristiano 2026")
        assertEquals(Range(2026, 2026), i.eraRange)
        assertEquals(2026, i.year)
        assertEquals("merengue cristiano", i.cleanPrompt)
    }

    @Test
    fun `open ranges`() {
        assertEquals(Range(2015, now), intent("trap desde 2015").eraRange)
        assertEquals(Range(2015, now), intent("trap despues de 2015").eraRange)
        assertEquals(Range(1950, 1999), intent("rock antes de 2000").eraRange)
        assertEquals(Range(1950, 2010), intent("rock hasta 2010").eraRange)
    }

    @Test
    fun `a quantity is never read as a decade, and a request without years has no range`() {
        assertNull(intent("las 20 mejores de salsa").eraRange)
        assertEquals(20, intent("las 20 mejores de salsa").count)
        assertNull(intent("pon 50 canciones de salsa").eraRange)
        assertNull(intent("bossa nova").eraRange)
        assertNull(intent("musica para correr").eraRange)
        assertFalse(intent("musica para correr").checkable)
    }

    @Test
    fun `the search words name the era`() {
        assertEquals("2000s", Range(2004, 2010).searchWord())
        assertEquals("1970s", Range(1970, 1989).searchWord())
        assertEquals("2026", Range(2026, 2026).searchWord())
        assertTrue(MusicRequestIntent.styleQuery(intent("reggaeton cristiano del 2004 a 2010"), "reggaeton")!!.endsWith("2000s"))
    }

    @Test
    fun `a song with a known year outside the range never plays, an unknown one only fills`() {
        val years = mapOf("a" to 2005, "b" to 2012, "c" to 2008, "d" to null, "e" to 1999)
        val range = Range(2004, 2010)
        val few = MusicRequestEra.select(listOf("a", "b", "c", "d", "e"), { years[it] }, range, enoughVerified = 8)
        assertEquals(listOf("a", "c", "d"), few.kept) // sure first, unknown after, out never
        assertEquals(2, few.inRange)
        assertEquals(1, few.unknown)
        assertEquals(2, few.out)
        val plenty = MusicRequestEra.select(listOf("a", "b", "c", "d", "e"), { years[it] }, range, enoughVerified = 2)
        assertEquals(listOf("a", "c"), plenty.kept) // enough sure ones: the unknown stay out
    }

    @Test
    fun `the song the user named is never judged`() {
        val kept = MusicRequestEra.select(listOf("named", "x"), { 1980 }, Range(2004, 2010), isProtected = { it == "named" }, enoughVerified = 1)
        assertEquals(listOf("named"), kept.kept)
    }

    @Test
    fun `a compilation's year says nothing`() {
        assertNull(MusicRequestEra.trustworthyAlbumYear("Reggaeton Hits 2015", 2015))
        assertNull(MusicRequestEra.trustworthyAlbumYear("Los Mejores Éxitos", 2012))
        assertEquals(2005, MusicRequestEra.trustworthyAlbumYear("Barrio Fino", 2005))
        assertNull(MusicRequestEra.trustworthyAlbumYear("Barrio Fino", null))
    }
}
