package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-02 — the player's «Suscribirse» chip. The rules a refactor could silently change: which artists the chip
 * acts on, when it reads «Suscrito», when the tap asks «¿a cuál?», and which channel id the follow is stored under.
 */
class PlayerArtistFollowTest {
    private fun artist(id: String?, name: String) = MediaMetadata.Artist(id = id, name = name)

    @Test
    fun `candidates are the named artists once each`() {
        val list = listOf(
            artist("UC1", "Luis Miguel"),
            artist("UC1", "Luis Miguel"),
            artist("UC2", "  "),
            artist(null, "Ricardo Montaner"),
        )
        assertEquals(listOf("Luis Miguel", "Ricardo Montaner"), PlayerArtistFollow.candidates(list).map { it.name })
    }

    @Test
    fun `the chip reads Suscrito only when every artist is followed`() {
        assertFalse(PlayerArtistFollow.allFollowed(emptyList()))
        assertTrue(PlayerArtistFollow.allFollowed(listOf(true)))
        assertFalse(PlayerArtistFollow.allFollowed(listOf(true, false)))
        assertTrue(PlayerArtistFollow.allFollowed(listOf(true, true)))
    }

    @Test
    fun `one artist toggles directly and several ask which`() {
        assertFalse(PlayerArtistFollow.asksWhich(1))
        assertTrue(PlayerArtistFollow.asksWhich(2))
        assertTrue(PlayerArtistFollow.asksWhich(5))
    }

    @Test
    fun `a real channel id is used as is and a name-only artist waits for its resolved id`() {
        assertEquals("UC1", PlayerArtistFollow.knownId(artist("UC1", "A"), emptyMap()))
        // A local row has no channel to follow until it is looked up.
        assertNull(PlayerArtistFollow.knownId(artist("LOCAL_x", "B"), emptyMap()))
        assertNull(PlayerArtistFollow.knownId(artist(null, "B"), emptyMap()))
        // Once resolved in this session, the resolved id wins and the state can be read.
        assertEquals("UCb", PlayerArtistFollow.knownId(artist("LOCAL_x", "B"), mapOf("B" to "UCb")))
    }
}
