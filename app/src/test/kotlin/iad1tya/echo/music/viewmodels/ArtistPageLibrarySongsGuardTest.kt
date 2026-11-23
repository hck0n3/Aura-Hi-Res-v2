package iad1tya.echo.music.viewmodels

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-23 (registry row 673): «En tu biblioteca» on an artist's page = only the songs he LIKED and the songs
 * in HIS playlists. The old query (`artistLibraryOrLikedSongs`) also listed the YouTube-library / Spotify-import
 * `inLibrary` stamp, downloads, device files and any playlist row (even a list only opened). No Android SDK in the
 * unit job, so the wiring is pinned as source text.
 */
class ArtistPageLibrarySongsGuardTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun query(dao: String, fn: String) = dao.substringBefore("fun $fn(").substringAfterLast("@Query(")

    @Test
    fun `the artist page query keeps only likes and the user's own playlists`() {
        val q = query(main("db/DatabaseDao.kt"), "artistPageLibrarySongs")
        val where = q.substringAfter("WHERE").substringBefore("ORDER BY")
        assertTrue(where.contains("song.liked = 1"))
        assertTrue(where.contains("playlist.bookmarkedAt IS NOT NULL OR playlist.browseId IS NULL OR playlist.isLocal = 1"))
        assertTrue("la lista de recomendaciones no es del usuario", where.contains("playlist.id != 'AURA_AI_RECS'"))
        assertTrue(main("reco/AutoRecoPlaylistWorker.kt").contains("const val PLAYLIST_ID = \"AURA_AI_RECS\""))
        for (gone in listOf("inLibrary IS NOT NULL", "isDownloaded", "song.isLocal")) {
            assertFalse("vuelve a contar $gone", where.contains(gone))
        }
        // Same name bridging as before (YouTube channel id vs local LA######## row).
        assertTrue(where.contains("OR (:artistName != '' AND LOWER(artist.name) = LOWER(:artistName))"))
    }

    @Test
    fun `the old broad query is untouched for the artist menu and the library`() {
        val q = query(main("db/DatabaseDao.kt"), "artistLibraryOrLikedSongs")
        for (kept in listOf("song.inLibrary IS NOT NULL", "song.isDownloaded = 1", "song.isLocal = 1", "EXISTS (SELECT 1 FROM playlist_song_map")) {
            assertTrue("falta $kept", q.contains(kept))
        }
        assertEquals(2, Regex("""artistLibraryOrLikedSongs\(""").findAll(main("ui/menu/ArtistMenu.kt")).count())
        assertTrue(main("viewmodels/LibraryViewModels.kt").contains("database.artistLibraryOrLikedSongs("))
    }

    @Test
    fun `only the online section reads the narrow list, the local and offline page keep the broad one`() {
        val vm = main("viewmodels/ArtistViewModel.kt")
        assertTrue(vm.contains("database.artistPageLibrarySongs(artistId, name)"))
        assertTrue(vm.contains("database.artistLibraryOrLikedSongs(artistId, name)"))
        val screen = main("ui/newui/AuraArtistScreen.kt")
        assertTrue(screen.contains("val artistPageLibrarySongs by viewModel.artistPageLibrarySongs.collectAsState()"))
        assertTrue(screen.contains("if (hideExplicit) artistPageLibrarySongs.filter { !it.song.explicit } else artistPageLibrarySongs"))
        // showLocal (device-files artist / offline fallback) still decides and plays from the broad catalogue.
        assertTrue(screen.contains("showLocal = artistPage == null && hasFailed && allLibrarySongs.isNotEmpty()"))
        assertTrue(screen.contains("allLibrarySongs.filter { !it.song.explicit }"))
        // Row 140/146: the section is still placed after albums and videos.
        assertTrue(screen.contains("artistLibraryInsertIndex(onlineArtistSections.map { it.title })"))
    }

    @Test
    fun `the section's full list is the same narrow list, every other way in keeps the wide one`() {
        val screen = main("ui/newui/AuraArtistScreen.kt")
        assertTrue(screen.contains("onClick = { navController.navigate(\"artist/\$artistId/songs?ownLibrary=true\") },"))
        // The local / offline page's «Canciones» and the classic screen open the wide list.
        assertTrue(screen.contains("navController.navigate(\"artist/\${viewModel.artistId}/songs\")"))
        assertTrue(main("ui/screens/artist/ArtistScreen.kt").contains("navController.navigate(\"artist/\${viewModel.artistId}/songs\")"))
        val nav = main("ui/screens/NavigationBuilder.kt")
        assertTrue(nav.contains("route = \"artist/{artistId}/songs?ownLibrary={ownLibrary}\""))
        assertTrue(nav.substringAfter("navArgument(\"ownLibrary\")").substringBefore("}").contains("defaultValue = false"))
        val vm = main("viewmodels/LibraryViewModels.kt").substringAfter("class ArtistSongsViewModel").substringBefore("class LibraryMixViewModel")
        assertTrue(vm.contains("private val ownLibrary = savedStateHandle.get<Boolean>(\"ownLibrary\") ?: false"))
        assertTrue(vm.contains("(if (ownLibrary) database.artistPageLibrarySongs(artistId, name) else database.artistLibraryOrLikedSongs(artistId, name))"))
    }
}
