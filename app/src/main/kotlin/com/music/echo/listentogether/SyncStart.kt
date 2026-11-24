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

/**
 * `hostPlaying` for the host's change_track. Null — no synchronized start: the room keeps the old barrier, with its clock running — when
 * the host already HEARS the new track (the song ended, gapless or crossfade; repeat; a pre-buffered next): guests advance in place in
 * step with it (row 616), and a hold arriving one round trip later would pause and rewind everyone. Otherwise, whether it means to play.
 */
internal fun changeTrackHostPlaying(
    hostHearing: Boolean,
    intent: Boolean,
    holding: Boolean,
): Boolean? = if (hostHearing) null else intent || holding

/**
 * `hostPlaying` for the PLAY that follows the host's change_track. True when that change_track opened a synchronized start (the server
 * absorbs this PLAY), or left the room paused while guests loaded the track paused too (the server starts everyone together on it).
 * Null when guests already play it — they carry «playing» across a track change (lastRoomPlaying), so a barrier now would pull them
 * back to the start — or when the host already hears it.
 */
internal fun playStartsTogether(
    changeHostPlaying: Boolean?,
    roomWasPlaying: Boolean,
    hostHearing: Boolean,
): Boolean? =
    when {
        changeHostPlaying == true -> true
        changeHostPlaying == false && !roomWasPlaying && !hostHearing -> true
        else -> null
    }

/** What a guest does when a synchronized start's hold lets go of it without having started it ([guestAfterHoldRelease]). */
internal data class HoldReleasePlan(
    /** Ask the server where the room is (`request_sync`): the room state on this device may be older than the room. */
    val askRoom: Boolean,
    /** Apply the room state this device has, as before. */
    val applyRoomState: Boolean,
)

/**
 * Row 816 (C2): a hold let go after the `play` that named its start time (a pause came during the wait, or the clock could not time
 * it) applies the room state this device has, which that `play` brought. A hold let go WITHOUT a start time (the 12.4 s
 * [SYNC_START_HOLD_MAX_MS] fallback, a barrier that ended without one, or a socket that came back during the barrier and so missed
 * the `play`) is older than the room: the change_track left «not playing», and applying that kept the guest paused on the loaded
 * song. Then it asks the room, and applies its state now only when that state already says «playing».
 */
internal fun guestAfterHoldRelease(
    startTimeNamed: Boolean,
    roomPlaying: Boolean,
): HoldReleasePlan =
    if (startTimeNamed) HoldReleasePlan(askRoom = false, applyRoomState = true) else HoldReleasePlan(askRoom = true, applyRoomState = roomPlaying)

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
