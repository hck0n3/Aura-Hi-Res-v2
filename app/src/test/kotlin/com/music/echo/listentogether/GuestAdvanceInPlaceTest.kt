package iad1tya.echo.music.listentogether

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 616: the guest jumps to the room's new track in place only when what it holds after that track agrees with the room's queue. */
class GuestAdvanceInPlaceTest {
    @Test
    fun nextTrackAlreadyLoadedIsReusedEvenWhenTheRoomWindowShifted() {
        // guest list: current, earlier ones, then the upcoming ones (how playTrack loads it)
        val have = listOf("cur", "h1", "h2", "u1", "u2", "u3")
        // the host pressed next: its window is now around u1
        val room = listOf("h1", "h2", "cur", "u1", "u2", "u3", "u4")
        assertTrue(reuseInPlace(have, "u1", room))
    }

    @Test
    fun aDifferentUpcomingQueueIsNotReused() {
        assertFalse(reuseInPlace(listOf("a", "b", "c", "d"), "b", listOf("a", "b", "x", "d")))
    }

    @Test
    fun aTrackThatIsNotInTheListIsNotReused() {
        assertFalse(reuseInPlace(listOf("a", "b"), "z", listOf("z", "b")))
    }

    @Test
    fun theLastTrackOfTheGuestListAgreesWithAnyRoomQueue() {
        // nothing after it on the guest: the next change of the room rebuilds the list as before
        assertTrue(reuseInPlace(listOf("a", "b"), "b", listOf("a", "b", "c")))
    }
}
