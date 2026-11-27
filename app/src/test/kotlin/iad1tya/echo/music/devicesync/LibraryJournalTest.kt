package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 900 — the journal of changes made before a device can date them against the room: its pure rules and its file. */
class LibraryJournalTest {
    private fun entry(seq: Long, key: String, value: String, boot: Long = 7, elapsed: Long = 1_000, wall: Long = 50_000) =
        JournalEntry(seq, key, value, boot, elapsed, wall)

    @Test
    fun `observed sees additions, changes and removals, never a create-only fact or a family it does not know going away`() {
        val before = mapOf("lk:a" to "1", "lk:b" to "1", "pl:LPabcdefgh" to "{\"n\":\"A\"}", "sg:a" to "{}", "hv:a:5" to "1", "zz:x" to "1")
        val after = mapOf("lk:a" to "1", "pl:LPabcdefgh" to "{\"n\":\"B\"}", "lk:c" to "1")
        assertEquals(
            mapOf("pl:LPabcdefgh" to "{\"n\":\"B\"}", "lk:c" to "1", "lk:b" to "0"),
            JournalRules.observed(before, after),
        )
    }

    @Test
    fun `the same boot dates a change exactly by the monotonic clock, whatever the phone clock says`() {
        val e = entry(0, "lk:a", "1", boot = 7, elapsed = 10_000, wall = 999_999_999)
        // Now: 60 s of monotonic time later, the room's clock says 2_000_000.
        assertEquals(2_000_000L - 60_000L, JournalRules.realTime(e, serverNow = 2_000_000L, mono = Mono(7, 70_000), wallNow = 0L, calibration = null))
    }

    @Test
    fun `another boot is dated by the last calibration only when the phone clock is believable, and never in the future`() {
        val e = entry(0, "lk:a", "1", boot = 7, elapsed = 10_000, wall = 5_000_000)
        val now = Mono(8, 3_000)
        assertNull("no calibration: cannot be dated", JournalRules.realTime(e, 9_000_000, now, 6_000_000, null))
        assertEquals(5_000_000L + 100L, JournalRules.realTime(e, 9_000_000, now, 6_000_000, Calibration(offsetMs = 100, atWallMs = 4_000_000)))
        assertNull("the phone clock went back past the calibration", JournalRules.realTime(e, 9_000_000, now, 6_000_000, Calibration(100, 5_500_000)))
        assertNull("a moment later than the phone clock now", JournalRules.realTime(e, 9_000_000, now, 4_900_000, Calibration(100, 4_000_000)))
        assertEquals("capped at the room's now", 5_000_500L, JournalRules.realTime(e, 5_000_500, now, 6_000_000, Calibration(10_000, 4_000_000)))
        assertNull("an unknown boot count is another boot", JournalRules.realTime(entry(0, "lk:a", "1", boot = -1), 9_000_000, Mono(-1, 50_000), 0, null))
    }

    @Test
    fun `the journal becomes pending registers in the order the changes were made, each later than what the ledger knew`() {
        val mono = Mono(7, 100_000)
        val entries = listOf(
            entry(2, "lk:a", "0", elapsed = 90_000),
            entry(1, "lk:a", "1", elapsed = 80_000),
            entry(3, "lk:b", "1", elapsed = 95_000),
        )
        val ledger = mapOf("lk:b" to Reg("lk:b", 2_000_000, "0"))
        val (regs, undated) = JournalRules.toRegs(entries, ledger, serverNow = 1_000_000, mono = mono, wallNow = 0, calibration = null)
        assertEquals(0, undated)
        assertEquals(Reg("lk:a", 1_000_000 - 10_000, "0", pending = true), regs.single { it.key == "lk:a" })
        assertEquals("a deliberate change of what this device showed is later than it", Reg("lk:b", 2_000_001, "1", pending = true), regs.single { it.key == "lk:b" })
    }

    @Test
    fun `held removals, known create-only facts and changes already pending are left alone, and what cannot be dated is counted`() {
        val mono = Mono(7, 100_000)
        val ledger = mapOf(
            "lk:held" to Reg("lk:held", 10, "0", parked = true),
            "sg:s" to Reg("sg:s", 10, "{\"t\":\"x\"}"),
            "lk:p" to Reg("lk:p", 10, "1", pending = true),
        )
        val entries = listOf(
            entry(1, "lk:held", "1"),
            entry(2, "sg:s", "{\"t\":\"y\"}"),
            entry(3, "lk:p", "1"),
            entry(4, "lk:old", "1", boot = 3),
        )
        val (regs, undated) = JournalRules.toRegs(entries, ledger, serverNow = 1_000_000, mono = mono, wallNow = 0, calibration = null)
        assertTrue(regs.isEmpty())
        assertEquals(1, undated)
    }

    @Test
    fun `the file keeps entries, the counter and the calibration across instances, and skips a broken line`() {
        val dir = Files.createTempDirectory("journal").toFile()
        try {
            val a = LibraryJournalStore(dir)
            assertEquals(0L, a.nextSeq(2))
            a.append(listOf(entry(0, "lk:a", "1"), entry(1, "pl:LPabcdefgh", "{\"n\":\"Gym\"}")))
            a.saveCalibration(Calibration(-250, 123))
            File(dir, "journal.tsv").appendText("broken line\n")
            val b = LibraryJournalStore(dir)
            assertEquals(listOf("lk:a", "pl:LPabcdefgh"), b.load().map { it.key })
            assertEquals("{\"n\":\"Gym\"}", b.load()[1].value)
            assertEquals("a number is never handed out twice", 2L, b.nextSeq(1))
            assertEquals(Calibration(-250, 123), b.calibration())
            b.clear()
            assertTrue(LibraryJournalStore(dir).load().isEmpty())
            assertEquals(3L, LibraryJournalStore(dir).nextSeq(1))
        } finally {
            dir.deleteRecursively()
        }
    }
}
