package iad1tya.echo.music.utils

import iad1tya.echo.music.utils.AgeGateChain.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeGateChainTest {
    @Test
    fun `an age gate is recognised by status, including the LOGIN_REQUIRED YouTube really sends`() {
        assertTrue(AgeGateChain.isAgeGateShaped("LOGIN_REQUIRED"))
        assertTrue(AgeGateChain.isAgeGateShaped("AGE_CHECK_REQUIRED"))
        assertTrue(AgeGateChain.isAgeGateShaped("AGE_VERIFICATION_REQUIRED"))
        assertTrue(AgeGateChain.isAgeGateShaped("CONTENT_CHECK_REQUIRED"))
        assertFalse(AgeGateChain.isAgeGateShaped("OK"))
        assertFalse(AgeGateChain.isAgeGateShaped("UNPLAYABLE"))
        assertFalse(AgeGateChain.isAgeGateShaped(null))
    }

    @Test
    fun `a signed-in user gets the account clients first and the creator client, which needs a stream PO token, after them`() {
        assertEquals(
            listOf(Step.WEB_EMBEDDED, Step.TV_SAMSUNG, Step.WEB_CREATOR, Step.LEGACY_TV_EMBEDDED),
            AgeGateChain.order(isLoggedIn = true),
        )
    }

    @Test
    fun `a guest has no creator client and the legacy embed stays the last resort`() {
        val order = AgeGateChain.order(isLoggedIn = false)
        assertFalse(Step.WEB_CREATOR in order)
        assertEquals(Step.WEB_EMBEDDED, order.first())
        assertEquals(Step.LEGACY_TV_EMBEDDED, order.last())
    }

    @Test
    fun `steps already tried are skipped`() {
        assertEquals(
            listOf(Step.TV_SAMSUNG, Step.LEGACY_TV_EMBEDDED),
            AgeGateChain.order(isLoggedIn = false, alreadyTried = setOf(Step.WEB_EMBEDDED)),
        )
    }

    @Test
    fun `the failure message names the age, never says the song is missing, and differs for a guest`() {
        val signedIn = AgeGateChain.failureMessage(true)
        val guest = AgeGateChain.failureMessage(false)
        assertTrue(signedIn.contains("edad") && signedIn.contains("cuenta"))
        assertTrue(guest.contains("edad") && guest.contains("Inicia sesión"))
        for (m in listOf(signedIn, guest)) {
            assertFalse(m.contains("no está disponible"))
            assertFalse(m.contains("no existe"))
        }
    }
}
