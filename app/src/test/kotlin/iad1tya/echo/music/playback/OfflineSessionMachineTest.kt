package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22: «que detecte que no hay internet por cualquier motivo y pase el reproductor y su cola a modo sin
 * conexión … y que vuelva a la normalidad cuando regrese la red». The enter/exit rules of the player's offline session.
 *
 * Reviews 2026-11-23: a FALSE POSITIVE (blocking online music while the internet works) is worse than the stall it prevents.
 * What these pin, by importance: entering needs proof the DEVICE has no internet (no network now, or two connectivity
 * failures close together on DIFFERENT songs — never one, never the retry of the same song); a stall needs that same proof;
 * a network change or a network song that plays clears the failures even with no session; and every way back ends it.
 */
class OfflineSessionMachineTest {
    private val window = 45_000L
    private val maxAge = 120_000L
    private fun machine() = OfflineSessionMachine(window, maxAge)

    /** Two failures on different songs, close together: the shortest honest way in with a network present. */
    private fun OfflineSessionMachine.twoFailures(nowMs: Long = 0, epoch: Long = 1) {
        onConnectivityFailure("a", systemHasNetwork = true, epoch = epoch, nowMs = nowMs)
        onConnectivityFailure("b", systemHasNetwork = true, epoch = epoch, nowMs = nowMs + 1)
    }

    // ---- entering -----------------------------------------------------------------------------------

    @Test
    fun `the system reporting no network starts the session, once`() {
        val m = machine()
        assertEquals(OfflineSessionReason.NO_NETWORK, m.onNoNetwork(epoch = 1, nowMs = 0))
        assertTrue(m.active)
        assertNull("one entry, one notice", m.onNoNetwork(epoch = 1, nowMs = 10))
    }

    @Test
    fun `a connectivity failure with no network at all starts it`() {
        val m = machine()
        assertEquals(OfflineSessionReason.NO_NETWORK, m.onConnectivityFailure("a", systemHasNetwork = false, epoch = 1, nowMs = 0))
    }

    /** One failure never enters while a network exists — validated or not (VPN, IPv6-only, captive portals). */
    @Test
    fun `one connectivity failure with a network present never starts it`() {
        val m = machine()
        assertNull(m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0))
        assertFalse(m.active)
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 1))
    }

    @Test
    fun `two connectivity failures on different songs close together start it`() {
        val m = machine()
        assertNull(m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0))
        assertEquals(OfflineSessionReason.REPEATED_FAILURE, m.onConnectivityFailure("b", systemHasNetwork = true, epoch = 1, nowMs = 6_000))
        assertTrue(m.active)
    }

    /** (a) The bounded retry re-tries the SAME song 3 s later: that is the same evidence again, not a second failure. */
    @Test
    fun `the retry of the same song is not a second failure`() {
        val m = machine()
        assertNull(m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0))
        assertNull(m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 3_000))
        assertNull(m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 9_000))
        assertFalse(m.active)
        assertEquals(OfflineSessionReason.REPEATED_FAILURE, m.onConnectivityFailure("b", systemHasNetwork = true, epoch = 1, nowMs = 12_000))
    }

    @Test
    fun `two failures far apart are not a streak`() {
        val m = machine()
        assertNull(m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0))
        assertNull(m.onConnectivityFailure("b", systemHasNetwork = true, epoch = 1, nowMs = window + 1))
        assertFalse(m.active)
    }

    /** (2) Failure 1 on a dying wifi, onAvailable of mobile data, failure 2 on the working data: not a streak. */
    @Test
    fun `a network change clears the failures even with no session`() {
        val m = machine()
        m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0)
        assertFalse("nothing to end", m.onNetworkChanged())
        assertNull(m.onConnectivityFailure("b", systemHasNetwork = true, epoch = 2, nowMs = 5_000))
        assertFalse(m.active)
    }

    /** (2) A network song that plays (or a fresh resolve) proves the internet works: the failures go too. */
    @Test
    fun `a network that works clears the failures even with no session`() {
        val m = machine()
        m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0)
        assertTrue(m.hasEvidence())
        assertFalse(m.onNetworkWorked())
        assertFalse(m.hasEvidence())
        assertNull(m.onConnectivityFailure("b", systemHasNetwork = true, epoch = 1, nowMs = 5_000))
        assertFalse(m.active)
    }

    // ---- stalls -------------------------------------------------------------------------------------

    /** (1) An isolated refusal + a slow fresh resolve on a low-end phone is not "no internet". */
    @Test
    fun `one failure never makes a stall evidence`() {
        val m = machine()
        assertFalse("nothing at all", m.stallHasEvidence(systemHasNetwork = true, nowMs = 0))
        assertTrue("no network now", m.stallHasEvidence(systemHasNetwork = false, nowMs = 0))
        m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0)
        assertFalse("a single connectivity failure", m.stallHasEvidence(systemHasNetwork = true, nowMs = 5_000))
        m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 6_000)
        assertFalse("its retry is the same evidence", m.stallHasEvidence(systemHasNetwork = true, nowMs = 7_000))
    }

    /** A slow probe (cold PoToken / player JS, 10-30 s on a Redmi 14C) must be allowed to finish. */
    @Test
    fun `being active is not evidence, so the probe that buffers is never cut`() {
        val m = machine()
        m.twoFailures()
        assertTrue(m.active)
        assertFalse("even right after the failures", m.stallHasEvidence(systemHasNetwork = true, nowMs = 2_000))
        assertFalse(m.stallHasEvidence(systemHasNetwork = true, nowMs = window * 2))
        assertTrue("only no network at all", m.stallHasEvidence(systemHasNetwork = false, nowMs = window * 2))
    }

    @Test
    fun `a stall never refreshes the evidence`() {
        val m = machine()
        m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0)
        assertEquals(OfflineSessionReason.STALL, m.onStall(systemHasNetwork = true, epoch = 1, nowMs = 10_000))
        assertFalse("the probe is due counted from the FAILURE", m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = window))
        assertEquals(maxAge - 10_000, m.msUntilExpiry(nowMs = 10_000))
        assertNull("already active", m.onStall(systemHasNetwork = true, epoch = 1, nowMs = 11_000))
    }

    // ---- while active -------------------------------------------------------------------------------

    @Test
    fun `while active the resolver refuses at once, then lets one probe through`() {
        val m = machine()
        m.onNoNetwork(epoch = 7, nowMs = 0)
        assertTrue("no network: always refused", m.shouldRejectResolve(systemHasNetwork = false, epoch = 7, nowMs = window * 3))
        m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 7, nowMs = 100)
        assertTrue(m.shouldRejectResolve(systemHasNetwork = true, epoch = 7, nowMs = window - 1))
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 7, nowMs = 100 + window))
    }

    /** (3) A probe that fails for lack of internet IS a connectivity failure: it renews the evidence, same song or not. */
    @Test
    fun `a failed probe renews the evidence, a YouTube-side failure does not`() {
        val m = machine()
        m.twoFailures(nowMs = 0)
        assertEquals("counted from the second failure, at 1 ms", maxAge + 1 - 2_000, m.msUntilExpiry(nowMs = 2_000))
        assertNull(m.onConnectivityFailure("b", systemHasNetwork = true, epoch = 1, nowMs = 50_000))
        assertEquals("the probe of the same song renews it", maxAge, m.msUntilExpiry(nowMs = 50_000))
        assertTrue(m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 50_001))
    }

    // ---- leaving ------------------------------------------------------------------------------------

    @Test
    fun `any new network or validation ends it, whatever the evidence`() {
        val noNetwork = machine()
        noNetwork.onNoNetwork(epoch = 1, nowMs = 0)
        assertTrue(noNetwork.onNetworkChanged())

        val failed = machine()
        failed.twoFailures()
        assertTrue(failed.onNetworkChanged())
        assertFalse(failed.active)
        assertFalse("nothing to end twice", failed.onNetworkChanged())
    }

    @Test
    fun `a network that works ends it and resets the streak`() {
        val m = machine()
        m.onNoNetwork(epoch = 1, nowMs = 0)
        assertTrue(m.onNetworkWorked())
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 1))
        assertNull("a single later failure is not a streak", m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 2))
    }

    @Test
    fun `the hard cap ends it while a network exists, never with no network at all`() {
        val m = machine()
        m.onConnectivityFailure("a", systemHasNetwork = true, epoch = 1, nowMs = 0)
        m.onConnectivityFailure("b", systemHasNetwork = true, epoch = 1, nowMs = 0)
        assertFalse(m.expireIfDue(systemHasNetwork = true, nowMs = maxAge - 1))
        assertFalse("no network: the session is right", m.expireIfDue(systemHasNetwork = false, nowMs = maxAge))
        assertTrue(m.active)
        assertTrue(m.expireIfDue(systemHasNetwork = true, nowMs = maxAge))
        assertFalse(m.active)
        assertNull("no session, no timer", m.msUntilExpiry(nowMs = maxAge))
    }

    @Test
    fun `the switch or the user end it at once`() {
        val m = machine()
        m.onNoNetwork(epoch = 1, nowMs = 0)
        assertTrue(m.forceExit())
        assertFalse(m.active)
        assertFalse(m.forceExit())
    }

    @Test
    fun `after leaving, the old evidence neither refuses nor blames a stall`() {
        val m = machine()
        m.twoFailures()
        m.onNetworkChanged()
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 10))
        assertFalse(m.stallHasEvidence(systemHasNetwork = true, nowMs = 10))
        assertFalse(m.hasEvidence())
    }

    // ---- after the hard cap (row 717) ---------------------------------------------------------------------

    private val reentry = 600_000L
    private fun machineWithReentry() = OfflineSessionMachine(window, maxAge, reentry)

    /** A session that ended by the cap, two failures at 0 and 1: expired at [expiredAt]. */
    private val expiredAt = maxAge + 1

    private fun expired(): OfflineSessionMachine = machineWithReentry().also {
        it.twoFailures()
        assertTrue(it.expireIfDue(systemHasNetwork = true, nowMs = expiredAt))
    }

    /** C4: the cap is no proof the network works — one connectivity failure on the same network, soon after, comes back. */
    @Test
    fun `right after the hard cap one failure on the same network brings it back`() {
        val m = expired()
        assertTrue("a timed-out session still has something to clear", m.hasEvidence())
        assertEquals(
            OfflineSessionReason.AFTER_EXPIRY,
            m.onConnectivityFailure("probe", systemHasNetwork = true, epoch = 1, nowMs = expiredAt + 5_000),
        )
        assertTrue(m.active)
    }

    @Test
    fun `the re-entry needs the same network, the window, and no proof since`() {
        assertNull("another network", expired().onConnectivityFailure("p", systemHasNetwork = true, epoch = 2, nowMs = expiredAt + 1))
        assertNull("too late", expired().onConnectivityFailure("p", systemHasNetwork = true, epoch = 1, nowMs = expiredAt + reentry))
        val worked = expired()
        worked.onNetworkWorked()
        assertFalse(worked.hasEvidence())
        assertNull("the network worked since", worked.onConnectivityFailure("p", systemHasNetwork = true, epoch = 1, nowMs = expiredAt + 1))
        val changed = expired()
        changed.onNetworkChanged()
        assertNull("the network changed since", changed.onConnectivityFailure("p", systemHasNetwork = true, epoch = 1, nowMs = expiredAt + 1))
    }

    @Test
    fun `without the re-entry window the cap behaves as before`() {
        val m = machine()
        m.twoFailures()
        assertTrue(m.expireIfDue(systemHasNetwork = true, nowMs = expiredAt))
        assertFalse(m.hasEvidence())
        assertNull(m.onConnectivityFailure("p", systemHasNetwork = true, epoch = 1, nowMs = expiredAt + 1))
    }
}
