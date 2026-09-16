package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every video must be playable as audio.
 *
 * 🔴 OWNER REPORT (2026-09-16): *"algunos videos solo reproducían video"*. The audio resolver searched
 * only `adaptiveFormats.filter { it.isAudio }`, and `isAudio` is `width == null` — so a PROGRESSIVE
 * stream (itag 18 / 22, picture and sound in one file) was invisible to it, while the video resolver
 * deliberately searched both lists. A video whose response carries no adaptive audio therefore played as
 * video and refused to play as audio, even though the progressive stream in hand had the audio in it.
 *
 * These pin the fallback that fixes it, including the two ways it could go wrong quietly: picking a
 * stream with no audio track (silence, which is worse than the original failure) and pulling 720p over
 * mobile data to listen to a song.
 */
class ProgressiveAudioFallbackTest {
    private fun stream(
        itag: Int,
        bitrate: Int,
        hasAudioTrack: Boolean = true,
        hasUrl: Boolean = true,
    ) = ProgressiveAudioFallback.Stream(itag, bitrate, hasAudioTrack, hasUrl)

    /** The usual shape of a response with no adaptive audio: itag 18 (360p) and 22 (720p). */
    private val typical = listOf(stream(18, 96_000), stream(22, 192_000))

    @Test
    fun `on wifi it takes the better audio, which is the bigger progressive stream`() {
        val index = ProgressiveAudioFallback.pickIndex(typical, preferSmallest = false)
        assertEquals("itag 22 carries ~192 kbps AAC against itag 18's ~96", 1, index)
    }

    @Test
    fun `on mobile data or data saver it takes the small one`() {
        // Progressive means paying for a picture nobody watches, so bytes win over bitrate here.
        val index = ProgressiveAudioFallback.pickIndex(typical, preferSmallest = true)
        assertEquals(0, index)
    }

    @Test
    fun `a stream with no audio track is never chosen`() {
        // Silence is worse than the failure this replaces: the song would look like it is playing.
        val streams = listOf(
            stream(137, 2_000_000, hasAudioTrack = false),
            stream(18, 96_000, hasAudioTrack = true),
        )

        assertEquals(1, ProgressiveAudioFallback.pickIndex(streams, preferSmallest = false))
        assertEquals(1, ProgressiveAudioFallback.pickIndex(streams, preferSmallest = true))
    }

    @Test
    fun `a stream with neither url nor cipher is never chosen`() {
        val streams = listOf(
            stream(22, 192_000, hasUrl = false),
            stream(18, 96_000, hasUrl = true),
        )

        assertEquals("an unplayable entry must not win on bitrate", 1, ProgressiveAudioFallback.pickIndex(streams, preferSmallest = false))
    }

    @Test
    fun `nothing usable gives null, so the caller can try the next client`() {
        assertNull(ProgressiveAudioFallback.pickIndex(emptyList(), preferSmallest = false))
        assertNull(
            "video-only progressive entries carry no sound",
            ProgressiveAudioFallback.pickIndex(
                listOf(stream(137, 2_000_000, hasAudioTrack = false)),
                preferSmallest = false,
            ),
        )
        assertNull(
            ProgressiveAudioFallback.pickIndex(
                listOf(stream(18, 96_000, hasUrl = false)),
                preferSmallest = true,
            ),
        )
    }

    @Test
    fun `a single usable stream is chosen whichever way the preference points`() {
        val only = listOf(stream(18, 96_000))

        assertEquals(0, ProgressiveAudioFallback.pickIndex(only, preferSmallest = false))
        assertEquals(0, ProgressiveAudioFallback.pickIndex(only, preferSmallest = true))
    }
}
