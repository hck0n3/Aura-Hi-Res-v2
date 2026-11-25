package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.newui.auraLibraryCellDp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 875 (Biblioteca in columns) and 876 (album / playlist / artist / collection detail in two columns), plan
 * phase 1 item 4. There is no Android SDK in the unit job, so the wiring is pinned on the source text; the decisions
 * themselves are pure and tested in `ui/newui/AuraLibraryDetailLayoutTest.kt`.
 */
class LibraryDetailColumnsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    // ── 875 · one width reading and one threshold for the Biblioteca cells and the picker rows ─────────────────────────

    @Test
    fun `the library cells and the picker rows read the layer's container width and its medium line`() {
        val cells = main("ui/newui/AuraLibraryViewType.kt")
        assertFalse("la celda ya no cae a Configuration", cells.contains("configWidthDp"))
        assertTrue(cells.contains("?: auraWindowInfo().widthDp"))
        assertTrue(cells.contains("if (windowWidthDp < AURA_MEDIUM_MIN_WIDTH_DP) return if (big) 150f else 104f"))

        val sizing = main("ui/component/ListItemSizing.kt")
        assertFalse("la fila amplia ya no mide Configuration", sizing.contains("configWidthDp"))
        assertTrue(sizing.contains("const val ROOMY_LIST_WIDE_DP = iad1tya.echo.music.ui.newui.AURA_MEDIUM_MIN_WIDTH_DP"))
        assertTrue(sizing.contains("auraWindowInfo().widthDp >= ROOMY_LIST_WIDE_DP"))
    }

    @Test
    fun `the cell sizes keep their old lines exactly - a phone and a folded foldable are untouched`() {
        assertEquals(150f, auraLibraryCellDp(599.9f, true), 0f)
        assertEquals(104f, auraLibraryCellDp(599.9f, false), 0f)
        assertEquals(176f, auraLibraryCellDp(600f, true), 0f)
        assertEquals(132f, auraLibraryCellDp(600f, false), 0f)
        // The owner's Z Fold7 cover screen, ~360-410 dp.
        for (w in listOf(360f, 384f, 411f)) {
            assertEquals(150f, auraLibraryCellDp(w, true), 0f)
            assertEquals(104f, auraLibraryCellDp(w, false), 0f)
        }
    }

    // ── 875 · the Biblioteca list view in columns ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the four library tabs lay their list view out in the layer's column count and keep the user's view type`() {
        val tabs = main("ui/newui/AuraLibraryTabs.kt")
        assertEquals("las cuatro pestañas cuentan columnas", 4, Regex("""val listColumns = rememberAuraLibraryListColumns\(\)""").findAll(tabs).count())
        assertEquals(4, Regex("""columns = auraLibraryColumns\(viewType, gridCellSize, listColumns\),""").findAll(tabs).count())
        assertFalse("ninguna pestaña se queda en una columna fija", tabs.contains("columns = auraLibraryColumns(viewType, gridCellSize),"))
        assertEquals("las filas de álbum, artista y lista no invaden la columna vecina", 3, Regex("""bleed = listColumns == 1,""").findAll(tabs).count())
        // The view-type switch, the sort control and the shuffle chip are full-line items, whatever the column count.
        assertTrue(tabs.contains("AuraViewTypeToggle(viewType) { viewType = it }"))
        assertTrue(tabs.contains("item(key = \"aura_songs_sort\", contentType = \"aura_songs_sort\", span = { GridItemSpan(maxLineSpan) })"))
        assertTrue(tabs.contains("item(key = \"aura_songs_shuffle_chip\", contentType = \"aura_songs_shuffle_chip\", span = { GridItemSpan(maxLineSpan) })"))
        // The song rows keep their swipe actions.
        assertTrue(tabs.contains("swipeMediaItem = song.toMediaItem(),"))

        val viewType = main("ui/newui/AuraLibraryViewType.kt")
        assertTrue(viewType.contains("if (viewType == LibraryViewType.LIST) GridCells.Fixed(listColumns) else GridCells.Adaptive(minSize = gridCellSize)"))
        assertTrue(viewType.contains("internal fun rememberAuraLibraryListColumns(): Int = auraLibraryListColumns(auraPaneWidthDp())"))
        assertTrue("una sola columna sangra como siempre", viewType.contains("modifier = if (bleed) modifier.auraBleedHorizontal(AuraSpacing.Gutter) else modifier,"))
    }

    @Test
    fun `the pinned hub lists its pins in columns on a wide pane and keeps one column while reordering`() {
        val tabs = main("ui/newui/AuraLibraryTabs.kt")
        val hub = tabs.substringAfter("fun AuraLibraryHub(").substringBefore("private fun auraIsPlaylistPinned(")
        assertTrue(hub.contains("val listColumns = auraLibraryListColumns(paneWidth.value)"))
        assertTrue(hub.contains("visible.chunked(listColumns)"))
        assertTrue("una fila por columna, la misma fila", hub.contains("line.forEach { r -> pinListRow(r, Modifier.weight(1f)) }"))
        assertTrue("el teléfono conserva su lista", hub.contains("pinListRow(r, Modifier.animateItem())"))
        // Reordering keeps its single draggable column.
        assertTrue(hub.contains("items(editKeys, key = { it }, contentType = { \"aura_hub_pin_edit_row\" })"))
    }

    // ── 876 · album / playlist / artist / collection detail in two columns ────────────────────────────────────────────

    private val detailScreens = listOf(
        "ui/newui/AuraAlbumScreen.kt",
        "ui/newui/AuraArtistScreen.kt",
        "ui/newui/AuraLocalPlaylistScreen.kt",
        "ui/newui/AuraOnlinePlaylistScreen.kt",
        "ui/newui/AuraCollectionScreens.kt",
    )

    @Test
    fun `every detail page goes through the shared two-column body and decides it from the window layer`() {
        for (path in detailScreens) {
            val s = main(path)
            assertTrue("$path: un solo cuerpo compartido", s.contains("AuraDetailColumns(\n"))
            assertTrue("$path: la decisión sale de la capa", s.contains("rememberAuraDetailHeaderWidth()?.takeIf {"))
            assertTrue("$path: la cabecera tiene su propia lista", s.contains("val headerListState = rememberLazyListState()"))
            assertTrue("$path", s.contains("headerListState = headerListState,"))
            assertFalse("$path: ninguna lista propia fuera del cuerpo compartido", Regex("""(?m)^\s+LazyColumn\(""").containsMatchIn(s))
        }
        val helper = main("ui/newui/AuraDetailColumns.kt")
        assertTrue(helper.contains("auraDetailHeaderWidthDp(auraPaneWidthDp(), auraWindowInfo().isCompactHeight)?.dp"))
        assertFalse("sin medir la ventana a mano", helper.contains("screenWidthDp") || helper.contains("LocalConfiguration"))
    }

    @Test
    fun `one column is the page it always was - the same list with the header items then the body`() {
        val helper = main("ui/newui/AuraDetailColumns.kt")
        val single = helper.substringAfter("if (headerWidth == null) {").substringBefore("return\n")
        assertTrue(single.contains("state = listState,"))
        assertTrue(single.contains("modifier = modifier,"))
        assertTrue(single.contains("contentPadding = contentPadding,"))
        assertTrue("primero la cabecera, después el cuerpo", single.indexOf("header()") in 0 until single.indexOf("body()"))
    }

    @Test
    fun `in two columns each column scrolls on its own, knows its width and freezes the shell glass while it scrolls`() {
        val helper = main("ui/newui/AuraDetailColumns.kt")
        val two = helper.substringAfter("return\n")
        assertTrue("fila 196", two.contains("ScrollStateBusReporter { headerListState.isScrollInProgress }"))
        assertTrue(two.contains("state = headerListState,") && two.contains("state = listState,"))
        assertTrue(two.contains("LocalContentPaneWidth provides headerWidth") && two.contains("LocalContentPaneWidth provides bodyWidth"))
        assertTrue("las canciones empiezan bajo la barra flotante", two.contains("max(top, auraStatusBarPadding() + AURA_DETAIL_BODY_TOP_CLEARANCE)"))
        assertFalse("nada por fotograma (regla 7)", two.contains("withFrameNanos") || two.contains("withFrameMillis") || two.contains("while ("))
    }

    @Test
    fun `the wide top and the phone hero survive - the header column uses the phone header and its own parallax`() {
        val album = main("ui/newui/AuraAlbumScreen.kt")
        assertTrue(album.contains("if (wideTop && headerWidth == null) {"))
        assertTrue(album.contains("listState = heroListState,"))
        assertTrue(album.contains("AuraWideTop(") && album.contains("AuraAlbumHero("))

        val artist = main("ui/newui/AuraArtistScreen.kt")
        // Row 497's own line is untouched; the two-column page simply does not take the wide top.
        assertTrue(artist.contains("val wideTop = maxWidth >= AURA_WIDE_HERO_MIN_WIDTH"))
        assertTrue(artist.contains("if (wideTop && headerWidth == null) {"))
        assertTrue(artist.contains("listState = heroListState,"))

        for (path in listOf("ui/newui/AuraLocalPlaylistScreen.kt", "ui/newui/AuraCollectionScreens.kt")) {
            val s = main(path)
            assertTrue("$path: el héroe sigue a su propia lista", s.contains("listState = heroListState,"))
            assertTrue("$path: el desplazador rápido cuenta lo que queda encima", s.contains("headerItems = if (headerWidth != null) 1 else 2,"))
        }
    }

    @Test
    fun `the header column never re-blurs a playing canvas or artist video - rule 7`() {
        val album = main("ui/newui/AuraAlbumScreen.kt")
        assertTrue(album.contains("} else if (!blurMovingMedia && canvasEnabled && (canvasPrimaryUrl != null || canvasFallbackUrl != null)) {"))
        val artist = main("ui/newui/AuraArtistScreen.kt")
        assertTrue(artist.contains("} else if (!blurMovingMedia && backgroundVideoUrl != null && showBackgroundVideo) {"))
        for ((path, s) in listOf("AuraAlbumScreen.kt" to album, "AuraArtistScreen.kt" to artist)) {
            assertTrue("$path: el móvil conserva su cristal", s.contains("blurMovingMedia: Boolean = true,"))
            assertTrue("$path: solo la columna de cabecera lo apaga", s.contains("blurMovingMedia = headerWidth == null,"))
            // The blur band stays the lean one (LeanModeGuardTest) for every other case.
            assertTrue("$path", s.contains("Modifier.hazeEffect(heroHaze) {"))
        }
    }

    @Test
    fun `the two-column page needs the loaded content - loading, error and search keep one column`() {
        assertTrue(main("ui/newui/AuraAlbumScreen.kt").contains("rememberAuraDetailHeaderWidth()?.takeIf { album != null && album.songs.isNotEmpty() }"))
        assertTrue(main("ui/newui/AuraArtistScreen.kt").contains("rememberAuraDetailHeaderWidth()?.takeIf { isKnownArtist || showLocal }"))
        assertTrue(main("ui/newui/AuraOnlinePlaylistScreen.kt").contains("rememberAuraDetailHeaderWidth()?.takeIf { currentPlaylist != null && !isSearching }"))
        assertTrue(main("ui/newui/AuraCollectionScreens.kt").contains("rememberAuraDetailHeaderWidth()?.takeIf { songs?.isNotEmpty() == true && !isSearching }"))
        val local = main("ui/newui/AuraLocalPlaylistScreen.kt")
        assertTrue(local.contains("!isSearching && playlist?.let { it.songCount != 0 || it.playlist.remoteSongCount != 0 } == true"))
        // Drag-reorder stays on the track list's state.
        assertTrue(local.contains("rememberReorderableLazyListState(\n        lazyListState = lazyListState,"))
    }

    @Test
    fun `the column bounds are the phone widths the app already uses`() {
        assertTrue(main("ui/newui/AuraWideChrome.kt").contains("internal val AURA_HOME_REFERENCE_MAX_WIDTH = 430.dp"))
        assertTrue(main("ui/newui/AuraContent.kt").contains("internal val AURA_SONG_PAGE_MIN_WIDTH = 400.dp"))
    }
}
