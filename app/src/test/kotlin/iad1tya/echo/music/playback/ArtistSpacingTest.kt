package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [ArtistSpacing] is the pure core of RadioQueueShaping.spacedByArtist. Without a lead-in it must behave exactly
 * as before (window of 2, primary artist, take-the-head fallback, nothing dropped); the new part is the lead-in:
 * the artists of the songs the batch FOLLOWS, so the seam between two batches is spaced like the inside of one.
 */
class ArtistSpacingTest {
    private fun ids(items: List<Pair<String, String?>>, leading: List<String> = emptyList()) =
        ArtistSpacing.space(items, { it.second }, leading).map { it.first }

    @Test
    fun `without a lead-in the old behaviour is kept`() {
        val two = listOf("a" to "x", "b" to "x")
        assertSame(two, ArtistSpacing.space(two, { it.second }))
        assertEquals(listOf("a1", "b1", "a2", "a3"), ids(listOf("a1" to "a", "a2" to "a", "a3" to "a", "b1" to "b")))
        assertEquals(listOf("a1", "b1", "a2", "b2"), ids(listOf("a1" to "a", "a2" to "a", "b1" to "b", "b2" to "b")))
        assertEquals(listOf("a1", "a2", "a3"), ids(listOf("a1" to "a", "a2" to "a", "a3" to "a")))
    }

    @Test
    fun `a batch no longer opens with the artist of the song that is playing`() {
        // YouTube's radio puts the seed's own artist first; the seed song (artist "a") is what plays now.
        val batch = listOf("a1" to "a", "a2" to "a", "b1" to "b", "c1" to "c", "a3" to "a")
        assertEquals(listOf("b1", "c1", "a1", "a2", "a3"), ids(batch, listOf("a")))
    }

    @Test
    fun `the lead-in covers two songs and goes stale after two placements`() {
        val batch = listOf("a1" to "a", "b1" to "b", "c1" to "c", "d1" to "d")
        // c1 first (a and b are in the lead-in); once c1 is placed the window is [b, c], so a1 is eligible again
        assertEquals(listOf("c1", "a1", "b1", "d1"), ids(batch, listOf("a", "b")))
        // only the last WINDOW lead-in artists count
        assertEquals(listOf("a1", "b1", "c1", "d1"), ids(batch, listOf("a", "b", "z", "y")))
    }

    @Test
    fun `lead-in is lowercase-normalised, blank ones ignored, a short batch is still spaced`() {
        assertEquals(listOf("b", "a"), ids(listOf("a" to "x", "b" to "y"), listOf("  X ")))
        assertEquals(listOf("a", "b"), ids(listOf("a" to "x", "b" to "x"), listOf("", "  ")))
        assertEquals(listOf("only"), ids(listOf("only" to "x"), listOf("x")))
    }

    @Test
    fun `nothing is ever dropped or duplicated and an all-same batch keeps its order`() {
        val same = listOf("a1" to "a", "a2" to "a", "a3" to "a")
        assertEquals(listOf("a1", "a2", "a3"), ids(same, listOf("a")))
        val rnd = java.util.Random(7)
        repeat(50) {
            val items = (0 until 12).map { "i$it" to listOf("a", "b", "c", null)[rnd.nextInt(4)] }
            val out = ids(items, listOf("a", "b"))
            assertEquals(items.map { it.first }.sorted(), out.sorted())
        }
    }

    @Test
    fun `an unknown artist is always eligible`() {
        val batch = listOf("a1" to "a", "u1" to null, "a2" to "a")
        assertEquals(listOf("u1", "a1", "a2"), ids(batch, listOf("a")))
    }
}
