package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.ui.newui.CoverColors.Kind
import iad1tya.echo.music.ui.newui.CoverColors.Swatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-09: *"cuando las portadas son negras y otros colores omite el color negro… que los mezcle
 * bien"*, *"los colores blancos los omite también"*, *"que sea más vasto, porque siento que es muy
 * repetitivo"*. Pure ARGB maths — no Bitmap, no Palette, no Compose.
 */
class AuraCoverMixTest {

    private val black = 0xFF0A0A0A.toInt()
    private val red = 0xFFD32F2F.toInt()
    private val gold = 0xFFD4A537.toInt()
    private val white = 0xFFF5F5F5.toInt()
    private val grey = 0xFF7A7A7A.toInt()
    private val blue = 0xFF1E5BD8.toInt()
    private val yellow = 0xFFF2C21B.toInt()

    private fun mix(vararg swatches: Swatch) = AuraCoverMix.resolve(CoverColors.pick(swatches.toList())!!)

    private fun alpha(argb: Int) = ((argb ushr 24) and 0xFF) / 255f

    private fun isNeutral(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b) <= 2
    }

    private fun hueGap(a: Int, b: Int): Double {
        val d = Math.abs(Oklab.hue(a) - Oklab.hue(b)) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    @Test
    fun `black, red and gold - the black is the ground and a dark lobe, red and gold are lobes`() {
        val m = mix(Swatch(black, 6000), Swatch(red, 2500), Swatch(gold, 1000))
        // The black dominates the cover: a near-black ground (the shipped deep sits at L 0.144).
        assertTrue("ground L ${Oklab.lightness(m.ground)}", Oklab.lightness(m.ground) <= 0.17)
        assertTrue(Oklab.lightness(m.groundBottom) <= 0.17)
        // …and it also deepens the middle of the screen.
        assertEquals(Kind.DARK, m.lobeKinds[2])
        assertTrue(Oklab.lightness(m.lobes[2]) < 0.12)
        // Red and gold are both on screen, in their own hues.
        val coloured = m.lobes.filterIndexed { i, _ -> m.lobeKinds[i] == Kind.CHROMA }
        assertTrue(coloured.any { hueGap(it, red) < 12.0 })
        assertTrue(coloured.any { hueGap(it, gold) < 12.0 })
        assertTrue(hueGap(m.seed, red) < 12.0)
    }

    @Test
    fun `a black and white cover is deep charcoal with soft grey light, never a muddy grey`() {
        val m = mix(Swatch(white, 2000), Swatch(grey, 5000), Swatch(black, 3000))
        assertTrue(isNeutral(m.ground) && isNeutral(m.groundBottom))
        val l = Oklab.lightness(m.ground)
        assertTrue("charcoal, L $l", l in 0.14..0.23)
        // Light lobes: neutral, light, soft (no flare); and the black as a dark lobe.
        m.lobes.forEachIndexed { i, lobe ->
            assertTrue(isNeutral(lobe))
            when (m.lobeKinds[i]) {
                Kind.LIGHT -> {
                    assertTrue(Oklab.lightness(lobe) in AuraCoverMix.LIGHT_L_MIN..AuraCoverMix.LIGHT_L_MAX)
                    assertTrue("soft light, alpha ${alpha(lobe)}", alpha(lobe) <= 0.31f)
                }
                Kind.DARK -> assertTrue(Oklab.lightness(lobe) < 0.12)
                Kind.CHROMA -> throw AssertionError("a black-and-white cover has no hue to show")
            }
        }
        assertEquals(Kind.DARK, m.lobeKinds[2])
    }

    @Test
    fun `a white cover with a blue title keeps a soft white light and the blue`() {
        val m = mix(Swatch(white, 7000), Swatch(blue, 3000))
        assertTrue(m.lobeKinds.contains(Kind.LIGHT))
        assertTrue(m.lobeKinds.contains(Kind.CHROMA))
        assertEquals(Kind.CHROMA, m.lobeKinds[0])
        assertTrue(hueGap(m.lobes[0], blue) < 12.0)
        m.lobes.filterIndexed { i, _ -> m.lobeKinds[i] == Kind.LIGHT }.forEach {
            assertTrue(Oklab.lightness(it) <= AuraCoverMix.LIGHT_L_MAX + 1e-3)
            assertTrue(alpha(it) <= 0.31f)
        }
        // A dark ground all the same: light text must stay readable.
        assertTrue(Oklab.lightness(m.ground) <= AuraCoverMix.GROUND_L + 0.01)
        assertTrue(Oklab.lightness(m.groundBottom) <= AuraCoverMix.GROUND_L + 0.01)
    }

    @Test
    fun `a white and black cover is a charcoal ground with soft white light`() {
        val m = mix(Swatch(white, 5000), Swatch(black, 5000))
        assertTrue(isNeutral(m.ground))
        assertTrue(Oklab.lightness(m.ground) < AuraCoverMix.GROUND_L)
        assertTrue(m.lobeKinds.count { it == Kind.LIGHT } >= 2)
        assertEquals(Kind.DARK, m.lobeKinds[2])
    }

    @Test
    fun `an all-black cover still has light - a soft grey, quieter than real colours`() {
        val m = mix(Swatch(black, 9000), Swatch(0xFF1C1C1C.toInt(), 1000))
        assertTrue(Oklab.lightness(m.ground) <= 0.16)
        m.lobes.forEachIndexed { i, lobe ->
            if (m.lobeKinds[i] == Kind.LIGHT) assertTrue(alpha(lobe) < 0.25f)
        }
    }

    /** "muy repetitivo": every slot shows a different colour of a colourful cover. */
    @Test
    fun `a colourful cover fills all five lobes with five different colours`() {
        val m = mix(
            Swatch(blue, 3000), Swatch(yellow, 2500), Swatch(red, 2000),
            Swatch(0xFF2FA84F.toInt(), 1500), Swatch(0xFF8E44AD.toInt(), 1000),
        )
        assertEquals(AuraCoverMix.SLOT_COUNT, m.lobes.size)
        assertEquals(5, m.lobes.map { it and 0x00FFFFFF }.toSet().size)
        assertEquals(5, m.spectrum.size)
        // Two-tone ground: main colour on top, the second below, at the same perceptual lightness.
        assertTrue(m.ground != m.groundBottom)
        assertEquals(Oklab.lightness(m.ground), Oklab.lightness(m.groundBottom), 0.01)
    }

    @Test
    fun `lobes have the same visual weight on every hue`() {
        val lightness = (0 until 360 step 10).map { hue ->
            val argb = java.awt.Color.HSBtoRGB(hue / 360f, 0.9f, 0.9f)
            Oklab.lightness(AuraCoverMix.chromaLobe(argb))
        }
        lightness.forEach { assertTrue("L $it", it in (AuraCoverMix.CHROMA_L_MIN - 1e-3)..(AuraCoverMix.CHROMA_L_MAX + 1e-3)) }
        // The old HSV clamp let a yellow sit ~0.45 L above a blue; the band keeps them within 0.22.
        assertTrue(lightness.max() - lightness.min() <= 0.221)
    }

    @Test
    fun `dark colours of the cover are not forced bright as lobes, nor lost`() {
        val navy = 0xFF0A1A40.toInt()
        val lobe = AuraCoverMix.chromaLobe(navy)
        // Visible on the ground (floor of the band)…
        assertEquals(AuraCoverMix.CHROMA_L_MIN, Oklab.lightness(lobe), 0.01)
        // …and still navy: same hue.
        assertTrue(hueGap(lobe, navy) < 10.0)
        // A navy-dominated cover paints a deep navy ground (its black), not a generic one.
        val m = mix(Swatch(navy, 8000), Swatch(0xFFC9B48A.toInt(), 2000))
        assertTrue(Oklab.lightness(m.ground) <= 0.17)
        assertTrue(Oklab.chroma(m.ground) > 0.01)
        assertTrue(hueGap(m.ground, navy) < 25.0)
    }

    @Test
    fun `the ground follows how much of the cover is black`() {
        val little = mix(Swatch(red, 9000), Swatch(black, 1000))
        val half = mix(Swatch(red, 5000), Swatch(black, 5000))
        val most = mix(Swatch(red, 2000), Swatch(black, 8000))
        assertEquals(AuraCoverMix.GROUND_L, Oklab.lightness(little.ground), 0.01)
        assertTrue(Oklab.lightness(half.ground) < Oklab.lightness(little.ground))
        assertTrue(Oklab.lightness(most.ground) < Oklab.lightness(half.ground))
        assertEquals(AuraCoverMix.DARK_GROUND_L, Oklab.lightness(most.ground), 0.01)
    }

    @Test
    fun `oklab round-trips and greys stay grey`() {
        listOf(red, gold, blue, yellow, white, black, grey).forEach { argb ->
            val lab = Oklab.fromArgb(argb)
            val back = Oklab.toArgb(lab[0], lab[1], lab[2])
            for (shift in intArrayOf(16, 8, 0)) {
                val d = Math.abs(((argb shr shift) and 0xFF) - ((back shr shift) and 0xFF))
                assertTrue("channel off by $d", d <= 1)
            }
        }
        assertTrue(isNeutral(Oklab.toArgb(0.5, 0.0, 0.0)))
        // Out of gamut: chroma gives way, lightness stays.
        val clipped = Oklab.fromLch(0.9, 0.4, 264.0)
        assertEquals(0.9, Oklab.lightness(clipped), 0.01)
    }
}
