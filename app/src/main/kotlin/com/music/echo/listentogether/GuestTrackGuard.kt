package iad1tya.echo.music.listentogether

/** How many times a guest reloads the room's current track before it stops trying for that track (row 796). */
internal const val MAX_GUEST_TRACK_RELOADS = 3

/** How long past the end of the room's track a guest that finished it first waits for the host's change_track (row 616). */
internal const val GUEST_TRACK_HANDOFF_MS = 2_000L

/** What a guest whose player may not be on the room's current track does about it ([GuestTrackGuard.decide]). */
internal sealed interface GuestTrackStep {
    /** The player is on the room's track: nothing to do. */
    data object OnRoomTrack : GuestTrackStep

    /** Nothing now; look again in [inMs]. */
    data class Wait(val inMs: Long) : GuestTrackStep

    /** Load the room's track; [attempt] counts from 1 for this room track. */
    data class Reload(val attempt: Int) : GuestTrackStep

    /** Stop playing the song the room did not choose, and wait for the room's next command. */
    data object Hold : GuestTrackStep
}

/**
 * Row 796 (owner 2026-11-24: «a veces un dispositivo está en una canción y el otro en otra»): a guest may never keep playing a song
 * that is not the room's current track.
 *
 * The guest's player leaves the room's track on its own: an error skip when the stream fails here, an offline skip, a «No me gusta»
 * synced from another device, or simply finishing the song before the host. The bridge used to reload the room's track ONCE per
 * (track, anchor) and then return without retrying, so a reload that failed (the error path skips to the next song again) left the
 * guest on a different song until the host happened to send another command; and it only looked at the next 15 s heartbeat.
 *
 * Now, per room track: up to [maxReloads] reloads, at least [minSpacingMs] apart; after that the guest holds (pauses) instead of
 * playing a song the room never chose. A guest that finished the room's song first waits for the host's change_track while the room
 * is inside [handoffMs] of that song's end (it may already be playing its own next song, which is normally the room's next too), once,
 * and then holds. Every Wait is bounded, so the caller's rechecks end. Synchronized: the bridge's collectors and the player's thread
 * call it.
 */
internal class GuestTrackGuard(
    private val maxReloads: Int = MAX_GUEST_TRACK_RELOADS,
    private val minSpacingMs: Long = REALIGN_MIN_SPACING_MS,
    private val handoffMs: Long = GUEST_TRACK_HANDOFF_MS,
) {
    private var trackId: String? = null
    private var reloads = 0
    private var lastReloadAtMs: Long? = null
    private var handoffWaited = false

    /**
     * @param loadedId the media id the player is on (null = none).
     * @param cameFromRoomTrack the player's previous item is the room's track (it moved on from it by itself).
     * @param roomPositionMs where the room is now on its track, NOT capped to the track's length.
     * @param roomDurationMs the room's track length, 0 when unknown.
     */
    @Synchronized
    fun decide(
        roomTrackId: String,
        loadedId: String?,
        cameFromRoomTrack: Boolean,
        roomPositionMs: Long,
        roomDurationMs: Long,
        roomPlaying: Boolean,
        nowMs: Long,
    ): GuestTrackStep {
        if (roomTrackId != trackId) {
            trackId = roomTrackId
            reloads = 0
            lastReloadAtMs = null
            handoffWaited = false
        }
        // The counters are NOT reset here: a reload that lands and then fails again (the error path skips on) must count.
        if (loadedId == roomTrackId) return GuestTrackStep.OnRoomTrack
        if (cameFromRoomTrack && roomDurationMs > 0L && roomPositionMs >= roomDurationMs - handoffMs) {
            if (!roomPlaying) return GuestTrackStep.Hold
            val waitMs = roomDurationMs + handoffMs - roomPositionMs
            if (waitMs <= 0L || handoffWaited) return GuestTrackStep.Hold
            handoffWaited = true
            return GuestTrackStep.Wait(waitMs)
        }
        if (reloads >= maxReloads) return GuestTrackStep.Hold
        val last = lastReloadAtMs
        if (last != null && nowMs - last < minSpacingMs) return GuestTrackStep.Wait(minSpacingMs - (nowMs - last))
        reloads++
        lastReloadAtMs = nowMs
        return GuestTrackStep.Reload(reloads)
    }

    /** Leaving the room, or the user resuming this guest by hand: the room's track gets its reloads again. */
    @Synchronized
    fun reset() {
        trackId = null
        reloads = 0
        lastReloadAtMs = null
        handoffWaited = false
    }
}
