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

    /**
     * Owner log 2026-10-05 11:55: a batch seeded at the true end of the list (no time to look genres up)
     * had 42 of 52 candidates with UNKNOWN genre, so only 8 followed the pattern.
     */
    @Test
    fun candidatesInheritTheLaneOfTheSeedThatBroughtThem() {
        val lanes = ContextPattern.inheritedLanes(
            seedLanes = listOf("cumbia", "pop"),
            pages = listOf(listOf("c1", "shared", "c2"), listOf("p1", "p2", "shared")),
        )
        assertEquals("cumbia", lanes["c1"])
        assertEquals("pop", lanes["p1"])
        // Same round-robin as the merge: position 1 of the cumbia page reaches "shared" before position 2 of pop.
        assertEquals("cumbia", lanes["shared"])
        assertEquals("cumbia", lanes["c2"])
    }

    @Test
    fun aLanelessSeedThatGotThereFirstKeepsTheCandidateLaneless() {
        val lanes = ContextPattern.inheritedLanes(
            seedLanes = listOf(null, "pop"),
            pages = listOf(listOf("x"), listOf("p1", "x")),
        )
        assertEquals(null, lanes["x"])
        assertEquals("pop", lanes["p1"])
    }

    @Test
    fun inheritedLanesPlaceUnknownsInsideThePatternWithoutDroppingAnything() {
        val inherited = mapOf("u1" to "cumbia", "u2" to "pop")
        val songs = listOf(
            Song("u1", null, "a"), Song("u2", null, "b"), Song("u3", null, "c"),
            Song("k1", "cumbia", "d"),
        )
        val out = ContextPattern.arrange(
            ranked = songs,
            shares = mapOf("cumbia" to 0.5, "pop" to 0.5),
            // A KNOWN lane always wins; the inherited one only fills an unknown.
            laneOf = { it.lane ?: inherited[it.id] },
            isContextArtist = { false },
            artistOf = { it.artist },
        )
        assertEquals(songs.size, out.size)
        assertEquals(setOf("u1", "u2", "k1"), out.take(3).map { it.id }.toSet())
        // The candidate with no lane at all and no inheritance still plays, after the pattern.
        assertEquals("u3", out.last().id)
    }
}
