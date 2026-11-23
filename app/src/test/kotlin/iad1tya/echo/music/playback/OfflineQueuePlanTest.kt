package iad1tya.echo.music.playback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner (2.0.69-beta2): «el modo sin conexión no reemplaza la cola por la cola que está disponible sin conexión». The queue
 * decisions of the switch, the top-up at the end and the way back (row 705).
 */
class OfflineQueuePlanTest {
    private fun c(id: String, album: String? = null, vararg artists: String) =
        OfflineQueuePlan.Candidate(id, album, artists.toSet())

    private fun fill(
        ordered: List<OfflineQueuePlan.Candidate>,
        count: Int = OfflineQueuePlan.BATCH,
        ahead: Set<String> = emptySet(),
        inQueue: Map<String, Int> = emptyMap(),
        heard: Set<String> = emptySet(),
        playable: (String) -> Boolean = { true },
        lastResort: Boolean = false,
    ) = OfflineQueuePlan.fill(ordered, count, ahead, inQueue, { it in heard }, playable, lastResort)

    @Test
    fun `same album first, then same artist, then the rest, each song once`() {
        val candidates = listOf(
            c("r1"), c("a1", "ALB", "X"), c("s1", null, "ART"), c("r2"), c("a2", "ALB"), c("s2", "OTHER", "ART"), c("a1", "ALB"),
        )
        repeat(20) { seed ->
            val ordered = OfflineQueuePlan.contextOrder(candidates, "ALB", setOf("ART"), Random(seed)).map { it.id }
            assertEquals(6, ordered.size)
            assertEquals(setOf("a1", "a2"), ordered.take(2).toSet())
            assertEquals(setOf("s1", "s2"), ordered.subList(2, 4).toSet())
            assertEquals(setOf("r1", "r2"), ordered.drop(4).toSet())
        }
    }

    @Test
    fun `no context is just the whole library shuffled`() {
        val ordered = OfflineQueuePlan.contextOrder(listOf(c("a"), c("b"), c("c")), null, emptySet(), Random(1))
        assertEquals(setOf("a", "b", "c"), ordered.map { it.id }.toSet())
    }

    @Test
    fun `never a song that needs the network`() {
        val picked = fill(listOf(c("net1"), c("off1"), c("net2"), c("off2")), playable = { it.startsWith("off") })
        assertEquals(listOf("off1", "off2"), picked)
    }

    @Test
    fun `fresh songs first, in order, never what is already ahead`() {
        val picked = fill(
            listOf(c("cur"), c("next"), c("heard"), c("queued"), c("f1"), c("f2")),
            ahead = setOf("cur", "next"),
            inQueue = mapOf("queued" to 0, "cur" to 3, "next" to 4),
            heard = setOf("heard"),
            count = 2,
        )
        assertEquals(listOf("f1", "f2"), picked)
    }

    @Test
    fun `with nothing new the library loops, what played longest ago first`() {
        val picked = fill(
            listOf(c("b"), c("a"), c("c"), c("cur")),
            ahead = setOf("cur"),
            inQueue = mapOf("a" to 1, "b" to 5, "c" to 3, "cur" to 6),
            heard = setOf("a", "b", "c", "cur"),
        )
        assertEquals(listOf("a", "c", "b"), picked)
    }

    @Test
    fun `fresh songs come before the loop`() {
        val picked = fill(listOf(c("old"), c("new")), inQueue = mapOf("old" to 0), heard = setOf("old"))
        assertEquals(listOf("new", "old"), picked)
    }

    @Test
    fun `only at the end of the queue the one song left repeats rather than the music stopping`() {
        val library = listOf(c("only"))
        assertEquals(emptyList<String>(), fill(library, ahead = setOf("only")))
        assertEquals(listOf("only"), fill(library, ahead = setOf("only"), lastResort = true))
        assertEquals(emptyList<String>(), fill(library, ahead = setOf("only"), lastResort = true, playable = { false }))
    }

    @Test
    fun `the count is respected and the playability check is lazy`() {
        val asked = mutableListOf<String>()
        val library = (1..50).map { c("s$it") }
        val picked = fill(library, count = 3, playable = { asked += it; true })
        assertEquals(listOf("s1", "s2", "s3"), picked)
        assertEquals(listOf("s1", "s2", "s3"), asked)
        assertEquals(emptyList<String>(), fill(library, count = 0))
    }

    // ---- the way back ------------------------------------------------------------------------------------

    /** Queue: [p0, ANCHOR(1)] + owned [k1, f1, f2] at 2..4, linear. */
    private val owned = listOf("k1", "f1", "f2")

    private fun played(current: Int, anchorPlayed: Boolean = true, before: Set<Int> = (0 until current).toSet()) =
        OfflineQueuePlan.playedDuringSession(owned, 2, 1, "ANCHOR", anchorPlayed, current, before)

    @Test
    fun `still on the anchor nothing of the offline queue played`() {
        assertEquals(setOf("ANCHOR"), played(1, anchorPlayed = false))
    }

    @Test
    fun `inside the offline queue what came before and the current one played`() {
        assertEquals(setOf("ANCHOR", "k1", "f1"), played(3))
        assertEquals(setOf("k1", "f1"), played(3, anchorPlayed = false))
    }

    @Test
    fun `shuffle reads play order, not index order`() {
        assertEquals(setOf("ANCHOR", "f2", "f1"), played(3, before = setOf(0, 1, 4)))
    }

    @Test
    fun `back before the anchor the user moved and nothing is restored`() {
        assertNull(played(0))
    }

    @Test
    fun `the songs that did not play come back in their order`() {
        val original = listOf("failed", "n1", "k1", "n2")
        val back = OfflineQueuePlan.toRestore(original, setOf("ANCHOR", "k1"))
        assertEquals(listOf("failed", "n1", "n2"), back.map { original[it] })
        assertTrue(OfflineQueuePlan.toRestore(listOf("k1"), setOf("k1")).isEmpty())
    }
}
