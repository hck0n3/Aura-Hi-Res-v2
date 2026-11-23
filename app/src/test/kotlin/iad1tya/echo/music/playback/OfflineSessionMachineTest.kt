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
 * Review 2026-11-23: a FALSE POSITIVE (blocking online music while the internet works) is worse than the stall it prevents.
 * What these pin, by importance: entering needs proof the DEVICE has no internet (no network now, or two connectivity
 * failures close together — never one, never "not validated"); being active is never evidence by itself, so a slow probe is
 * never cut; and every way back (new network, validated, a network item playing, the cap, the switch, the user) ends it.
 */
class OfflineSessionMachineTest {
    private val window = 45_000L
    private val maxAge = 120_000L
    private fun machine() = OfflineSessionMachine(window, maxAge)

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
        assertEquals(OfflineSessionReason.NO_NETWORK, m.onConnectivityFailure(systemHasNetwork = false, epoch = 1, nowMs = 0))
    }

    /** C3: one failure never enters while a network exists — validated or not (VPN, IPv6-only, captive portals). */
    @Test
    fun `one connectivity failure with a network present never starts it`() {
        val m = machine()
        assertNull(m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0))
        assertFalse(m.active)
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 1))
    }

    @Test
    fun `two connectivity failures close together start it`() {
        val m = machine()
        assertNull(m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0))
        assertEquals(OfflineSessionReason.REPEATED_FAILURE, m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 6_000))
        assertTrue(m.active)
    }

    @Test
    fun `two failures far apart are not a streak`() {
        val m = machine()
        assertNull(m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0))
        assertNull(m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = window + 1))
        assertFalse(m.active)
    }

    // ---- stalls (C1) --------------------------------------------------------------------------------

    @Test
    fun `a stall counts only with independent evidence`() {
        val m = machine()
        assertFalse("nothing at all", m.stallHasEvidence(systemHasNetwork = true, nowMs = 0))
        assertTrue("no network now", m.stallHasEvidence(systemHasNetwork = false, nowMs = 0))
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0)
        assertTrue("a connectivity failure a moment ago", m.stallHasEvidence(systemHasNetwork = true, nowMs = 5_000))
        assertFalse("an old one", m.stallHasEvidence(systemHasNetwork = true, nowMs = window + 1))
    }

    /** C1: a slow probe (cold PoToken / player JS, 10-30 s on a Redmi 14C) must be allowed to finish. */
    @Test
    fun `being active is not evidence, so the probe that buffers is never cut`() {
        val m = machine()
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0)
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 1_000)
        assertTrue(m.active)
        assertFalse("even right after the failures", m.stallHasEvidence(systemHasNetwork = true, nowMs = 2_000))
        assertFalse(m.stallHasEvidence(systemHasNetwork = true, nowMs = window * 2))
        assertTrue("only no network at all", m.stallHasEvidence(systemHasNetwork = false, nowMs = window * 2))
    }

    /** C1: a stall proves nothing new, so it never pushes the probe window or the cap further away. */
    @Test
    fun `a stall never refreshes the evidence`() {
        val m = machine()
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0)
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
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 7, nowMs = 100)
        assertTrue(m.shouldRejectResolve(systemHasNetwork = true, epoch = 7, nowMs = window - 1))
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 7, nowMs = 100 + window))
    }

    /** C5: a YouTube-side failure of the probe is not a connectivity failure, so nothing extends the session. */
    @Test
    fun `only connectivity failures extend the session`() {
        val m = machine()
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0)
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 1_000)
        assertEquals(maxAge - 1_000, m.msUntilExpiry(nowMs = 2_000))
        assertNull(m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 50_000))
        assertEquals("a real one does", maxAge, m.msUntilExpiry(nowMs = 50_000))
    }

    // ---- leaving (C4, C6) ---------------------------------------------------------------------------

    @Test
    fun `any new network or validation ends it, whatever the evidence`() {
        val noNetwork = machine()
        noNetwork.onNoNetwork(epoch = 1, nowMs = 0)
        assertTrue(noNetwork.onNetworkChanged())

        val failed = machine()
        failed.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0)
        failed.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 1)
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
        assertNull("a single later failure is not a streak", m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 2))
    }

    @Test
    fun `the hard cap ends it while a network exists, never with no network at all`() {
        val m = machine()
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0)
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0)
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
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 0)
        m.onConnectivityFailure(systemHasNetwork = true, epoch = 1, nowMs = 1)
        m.onNetworkChanged()
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 10))
        assertFalse(m.stallHasEvidence(systemHasNetwork = true, nowMs = 10))
    }
}
