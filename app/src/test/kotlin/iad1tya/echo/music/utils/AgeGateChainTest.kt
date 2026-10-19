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
    fun `desktopLegacyAgeGateReason marks an age gate whatever the status`() {
        assertTrue(AgeGateChain.isAgeGateShaped("UNPLAYABLE", desktopLegacyAgeGateReason = 1))
        assertFalse(AgeGateChain.isAgeGateShaped("UNPLAYABLE", desktopLegacyAgeGateReason = null))
    }

    @Test
    fun `only the steps whose streams need a GVS PO token ask for one`() {
        assertTrue(AgeGateChain.needsStreamPoToken(Step.WEB_CREATOR))
        assertTrue(AgeGateChain.needsStreamPoToken(Step.ACCOUNT_WEB_REMIX))
        assertFalse(AgeGateChain.needsStreamPoToken(Step.WEB_EMBEDDED))
        assertFalse(AgeGateChain.needsStreamPoToken(Step.TV_SAMSUNG))
        assertFalse(AgeGateChain.needsStreamPoToken(Step.LEGACY_TV_EMBEDDED))
    }

    @Test
    fun `web_embedded stays first with a session, as in yt-dlp's authenticated default clients`() {
        assertEquals(Step.WEB_EMBEDDED, AgeGateChain.order(isLoggedIn = true).first())
    }

    @Test
    fun `a signed-in user gets the account clients first and the creator client, which needs a stream PO token, after them`() {
        assertEquals(
            listOf(Step.WEB_EMBEDDED, Step.TV_SAMSUNG, Step.WEB_CREATOR, Step.ACCOUNT_WEB_REMIX, Step.LEGACY_TV_EMBEDDED),
            AgeGateChain.order(isLoggedIn = true),
        )
    }

    @Test
    fun `the account WEB_REMIX step never goes before the clients that need no stream PO token`() {
        val order = AgeGateChain.order(isLoggedIn = true)
        val remix = order.indexOf(Step.ACCOUNT_WEB_REMIX)
        assertTrue(remix > order.indexOf(Step.WEB_EMBEDDED))
        assertTrue(remix > order.indexOf(Step.TV_SAMSUNG))
        assertTrue(remix < order.indexOf(Step.LEGACY_TV_EMBEDDED))
    }

    @Test
    fun `a guest has no creator client and the legacy embed stays the last resort`() {
        val order = AgeGateChain.order(isLoggedIn = false)
        assertFalse(Step.WEB_CREATOR in order)
        assertFalse("WEB_REMIX con cuenta no tiene sentido sin cuenta", Step.ACCOUNT_WEB_REMIX in order)
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
