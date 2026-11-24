package iad1tya.echo.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 833: reconcileSpotifyMirrors keeps in step (adds AND removes) only Aura's own copy of a Spotify source. A mirror an
 * older version created keeps losing what Spotify removed (row 317); one that may be the user's own playlist, linked by name by an
 * older version, gets nothing added or removed.
 */
class SpotifyMirrorOwnershipTest {
    private fun owned(
        createdMarker: String? = null,
        storedFingerprint: String? = null,
        remote: List<String> = listOf("a", "b", "mine"),
        local: Set<String> = setOf("a", "b", "c"),
    ) = SpotifyMirrorOwnership.isAurasCopy("PLx", createdMarker, storedFingerprint, remote, local)

    @Test
    fun `a playlist Aura created for this mirror is its copy`() {
        assertTrue(owned(createdMarker = "PLx"))
    }

    @Test
    fun `a marker for another browseId does not count`() {
        assertFalse(owned(createdMarker = "PLother"))
    }

    @Test
    fun `a mirror an earlier version reconciled completely is adopted even after Spotify removed songs`() {
        // Spotify dropped «gone» after the last complete pass: the account copy still has it, and it must still be removed.
        assertTrue(owned(storedFingerprint = "PLx|3|12345", remote = listOf("a", "b", "gone")))
    }

    @Test
    fun `a fingerprint stored for another browseId does not count`() {
        assertFalse(owned(storedFingerprint = "PLother|3|12345"))
        assertFalse(owned(storedFingerprint = "PLxy|3|12345"))
    }

    @Test
    fun `an account copy with no song outside the mirror is adopted`() {
        assertTrue(owned(remote = listOf("a", "b")))
        assertTrue(owned(remote = emptyList()))
    }

    @Test
    fun `an unknown playlist with songs outside the mirror is left untouched`() {
        // May be the user's own playlist that an older version linked by name: no song added, none removed.
        assertFalse(owned())
    }
}
