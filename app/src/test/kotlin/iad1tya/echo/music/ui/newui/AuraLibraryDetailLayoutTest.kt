package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 875 and 876 (plan, phase 1 item 4): the Biblioteca list view in columns and the detail pages in two
 * columns. Pure decisions, no Android: the pane width in, the layout out. A compact window (the Fold7's cover screen, any
 * phone) must keep its single column.
 */
class AuraLibraryDetailLayoutTest {

    @Test
    fun `a compact window keeps one column of rows`() {
        for (w in listOf(0f, 320f, 360f, 384f, 411f, 480f, 599.9f)) {
            assertEquals("$w", 1, auraLibraryListColumns(w, compactHeight = false))
        }
    }

    @Test
    fun `a phone on its side keeps one column of rows`() {
        // Wide but short (compact height): its width class is medium or expanded, the list stays the phone's.
        for (w in listOf(640f, 800f, 851f, 915f, 1000f, 1200f)) {
            assertEquals("$w", 1, auraLibraryListColumns(w, compactHeight = true))
        }
    }

    @Test
    fun `a medium pane gets a second column only where two 400 dp columns fit`() {
        assertEquals(1, auraLibraryListColumns(600f, compactHeight = false))
        assertEquals(1, auraLibraryListColumns(750f, compactHeight = false))
        assertEquals(1, auraLibraryListColumns(799.9f, compactHeight = false))
        assertEquals(2, auraLibraryListColumns(800f, compactHeight = false))
        assertEquals(2, auraLibraryListColumns(839.9f, compactHeight = false))
    }

    @Test
    fun `an expanded pane gets two or three columns and never more`() {
        assertEquals(2, auraLibraryListColumns(840f, compactHeight = false))
        assertEquals(2, auraLibraryListColumns(1199f, compactHeight = false))
        assertEquals(3, auraLibraryListColumns(1200f, compactHeight = false))
        assertEquals(3, auraLibraryListColumns(1480f, compactHeight = false))
        assertEquals(AURA_LIBRARY_LIST_MAX_COLUMNS, auraLibraryListColumns(4000f, compactHeight = false))
    }

    @Test
    fun `every column of rows is at least 400 dp wide`() {
        var w = 600f
        while (w <= 3000f) {
            val columns = auraLibraryListColumns(w, compactHeight = false)
            if (columns > 1) assertEquals("$w", true, w / columns >= AURA_LIBRARY_LIST_MIN_COLUMN_DP)
            w += 7f
        }
    }

    // ── 876 · detail pages in two columns ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a compact window or a compact height keeps the one-column detail page`() {
        for (w in listOf(320f, 360f, 384f, 411f, 599.9f)) {
            assertNull("$w", auraDetailHeaderWidthDp(w, compactHeight = false))
        }
        // A phone on its side is wide but short: it keeps its page (and its wide top from 560 dp, as before).
        for (w in listOf(720f, 840f, 915f, 1200f)) {
            assertNull("$w", auraDetailHeaderWidthDp(w, compactHeight = true))
        }
    }

    @Test
    fun `a medium pane gets two columns only where a 320 dp header and 400 dp of tracks fit`() {
        assertNull(auraDetailHeaderWidthDp(600f, false))
        assertNull(auraDetailHeaderWidthDp(673f, false))
        assertNull(auraDetailHeaderWidthDp(719.9f, false))
        assertEquals(320f, auraDetailHeaderWidthDp(720f, false)!!, 0f)
        assertEquals(320f, auraDetailHeaderWidthDp(750f, false)!!, 0f)
        assertEquals(332f, auraDetailHeaderWidthDp(830f, false)!!, 1e-3f)
    }

    @Test
    fun `an expanded pane gives the header 40 percent, between the narrowest and the widest phone`() {
        assertEquals(336f, auraDetailHeaderWidthDp(840f, false)!!, 1e-3f)
        assertEquals(400f, auraDetailHeaderWidthDp(1000f, false)!!, 1e-3f)
        assertEquals(AURA_DETAIL_HEADER_MAX_DP, auraDetailHeaderWidthDp(1480f, false)!!, 0f)
        assertEquals(AURA_DETAIL_HEADER_MAX_DP, auraDetailHeaderWidthDp(2560f, false)!!, 0f)
    }

    @Test
    fun `whenever there are two columns both have their room`() {
        var w = 0f
        while (w <= 3000f) {
            val header = auraDetailHeaderWidthDp(w, false)
            if (header != null) {
                assertTrue("$w", header >= AURA_DETAIL_HEADER_MIN_DP && header <= AURA_DETAIL_HEADER_MAX_DP)
                assertTrue("$w", w - header >= AURA_DETAIL_BODY_MIN_DP)
                assertTrue("$w", w >= AURA_MEDIUM_MIN_WIDTH_DP)
            }
            w += 3f
        }
    }

    @Test
    fun `the header column bounds are the phone widths the app already uses`() {
        // AURA_HOME_REFERENCE_MAX_WIDTH (430 dp, the widest phone) and AURA_SONG_PAGE_MIN_WIDTH (400 dp, one song column).
        assertEquals(430f, AURA_DETAIL_HEADER_MAX_DP, 0f)
        assertEquals(400f, AURA_DETAIL_BODY_MIN_DP, 0f)
        assertEquals(0.4f, AURA_DETAIL_HEADER_FRACTION, 0f)
    }
}
