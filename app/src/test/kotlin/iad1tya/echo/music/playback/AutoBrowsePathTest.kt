package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/** Registry row 883: an Android Auto browse id keeps a phone song's `content://` id whole. */
class AutoBrowsePathTest {
    private val local = "content://media/external/audio/media/12"

    @Test
    fun `ids without a local song split exactly as before`() {
        for (id in listOf("root", "song/abc123", "album/MPREb_x/abc123", "playlist/LP_LIKED/__shuffle__", "search/bad bunny/abc123")) {
            assertEquals(id, id.split("/"), AutoBrowsePath.segments(id))
        }
    }

    @Test
    fun `a local song id is the last segment, whole`() {
        assertEquals(listOf("song", local), AutoBrowsePath.segments("song/$local"))
        assertEquals(listOf("album", "LOCAL_ALBUM_7", local), AutoBrowsePath.segments("album/LOCAL_ALBUM_7/$local"))
        assertEquals(listOf("playlist", "LP_LIKED", local), AutoBrowsePath.segments("playlist/LP_LIKED/$local"))
        assertEquals(listOf("search", "rosalia", local), AutoBrowsePath.segments("search/rosalia/$local"))
        assertEquals(listOf("artist", "LOCAL_ARTIST_ab", "file:///sdcard/Music/a.flac"), AutoBrowsePath.segments("artist/LOCAL_ARTIST_ab/file:///sdcard/Music/a.flac"))
    }

    @Test
    fun `the search query is recovered the way onSetMediaItems recovers it`() {
        val mediaId = "search/rosalia/$local"
        val songId = AutoBrowsePath.segments(mediaId).last()
        assertEquals(local, songId)
        assertEquals("rosalia", mediaId.removePrefix("search/").removeSuffix("/$songId"))
    }

    @Test
    fun `a bare local id stays one segment and a scheme inside a word does not cut`() {
        assertEquals(listOf(local), AutoBrowsePath.segments(local))
        assertEquals("search/xcontent://y/abc".split("/"), AutoBrowsePath.segments("search/xcontent://y/abc"))
    }
}
