package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Registry row 875 (plan, phase 1 item 4): the Biblioteca list view in columns. Pure decisions, no Android: the pane width
 * in, the column count out. A compact window (the Fold7's cover screen, any phone) must keep its single column.
 */
class AuraLibraryDetailLayoutTest {

    @Test
    fun `a compact window keeps one column of rows`() {
        for (w in listOf(0f, 320f, 360f, 384f, 411f, 480f, 599.9f)) {
            assertEquals("$w", 1, auraLibraryListColumns(w))
        }
    }

    @Test
    fun `a medium pane gets a second column only where two 400 dp columns fit`() {
        assertEquals(1, auraLibraryListColumns(600f))
        assertEquals(1, auraLibraryListColumns(750f))
        assertEquals(1, auraLibraryListColumns(799.9f))
        assertEquals(2, auraLibraryListColumns(800f))
        assertEquals(2, auraLibraryListColumns(839.9f))
    }

    @Test
    fun `an expanded pane gets two or three columns and never more`() {
        assertEquals(2, auraLibraryListColumns(840f))
        assertEquals(2, auraLibraryListColumns(1199f))
        assertEquals(3, auraLibraryListColumns(1200f))
        assertEquals(3, auraLibraryListColumns(1480f))
        assertEquals(AURA_LIBRARY_LIST_MAX_COLUMNS, auraLibraryListColumns(4000f))
    }

    @Test
    fun `every column of rows is at least 400 dp wide`() {
        var w = 600f
        while (w <= 3000f) {
            val columns = auraLibraryListColumns(w)
            if (columns > 1) assertEquals("$w", true, w / columns >= AURA_LIBRARY_LIST_MIN_COLUMN_DP)
            w += 7f
        }
    }
}
