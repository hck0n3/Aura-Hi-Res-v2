package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.PlaylistOrder
import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 900 — a playlist's order between devices: one fractional position per song, newest wins per song. */
class PlaylistOrderTest {
    @Test
    fun `a position between two others is strictly between them and valid, however often the same gap is split`() {
        val rnd = Random(900)
        var lo = ""
        var hi: String? = null
        repeat(200) {
            val mid = PlaylistOrder.between(lo, hi)
            assertTrue(mid, PlaylistOrder.valid(mid))
            assertTrue("$lo < $mid", lo < mid)
            hi?.let { assertTrue("$mid < $it", mid < it) }
            if (rnd.nextBoolean()) lo = mid else hi = mid
        }
    }

    @Test
    fun `append and insert at the top stay short and in order`() {
        var last = "a01"
        repeat(1000) {
            val next = PlaylistOrder.after(last)
            assertTrue(next > last)
            assertTrue(PlaylistOrder.valid(next))
            assertEquals(3, next.length)
            last = next
        }
        var first = "a01"
        repeat(1000) {
            val prev = PlaylistOrder.before(first)
            assertTrue(prev < first)
            assertTrue(PlaylistOrder.valid(prev))
            first = prev
        }
    }

    @Test
    fun `a list with no positions is numbered the same way on every device, with short positions`() {
        val songs = (1..5000).map { "s$it" }
        val a = PlaylistOrder.assign(songs) { null }
        assertEquals(a, PlaylistOrder.assign(songs) { null })
        assertTrue(a.zipWithNext().all { (x, y) -> x < y })
        assertTrue(a.all { it.length == 3 && PlaylistOrder.valid(it) })
    }

    @Test
    fun `a move gives a new position to the moved song alone, and an addition to the added one alone`() {
        val songs = (1..6).map { "s$it" }
        val known = songs.zip(PlaylistOrder.assign(songs) { null }).toMap()
        val moved = listOf("s5", "s1", "s2", "s3", "s4", "s6")
        val afterMove = moved.zip(PlaylistOrder.assign(moved) { known[it] }).toMap()
        assertEquals(setOf("s5"), moved.filter { afterMove[it] != known[it] }.toSet())
        assertTrue(moved.map { afterMove.getValue(it) }.zipWithNext().all { (x, y) -> x < y })

        val added = listOf("s1", "s2", "new", "s3", "s4", "s5", "s6")
        val afterAdd = added.zip(PlaylistOrder.assign(added) { known[it] }).toMap()
        assertEquals(setOf("new"), added.filter { afterAdd[it] != known[it] }.toSet())
        assertTrue(afterAdd.getValue("s2") < afterAdd.getValue("new") && afterAdd.getValue("new") < afterAdd.getValue("s3"))
    }

    @Test
    fun `positions that do not fit any more, or are not valid, are replaced`() {
        val known = mapOf("a" to "a01", "b" to "a02", "c" to "a02", "d" to "bad!", "e" to "a1a")
        val got = PlaylistOrder.assign(listOf("a", "b", "c", "d", "e")) { known[it] }
        assertTrue("strictly increasing whatever came in", got.zipWithNext().all { (x, y) -> x < y })
        assertEquals("a01", got[0])
        assertEquals("of two songs with the same position one keeps it", 1, listOf(got[1], got[2]).count { it == "a02" })
        assertEquals("a1a", got[4])
    }

    @Test
    fun `sorting by positions keeps a song with no position right after the one it follows here`() {
        val positions = mapOf("a" to "a03", "b" to "a01", "c" to "a02")
        assertEquals(listOf("b", "c", "a", "local"), PlaylistOrder.sorted(listOf("a", "local", "b", "c"), positions))
        assertEquals("a song first here stays first", listOf("top", "b", "a"), PlaylistOrder.sorted(listOf("top", "a", "b"), mapOf("a" to "a05", "b" to "a01")))
        assertEquals("a tie goes by song id on every device", listOf("x", "y"), PlaylistOrder.sorted(listOf("y", "x"), mapOf("x" to "a01", "y" to "a01")))
    }

    @Test
    fun `a song in a list twice keeps its second copy where it is when another song moves`() {
        // «Gym» here is [S, T, U, V, S]: S at #1 and #5. Another device moved V to the top; S keeps its first position.
        val entries = listOf("S", "T", "U", "V", "S")
        val positions = mapOf("S" to "a02", "T" to "a03", "U" to "a04", "V" to "a01")
        val order = PlaylistOrder.sortedEntries(entries, positions)
        assertEquals("entry indices: the second S stays after U, it does not follow V to the top", listOf(3, 0, 1, 2, 4), order)
        assertEquals(listOf("V", "S", "T", "U", "S"), order.map { entries[it] })
        val gym = listOf("S", "T", "U", "V", "W", "S") // T moved to the end on another device
        val moved = PlaylistOrder.sortedEntries(gym, mapOf("S" to "a02", "T" to "a07", "U" to "a04", "V" to "a05", "W" to "a06"))
        assertEquals(listOf("S", "U", "V", "W", "S", "T"), moved.map { gym[it] })
        assertEquals("nothing moved = nothing changes", entries.indices.toList(), PlaylistOrder.sortedEntries(entries, positions - "V" + ("V" to "a05")))
        assertEquals(
            "without duplicates it is the order of sorted()",
            PlaylistOrder.sorted(listOf("a", "local", "b", "c"), mapOf("a" to "a03", "b" to "a01", "c" to "a02")),
            PlaylistOrder.sortedEntries(listOf("a", "local", "b", "c"), mapOf("a" to "a03", "b" to "a01", "c" to "a02")).map { listOf("a", "local", "b", "c")[it] },
        )
    }

    @Test
    fun `the facts of a list use the position key and leave out ids that cannot travel`() {
        val facts = PlaylistOrder.facts("LPabcdefgh", listOf("s1", "bad:id", "s2", "s1"), emptyMap())
        assertEquals(listOf("po:LPabcdefgh:s1", "po:LPabcdefgh:s2"), facts.keys.toList())
    }

    @Test
    fun `a position is never sent as a removal, never counted as a removal, and reorders only its list`() {
        val ledger = mapOf("po:LPabcdefgh:s1" to Reg("po:LPabcdefgh:s1", 10, "a01"), "pm:LPabcdefgh:s1" to Reg("pm:LPabcdefgh:s1", 10, "1"))
        val ops = LibrarySyncCore.localOps(emptyMap(), ledger, 100)
        assertEquals(listOf("pm:LPabcdefgh:s1"), ops.map { it.key })

        val plan = LibrarySyncCore.plan(listOf(Reg("po:LPabcdefgh:s2", 20, "a05"), Reg("po:LPother0000:s3", 20, "0")))
        assertEquals("an invalid position is not used", setOf("LPabcdefgh"), plan.reorder)
        assertEquals(0, plan.removalCount)
        assertEquals(setOf("LPx"), LibrarySyncCore.plan(listOf(Reg("pm:LPx:s9", 20, "1"))).reorder)

        val ids = LibrarySyncCore.PlaylistIds.of(ledger, listOf(Reg("po:LPabcdefgh:s2", 20, "a05")))
        assertEquals(mapOf("s1" to "a01", "s2" to "a05"), ids.orderOf("LPabcdefgh"))
        assertFalse(LibrarySyncCore.isMeta("po:LPabcdefgh:s1"))
        assertTrue(LibrarySyncCore.parse("po:LPabcdefgh:s1") is LibrarySyncCore.Key.Order)
    }
}
