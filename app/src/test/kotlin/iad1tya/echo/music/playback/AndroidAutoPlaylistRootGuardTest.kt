package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 786 (Android Auto): the «Descargadas» card counts only finished downloads, like the list it opens, and browsing the
 * playlists root while offline (manual switch or the playback offline session) does not call YouTube.
 */
class AndroidAutoPlaylistRootGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    @Test
    fun `the downloaded count and the offline root follow the list they open`() {
        val cb = File(root, "app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt").readText()
        val playlistRoot = cb.substringAfter("MusicService.PLAYLIST -> {").substringBefore("likedSongsMediaItem(likedSongCount),")
        assertTrue(playlistRoot.contains("downloadUtil.downloads.value.values.count { it.state == Download.STATE_COMPLETED }"))
        assertFalse("every download state was counted", playlistRoot.contains("downloadUtil.downloads.value.size"))
        assertTrue(playlistRoot.contains("val offline = PlaybackOfflineSession.isActive ||"))
        assertTrue(playlistRoot.contains("(context.dataStore.data.first()[iad1tya.echo.music.constants.OfflineModeKey] ?: false)"))
        assertTrue(playlistRoot.contains("val youtubePlaylists = if (offline) emptyList<PlaylistItem>() else try {"))
        assertTrue("the online fetch is still there when online", playlistRoot.contains("YouTube.home().getOrNull()?.sections"))
        // The list the card opens keeps only completed downloads.
        assertTrue(cb.contains("downloads[it.id]?.state == Download.STATE_COMPLETED"))
    }
}
