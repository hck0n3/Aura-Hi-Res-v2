package iad1tya.echo.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las curvas del cristal líquido, fijadas contra la receta de SimpMusic.
 *
 * El dueño pidió el efecto **exacto**, no uno parecido. El dibujo solo se puede juzgar en el teléfono,
 * pero las curvas son aritmética y aquí quedan clavadas: si alguien "mejora" un número, este test dice
 * cuál y contra qué original.
 */
class LiquidGlassMathTest {
    private val tolerance = 0.0001f

    @Test
    fun `the luminance curve keeps its sign and flattens the middle`() {
        // sign(c) * c * c, con c = 2*lum - 1. El cuadrado deja plana la zona media: un fondo normal no
        // debe cambiar el cristal, solo los extremos, que son donde se rompe.
        assertEquals(0f, LiquidGlassMath.luminanceCurve(0.5f), tolerance)
        assertEquals(1f, LiquidGlassMath.luminanceCurve(1f), tolerance)
        assertEquals(-1f, LiquidGlassMath.luminanceCurve(0f), tolerance)
        // 0.75 -> c = 0.5 -> 0.25. Un cuarto del efecto a mitad de camino: eso es "plano en el medio".
        assertEquals(0.25f, LiquidGlassMath.luminanceCurve(0.75f), tolerance)
        assertEquals(-0.25f, LiquidGlassMath.luminanceCurve(0.25f), tolerance)
    }

    @Test
    fun `luminance outside zero to one cannot bend the curve past its ends`() {
        assertEquals(1f, LiquidGlassMath.luminanceCurve(4f), tolerance)
        assertEquals(-1f, LiquidGlassMath.luminanceCurve(-4f), tolerance)
    }

    @Test
    fun `blur doubles on a bright background and thins to a quarter on a dark one`() {
        val base = 8f
        assertEquals(base, LiquidGlassMath.blurRadiusPx(base, 0.5f, 0f), tolerance)
        // SimpMusic: lerp(8dp, 16dp, l) hacia arriba…
        assertEquals(base * 2f, LiquidGlassMath.blurRadiusPx(base, 1f, 0f), tolerance)
        // …y lerp(8dp, 2dp, -l) hacia abajo. Un fondo oscuro no tiene detalle que tapar; desenfocarlo
        // solo gastaría batería.
        assertEquals(base * 0.25f, LiquidGlassMath.blurRadiusPx(base, 0f, 0f), tolerance)
    }

    @Test
    fun `pressing adds blur and nothing ever goes negative`() {
        val pressed = LiquidGlassMath.blurRadiusPx(8f, 0.5f, 4f)
        assertEquals(12f, pressed, tolerance)
        // Un ajuste de desenfoque a 0 con el usuario pulsando no puede dar un radio negativo: eso sería
        // un parámetro inválido para el RenderEffect, no un cristal más plano.
        assertTrue(LiquidGlassMath.blurRadiusPx(0f, 0f, 0f) >= 0f)
    }

    @Test
    fun `the scrim darkens as the background brightens, and never below the floor`() {
        val min = 0.12f
        val max = 0.5f
        // Por debajo de 0.3 la rampa aún no empieza: se queda en el suelo, que es lo que impide que un
        // cristal sobre negro se vuelva invisible.
        assertEquals(min, LiquidGlassMath.scrimAlpha(0f, min, max), tolerance)
        assertEquals(min, LiquidGlassMath.scrimAlpha(0.3f, min, max), tolerance)
        // A 0.8 la rampa ha terminado.
        assertEquals(max, LiquidGlassMath.scrimAlpha(0.8f, min, max), tolerance)
        assertEquals(max, LiquidGlassMath.scrimAlpha(1f, min, max), tolerance)
        // Y a mitad de rampa, la mitad justa: es una interpolación lineal, no una curva.
        assertEquals((min + max) / 2f, LiquidGlassMath.scrimAlpha(0.55f, min, max), tolerance)
    }

    @Test
    fun `the lens never lets the two refractions meet in the middle`() {
        // Esta es la razón de que la altura sea minDimension / 4 y no / 2: en una píldora ancha, si la
        // refracción de arriba y la de abajo se tocan en el eje medio aparece una costura horizontal
        // oscura. SimpMusic lo documenta como el fallo que tuvo que corregir.
        val minDimension = 120f
        val inradius = minDimension / 2f
        assertTrue(
            "the refraction height must stay under the stadium inradius",
            LiquidGlassMath.lensHeightPx(minDimension, 0f) < inradius,
        )
        assertEquals(30f, LiquidGlassMath.lensHeightPx(minDimension, 0f), tolerance)
        assertEquals(inradius, LiquidGlassMath.lensAmountPx(minDimension), tolerance)
        // Incluso pulsando a fondo (2dp a densidad 3 = 6px) sigue por debajo del eje medio.
        assertTrue(LiquidGlassMath.lensHeightPx(minDimension, 6f) < inradius)
    }
}
