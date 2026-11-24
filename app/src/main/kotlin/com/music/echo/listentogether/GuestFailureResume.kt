package iad1tya.echo.music.listentogether

/**
 * Row 821: how long after a failure pause each automatic re-play of the guest waits (first, second, third); its size is how many
 * automatic re-plays there are before the guest gives up.
 */
internal val GUEST_FAILURE_RESUME_BACKOFF_MS: List<Long> = listOf(15_000L, 30_000L, 60_000L)

/** [guestRetryExhaustedLogLine]'s reason: the service stopped on a playback error (error stop, end of an error-skip chain). */
internal const val GUEST_RETRY_REASON_PLAYBACK_ERROR = 1

/** [guestRetryExhaustedLogLine]'s reason: the system refused the service's foreground start. */
internal const val GUEST_RETRY_REASON_FOREGROUND_REFUSED = 2

/** What the room's automatic paths (heartbeat, realign, recheck, a released hold) do with a guest its service paused on a failure. */
internal sealed interface GuestFailureResumeStep {
    /** Play it again; [attempt] counts from 1 since the room's last command. */
    data class Resume(val attempt: Int) : GuestFailureResumeStep

    /** Not yet: the backoff since the last failure pause has not passed. */
    data object Wait : GuestFailureResumeStep

    /** Stop trying, now: say so once ([attempts] made, [reason] one of `GUEST_RETRY_REASON_*`) and stay paused. */
    data class GiveUp(val attempts: Int, val reason: Int) : GuestFailureResumeStep

    /** Already given up: stay paused until the user or the room's next command. */
    data object Hold : GuestFailureResumeStep
}

/**
 * Row 821 (rule 7, battery): since row 816 a failure pause no longer holds the guest, so every automatic path of the bridge played it
 * again while the room played — every 15 s, without end (an offline stop retried forever; a refused foreground start paused and
 * played again each heartbeat). Now at most `backoffMs.size` automatic re-plays per room command, each one only `backoffMs[n]` after
 * the failure pause before it; and none at all after a refused foreground start, which only the user or the room's next command
 * retries. The room's own commands are not counted here: they still apply at once.
 */
internal fun guestFailureResumeStep(
    attemptsMade: Int,
    msSinceFailurePause: Long,
    foregroundRefused: Boolean,
    gaveUp: Boolean,
    backoffMs: List<Long> = GUEST_FAILURE_RESUME_BACKOFF_MS,
): GuestFailureResumeStep =
    when {
        gaveUp -> GuestFailureResumeStep.Hold
        foregroundRefused -> GuestFailureResumeStep.GiveUp(attemptsMade, GUEST_RETRY_REASON_FOREGROUND_REFUSED)
        attemptsMade >= backoffMs.size -> GuestFailureResumeStep.GiveUp(attemptsMade, GUEST_RETRY_REASON_PLAYBACK_ERROR)
        msSinceFailurePause < backoffMs[attemptsMade] -> GuestFailureResumeStep.Wait
        else -> GuestFailureResumeStep.Resume(attemptsMade + 1)
    }

/** The shared-log line when the guest gives up (rule 4: numbers only). */
internal fun guestRetryExhaustedLogLine(
    attempts: Int,
    reason: Int,
): String = "LT_GUEST_RETRY_EXHAUSTED attempts=$attempts reason=$reason"

/**
 * Row 821: [guestFailureResumeStep]'s state for the room's current command. [reset] on every new room command (as
 * [GuestTrackGuard.onRoomCommand]), on the user's own resume and on leaving the room. Synchronized: the player's thread and the
 * bridge's collectors call it.
 */
internal class GuestFailureResumeGuard(
    private val backoffMs: List<Long> = GUEST_FAILURE_RESUME_BACKOFF_MS,
) {
    private var attempts = 0
    private var failurePausedAtMs: Long? = null
    private var gaveUp = false

    /** The guest's service has just paused it on a failure. */
    @Synchronized
    fun onFailurePause(nowMs: Long) {
        failurePausedAtMs = nowMs
    }

    /** A failure pause this guard did not see is timed from the first time it is asked. */
    @Synchronized
    fun decide(
        nowMs: Long,
        foregroundRefused: Boolean,
    ): GuestFailureResumeStep {
        val pausedAt = failurePausedAtMs ?: nowMs.also { failurePausedAtMs = it }
        val step = guestFailureResumeStep(attempts, nowMs - pausedAt, foregroundRefused, gaveUp, backoffMs)
        when (step) {
            is GuestFailureResumeStep.Resume -> attempts = step.attempt
            is GuestFailureResumeStep.GiveUp -> gaveUp = true
            GuestFailureResumeStep.Wait, GuestFailureResumeStep.Hold -> Unit
        }
        return step
    }

    @Synchronized
    fun reset() {
        attempts = 0
        failurePausedAtMs = null
        gaveUp = false
    }
}
