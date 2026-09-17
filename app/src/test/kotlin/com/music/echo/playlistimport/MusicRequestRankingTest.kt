package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-09-17): reunir muchas candidatas y ELEGIR, con su gusto empujando pero sin destruir la
 * curación de la lista de origen.
 */
class MusicRequestRankingTest {

    private data class Track(val id: String, val artist: String, val taste: Double = 0.0)

    private val AVOID = -1_000_000.0

    private fun pick(list: List<Track>, target: Int) = MusicRequestRanking.pick(
        candidates = list,
        target = target,
        artistOf = { it.artist },
        tasteOf = { it.taste },
        avoidScore = AVOID,
    ).map { it.id }

    @Test
    fun `with no taste profile the source order is kept`() {
        val list = (1..6).map { Track("s$it", "artista$it") }
        assertEquals(listOf("s1", "s2", "s3"), pick(list, 3))
    }

    @Test
    fun `a favourite is pulled up but does not take over`() {
        val list = (1..20).map { Track("s$it", "artista$it", taste = if (it == 12) 1.5 else 0.0) }
        val out = pick(list, 5)
        // Adelanta, porque es suyo...
        assertTrue(out.contains("s12"))
        // ...pero la cabeza de la lista sigue siendo la cabeza: no se convierte en el número uno.
        assertEquals("s1", out.first())
    }

    @Test
    fun `disliked candidates are dropped`() {
        val list = listOf(
            Track("bueno1", "a"), Track("marcado", "b", taste = AVOID), Track("bueno2", "c"),
        )
        assertEquals(listOf("bueno1", "bueno2"), pick(list, 5))
    }

    @Test
    fun `everything disliked still returns something`() {
        // Nunca un resultado vacío por filtrar de más: es peor que una canción que no le guste.
        val list = (1..3).map { Track("s$it", "a$it", taste = AVOID) }
        assertEquals(3, pick(list, 5).size)
    }

    @Test
    fun `no two songs in a row by the same artist`() {
        val list = listOf(
            Track("a1", "Ana"), Track("a2", "Ana"), Track("a3", "Ana"),
            Track("b1", "Beto"), Track("c1", "Carla"),
        )
        val out = MusicRequestRanking.pick(
            candidates = list, target = 5,
            artistOf = { it.artist }, tasteOf = { it.taste }, avoidScore = AVOID,
        )
        out.zipWithNext().forEach { (x, y) -> assertTrue(x.artist != y.artist) }
    }

    @Test
    fun `nothing is lost when the artist cannot be separated`() {
        val list = (1..4).map { Track("s$it", "Ana") }
        assertEquals(4, pick(list, 10).size)
    }

    @Test
    fun `unknown artists may follow each other`() {
        val list = listOf(Track("s1", ""), Track("s2", ""), Track("s3", ""))
        assertEquals(listOf("s1", "s2", "s3"), pick(list, 3))
    }

    @Test
    fun `the target is respected and an empty pool is empty`() {
        assertEquals(2, pick((1..30).map { Track("s$it", "a$it") }, 2).size)
        assertTrue(pick(emptyList(), 10).isEmpty())
    }
}
