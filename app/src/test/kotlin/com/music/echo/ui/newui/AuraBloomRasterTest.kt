package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins HALLAZGO-055: the bloom gradient field is rasterized once per track into a bitmap
 * capped at [BLOOM_RASTER_MAX_DIM] on its longest side, and blitted per frame — instead of
 * re-shading three translucent radial gradients on every frame (six during the dissolve),
 * the fill-rate cost behind the owner's jank report.
 */
class AuraBloomRasterTest {

    @Test
    fun `small surfaces rasterize at full resolution`() {
        assertEquals(1f, bloomRasterScale(800f, 600f, BLOOM_RASTER_MAX_DIM))
        assertEquals(1f, bloomRasterScale(1080f, 1080f, BLOOM_RASTER_MAX_DIM))
    }

    @Test
    fun `large panels downscale proportionally to the cap`() {
        // QHD+ portrait: 1440 x 3120 -> longest side clamped to 1080.
        val scale = bloomRasterScale(1440f, 3120f, BLOOM_RASTER_MAX_DIM)
        assertEquals(1080f / 3120f, scale, 1e-6f)
        assertTrue(scale < 1f)
        // The raster stays under a couple of MB (ARGB_8888).
        val pixels = (1440f * scale) * (3120f * scale)
        assertTrue(pixels * 4 < 3L * 1024 * 1024)
    }

    @Test
    fun `landscape uses the same longest-side cap`() {
        val scale = bloomRasterScale(3120f, 1440f, BLOOM_RASTER_MAX_DIM)
        assertEquals(1080f / 3120f, scale, 1e-6f)
    }

    @Test
    fun `degenerate inputs never divide by zero`() {
        assertEquals(1f, bloomRasterScale(0f, 0f, BLOOM_RASTER_MAX_DIM))
        assertEquals(1f, bloomRasterScale(-5f, 100f, BLOOM_RASTER_MAX_DIM))
        assertEquals(1f, bloomRasterScale(500f, 900f, maxDim = 0))
    }
}
