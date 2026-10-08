package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fila 359 (dueño 2026-10-08): "que en pedir música no salgan instrumentales, a menos que pida karaoke". */
class MusicRequestInstrumentalTest {

    @Test
    fun `vocal-less versions are recognised by title, album or channel`() {
        assertTrue(MusicRequestInstrumental.isInstrumental("Te Busco (Instrumental)"))
        assertTrue(MusicRequestInstrumental.isInstrumental("Gasolina - Karaoke Version"))
        assertTrue(MusicRequestInstrumental.isInstrumental("Way Maker (Pista con letra)"))
        assertTrue(MusicRequestInstrumental.isInstrumental("Oceans [Pista]"))
        assertTrue(MusicRequestInstrumental.isInstrumental("Hello (In the Style of Adele)"))
        assertTrue(MusicRequestInstrumental.isInstrumental("Perfect", artists = listOf("Sing King")))
        assertTrue(MusicRequestInstrumental.isInstrumental("Shallow", album = "Karaoke Hits 2019"))
        assertTrue(MusicRequestInstrumental.isInstrumental("Dark Trap Type Beat"))
    }

    @Test
    fun `real songs that merely contain similar words are kept`() {
        assertFalse(MusicRequestInstrumental.isInstrumental("Te Busco"))
        assertFalse(MusicRequestInstrumental.isInstrumental("La Pista"))
        assertFalse(MusicRequestInstrumental.isInstrumental("Beat It"))
        assertFalse(MusicRequestInstrumental.isInstrumental("Pistas de baile"))
        assertFalse(MusicRequestInstrumental.isInstrumental("Karaokee")) // not the whole word
        assertFalse(MusicRequestInstrumental.isInstrumental("Instrumento de tu paz"))
    }

    @Test
    fun `asking for karaoke or instrumental turns the filter off`() {
        assertTrue(MusicRequestInstrumental.wantsInstrumental("karaoke de Marco Antonio Solís"))
        assertTrue(MusicRequestInstrumental.wantsInstrumental("música instrumental para estudiar"))
        assertTrue(MusicRequestInstrumental.wantsInstrumental("pistas para cantar en la iglesia"))
        assertFalse(MusicRequestInstrumental.wantsInstrumental("cumbia cristiana"))
        assertFalse(MusicRequestInstrumental.wantsInstrumental("música para la pista de baile"))
        assertFalse(MusicRequestInstrumental.wantsInstrumental("algo con buen beat"))
    }
}
