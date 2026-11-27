package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import java.io.File

/**
 * Row 900 — the journal of what the user changed in the library while this device could not date it against the room yet: after
 * the app or its service started and before the first full download from the room (offline included), when the server's clock is
 * not known in this process. Every change seen being made is written down at once with the moment it happened, measured on the
 * device's monotonic clock ([Mono]: boot count + time since boot, which no clock change and no missing network can move) and a
 * per-device counter ([JournalEntry.seq]) that orders two changes of the same moment.
 *
 * When the room's clock is known again the entries become pending registers of the ledger with the room's time of the moment they
 * were MADE ([JournalRules.toRegs]), and compete newest-wins with what the room sends: an offline change keeps its place in time, it
 * neither loses to an older change of another device nor beats a newer one. The ledger keeps them pending until the room
 * acknowledges each one (`lib_ack.ids`); only then are they settled.
 *
 * Only what was SEEN being made goes in: the library as it was when this instance started is the baseline, so whatever happened
 * while the sync was not watching (the app not running, «Biblioteca» off, a restored backup) is never dated now — the room decides
 * first for that, as before (row 654).
 */
data class Mono(
    /** Which boot of the device ([android.provider.Settings.Global.BOOT_COUNT]); -1 when unknown. */
    val boot: Long,
    /** Time since that boot ([android.os.SystemClock.elapsedRealtime]). */
    val elapsedMs: Long,
)

/** One change seen being made: [key] became [value] ("0" = gone) at [elapsedMs] of boot [boot]; [wallMs] is the phone clock then. */
data class JournalEntry(val seq: Long, val key: String, val value: String, val boot: Long, val elapsedMs: Long, val wallMs: Long)

/** The room's clock minus the phone's ([offsetMs]), measured when the phone clock read [atWallMs]. */
data class Calibration(val offsetMs: Long, val atWallMs: Long)

/** Where the journal lives (a file per room; memory in tests). */
interface JournalPersistence {
    fun load(): List<JournalEntry>
    fun append(entries: Collection<JournalEntry>)
    fun clear()

    /** Reserves [count] numbers of the per-device counter and returns the first; a number is never handed out twice. */
    fun nextSeq(count: Int): Long

    fun calibration(): Calibration?
    fun saveCalibration(calibration: Calibration)
}

class MemoryJournal : JournalPersistence {
    private val entries = ArrayList<JournalEntry>()
    private var seq = 0L
    private var cal: Calibration? = null

    @Synchronized override fun load(): List<JournalEntry> = ArrayList(entries)
    @Synchronized override fun append(entries: Collection<JournalEntry>) { this.entries += entries }
    @Synchronized override fun clear() { entries.clear() }
    @Synchronized override fun nextSeq(count: Int): Long = seq.also { seq += count }
    @Synchronized override fun calibration(): Calibration? = cal
    @Synchronized override fun saveCalibration(calibration: Calibration) { cal = calibration }
}

/**
 * The journal on disk, next to the room's ledger: `journal.tsv` (one entry per line, `seq TAB boot TAB elapsed TAB wall TAB key TAB
 * value`; keys and values never hold a tab or a newline, the sync escapes them) and `journal.state` (the counter and the calibration).
 * Only counts ever reach the log.
 */
class LibraryJournalStore(private val dir: File) : JournalPersistence {
    private val file get() = File(dir, "journal.tsv")
    private val state get() = File(dir, "journal.state")

    override fun load(): List<JournalEntry> {
        val f = file
        if (!f.isFile) return emptyList()
        val out = ArrayList<JournalEntry>()
        f.forEachLine { line ->
            val p = line.split('\t', limit = 6)
            if (p.size < 6) return@forEachLine
            val seq = p[0].toLongOrNull() ?: return@forEachLine
            val boot = p[1].toLongOrNull() ?: return@forEachLine
            val elapsed = p[2].toLongOrNull() ?: return@forEachLine
            val wall = p[3].toLongOrNull() ?: return@forEachLine
            if (p[4].isEmpty()) return@forEachLine
            out += JournalEntry(seq, p[4], p[5], boot, elapsed, wall)
        }
        return out
    }

    override fun append(entries: Collection<JournalEntry>) {
        if (entries.isEmpty()) return
        dir.mkdirs()
        file.appendText(entries.joinToString(separator = "") { "${it.seq}\t${it.boot}\t${it.elapsedMs}\t${it.wallMs}\t${it.key}\t${it.value}\n" })
    }

    override fun clear() {
        file.delete()
    }

    override fun nextSeq(count: Int): Long {
        val (seq, cal) = readState()
        writeState(seq + count, cal)
        return seq
    }

    override fun calibration(): Calibration? = readState().second

    override fun saveCalibration(calibration: Calibration) {
        writeState(readState().first, calibration)
    }

    private fun readState(): Pair<Long, Calibration?> {
        val f = state
        if (!f.isFile) return 0L to null
        val p = runCatching { f.readText().trim().split('\t') }.getOrNull() ?: return 0L to null
        val seq = p.getOrNull(0)?.toLongOrNull() ?: 0L
        val offset = p.getOrNull(1)?.toLongOrNull()
        val at = p.getOrNull(2)?.toLongOrNull()
        return seq to (if (offset != null && at != null) Calibration(offset, at) else null)
    }

    private fun writeState(seq: Long, cal: Calibration?) {
        dir.mkdirs()
        val tmp = File(dir, "journal.state.tmp")
        tmp.writeText(listOfNotNull(seq.toString(), cal?.offsetMs?.toString(), cal?.atWallMs?.toString()).joinToString("\t"))
        if (!tmp.renameTo(state)) {
            state.writeText(tmp.readText())
            tmp.delete()
        }
    }
}

/** The pure rules of the journal, pinned by plain JVM tests. */
object JournalRules {
    /**
     * What changed between two reads of the library: key → its new value ("0" = gone). Create-only facts (`sg:`, `ag:`, `hv:`, `pb:`)
     * are never «gone» (they leave the read once the ledger knows them), and a playlist position (`po:`) is never removed either.
     */
    fun observed(before: Map<String, String>, after: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((key, value) in after) if (before[key] != value) out[key] = value
        for (key in before.keys) {
            if (key in after || LibrarySyncCore.isMeta(key) || LibrarySyncCore.isOrder(key) || LibrarySyncCore.parse(key) == null) continue
            out[key] = LibrarySyncCore.OFF
        }
        return out
    }

    /**
     * The room's time of the moment [e] was made, or null when it cannot be known. The same boot: exactly the room's time now minus
     * how long ago it was on the monotonic clock. Another boot: the phone clock of then plus the last [calibration] taken before it,
     * only if the phone clock did not go back past that calibration and the moment is not in the future — never later than now.
     */
    fun realTime(e: JournalEntry, serverNow: Long, mono: Mono, wallNow: Long, calibration: Calibration?): Long? {
        if (e.boot >= 0 && e.boot == mono.boot && e.elapsedMs <= mono.elapsedMs) return serverNow - (mono.elapsedMs - e.elapsedMs)
        val cal = calibration ?: return null
        if (e.wallMs < cal.atWallMs || e.wallMs > wallNow) return null
        return minOf(serverNow, e.wallMs + cal.offsetMs)
    }

    /**
     * The journal as pending registers of the ledger, in the order the changes were made. A change is a deliberate act on what this
     * device showed, so its time is also later than what the ledger knew (as [LibrarySyncCore.localOps] does). Skipped: what cannot
     * be dated (returned as the second value), a register the mass-removal brake holds (row 807: only «Aplicar»/«Descartar» resolve
     * it), a create-only fact the ledger already has, and a change the ledger already holds as pending or newer.
     */
    fun toRegs(
        entries: List<JournalEntry>,
        ledger: Map<String, Reg>,
        serverNow: Long,
        mono: Mono,
        wallNow: Long,
        calibration: Calibration?,
    ): Pair<List<Reg>, Int> {
        val out = LinkedHashMap<String, Reg>()
        var undated = 0
        for (e in entries.sortedBy { it.seq }) {
            val real = realTime(e, serverNow, mono, wallNow, calibration)
            if (real == null) {
                undated++
                continue
            }
            val known = out[e.key] ?: ledger[e.key]
            if (known != null && LibrarySyncCore.isHeld(known)) continue
            if (LibrarySyncCore.isMeta(e.key) && known != null) continue
            if (known != null && known.value == e.value && (known.pending || known.ts >= real)) continue
            out[e.key] = Reg(e.key, maxOf(real, (known?.ts ?: -1L) + 1), e.value, pending = true)
        }
        return out.values.toList() to undated
    }
}
