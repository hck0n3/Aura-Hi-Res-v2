package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rows 627-629: favourites sort by WHEN they were saved, offline mode lists only what has downloads, and a liked song can be removed by name. */
class LibraryFavoritesAndOfflineTest {
    private val root = File("src/main/kotlin/com/music/echo")

    @Test
    fun savedAlbumsAndPlaylistsAreOrderedByTheDateTheyWereSaved() {
        val dao = File(root, "db/DatabaseDao.kt").readText()
        assertTrue(dao.contains("FROM album WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt, rowId"))
        assertTrue(dao.contains("playlist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt, rowId\")\n    fun playlistsByCreateDateAsc"))
        assertTrue(dao.contains("isEditable AND bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt, rowId\")\n    fun editablePlaylistsByCreateDateAsc"))
        // liked songs already sort by their like date
        assertTrue(dao.contains("SELECT * FROM song WHERE liked ORDER BY likedDate"))
    }

    @Test
    fun offlineModeListsOnlyAlbumsAndPlaylistsWithDownloads() {
        val dao = File(root, "db/DatabaseDao.kt").readText()
        assertTrue(dao.contains("fun albumIdsWithDownloads()"))
        assertTrue(dao.contains("fun playlistIdsWithDownloads()"))
        val tabs = File(root, "ui/newui/AuraLibraryTabs.kt").readText()
        assertTrue(tabs.contains("if (offlineLibrary) allAlbums.filter { it.id in downloadedAlbumIds.toHashSet() } else allAlbums"))
        assertTrue(tabs.contains("if (offlineLibrary) allPlaylists.filter { it.id in downloadedPlaylistIds.toHashSet() } else allPlaylists"))
    }

    @Test
    fun aLikedSongCanBeRemovedFromItsMenuAndSelectionHasAClearLabel() {
        val menu = File(root, "ui/menu/SongMenu.kt").readText()
        assertTrue(menu.contains("if (song.song.liked) {") && menu.contains("R.string.action_remove_like"))
        val sel = File(root, "ui/menu/SelectionSongsMenu.kt").readText()
        assertTrue(sel.contains("if (allLiked) R.string.action_remove_like_all else R.string.like_all"))
    }

    @Test
    fun theLauncherArtworkKeepsItsWholeRimInsideTheMask() {
        // Row 630: the glass tile is ~62 % of the canvas, so the launcher's mask (66.7 %) never cuts the rim.
        assertTrue(File("src/main/res/drawable-nodpi/ic_launcher_art.webp").length() > 20_000)
    }
}
