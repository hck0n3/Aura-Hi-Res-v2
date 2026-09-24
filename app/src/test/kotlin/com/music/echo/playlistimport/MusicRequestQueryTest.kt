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
        // "salsa" ronda 7: ahora SÍ prefiere listas (es un género reconocido) — ver
        // `a bare genre request prefers playlists` más abajo. Lo que este test sigue
        // comprobando es que la query en sí no se toca cuando no hay década.
        val p = MusicRequestQuery.build("salsa de Puerto Rico")
        assertNull(p.decade)
        assertEquals("salsa de puerto rico", p.query)
    }

    @Test
    fun `written decades are understood`() {
        assertEquals("80", MusicRequestQuery.build("música de los ochentas").decade)
        assertEquals("90", MusicRequestQuery.build("lo mejor de los noventa").decade)
        assertEquals("70", MusicRequestQuery.build("seventies rock").decade)
    }

    /**
     * Dueño (ronda 6): "reggae de los 90" perdía "reggae" — la petición se reemplazaba entera por
     * "exitos de los 90". El género que pidió junto con la década ahora se conserva.
     */
    @Test
    fun `a genre named alongside a decade is not discarded`() {
        val p = MusicRequestQuery.build("reggae de los 90")
        assertEquals("90", p.decade)
        assertEquals("reggae exitos de los 90", p.query)
        assertTrue(p.preferPlaylists)
    }

    @Test
    fun `a genre named alongside a decade in english keeps the english suffix`() {
        val p = MusicRequestQuery.build("reggae de los 90 en ingles")
        assertEquals("90", p.decade)
        assertEquals("en", p.language)
        assertEquals("reggae 90s hits english", p.query)
    }

    @Test
    fun `a written decade word does not swallow the genre that follows it`() {
        assertEquals("rock 70s hits english", MusicRequestQuery.build("seventies rock in english").query)
    }

    @Test
    fun `a pure decade request with no genre is unchanged`() {
        assertEquals("exitos de los 80", MusicRequestQuery.build("música de los ochentas").query)
    }

    /** Ronda 6 (dueño): "lo que suena ahora" / tendencias debe preferir listas (los charts reales). */
    @Test
    fun `a trending request prefers playlists and is flagged`() {
        val p = MusicRequestQuery.build("lo que suena ahora")
        assertTrue(p.trending)
        assertTrue(p.preferPlaylists)
    }

    @Test
    fun `a plain request is not flagged as trending`() {
        assertFalse(MusicRequestQuery.build("salsa de Puerto Rico").trending)
    }

    /**
     * Ronda 7 (dueño): un género suelto ("reggaeton", "bossa nova") debe preferir listas para que
     * pase por las categorías oficiales de YouTube Music en vez de caer directo a una búsqueda cruda.
     */
    @Test
    fun `a bare genre request prefers playlists`() {
        assertTrue(MusicRequestQuery.build("reggaeton").preferPlaylists)
        assertTrue(MusicRequestQuery.build("bossanova").preferPlaylists)
        assertTrue(MusicRequestQuery.build("ponme salsa").preferPlaylists)
    }

    @Test
    fun `an artist name with no genre word does not prefer playlists`() {
        assertFalse(MusicRequestQuery.build("canciones de Bad Bunny").preferPlaylists)
    }

    /**
     * Ronda 9 (dueño, auditoría del algoritmo): "música cristiana"/"quiero alabanza"/"worship" y
     * "música romántica"/"canciones tristes" no activaban preferPlaylists aunque MusicRequestMoods
     * sí reconoce esas familias — la petición se saltaba las categorías oficiales por completo.
     */
    @Test
    fun `christian theme words alone prefer playlists`() {
        assertTrue(MusicRequestQuery.build("musica cristiana").preferPlaylists)
        assertTrue(MusicRequestQuery.build("quiero alabanza").preferPlaylists)
        assertTrue(MusicRequestQuery.build("worship").preferPlaylists)
    }

    @Test
    fun `romance and sad mood words alone prefer playlists`() {
        assertTrue(MusicRequestQuery.build("musica romantica").preferPlaylists)
        assertTrue(MusicRequestQuery.build("canciones tristes").preferPlaylists)
        assertTrue(MusicRequestQuery.build("algo para el desamor").preferPlaylists)
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

    /**
     * Ronda 9 (dueño): "pedí death metal más el nombre de una canción y de un artista, y reprodujo
     * lo que quiso — quiero que entienda cuando soy específico". Lo que sobra tras quitar el género
     * es lo que se busca y antepone en [AiPlaylistGenerator]; si no sobra nada, la petición es pura.
     */
    @Test
    fun `residualBeyondCategory finds the specific song and artist named alongside a genre`() {
        assertEquals(
            "raining blood slayer",
            MusicRequestQuery.residualBeyondCategory("death metal raining blood slayer"),
        )
        assertEquals("", MusicRequestQuery.residualBeyondCategory("death metal"))
        assertEquals("", MusicRequestQuery.residualBeyondCategory("musica para estudiar"))
    }

    /**
     * Ronda 9 (dueño): "pedí trap cristiano en inglés y me puso trap cristiano en español — que
     * respete lo que pido siempre". Sin década, "en ingles" se mandaba tal cual dentro de la consulta
     * — el buscador no lo lee como un filtro, así que no sesgaba el idioma del resultado en absoluto.
     */
    @Test
    fun `a genre plus theme request in english strips the raw hint and adds a search-friendly suffix`() {
        val p = MusicRequestQuery.build("trap cristiano en ingles")
        assertNull(p.decade)
        assertEquals("en", p.language)
        assertEquals("trap cristiano english", p.query)
    }

    @Test
    fun `a no-decade request in spanish normalizes whatever hint phrasing was used`() {
        val p = MusicRequestQuery.build("bachata in spanish")
        assertEquals("es", p.language)
        assertEquals("bachata en espanol", p.query)
    }

    @Test
    fun `the query never exceeds the practical search length`() {
        val long = "quiero música de los 80s en inglés " + "muy larga ".repeat(20)
        assertTrue(MusicRequestQuery.build(long).query.length <= 80)
    }
}
