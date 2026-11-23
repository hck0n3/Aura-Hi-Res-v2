package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Test

/** Row 744 (P1-1/P1-2): the host publishes its queue in the order it will PLAY it, which with shuffle on is not the timeline's. */
class HostQueueOrderTest {
    /** A playback order over timeline indices, walked like media3's Timeline with repeat off (-1 past either end). */
    private class Order(private val order: List<Int>) {
        fun next(i: Int): Int = order.indexOf(i).let { p -> if (p < 0 || p + 1 >= order.size) -1 else order[p + 1] }

        fun previous(i: Int): Int = order.indexOf(i).let { p -> if (p <= 0) -1 else order[p - 1] }
    }

    @Test
    fun withoutShuffleItIsExactlyTheTimelineWindow() {
        val ids = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        val linear = Order(ids.indices.toList())
        for (cursor in ids.indices) {
            for (window in listOf(1, 2, 3, 25)) {
                assertEquals(
                    "cursor $cursor window $window",
                    hostQueueWindow(ids.size, cursor, window) { ids[it] }.toList(),
                    hostQueueOrder(ids.size, cursor, window, linear::next, linear::previous) { ids[it] },
                )
            }
        }
    }

    @Test
    fun withShuffleTheUpcomingSongsAreTheShuffleOrder() {
        val ids = listOf("a", "b", "c", "d", "e")
        // media3 plays 3 → 0 → 4 → 1 → 2.
        val shuffled = Order(listOf(3, 0, 4, 1, 2))
        val published = hostQueueOrder(ids.size, cursor = 0, window = 25, next = shuffled::next, previous = shuffled::previous) { ids[it] }
        assertEquals(listOf("d", "a", "e", "b", "c"), published.map { ids[it] })
        // The guest loads it from the host's song on: the next one is the host's next one, not the timeline's "b".
        val (order, start) = roomPlaybackOrder(TrackInfo(id = "a"), published.map { TrackInfo(id = ids[it]) })
        assertEquals("e", order[start + 1].id)
    }

    @Test
    fun theWindowStartsAfterAnEarlierCopyOfTheCurrentSongInPlaybackOrder() {
        val ids = listOf("a", "b", "a", "c")
        // Plays 2 → 1 → 0 → 3: the host is on index 0, the second "a" it hears.
        val shuffled = Order(listOf(2, 1, 0, 3))
        val published = hostQueueOrder(ids.size, cursor = 0, window = 25, next = shuffled::next, previous = shuffled::previous) { ids[it] }
        assertEquals(listOf("b", "a", "c"), published.map { ids[it] })
    }

    @Test
    fun theWindowIsBoundedOnBothSides() {
        val ids = (0 until 100).map { "s$it" }
        val linear = Order(ids.indices.toList())
        val published = hostQueueOrder(ids.size, cursor = 50, window = 25, next = linear::next, previous = linear::previous) { ids[it] }
        assertEquals(25 + 1 + 24, published.size)
        assertEquals(25, published.first())
        assertEquals(74, published.last())
    }

    @Test
    fun anEmptyOrUnsetTimelinePublishesNothing() {
        val none = Order(emptyList())
        assertEquals(emptyList<Int>(), hostQueueOrder(0, 0, 25, none::next, none::previous) { "x" })
        assertEquals(emptyList<Int>(), hostQueueOrder(3, -1, 25, none::next, none::previous) { "x" })
    }
}
