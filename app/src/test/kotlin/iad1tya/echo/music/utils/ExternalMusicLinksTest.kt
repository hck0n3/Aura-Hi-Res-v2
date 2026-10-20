package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ronda 9 (dueño): un link de Amazon Music de UNA canción ("/albums/{albumAsin}?trackAsin={id}")
 * se leía como una petición del ÁLBUM completo — la ruta sola no distingue los dos casos, y
 * `trackAsin` (el parámetro propio de Amazon que sí lo dice sin ambigüedad) nunca se miraba.
 */
class ExternalMusicLinksTest {

    @Test
    fun `a trackAsin always means a single track, regardless of the path`() {
        assertEquals(
            ExternalMusicLinks.Kind.TRACK,
            ExternalMusicLinks.kindFromPathAndQuery("/albums/B0CJRR3SJC", "B0CJRQZQYX"),
        )
        assertEquals(
            ExternalMusicLinks.Kind.TRACK,
            ExternalMusicLinks.kindFromPathAndQuery("/playlist/somePlaylist", "B0CJRQZQYX"),
        )
    }

    @Test
    fun `without a trackAsin the path still decides`() {
        assertEquals(ExternalMusicLinks.Kind.ALBUM, ExternalMusicLinks.kindFromPathAndQuery("/albums/B0CJRR3SJC", null))
        assertEquals(ExternalMusicLinks.Kind.PLAYLIST, ExternalMusicLinks.kindFromPathAndQuery("/playlist/xyz", null))
        assertEquals(ExternalMusicLinks.Kind.TRACK, ExternalMusicLinks.kindFromPathAndQuery("/tracks/B0CJRQZQYX", null))
    }

    @Test
    fun `a blank trackAsin is treated as absent`() {
        assertEquals(ExternalMusicLinks.Kind.ALBUM, ExternalMusicLinks.kindFromPathAndQuery("/albums/B0CJRR3SJC", ""))
    }

    @Test
    fun `a song_link wrapper of a YouTube Music URL unwraps to that URL`() {
        assertEquals(
            "https://music.youtube.com/playlist?list=PLabc",
            ExternalMusicLinks.songLinkWrappedYouTubeUrl("https://song.link/https://music.youtube.com/playlist?list=PLabc"),
        )
        assertEquals(
            "https://music.youtube.com/channel/UCxyz",
            ExternalMusicLinks.songLinkWrappedYouTubeUrl("https://album.link/https://music.youtube.com/channel/UCxyz"),
        )
    }

    @Test
    fun `a re-shared percent-encoded wrapper unwraps too`() {
        assertEquals(
            "https://music.youtube.com/playlist?list=PLabc",
            ExternalMusicLinks.songLinkWrappedYouTubeUrl("https://song.link/https%3A%2F%2Fmusic.youtube.com%2Fplaylist%3Flist%3DPLabc"),
        )
    }

    @Test
    fun `other song_link links are left to their existing routes`() {
        assertNull(ExternalMusicLinks.songLinkWrappedYouTubeUrl("https://song.link/y/abc123"))
        assertNull(ExternalMusicLinks.songLinkWrappedYouTubeUrl("https://song.link/s/0ab12"))
        assertNull(ExternalMusicLinks.songLinkWrappedYouTubeUrl("https://song.link/https://open.spotify.com/track/1"))
        assertNull(ExternalMusicLinks.songLinkWrappedYouTubeUrl("https://music.youtube.com/watch?v=abc"))
    }
}
