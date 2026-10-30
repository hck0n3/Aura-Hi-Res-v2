package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Registry row 536 — the on-disk memory of the library sync survives a restart and does not grow without bound. */
class LibraryLedgerStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = LibraryLedgerStore(File(tmp.root, "device_sync/library_ledger.tsv"))

    @Test
    fun `what was written is read back, the last line for a key wins, pending survives`() {
        val s = store()
        s.append(listOf(Reg("lk:a", 1, "1", pending = true), Reg("pl:LP1", 2, LibrarySyncCore.encodePlaylistName("Gym \"x\""), pending = false)))
        s.append(listOf(Reg("lk:a", 5, "0", pending = false)))
        val back = store().load()
        assertEquals(Reg("lk:a", 5, "0", pending = false), back["lk:a"])
        assertEquals(LibrarySyncCore.encodePlaylistName("Gym \"x\""), back["pl:LP1"]?.value)
        assertEquals(2, back.size)
        assertTrue(Reg("k", 1, "1", pending = true).pending)
    }

    @Test
    fun `a missing or damaged file is an empty ledger, never a crash`() {
        assertTrue(store().load().isEmpty())
        val f = File(tmp.root, "device_sync/library_ledger.tsv")
        f.parentFile.mkdirs()
        f.writeText("garbage without tabs\nlk:b\tnot-a-number\t0\t1\nlk:ok\t7\t0\t1\n")
        assertEquals(setOf("lk:ok"), store().load().keys)
    }

    @Test
    fun `the log is compacted only when it has grown far beyond what it holds`() {
        val s = store()
        val map = HashMap<String, Reg>()
        repeat(1500) { i -> val r = Reg("lk:a", i.toLong(), "1"); s.append(listOf(r)); map["lk:a"] = r }
        // 1500 lines for 1 register: far beyond 2*1 + 1000
        assertTrue(s.compactIfBloated(map))
        assertEquals(1, File(tmp.root, "device_sync/library_ledger.tsv").readLines().size)
        assertFalse("now it is tight", s.compactIfBloated(map))
        assertEquals(map, store().load())
    }
}
