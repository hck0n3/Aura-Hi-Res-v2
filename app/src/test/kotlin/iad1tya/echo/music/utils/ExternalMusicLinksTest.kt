package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
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
}
