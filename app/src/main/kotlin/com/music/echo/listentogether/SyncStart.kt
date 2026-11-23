package iad1tya.echo.music.listentogether

/**
 * A synchronized start announced by Aura's rooms server (docs/worker/rooms, W2): every device, the host too, holds [trackId] paused at
 * [position] while the barrier is open, and plays at [startAtServerTime] on the server's clock once the `play` that closes it names it.
 * Metrolist's server and older Aura servers never announce one, so with them nothing here ever holds.
 */
data class SyncStart(
    val trackId: String,
    val position: Long,
    /** The `buffer_wait` that opened it: two barriers on the same track and position are still two. */
    val revision: Int,
    /** 0 while the barrier is open. */
    val startAtServerTime: Long = 0L,
    /** This device has started (or let go): the hold is over and nothing more is timed. */
    val started: Boolean = false,
)

/** The lead the server gives between closing a barrier and its start time (docs/worker/rooms `SYNC_START_LEAD_MS`). */
internal const val SYNC_START_LEAD_MS = 400L

/**
 * How long a hold may wait for its start time before the device lets go and follows the room as before. The server closes every barrier
 * within its 10 s cap (docs/worker/rooms `BUFFER_TIMEOUT_MS`) and names the start [SYNC_START_LEAD_MS] later; the last 2 s are room for
 * that message to arrive.
 */
internal const val SYNC_START_HOLD_MAX_MS = 10_000L + SYNC_START_LEAD_MS + 2_000L

/**
 * Whether a device holds paused for [hold] while the room's track (the host's own, on the host) is [roomTrackId]: until it has started,
 * and only on the hold's own track — a barrier for the next song does not stop the one still playing.
 */
internal fun syncStartHolds(hold: SyncStart?, roomTrackId: String?): Boolean =
    hold != null && !hold.started && (hold.trackId.isBlank() || roomTrackId.isNullOrBlank() || hold.trackId == roomTrackId)

/** The hold after a `buffer_wait`: a synchronized one opens a new hold, an old-style one means nobody holds. */
internal fun syncStartAfterBufferWait(p: BufferWaitPayload, revision: Int): SyncStart? =
    if (p.syncStart) SyncStart(trackId = p.trackId, position = p.startPosition, revision = revision) else null

/** The hold after `buffer_complete`: a barrier that ended without naming a start time (cancelled, or the room paused) holds no one. */
internal fun syncStartAfterBufferComplete(previous: SyncStart?): SyncStart? = previous?.takeIf { it.startAtServerTime > 0L }

/**
 * The hold after a transport command [p] reaches this device. The `play` that closes a barrier carries the start time (and is a hold of
 * its own for someone who arrived during the barrier); any other play or a pause means the room moved on; a new track ends a hold for
 * another track; seeks and queue updates leave it as it is (a new synchronized jump always brings its own `buffer_wait` first).
 */
internal fun syncStartAfterCommand(previous: SyncStart?, p: PlaybackActionPayload, revision: Int): SyncStart? =
    when (p.action) {
        PlaybackActions.PLAY ->
            if (p.startAtServerTime > 0L) {
                SyncStart(
                    trackId = p.trackId.ifBlank { p.trackInfo?.id ?: previous?.trackId.orEmpty() },
                    position = p.position,
                    revision = previous?.revision ?: revision,
                    startAtServerTime = p.startAtServerTime,
                )
            } else {
                null
            }
        PlaybackActions.PAUSE -> null
        PlaybackActions.CHANGE_TRACK -> previous?.takeIf { it.trackId == (p.trackInfo?.id ?: p.trackId) }
        else -> previous
    }

/** When to call play() for a synchronized start, and with the decoder at which position. */
internal data class SyncStartTiming(
    val waitMs: Long,
    val fromPosition: Long,
)

/**
 * The play() for a start at [startAtServerTime] from [startPosition]: [outputLatencyMs] early, so the sound leaves THIS device's output at
 * the start time (row 262: every device compensates its own output, and positions in the room are what is heard); a little late, from
 * where the room already is. Null when there is nothing to time — no start time (older server, Metrolist), an uncalibrated clock, or a
 * start further in the past than the server's lead — and then the device follows the room as it always did. A wait longer than the
 * server's lead can only be a clock error, so it is never waited out.
 */
internal fun syncStartTiming(
    startAtServerTime: Long,
    serverNow: Long?,
    outputLatencyMs: Int,
    startPosition: Long,
): SyncStartTiming? {
    if (startAtServerTime <= 0L || serverNow == null) return null
    val wait = startAtServerTime - outputLatencyMs - serverNow
    return when {
        wait >= 0L -> SyncStartTiming(wait.coerceAtMost(SYNC_START_LEAD_MS), startPosition)
        -wait <= SYNC_START_LEAD_MS -> SyncStartTiming(0L, startPosition - wait)
        else -> null
    }
}
