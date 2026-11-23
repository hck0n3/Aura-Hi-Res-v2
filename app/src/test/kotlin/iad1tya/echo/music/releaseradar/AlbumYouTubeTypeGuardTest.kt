package iad1tya.echo.music.releaseradar

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 726: opening an album reads YouTube's own type from its header and (a) the album page uses it,
 * (b) a Novedades row with no type («Release») learns it, before the one-track rule of row 710 runs.
 */
class AlbumYouTubeTypeGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun source(path: String): String {
        val f = File(root, path)
        assertTrue("$path se movió: actualiza este test", f.isFile)
        return f.readText()
    }

    @Test
    fun `the header type text is parsed into the album page`() {
        val youTube = source("innertube/src/main/kotlin/com/music/innertube/YouTube.kt")
        assertTrue(youTube.contains("typeText = AlbumPage.getTypeText("))
        val page = source("innertube/src/main/kotlin/com/music/innertube/pages/AlbumPage.kt")
        assertTrue(page.contains("header?.subtitle?.runs?.firstOrNull()?.text"))
    }

    @Test
    fun `the radar row is only filled when it has no type`() {
        val dao = source("app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt")
        assertTrue(
            "solo una fila sin tipo aprende el de YouTube; Spotify y el título no se pisan",
            dao.contains("UPDATE release_radar SET type = :type WHERE playId = :albumId AND type = 'Release'"),
        )
        assertTrue(ReleaseRadarMatching.TYPE_UNKNOWN == "Release")
    }

    @Test
    fun `the album view model fills the radar row before the one-track rule`() {
        val vm = source("app/src/main/kotlin/com/music/echo/viewmodels/AlbumViewModel.kt")
        assertTrue(vm.contains("youTubeType.value = ReleaseRadarMatching.youTubeHeaderType(it.typeText)"))
        assertTrue(vm.contains("youTubeType.value = ReleaseRadarMatching.youTubeHeaderType(cached.typeText)"))
        val fill = vm.indexOf("database.fillRadarReleaseType(albumId, it)")
        val single = vm.indexOf("database.markRadarReleaseSingle(albumId)")
        assertTrue(fill in 0 until single)
    }

    @Test
    fun `both album screens pass the YouTube type to the page type`() {
        val aura = source("app/src/main/kotlin/com/music/echo/ui/newui/AuraAlbumScreen.kt")
        assertTrue(aura.contains("albumReleaseType(album, radarType, youTubeType)"))
        assertTrue(aura.contains(".albumPageType(radarType, album.album.songCount, album.album.title, youTubeType)"))
        val classic = source("app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt")
        assertTrue(
            Regex(Regex.escape("albumReleaseType(albumWithSongs, radarType, youTubeType)")).findAll(classic).count() == 2,
        )
    }
}
