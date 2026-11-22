package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A late or drifting host re-anchors the room: what it last published, and when a drift is worth republishing. */
class HostDriftTest {
    @Test
    fun aDriftInsideTheGuestsDeadBandIsNotRepublished() {
        assertFalse(hostDriftNeedsRepublish(heardMs = 10_050L, roomMs = 10_000L))
        assertFalse(hostDriftNeedsRepublish(heardMs = 10_000L - HOST_DRIFT_REPUBLISH_MS, roomMs = 10_000L))
        assertTrue(HOST_DRIFT_REPUBLISH_MS > SyncCorrection.IN_SYNC_MS)
    }

    @Test
    fun aHostWhoseAudioStartedLateIsRepublishedEitherWay() {
        // the host published change_track at 0 and its audio started 1.5 s later: it is behind the room
        assertTrue(hostDriftNeedsRepublish(heardMs = 13_500L, roomMs = 15_000L))
        // a tempo above 1.0x leaves it ahead
        assertTrue(hostDriftNeedsRepublish(heardMs = 15_200L, roomMs = 15_000L))
    }

    @Test
    fun playAndPauseAnchorTheirOwnPlayState() {
        assertEquals(SentAnchor(1_000L, 50L, playing = true), nextSentAnchor(null, PlaybackActions.PLAY, 1_000L, 50L))
        assertEquals(SentAnchor(2_000L, 60L, playing = false), nextSentAnchor(null, PlaybackActions.PAUSE, 2_000L, 60L))
    }

    @Test
    fun aSeekKeepsWhetherTheRoomWasPlaying() {
        val playing = SentAnchor(1_000L, 50L, playing = true)
        assertEquals(SentAnchor(90_000L, 70L, playing = true), nextSentAnchor(playing, PlaybackActions.SEEK, 90_000L, 70L))
        assertEquals(SentAnchor(90_000L, 70L, playing = false), nextSentAnchor(null, PlaybackActions.SEEK, 90_000L, 70L))
    }

    @Test
    fun aTrackChangeIsNotPlayingUntilItsPlayFollows() {
        val playing = SentAnchor(100_000L, 50L, playing = true)
        assertEquals(SentAnchor(0L, 80L, playing = false), nextSentAnchor(playing, PlaybackActions.CHANGE_TRACK, 0L, 80L))
    }

    @Test
    fun aQueueUpdateMovesNoAnchor() {
        val playing = SentAnchor(1_000L, 50L, playing = true)
        assertEquals(playing, nextSentAnchor(playing, PlaybackActions.SYNC_QUEUE, 0L, 90L))
        assertNull(nextSentAnchor(null, PlaybackActions.SYNC_QUEUE, 0L, 90L))
    }
}
