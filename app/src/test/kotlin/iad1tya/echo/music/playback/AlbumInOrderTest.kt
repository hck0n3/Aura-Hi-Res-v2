package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 903 — «no fade inside an album played in order». The rule may only REMOVE a fade where the album order
 * is known for certain; every doubt keeps today's crossfade.
 */
class AlbumInOrderTest {

    private fun pair(
        curAlbum: String? = "AL1",
        curIdx: Int? = 3,
        nextAlbum: String? = "AL1",
        nextIdx: Int? = 4,
        shuffle: Boolean = false,
        repeatOne: Boolean = false,
    ) = AlbumInOrder.isInOrderPair(curAlbum, curIdx, nextAlbum, nextIdx, shuffle, repeatOne)

    @Test
    fun `track n then n+1 of the same album, in order, is gapless`() {
        assertTrue(pair())
        assertTrue(pair(curIdx = 0, nextIdx = 1))
    }

    @Test
    fun `anything else keeps the crossfade`() {
        assertFalse("other album", pair(nextAlbum = "AL2"))
        assertFalse("skipped a track", pair(nextIdx = 5))
        assertFalse("backwards", pair(curIdx = 4, nextIdx = 3))
        assertFalse("same track twice", pair(nextIdx = 3))
        assertFalse("shuffle", pair(shuffle = true))
        assertFalse("repeat one", pair(repeatOne = true))
        assertFalse("unknown position", pair(curIdx = null))
        assertFalse("unknown position", pair(nextIdx = null))
        assertFalse("no album", pair(curAlbum = null, nextAlbum = null))
        assertFalse("empty album id", pair(curAlbum = "", nextAlbum = ""))
    }

    @Test
    fun `pair key needs both ids`() {
        assertEquals("a>b", AlbumInOrder.pairKey("a", "b"))
        assertNull(AlbumInOrder.pairKey(null, "b"))
        assertNull(AlbumInOrder.pairKey("a", null))
    }

    @Test
    fun `the crossfade skips the fade for an album pair and stays event-driven`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val engine = File(root, "app/src/main/kotlin/com/music/echo/playback/CrossfadeEngine.kt").readText()
        // Both fade entry points and the preload keep-or-drop decision.
        assertTrue(engine.contains("if (isAlbumInOrderPair()) {\n        traceCrossfade(\"album-in-order\""))
        assertTrue(engine.contains("if (isAlbumInOrderPair()) return // row 903"))
        assertTrue(engine.contains("gaplessBypass = (crossfadeGapless && isNextItemGapless()) || isAlbumInOrderPair()"))
        // One lookup per pair, off the main thread; a true verdict re-arms the schedule.
        val fn = engine.substringAfter("internal fun MusicService.isAlbumInOrderPair(): Boolean {")
            .substringBefore("internal fun MusicService.isNextItemGapless()")
        assertTrue(fn.contains("if (key == albumInOrderKey) return albumInOrderVerdict"))
        assertTrue(fn.contains("withContext(Dispatchers.IO)"))
        assertTrue(fn.contains("database.songAlbumMapsIn(albumId, listOf(currentId, nextId))"))
        assertTrue(fn.contains("if (verdict && !isCrossfading) scheduleCrossfade()"))
        assertFalse("no polling", fn.contains("delay(") || fn.contains("while ("))
        val dao = File(root, "app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt").readText()
        assertTrue(dao.contains("SELECT * FROM song_album_map WHERE albumId = :albumId AND songId IN (:songIds)"))
    }
}
