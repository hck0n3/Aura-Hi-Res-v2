package iad1tya.echo.music.playback

/**
 * The enhanced shuffle's CYCLE decisions, as pure functions: when a list counts as FINISHED, and when its
 * per-list no-repeat memory may be reset. No Android, no media3, no player — so they can be unit-tested.
 *
 * This class of bug ("the shuffle repeats") has now been fixed seven times, and the reason it kept coming
 * back is that the decision lived inline in [MusicService], tangled with the live player, where no test
 * could reach it. Ordering already moved out to [ShuffleOrdering] for the same reason; this is the other
 * half — the *when*, not the *order*.
 *
 * The two facts these functions keep apart:
 *  - COVERAGE: how many items the CONTEXT (the list the user opened) actually loaded. Used to be read off
 *    the radio seed pool, which is a different fact that merely lived in the same field: it is written by
 *    one code path only, is empty for a queue handed over by Android Auto, and can hold the size of a
 *    completely different list. Judging "everything played" against the LIVE TIMELINE instead of the
 *    context is what let a shrinking queue finish a lap it never played.
 *  - COMPLETION: every song of the context is in the played set. Completion hands the queue to the
 *    infinite radio; it does NOT by itself reset the memory — that needs the user to re-activate shuffle.
 */
object EnhancedShuffleCycle {

    /** Coverage is unknown: judge by the timeline alone (see [coversContext] for why not "not finished"). */
    const val COVERAGE_UNKNOWN = 0

    /**
     * Coverage only counts when it describes THIS context. A size measured for another list is not a
     * weaker signal, it is a wrong one: it made a 12-song car queue impossible to finish against an
     * 80-song playlist measured minutes earlier, and it made an 80-song list finish against a 4-track EP.
     */
    fun coverageOf(contextId: String?, coverageContextId: String?, coverageSize: Int): Int =
        if (contextId != null && contextId == coverageContextId && coverageSize > 0) coverageSize
        else COVERAGE_UNKNOWN

    /**
     * Does the live timeline still cover the whole context?
     *
     * UNKNOWN coverage answers YES on purpose. "If we don't know, report not finished" looks like the safe
     * side and is strictly worse: the very same reading decides the handoff to the infinite radio, so a
     * permanent "not finished" means the list can never end, the radio never takes over, and the queue
     * loops re-shuffling songs already heard — the exact complaint this feature exists to prevent.
     */
    fun coversContext(timelineSize: Int, coverageSize: Int): Boolean =
        coverageSize <= COVERAGE_UNKNOWN || timelineSize >= coverageSize

    /**
     * Has this list been played to the end? Every id must be known AND already played, and the pool being
     * judged must still cover the context.
     *
     * [idAt] is an accessor rather than a list so the hot path (every auto-advance, and with crossfade ON
     * that is every song) can read straight from the player's timeline without copying thousands of ids.
     * An id that cannot be read answers "not finished" — an unreadable item is never proof of completion.
     */
    inline fun isCycleComplete(
        timelineSize: Int,
        coverageSize: Int,
        playedIds: Set<String>,
        idAt: (Int) -> String?,
    ): Boolean {
        if (timelineSize <= 0) return false
        if (!coversContext(timelineSize, coverageSize)) return false
        for (i in 0 until timelineSize) {
            val id = idAt(i) ?: return false
            if (id !in playedIds) return false
        }
        return true
    }

    /**
     * The owner's rule, in one line: "lo que ya se reprodujo de la lista no se vuelva a repetir A MENOS QUE
     * ya se haya finalizado la reproducción de esa lista Y el usuario vuelva a activar el aleatorio".
     *
     * TWO conditions. Finishing the list alone must not reset anything — the memory has to still be there
     * when he comes back to that list later. The reset belongs to the moment he turns shuffle on again.
     */
    fun shouldResetForNewCycle(isUserActivation: Boolean, cycleComplete: Boolean): Boolean =
        isUserActivation && cycleComplete
}
