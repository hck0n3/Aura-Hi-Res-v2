package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 425 (owner 2026-10-18): «el modo sin conexión quiero que muestre SIEMPRE las listas y álbumes
 * descargados». The offline body was a pure song list; the albums and playlists you downloaded were reachable only song by
 * song. Pins the query (it must count REAL downloads, `isDownloaded`) and that the body draws both shelves before the songs.
 */
class OfflineCollectionsTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the queries only list collections with a really downloaded song`() {
        val dao = main("db/DatabaseDao.kt")
        val albums = dao.substringAfter("fun downloadedAlbums()").let { dao.substringBefore("fun downloadedAlbums()").takeLast(900) }
        assertTrue(albums.contains("WHERE song.isDownloaded = 1"))
        val playlists = dao.substringBefore("fun downloadedPlaylists()").takeLast(1500)
        assertTrue(playlists.contains("s.isDownloaded = 1"))
        assertTrue("solo listas de la biblioteca", playlists.contains("playlist.bookmarkedAt IS NOT NULL"))
    }

    @Test
    fun `the offline body shows albums then playlists before the song sections, and a partial one says how much plays`() {
        val src = main("ui/screens/OfflineHome.kt")
        val body = src.substringAfter("LazyColumn(").substringBefore("section(\"downloaded\"")
        assertTrue(body.indexOf("offline_albums") in 0 until body.indexOf("offline_playlists"))
        assertTrue(body.indexOf("offline_playlists") < body.indexOf("offline_empty"))
        assertTrue(src.contains("navController.navigate(\"album/\${it.id}\")"))
        assertTrue(src.contains("navController.navigate(\"local_playlist/\${it.id}\")"))
        assertTrue(src.contains("R.string.offline_collection_progress"))
    }
}
