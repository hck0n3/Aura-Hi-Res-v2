package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-09-17): *"necesito que sea lo más asertivo posible la función de pedir música, que no
 * improvise"*.
 *
 * La regla que se fija aquí: una lista solo se reproduce si su título DEMUESTRA que corresponde a lo
 * que él pidió. Si pidió una década, el título tiene que nombrarla; si ninguna candidata lo hace, no
 * se usa ninguna.
 */
class MusicRequestMatchTest {

    private val eighties = MusicRequestQuery.build("música de los 80s")
    private val eightiesEnglish = MusicRequestQuery.build("música de los 80s en inglés")
    private val study = MusicRequestQuery.build("música para estudiar")

    @Test
    fun `a playlist that does not name the decade is rejected`() {
        assertEquals(MusicRequestMatch.REJECT, MusicRequestMatch.score("Mi mix", eighties))
        assertEquals(MusicRequestMatch.REJECT, MusicRequestMatch.score("Para el carro", eighties))
        assertEquals(MusicRequestMatch.REJECT, MusicRequestMatch.score("Reggaetón 2024", eighties))
    }

    @Test
    fun `a playlist that names the decade passes`() {
        assertTrue(MusicRequestMatch.score("Éxitos de los 80", eighties) > 0)
        assertTrue(MusicRequestMatch.score("80s Hits", eighties) > 0)
        assertTrue(MusicRequestMatch.score("Lo mejor de los ochenta", eighties) > 0)
        assertTrue(MusicRequestMatch.score("1980s Greatest", eighties) > 0)
    }

    @Test
    fun `a number inside another word does not count as the decade`() {
        // "808 State" es un grupo de techno: su "80" no nombra una década.
        assertEquals(MusicRequestMatch.REJECT, MusicRequestMatch.score("808 State Essentials", eighties))
    }

    @Test
    fun `the requested language wins the tie`() {
        val english = MusicRequestMatch.score("80s Hits English", eightiesEnglish)
        val spanish = MusicRequestMatch.score("Éxitos de los 80", eightiesEnglish)
        assertTrue(english > spanish)
    }

    @Test
    fun `the best candidate is chosen and not the first`() {
        val titles = listOf("Mi mix", "Fiesta random", "Éxitos de los 80", "Rock 2020")
        assertEquals(2, MusicRequestMatch.bestIndex(titles, eighties))
    }

    @Test
    fun `nothing is chosen when nobody proves it`() {
        val titles = listOf("Mi mix", "Para el carro", "Reggaetón 2024")
        assertNull(MusicRequestMatch.bestIndex(titles, eighties))
    }

    @Test
    fun `without a decade a playlist still has to share words with the request`() {
        // "Mi mix" no pasa por llamarse "mix": tiene que compartir una palabra de CONTENIDO.
        assertNull(MusicRequestMatch.bestIndex(listOf("Fiesta latina", "Mi mix"), study))
        assertEquals(0, MusicRequestMatch.bestIndex(listOf("Música para estudiar y concentrarse"), study))
    }

    @Test
    fun `decade tokens cover digits and words`() {
        val tokens = MusicRequestMatch.decadeTokens("80")
        assertTrue(tokens.contains("80s"))
        assertTrue(tokens.contains("1980"))
        assertTrue(tokens.contains("ochenta"))
    }

    @Test
    fun `a blank title is never chosen`() {
        assertEquals(MusicRequestMatch.REJECT, MusicRequestMatch.score("   ", eighties))
    }

    /**
     * Ronda 9 (dueño): "bachata cristiana" ponía canciones seculares — una lista titulada solo
     * "Bachata" pasaba con la regla de "al menos una palabra en común", ignorando "cristiana" por
     * completo. Con dos familias reconocidas a la vez, el título tiene que demostrar las dos.
     */
    @Test
    fun `a playlist matching only one of two requested groups is rejected`() {
        val christianBachata = MusicRequestQuery.build("bachata cristiana")
        val groups = listOf(listOf("bachata"), listOf("cristiana", "cristiano", "gospel", "worship"))
        assertEquals(
            MusicRequestMatch.REJECT,
            MusicRequestMatch.score("Bachata Romántica 2024", christianBachata, groups),
        )
        assertTrue(
            MusicRequestMatch.score("Bachata Cristiana Mix", christianBachata, groups) > 0,
        )
    }

    @Test
    fun `fewer than two groups keeps the old single-word rule`() {
        val bachata = MusicRequestQuery.build("bachata")
        val groups = listOf(listOf("bachata"))
        assertTrue(MusicRequestMatch.score("Bachata Romántica 2024", bachata, groups) > 0)
    }
}
