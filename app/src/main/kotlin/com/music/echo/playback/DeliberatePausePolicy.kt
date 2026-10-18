package iad1tya.echo.music.playback

/**
 * A pause the USER chose is never undone by anything but the user.
 *
 * Owner, twice (2026-09-16 and 2026-10-18): *«le puse pausa, no estoy haciendo nada, me salgo y al rato de la nada se
 * dispara»*. The first round (registry 241) only instrumented the two doors it found, because it had no log. These
 * are the two doors, closed on the one principle the rest of the service already follows ("a manual pause is never
 * auto-resumed", see `pausedByNetwork` / `wasPlayingBeforeAudioFocusLoss`):
 *
 *  1. `MusicService.onAudioDevicesAdded` with "Reanudar al conectar Bluetooth": no time limit and no requirement
 *     that the user ever paused, so ANY headset / car / watch / TV that connects started playback hours after a
 *     deliberate pause. Now it needs the pause NOT to be deliberate.
 *  2. An external PLAY (the Bluetooth stack replays AVRCP PLAY when a headset reconnects) accepted after a
 *     deliberate pause, seconds after a playback route appeared. Now rejected inside [EXTERNAL_PLAY_SHIELD_MS]
 *     of a route connecting. A press on the headset button 10 s later still works.
 *
 * Pure so the decision table is pinned in a unit test without a device.
 */
object DeliberatePausePolicy {

    /** How long after an audio route connects a foreign PLAY is treated as the stack's reconnect echo. */
    const val EXTERNAL_PLAY_SHIELD_MS = 10_000L

    /** Mirrors `Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST` by value (kept Android-free for tests). */
    const val REASON_USER_REQUEST = 1

    /**
     * Was this pause the user's? media3 reports OUR OWN pauses (audio focus loss, error stop, volume mute) with the
     * same USER_REQUEST reason as a real tap, so the service's own "I am about to pause" flags rule them out.
     */
    fun isDeliberate(
        reason: Int,
        ownFocusPause: Boolean,
        ownStopPause: Boolean,
        pausedByVolumeMute: Boolean,
    ): Boolean = reason == REASON_USER_REQUEST && !ownFocusPause && !ownStopPause && !pausedByVolumeMute

    /** May a newly connected playback route start playback by itself? */
    fun mayResumeOnRouteConnect(
        resumeSetting: Boolean,
        androidAutoNoisyResume: Boolean,
        deliberatePause: Boolean,
    ): Boolean = androidAutoNoisyResume || (resumeSetting && !deliberatePause)

    /** Should a PLAY from another app be rejected right now? */
    fun shouldRejectExternalPlay(
        deliberatePause: Boolean,
        playerIsPlaying: Boolean,
        fromOwnPackage: Boolean,
        sinceRouteConnectedMs: Long,
    ): Boolean = deliberatePause && !playerIsPlaying && !fromOwnPackage &&
        sinceRouteConnectedMs in 0..EXTERNAL_PLAY_SHIELD_MS
}
