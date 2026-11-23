package iad1tya.echo.music.reco

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 720 — the quick-skip log is only worth anything if the service feeds it on the user's skips and both
 * profile builders read it. Pinned in source, the convention of the other guard tests.
 */
class QuickSkipWiringTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the service records on a seek, from the seek's own old position, off the main thread`() {
        val service = main("playback/MusicService.kt")
        val discontinuity = service.substringAfter("override fun onPositionDiscontinuity(").substringBefore("private fun recordQuickSkip(")
        assertTrue(discontinuity.contains("if (reason == Player.DISCONTINUITY_REASON_SEEK) {"))
        assertTrue(discontinuity.contains("recordQuickSkip(oldPosition, newPosition)"))
        val record = service.substringAfter("private fun recordQuickSkip(").substringBefore("\n    }\n")
        assertTrue("only a move to the NEXT song", record.contains("toNextInOrder = player.previousMediaItemIndex == oldPosition.mediaItemIndex"))
        assertTrue(record.contains("listenedMs = oldPosition.positionMs"))
        assertTrue("the user's own history threshold", record.contains("historyThresholdMs = historyDurationMsHint.toLong()"))
        assertTrue(record.contains("historyPaused = pauseListenHistoryHint"))
        assertTrue(record.contains("listenTogetherManager.isInRoom && !listenTogetherManager.isHost"))
        assertTrue(record.contains("castConnectionHandler?.isCasting?.value == true || onlyShowingMirror"))
        assertTrue(record.contains("QuickSkipLog.isMusicRequestContext(radioOriginContextId)"))
        for (appMove in listOf(
            "consecutivePlaybackErr > 0", "consecutiveNoMusicSkips > 0", "player.playerError != null",
            "old.mediaId in recentlyFailedSongs", "old.mediaId in dislikeHandledHere", "PlaybackOfflineSession.isActive",
        )) assertTrue("the app's own move: $appMove", record.contains(appMove))
        assertTrue(record.contains("scope.launch(Dispatchers.IO)"))
        assertFalse("ids and names stay in the private file, never in the shared log", record.contains("Timber"))
    }

    @Test
    fun `both profile builders read the log`() {
        val service = main("playback/MusicService.kt")
        val taste = service.substringAfter("internal suspend fun tasteProfile()").substringBefore("cachedTaste = it")
        assertTrue(taste.contains("QuickSkipLog.read(this@MusicService)"))
        assertTrue(taste.contains("quickSkips = quickSkips"))
        val home = main("viewmodels/HomeViewModel.kt")
        val refresh = home.substringAfter("private suspend fun refreshTasteProfile()").substringBefore("private fun List<Song>.rankedByTaste()")
        assertTrue(refresh.contains("QuickSkipLog.read(context)"))
        assertTrue(refresh.contains("quickSkips = quickSkips"))
    }

    @Test
    fun `the engine drops the skips of what he marked No me gusta`() {
        val engine = main("reco/AffinityEngine.kt")
        val fold = engine.substringAfter("if (quickSkips.isNotEmpty()) {").substringBefore("// B4: seed taste")
        assertTrue(fold.contains("if (s.songId in disliked.songs) return@forEach"))
        assertTrue(fold.contains("isDislikedArtist(id, name.orEmpty())"))
        assertTrue(fold.contains("-min(v, QUICK_SKIP_KEY_CAP)"))
    }
}
