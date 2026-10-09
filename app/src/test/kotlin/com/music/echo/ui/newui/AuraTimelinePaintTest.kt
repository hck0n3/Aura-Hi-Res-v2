package iad1tya.echo.music.ui.newui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-09: *"la barra de tiempo del reproductor… con las combinaciones de colores de la portada de
 * una manera premium y animada"* and *"el me gusta del corazón lo veo desalineado"*.
 */
class AuraTimelinePaintTest {

    private val red = Color(0xFFE74540)
    private val gold = Color(0xFFCD9900)
    private val white = Color(0xFFD4D4D4)

    @Test
    fun `the gradient loops back on its first colour, so its repeat has no seam`() {
        val stops = timelineLoopStops(listOf(red, gold, white))
        assertEquals(listOf(red, gold, white, red), stops)
        assertEquals(red, timelineColorAt(stops, 0f))
        assertEquals(red, timelineColorAt(stops, 1f))
        assertEquals(red, timelineColorAt(stops, -2f))
        assertEquals(gold, timelineColorAt(stops, 1f / 3f))
        // A single colour still makes a valid (flat) gradient.
        assertEquals(3, timelineLoopStops(listOf(red)).size)
    }

    @Test
    fun `the flow is slow, stays inside one period and loops with the ambient clock`() {
        val period = timelineGradientPeriod(1000f)
        assertEquals(750f, period, 0.01f)
        for (step in 0..1000) {
            val shift = timelineFlowShift(step / 1000f, period)
            assertTrue(shift >= 0f && shift < period + 0.01f)
        }
        // Integer cycles per ambient period: the end of the clock's cycle is its start.
        assertEquals(0f, timelineFlowShift(0f, period), 0.01f)
        val nearEnd = timelineFlowShift(0.99999f, period)
        assertTrue(nearEnd < 0.1f || nearEnd > period - 0.1f)
        // Speed: one period every PERIOD_MS / TIMELINE_FLOW_CYCLES — 14 s, a drift and not a scroll.
        assertTrue(AuraAmbientMotion.PERIOD_MS / TIMELINE_FLOW_CYCLES >= 10_000L)
        // Degenerate width never divides by zero.
        assertTrue(timelineGradientPeriod(0f) >= 1f)
    }

    @Test
    fun `the heart glyph is centred on the title block and its edge sits on the content edge`() {
        val glyph = 26.dp
        val (x, y) = auraHeartOpticalOffset(glyph)
        // Vertical (row 363): the glyph itself is centred now (AuraIcons.heartPath), so no lift is needed.
        assertEquals(12f, HEART_VISUAL_CENTER_Y, 0f)
        assertEquals(0f, y.value, 0.01f)
        // Horizontal: box inset (48 − 26) / 2 = 11 dp + the glyph's own right side bearing.
        val expected = (48f - glyph.value) / 2f + glyph.value * (24f - HEART_VISUAL_RIGHT) / 24f
        assertEquals(expected, x.value, 0.01f)
        // The touch target overhangs into the 21 dp gutter but never past the screen edge.
        assertTrue(x.value < AuraSpacing.Gutter.value)
        // A glyph as big as its box needs no inset.
        assertEquals(48f * (24f - HEART_VISUAL_RIGHT) / 24f, auraHeartOpticalOffset(48.dp).first.value, 0.01f)
    }
}
