package iad1tya.echo.music.listentogether

/** How often the SAME correction may be logged again while it repeats (row 797). */
internal const val SYNC_LOG_REPEAT_MS = 60_000L

/** The word the shared log uses for [action]. */
internal fun syncActionLabel(action: SyncAction): String =
    when (action) {
        SyncAction.Hold -> "hold"
        is SyncAction.Trim -> "trim"
        is SyncAction.Resync -> "resync"
    }

/**
 * Row 797: whether a guest's sync decision goes into the shared app.log. Every CHANGE of decision is written (that is what says
 * why the room drifted), and a trim or a jump that keeps repeating at most every [repeatMs]; an in-sync hold is never repeated.
 * The heartbeat decides every 15 s, so without this a long room would write four identical lines a minute for nothing (rule 7).
 */
internal class SyncDecisionLogGate(private val repeatMs: Long = SYNC_LOG_REPEAT_MS) {
    private var last: String? = null
    private var lastAtMs = 0L

    @Synchronized
    fun shouldLog(action: String, nowMs: Long): Boolean {
        val due = action != last || (action != "hold" && nowMs - lastAtMs >= repeatMs)
        if (!due) return false
        last = action
        lastAtMs = nowMs
        return true
    }

    @Synchronized
    fun reset() {
        last = null
        lastAtMs = 0L
    }
}
