package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The listen-cache key grammar: yt-stream-<videoId>[-<itag>] — where videoId chars may include
 * dashes, and the itag is only stripped when the LAST dash segment is numeric. MusicService's
 * factory (the builder) and the cache-list/purge consumers (the parsers) must agree byte-for-byte
 * or the listen-cache becomes invisible again (2026-08-29 audit).
 */
class StreamCacheKeysTest {

    @Test
    fun `build matches the factory format`() {
        assertEquals("yt-stream-dQw4w9WgXcQ-140", StreamCacheKeys.build("dQw4w9WgXcQ", "140"))
        assertEquals("yt-stream-dQw4w9WgXcQ", StreamCacheKeys.build("dQw4w9WgXcQ", null))
        assertEquals("yt-stream-dQw4w9WgXcQ", StreamCacheKeys.build("dQw4w9WgXcQ", ""))
    }

    @Test
    fun `songIdOf parses videoId with numeric itag`() {
        assertEquals("dQw4w9WgXcQ", StreamCacheKeys.songIdOf("yt-stream-dQw4w9WgXcQ-140"))
        assertEquals("dQw4w9WgXcQ", StreamCacheKeys.songIdOf("yt-stream-dQw4w9WgXcQ-774"))
    }

    @Test
    fun `songIdOf keeps videoIds that contain dashes`() {
        // Video ids come from a 64-symbol alphabet that includes '-'. A trailing NON-numeric
        // segment is part of the id, not an itag.
        assertEquals("ab-cd-ef", StreamCacheKeys.songIdOf("yt-stream-ab-cd-ef"))
        // A trailing numeric segment after a dashed id is the itag: everything before it is the id.
        assertEquals("ab-cd", StreamCacheKeys.songIdOf("yt-stream-ab-cd-140"))
    }

    @Test
    fun `songIdOf passes non-stream keys through as null`() {
        assertNull(StreamCacheKeys.songIdOf("dQw4w9WgXcQ")) // plain mediaId key (Qobuz/podcast)
        assertNull(StreamCacheKeys.songIdOf("yt-stream-")) // malformed: empty remainder
        assertNull(StreamCacheKeys.songIdOf("https://qobuz.example/file.flac"))
    }

    @Test
    fun `belongsTo matches any quality of the song`() {
        assertTrue(StreamCacheKeys.belongsTo("yt-stream-dQw4w9WgXcQ-140", "dQw4w9WgXcQ"))
        assertTrue(StreamCacheKeys.belongsTo("yt-stream-dQw4w9WgXcQ-774", "dQw4w9WgXcQ"))
        assertFalse(StreamCacheKeys.belongsTo("yt-stream-otherVideo-140", "dQw4w9WgXcQ"))
        assertFalse(StreamCacheKeys.belongsTo("dQw4w9WgXcQ", "dQw4w9WgXcQ")) // not a stream key
    }
}
