package iad1tya.echo.music.spotifyimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Registry row 832 — a Spotify pass only marks as liked the songs that are new in Spotify since the last pass. */
class SpotifyLikedImportLedgerTest {

    @Test
    fun `the first import ever marks every matched song`() {
        val matched = listOf("sp1" to "v1", "sp2" to "v2")
        assertEquals(setOf("v1", "v2"), SpotifyLikedImportLedger.toMark(matched, previous = null, mirrorSongIds = emptySet()))
    }

    @Test
    fun `a song already imported is not marked again even if the user un-liked it`() {
        val matched = listOf("sp1" to "v1", "sp2" to "v2", "sp3" to "v3")
        assertEquals(setOf("v3"), SpotifyLikedImportLedger.toMark(matched, previous = setOf("sp1", "sp2"), mirrorSongIds = emptySet()))
    }

    @Test
    fun `without a set yet, the songs already in the mirror count as seen`() {
        val matched = listOf("sp1" to "v1", "sp2" to "v2")
        assertEquals(setOf("v2"), SpotifyLikedImportLedger.toMark(matched, previous = null, mirrorSongIds = setOf("v1")))
    }

    @Test
    fun `the set never forgets an id, so a short Spotify read cannot re-like it later`() {
        val afterShortRead = SpotifyLikedImportLedger.next(previous = setOf("sp1", "sp2"), matched = listOf("sp3" to "v3"))
        assertEquals(setOf("sp1", "sp2", "sp3"), afterShortRead)
        assertEquals(emptySet<String>(), SpotifyLikedImportLedger.toMark(listOf("sp1" to "v1"), afterShortRead, emptySet()))
    }

    @Test
    fun `a track without a Spotify id is keyed by its video id`() {
        assertEquals("yt:v9", SpotifyLikedImportLedger.trackKey("", "v9"))
        assertEquals("sp1", SpotifyLikedImportLedger.trackKey("sp1", "v1"))
    }

    @Test
    fun `accounts get different sets and no id is stored in the clear`() {
        assertNull(SpotifyLikedImportLedger.tagOf(null))
        assertNull(SpotifyLikedImportLedger.tagOf(" "))
        val a = SpotifyLikedImportLedger.tagOf("user_a")!!
        val b = SpotifyLikedImportLedger.tagOf("user_b")!!
        assertNotEquals(a, b)
        assertEquals(12, a.length)
        assertEquals("spotify_liked_imported_ids@$a", SpotifyLikedImportLedger.keyOf(a).name)
    }
}
