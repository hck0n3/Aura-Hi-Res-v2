package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.Key
import iad1tya.echo.music.devicesync.LibrarySyncCore.PinMeta
import iad1tya.echo.music.devicesync.LibrarySyncCore.PinRef
import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 584 — pins (Biblioteca ▸ Fijados and «Fijadas en inicio») travel as registers of the library sync. */
class LibraryPinsSyncTest {
    @Test
    fun `a pin key round-trips and a bad one is not a pin`() {
        val ref = PinRef("l", "P", "PLabc123")
        assertEquals("pn:l:P:PLabc123", LibrarySyncCore.pinKey(ref))
        assertEquals(Key.Pin(ref), LibrarySyncCore.parse(LibrarySyncCore.pinKey(ref)))
        assertEquals(Key.Pin(PinRef("h", "S", "vid1")), LibrarySyncCore.parse("pn:h:S:vid1"))
        assertNull(LibrarySyncCore.parse("pn:x:P:abc"))
        assertNull(LibrarySyncCore.parse("pn:l:P:"))
        assertNull(LibrarySyncCore.parse("pn:l:P:a:b"))
        assertNull(LibrarySyncCore.parse("pn:l"))
    }

    @Test
    fun `the snapshot survives the trip and stays well under the room's limit`() {
        val meta = PinMeta("Mi lista \"favorita\"", "12 canciones", "https://x/y.jpg", local = true, extra = "RDAMPL1", at = 1_700_000_000_000L)
        val back = LibrarySyncCore.decodePin(LibrarySyncCore.encodePin(meta))
        assertEquals(meta, back)
        val huge = PinMeta("T".repeat(500), "S".repeat(500), "C".repeat(500), extra = "X".repeat(500), at = Long.MAX_VALUE)
        assertTrue(LibrarySyncCore.encodePin(huge).length < 800)
        assertNull(LibrarySyncCore.decodePin("{\"t\":\"\"}"))
    }

    @Test
    fun `an accepted pin is planned as an upsert and an off register as a removal`() {
        val ref = PinRef("h", "A", "MPREb_1")
        val on = Reg(LibrarySyncCore.pinKey(ref), 10, LibrarySyncCore.encodePin(PinMeta("Album")))
        val off = Reg(LibrarySyncCore.pinKey(PinRef("l", "R", "UC1")), 11, LibrarySyncCore.OFF)
        val plan = LibrarySyncCore.plan(listOf(on, off))
        assertEquals(listOf(ref to PinMeta("Album")), plan.pinUpserts)
        assertEquals(listOf(PinRef("l", "R", "UC1")), plan.pinRemovals)
        // An unpin counts toward the mass-deletion brake like any other removal.
        assertEquals(1, plan.removalCount)
    }

    @Test
    fun `pinning here is an op, unpinning here is an off register, pins are not create-only`() {
        val key = LibrarySyncCore.pinKey(PinRef("l", "P", "PL1"))
        val v = LibrarySyncCore.encodePin(PinMeta("Lista"))
        val pinned = LibrarySyncCore.localOps(mapOf(key to v), emptyMap(), now = 100)
        assertEquals(listOf(Reg(key, 100, v, pending = true)), pinned)
        val unpinned = LibrarySyncCore.localOps(emptyMap(), mapOf(key to Reg(key, 100, v)), now = 200)
        assertEquals(listOf(Reg(key, 200, LibrarySyncCore.OFF, pending = true)), unpinned)
    }
}
