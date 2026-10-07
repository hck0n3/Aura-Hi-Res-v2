package iad1tya.echo.music.ui.newui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import iad1tya.echo.music.ui.component.ColorPickerConversions
import iad1tya.echo.music.ui.theme.contrastRatio
import iad1tya.echo.music.ui.theme.ensureLegibleOn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan A4 (row 338): the opt-in light variant of Aura stays legible, and OFF changes nothing. */
class AuraLightModeTest {

    @After
    fun tearDown() = AuraPalette.reset()

    private fun textSteps() = listOf(
        "muted" to AuraPalette.OnGroundMuted,
        "faint" to AuraPalette.OnGroundFaint,
        "ghost" to AuraPalette.OnGroundGhost,
        "nav" to AuraPalette.NavInactive,
    )

    @Test
    fun darkIsTheDefaultAndUntouched() {
        AuraPalette.reset()
        assertFalse(AuraPalette.isLight)
        assertEquals(Color(0xFF060A12), AuraPalette.Ground)
        assertEquals(Color.White.copy(alpha = 0.07f), AuraPalette.SurfaceFill)
        assertEquals(Color.White.copy(alpha = 0.10f), AuraPalette.SurfaceLine)
    }

    @Test
    fun brandLightGroundKeepsEveryTextStepAboveAa() {
        AuraPalette.apply(AuraAccent.Brand, pureBlack = false, coverCorners = AuraCoverCorners.Render, light = true)
        assertTrue(AuraPalette.isLight)
        val ground = AuraPalette.Ground
        assertTrue("light ground must be light", contrastRatio(Color.Black, ground) > 15f)
        textSteps().forEach { (name, ink) ->
            val ratio = contrastRatio(ink.compositeOver(ground), ground)
            assertTrue("$name is $ratio:1 on the light ground", ratio >= 4.5f)
        }
        assertTrue(contrastRatio(AuraPalette.OnGround, ground) >= 7f)
    }

    @Test
    fun everyCoverTintedLightGroundStaysLegible() {
        for (hue in 0 until 360 step 15) {
            val seed = ColorPickerConversions.hsvToColor(hue.toFloat(), 0.8f, 0.8f)
            val ground = auraArtworkGroundLight(seed)
            val ink = ensureLegibleOn(ColorPickerConversions.hsvToColor(hue.toFloat(), 0.40f, 0.20f), ground, 4.5f)
            AuraPalette.apply(
                AuraAccent.from(seed, ground), pureBlack = false, coverCorners = AuraCoverCorners.Render,
                artworkInk = ink, artworkGround = ground, light = true,
            )
            textSteps().forEach { (name, step) ->
                val ratio = contrastRatio(step.compositeOver(ground), ground)
                assertTrue("hue $hue: $name is $ratio:1", ratio >= 4.5f)
            }
            // The accents are walked darker on a light ground: the play button ink flips to white.
            assertTrue(contrastRatio(AuraPalette.Teal, ground) >= 4.5f)
        }
    }

    @Test
    fun lightFilmsAreBlackNotWhite() {
        AuraPalette.apply(AuraAccent.Brand, pureBlack = false, coverCorners = AuraCoverCorners.Render, light = true)
        assertEquals(Color.Black.copy(alpha = 0.10f), AuraPalette.SurfaceLine)
        assertEquals(Color.White, AuraPalette.OnAccent)
    }
}
