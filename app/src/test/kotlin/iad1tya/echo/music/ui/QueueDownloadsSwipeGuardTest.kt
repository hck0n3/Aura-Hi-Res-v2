package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 503-510 (owner 2026-10-29): what the smart queue, the download button, the add-to-list picker, the swipe
 * on the pinned lists, the player's search, the animated covers and «Pedir música» must keep doing.
 */
class QueueDownloadsSwipeGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun module(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    // ── 504 · the picker says «ya tiene esta canción» in words ────────────────────────────────────────────────────────
    @Test
    fun `the add to list picker says in words which lists already have the song`() {
        val dialog = main("ui/menu/AddToPlaylistDialog.kt")
        assertTrue(dialog.contains("note = if (alreadyHasSong) stringResource(R.string.playlist_already_has_song) else null"))
        assertTrue("el tick propio, distinto al de «descargada»", dialog.contains("R.drawable.library_add_check"))
        assertTrue(main("ui/component/Items.kt").contains("note: String? = null"))
        assertTrue(main("ui/component/ListItemSizing.kt").contains("note = note"))
    }

    // ── 505 · downloads ───────────────────────────────────────────────────────────────────────────────────────────────
    @Test
    fun `the player's download button opens the sheet instead of removing the download, and the sheet knows the real state`() {
        val player = main("ui/newui/AuraPlayer.kt")
        val onClick = player.substringAfter("isExporting -> Unit\n").substringBefore("size = quickAccessGlyph")
        assertTrue(onClick.contains("downloadBusy || downloadDone || downloadHeld || isExported ->"))
        assertTrue(onClick.contains("showDownloadOrExportDialog = true"))
        assertFalse("ya no borra en silencio", onClick.contains("removeSongDownloads(context, songId, isVideo)"))
        assertTrue(player.contains("offlineState = download?.state"))
        assertFalse(player.contains("offlineState = Download.STATE_STOPPED,\n            onDismiss = { showDownloadOrExportDialog = false }"))
    }

    @Test
    fun `the download sheet tells what already exists and where the song is, and reuses the listen cache`() {
        val sheet = main("ui/newui/AuraDownloadSheet.kt")
        assertTrue(sheet.contains("AlreadyDownloadedNotice("))
        assertTrue(sheet.contains("database.playlistNamesContaining("))
        assertTrue(sheet.contains("downloadUtil.reuseListenCopyThen(item.id)"))
        assertTrue(main("db/DatabaseDao.kt").contains("fun playlistNamesContaining(songId: String): Flow<List<String>>"))
        val util = main("playback/DownloadUtil.kt")
        assertTrue(util.contains("fun reuseListenCopyThen(songId: String, then: () -> Unit)"))
        val adoption = main("playback/ListenCacheAdoption.kt")
        // The guards that keep it from mixing containers (registry #28 / #40 / #57).
        assertTrue(adoption.contains("downloadCache.getCachedSpans(songId).isNotEmpty()"))
        assertTrue(adoption.contains("OfflineIntegrity.isFullyOnDisk(downloadCache, songId)"))
        assertTrue(adoption.contains("rollback(downloadCache, songId)"))
        for (res in listOf("values", "values-es", "values-es-rUS")) {
            val xml = File(root, "app/src/main/res/$res/echo_strings.xml").readText()
            for (name in listOf("download_sheet_already_title", "download_sheet_already_modes", "download_sheet_in_lists")) {
                assertTrue("$res/$name", xml.contains("name=\"$name\""))
            }
        }
    }

    // ── 506 · like / add-to-list swipe on the lists the Biblioteca pins ──────────────────────────────────────────────
    @Test
    fun `a YouTube list and the library lists have the like and add to list swipe`() {
        listOf("ui/newui/AuraOnlinePlaylistScreen.kt", "ui/newui/AuraCollectionScreens.kt").forEach {
            val s = main(it)
            assertTrue(it, s.contains("AuraLibrarySwipeActionsBox("))
            assertTrue(it, s.contains("onAddToPlaylist = { showSwipeAddToPlaylist = true }"))
        }
    }

    // ── 507 · the player's own search asks before it plays a track ───────────────────────────────────────────────────
    @Test
    fun `the player search asks what to do with a tapped track in the wide window, the general search does not`() {
        val search = main("ui/newui/AuraPlayerQuickSearch.kt")
        assertTrue(search.contains("askBeforePlaying = true"))
        val pane = main("ui/newui/AuraSearchDetailPane.kt")
        assertTrue(pane.contains("askBeforePlaying: Boolean = false"))
        assertTrue(pane.contains("ask != null -> ask(track) { onClick(index, track) }"))
        // The general search (Buscar) never passes it.
        listOf("ui/newui/AuraSearchResultScreen.kt", "ui/newui/AuraSearchSuggestions.kt").forEach {
            assertFalse(it, main(it).contains("askBeforePlaying"))
        }
    }

    // ── 508 · animated covers ─────────────────────────────────────────────────────────────────────────────────────────
    @Test
    fun `the Apple token is kept until its own expiry, dropped when Apple rejects it, and every outcome is logged`() {
        for (path in listOf(
            "canvas/src/main/kotlin/com/music/echo/canvas/AppleMusicTokenProvider.kt",
            "applecanvas/src/main/kotlin/com/music/echo/applecanvas/AppleMusicTokenProvider.kt",
        )) {
            val s = module(path)
            assertTrue(path, s.contains("cachedExpiryMs"))
            assertTrue(path, s.contains("suspend fun invalidate()"))
            assertTrue(path, s.contains("MAX_SCRIPTS"))
            assertTrue(path, s.contains("AppleCanvasDiagnostics.log("))
            // Row 515 (SimpMusic 2.4.0, AMTokenManager): any 3-segment JWT is a candidate and the web player's own
            // (issuer AMPWebPlay) wins — Apple rotates the header's field order, so an «eyJhbGciOiJFUzI1NiI» prefix missed it —
            // and the regular site is read before the beta one, without stopping at the first page that lists scripts.
            assertTrue(path, s.contains("TOKEN_REGEX = Regex(\"\"\"eyJ[A-Za-z0-9\\-_=]{10,}"))
            assertTrue(path, s.contains("AMPWebPlay"))
            assertTrue(path, s.contains("listOf(\"https://music.apple.com\", \"https://beta.music.apple.com\")"))
            assertTrue(path, !s.contains("if (scripts.isNotEmpty()) break"))
            // The battery guard of row 491 stays: the back-off check is before the first download.
            assertTrue(path, s.indexOf("< RETRY_AFTER_MS") in 1 until s.indexOf("httpClient.get("))
        }
        assertTrue(module("canvas/src/main/kotlin/com/music/echo/canvas/AppleMusicArtistBackgroundProvider.kt").contains("AppleMusicTokenProvider.invalidate()"))
        assertTrue(module("applecanvas/src/main/kotlin/com/music/echo/applecanvas/AppleMusicCanvasProvider.kt").contains("AppleMusicTokenProvider.invalidate()"))
        assertTrue(main("App.kt").contains("AppleCanvasDiagnostics.sink"))
    }

    // ── 509 · Pedir música: the named artist is locked, «parecido a» reads YouTube's similar artists ─────────────────
    @Test
    fun `an artist the phrase search could not confirm is probed alone before the request drifts to other artists`() {
        val gen = main("playlistimport/AiPlaylistGenerator.kt")
        assertTrue(gen.contains("val probedArtist ="))
        assertTrue(gen.contains("val lockedArtist = confirmedArtist ?: probedArtist"))
        assertTrue(gen.contains("lockedArtist != null ->"))
        assertTrue("«parecido a X» lee los artistas parecidos de YouTube", gen.contains("SIMILAR_ARTISTS"))
    }

    // ── 510 · nothing here subscribes to an artist ────────────────────────────────────────────────────────────────────
    @Test
    fun `Pedir musica and the Spotify playlist import never follow an artist`() {
        val forbidden = listOf("subscribeChannel", "followedByUserAt", "localToggleLike", "markFollowedByUser", "followArtistsWithContent")
        listOf(
            "playlistimport/AiPlaylistGenerator.kt", "viewmodels/MusicRequestViewModel.kt",
            "playlistimport/MusicRequestStyleGate.kt", "playlistimport/JrPlaylistImporter.kt",
        ).forEach { path ->
            val code = main(path).lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }.joinToString("\n")
            forbidden.forEach { assertFalse("$path usa $it", code.contains(it)) }
        }
        // The Spotify import follows only through its explicit «artistas que sigues» source.
        val spotify = main("spotifyimport/SpotifyImportRepository.kt")
        assertFalse(spotify.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n").contains("followArtistsWithContent("))
    }

    // ── 511 · the YouTube list sync keeps what the user added ───────────────────────────────────────────────────────
    @Test
    fun `the list sync keeps the songs the user added that YouTube does not have yet`() {
        val sync = main("utils/SyncUtils.kt")
        assertTrue(sync.contains("val pendingAdds = localRows.filter { it.map.setVideoId == null && it.song.id !in remoteIdSet }"))
        assertTrue(sync.contains("YouTube.addToPlaylist(browseId, row.song.id)"))
        // The Spotify mirror is still never rebuilt from YouTube, and the empty / truncated-remote guards are still there.
        assertTrue(sync.contains("if (isSpotifyMirror(playlistId))"))
        assertTrue(sync.contains("remoteIds.size < localIds.size / 2"))
    }
}
