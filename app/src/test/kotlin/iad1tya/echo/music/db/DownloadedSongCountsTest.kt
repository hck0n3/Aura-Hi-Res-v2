package iad1tya.echo.music.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 855 (owner test #18, 2026-11-24): Inicio said «Descargado · 21 canciones» and the list it opens showed 20.
 * The tile counted Media3's download index (companion `<id>::video` downloads and COMPLETED records the library no
 * longer lists included); now it counts the list's own rows with the list's own filter.
 */
class DownloadedSongCountsTest {

    private val root: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun src(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    @Test
    fun `the tile shows every downloaded row unless videos are hidden, like the list`() {
        val counts = DownloadedSongCounts(total = 20, videos = 3)
        assertEquals(20, counts.listed(hideVideoSongs = false))
        assertEquals(17, counts.listed(hideVideoSongs = true))
        assertEquals(0, DownloadedSongCounts.EMPTY.listed(hideVideoSongs = true))
        assertEquals(0, DownloadedSongCounts(total = 2, videos = 5).listed(hideVideoSongs = true))
    }

    @Test
    fun `the tile reads the same rows and filter as the list it opens`() {
        val dao = src("db/DatabaseDao.kt")
        assertTrue(dao.contains("@Query(\"SELECT * FROM song WHERE isDownloaded = 1 ORDER BY dateDownload\")"))
        assertTrue(
            dao.contains(
                "@Query(\"SELECT COUNT(1) AS total, COALESCE(SUM(isVideo), 0) AS videos FROM song WHERE isDownloaded = 1\")\n" +
                    "    fun downloadedSongCounts(): Flow<DownloadedSongCounts>",
            ),
        )
        val vm = src("viewmodels/AutoPlaylistViewModel.kt")
        assertTrue(
            "the list's filter is the one [DownloadedSongCounts.listed] mirrors",
            vm.contains(
                "\"downloaded\" -> database.downloadedSongs(sortType, descending)\n" +
                    "                        .map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }",
            ),
        )
        val tile = src("ui/newui/AuraHomeLayout.kt").substringAfter("internal fun AuraQuickAccess(").substringBefore("val tiles = listOf(")
        assertTrue(tile.contains("val (hideVideoSongs) = rememberPreference(HideVideoSongsKey, false)"))
        assertTrue(tile.contains("val downloadedCount = downloadedCounts.listed(hideVideoSongs)"))
        assertFalse("Media3's index is not what the list shows", tile.contains("Download.STATE_COMPLETED"))
        assertFalse(tile.contains("LocalDownloadUtil"))
    }
}
