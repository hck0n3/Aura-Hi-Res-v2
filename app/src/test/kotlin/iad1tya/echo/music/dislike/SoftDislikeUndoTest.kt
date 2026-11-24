package iad1tya.echo.music.dislike

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 803 (owner 2026-11-24): «Menos de esto» answers with «Se recomendará menos a X · Deshacer». */
class SoftDislikeUndoTest {

    @Test
    fun `undo takes back only what this tap added`() {
        val nothingBefore = DislikeStore.Disliked()
        assertEquals(Pair<String?, String?>("s1", "a1"), SoftDislikeUndoRule.addedByTap(nothingBefore, "s1", "a1"))
        val artistAlreadyMarked = DislikeStore.Disliked(softArtists = setOf("a1"))
        assertEquals(Pair<String?, String?>("s1", null), SoftDislikeUndoRule.addedByTap(artistAlreadyMarked, "s1", "a1"))
        val songAlreadyMarked = DislikeStore.Disliked(softSongs = setOf("s1"))
        assertEquals(Pair<String?, String?>(null, "a1"), SoftDislikeUndoRule.addedByTap(songAlreadyMarked, "s1", "a1"))
        assertEquals(Pair<String?, String?>("s1", null), SoftDislikeUndoRule.addedByTap(nothingBefore, "s1", null))
    }

    @Test
    fun `nothing new means no Deshacer`() {
        assertFalse(SoftDislikeOffer(1L, 0L, "X", songId = null, artistKey = null).canUndo)
        assertTrue(SoftDislikeOffer(1L, 0L, "X", songId = "s1", artistKey = null).canUndo)
        assertTrue(SoftDislikeOffer(1L, 0L, null, songId = null, artistKey = "a1").canUndo)
    }

    @Test
    fun `same window as Cola cerrada`() {
        assertEquals(iad1tya.echo.music.playback.DismissedQueueUndoRule.UNDO_WINDOW_MS, SoftDislikeUndoRule.UNDO_WINDOW_MS)
    }

    // ---- wiring -----------------------------------------------------------------------------------

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    @Test
    fun `the menu reads the soft sets before marking and hands the shell the offer instead of a Toast`() {
        val menu = main("ui/menu/YouTubeSongMenu.kt")
        val item = menu.substringAfter("title = { Text(text = \"Menos de esto\") }").substringBefore("onDismiss()")
        val before = item.indexOf("val before = store.snapshot()")
        val mark = item.indexOf(".softDislikeSongAndArtist(song.id, artistKey)")
        val offer = item.indexOf("iad1tya.echo.music.dislike.SoftDislikeUndo.offer(before, song.id, artistKey, artistName)")
        assertTrue(before >= 0)
        assertTrue(mark > before)
        assertTrue(offer > mark)
        assertFalse(item.contains("Toast"))
    }

    @Test
    fun `undo calls the store's two undo methods and answers one offer once`() {
        val undo = main("dislike/SoftDislikeUndo.kt").substringAfter("suspend fun undo(").substringBefore("\n    }")
        assertTrue(undo.contains("if (!_offer.compareAndSet(current, null)) return"))
        assertTrue(undo.contains("current.songId?.let { store.undoSoftDislikeSong(it) }"))
        assertTrue(undo.contains("current.artistKey?.let { store.undoSoftDislikeArtist(it) }"))
    }

    @Test
    fun `the shell shows it with Deshacer and runs the undo on the process scope`() {
        val shell = main("MainActivity.kt")
        assertTrue(shell.contains("iad1tya.echo.music.dislike.SoftDislikeUndo.offer.collectLatest { offer ->"))
        assertTrue(shell.contains("?.let { this@MainActivity.getString(R.string.soft_dislike_undo_artist, it) }"))
        assertTrue(shell.contains("actionLabel = if (offer.canUndo) queueClosedUndoLabel else null,"))
        assertTrue(shell.contains("if (data != null && data.visuals.message != message) data.dismiss()"))
        assertTrue(shell.contains("this@MainActivity.applicationScope.launch {"))
        assertTrue(shell.contains("iad1tya.echo.music.dislike.SoftDislikeUndo.drop(offer.token)"))
        val es = File(root, "app/src/main/res/values-es/strings.xml").readText()
        assertTrue(es.contains("<string name=\"soft_dislike_undo_artist\">Se recomendará menos a %1\$s</string>"))
    }
}
