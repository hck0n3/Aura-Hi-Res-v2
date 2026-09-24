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
}
