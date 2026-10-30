package iad1tya.echo.music.devicesync

import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import java.io.File

/**
 * Row 536 — what this device last knew of every synced register, kept on disk so a restart (or a day offline) resumes where it
 * stopped. An append-only text log, one register per line, the last line for a key winning; it is rewritten whole only when it has
 * grown to more than twice what it needs ([compactIfBloated]). Line: `key TAB ts TAB pending(0|1) TAB value`. Keys have no tab or
 * newline, and a value never has either (the sync escapes them), so no quoting is needed.
 */
class LibraryLedgerStore(private val file: File) : LedgerPersistence {
    private var lines = 0

    override fun load(): HashMap<String, Reg> {
        val map = HashMap<String, Reg>()
        lines = 0
        if (!file.isFile) return map
        file.forEachLine { line ->
            lines++
            val parts = line.split('\t', limit = 4)
            if (parts.size < 4) return@forEachLine
            val ts = parts[1].toLongOrNull() ?: return@forEachLine
            map[parts[0]] = Reg(parts[0], ts, parts[3], pending = parts[2] == "1")
        }
        return map
    }

    override fun append(regs: Collection<Reg>) {
        if (regs.isEmpty()) return
        file.parentFile?.mkdirs()
        file.appendText(regs.joinToString(separator = "") { line(it) })
        lines += regs.size
    }

    /** Rewrites the log from [map] when it holds far more lines than registers. Returns true when it did. */
    override fun compactIfBloated(map: Map<String, Reg>): Boolean {
        if (lines <= map.size * 2 + 1000) return false
        rewrite(map)
        return true
    }

    override fun rewrite(map: Map<String, Reg>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(map.values.joinToString(separator = "") { line(it) })
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        lines = map.size
    }

    private fun line(r: Reg) = "${r.key}\t${r.ts}\t${if (r.pending) 1 else 0}\t${r.value}\n"
}
