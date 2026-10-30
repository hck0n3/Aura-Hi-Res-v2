package iad1tya.echo.music.devicesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 528 — the pairing code, the room id and the merge rules of the device sync. */
class DeviceSyncPureTest {
    @Test
    fun `a code is twenty characters in groups of four and survives being retyped`() {
        val code = SyncCode.generate()
        assertEquals(24, code.length)
        assertEquals(4, code.count { it == '-' })
        val normalized = SyncCode.normalize(code)
        assertEquals(20, normalized?.length)
        // lower case, spaces and stray symbols are tolerated
        assertEquals(normalized, SyncCode.normalize(code.lowercase().replace("-", " ")))
        assertNull(SyncCode.normalize("ABCD-EFGH"))
        assertNull(SyncCode.normalize(""))
        assertEquals(code, SyncCode.pretty(normalized!!))
    }

    @Test
    fun `the room id is a 64 hex hash, stable per code and different between codes`() {
        val a = SyncCode.normalize("ABCDE-FGHJK-MNPQR-STUVW")!!
        val b = SyncCode.normalize("ABCDE-FGHJK-MNPQR-STUVX")!!
        val id = SyncCode.groupId(a)
        assertTrue(Regex("[a-f0-9]{64}").matches(id))
        assertEquals(id, SyncCode.groupId(a))
        assertNotEquals(id, SyncCode.groupId(b))
        // the code itself is never in the id
        assertTrue(!id.contains(a.lowercase()))
    }

    @Test
    fun `the server address is accepted however it was pasted`() {
        val g = "a".repeat(64)
        assertEquals("wss://aura-sync.x.workers.dev/sync?g=$g", SyncCode.socketUrl("https://aura-sync.x.workers.dev", g))
        assertEquals("wss://aura-sync.x.workers.dev/sync?g=$g", SyncCode.socketUrl("aura-sync.x.workers.dev/", g))
        assertEquals("wss://aura-sync.x.workers.dev/sync?g=$g", SyncCode.socketUrl("https://aura-sync.x.workers.dev/sync", g))
        assertEquals("ws://192.168.1.5:8787/sync?g=$g", SyncCode.socketUrl("http://192.168.1.5:8787", g))
        assertNull(SyncCode.socketUrl("   ", g))
    }

    @Test
    fun `a remote shuffle report is applied by cycle, then union, and a stale one is ignored`() {
        // newer cycle: wipe and adopt
        assertEquals(
            ShuffleMerge.Plan.Reset(2, listOf("a", "b")),
            ShuffleMerge.plan(localEpoch = 1, localIds = setOf("x"), remoteEpoch = 2, remoteIds = listOf("a", "b", "a")),
        )
        // same cycle: only what is missing
        assertEquals(
            ShuffleMerge.Plan.Add(listOf("c")),
            ShuffleMerge.plan(1, setOf("a", "b"), 1, listOf("a", "c")),
        )
        assertEquals(ShuffleMerge.Plan.Ignore, ShuffleMerge.plan(1, setOf("a"), 1, listOf("a")))
        assertEquals(ShuffleMerge.Plan.Ignore, ShuffleMerge.plan(3, setOf("a"), 2, listOf("z")))
    }

    @Test
    fun `what this device reports is the new plays, or the whole set when its cycle moved on`() {
        assertNull(ShuffleMerge.outgoing(epoch = 1, pushedEpoch = 1, allIds = listOf("a", "b"), newIds = emptyList()))
        assertEquals(1 to listOf("b"), ShuffleMerge.outgoing(1, 1, listOf("a", "b"), listOf("b")))
        assertEquals(2 to listOf("n"), ShuffleMerge.outgoing(2, 1, listOf("n"), emptyList()))
        // never reported before: everything
        assertEquals(0 to listOf("a", "b"), ShuffleMerge.outgoing(0, null, listOf("a", "b"), listOf("b")))
    }
}
