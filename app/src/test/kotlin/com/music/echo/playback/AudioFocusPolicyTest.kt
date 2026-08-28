package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the AudioFocus-change decisions extracted from
 * MusicService.handleAudioFocusChange into AudioFocusPolicy (HALLAZGO-031, RONDA 3 FASE 1).
 *
 * Pinned facts: a focus GAIN restores the user volume (0 when muted) and schedules a resume ONLY
 * when the sticky "was playing before loss" flag is set and the player is not already playing; a
 * LOSS pauses but NEVER abandons the focus request (abandoning unregistered the listener, so the
 * returning GAIN after Google dictation / notifications never reached the app and playback stayed
 * paused forever — the owner report); every loss branch captures the resume intent from
 * playWhenReady (NOT isPlaying, which reads false while buffering and silently dropped the intent);
 * the flag is OR-sticky across interruption bursts (a second loss while already paused keeps it);
 * CAN_DUCK lowers the volume to 20% only while actually playing and never pauses; GAIN_TRANSIENT_
 * MAY_DUCK restores volume without resuming; an unknown focus change is a full no-op.
 */
class AudioFocusPolicyTest {

    private fun snapshot(
        hasAudioFocus: Boolean = true,
        isPlaying: Boolean = true,
        playWhenReady: Boolean = true,
        wasPlayingBeforeLoss: Boolean = false,
        isMuted: Boolean = false,
        userVolume: Float = 0.8f,
    ): AudioFocusPolicy.Snapshot = AudioFocusPolicy.Snapshot(
        hasAudioFocus = hasAudioFocus,
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        wasPlayingBeforeLoss = wasPlayingBeforeLoss,
        isMuted = isMuted,
        userVolume = userVolume,
    )

    // ------------------------------------------------------------------ GAIN

    @Test
    fun gainGrantsFocusAndRestoresVolume() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_GAIN,
            snapshot(hasAudioFocus = false, isPlaying = false, playWhenReady = false),
        )
        assertTrue(decision.hasAudioFocus)
        assertEquals(0.8f, decision.volume!!, 0.0001f)
        assertFalse(decision.pause)
        assertFalse(decision.abandonFocus)
    }

    @Test
    fun gainSchedulesResumeWhenWasPlayingAndNowPaused() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_GAIN,
            snapshot(hasAudioFocus = false, isPlaying = false, playWhenReady = false, wasPlayingBeforeLoss = true),
        )
        assertTrue(decision.scheduleResume)
    }

    @Test
    fun gainDoesNotResumeWithoutStickyFlag() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_GAIN,
            snapshot(hasAudioFocus = false, isPlaying = false, playWhenReady = false, wasPlayingBeforeLoss = false),
        )
        assertFalse(decision.scheduleResume)
    }

    @Test
    fun gainDoesNotResumeWhenAlreadyPlaying() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_GAIN,
            snapshot(isPlaying = true, wasPlayingBeforeLoss = true),
        )
        assertFalse(decision.scheduleResume)
    }

    @Test
    fun gainWhileMutedSetsZeroVolume() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_GAIN,
            snapshot(hasAudioFocus = false, isPlaying = false, playWhenReady = false, isMuted = true),
        )
        assertEquals(0f, decision.volume!!, 0.0001f)
    }

    @Test
    fun transientGainAlsoRestoresVolumeAndResumes() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_GAIN_TRANSIENT,
            snapshot(hasAudioFocus = false, isPlaying = false, playWhenReady = false, wasPlayingBeforeLoss = true),
        )
        assertTrue(decision.hasAudioFocus)
        assertEquals(0.8f, decision.volume!!, 0.0001f)
        assertTrue(decision.scheduleResume)
    }

    @Test
    fun mayDuckGainRestoresVolumeWithoutResuming() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_GAIN_TRANSIENT_MAY_DUCK,
            snapshot(hasAudioFocus = false, isPlaying = false, playWhenReady = false, wasPlayingBeforeLoss = true),
        )
        assertTrue(decision.hasAudioFocus)
        assertEquals(0.8f, decision.volume!!, 0.0001f)
        assertFalse(decision.scheduleResume)
    }

    // ------------------------------------------------------------------ LOSS

    @Test
    fun lossPausesAndKeepsResumeIntent() {
        val decision = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_LOSS, snapshot())
        assertTrue(decision.pause)
        assertTrue(decision.wasPlayingBeforeLoss)
        assertFalse(decision.hasAudioFocus)
    }

    @Test
    fun lossNeverAbandonsTheFocusRequest() {
        // The 031 fix: abandoning on LOSS unregistered the listener, so the returning GAIN after
        // the interruptor (dictation, call, navigation prompt) released focus never arrived and
        // playback never resumed. The request is only abandoned on stop/onDestroy.
        for (change in listOf(
            AudioFocusPolicy.FOCUS_LOSS,
            AudioFocusPolicy.FOCUS_LOSS_TRANSIENT,
            AudioFocusPolicy.FOCUS_LOSS_TRANSIENT_CAN_DUCK,
        )) {
            val decision = AudioFocusPolicy.decide(change, snapshot())
            assertFalse("change=$change must not abandon focus", decision.abandonFocus)
        }
    }

    @Test
    fun lossCapturesIntentFromPlayWhenReadyWhileBuffering() {
        // Buffering: isPlaying=false but playWhenReady=true. The old isPlaying capture read false
        // here and dropped the resume intent even though the user clearly wanted playback.
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_LOSS,
            snapshot(isPlaying = false, playWhenReady = true, wasPlayingBeforeLoss = false),
        )
        assertTrue(decision.wasPlayingBeforeLoss)
        assertFalse(decision.pause)
    }

    @Test
    fun lossDuringManualPauseKeepsNoResumeIntent() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_LOSS,
            snapshot(isPlaying = false, playWhenReady = false, wasPlayingBeforeLoss = false),
        )
        assertFalse(decision.wasPlayingBeforeLoss)
        assertFalse(decision.pause)
    }

    @Test
    fun lossFlagIsStickyAcrossInterruptionBursts() {
        // Second loss lands while already paused by the first one (playWhenReady=false after
        // pause()): the OR-sticky flag must survive, or the delayed resume never fires.
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_LOSS_TRANSIENT,
            snapshot(isPlaying = false, playWhenReady = false, wasPlayingBeforeLoss = true),
        )
        assertTrue(decision.wasPlayingBeforeLoss)
    }

    @Test
    fun transientLossPausesLikeFullLoss() {
        val decision = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_LOSS_TRANSIENT, snapshot())
        assertTrue(decision.pause)
        assertTrue(decision.wasPlayingBeforeLoss)
        assertFalse(decision.hasAudioFocus)
        assertNull(decision.volume)
    }

    // ------------------------------------------------------------------ DUCK

    @Test
    fun duckLowersVolumeToTwentyPercentWhilePlaying() {
        val decision = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_LOSS_TRANSIENT_CAN_DUCK, snapshot())
        assertEquals(0.16f, decision.volume!!, 0.0001f)
        assertFalse(decision.pause)
        assertTrue(decision.wasPlayingBeforeLoss)
        assertFalse(decision.hasAudioFocus)
    }

    @Test
    fun duckWhileMutedSetsZeroVolume() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_LOSS_TRANSIENT_CAN_DUCK,
            snapshot(isMuted = true),
        )
        assertEquals(0f, decision.volume!!, 0.0001f)
    }

    @Test
    fun duckDoesNotTouchVolumeWhenNotPlaying() {
        val decision = AudioFocusPolicy.decide(
            AudioFocusPolicy.FOCUS_LOSS_TRANSIENT_CAN_DUCK,
            snapshot(isPlaying = false, playWhenReady = true),
        )
        assertNull(decision.volume)
        assertTrue(decision.wasPlayingBeforeLoss)
    }

    // ------------------------------------------------------------------ unknown + sequences

    @Test
    fun unknownFocusChangeIsAFullNoOp() {
        val decision = AudioFocusPolicy.decide(999, snapshot(hasAudioFocus = true, wasPlayingBeforeLoss = true))
        assertTrue(decision.hasAudioFocus)
        assertTrue(decision.wasPlayingBeforeLoss)
        assertFalse(decision.pause)
        assertNull(decision.volume)
        assertFalse(decision.scheduleResume)
        assertFalse(decision.abandonFocus)
    }

    @Test
    fun dictationSequenceLossThenReturningGainResumes() {
        // Owner report (031): playing -> Google dictation takes permanent focus (LOSS) -> dictation
        // ends -> GAIN comes back because the request was NOT abandoned -> resume + full volume.
        var state = snapshot(hasAudioFocus = true, isPlaying = true, playWhenReady = true)

        val loss = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_LOSS, state)
        assertTrue(loss.pause)
        assertFalse(loss.abandonFocus)
        state = state.copy(
            hasAudioFocus = loss.hasAudioFocus,
            isPlaying = false,
            playWhenReady = false, // player.pause() clears playWhenReady
            wasPlayingBeforeLoss = loss.wasPlayingBeforeLoss,
        )

        val gain = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_GAIN, state)
        assertTrue(gain.hasAudioFocus)
        assertTrue(gain.scheduleResume)
        assertEquals(0.8f, gain.volume!!, 0.0001f)
    }

    @Test
    fun notificationBurstSequenceKeepsResumeIntentAcrossLosses() {
        // Notification 1: LOSS_TRANSIENT while playing. Notification 2 lands inside the 300ms
        // resume window: already paused, playWhenReady=false. The flag must survive both.
        var state = snapshot(hasAudioFocus = true, isPlaying = true, playWhenReady = true)

        val first = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_LOSS_TRANSIENT, state)
        state = state.copy(
            hasAudioFocus = first.hasAudioFocus,
            isPlaying = false,
            playWhenReady = false,
            wasPlayingBeforeLoss = first.wasPlayingBeforeLoss,
        )

        val second = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_LOSS_TRANSIENT, state)
        assertTrue(second.wasPlayingBeforeLoss)

        state = state.copy(wasPlayingBeforeLoss = second.wasPlayingBeforeLoss)
        val gain = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_GAIN, state)
        assertTrue(gain.scheduleResume)
    }

    @Test
    fun duckThenLossThenGainRestoresFullVolume() {
        // Duck stuck at 0.2x only if the restoring GAIN never arrives; with no-abandon it does,
        // and the GAIN branch always writes the full user volume back.
        var state = snapshot(hasAudioFocus = true, isPlaying = true, playWhenReady = true)

        val duck = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_LOSS_TRANSIENT_CAN_DUCK, state)
        state = state.copy(hasAudioFocus = duck.hasAudioFocus, wasPlayingBeforeLoss = duck.wasPlayingBeforeLoss)

        val loss = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_LOSS_TRANSIENT, state)
        state = state.copy(
            hasAudioFocus = loss.hasAudioFocus,
            isPlaying = false,
            playWhenReady = false,
            wasPlayingBeforeLoss = loss.wasPlayingBeforeLoss,
        )

        val gain = AudioFocusPolicy.decide(AudioFocusPolicy.FOCUS_GAIN, state)
        assertEquals(0.8f, gain.volume!!, 0.0001f)
        assertTrue(gain.scheduleResume)
    }
}
