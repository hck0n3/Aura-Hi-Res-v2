package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-09-17): *"le pedí música de los 80s y nunca funcionó, luego le pedí música de los 80s
 * en inglés y no funcionó […] necesito que pueda entender el lenguaje natural"*.
 *
 * Sus dos peticiones exactas son los dos primeros casos.
 */
class MusicRequestQueryTest {

    @Test
    fun `musica de los 80s becomes a query the search engine rewards`() {
        val p = MusicRequestQuery.build("música de los 80s")
        assertEquals("80", p.decade)
        assertNull(p.language)
        assertEquals("exitos de los 80", p.query)
        assertTrue(p.preferPlaylists)
    }

    @Test
    fun `musica de los 80s en ingles asks for english hits`() {
        val p = MusicRequestQuery.build("música de los 80s en inglés")
        assertEquals("80", p.decade)
        assertEquals("en", p.language)
        assertEquals("80s hits english", p.query)
        assertTrue(p.preferPlaylists)
    }

    @Test
    fun `lead-in filler is stripped`() {
        assertEquals("rock argentino", MusicRequestQuery.build("ponme rock argentino").query)
        assertEquals("rock argentino", MusicRequestQuery.build("quiero algo de rock argentino").query)
        assertEquals("bad bunny", MusicRequestQuery.build("pon música de Bad Bunny").query)
    }

    @Test
    fun `a moment prefers a playlist even with no decade`() {
        val p = MusicRequestQuery.build("música para estudiar")
        assertNull(p.decade)
        assertTrue(p.preferPlaylists)
    }

    @Test
    fun `a plain request is passed through unchanged`() {
        val p = MusicRequestQuery.build("salsa de Puerto Rico")
        assertNull(p.decade)
        assertFalse(p.preferPlaylists)
        assertEquals("salsa de puerto rico", p.query)
    }

    @Test
    fun `written decades are understood`() {
        assertEquals("80", MusicRequestQuery.build("música de los ochentas").decade)
        assertEquals("90", MusicRequestQuery.build("lo mejor de los noventa").decade)
        assertEquals("70", MusicRequestQuery.build("seventies rock").decade)
    }

    @Test
    fun `a number that is not a decade is not a decade`() {
        // "50 Cent" no es una petición de los años 50: hace falta el artículo o la ese.
        assertNull(MusicRequestQuery.build("música de 50 Cent").decade)
        assertNull(MusicRequestQuery.build("el top 10 de rock").decade)
    }

    @Test
    fun `the 2000s work too`() {
        val p = MusicRequestQuery.build("éxitos de los 2000")
        assertEquals("2000", p.decade)
        assertEquals("exitos de los 2000", p.query)
    }

    @Test
    fun `a blank prompt yields a blank query`() {
        val p = MusicRequestQuery.build("   ")
        assertEquals("", p.query)
        assertFalse(p.preferPlaylists)
    }

    @Test
    fun `the query never exceeds the practical search length`() {
        val long = "quiero música de los 80s en inglés " + "muy larga ".repeat(20)
        assertTrue(MusicRequestQuery.build(long).query.length <= 80)
    }
}
