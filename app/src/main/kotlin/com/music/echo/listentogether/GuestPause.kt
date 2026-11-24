package iad1tya.echo.music.listentogether

/**
 * Row 816 (owner 2026-11-24: «a veces doy play en un dispositivo y el otro solo pone la canción pero no le da play ni la
 * continúa»): does a pause this guest's player made by itself — not one of the room's own calls — keep the guest out of the
 * room's play and pause?
 *
 * It used to, for EVERY cause, until the user pressed play here: the guest then loaded each next track of the room paused and
 * ignored every PLAY. The service also pauses when it gives up on playback by itself (`MusicService.pausedByPlaybackFailure`:
 * the error stop, the end of an error-skip chain, a foreground start the system refused), and none of those is the user
 * leaving the room's music, so the room keeps driving the guest after them. Every other pause still holds the guest: the
 * user's own, and the service's pauses for the device's audio situation (audio focus lost to a call or another app, headphones
 * unplugged, «pause on mute», the sleep timer). The service resumes those itself when the situation ends (and that resume
 * rejoins the room through `requestSync`), and the room must not undo them: it would play over a call, out of the speaker, or
 * through the sleep timer.
 */
internal fun guestPausedLocally(
    playWhenReady: Boolean,
    inRoom: Boolean,
    isHost: Boolean,
    pausedByPlaybackFailure: Boolean,
): Boolean = !playWhenReady && inRoom && !isHost && !pausedByPlaybackFailure

/**
 * The shared-log line for a change of [guestPausedLocally] (rule 4: booleans and media3's reason number only). [reason] is media3's
 * `PLAY_WHEN_READY_CHANGE_REASON_*` (1 user request, 2 audio focus loss, 3 becoming noisy, 4 remote, 5 end of item, 6 suppressed
 * too long), or [PAUSED_LOCALLY_REASON_NONE] when no player change caused it (leaving the room, a player attached).
 */
internal fun pausedLocallyLogLine(
    pausedLocally: Boolean,
    reason: Int,
    deliberate: Boolean,
    playbackFailure: Boolean,
): String = "LT_PAUSED_LOCALLY=$pausedLocally reason=$reason deliberate=$deliberate failure=$playbackFailure"

/** [pausedLocallyLogLine]'s reason when the change did not come from the player. */
internal const val PAUSED_LOCALLY_REASON_NONE = -1

/**
 * Row 817: a guest pressed play while the room is paused. The bridge pauses it again (the host decides when the room plays), and
 * now says so with a short notice instead of doing it silently.
 */
internal fun roomRefusesGuestResume(
    inRoom: Boolean,
    isHost: Boolean,
    roomPlaying: Boolean,
    hasRoomTrack: Boolean,
): Boolean = inRoom && !isHost && !roomPlaying && hasRoomTrack
