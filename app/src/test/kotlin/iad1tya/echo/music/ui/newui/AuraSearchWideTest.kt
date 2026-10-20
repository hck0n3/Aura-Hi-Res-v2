package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20 (unfolded Z Fold): the search shows small results, opens a second floating window per tap, and cannot tell
 * a song from an album by its cover. The numbers behind the wide layouts, what a result row shows, and source guards for the
 * wiring (there is no Android SDK in the unit job, so what cannot be rendered is pinned as text).
 */
class AuraSearchWideTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun file(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }

    private fun main(path: String) = file("app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `results and detail sit side by side only on a room that really is wide`() {
        // an unfolded Z Fold: ~750 x 830 dp window, a little less of content
        assertTrue(auraSearchSplitFits(750f, 700f))
        assertTrue(auraSearchSplitFits(829f, 560f))
        // a folded phone, in either orientation of its cover screen
        assertFalse(auraSearchSplitFits(412f, 800f))
        assertFalse(auraSearchSplitFits(360f, 780f))
        // a phone on its side is wide but short: it keeps its single column
        assertFalse(auraSearchSplitFits(800f, 300f))
        // the content pane left of the split player (~400 dp) keeps it too
        assertFalse(auraSearchSplitFits(420f, 700f))
        assertTrue("the boundary is inclusive, like the rest of the wide chrome", auraSearchSplitFits(600f, 420f))
    }

    @Test
    fun `the detail half is a menu-sized column that follows the room within bounds`() {
        for (total in listOf(600, 700, 750, 900, 1100, 1400)) {
            val w = auraSearchDetailWidth(total.dp).value
            assertTrue("detail $w at $total", w in 300f..520f)
            assertTrue("the results keep more than the detail's minimum at $total", total - w >= 280f)
        }
        assertTrue(auraSearchDetailWidth(1100.dp) > auraSearchDetailWidth(700.dp))
    }

    @Test
    fun `filter grids use as many big cells as fit and never fewer than the floor`() {
        // 150 dp covers, 14 dp apart: 400 dp of room -> 2; 760 dp -> 4
        assertEquals(2, auraSearchGridColumns(358.dp, 150.dp, 14.dp))
        assertEquals(4, auraSearchGridColumns(718.dp, 150.dp, 14.dp))
        // videos: 230 dp cells, a single column on a narrow half
        assertEquals(1, auraSearchGridColumns(358.dp, 230.dp, 14.dp, floor = 1))
        assertEquals(2, auraSearchGridColumns(500.dp, 230.dp, 14.dp, floor = 1))
        // the floor wins on a tiny room
        assertEquals(2, auraSearchGridColumns(100.dp, 150.dp, 14.dp))
    }

    @Test
    fun `explore tiles stay two on a phone and grow with a wide pane`() {
        assertEquals(2, auraExploreColumns(412.dp, wide = false))
        assertEquals(2, auraExploreColumns(900.dp, wide = false))
        assertEquals(3, auraExploreColumns(750.dp, wide = true))
        assertEquals(5, auraExploreColumns(1100.dp, wide = true))
        assertEquals(5, auraExploreColumns(2000.dp, wide = true))
    }

    @Test
    fun `a result shows its artist, its album, its duration and the explicit mark only where the data exists`() {
        val song = SongItem(
            id = "s1",
            title = "Title",
            artists = listOf(Artist("Ana", "a1"), Artist("Bo", null)),
            album = Album("Disc", "al1"),
            duration = 204,
            thumbnail = "t",
            explicit = true,
        )
        val entry = auraSearchEntry(song)
        assertEquals(AuraContentKind.Song, entry.kind)
        assertEquals("Ana, Bo", entry.artist)
        assertEquals("Disc", entry.meta)
        assertEquals("3:24", entry.duration)
        assertTrue(entry.explicit)

        val bare = auraSearchEntry(song.copy(artists = emptyList(), album = null, duration = null, explicit = false))
        assertNull("never invented", bare.artist)
        assertNull(bare.meta)
        assertNull(bare.duration)
        assertFalse(bare.explicit)

        val album = AlbumItem(
            browseId = "b", playlistId = "p", title = "Disc", artists = listOf(Artist("Ana", "a1")), year = 2021, thumbnail = "t",
        )
        val albumEntry = auraSearchEntry(album)
        assertEquals(AuraContentKind.Album, albumEntry.kind)
        assertEquals("Ana", albumEntry.artist)
        assertEquals("2021", albumEntry.meta)
        assertNull("an album has no per-track duration", albumEntry.duration)

        val artist = auraSearchEntry(
            ArtistItem(id = "a", title = "Ana", thumbnail = "t", shuffleEndpoint = null, radioEndpoint = null),
        )
        assertEquals(AuraContentKind.Artist, artist.kind)
        assertNull(artist.artist)

        val playlist = auraSearchEntry(
            PlaylistItem(
                id = "l", title = "Mix", author = Artist("Ana", null), songCountText = "40 canciones", thumbnail = "t",
                playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null,
            ),
        )
        assertEquals(AuraContentKind.Playlist, playlist.kind)
        assertEquals("Ana", playlist.artist)
        assertEquals("40 canciones", playlist.meta)
    }

    @Test
    fun `songs, albums, artists and videos are told apart by shape as well as by the pill`() {
        // The shapes live in AuraContentType.kt (shared by Inicio, Novedades and the library): a video is 16:9, an artist a circle,
        // a release a square. Read as text — building the icons needs the Android runtime.
        val type = main("ui/newui/AuraContentType.kt")
        assertTrue(type.contains("private const val VIDEO_RATIO = 16f / 9f"))
        val artist = type.substringAfter("AuraContentKind.Artist -> AuraTypeVisual(").substringBefore("AuraContentKind.Podcast")
        assertTrue("an artist is a circle", artist.contains("CircleShape"))
        val album = type.substringAfter("AuraContentKind.Album -> AuraTypeVisual(").substringBefore("AuraContentKind.Ep")
        assertTrue(album.contains("AuraShapes.Artwork, 1f"))
        // the search gives every one of them a bigger cover than the 54 dp phone row, videos keeping their frame
        assertTrue(auraSearchRowCoverWidth(AuraContentKind.Song) > 54.dp)
        assertTrue(auraSearchRowCoverWidth(AuraContentKind.Video) > auraSearchRowCoverWidth(AuraContentKind.Song))
        assertEquals(auraSearchRowCoverWidth(AuraContentKind.Album), auraSearchRowCoverWidth(AuraContentKind.Artist))
    }

    @Test
    fun `every kind has its pill label in English and in Spanish`() {
        val kinds = listOf("song", "video", "album", "single", "ep", "playlist", "artist", "podcast")
        for (dir in listOf("values", "values-es")) {
            val xml = file("app/src/main/res/$dir/aura_search_strings.xml").readText()
            for (kind in kinds) assertTrue("$dir lacks aura_kind_$kind", xml.contains("name=\"aura_kind_$kind\""))
            for (name in listOf("aura_search_detail_empty_title", "aura_search_detail_empty_body", "aura_search_played")) {
                assertTrue("$dir lacks $name", xml.contains("name=\"$name\""))
            }
        }
        val kindsInCode = main("ui/newui/AuraContentType.kt").substringAfter("enum class AuraContentKind {").substringBefore("}")
        assertEquals("a new content kind needs its pill label", kinds.size, kindsInCode.lines().count { it.trim().endsWith(",") })
    }

    @Test
    fun `every wide result carries the type pill`() {
        val wide = main("ui/newui/AuraSearchWide.kt")
        val row = wide.substringAfter("internal fun AuraSearchWideRow(").substringBefore("/** [AuraSearchWideRow] for a YouTube Music result. */")
        assertTrue(row.contains("AuraTypeBadge(entry.kind)"))
        assertTrue("the explicit mark comes from the data", row.contains("if (entry.explicit)") && row.contains("AuraExplicitBadge()"))
        val card = wide.substringAfter("internal fun AuraSearchGridCard(").substringBefore("// ── The split")
        assertTrue(card.contains("AuraTypeBadge(entry.kind"))
        assertFalse("a second floating window is exactly what the owner did not want", wide.contains("menuState.show") || wide.contains("LocalMenuState"))
    }

    @Test
    fun `the detail pane is a useful master-detail and keeps every menu one tap away`() {
        val pane = main("ui/newui/AuraSearchDetailPane.kt")
        // content: tracks of a release / playlist, the whole artist, drilling with a back stack
        for (call in listOf("YouTube.album(", "YouTube.artist(", "YouTube.playlist(", "BackHandler(enabled = stack.size > 1")) {
            assertTrue("the pane lost $call", pane.contains(call))
        }
        // the full existing menus still live inside the pane (hiding is losing), behind «Todas las opciones»
        val options = pane.substringAfter("private fun PaneOptionsPage(").substringBefore("private fun PaneEntity(")
        for (menu in listOf(
            "YouTubeSongMenu", "YouTubeAlbumMenu", "YouTubeArtistMenu", "YouTubePlaylistMenu",
            "SongMenu", "AlbumMenu", "ArtistMenu", "PlaylistMenu",
        )) assertTrue("the options page lost $menu", Regex("(?<![A-Za-z])$menu\\(").containsMatchIn(options))
        // nothing a user owns reaches the shared log, and nothing loads outside the keyed effect
        assertFalse(pane.contains("Timber.") || pane.contains("AppLogger") || pane.contains("Log.i("))
        assertTrue(pane.contains("LaunchedEffect(key, attempt)") && pane.contains("withContext(Dispatchers.IO)"))
        // the artist's name is not drawn twice: the header is the only place that draws a title
        assertEquals(1, Regex("style = AuraType.PlayerTitle").findAll(pane).count())
    }

    @Test
    fun `action tiles wrap to more rows on a narrow half instead of truncating`() {
        assertEquals(5, auraPaneTileColumns(428f, 5))
        assertEquals(3, auraPaneTileColumns(268f, 5))
        assertEquals(2, auraPaneTileColumns(268f, 4))
        assertEquals(4, auraPaneTileColumns(520f, 4))
        assertEquals(3, auraPaneTileColumns(100f, 3))
        assertEquals(1, auraPaneTileColumns(400f, 0))
    }

    @Test
    fun `the artist chips jump to the item where each section starts`() {
        // header + actions + chips = 3 fixed items; a song section of 5 rows, a shelf (1 row), another shelf
        assertEquals(listOf(3, 9, 11), auraPaneSectionStarts(listOf(5, 1, 1), 3))
        assertEquals(emptyList<Int>(), auraPaneSectionStarts(emptyList(), 3))
    }

    @Test
    fun `tapping a track queues the playable ones and lands on the tapped one`() {
        val unavailable = setOf("b", "d")
        val (kept, index) = auraPanePlayable(listOf("a", "b", "c", "d", "e"), 2) { it in unavailable }
        assertEquals(listOf("a", "c", "e"), kept)
        assertEquals("c", kept[index])
        // the tapped row stays even when the source marks it unavailable (the player explains and skips it)
        val (keptTapped, tappedIndex) = auraPanePlayable(listOf("a", "b", "c"), 1) { it == "b" }
        assertEquals(listOf("a", "b", "c"), keptTapped)
        assertEquals(1, tappedIndex)
        assertEquals(0, auraPanePlayable(emptyList<String>(), 3) { false }.second)
    }

    @Test
    fun `the page cache is bounded and expires`() {
        var now = 0L
        val cache = AuraPaneCache(maxEntries = 2, ttlMs = 1_000L, clock = { now })
        cache.put("a", "1")
        cache.put("b", "2")
        assertEquals("1", cache.get("a"))
        cache.put("c", "3")
        assertNull("the least recently used page goes first", cache.get("b"))
        assertEquals("3", cache.get("c"))
        now = 1_001L
        assertNull("a stale page is never served", cache.get("c"))
    }

    @Test
    fun `the three screens split on the room they really have and keep their phone path`() {
        val results = main("ui/newui/AuraSearchResultScreen.kt")
        assertTrue(results.contains("auraSearchSplitFits(maxWidth.value, maxHeight.value)"))
        assertTrue(results.contains("AuraSearchSplit("))
        assertTrue("single-filter grids", results.contains("LazyVerticalGrid(") && results.contains("auraResultFilterIsGrid(searchFilter)"))
        assertTrue("infinite scroll on the grid too", results.contains("gridState.layoutInfo.visibleItemsInfo"))
        // phone: tap still goes through the unchanged handlers
        assertTrue(results.contains("onClick = { onItemClick(item) }") && results.contains("onMenuClick = { openMenu(item) }"))

        val suggestions = main("ui/newui/AuraSearchSuggestions.kt")
        assertEquals("online panel and library panel", 2, Regex("auraSearchSplitFits\\(maxWidth\\.value, maxHeight\\.value\\)").findAll(suggestions).count())
        assertTrue(suggestions.contains("AuraSearchDetailPane("))
        assertTrue("every control of the phone row survives", suggestions.contains("openMenu(item)") && suggestions.contains("SongMenu("))

        val quick = main("ui/newui/AuraPlayerQuickSearch.kt")
        assertTrue(quick.contains("val wide = rememberAuraWideWindows()"))
        assertTrue(quick.contains("AuraSearchDetailPane("))
        assertTrue("the phone still opens the floating song menu", quick.contains("if (wideMode) pickInDetail else openSongActions") || quick.contains("openSongActions(song)"))
        assertTrue("offline search survived", quick.contains("AuraOfflineSearchResults("))
        assertTrue("history and voice survived", quick.contains("AuraSearchHistoryHeader(onClearAll = onClearHistory)") && quick.contains("rememberAuraVoiceSearch("))
    }

    @Test
    fun `the quick search card grows only in a wide window`() {
        val host = main("ui/component/BottomSheetPage.kt").substringAfter("private fun AuraSpotlightHost(")
        assertTrue(host.contains("val wideSpotlight = iad1tya.echo.music.ui.newui.rememberAuraWideWindows()"))
        assertTrue(host.contains("AuraSpotlightWideMaxWidthDp.dp else 640.dp"))
        assertTrue("the phone keeps its 72 % cap", host.contains("else 0.72f"))
        assertTrue(AuraSpotlightWideMaxWidthDp > 640)
    }
}
