package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner round 2026-10-15 — the wide-window rules, pinned.
 *
 * "En el modo plegable no me gusta la interfaz, no se parece a la normal" had three measurable causes: the
 * "see all" grids stayed at two columns (300 dp tiles on an unfolded foldable), the album / artist / playlist
 * hero only left the full-width square at 840 dp (no foldable reaches it) and the Inicio card scale followed
 * the window width. Each is a number a refactor can silently put back.
 */
class AuraWideChromeTest {

    @Test
    fun `a phone keeps its two columns, wide panes get more`() {
        assertEquals(2, auraSeeAllColumns(360.dp))
        assertEquals(2, auraSeeAllColumns(411.dp))
        assertEquals(2, auraSeeAllColumns(430.dp))
        // Unfolded Z Fold content pane (725 dp window minus the 80 dp rail).
        assertEquals(3, auraSeeAllColumns(645.dp))
        assertTrue(auraSeeAllColumns(1000.dp) >= 4)
    }

    @Test
    fun `video lists are one wide column on a phone and never fewer than one`() {
        assertEquals(1, auraSeeAllColumns(360.dp, videoHeavy = true))
        assertEquals(1, auraSeeAllColumns(430.dp, videoHeavy = true))
        assertEquals(1, auraSeeAllColumns(10.dp, videoHeavy = true))
        assertTrue(auraSeeAllColumns(1000.dp, videoHeavy = true) >= 2)
    }

    @Test
    fun `the wide hero starts below any foldable and above any phone in portrait`() {
        assertTrue("a phone in portrait (<= 430 dp) keeps the square", AURA_WIDE_HERO_MIN_WIDTH > 430.dp)
        assertTrue("an unfolded foldable's pane (~645 dp) takes the band", AURA_WIDE_HERO_MIN_WIDTH <= 645.dp)
    }

    @Test
    fun `the Inicio card scale stops following the window at a large phone`() {
        assertEquals(430.dp, AURA_HOME_REFERENCE_MAX_WIDTH)
    }

    @Test
    fun `the player pane is phone sized on a foldable and capped on a tablet`() {
        assertEquals(340.dp, auraWidePlayerPaneWidth(725.dp)) // 46 % = 333 -> floor 340
        assertEquals(520.dp, auraWidePlayerPaneWidth(1600.dp))
        assertTrue(auraWidePlayerPaneWidth(1000.dp) in 340.dp..520.dp)
    }

    @Test
    fun `the nav capsule shares the row with the mini player and is wide when alone`() {
        val window = 829.dp
        val pane = auraWidePlayerPaneWidth(window)
        val withPlayer = auraWideNavWidth(window, playerVisible = true)
        // capsule + gap + pane + both outer margins fill the row exactly
        assertEquals(
            window.value,
            (withPlayer + AuraWideNavGap + pane + AuraFloatingInset * 2).value,
            0.01f,
        )
        assertEquals(720.dp, auraWideNavWidth(1200.dp, playerVisible = false))
        assertTrue(auraWideNavWidth(829.dp, playerVisible = false) > withPlayer)
    }

    @Test
    fun `the two cards share one centred gap and never overlap or leave the window`() {
        // 1800 x 2000 px window, a 828 px pane, 26 px gap (10 dp at ~2.6x).
        val (left, right) = auraSplitCardRects(1800f, 2000f, 828f, 26f, top = 140f, bottom = 8f)
        assertEquals(26f, left.left, 0f)
        assertEquals(1800f - 26f, right.right, 0f)
        // The gap between the cards is the whole gap, centred on the pane boundary (x = 972).
        assertEquals(26f, right.left - left.right, 0f)
        assertEquals(972f, (left.right + right.left) / 2f, 0f)
        // Same vertical extent, inside the window.
        assertEquals(left.top, right.top, 0f)
        assertEquals(left.bottom, right.bottom, 0f)
        assertTrue(left.top >= 0f && left.bottom <= 2000f)
        assertTrue(left.right < right.left)
    }

    @Test
    fun `the header puts the buttons on their own line only when the title does not fit beside them`() {
        // A 411 dp phone at 2.6x: 1069 px wide, 40 dp gutters -> ~ 965 px for the row; five 48 dp buttons ~ 650 px.
        val gap = 21
        // «Aura Hi-Res» at 29 sp needs ~ 440 px: it did NOT fit beside the buttons (and ended in «…»).
        assertTrue(!auraHeaderFitsOneRow(titleWidth = 440, actionsWidth = 650, availableWidth = 965, gap = gap))
        // The same title on a Z Fold 7 (750 dp): plenty of room, the one-row header is unchanged.
        assertTrue(auraHeaderFitsOneRow(titleWidth = 440, actionsWidth = 650, availableWidth = 1700, gap = gap))
        // A short title beside fewer buttons still shares the row.
        assertTrue(auraHeaderFitsOneRow(titleWidth = 200, actionsWidth = 260, availableWidth = 965, gap = gap))
        // Exactly full is fine; one pixel over is not.
        assertTrue(auraHeaderFitsOneRow(titleWidth = 100, actionsWidth = 100, availableWidth = 221, gap = 21))
        assertTrue(!auraHeaderFitsOneRow(titleWidth = 100, actionsWidth = 100, availableWidth = 220, gap = 21))
    }

    @Test
    fun `the cover frame line softens with the cover size and keeps the full line on thumbnails`() {
        assertEquals(1f, auraArtworkEdgeStrength(48f), 1e-6f)
        assertEquals(1f, auraArtworkEdgeStrength(120f), 1e-6f)
        assertEquals(ARTWORK_EDGE_MIN_STRENGTH, auraArtworkEdgeStrength(260f), 1e-6f)
        val mid = auraArtworkEdgeStrength(180f)
        assertTrue(mid < 1f && mid > ARTWORK_EDGE_MIN_STRENGTH)
        // Monotonic: a bigger cover never gets a stronger line.
        var previous = 1f
        for (dp in 100..300 step 10) {
            val v = auraArtworkEdgeStrength(dp.toFloat())
            assertTrue(v <= previous + 1e-6f)
            previous = v
        }
    }
}
