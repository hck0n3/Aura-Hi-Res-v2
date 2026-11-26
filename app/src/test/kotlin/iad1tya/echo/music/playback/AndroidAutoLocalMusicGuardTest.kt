package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 883: the music on this phone in Android Auto and in Library search.
 *  - Auto's Playlists tab carries a «Local» entry (the phone's audio files, as Biblioteca ▸ Local lists them) that
 *    browses, plays and shuffles in the same memory as the in-app Local tab; the root keeps its four tabs.
 *  - Browse ids are read with [AutoBrowsePath.segments], never `split("/")`, so a `content://` song id stays whole.
 *  - Auto's voice/text search and the Library search screen already include local songs, albums and artists.
 */
class AndroidAutoLocalMusicGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun read(path: String) = File(root, path).readText()

    private val cb by lazy { read("app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt") }

    @Test
    fun `the playlists tab lists Local after Liked and Downloaded and the root keeps four tabs`() {
        val playlistRoot = cb.substringAfter("MusicService.PLAYLIST -> {").substringBefore("database.playlistsByCreateDateAsc()")
        val liked = playlistRoot.indexOf("likedSongsMediaItem(likedSongCount),")
        val downloaded = playlistRoot.indexOf("downloadedSongsMediaItem(downloadedSongCount),")
        val local = playlistRoot.indexOf("localSongsMediaItem(localAudioSongs().size),")
        assertTrue(liked in 0 until downloaded && downloaded < local)
        val rootChildren = cb.substringAfter("private fun rootChildren() = listOf(").substringBefore("private suspend fun getMediaItem(")
        assertEquals("Android Auto shows at most four root tabs", 4, Regex("browsableMediaItem\\(").findAll(rootChildren).count())
    }

    @Test
    fun `Local browses, plays, resolves and shares the in-app shuffle memory`() {
        assertEquals("browse children + the queue it plays", 2, cb.split("AutoBrowsePath.LOCAL_PLAYLIST_ID -> flowOf(localAudioSongs())").size - 1)
        assertTrue(cb.contains("AutoBrowsePath.LOCAL_PLAYLIST_ID -> AutoBrowsePath.LOCAL_CONTEXT_ID"))
        assertTrue(cb.contains("AutoBrowsePath.LOCAL_PLAYLIST_ID -> localSongsMediaItem(localAudioSongs().size)"))
        assertTrue(cb.contains("\"\${MusicService.PLAYLIST}/\${AutoBrowsePath.LOCAL_PLAYLIST_ID}\","))
        assertTrue(cb.contains("context.getString(R.string.filter_local),"))
        val songs = cb.substringAfter("private suspend fun localAudioSongs(): List<Song> =").substringBefore("private fun ")
        assertTrue(songs.contains("database.localSongs().first().filter { song ->"))
        assertTrue("row 583: no video files", songs.contains("!song.song.isVideo && SupportedLocalAudio.isSupportedMimeType(song.format?.mimeType)"))
        assertEquals("local:music", AutoBrowsePath.LOCAL_CONTEXT_ID)
        assertTrue(read("app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt").contains("contextId = \"local:music\","))
        assertEquals("LP_LOCAL", AutoBrowsePath.LOCAL_PLAYLIST_ID)
    }

    @Test
    fun `browse ids keep a local song id whole`() {
        assertTrue(cb.contains("val path = mediaItems.firstOrNull()?.mediaId?.let(AutoBrowsePath::segments)"))
        assertTrue(cb.contains("val path = AutoBrowsePath.segments(mediaId)"))
        assertFalse(cb.contains(".split(\"/\")"))
    }

    @Test
    fun `search in the car and in the Library already reaches local music`() {
        assertEquals(
            "onGetSearchResult and the tap that replays it scan every song row, local ones included",
            2,
            cb.split("val localSongs = database.allSongs().first().filter { song ->").size - 1,
        )
        val dao = read("app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt")
        val songs = dao.substringBefore("fun searchSongs(").substringAfterLast("@Query(")
        assertTrue(songs.contains("(inLibrary IS NOT NULL OR liked = 1 OR isLocal = 1)"))
        val artists = dao.substringBefore("fun searchArtists(").substringAfterLast("@Query(")
        assertTrue(artists.contains("(song.inLibrary IS NOT NULL OR song.liked = 1 OR song.isLocal = 1)"))
        val albums = dao.substringBefore("fun searchAlbums(").substringAfterLast("@Query(")
        assertTrue(albums.contains("(song.inLibrary IS NOT NULL OR song.isLocal = 1)"))
        val vm = read("app/src/main/kotlin/com/music/echo/viewmodels/LocalSearchViewModel.kt")
        for (call in listOf("database.searchSongs(query, PREVIEW_SIZE)", "database.searchAlbums(query, PREVIEW_SIZE)", "database.searchArtists(query, PREVIEW_SIZE)")) {
            assertTrue(call, vm.contains(call))
        }
    }
}
