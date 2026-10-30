package iad1tya.echo.music.ui

import iad1tya.echo.music.canvas.CanvasArtwork
import iad1tya.echo.music.ui.player.CanvasAlbumMemo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 523 — owner 2026-10-29: the animated (or still) cover of an album is looked up ONCE and shared by every song of
 * it; animated covers are reused later; the album page's cover moves without pressing play.
 */
class CanvasAlbumMemoTest {
    @Test
    fun `two songs of the same album share one key, whatever the casing or the punctuation`() {
        val a = CanvasAlbumMemo.key("Marcos Witt", "Sigues Siendo Dios", "Poderoso")
        val b = CanvasAlbumMemo.key("marcos  witt!", "SIGUES siendo dios", "Dios De Pactos")
        assertNotNull(a)
        assertEquals(a, b)
        // no album: the song is the key, so a single is still looked up once
        assertEquals(CanvasAlbumMemo.key("Marcos Witt", null, "Poderoso"), CanvasAlbumMemo.key("marcos witt", "", "PODEROSO"))
        assertNull(CanvasAlbumMemo.key("", "Album", "Song"))
        assertNull(CanvasAlbumMemo.key("Artist", null, ""))
    }

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `the player asks the album memo before the providers and remembers only an answered lookup`() {
        val t = main("ui/player/Thumbnail.kt")
        val memo = t.indexOf("CanvasAlbumMemo.lookup(memoContext, it)")
        val chain = t.indexOf("var lookupAnswered = false")
        assertTrue(memo in 1 until chain)
        assertTrue("el reloj de espera no cuenta como respuesta", t.contains("memoKey != null && memo == null && lookupAnswered"))
        assertTrue(t.contains("(spotifyCanvas ?: linkedSetOf("))
    }

    @Test
    fun `the album page's cover moves without pressing play, the player's still follows the music`() {
        val album = main("ui/newui/AuraAlbumScreen.kt")
        assertEquals(2, Regex("isPlaying = true,").findAll(album.substring(album.indexOf("CanvasArtworkPlayer("))).count().coerceAtMost(2))
        val thumb = main("ui/player/Thumbnail.kt")
        assertTrue(thumb.substring(thumb.indexOf("canvasArtwork?.let { artwork ->")).contains("isPlaying = isPlaying"))
    }
}
