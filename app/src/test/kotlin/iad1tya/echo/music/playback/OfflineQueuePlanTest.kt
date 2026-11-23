package iad1tya.echo.music.playback

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner (2.0.69-beta2): «el modo sin conexión no reemplaza la cola por la cola que está disponible sin conexión». The queue
 * decisions of the switch, the top-up at the end and the way back (rows 705, 717).
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

    // ---- the way back (row 717) ------------------------------------------------------------------------------

    private val P = OfflineQueuePlan.Role.PREFIX
    private val K = OfflineQueuePlan.Role.KEPT
    private val F = OfflineQueuePlan.Role.FILL

    private fun q(vararg items: Pair<String, OfflineQueuePlan.Role>) = items.map { OfflineQueuePlan.Entry(it.first, it.second) }

    /** Plays the ops on the ids the way media3 does (moveMediaItem's index is the one after the move). */
    private fun replay(entries: List<OfflineQueuePlan.Entry>, ops: List<OfflineQueuePlan.Op>, original: List<String>): List<String> {
        val list = entries.map { it.id }.toMutableList()
        for (op in ops) when (op) {
            is OfflineQueuePlan.Op.Remove -> list.removeAt(op.index)
            is OfflineQueuePlan.Op.Move -> list.add(op.to, list.removeAt(op.from))
            is OfflineQueuePlan.Op.Insert -> list.add(op.index, original[op.originalIndex])
        }
        return list
    }

    private fun restore(
        entries: List<OfflineQueuePlan.Entry>,
        current: Int,
        heard: Set<String>,
        original: List<String>,
        shuffle: Boolean = false,
        repeatAll: Boolean = false,
    ): Pair<List<String>, OfflineQueuePlan.Restore> {
        val plan = OfflineQueuePlan.restorePlan(entries, current, heard, original, shuffle, repeatAll)
        val after = replay(entries, plan.ops, original)
        assertEquals("the plan's own picture matches what the player ends with", after, plan.entries.map { it.id })
        return after to plan
    }

    /** C1: song A streamed with enough buffer, the network went, A finished — it never plays again. */
    @Test
    fun `a song that finished from its buffer does not come back`() {
        val (after, plan) = restore(
            q("h0" to P, "A" to P, "f1" to F, "f2" to F),
            current = 2,
            heard = setOf("A"),
            original = listOf("A", "n1", "n2"),
        )
        assertEquals(listOf("h0", "A", "f1", "n1", "n2"), after)
        assertEquals(2, plan.restored)
    }

    /** P3: the song the error path skipped (it could not play) comes back once — moved, not copied. */
    @Test
    fun `a skipped song is moved after the current one, never duplicated`() {
        val (after, _) = restore(
            q("h0" to P, "B" to P, "f1" to F, "f2" to F),
            current = 2,
            heard = emptySet(),
            original = listOf("B", "n1"),
        )
        assertEquals(listOf("h0", "f1", "B", "n1"), after)
    }

    /** C2: nothing left to restore — the unheard fill goes all the same. */
    @Test
    fun `the unheard fill always goes`() {
        val (after, plan) = restore(
            q("h0" to P, "A" to P, "f1" to F, "f2" to F, "f3" to F),
            current = 3,
            heard = setOf("A", "f1"),
            original = listOf("A"),
        )
        assertEquals(listOf("h0", "A", "f1", "f2"), after)
        assertEquals(0, plan.restored)
        assertEquals(1, plan.removed)
    }

    /** P3: what plays offline stays where it is (its preload too); only the network songs are inserted around it. */
    @Test
    fun `kept songs stay in place and the original order comes back`() {
        val (after, plan) = restore(
            q("h0" to P, "A" to P, "k1" to K, "k2" to K, "f1" to F),
            current = 1,
            heard = emptySet(),
            original = listOf("n1", "k1", "n2", "k2", "n3"),
        )
        assertEquals(listOf("h0", "A", "n1", "k1", "n2", "k2", "n3"), after)
        assertTrue("never a kept song moved", plan.ops.none { it is OfflineQueuePlan.Op.Move })
        assertEquals(listOf(OfflineQueuePlan.Op.Remove(4)), plan.ops.filterIsInstance<OfflineQueuePlan.Op.Remove>())
    }

    @Test
    fun `a kept song out of place is moved back into the original order`() {
        val (after, plan) = restore(q("A" to P, "k2" to K, "k1" to K), current = 0, heard = emptySet(), original = listOf("k1", "k2"))
        assertEquals(listOf("A", "k1", "k2"), after)
        assertEquals(1, plan.ops.size)
    }

    /** In linear play what the user jumped over counts as passed. */
    @Test
    fun `songs jumped over in linear play do not come back`() {
        val (after, _) = restore(
            q("A" to P, "k1" to K, "k2" to K, "k3" to K, "f1" to F),
            current = 3,
            heard = setOf("A", "k1"),
            original = listOf("k1", "k2", "k3", "n1"),
        )
        assertEquals(listOf("A", "k1", "k2", "k3", "n1"), after)
    }

    /** P5: a repeat-all queue that wrapped gets its own structure back, and the loop no longer carries the fill. */
    @Test
    fun `repeat-all wrapped back to the start`() {
        val (after, _) = restore(
            q("h0" to P, "h1" to P, "A" to P, "f1" to F, "f2" to F),
            current = 0,
            heard = setOf("A", "f1", "f2"),
            original = listOf("n1", "n2"),
            repeatAll = true,
        )
        assertEquals(listOf("h0", "h1", "A", "n1", "n2"), after)
    }

    @Test
    fun `back in the history, a song that will play again is left alone`() {
        val (after, _) = restore(
            q("h0" to P, "B" to P, "X" to P, "f1" to F),
            current = 0,
            heard = setOf("X", "f1"),
            original = listOf("B", "n1"),
            repeatAll = true,
        )
        assertEquals(listOf("h0", "B", "X", "n1"), after)
    }

    @Test
    fun `the user went back - what was heard ahead stays and the originals follow it`() {
        val (after, _) = restore(
            q("h0" to P, "h1" to P, "A" to P, "f1" to F, "f2" to F),
            current = 0,
            heard = setOf("A", "f1", "f2"),
            original = listOf("n1", "n2"),
        )
        assertEquals(listOf("h0", "h1", "A", "f1", "f2", "n1", "n2"), after)
    }

    @Test
    fun `shuffle leaves the order to the shuffle and still never duplicates`() {
        val (after, plan) = restore(
            q("A" to P, "k1" to K, "f1" to F, "f2" to F),
            current = 0,
            heard = setOf("f1"),
            original = listOf("k1", "n1", "n2"),
            shuffle = true,
        )
        assertEquals(listOf("A", "k1", "f1", "n1", "n2"), after)
        assertEquals(3, plan.restored)
    }

    /** C4: the probe is the next original song that needs the network and was not heard. */
    @Test
    fun `the probe is the next network song not heard`() {
        val original = listOf("A", "n1", "k1", "n2")
        val network = setOf(0, 1, 3)
        assertEquals(1, OfflineQueuePlan.probeTarget(original, network, setOf("A"), "x"))
        assertEquals(3, OfflineQueuePlan.probeTarget(original, network, setOf("A", "n1"), "x"))
        assertNull(OfflineQueuePlan.probeTarget(original, network, setOf("A", "n1"), "n2"))
    }
}
