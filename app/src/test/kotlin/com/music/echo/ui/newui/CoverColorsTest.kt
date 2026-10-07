package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.ui.newui.CoverColors.Swatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 341 (owner 2026-10-07: "reflejar el color de las portadas exacto"). */
class CoverColorsTest {

    private val red = 0xFFD32F2F.toInt()
    private val blue = 0xFF1E5BD8.toInt()
    private val yellow = 0xFFF2C21B.toInt()
    private val black = 0xFF0A0A0A.toInt()
    private val white = 0xFFF5F5F5.toInt()
    private val grey = 0xFF7A7A7A.toInt()

    @Test
    fun `the colour that fills the cover wins over a small vivid detail`() {
        // A mostly-blue cover with a little bright yellow: the old "vibrant first" order picked the
        // yellow; the eye names it blue.
        val trio = CoverColors.pick(listOf(Swatch(yellow, 300), Swatch(blue, 6000), Swatch(black, 3000)))!!
        assertEquals(blue, trio.primary)
        assertEquals(yellow, trio.secondary)
    }

    @Test
    fun `a black cover with a red title is red, not black`() {
        val trio = CoverColors.pick(listOf(Swatch(black, 9000), Swatch(red, 600)))!!
        assertEquals(red, trio.primary)
    }

    @Test
    fun `second and third are different colours of the cover, never shades of the first`() {
        val darkerBlue = 0xFF173F96.toInt()
        val trio = CoverColors.pick(
            listOf(Swatch(blue, 5000), Swatch(darkerBlue, 4000), Swatch(red, 800), Swatch(yellow, 700)),
        )!!
        assertEquals(blue, trio.primary)
        assertEquals(setOf(red, yellow), setOf(trio.secondary, trio.tertiary))
        listOf(trio.secondary!!, trio.tertiary!!).forEach {
            assertTrue(CoverColors.hueDistance(CoverColors.hsv(it)[0], CoverColors.hsv(blue)[0]) >= CoverColors.MIN_HUE_GAP)
        }
    }

    @Test
    fun `a speck of noise does not colour the screen`() {
        val trio = CoverColors.pick(listOf(Swatch(blue, 9990), Swatch(red, 10)))!!
        assertEquals(blue, trio.primary)
        assertNull(trio.secondary)
    }

    @Test
    fun `a black and white cover stays grey, never an invented hue`() {
        val trio = CoverColors.pick(listOf(Swatch(white, 2000), Swatch(grey, 5000), Swatch(black, 3000)))!!
        assertEquals(grey, trio.primary)
        assertNull(trio.secondary)
        assertNull(trio.tertiary)
    }

    @Test
    fun `nothing extracted means no trio`() {
        assertNull(CoverColors.pick(emptyList()))
        assertNull(CoverColors.pick(listOf(Swatch(red, 0))))
    }

    @Test
    fun `colours come back opaque`() {
        val trio = CoverColors.pick(listOf(Swatch(red and 0x00FFFFFF, 10)))
        assertNotNull(trio)
        assertEquals(red, trio!!.primary)
    }

    @Test
    fun `hue maths`() {
        assertEquals(0f, CoverColors.hsv(0xFFFF0000.toInt())[0], 0.5f)
        assertEquals(120f, CoverColors.hsv(0xFF00FF00.toInt())[0], 0.5f)
        assertEquals(240f, CoverColors.hsv(0xFF0000FF.toInt())[0], 0.5f)
        assertEquals(20f, CoverColors.hueDistance(350f, 10f), 0.001f)
    }
}
