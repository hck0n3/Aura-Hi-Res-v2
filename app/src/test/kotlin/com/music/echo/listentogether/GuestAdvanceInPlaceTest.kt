package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 616: the guest jumps to the room's new track in place only when what it holds after that track agrees with the room's queue. */
class GuestAdvanceInPlaceTest {
    @Test
    fun nextTrackAlreadyLoadedIsReusedEvenWhenTheRoomWindowShifted() {
        // guest list: current, earlier ones, then the upcoming ones (how playTrack loaded it before roomPlaybackOrder)
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

    private fun tracks(vararg ids: String) = ids.map { TrackInfo(id = it) }

    @Test
    fun theGuestLoadsTheRoomOrderAndStartsAtTheCurrentTrackSoTheNextItemIsTheRoomsNextSong() {
        // the host's window carries two songs it already played before the current one
        val (loaded, start) = roomPlaybackOrder(TrackInfo(id = "cur"), tracks("h1", "h2", "cur", "u1", "u2"))
        assertEquals(listOf("h1", "h2", "cur", "u1", "u2"), loaded.map { it.id })
        assertEquals(2, start)
        // what ExoPlayer buffers next and what the service preloads: the items after the current index
        assertEquals("u1", loaded[start + 1].id)
    }

    @Test
    fun theHostPressingNextIsReusedInPlaceOnTheSongTheGuestHasBeenPreparing() {
        val (loaded, start) = roomPlaybackOrder(TrackInfo(id = "cur"), tracks("h1", "h2", "cur", "u1", "u2", "u3"))
        val have = loaded.map { it.id }
        // the host pressed next: its window now also carries one more upcoming song
        val room = listOf("h1", "h2", "cur", "u1", "u2", "u3", "u4")
        assertTrue(reuseInPlace(have, "u1", room))
        assertEquals(start + 1, have.indexOf("u1"))
    }

    @Test
    fun aCurrentTrackMissingFromTheRoomQueueLeadsAndTheQueueFollows() {
        val (loaded, start) = roomPlaybackOrder(TrackInfo(id = "x"), tracks("a", "", "b", "a"))
        // blank ids dropped; the song queued twice stays twice (review of row 652: dropping a copy shifts everything after it)
        assertEquals(listOf("x", "a", "b", "a"), loaded.map { it.id })
        assertEquals(0, start)
    }

    @Test
    fun theHostWindowStartsAfterAnEarlierCopyOfTheCurrentSong() {
        // host queue A B A C, playing the SECOND A (index 2)
        val ids = listOf("A", "B", "A", "C")
        assertEquals(1 until 4, hostQueueWindow(ids.size, 2, 25) { ids[it] })
        // no earlier copy: the plain window around the cursor
        assertEquals(0 until 4, hostQueueWindow(ids.size, 1, 25) { ids[it] })
    }

    @Test
    fun aGuestOfAQueueWithTheCurrentSongTwiceStartsOnTheHostsCopyAndNextIsTheRoomsNext() {
        val ids = listOf("A", "B", "A", "C")
        val window = hostQueueWindow(ids.size, 2, 25) { ids[it] }.map { TrackInfo(id = ids[it]) }
        val (loaded, start) = roomPlaybackOrder(TrackInfo(id = "A"), window)
        assertEquals(listOf("B", "A", "C"), loaded.map { it.id })
        assertEquals("C", loaded[start + 1].id)
    }

    @Test
    fun aRepeatedSongIsReusedInPlaceOnTheCopyAheadOfTheGuest() {
        // the guest holds A B A C D and plays B (index 1); the room moves on to the second A
        val have = listOf("A", "B", "A", "C", "D")
        val room = listOf("B", "A", "C", "D")
        assertEquals(2, reuseIndexInPlace(have, "A", room, currentIndex = 1))
    }

    @Test
    fun theRoomQueueIsComparedAsTheGuestLoadsItSoBlanksAndDuplicatesStillMatch() {
        val roomIds = listOf("h1", "", "cur", "u1", "cur", "u2")
        val (loaded, _) = roomPlaybackOrder(TrackInfo(id = "cur"), roomIds.map { TrackInfo(id = it) })
        val have = loaded.map { it.id }
        assertTrue(reuseInPlace(have, "u1", roomIds))
        assertEquals(3, reuseIndexInPlace(have, "cur", listOf("u1", "cur", "u2"), currentIndex = 2))
    }
}
