package iad1tya.echo.music.reco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner (2026-10-04): "cuando termina la playlist viene una canción que nada que ver y luego viene cumbia"
 * — a one-genre list must continue in that genre; a mixed list must repeat its own mix.
 */
class ContextPatternTest {

    private data class Song(val id: String, val lane: String?, val artist: String, val ctxArtist: Boolean = false)

    private fun arrange(songs: List<Song>, shares: Map<String, Double>) =
        ContextPattern.arrange(
            ranked = songs,
            shares = shares,
            laneOf = { it.lane },
            isContextArtist = { it.ctxArtist },
            artistOf = { it.artist },
        )

    @Test
    fun sequenceFollowsTheSharesAndInterleaves() {
        val seq = ContextPattern.sequence(mapOf("cumbia" to 0.5, "reggaeton" to 0.3, "pop" to 0.2), 10)
        assertEquals(5, seq.count { it == "cumbia" })
        assertEquals(3, seq.count { it == "reggaeton" })
        assertEquals(2, seq.count { it == "pop" })
        assertEquals("cumbia", seq.first())
        // Interleaved, never in blocks: no lane plays three times in a row.
        assertTrue(seq.windowed(3).none { w -> w.distinct().size == 1 })
    }

    @Test
    fun oneGenreListIsOneGenreSequence() {
        assertEquals(List(6) { "cumbia" }, ContextPattern.sequence(mapOf("cumbia" to 1.0), 6))
    }

    @Test
    fun noiseLaneDoesNotEnterThePattern() {
        val seq = ContextPattern.sequence(mapOf("cumbia" to 0.95, "pop" to 0.05), 20)
        assertTrue(seq.all { it == "cumbia" })
    }

    @Test
    fun unknownSongAtRelatednessRankZeroNoLongerOpensTheContinuation() {
        // The exact report: YouTube's most related pick is something we know nothing about, cumbia follows.
        val ranked = listOf(
            Song("x", lane = null, artist = "desconocido"),
            Song("c1", lane = "cumbia", artist = "a"),
            Song("c2", lane = "cumbia", artist = "b"),
        )
        val out = arrange(ranked, mapOf("cumbia" to 1.0))
        assertEquals(listOf("c1", "c2", "x"), out.map { it.id })
    }

    @Test
    fun nothingIsEverDropped() {
        val ranked = listOf(
            Song("x", null, "u1"),
            Song("p", "pop", "p1"),
            Song("c", "cumbia", "c1"),
            Song("r", "rock", "r1"), // known off-context, sunk by the caller
        )
        val out = arrange(ranked, mapOf("cumbia" to 1.0))
        assertEquals(ranked.map { it.id }.toSet(), out.map { it.id }.toSet())
        assertEquals(ranked.size, out.size)
        assertEquals("c", out.first().id)
    }

    @Test
    fun mixedListRepeatsItsOwnMix() {
        val ranked = buildList {
            repeat(6) { add(Song("c$it", "cumbia", "ca$it")) }
            repeat(6) { add(Song("r$it", "reggaeton", "ra$it")) }
            repeat(6) { add(Song("p$it", "pop", "pa$it")) }
        }
        val out = arrange(ranked, mapOf("cumbia" to 0.5, "reggaeton" to 0.3, "pop" to 0.2))
        val firstTen = out.take(10).map { it.lane }
        assertEquals(5, firstTen.count { it == "cumbia" })
        assertEquals(3, firstTen.count { it == "reggaeton" })
        assertEquals(2, firstTen.count { it == "pop" })
    }

    @Test
    fun contextArtistFillsAnEmptyLaneSlotBeforeUnknowns() {
        val ranked = listOf(
            Song("u", null, "nadie"),
            Song("own", null, "artista de la lista", ctxArtist = true),
            Song("c", "cumbia", "otro"),
        )
        val out = arrange(ranked, mapOf("cumbia" to 1.0))
        assertEquals(listOf("c", "own", "u"), out.map { it.id })
    }

    @Test
    fun sameArtistIsSpacedWithinTheLane() {
        val ranked = listOf(
            Song("a1", "cumbia", "A"),
            Song("a2", "cumbia", "A"),
            Song("b1", "cumbia", "B"),
        )
        val out = arrange(ranked, mapOf("cumbia" to 1.0))
        assertEquals(listOf("a1", "b1", "a2"), out.map { it.id })
    }

    @Test
    fun withoutSharesTheOrderIsUntouched() {
        val ranked = listOf(Song("1", null, "a"), Song("2", "pop", "b"))
        assertEquals(ranked, arrange(ranked, emptyMap()))
    }

    @Test
    fun studyArtistsPicksTheMostFrequentFirst() {
        val names = listOf("Rara", "Los Ángeles Azules", "Celso Piña", "los ángeles azules", "Celso Piña", "Los Ángeles Azules", " ")
        assertEquals(listOf("Los Ángeles Azules", "Celso Piña"), ContextPattern.studyArtists(names, 2))
        assertEquals(listOf("Los Ángeles Azules", "Celso Piña", "Rara"), ContextPattern.studyArtists(names, 10))
    }

    @Test
    fun seedLanesGiveTheDominantGenreTheFirstSeed() {
        val seeds = ContextPattern.seedLanes(mapOf("pop" to 0.25, "cumbia" to 0.75), 4)
        assertEquals("cumbia", seeds.first())
        assertEquals(3, seeds.count { it == "cumbia" })
    }
}
