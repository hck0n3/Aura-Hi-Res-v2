package iad1tya.echo.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La deformación de la pastilla del cristal interactivo.
 *
 * El dueño lo pidió *"flotante y animada según las acciones"*. Esto es la mitad que se puede comprobar
 * sin un teléfono: que al moverse deprisa se alargue y se estreche —y no simplemente crezca— y que un
 * gesto rápido no pueda romper la escala.
 */
class LiquidSquashStretchTest {
    private val tol = 0.0001f

    @Test
    fun `at rest the pill is exactly its base size`() {
        assertEquals(1f, LiquidSquashStretch.scaleX(1f, 0f), tol)
        assertEquals(1f, LiquidSquashStretch.scaleY(1f, 0f), tol)
    }

    @Test
    fun `moving stretches one axis and squashes the other`() {
        val x = LiquidSquashStretch.scaleX(1f, 0.2f)
        val y = LiquidSquashStretch.scaleY(1f, 0.2f)
        assertTrue("it must get LONGER along the movement", x > 1f)
        assertTrue("and NARROWER across it — otherwise it is just growing", y < 1f)
    }

    @Test
    fun `the two axes move in opposite directions, which is what reads as a droplet`() {
        // Same velocity number, opposite effect: that is the whole trick. If both grew, the pill would
        // inflate like a balloon instead of deforming like a drop.
        val v = 0.15f
        assertTrue(LiquidSquashStretch.scaleX(1f, v) > LiquidSquashStretch.scaleY(1f, v))
    }

    @Test
    fun `moving the other way mirrors the deformation`() {
        val forward = LiquidSquashStretch.scaleX(1f, 0.1f)
        val back = LiquidSquashStretch.scaleX(1f, -0.1f)
        assertTrue("forwards stretches", forward > 1f)
        assertTrue("backwards squashes", back < 1f)
    }

    @Test
    fun `a fast flick cannot blow the scale up or flip it`() {
        // Without the clamp the divisor can reach zero (infinite scale) or go negative (the pill turns
        // inside out). This is the case that protects it.
        listOf(5f, 50f, 1000f, -5f, -50f, -1000f).forEach { v ->
            val x = LiquidSquashStretch.scaleX(1f, v)
            val y = LiquidSquashStretch.scaleY(1f, v)
            assertTrue("scaleX must stay finite and positive at velocity $v", x.isFinite() && x > 0f)
            assertTrue("scaleY must stay finite and positive at velocity $v", y.isFinite() && y > 0f)
        }
        // The clamp caps the stretch at a known bound: 1 / (1 - 0.2) = 1.25.
        assertEquals(1.25f, LiquidSquashStretch.scaleX(1f, 1000f), tol)
    }

    @Test
    fun `the base scale is respected, not replaced`() {
        // A pressed pill is already inflated; the deformation multiplies that, it does not overwrite it.
        val pressed = 76f / 56f
        assertTrue(LiquidSquashStretch.scaleX(pressed, 0.1f) > pressed)
    }
}
