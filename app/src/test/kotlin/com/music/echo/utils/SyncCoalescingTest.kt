package iad1tya.echo.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins HALLAZGO-056: sync operations coalesce instead of stacking. The owner's BETA-012 log showed
 * FOUR parallel `FEmusic_library_privately_owned_tracks` browses landing within 31 ms — YouTube
 * answered 429 (rateLimitExceeded) and the whole library showed an error. The root cause was
 * `performFullSyncSuspend()` bypassing the serial sync channel and running CONCURRENTLY with a
 * queued FullSync plus individual queued ops, so the same pull pass ran several times at once.
 *
 * Rules encoded here:
 *  - an individual pull op never starts while a full sync is running (the full sync already
 *    covers it);
 *  - an individual op never overlaps another run of the SAME op;
 *  - steps inside the full-sync composite are blocked only by the same step running elsewhere;
 *  - the composite itself (shouldStartFullSync) never overlaps a same-kind run, and an
 *    upload-carrying request is never swallowed by a down-only run;
 *  - otherwise the op starts.
 */
class SyncCoalescingTest {

    @Test
    fun `an op starts when nothing overlaps`() {
        assertTrue(shouldStartSyncOp(isFullSync = false, fullSyncRunning = false, sameOpRunning = false))
        assertTrue(shouldStartSyncOp(isFullSync = true, fullSyncRunning = false, sameOpRunning = false))
    }

    @Test
    fun `full-sync inner steps are blocked only by the same step, never by the composite itself`() {
        // isFullSync = true marks the steps running INSIDE executeFullSync: they ARE the full sync,
        // so the "a full sync is running" rule must not block them (it would make the composite skip
        // all of its own steps). Only the same step already running elsewhere coalesces them.
        assertTrue(shouldStartSyncOp(isFullSync = true, fullSyncRunning = true, sameOpRunning = false))
        assertFalse(shouldStartSyncOp(isFullSync = true, fullSyncRunning = true, sameOpRunning = true))
        assertFalse(shouldStartSyncOp(isFullSync = true, fullSyncRunning = false, sameOpRunning = true))
    }

    @Test
    fun `individual pull ops wait out a running full sync`() {
        assertFalse(shouldStartSyncOp(isFullSync = false, fullSyncRunning = true, sameOpRunning = false))
    }

    @Test
    fun `the same individual op never overlaps itself`() {
        assertFalse(shouldStartSyncOp(isFullSync = false, fullSyncRunning = false, sameOpRunning = true))
    }

    // ---- full-sync composite decisions (down-only vs upload-carrying runs) -------------------------

    @Test
    fun `a full sync starts when none is running`() {
        assertTrue(shouldStartFullSync(requestIncludeUpload = false, fullSyncRunning = false, uploadInFlight = false))
        assertTrue(shouldStartFullSync(requestIncludeUpload = true, fullSyncRunning = false, uploadInFlight = false))
    }

    @Test
    fun `a down-only request coalesces into any running full sync`() {
        assertFalse(shouldStartFullSync(requestIncludeUpload = false, fullSyncRunning = true, uploadInFlight = false))
        assertFalse(shouldStartFullSync(requestIncludeUpload = false, fullSyncRunning = true, uploadInFlight = true))
    }

    @Test
    fun `an upload request coalesces only when an upload pass is already in flight`() {
        // A down-only run does NOT cover the upload pass — the deliberate "Sincronizar todo" /
        // pull-to-refresh upload must still happen (per-step guards keep the requests unique).
        assertTrue(shouldStartFullSync(requestIncludeUpload = true, fullSyncRunning = true, uploadInFlight = false))
        assertFalse(shouldStartFullSync(requestIncludeUpload = true, fullSyncRunning = true, uploadInFlight = true))
    }
}
