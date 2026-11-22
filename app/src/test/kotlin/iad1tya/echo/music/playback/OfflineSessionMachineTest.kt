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
 * What these pin, by importance: entering needs REAL evidence (declaring offline with a working network hides the user's
 * music); VALIDATED is never required to be online (VPN / IPv6-only / captive portals, ronda 9 / row 284); a network that
 * never validates still finds its way back through a probe; and the session never sticks.
 */
class OfflineSessionMachineTest {
    private val window = 45_000L
    private fun machine() = OfflineSessionMachine(window)

    // ---- entering -----------------------------------------------------------------------------------

    @Test
    fun `the system reporting no network starts the session`() {
        val m = machine()
        assertEquals(OfflineSessionReason.NO_NETWORK, m.onNoNetwork(epoch = 1, nowMs = 0))
        assertTrue(m.active)
        assertNull("one entry, one notice", m.onNoNetwork(epoch = 1, nowMs = 10))
    }

    @Test
    fun `a network failure with no network at all starts it`() {
        val m = machine()
        assertEquals(
            OfflineSessionReason.NO_NETWORK,
            m.onNetworkFailure(systemHasNetwork = false, validated = false, epoch = 1, nowMs = 0),
        )
    }

    @Test
    fun `one failure on a network that is not validated starts it`() {
        val m = machine()
        assertEquals(
            OfflineSessionReason.UNVALIDATED_FAILURE,
            m.onNetworkFailure(systemHasNetwork = true, validated = false, epoch = 1, nowMs = 0),
        )
    }

    /** Not validated, alone, is NOT evidence: a VPN never validates and plays fine. Only a real failure counts. */
    @Test
    fun `not being validated without any failure never starts it`() {
        val m = machine()
        assertFalse(m.active)
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 0))
        assertFalse(m.stallHasEvidence(systemHasNetwork = true, validationLost = false, nowMs = 0))
    }

    @Test
    fun `on a validated network one failure is retried and two in a row start it`() {
        val m = machine()
        assertNull(m.onNetworkFailure(systemHasNetwork = true, validated = true, epoch = 1, nowMs = 0))
        assertFalse(m.active)
        assertEquals(
            OfflineSessionReason.REPEATED_FAILURE,
            m.onNetworkFailure(systemHasNetwork = true, validated = true, epoch = 1, nowMs = 6_000),
        )
        assertTrue(m.active)
    }

    @Test
    fun `two failures far apart are not a streak`() {
        val m = machine()
        assertNull(m.onNetworkFailure(systemHasNetwork = true, validated = true, epoch = 1, nowMs = 0))
        assertNull(m.onNetworkFailure(systemHasNetwork = true, validated = true, epoch = 1, nowMs = window + 1))
        assertFalse(m.active)
    }

    // ---- while active -------------------------------------------------------------------------------

    @Test
    fun `while active the resolver refuses at once instead of trying`() {
        val m = machine()
        m.onNetworkFailure(systemHasNetwork = true, validated = false, epoch = 7, nowMs = 0)
        assertTrue(m.shouldRejectResolve(systemHasNetwork = true, epoch = 7, nowMs = 1_000))
        assertTrue("no network: always refused", m.shouldRejectResolve(systemHasNetwork = false, epoch = 8, nowMs = window * 3))
    }

    /** onAvailable then a resolve: the network changed, so the very next resolve is let through as a probe. */
    @Test
    fun `a new network lets the next resolve through as a probe`() {
        val m = machine()
        m.onNoNetwork(epoch = 3, nowMs = 0)
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 4, nowMs = 500))
    }

    /** A VPN that never validates and never changes still gets a probe after the window: the session cannot stick. */
    @Test
    fun `the same network gets a probe after the window`() {
        val m = machine()
        m.onNetworkFailure(systemHasNetwork = true, validated = false, epoch = 3, nowMs = 0)
        assertTrue(m.shouldRejectResolve(systemHasNetwork = true, epoch = 3, nowMs = window - 1))
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 3, nowMs = window))
    }

    @Test
    fun `a failed probe closes the window again`() {
        val m = machine()
        m.onNetworkFailure(systemHasNetwork = true, validated = false, epoch = 3, nowMs = 0)
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 3, nowMs = window))
        assertNull("already active: no second notice", m.onNetworkFailure(true, false, epoch = 3, nowMs = window + 10))
        assertTrue(m.shouldRejectResolve(systemHasNetwork = true, epoch = 3, nowMs = window + 20))
    }

    @Test
    fun `a stall counts only with evidence`() {
        val m = machine()
        assertFalse(m.stallHasEvidence(systemHasNetwork = true, validationLost = false, nowMs = 0))
        assertTrue(m.stallHasEvidence(systemHasNetwork = false, validationLost = false, nowMs = 0))
        assertTrue(m.stallHasEvidence(systemHasNetwork = true, validationLost = true, nowMs = 0))
        m.onNetworkFailure(systemHasNetwork = true, validated = true, epoch = 1, nowMs = 0)
        assertTrue("a recent failure", m.stallHasEvidence(systemHasNetwork = true, validationLost = false, nowMs = 5_000))
        assertFalse("an old failure", m.stallHasEvidence(systemHasNetwork = true, validationLost = false, nowMs = window + 1))
        assertEquals(OfflineSessionReason.STALL, m.onStall(epoch = 1, nowMs = 6_000))
        assertTrue(m.stallHasEvidence(systemHasNetwork = true, validationLost = false, nowMs = window * 10))
    }

    // ---- leaving ------------------------------------------------------------------------------------

    @Test
    fun `validation or a successful resolve ends it, once`() {
        val validated = machine()
        validated.onNoNetwork(epoch = 1, nowMs = 0)
        assertTrue(validated.onValidated())
        assertFalse(validated.active)
        assertFalse("nothing to end twice", validated.onValidated())

        val resolved = machine()
        resolved.onNetworkFailure(systemHasNetwork = true, validated = false, epoch = 1, nowMs = 0)
        assertTrue(resolved.onResolveSucceeded())
        assertFalse(resolved.active)
        assertFalse(resolved.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 1))
    }

    @Test
    fun `a success resets the streak, so a later single failure is retried again`() {
        val m = machine()
        m.onNetworkFailure(systemHasNetwork = true, validated = true, epoch = 1, nowMs = 0)
        assertFalse("no session to end", m.onResolveSucceeded())
        assertNull(m.onNetworkFailure(systemHasNetwork = true, validated = true, epoch = 1, nowMs = 1_000))
        assertFalse(m.active)
    }

    @Test
    fun `after leaving, the old evidence does not refuse nor blame a stall`() {
        val m = machine()
        m.onNetworkFailure(systemHasNetwork = true, validated = false, epoch = 1, nowMs = 0)
        m.onValidated()
        assertFalse(m.shouldRejectResolve(systemHasNetwork = true, epoch = 1, nowMs = 10))
        assertFalse(m.stallHasEvidence(systemHasNetwork = true, validationLost = false, nowMs = 10))
    }
}
