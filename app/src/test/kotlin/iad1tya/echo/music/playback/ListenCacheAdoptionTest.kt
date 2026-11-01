package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Row 505 — which complete listen-cache copy a download may adopt (pure part; the copy itself is Media3 on disk). */
class ListenCacheAdoptionTest {
    private fun key(id: String, itag: String?) = StreamCacheKeys.build(id, itag)

    @Test
    fun `the itag is read from the listen key, also for a video id that contains dashes`() {
        assertEquals(251, ListenCacheAdoption.itagOf(key("dQw4w9WgXcQ", "251")))
        assertEquals(140, ListenCacheAdoption.itagOf(key("ab-cd_EF12-", "140")))
        assertNull(ListenCacheAdoption.itagOf(key("ab-cd_EF12-", null)))
        assertNull(ListenCacheAdoption.itagOf("not-a-listen-key"))
    }

    @Test
    fun `the copy that the database describes wins, and no match means no adoption`() {
        val opus = key("dQw4w9WgXcQ", "251")
        val aac = key("dQw4w9WgXcQ", "140")
        assertEquals(opus, ListenCacheAdoption.pickKey(listOf(aac, opus), dbItag = 251))
        assertEquals(aac, ListenCacheAdoption.pickKey(listOf(aac, opus), dbItag = 140))
        // The database names an itag that no complete copy has: those bytes are not the ones the app describes.
        assertNull(ListenCacheAdoption.pickKey(listOf(aac), dbItag = 251))
        // The database knows no format yet: any complete copy (the first in key order) will do.
        assertEquals(aac, ListenCacheAdoption.pickKey(listOf(opus, aac), dbItag = null))
        assertNull(ListenCacheAdoption.pickKey(emptyList(), dbItag = 251))
    }
}
