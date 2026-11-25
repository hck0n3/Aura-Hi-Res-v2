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
    fun `the library and queue menus offer it only for a YouTube catalogue song`() {
        // Row 861: a YouTube video id, not a device file.
        assertTrue(SoftDislikeUndoRule.isYouTubeCatalogueSong("dQw4w9WgXcQ", isLocal = false))
        assertTrue(SoftDislikeUndoRule.isYouTubeCatalogueSong("a-b_C1d2E3f", isLocal = false))
        assertFalse(SoftDislikeUndoRule.isYouTubeCatalogueSong("dQw4w9WgXcQ", isLocal = true))
        assertFalse(SoftDislikeUndoRule.isYouTubeCatalogueSong("content://media/external/audio/media/42", isLocal = false))
        assertFalse(SoftDislikeUndoRule.isYouTubeCatalogueSong("file:///sdcard/a.mp3", isLocal = false))
        assertFalse(SoftDislikeUndoRule.isYouTubeCatalogueSong("https://x.y/a", isLocal = false))
        assertFalse(SoftDislikeUndoRule.isYouTubeCatalogueSong("", isLocal = false))
        assertFalse(SoftDislikeUndoRule.isYouTubeCatalogueSong("dQw4w9WgXc", isLocal = false))
        assertFalse(SoftDislikeUndoRule.isYouTubeCatalogueSong("dQw4w9WgX/Q", isLocal = false))
    }

    @Test
    fun `a library-made artist id falls back to the name like the online menu`() {
        assertEquals("UCabc", SoftDislikeUndoRule.youTubeArtistId("UCabc"))
        assertEquals("FEmusic_library_privately_owned_artist_x", SoftDislikeUndoRule.youTubeArtistId("FEmusic_library_privately_owned_artist_x"))
        assertEquals(null, SoftDislikeUndoRule.youTubeArtistId("LAabcdefgh"))
        assertEquals(null, SoftDislikeUndoRule.youTubeArtistId("LOCAL_ARTIST_0123"))
        assertEquals(null, SoftDislikeUndoRule.youTubeArtistId(null))
        assertEquals(
            "artist name",
            iad1tya.echo.music.reco.SoftDislikeRank.artistKey(SoftDislikeUndoRule.youTubeArtistId("LAabcdefgh"), " Artist Name "),
        )
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
        val effect = shell.substringAfter("iad1tya.echo.music.dislike.SoftDislikeUndo.offer.collectLatest { offer ->")
            .substringBefore("var showSettingDialoge")
        // Only a plain notice is cleared; an actionable one (previous-queue offer, «Cola cerrada») is never dismissed.
        assertTrue(effect.contains("if (data != null && data.visuals.message != message && data.visuals.actionLabel == null) data.dismiss()"))
        assertFalse(effect.contains("if (data != null && data.visuals.message != message) data.dismiss()"))
        assertTrue(effect.contains("data != null && (data.visuals.message == message || data.visuals.actionLabel != null)"))
        // Row 852: with the player sheet open, or a notice with an action in front, it keeps «Deshacer» in the overlay
        // host (drawn after BottomSheetMenu, above the player) — never a Toast, which has no button.
        val softEffect = effect.substringBefore("// Row 805 (owner decision H8)")
        assertTrue(softEffect.contains("var inOverlay = !playerBottomSheetState.isCollapsed && !playerBottomSheetState.isDismissed"))
        assertTrue(softEffect.contains("softDislikeOverlayHostState.showSnackbar("))
        assertEquals(2, Regex(Regex.escape("actionLabel = if (offer.canUndo) queueClosedUndoLabel else null,")).findAll(softEffect).count())
        assertFalse(softEffect.contains("Toast"))
        val menuHost = shell.indexOf("LocalMenuState.current,")
        val overlayHost = shell.indexOf("hostState = softDislikeOverlayHostState,")
        assertTrue(menuHost >= 0)
        assertTrue("the overlay host must compose after the menu and the player sheet", overlayHost > menuHost)
        assertTrue(shell.contains("this@MainActivity.applicationScope.launch {"))
        assertTrue(shell.contains("iad1tya.echo.music.dislike.SoftDislikeUndo.drop(offer.token)"))
        val es = File(root, "app/src/main/res/values-es/strings.xml").readText()
        assertTrue(es.contains("<string name=\"soft_dislike_undo_artist\">Se recomendará menos a %1\$s</string>"))
    }

    @Test
    fun `every Menos de esto entry point hands its tap to the shared undo offer`() {
        // Row 852 (owner test #14): a «Menos de esto» that marks without SoftDislikeUndo.offer has no «Deshacer».
        // Only the store itself (dislike/) and the device sync, which applies another device's state, may write the soft sets.
        val src = File(root, "app/src/main/kotlin/com/music/echo")
        val writers = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.parentFile.name == "dislike" || it.name == "LibrarySyncAdapter.kt" }
            .filter { f ->
                val t = f.readText()
                t.contains("softDislikeSongAndArtist(") || t.contains("softDislikeSong(") || t.contains("softDislikeArtist(") ||
                    t.contains("\"Menos de esto\")")
            }
            .toList()
        assertTrue("no «Menos de esto» entry point found: update this test", writers.isNotEmpty())
        for (f in writers) {
            val t = f.readText()
            assertFalse("${f.name} marks one half only, without the shared undo", t.contains("softDislikeSong(") || t.contains("softDislikeArtist("))
            var from = 0
            var marks = 0
            while (true) {
                val mark = t.indexOf(".softDislikeSongAndArtist(", from)
                if (mark < 0) break
                marks++
                val next = t.indexOf(".softDislikeSongAndArtist(", mark + 1).let { if (it < 0) t.length else it }
                val offer = t.indexOf("iad1tya.echo.music.dislike.SoftDislikeUndo.offer(before, ", mark)
                assertTrue("${f.name}: a «Menos de esto» without SoftDislikeUndo.offer right after it", offer in (mark + 1) until next)
                from = mark + 1
            }
            assertTrue("${f.name} shows «Menos de esto» but never marks through the shared path", marks > 0)
        }
    }

    @Test
    fun `Menos de esto in the online, library and queue menus is the same action with the same undo`() {
        // Row 861: Biblioteca, «Me gusta», local lists and history (SongMenu) and the queue (QueueMenu) got the online
        // menu's action. Every entry point, old and new, must mark song + artist in one edit and hand the shell the offer.
        val menus = listOf("ui/menu/YouTubeSongMenu.kt", "ui/menu/SongMenu.kt", "ui/menu/QueueMenu.kt")
        for (path in menus) {
            val src = main(path)
            val title = "title = { Text(text = \"Menos de esto\") }"
            assertEquals("$path: one «Menos de esto»", 1, Regex(Regex.escape(title)).findAll(src).count())
            val item = src.substringAfter(title).substringBefore("onDismiss()")
            assertTrue(path, item.contains("description = { Text(text = \"Se recomendará menos parecido a esto\") }"))
            assertTrue(path, item.contains("painterResource(R.drawable.thumb_down)"))
            assertTrue(path, item.contains("iad1tya.echo.music.reco.SoftDislikeRank.artistKey("))
            assertTrue(path, item.contains("context.applicationScope.launch {"))
            assertTrue(path, item.contains("val store = iad1tya.echo.music.dislike.DislikeStoreEntryPoint.get(context)"))
            val before = item.indexOf("val before = store.snapshot()")
            val mark = item.indexOf(".softDislikeSongAndArtist(")
            val offer = item.indexOf("iad1tya.echo.music.dislike.SoftDislikeUndo.offer(before, ")
            assertTrue(path, before >= 0 && mark > before && offer > mark)
            // The song id marked is the song id offered for «Deshacer», with the same artist key and name.
            val markedId = item.substring(mark).substringAfter("(").substringBefore(",")
            val offeredId = item.substring(offer).substringAfter("offer(before, ").substringBefore(",")
            assertEquals(path, markedId, offeredId)
            assertTrue(path, item.contains(".softDislikeSongAndArtist($markedId, artistKey)"))
            assertTrue(path, item.contains("SoftDislikeUndo.offer(before, $offeredId, artistKey, artistName)"))
            // Never a Toast, never an unlike, never a removal from a list or the queue.
            for (forbidden in listOf("Toast", "toggleLike", "likeSong(", "removeMediaItem", "delete(", "remove(")) {
                assertFalse("$path: «Menos de esto» must not $forbidden", item.contains(forbidden))
            }
        }
        // The two new ones: only for YouTube catalogue songs, and a library-made artist id falls back to the name.
        val song = main("ui/menu/SongMenu.kt")
        assertTrue(
            song.substringBefore("title = { Text(text = \"Menos de esto\") }").takeLast(300)
                .contains("if (iad1tya.echo.music.dislike.SoftDislikeUndoRule.isYouTubeCatalogueSong(song.id, song.song.isLocal)) {"),
        )
        assertTrue(song.contains("ActionLog.tap(ActionId.SONGMENU_LESS_LIKE_THIS)"))
        val queue = main("ui/menu/QueueMenu.kt")
        assertTrue(
            queue.substringBefore("title = { Text(text = \"Menos de esto\") }").takeLast(300)
                .contains("if (iad1tya.echo.music.dislike.SoftDislikeUndoRule.isYouTubeCatalogueSong(mediaMetadata.id, librarySong?.song?.isLocal == true)) {"),
        )
        assertTrue(queue.contains("ActionLog.tap(ActionId.QMENU_LESS_LIKE_THIS)"))
        for (src in listOf(song, queue)) {
            val item = src.substringAfter("title = { Text(text = \"Menos de esto\") }").substringBefore("onDismiss()")
            assertTrue(item.contains("iad1tya.echo.music.dislike.SoftDislikeUndoRule.youTubeArtistId(primary?.id),"))
        }
    }
}
