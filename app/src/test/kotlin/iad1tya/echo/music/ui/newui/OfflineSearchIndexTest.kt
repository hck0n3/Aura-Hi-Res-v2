package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.db.OfflineCollection
import iad1tya.echo.music.db.entities.AlbumEntity
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongEntity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: *«el modo sin conexión… que tenga su propio buscador, pero que solo busque de las que están disponibles
 * en el modo sin conexión, que no busque de otro lado»*.
 *
 * The index is pure: it can only return what it was handed (the offline library), it ignores case and accents, and it finds
 * a song by its title, its artist or its album. The guards pin that the search never reaches the network layers and that the
 * Buscar tab routes to it while offline.
 */
class OfflineSearchIndexTest {
    private fun song(id: String, title: String, artist: String, album: String? = null, albumId: String? = null) = Song(
        song = SongEntity(id = id, title = title),
        artists = listOf(ArtistEntity(id = "ar_$artist", name = artist)),
        album = if (album != null) AlbumEntity(id = albumId ?: "al_$album", title = album, songCount = 1, duration = 1) else null,
    )

    private fun collection(id: String, title: String) = OfflineCollection(id, title, null, downloaded = 1, total = 1)

    private val library = listOf(
        song("1", "Corazón Espinado", "Santana", "Supernatural"),
        song("2", "El Niño del Tambor", "Villancicos"),
        song("3", "Smooth", "Santana", "Supernatural"),
        song("4", "Más Allá", "Ñengo Flow"),
    )
    private val index = OfflineSearchIndex(
        library,
        listOf(collection("al_Supernatural", "Supernatural"), collection("al_X", "Éxitos de Verano")),
        listOf(collection("pl1", "Para Correr")),
    )

    @Test
    fun `fold removes accents and case and keeps letters that do not decompose`() {
        assertEquals("corazon", offlineSearchFold("  CORAZÓN "))
        assertEquals("nino del tambor", offlineSearchFold("Niño   del\tTambor"))
        assertEquals("lodz", offlineSearchFold("Łódź"))
        assertEquals("strasse", offlineSearchFold("Straße"))
        assertEquals("", offlineSearchFold("   "))
    }

    @Test
    fun `accents and case do not matter in either direction`() {
        assertEquals(listOf("1"), index.search("corazon").songs.map { it.id })
        assertEquals(listOf("1"), index.search("CORAZÓN").songs.map { it.id })
        assertEquals(listOf("2"), index.search("nino").songs.map { it.id })
        assertEquals(listOf("4"), index.search("nengo").songs.map { it.id })
        assertEquals(listOf("al_X"), index.search("exitos").albums.map { it.id })
    }

    @Test
    fun `a song is found by artist or album and every word has to match`() {
        assertEquals(setOf("1", "3"), index.search("santana").songs.map { it.id }.toSet())
        assertEquals(setOf("1", "3"), index.search("supernatural").songs.map { it.id }.toSet())
        assertEquals(listOf("3"), index.search("santana smooth").songs.map { it.id })
        assertTrue(index.search("santana villancicos").songs.isEmpty())
    }

    @Test
    fun `title matches rank before artist-only matches`() {
        val idx = OfflineSearchIndex(
            listOf(song("a", "Otra cosa", "Luna"), song("b", "Luna llena", "Otro")),
            emptyList(),
            emptyList(),
        )
        assertEquals(listOf("b", "a"), idx.search("luna").songs.map { it.id })
    }

    @Test
    fun `albums are also found through the artist of their songs, artists carry their offline song count`() {
        assertEquals(listOf("al_Supernatural"), index.search("santana").albums.map { it.id })
        val santana = index.search("santana").artists.single()
        assertEquals(2, santana.songCount)
        assertEquals(setOf("1", "3"), index.songsOf(santana.id).map { it.id }.toSet())
        assertEquals(setOf("1", "3"), index.songsOfAlbum("al_Supernatural").map { it.id }.toSet())
        assertEquals(listOf("pl1"), index.search("correr").playlists.map { it.id })
    }

    @Test
    fun `nothing outside the handed library can ever be returned, and a blank query returns nothing`() {
        assertTrue(index.search("bohemian rhapsody").isEmpty)
        assertTrue(index.search("").isEmpty)
        assertTrue(index.search("   ").isEmpty)
        assertEquals(setOf("1", "2", "3", "4"), index.search("a").songs.map { it.id }.toSet() + index.search("e").songs.map { it.id }.toSet())
    }

    // ── Guards: the search is local and the offline Buscar uses it ───────────────────────────────────────────────────

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the offline search files never import the network, the YouTube search or the whole-library search`() {
        for (path in listOf("ui/newui/OfflineSearchIndex.kt", "ui/newui/AuraOfflineSearch.kt")) {
            val imports = main(path).lines().filter { it.startsWith("import ") }
            for (forbidden in listOf("com.music.innertube", "OnlineSearch", "LocalSearchViewModel", "YouTubeQueue", "okhttp", "java.net")) {
                assertFalse("$path no debe importar $forbidden", imports.any { it.contains(forbidden) })
            }
        }
    }

    @Test
    fun `the Buscar tab and the player search use the offline search while offline, and a pasted link does not start a network queue`() {
        val search = main("ui/newui/AuraSearchScreen.kt")
        assertTrue(search.contains("SearchSource.LOCAL -> if (offlineMode) {"))
        assertTrue(search.contains("AuraOfflineSearchResults("))
        assertTrue("el enlace pegado no abre cola de red sin conexión", search.contains("if (!offlineMode) when (val parsed = YouTubeUrlParser.parse(rawQuery))"))
        val quick = main("ui/newui/AuraPlayerQuickSearch.kt")
        assertTrue(quick.contains("if (offlineMode) {") && quick.contains("AuraOfflineSearchResults("))
    }

    @Test
    fun `the offline home and the search read ONE definition of what is available offline`() {
        val library = main("ui/newui/AuraOfflineSearch.kt")
        // The four sources of the offline body (registry 225) and the complete-cache test come from the same place.
        assertTrue(library.contains("cacheViewModel.fullyCachedSongs"))
        assertTrue(library.contains("downloadedSongs(SongSortType.CREATE_DATE"))
        assertTrue(library.contains("ExportedSongIdsKey") && library.contains("ExportedVideoIdsKey"))
        val home = main("ui/screens/OfflineHome.kt")
        assertTrue(home.contains("rememberOfflineLibrary()"))
        assertTrue(home.contains("OfflineSearchIndex(library.allSongs, library.albums, library.playlists)"))
        assertFalse("el cuerpo ya no lee las fuentes por su cuenta", home.contains("fullyCachedSongs"))
    }
}
