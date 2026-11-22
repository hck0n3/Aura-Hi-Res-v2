package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 636-644 — the hot-zone audit of 2026-11-22 (docs/audit/AUDIT_TRACKER.md). Each test pins one fix so a
 * later edit cannot quietly undo it. Source-text checks on purpose: these paths need a device (deep links, the player
 * sheet, a crossfade, an old SQLite) and none of them runs in a JVM test.
 */
class HotZoneAuditGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    /** F-005: a decoded /browse or /channel id with '/', '?' or '#' crashed NavController from an external link. */
    @Test
    fun `browse and channel deep links validate the id before navigating`() {
        val src = main("MainActivity.kt")
        assertTrue(src.contains("\"browse\" -> uri.lastPathSegment?.takeIf(::isRouteSafeId)"))
        assertTrue(src.contains("\"channel\" -> uri.lastPathSegment?.takeIf(::isRouteSafeId)"))
    }

    /** F-006 (row 345 c): at the end of an album/playlist the collection engine continues, not YouTube's raw automix. */
    @Test
    fun `the Aura player defers raw automix to the collection continuation`() {
        val src = main("ui/newui/AuraPlayer.kt")
        assertTrue(src.contains("!canSkipNext && automix.isNotEmpty() && !playerConnection.service.automixDefersToContinuation()"))
    }

    /** F-009: the listen cache is unlimited by default; more than 999 ids in one IN (...) throws on SQLite < 3.32. */
    @Test
    fun `the cached songs lookup is chunked under the SQLite variable cap`() {
        val src = main("viewmodels/CachePlaylistViewModel.kt")
        assertTrue(src.contains("pureCacheIds.toList().chunked(900).flatMap { database.getSongsByIds(it) }"))
        assertFalse(src.contains("database.getSongsByIds(pureCacheIds.toList())"))
    }

    /** F-010: refreshing an album deleted the ArtistEntity rows themselves (CASCADE: every song link + the follow). */
    @Test
    fun `refreshing an album never deletes its artists rows`() {
        val src = main("db/DatabaseDao.kt")
        assertFalse(src.contains("artists?.forEach(::delete)"))
    }

    /** F-003 (AGENTS rule 4): a failed audio fetch must not put the response body into app.log. */
    @Test
    fun `failed audio fetch log carries no response body`() {
        val src = main("playback/MusicService.kt")
        assertFalse(src.contains("body=\$peek"))
        assertFalse(src.contains("response.peekBody("))
    }

    /** F-007: the crossfade swaps the player instance; the device-sync listener must follow it, not stay on the old one. */
    @Test
    fun `device sync moves its play listener to the current player instance`() {
        val src = main("devicesync/DeviceSyncEngine.kt")
        assertTrue(src.contains("listenedPlayer?.removeListener(playWhenReadyListener)"))
        assertTrue(src.contains("p.addListener(playWhenReadyListener)"))
        assertFalse(src.contains("playerListenerAttached"))
    }

    /** F-011 (row 214): opening a library tab follows the user's sync cadence, never a full pull per visit. */
    @Test
    fun `library tabs go through the sync cadence`() {
        val src = main("ui/newui/AuraLibraryTabs.kt")
        assertFalse(src.contains("LaunchedEffect(Unit) {\n        if (ytmSync) withContext(Dispatchers.IO) { viewModel.sync() }"))
        assertFalse(src.contains("SongFilter.LIKED -> viewModel.syncLikedSongs()"))
        assertTrue(src.contains("if (ytmSync) syncUtils.tryAutoSync()"))
    }

    /** D-002: the classic player is gone; the inline lyrics it hosted live on in their own file. */
    @Test
    fun `classic player deleted and inline lyrics kept`() {
        assertFalse(File(root, "app/src/main/kotlin/com/music/echo/ui/player/Player.kt").exists())
        assertTrue(main("ui/player/InlineLyricsView.kt").contains("fun InlineLyricsView("))
        assertTrue(main("ui/newui/NewUiHosts.kt").contains("NewUiGate(classic = player, new = player)"))
    }

    /** F-012 (registry #48): the full-screen canvas setting must be read by the only player there is. */
    @Test
    fun `the Aura player honours the full-screen canvas in landscape setting`() {
        val src = main("ui/newui/AuraPlayer.kt")
        assertTrue(src.contains("rememberPreference(iad1tya.echo.music.constants.ImmersiveCanvasOnRotateKey, false)"))
        assertTrue(src.contains("immersiveCanvasArtwork != null -> {"))
        assertTrue(src.contains("private fun AuraImmersiveCanvas("))
        // Review fixes: the docked queue bar stands down (it covered the canvas transport), the decoder follows the local
        // player, and the view runs the shared lookup itself instead of waiting for Thumbnail (off screen in this view).
        assertTrue(src.contains("if (!isFullScreen && !immersiveVideo && immersiveCanvasArtwork == null) {"))
        assertTrue(src.contains("isPlaying = canvasPlaying,"))
        assertTrue(src.contains("iad1tya.echo.music.ui.player.canvasLookupOverNetwork("))
        val thumb = main("ui/player/Thumbnail.kt")
        assertTrue(thumb.contains("internal suspend fun canvasLookupWithoutNetwork("))
        assertTrue(thumb.contains("internal suspend fun canvasLookupOverNetwork("))
    }
}
