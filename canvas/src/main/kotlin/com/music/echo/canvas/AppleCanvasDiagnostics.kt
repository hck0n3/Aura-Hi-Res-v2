package iad1tya.echo.music.canvas

/**
 * Where the animated-cover code reports what it did, WITHOUT titles, artists or ids: a status word and a count at most
 * (AGENTS.md rule 4 — the app wires [sink] to its shared log). Until now this module only `println`ed, which Android
 * never shows, so «las portadas animadas ya no salen» (owner 2026-10-29) left no trace to read.
 */
object AppleCanvasDiagnostics {
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun log(message: String) {
        runCatching { sink?.invoke(message) }
    }
}
