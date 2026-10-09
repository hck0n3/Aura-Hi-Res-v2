package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 342 (owner 2026-10-07: "los colores de la portada… animados en el app"). */
class AuraAmbientMotionTest {

    @Test
    fun `the phase wraps and never leaves 0 to 1`() {
        var phase = 0f
        repeat(2_000) {
            phase = AuraAmbientMotion.nextPhase(phase, AuraAmbientMotion.TICK_MS)
            assertTrue(phase in 0f..1f)
        }
        assertEquals(0.5f, AuraAmbientMotion.nextPhase(0f, AuraAmbientMotion.PERIOD_MS / 2), 1e-4f)
        assertEquals(0.25f, AuraAmbientMotion.nextPhase(0.75f, AuraAmbientMotion.PERIOD_MS / 2), 1e-4f)
    }

    @Test
    fun `any battery or heat reason stops the motion`() {
        assertTrue(AuraAmbientMotion.motionAllowed(false, false, false, false, false))
        assertFalse(AuraAmbientMotion.motionAllowed(true, false, false, false, false))
        assertFalse(AuraAmbientMotion.motionAllowed(false, true, false, false, false))
        assertFalse(AuraAmbientMotion.motionAllowed(false, false, true, false, false))
        assertFalse(AuraAmbientMotion.motionAllowed(false, false, false, true, false))
        assertFalse(AuraAmbientMotion.motionAllowed(false, false, false, false, true))
    }

    @Test
    fun `the clock is slow, not the panel's refresh rate`() {
        // 15 fps or less: more steps would not be visible on soft gradients and only cost battery.
        assertTrue(AuraAmbientMotion.TICK_MS >= 60)
        assertTrue(AuraAmbientMotion.PERIOD_MS >= 30_000)
    }

    @Test
    fun `the lobes move, loop seamlessly and stay close to their place`() {
        for (i in 0 until BLOOM_LOBE_COUNT) {
            val start = bloomLobeDrift(i, 0f)
            val end = bloomLobeDrift(i, 0.99999f)
            assertEquals(start.first, end.first, 1e-3f)
            assertEquals(start.second, end.second, 1e-3f)
            assertEquals(start.third, end.third, 1e-3f)
            assertNotEquals(start, bloomLobeDrift(i, 0.25f))
            for (step in 0..100) {
                val (dx, dy, breathe) = bloomLobeDrift(i, step / 100f)
                assertTrue(dx in -1f..1f && dy in -1f..1f)
                assertTrue(breathe in 0.9f..1.1f)
            }
        }
        // The three lobes never move in lockstep: the cover's colours cross each other.
        assertNotEquals(bloomLobeDrift(0, 0.2f), bloomLobeDrift(1, 0.2f))
        assertNotEquals(bloomLobeDrift(1, 0.2f), bloomLobeDrift(2, 0.2f))
        assertNotEquals(bloomLobeDrift(2, 0.2f), bloomLobeDrift(3, 0.2f))
        assertNotEquals(bloomLobeDrift(3, 0.2f), bloomLobeDrift(4, 0.2f))
    }

    @Test
    fun `lobe amplitudes scale with the screen`() {
        val small = bloomLobeGeometry(1080f, 2400f)
        val big = bloomLobeGeometry(1440f, 3120f)
        for (i in 0 until BLOOM_LOBE_COUNT) {
            assertEquals(small.amplitudeX[i] / 1080f, big.amplitudeX[i] / 1440f, 1e-4f)
            assertTrue(small.radiusX[i] > 0f && small.radiusY[i] > 0f)
        }
    }

    /** Owner 2026-10-09: the cover's colours reach the whole screen, not only the top two thirds. */
    @Test
    fun `two lower lobes carry colour below the render's band`() {
        val h = 2400f
        val g = bloomLobeGeometry(1080f, h)
        assertEquals(BLOOM_LOBE_COUNT, g.centerY.size)
        for (i in 3 until BLOOM_LOBE_COUNT) {
            // Rest point below the old band's bottom edge (66 % of the height)…
            assertTrue(g.centerY[i] > 0.66f * h)
            // …and even at the top of its drift it stays in the lower half.
            assertTrue(g.centerY[i] - g.amplitudeY[i] > 0.5f * h)
            // Its glow reaches the bottom edge.
            assertTrue(g.centerY[i] + g.radiusY[i] * 0.9f >= h * 0.98f)
        }
    }
}

/** Row 353 (owner 2026-10-08): the full-screen player's «Predeterminado» is exactly the Inicio ground. */
class PlayerHomeLookTest {
    @Test
    fun `the default player background is the Inicio bloom and nothing else`() {
        val recipe = auraGroundRecipe(
            iad1tya.echo.music.constants.PlayerBackgroundStyle.DEFAULT,
            hasCover = true,
            motion = true,
        )
        assertEquals(1f, recipe.bloom, 0f)
        assertEquals(0f, recipe.cover, 0f)
        assertEquals(0f, recipe.wash, 0f)
        assertEquals(0f, recipe.lobes, 0f)
        assertEquals(0f, recipe.film, 0f)
    }
}
