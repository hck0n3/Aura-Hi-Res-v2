package iad1tya.echo.music.playback

/**
 * Pure decision core for AudioFocus changes extracted from
 * [MusicService.handleAudioFocusChange] (HALLAZGO-031, RONDA 3 FASE 1).
 *
 * The service keeps the side effects (pausing, setting volume, the delayed resume coroutine with
 * its cast check, abandoning the focus request); this object only decides WHAT to do for each
 * focus change given a snapshot of the player state, so the decision table can be characterized
 * without a device.
 *
 * The two 031 fixes pinned here:
 *  1. No loss branch abandons the focus request. Abandoning on AUDIOFOCUS_LOSS unregistered the
 *     listener, so when the interruptor (Google dictation, a call, a navigation prompt) released
 *     focus the returning AUDIOFOCUS_GAIN had no registered owner to deliver to, and playback
 *     stayed paused until the user pressed Play. The request is abandoned only on stop/onDestroy.
 *  2. Every loss branch captures the resume intent from playWhenReady, NOT isPlaying. isPlaying
 *     reads false while buffering, silently dropping the intent for a track that was loading.
 */
object AudioFocusPolicy {

    // Mirror the AudioManager constants by value so the core stays Android-free in unit tests.
    const val FOCUS_GAIN = 1
    const val FOCUS_GAIN_TRANSIENT = 2
    const val FOCUS_GAIN_TRANSIENT_MAY_DUCK = 3
    const val FOCUS_LOSS = -1
    const val FOCUS_LOSS_TRANSIENT = -2
    const val FOCUS_LOSS_TRANSIENT_CAN_DUCK = -3

    /** Fraction of the user volume used while ducking under AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK. */
    const val DUCK_VOLUME_FACTOR = 0.2f

    /**
     * The state the decision depends on. [wasPlayingBeforeLoss] is the sticky flag that survives a
     * whole interruption burst and is only cleared by an actual resume. [hasAudioFocus] is only
     * needed to pass it through unchanged on unknown focus changes.
     */
    data class Snapshot(
        val hasAudioFocus: Boolean,
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val wasPlayingBeforeLoss: Boolean,
        val isMuted: Boolean,
        val userVolume: Float,
    )

    /**
     * The decided effects. [volume] is null when the player volume must not be touched.
     * [abandonFocus] is never true for any focus change: see the object KDoc.
     */
    data class Decision(
        val hasAudioFocus: Boolean,
        val wasPlayingBeforeLoss: Boolean,
        val pause: Boolean,
        val volume: Float?,
        val scheduleResume: Boolean,
        val abandonFocus: Boolean,
    )

    fun decide(focusChange: Int, snapshot: Snapshot): Decision = when (focusChange) {
        FOCUS_GAIN,
        FOCUS_GAIN_TRANSIENT -> Decision(
            hasAudioFocus = true,
            wasPlayingBeforeLoss = snapshot.wasPlayingBeforeLoss,
            pause = false,
            volume = if (snapshot.isMuted) 0f else snapshot.userVolume,
            scheduleResume = snapshot.wasPlayingBeforeLoss && !snapshot.isPlaying,
            abandonFocus = false,
        )

        FOCUS_GAIN_TRANSIENT_MAY_DUCK -> Decision(
            hasAudioFocus = true,
            wasPlayingBeforeLoss = snapshot.wasPlayingBeforeLoss,
            pause = false,
            volume = if (snapshot.isMuted) 0f else snapshot.userVolume,
            scheduleResume = false,
            abandonFocus = false,
        )

        FOCUS_LOSS,
        FOCUS_LOSS_TRANSIENT -> Decision(
            hasAudioFocus = false,
            // OR-sticky, not an overwrite: a burst of notification sounds fires loss/gain pairs
            // back to back, and a second loss landing inside the delayed-resume window finds the
            // player already paused (playWhenReady=false) — overwriting would stomp the intent.
            wasPlayingBeforeLoss = snapshot.playWhenReady || snapshot.wasPlayingBeforeLoss,
            pause = snapshot.isPlaying,
            volume = null,
            scheduleResume = false,
            abandonFocus = false,
        )

        FOCUS_LOSS_TRANSIENT_CAN_DUCK -> Decision(
            hasAudioFocus = false,
            wasPlayingBeforeLoss = snapshot.playWhenReady || snapshot.wasPlayingBeforeLoss,
            pause = false,
            volume = if (snapshot.isPlaying) {
                if (snapshot.isMuted) 0f else snapshot.userVolume * DUCK_VOLUME_FACTOR
            } else {
                null
            },
            scheduleResume = false,
            abandonFocus = false,
        )

        else -> Decision(
            hasAudioFocus = snapshot.hasAudioFocus,
            wasPlayingBeforeLoss = snapshot.wasPlayingBeforeLoss,
            pause = false,
            volume = null,
            scheduleResume = false,
            abandonFocus = false,
        )
    }
}
