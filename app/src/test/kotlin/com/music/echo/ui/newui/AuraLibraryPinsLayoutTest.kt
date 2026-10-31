package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Biblioteca (owner 2026-10-20): *«no quiero que se vean todos los artistas allá abajo, ni mis playlists, a menos de que yo
 * pueda pinear playlists y álbumes… e incluso artistas… y esta parte rediséñala»*.
 *
 * The numbers behind the pinned cover grid and the shortcut tiles, and source guards for the wiring (there is no Android SDK in
 * the unit job, so what cannot be rendered is pinned as text).
 */
class AuraLibraryPinsLayoutTest {

    // ── The numbers ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the pinned grid is two columns on a phone and four unfolded, never one and never more than four`() {
        assertEquals(2, auraLibraryPinColumns(300.dp))
        assertEquals(2, auraLibraryPinColumns(393.dp))
        assertEquals(2, auraLibraryPinColumns(430.dp))
        assertEquals(3, auraLibraryPinColumns(520.dp))
        // An unfolded Fold with the player column open: the pane is ~645 dp and shows FOUR covers (registry rows 406 / 461).
        assertEquals(4, auraLibraryPinColumns(645.dp))
        assertEquals(4, auraLibraryPinColumns(900.dp))
        assertEquals(4, auraLibraryPinColumns(1400.dp))
        var previous = 0
        for (w in 280..1500 step 10) {
            val columns = auraLibraryPinColumns(w.dp)
            assertTrue("at $w dp: $columns columns", columns in 2..4)
            assertTrue("columns never shrink as the pane grows (at $w dp)", columns >= previous)
            previous = columns
        }
    }

    @Test
    fun `the covers fill the pane exactly between the gutters and never fall under the minimum they were laid out for`() {
        for (w in listOf(360, 393, 430, 520, 600, 645, 700, 840, 1100)) {
            val pane = w.dp
            val columns = auraLibraryPinColumns(pane)
            val card = auraLibraryPinCardWidth(pane, columns)
            val used = card.value * columns + AURA_LIBRARY_PIN_GAP.value * (columns - 1) + AuraSpacing.Gutter.value * 2
            assertEquals("at $w dp the row must span the pane", w.toFloat(), used, 0.01f)
            // Two columns is the floor even on a very narrow pane, so the 140 dp promise holds from 340 dp up.
            if (w >= 340) assertTrue("at $w dp a cover is ${card.value} dp", card.value >= AURA_LIBRARY_PIN_MIN_CARD.value - 0.01f)
        }
    }

    @Test
    fun `the shortcut tiles are two per row on a phone and spread out on a wide pane`() {
        assertEquals(2, auraLibraryShortcutColumns(393.dp))
        assertEquals(3, auraLibraryShortcutColumns(645.dp))
        assertEquals(4, auraLibraryShortcutColumns(900.dp))
        assertEquals(5, auraLibraryShortcutColumns(1500.dp))
        for (w in 280..1500 step 10) assertTrue(auraLibraryShortcutColumns(w.dp) in 2..5)
    }

    // ── Wiring ──────────────────────────────────────────────────────────────────────────────────

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val hub: String by lazy {
        main("ui/newui/AuraLibraryTabs.kt")
            .substringAfter("fun AuraLibraryHub(")
            .substringBefore("// ── 2. Canciones")
    }

    @Test
    fun `the hub no longer dumps every artist, playlist and album under the shortcuts`() {
        assertFalse("the all-items merge is gone", hub.contains("val allItems") || hub.contains("nonArtistItems") || hub.contains("artistItems"))
        assertFalse("no list of every artist / album / playlist is drawn", hub.contains("items(artistItems") || hub.contains("items(nonArtistItems"))
        assertTrue("the hub draws what was pinned", hub.contains("resolveLibraryPins(pins, playlists, albums, artists)"))
        assertTrue("nothing pinned → the explaining card", hub.contains("AuraPinsEmptyCard("))
        assertTrue("edit mode reorders by dragging", hub.contains("ReorderableItem(") && hub.contains(".draggableHandle()"))
        assertTrue("the order is written once, when the finger lifts", hub.contains("LibraryPins.reordered(it, order)"))
        assertTrue("the full lists are one tap away", hub.contains("AuraLibraryJumpRow(") && hub.contains("onOpenFilter"))
    }

    @Test
    fun `every shortcut button the hub always had is still there with its toggle`() {
        for (route in listOf(
            "auto_playlist/liked", "auto_playlist/exported", "auto_playlist/exported_videos", "top_playlist/",
            "auto_playlist/downloaded", "cache_playlist/cached", "favorite_albums", "podcasts",
        )) {
            assertTrue("falta el acceso a $route", hub.contains("\"$route"))
        }
        // Row 570 (owner 2026-10-31: «nada repetido»): Novedades is the bar's tab; the hub no longer carries a second door to the radar.
        assertFalse("el atajo Novedades se quitó del hub", hub.contains("navController.navigate(\"release_radar\")"))
        assertTrue("Local sigue visible nada más abrir Biblioteca (registry row 185)", hub.contains("AuraLibraryShortcut(AuraIcons.Device, localLabel, onOpenLocal)"))
        for (toggle in listOf("ShowLikedPlaylistKey", "ShowExportedPlaylistKey", "ShowExportedVideosPlaylistKey", "ShowTopPlaylistKey", "ShowDownloadedPlaylistKey", "ShowCachedPlaylistKey")) {
            assertTrue("el interruptor $toggle sigue mandando", hub.contains("rememberPreference($toggle, true)"))
        }
        assertTrue("pull-to-refresh and the sync-on-open stay", hub.contains("PullToRefreshBox(") && hub.contains("viewModel.syncAllLibrary()"))
        assertTrue("list/grid switch and sort stay", hub.contains("AuraViewTypeToggle(viewType)") && hub.contains("AuraSortControl("))
    }

    @Test
    fun `the full lists still show everything - pins only add a badge to them`() {
        val tabs = main("ui/newui/AuraLibraryTabs.kt")
        assertTrue(tabs.contains("viewModel.allAlbums.collectAsState()"))
        assertTrue(tabs.contains("viewModel.filteredArtists.collectAsState()"))
        assertTrue(tabs.contains("viewModel.allPlaylists.collectAsState()"))
        assertTrue("the albums grid still lists every album", tabs.contains("items(albums, key = { it.id })"))
        assertTrue("the artists grid still lists every artist", tabs.contains("items(artists, key = { it.id })"))
        assertTrue("the playlists grid still lists every playlist", tabs.contains("items(filteredPlaylists.distinctBy { it.id }, key = { it.id })"))
        assertTrue("a pinned cover only gains a badge, it is never filtered out", tabs.contains("badge = if (LibraryPin.keyOf(LibraryPinKind.ALBUM, album.id) in pinnedKeys)"))
    }

    @Test
    fun `all six playlist, album and artist menus carry the pin row`() {
        for ((file, call) in listOf(
            "ui/menu/PlaylistMenu.kt" to "playlistPinMenuItem(",
            "ui/menu/YouTubePlaylistMenu.kt" to "playlistPinMenuItem(",
            "ui/menu/AlbumMenu.kt" to "albumPinMenuItem(",
            "ui/menu/YouTubeAlbumMenu.kt" to "albumPinMenuItem(",
            "ui/menu/ArtistMenu.kt" to "artistPinMenuItem(",
            "ui/menu/YouTubeArtistMenu.kt" to "artistPinMenuItem(",
        )) {
            assertTrue("$file lost the «Anclar en Biblioteca» row", main(file).contains(call))
        }
        val playlistMenu = main("ui/menu/PlaylistMenu.kt")
        assertTrue(
            "the automatic lists (Me gusta, Descargado…) are not pinnable",
            playlistMenu.contains("if (autoPlaylist != true && downloadPlaylist != true)") && playlistMenu.contains("playlistPinMenuItem("),
        )
    }

    @Test
    fun `the pins live in the DataStore - no Room table, no migration - under a name that must not change`() {
        val pins = main("utils/LibraryPins.kt")
        assertFalse("a Room table would be a schema change (registry row 89)", pins.contains("androidx.room"))
        assertFalse(File(root, "app/src/main/kotlin/com/music/echo/db/entities").listFiles().orEmpty().any { it.name.contains("LibraryPin") })
        val keys = main("constants/LibraryPinKeys.kt")
        // Renaming the key orphans every pin in silence, exactly like renaming the `settings` file (SettingsSurviveUpdateTest).
        assertTrue(keys.contains("stringPreferencesKey(\"libraryPinsV1\")"))
        assertTrue("atomic read-modify-write", pins.contains("context.dataStore.edit"))
        assertFalse("the Home pin («Fijar playlist al inicio») is a separate feature and stays that way", pins.contains("speedDial"))
    }
}
