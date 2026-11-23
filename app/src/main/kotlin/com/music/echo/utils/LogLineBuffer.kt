package iad1tya.echo.music.utils

/**
 * The lines [AppLogger] has redacted but not yet written (registry row 731). Not thread-safe on purpose: only the logger's
 * single IO thread touches it. Pure Kotlin so the flush rule is unit-tested without Android.
 */
internal class LogLineBuffer(private val flushEveryLines: Int) {
    private val text = StringBuilder()
    private var lines = 0

    /** Queues one already-formatted line; true once enough lines wait that they should be written now. */
    fun add(line: String): Boolean {
        text.append(line)
        lines++
        return lines >= flushEveryLines
    }

    fun isEmpty(): Boolean = lines == 0

    /** Everything waiting, oldest first, and empties the buffer. */
    fun drain(): String {
        val out = text.toString()
        text.setLength(0)
        lines = 0
        return out
    }
}
