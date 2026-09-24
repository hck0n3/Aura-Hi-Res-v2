package iad1tya.echo.music.playlistimport

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 9 (dueño): "si pido una canción o artista específico, que la cola que sigue no sean 20-25
 * canciones con el mismo nombre — que sea el género relacionado, más del mismo artista, o una
 * combinación". [AiPlaylistGenerator.dedupKey] evita que distintas subidas de LA MISMA canción
 * cuenten como candidatas distintas; [AiPlaylistGenerator.dominantArtist] detecta cuándo el pool que
 * armó la escalera es, en la práctica, de un solo artista, para completarlo con más de ese artista
 * en vez de dejar que el ranking repita lo poco que hay.
 */
class AiPlaylistGeneratorTest {

    private fun song(id: String, title: String, artist: String) =
        SongItem(id = id, title = title, artists = listOf(Artist(name = artist, id = null)), thumbnail = "")

    @Test
    fun `different uploads of the same song by the same artist share a dedup key`() {
        val a = AiPlaylistGenerator.dedupKey("Envolver", "Anitta")
        val b = AiPlaylistGenerator.dedupKey("Envolver (Official Video)", "Anitta")
        val c = AiPlaylistGenerator.dedupKey("ENVOLVER [Lyrics]", "Anitta")
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun `a genuinely different song or artist gets a different dedup key`() {
        val envolver = AiPlaylistGenerator.dedupKey("Envolver", "Anitta")
        val otherSong = AiPlaylistGenerator.dedupKey("Girl From Rio", "Anitta")
        val otherArtist = AiPlaylistGenerator.dedupKey("Envolver", "Karol G")
        assertTrue(envolver != otherSong)
        assertTrue(envolver != otherArtist)
    }

    @Test
    fun `an artist behind at least half the pool is dominant`() {
        val items = listOf(
            song("1", "Song A", "Bad Bunny"),
            song("2", "Song B", "Bad Bunny"),
            song("3", "Song C", "Karol G"),
        )
        assertEquals("Bad Bunny", AiPlaylistGenerator.dominantArtist(items))
    }

    @Test
    fun `no dominant artist when the pool is evenly mixed`() {
        val items = listOf(
            song("1", "Song A", "Bad Bunny"),
            song("2", "Song B", "Karol G"),
            song("3", "Song C", "Feid"),
        )
        assertNull(AiPlaylistGenerator.dominantArtist(items))
    }

    @Test
    fun `an empty pool has no dominant artist`() {
        assertNull(AiPlaylistGenerator.dominantArtist(emptyList()))
    }

    /**
     * Ronda 9 (dueño): "pedí death metal más el nombre de una canción y de un artista, y reprodujo
     * lo que quiso". [AiPlaylistGenerator.bestSpecificMatch] es lo que decide si un candidato del
     * buscador demuestra de verdad la parte específica de la petición.
     */
    @Test
    fun `a candidate whose title and artist match the residual wins`() {
        val candidates = listOf(
            song("1", "Angel of Death", "Slayer"),
            song("2", "Raining Blood", "Slayer"),
            song("3", "Painkiller", "Judas Priest"),
        )
        val best = AiPlaylistGenerator.bestSpecificMatch("raining blood slayer", candidates)
        assertEquals("2", best?.id)
    }

    @Test
    fun `no candidate matching the residual returns null`() {
        val candidates = listOf(
            song("1", "Angel of Death", "Slayer"),
            song("2", "Painkiller", "Judas Priest"),
        )
        assertNull(AiPlaylistGenerator.bestSpecificMatch("raining blood slayer", candidates))
    }

    @Test
    fun `a blank residual matches nothing`() {
        val candidates = listOf(song("1", "Angel of Death", "Slayer"))
        assertNull(AiPlaylistGenerator.bestSpecificMatch("", candidates))
    }

    /**
     * Auditoría del algoritmo (ronda 9, dueño: "vela que nada sea placebo"): el residuo llega con
     * puntuación pegada ("queen, bohemian rhapsody") y la comparación antigua era por subcadena
     * cruda, no por límite de palabra — dos huecos que podían dar falso negativo o falso positivo.
     */
    @Test
    fun `punctuation stuck to the residual does not block a real match`() {
        val candidates = listOf(song("1", "Bohemian Rhapsody", "Queen"))
        val best = AiPlaylistGenerator.bestSpecificMatch("queen, bohemian rhapsody", candidates)
        assertEquals("1", best?.id)
    }

    @Test
    fun `a short residual word does not match as a substring of another word`() {
        // "amor" no debe matchear dentro de "amoroso" — solo como palabra completa.
        val candidates = listOf(song("1", "Estilo Amoroso", "Artista Random"))
        assertNull(AiPlaylistGenerator.bestSpecificMatch("amor", candidates))
    }
}
