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

    /** Row 342: "no quiero que agarre el color más fuerte de la portada". */
    @Test
    fun `a vivid colour does not beat a calmer one that fills more of the cover`() {
        val calmTeal = 0xFF4E8F8A.toInt() // s ~0.45
        val neonPink = 0xFFFF1FA0.toInt() // s ~0.88
        val trio = CoverColors.pick(listOf(Swatch(neonPink, 3000), Swatch(calmTeal, 4000)))!!
        assertEquals(calmTeal, trio.primary)
        assertEquals(neonPink, trio.secondary)
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

    // ── Owner 2026-10-09: "omite el color negro… los colores blancos los omite también" ─────────────

    @Test
    fun `a black cover with red and gold keeps its black as a tone, next to both colours`() {
        val gold = 0xFFD4A537.toInt()
        val trio = CoverColors.pick(listOf(Swatch(black, 6000), Swatch(red, 2500), Swatch(gold, 1000)))!!
        assertEquals(red, trio.primary)
        assertEquals(gold, trio.secondary)
        val dark = trio.tones.single { it.kind == CoverColors.Kind.DARK }
        assertEquals(6000f / 9500f, dark.share, 0.01f)
        assertTrue("the black stays black", Oklab.lightness(dark.rgb) < CoverColors.DARK_L)
        assertEquals(dark.share, trio.darkShare, 0f)
        assertEquals(
            listOf(red, gold),
            trio.tones.filter { it.kind == CoverColors.Kind.CHROMA }.map { it.rgb },
        )
    }

    @Test
    fun `a white cover with a blue title keeps its white as light`() {
        val trio = CoverColors.pick(listOf(Swatch(white, 7000), Swatch(blue, 3000)))!!
        assertEquals(blue, trio.primary)
        val light = trio.tones.single { it.kind == CoverColors.Kind.LIGHT }
        assertEquals(0.7f, light.share, 0.01f)
        assertEquals(0.7f, trio.lightShare, 0.01f)
        assertTrue(Oklab.lightness(light.rgb) > 0.9)
    }

    @Test
    fun `black and white are mixed in Oklab, not dropped, on a black and white cover`() {
        val trio = CoverColors.pick(listOf(Swatch(white, 2000), Swatch(grey, 5000), Swatch(black, 3000)))!!
        assertTrue(!trio.chromatic)
        val light = trio.tones.single { it.kind == CoverColors.Kind.LIGHT }
        val dark = trio.tones.single { it.kind == CoverColors.Kind.DARK }
        assertEquals(0.7f, light.share, 0.01f)
        assertEquals(0.3f, dark.share, 0.01f)
        // The light tone is the population-weighted mix of the white and the grey: between the two.
        val l = Oklab.lightness(light.rgb)
        assertTrue(l > Oklab.lightness(grey) && l < Oklab.lightness(white))
        // Neutral in, neutral out.
        assertEquals((light.rgb shr 16) and 0xFF, light.rgb and 0xFF)
    }

    /** Owner 2026-10-09: "la cantidad de colores… que sea más vasto". */
    @Test
    fun `a colourful cover gives up to five real colours, near-duplicates counted once`() {
        val green = 0xFF2FA84F.toInt()
        val purple = 0xFF8E44AD.toInt()
        val almostBlue = 0xFF2060DA.toInt()
        val trio = CoverColors.pick(
            listOf(
                Swatch(blue, 3000), Swatch(yellow, 2500), Swatch(red, 2000),
                Swatch(almostBlue, 1800), Swatch(green, 1500), Swatch(purple, 1000),
            ),
        )!!
        val hues = trio.tones.filter { it.kind == CoverColors.Kind.CHROMA }.map { it.rgb }
        assertEquals(CoverColors.MAX_CHROMATIC, hues.size)
        assertEquals(listOf(blue, yellow, red), hues.take(3))
        assertTrue(green in hues && purple in hues)
        assertTrue("a near-copy of the blue is not a new colour", almostBlue !in hues)
    }

    @Test
    fun `hue maths`() {
        assertEquals(0f, CoverColors.hsv(0xFFFF0000.toInt())[0], 0.5f)
        assertEquals(120f, CoverColors.hsv(0xFF00FF00.toInt())[0], 0.5f)
        assertEquals(240f, CoverColors.hsv(0xFF0000FF.toInt())[0], 0.5f)
        assertEquals(20f, CoverColors.hueDistance(350f, 10f), 0.001f)
    }
}
