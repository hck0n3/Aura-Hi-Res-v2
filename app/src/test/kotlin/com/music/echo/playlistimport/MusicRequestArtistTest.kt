package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 12 (dueño, 2026-10-05): *"pedí hip hop de Eminem y solo la primera canción fue hip hop de
 * Eminem y lo demás solo era de otro cantante"* y *"pedí afro gospel y me tiró cualquier cosa menos lo
 * que pedí"*. Sus dos peticiones exactas son los primeros casos.
 */
class MusicRequestArtistTest {

    @Test
    fun `hip hop de eminem leaves eminem and the search confirms it is the artist`() {
        val residual = MusicRequestQuery.residualBeyondCategory("hip hop de eminem")
        assertEquals("eminem", residual)
        val primaries = listOf("Eminem", "Eminem", "Dr. Dre", "Eminem")
        assertEquals("Eminem", MusicRequestArtist.confirm(residual, primaries))
    }

    @Test
    fun `afro gospel is two genres and leaves nothing to pin`() {
        val parsed = MusicRequestQuery.build("afro gospel")
        assertTrue(parsed.preferPlaylists)
        assertEquals("", MusicRequestQuery.residualBeyondCategory("afro gospel"))
    }

    @Test
    fun `a single coincidental artist match is not enough`() {
        // "rock alternativo": aunque exista un grupo llamado así, el buscador no lo devuelve dos veces.
        assertNull(MusicRequestArtist.confirm("alternativo", listOf("Alternativo", "Soda Stereo", "Maná")))
    }

    @Test
    fun `names compare without accents case punctuation or a leading the`() {
        assertEquals("Bad Bunny", MusicRequestArtist.confirm("bad bunny", listOf("Bad Bunny", "BAD BUNNY")))
        assertEquals("The Weeknd", MusicRequestArtist.confirm("weeknd", listOf("The Weeknd", "The Weeknd")))
        assertEquals("Maná", MusicRequestArtist.confirm("mana", listOf("Maná", "Maná")))
        assertTrue(MusicRequestArtist.sameName("Guns N' Roses", "guns n roses"))
    }

    @Test
    fun `a song name plus artist is not confirmed as an artist`() {
        // "redimi2 flipando" sigue siendo una CANCIÓN concreta (fila 312): el resto no es solo el artista.
        assertNull(MusicRequestArtist.confirm("redimi2 flipando", listOf("Redimi2", "Redimi2", "Redimi2")))
    }

    @Test
    fun `unknown primaries never count`() {
        assertNull(MusicRequestArtist.confirm("eminem", listOf(null, "", "Eminem")))
        assertFalse(MusicRequestArtist.sameName(null, "eminem"))
    }

    @Test
    fun `residual words keep only content words`() {
        assertEquals(listOf("argentino"), MusicRequestQuery.residualWords("argentino"))
        assertEquals(listOf("puerto", "rico"), MusicRequestQuery.residualWords("puerto rico"))
        assertEquals(emptyList<String>(), MusicRequestQuery.residualWords(""))
    }

    @Test
    fun `afro gospel needs a category that proves both families`() {
        val catalogo = listOf("Gospel", "Cristiana", "Afrobeats", "Afro Gospel", "Fiesta")
        val prompt = "afro gospel"
        val parsed = MusicRequestQuery.build(prompt)
        assertEquals(catalogo.indexOf("Afro Gospel"), MusicRequestMoods.pickCategory(catalogo, prompt, parsed))
        // Sin una categoría que nombre las dos, ninguna: "Gospel" a secas era la improvisación.
        assertNull(MusicRequestMoods.pickCategory(listOf("Gospel", "Afrobeats"), prompt, parsed))
    }

    @Test
    fun `a qualifier word must be named by the category`() {
        val prompt = "rock argentino"
        val parsed = MusicRequestQuery.build(prompt)
        val qualifiers = MusicRequestQuery.residualWords(MusicRequestQuery.residualBeyondCategory(prompt))
        assertEquals(listOf("argentino"), qualifiers)
        val catalogo = listOf("Rock", "Rock argentino", "Pop")
        assertEquals(1, MusicRequestMoods.pickCategory(catalogo, prompt, parsed, qualifiers))
        assertNull(MusicRequestMoods.pickCategory(listOf("Rock", "Pop"), prompt, parsed, qualifiers))
    }

    @Test
    fun `a playlist title must prove the qualifier too`() {
        val prompt = "afro gospel"
        val parsed = MusicRequestQuery.build(prompt)
        val groups = MusicRequestMoods.conceptGroupsFor(prompt, parsed)
        assertEquals(MusicRequestMatch.REJECT, MusicRequestMatch.score("Gospel Hits 2026", parsed, groups))
        assertTrue(MusicRequestMatch.score("Afro Gospel Praise", parsed, groups) > 0)
        assertTrue(MusicRequestMatch.score("African Gospel Music", parsed, groups) > 0)
    }
}
