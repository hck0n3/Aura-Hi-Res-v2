package iad1tya.echo.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins HALLAZGO-028: after Google sign-in the four library pulls (liked songs, library songs,
 * artists, playlists) ran for minutes with ZERO UI feedback — the owner read the silent Home as
 * "the app restarted and is broken/slow". The fix surfaces a "Sincronizando tu biblioteca…"
 * indicator driven by [anySyncActive].
 *
 * The rule encoded here: `overallStatus` only transitions for the FullSync composite and
 * ClearAllSynced; individual ops move ONLY their own per-op field. So "is anything syncing" must
 * be true when `overallStatus` is Syncing OR when ANY per-op field is Syncing — checking
 * `overallStatus` alone would miss every individual pull (the exact login case).
 */
class SyncActiveStateTest {

    @Test
    fun `a fresh state has nothing active`() {
        assertFalse(anySyncActive(SyncState()))
    }

    @Test
    fun `a running full sync is active through overallStatus`() {
        assertTrue(anySyncActive(SyncState(overallStatus = SyncStatus.Syncing)))
    }

    @Test
    fun `every individual op is detected on its own field`() {
        assertTrue(anySyncActive(SyncState(likedSongs = SyncStatus.Syncing)))
        assertTrue(anySyncActive(SyncState(librarySongs = SyncStatus.Syncing)))
        assertTrue(anySyncActive(SyncState(uploadedSongs = SyncStatus.Syncing)))
        assertTrue(anySyncActive(SyncState(likedAlbums = SyncStatus.Syncing)))
        assertTrue(anySyncActive(SyncState(uploadedAlbums = SyncStatus.Syncing)))
        assertTrue(anySyncActive(SyncState(artists = SyncStatus.Syncing)))
        assertTrue(anySyncActive(SyncState(playlists = SyncStatus.Syncing)))
    }

    @Test
    fun `the login case - several pulls queued, one running - is active`() {
        // After login the four pulls go through the serial channel: one runs, the rest wait as
        // Idle. The indicator must stay on for the running one.
        assertTrue(
            anySyncActive(
                SyncState(
                    likedSongs = SyncStatus.Completed,
                    librarySongs = SyncStatus.Syncing,
                    artists = SyncStatus.Idle,
                    playlists = SyncStatus.Idle,
                ),
            ),
        )
    }

    @Test
    fun `terminal per-op states are not active`() {
        assertFalse(
            anySyncActive(
                SyncState(
                    overallStatus = SyncStatus.Completed,
                    likedSongs = SyncStatus.Completed,
                    librarySongs = SyncStatus.Error("429"),
                    artists = SyncStatus.Idle,
                ),
            ),
        )
    }

    @Test
    fun `an overall error with no running op is not active`() {
        assertFalse(anySyncActive(SyncState(overallStatus = SyncStatus.Error("boom"))))
    }
}
