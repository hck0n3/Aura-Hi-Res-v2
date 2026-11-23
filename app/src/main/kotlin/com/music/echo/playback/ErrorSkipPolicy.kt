package iad1tya.echo.music.playback

/**
 * Skipping past a song that failed must never START music the user had not asked for.
 *
 * `MusicService.skipOnError` used to call `play()` whenever it was not casting, so a queue restored paused at startup
 * whose first item failed, or a paused queue whose item errored, began playing by itself (the phantom start of
 * registry 27, and the remote-mode self-starts of 657-664 / 681-686). media3 keeps `playWhenReady` through a fatal
 * error (`PlaybackInfo.copyWithPlaybackError` and `ExoPlayerImplInternal.resetInternal` carry it over, media3 1.11.1),
 * so the value read when the error arrives is the user's intent.
 *
 * Pure so the decision is pinned in a unit test without a device.
 */
object ErrorSkipPolicy {
    /** Resume after the skip only if playback was intended when the error arrived, and never while casting. */
    fun shouldResumeAfterErrorSkip(wasPlayWhenReady: Boolean, casting: Boolean): Boolean = wasPlayWhenReady && !casting
}
