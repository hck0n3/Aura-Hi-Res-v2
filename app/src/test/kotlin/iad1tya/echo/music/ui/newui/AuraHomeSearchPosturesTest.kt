package iad1tya.echo.music.ui.newui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 878 (plan, phase 1 item 4: «posturas del plegable»). Where a half-opened fold crosses a screen's box and
 * how the search split sits on it. Pure decisions + a source guard for the wiring.
 */
class AuraHomeSearchPosturesTest {
    private val density = 2.625f

    /** A Z Fold-like inner screen: 2184 px wide window, the fold a zero-width line in the middle. */
    private fun fold(isFlat: Boolean = false, isVertical: Boolean = true, left: Float = 1092f, right: Float = 1092f) =
        AuraHinge(
            left = left, top = 0f, right = right, bottom = 1968f,
            isFlat = isFlat, isVertical = isVertical, isSeparating = !isFlat, isOccluding = false,
        )

    @Test
    fun `in book posture the hinge band is measured from the box's own left edge`() {
        val band = auraBookHingeBand(AuraPosture.Book, listOf(fold()), boxLeftPx = 0f, boxWidthPx = 2184f, density = density)
        assertEquals(1092f / density, band!!.startDp, 0.01f)
        assertEquals(band.startDp, band.endDp, 0.0f)
        // The same window, but the screen starts 105 px in (the camera cut-out padded on the left).
        val shifted = auraBookHingeBand(AuraPosture.Book, listOf(fold()), boxLeftPx = 105f, boxWidthPx = 2079f, density = density)
        assertEquals((1092f - 105f) / density, shifted!!.startDp, 0.01f)
    }

    @Test
    fun `no band when the device is flat, in tabletop, or the hinge is outside the box`() {
        assertNull(auraBookHingeBand(AuraPosture.Flat, listOf(fold(isFlat = true)), 0f, 2184f, density))
        assertNull("tabletop is a horizontal fold", auraBookHingeBand(AuraPosture.Tabletop, listOf(fold(isVertical = false)), 0f, 2184f, density))
        assertNull("no hinge at all (every phone)", auraBookHingeBand(AuraPosture.Book, emptyList(), 0f, 2184f, density))
        assertNull("a box wholly left of the fold", auraBookHingeBand(AuraPosture.Book, listOf(fold()), 0f, 1000f, density))
        assertNull("a box wholly right of the fold", auraBookHingeBand(AuraPosture.Book, listOf(fold()), 1200f, 984f, density))
        assertNull("a flat vertical hinge is not avoided", auraBookHingeBand(AuraPosture.Book, listOf(fold(isFlat = true)), 0f, 2184f, density))
    }

    @Test
    fun `the search split sits on the hinge, keeps an occluding hinge empty and mirrors right to left`() {
        val total = 2184f / density // ~832 dp
        val band = AuraHingeBand(startDp = 416f, endDp = 416f)
        assertEquals(total - 416f, auraSearchHingeDetailWidth(total, band)!!, 0.01f)
        assertEquals("right to left: the detail is the left page", 416f, auraSearchHingeDetailWidth(total, band, detailOnLeft = true)!!, 0.01f)
        val occluding = AuraHingeBand(startDp = 400f, endDp = 432f)
        assertEquals(total - 432f, auraSearchHingeDetailWidth(total, occluding)!!, 0.01f)
    }

    @Test
    fun `the usual split stays when there is no band or a half would be too narrow`() {
        assertNull(auraSearchHingeDetailWidth(832f, null))
        assertNull("list half too narrow", auraSearchHingeDetailWidth(832f, AuraHingeBand(250f, 250f)))
        assertNull("detail half too narrow", auraSearchHingeDetailWidth(832f, AuraHingeBand(600f, 600f)))
        assertTrue("exactly the floor is allowed", auraSearchHingeDetailWidth(600f, AuraHingeBand(300f, 300f)) == 300f)
    }

    @Test
    fun `the split reads its place in the window only in book posture, and never per frame`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val split = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraSearchWide.kt").readText()
            .substringAfter("internal fun AuraSearchSplit(").substringBefore("internal fun AuraSearchDetailCard(")
        assertTrue(split.contains("val book = window.posture == AuraPosture.Book"))
        assertTrue(split.contains("if (book) {\n                    Modifier.onGloballyPositioned"))
        assertTrue("only a changed place is written", split.contains("if (next != box) box = next"))
        assertTrue(split.contains("auraBookHingeBand(window.posture, window.hinges, left, width, density)"))
        assertTrue(split.contains(".width(hingeDetail?.dp ?: detailWidth)"))
        assertFalse(split.contains("withFrameNanos") || split.contains("while ("))
    }
}
