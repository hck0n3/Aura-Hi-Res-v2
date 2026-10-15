package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 392 (owner 2026-10-15, Apple Music screenshot): «las orillas de las portadas no se ven como las de
 * Apple Music todavía». Measured on that screenshot: the radius GROWS WITH THE COVER — ≈ 3.5 dp on a 44 dp
 * thumbnail, ≈ 8.5 dp on a 182 dp tile — and the app used one fixed 11 dp for both.
 */
class AuraArtworkCornerTest {
    @Test
    fun `a list thumbnail is no longer a pill and a tile keeps its roundness`() {
        val thumb = auraArtworkCornerDp(44f)
        val tile = auraArtworkCornerDp(182f)
        assertEquals(3.6f, thumb, 0.5f)   // Apple Music ≈ 3.5 dp
        assertEquals(8.5f, tile, 0.6f)    // Apple Music ≈ 8.5 dp
        assertTrue("the old fixed 11 dp on a 48 dp thumbnail was nearly a quarter of its width", auraArtworkCornerDp(48f) < 5f)
    }

    @Test
    fun `it grows with the cover but stays between 3 and 14 dp`() {
        var last = 0f
        for (side in listOf(30f, 44f, 80f, 120f, 182f, 260f, 345f, 600f)) {
            val r = auraArtworkCornerDp(side)
            assertTrue("radius must not shrink as the cover grows ($side dp)", r >= last)
            assertTrue(r in 3f..14f)
            last = r
        }
    }

    @Test
    fun `the corner control scales it and it never exceeds half the side`() {
        assertEquals(0f, auraArtworkCornerDp(182f, scale = 0f), 0f)
        assertEquals(auraArtworkCornerDp(182f) * 2f, auraArtworkCornerDp(182f, scale = 2f), 0.001f)
        // A 12 dp cover cannot have a 14 dp corner: RoundedCornerShape's own rule, kept.
        assertEquals(6f, auraArtworkCornerDp(12f, scale = 10f), 0f)
    }

    @Test
    fun `the shape compares by its scale so a recomposition does not re-clip`() {
        assertEquals(AuraArtworkShape(1f), AuraArtworkShape(1f))
        assertEquals(AuraArtworkShape(1f).hashCode(), AuraArtworkShape(1f).hashCode())
        assertTrue(AuraArtworkShape(1f) != AuraArtworkShape(2f))
    }
}
