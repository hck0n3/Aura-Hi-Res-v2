package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 522 — owner 2026-10-29 (Galaxy Tab S12 Ultra): «del lado derecho sobra… reordenar el contenido para
 * dispositivos de gran tamaño» and «puedes hacer la portada más grande».
 */
class TabletFillTest {
    private val gap = 11.dp
    private val gutter = 16.dp
    private val card = 140.dp * 1.2f // an album card on the artist page

    @Test
    fun `a phone and a fold keep two rows exactly as before`() {
        assertEquals(2, auraShelfRows(itemCount = 10, availableWidth = 400.dp, cardWidth = card, gap = gap, gutter = gutter))
        assertEquals(2, auraShelfRows(itemCount = 10, availableWidth = 800.dp, cardWidth = card, gap = gap, gutter = gutter))
        // fewer than four items: one row, as always
        assertEquals(1, auraShelfRows(itemCount = 3, availableWidth = 400.dp, cardWidth = card, gap = gap, gutter = gutter))
    }

    @Test
    fun `a tablet pane takes one row when two would leave its right side empty, two when they fill it`() {
        // ~1 600 dp wide: about nine cards across
        assertEquals(1, auraShelfRows(itemCount = 10, availableWidth = 1600.dp, cardWidth = card, gap = gap, gutter = gutter))
        assertEquals(1, auraShelfRows(itemCount = 12, availableWidth = 1600.dp, cardWidth = card, gap = gap, gutter = gutter))
        assertEquals(2, auraShelfRows(itemCount = 20, availableWidth = 1600.dp, cardWidth = card, gap = gap, gutter = gutter))
        // unknown count or width: the old two rows
        assertEquals(2, auraShelfRows(itemCount = null, availableWidth = 1600.dp, cardWidth = card, gap = gap, gutter = gutter))
        assertEquals(2, auraShelfRows(itemCount = 10, availableWidth = 1600.dp, cardWidth = null, gap = gap, gutter = gutter))
    }

    @Test
    fun `song pages sit side by side on a wide pane and stay one page on a phone`() {
        assertEquals(1, auraSongPagesPerView(400.dp, pageCount = 5))
        assertEquals(1, auraSongPagesPerView(700.dp, pageCount = 5))
        assertEquals(2, auraSongPagesPerView(850.dp, pageCount = 5))
        assertEquals(3, auraSongPagesPerView(1600.dp, pageCount = 5))
        // never more than there are pages
        assertEquals(2, auraSongPagesPerView(1600.dp, pageCount = 2))
        assertEquals(1, auraSongPagesPerView(1600.dp, pageCount = 1))
    }

    @Test
    fun `a short shelf grows its cards on a wide pane until the row fills, and never on a phone`() {
        val w = 140.dp
        // five albums on a ~1 300 dp tablet pane: capped at 1.5x
        assertEquals(1.5f, auraWideShelfBoost(5, 1300.dp, w), 0.001f)
        // eight cards almost fill it already: a small growth only
        val eight = auraWideShelfBoost(8, 1300.dp, w)
        assertTrue(eight > 1f && eight < 1.15f)
        // a long shelf, a phone and a fold are untouched
        assertEquals(1f, auraWideShelfBoost(12, 1300.dp, w), 0f)
        assertEquals(1f, auraWideShelfBoost(5, 400.dp, w), 0f)
        assertEquals(1f, auraWideShelfBoost(5, 780.dp, w), 0f)
        assertEquals(1f, auraWideShelfBoost(0, 1300.dp, w), 0f)
    }

    @Test
    fun `the player cover lifts its 420 dp ceiling on a tablet window only`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val s = File(root, "app/src/main/kotlin/com/music/echo/ui/player/Thumbnail.kt").readText()
        assertTrue(s.contains("if (isWideLayout && !isTabletWindow) minOf(WIDE_MAX_THUMBNAIL_SIZE, slotCap) else slotCap"))
        assertTrue(s.contains("const val TABLET_COVER_MIN_WINDOW_DP = 1000"))
    }
}
