package iad1tya.echo.music.playback

import iad1tya.echo.music.constants.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the delivered-container classification extracted verbatim from the three inline
 * copies in MusicService (refresh-ahead renewal, upcoming-track preload, post-resolve container guard)
 * into DeliveredQuality (HALLAZGO-021 split, phase A).
 *
 * Pinned facts: matching is case-insensitive; flac wins over mp4/m4a when a MIME type mentions both
 * (the `when` checks flac first); everything that is neither flac nor an mp4/m4a container is OPUS;
 * and [DeliveredQuality.isLossless] / [DeliveredQuality.isSaavn] are INDEPENDENT booleans — the
 * container guard computes both of them, so a MIME type mentioning both markers must report true for
 * both (registry lessons #40/#57).
 */
class DeliveredQualityTest {

    @Test
    fun flacContainerIsLossless() {
        assertEquals(AudioQuality.LOSSLESS, DeliveredQuality.fromMimeType("audio/flac"))
    }

    @Test
    fun flacMatchingIsCaseInsensitive() {
        assertEquals(AudioQuality.LOSSLESS, DeliveredQuality.fromMimeType("audio/FLAC"))
        assertEquals(AudioQuality.LOSSLESS, DeliveredQuality.fromMimeType("FLAC"))
    }

    @Test
    fun flacWithParametersIsLossless() {
        assertEquals(AudioQuality.LOSSLESS, DeliveredQuality.fromMimeType("audio/flac; codecs=flac"))
    }

    @Test
    fun mp4ContainerIsSaavn() {
        assertEquals(AudioQuality.SAAVN, DeliveredQuality.fromMimeType("audio/mp4"))
    }

    @Test
    fun m4aContainerIsSaavn() {
        assertEquals(AudioQuality.SAAVN, DeliveredQuality.fromMimeType("audio/m4a"))
    }

    @Test
    fun mp4MatchingIsCaseInsensitive() {
        assertEquals(AudioQuality.SAAVN, DeliveredQuality.fromMimeType("audio/MP4"))
        assertEquals(AudioQuality.SAAVN, DeliveredQuality.fromMimeType("M4A"))
    }

    @Test
    fun mp4WithCodecsParameterIsSaavn() {
        assertEquals(AudioQuality.SAAVN, DeliveredQuality.fromMimeType("audio/mp4; codecs=mp4a.40.2"))
    }

    @Test
    fun opusContainerIsOpus() {
        assertEquals(AudioQuality.OPUS, DeliveredQuality.fromMimeType("audio/webm; codecs=opus"))
    }

    @Test
    fun unknownOrEmptyContainerIsOpus() {
        assertEquals(AudioQuality.OPUS, DeliveredQuality.fromMimeType(""))
        assertEquals(AudioQuality.OPUS, DeliveredQuality.fromMimeType("audio/mpeg"))
        assertEquals(AudioQuality.OPUS, DeliveredQuality.fromMimeType("application/octet-stream"))
    }

    /** The `when` checks flac FIRST: a MIME type mentioning both markers classifies as LOSSLESS. */
    @Test
    fun flacWinsOverMp4WhenBothPresent() {
        assertEquals(AudioQuality.LOSSLESS, DeliveredQuality.fromMimeType("audio/flac; profile=mp4"))
    }

    /** The guard booleans are independent: both markers present -> both true (guard computes both). */
    @Test
    fun guardBooleansAreIndependent() {
        assertTrue(DeliveredQuality.isLossless("audio/flac; profile=mp4"))
        assertTrue(DeliveredQuality.isSaavn("audio/flac; profile=mp4"))
    }

    @Test
    fun guardBooleansSingleMarkers() {
        assertTrue(DeliveredQuality.isLossless("audio/flac"))
        assertFalse(DeliveredQuality.isSaavn("audio/flac"))

        assertFalse(DeliveredQuality.isLossless("audio/mp4"))
        assertTrue(DeliveredQuality.isSaavn("audio/mp4"))

        assertFalse(DeliveredQuality.isLossless("audio/webm; codecs=opus"))
        assertFalse(DeliveredQuality.isSaavn("audio/webm; codecs=opus"))
    }

    /** fromMimeType agrees with the boolean pair exactly the way the original `when` did. */
    @Test
    fun fromMimeTypeAgreesWithBooleanPair() {
        for (mime in listOf("audio/flac", "audio/mp4", "audio/m4a", "audio/webm", "", "audio/FLAC")) {
            val expected = when {
                DeliveredQuality.isLossless(mime) -> AudioQuality.LOSSLESS
                DeliveredQuality.isSaavn(mime) -> AudioQuality.SAAVN
                else -> AudioQuality.OPUS
            }
            assertEquals(mime, expected, DeliveredQuality.fromMimeType(mime))
        }
    }
}
