package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El presupuesto de resoluciones anticipadas de vídeo.
 *
 * Lo que se fija aquí es la diferencia exacta entre el fallo del dueño y el arreglo: el tope sigue
 * existiendo en RÁFAGA (es lo que impide que un resolve especulativo le quite los mutex del WebView al
 * audio que suena) pero ya no se agota para toda la vida del proceso.
 */
class VideoPrefetchBudgetTest {
    private val minute = 60_000L

    @Test
    fun `the burst cap is unchanged — eight, then no more`() {
        val budget = VideoPrefetchBudget(capacity = 8, refillIntervalMs = 2 * minute)
        repeat(8) { attempt ->
            assertTrue("prefetch #${attempt + 1} inside the burst must be allowed", budget.tryConsume(0L))
        }
        assertFalse("the ninth in the same instant is what protects the audio resolve", budget.tryConsume(0L))
    }

    @Test
    fun `the budget refills, which is the whole bug`() {
        val budget = VideoPrefetchBudget(capacity = 8, refillIntervalMs = 2 * minute)
        repeat(8) { budget.tryConsume(0L) }
        assertFalse(budget.tryConsume(minute))
        // Two minutes of music later there is a token again. With the old lifetime counter this stayed
        // false until the process died, and every switch to video paid the full resolve.
        assertTrue("after one refill interval the switch can be instant again", budget.tryConsume(2 * minute))
        assertFalse("one interval buys exactly one token, not a reset", budget.tryConsume(2 * minute))
    }

    @Test
    fun `standing idle does not bank credit`() {
        val budget = VideoPrefetchBudget(capacity = 8, refillIntervalMs = 2 * minute)
        repeat(8) { budget.tryConsume(0L) }
        // Four hours in the background would be 120 refills if they accumulated.
        assertTrue(budget.tryConsume(4 * 60 * minute))
        assertEquals("the bucket fills to capacity and never past it", 7, budget.available)
    }

    @Test
    fun `a partial interval is not rounded up`() {
        val budget = VideoPrefetchBudget(capacity = 2, refillIntervalMs = 2 * minute)
        repeat(2) { budget.tryConsume(0L) }
        assertFalse("119 seconds is not two minutes", budget.tryConsume(2 * minute - 1000L))
        assertTrue(budget.tryConsume(2 * minute))
    }

    @Test
    fun `leftover time carries into the next interval instead of being dropped`() {
        val budget = VideoPrefetchBudget(capacity = 4, refillIntervalMs = 2 * minute)
        repeat(4) { budget.tryConsume(0L) }
        // Refill at 3 min: one token, and the remaining minute must still count toward the next one —
        // resetting the clock to `now` would make every refill cost up to two intervals in practice.
        assertTrue(budget.tryConsume(3 * minute))
        assertTrue("the leftover minute counts, so 4 min is the second refill", budget.tryConsume(4 * minute))
        assertFalse(budget.tryConsume(4 * minute))
    }

    @Test
    fun `the clock starts on first use, not on construction`() {
        val budget = VideoPrefetchBudget(capacity = 1, refillIntervalMs = 2 * minute)
        // A process that has been alive for an hour must not arrive with free refills banked: the thing
        // being limited is prefetching, not being alive.
        assertTrue(budget.tryConsume(60 * minute))
        assertFalse(budget.tryConsume(60 * minute))
        assertTrue(budget.tryConsume(62 * minute))
    }
}
