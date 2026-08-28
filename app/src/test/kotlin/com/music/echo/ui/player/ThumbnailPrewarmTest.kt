package iad1tya.echo.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cache audit H4 (HALLAZGO-037): the player cover prewarm used to burst-download EVERY cover in
 * the queue at 1200 px on each queue change. The window pins the new rule: prewarm only the
 * current song plus the next few, so distant covers load lazily (from disk) on scroll.
 */
class ThumbnailPrewarmTest {

    @Test
    fun emptyQueuePrewarmsNothing() {
        assertEquals(emptyList<Int>(), prewarmIndices(queueSize = 0, currentIndex = 0, ahead = 4))
    }

    @Test
    fun prewarmCoversCurrentAndNextSongs() {
        assertEquals(listOf(2, 3, 4, 5, 6), prewarmIndices(queueSize = 10, currentIndex = 2, ahead = 4))
    }

    @Test
    fun prewarmClampsAtQueueEnd() {
        assertEquals(listOf(8, 9), prewarmIndices(queueSize = 10, currentIndex = 8, ahead = 4))
        assertEquals(listOf(9), prewarmIndices(queueSize = 10, currentIndex = 9, ahead = 4))
    }

    @Test
    fun unknownIndexCoercesIntoQueue() {
        assertEquals(listOf(0, 1), prewarmIndices(queueSize = 10, currentIndex = -1, ahead = 1))
        assertEquals(listOf(9), prewarmIndices(queueSize = 10, currentIndex = 99, ahead = 4))
    }

    @Test
    fun zeroAheadPrewarmsOnlyCurrent() {
        assertEquals(listOf(3), prewarmIndices(queueSize = 10, currentIndex = 3, ahead = 0))
    }

    @Test
    fun negativeAheadPrewarmsNothing() {
        assertEquals(emptyList<Int>(), prewarmIndices(queueSize = 10, currentIndex = 3, ahead = -1))
    }
}
