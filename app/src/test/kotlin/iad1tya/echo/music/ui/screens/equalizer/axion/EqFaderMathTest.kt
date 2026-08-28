package iad1tya.echo.music.ui.screens.equalizer.axion

import iad1tya.echo.music.eq.data.EqConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the absolute Y → gain mapping of the custom band fader (HALLAZGO-054, second round).
 * The fader writes gainForFaderY(pointer Y) on the down event and on every move — no slop, no
 * delta accumulation — so this mapping IS the drag behavior; it must stay bit-identical.
 */
class EqFaderMathTest {

    private val travelPx = 600f
    private val min = EqConstants.GAIN_MIN
    private val max = EqConstants.GAIN_MAX
    private val range = max - min

    @Test
    fun topEdgeIsMaxGain() {
        assertEquals(max, gainForFaderY(0f, travelPx, min, max), 0.0001f)
    }

    @Test
    fun bottomEdgeIsMinGain() {
        assertEquals(min, gainForFaderY(travelPx, travelPx, min, max), 0.0001f)
    }

    @Test
    fun centerIsTheMidpoint() {
        assertEquals((min + max) / 2f, gainForFaderY(travelPx / 2f, travelPx, min, max), 0.0001f)
    }

    @Test
    fun quarterPositionsMapToQuarterGains() {
        // Screen Y grows DOWN: a quarter from the top is three quarters of the range down.
        assertEquals(max - range / 4f, gainForFaderY(travelPx / 4f, travelPx, min, max), 0.0001f)
        assertEquals(min + range / 4f, gainForFaderY(travelPx * 3f / 4f, travelPx, min, max), 0.0001f)
    }

    @Test
    fun positionsBeyondTheEdgesClamp() {
        assertEquals(max, gainForFaderY(-travelPx, travelPx, min, max), 0.0001f)
        assertEquals(min, gainForFaderY(travelPx * 2f, travelPx, min, max), 0.0001f)
    }

    @Test
    fun mappingIsMonotonicInY() {
        var previous = gainForFaderY(0f, travelPx, min, max)
        var y = 0f
        while (y <= travelPx) {
            val next = gainForFaderY(y, travelPx, min, max)
            assertTrue("moving the finger down must never raise the gain", next <= previous)
            previous = next
            y += travelPx / 8f
        }
    }

    @Test
    fun fullTravelCoversTheWholeRange() {
        assertEquals(range, gainForFaderY(0f, travelPx, min, max) - gainForFaderY(travelPx, travelPx, min, max), 0.0001f)
    }

    @Test
    fun degenerateTravelReturnsTheMidpointInsteadOfNaN() {
        assertEquals((min + max) / 2f, gainForFaderY(100f, 0f, min, max), 0.0001f)
        assertEquals((min + max) / 2f, gainForFaderY(100f, -1f, min, max), 0.0001f)
    }
}
