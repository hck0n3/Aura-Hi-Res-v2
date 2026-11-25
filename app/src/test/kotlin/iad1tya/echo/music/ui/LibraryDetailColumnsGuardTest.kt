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
}
