package iad1tya.echo.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins HALLAZGO-061: after Google sign-in the cookie persisted but the library stayed empty —
 * the completion that enqueues the four library syncs ran in the LoginScreen's composition
 * scope and was cancelled when the owner left the screen during the silent validation window.
 * The fix spreads the decision across four call sites (in-screen detection, disposal safety
 * net, App cookie watcher, cold-start recovery); these tests pin the shared predicates so the
 * call sites cannot drift apart and silently drop the sync again.
 */
class LoginCompletionTest {

    private val loggedCookie = "HSID=abc; SSID=def; SAPISID=xyz123; __Secure-3PSAPISID=xyz123"
    private val guestCookie = "HSID=abc; SSID=def; VISITOR_INFO1_LIVE=foo"

    @Test
    fun `a cookie with SAPISID is a logged session`() {
        assertTrue(isLoggedCookie(loggedCookie))
    }

    @Test
    fun `null, blank and SAPISID-less cookies are not logged sessions`() {
        assertFalse(isLoggedCookie(null))
        assertFalse(isLoggedCookie(""))
        assertFalse(isLoggedCookie("   "))
        assertFalse(isLoggedCookie(guestCookie))
    }

    @Test
    fun `completion runs once on a logged page cookie`() {
        assertTrue(shouldCompleteLogin(loggedCookie, hasCompleted = false))
    }

    @Test
    fun `completion does not repeat once the latch is set`() {
        assertFalse(shouldCompleteLogin(loggedCookie, hasCompleted = true))
    }

    @Test
    fun `completion never runs on a guest page cookie`() {
        assertFalse(shouldCompleteLogin(guestCookie, hasCompleted = false))
        assertFalse(shouldCompleteLogin(null, hasCompleted = false))
    }

    @Test
    fun `the cookie watcher fires only on the logged-out to logged-in edge`() {
        assertTrue(shouldSyncOnCookieChange(null, loggedCookie))
        assertTrue(shouldSyncOnCookieChange("", loggedCookie))
        assertTrue(shouldSyncOnCookieChange(guestCookie, loggedCookie))
    }

    @Test
    fun `the cookie watcher stays quiet on re-emission and on logout`() {
        // Cold start re-emits the persisted cookie: NOT a new login.
        assertFalse(shouldSyncOnCookieChange(loggedCookie, loggedCookie))
        assertFalse(shouldSyncOnCookieChange(null, null))
        assertFalse(shouldSyncOnCookieChange(null, guestCookie))
        // Logging out must not fire the sync either.
        assertFalse(shouldSyncOnCookieChange(loggedCookie, null))
        assertFalse(shouldSyncOnCookieChange(loggedCookie, ""))
        assertFalse(shouldSyncOnCookieChange(loggedCookie, guestCookie))
    }

    @Test
    fun `cold-start recovery heals a logged-in account that never synced`() {
        assertTrue(shouldRecoverLibrarySyncOnStart(loggedIn = true, lastLikedSyncTimeMs = 0L))
        assertTrue(shouldRecoverLibrarySyncOnStart(loggedIn = true, lastLikedSyncTimeMs = -1L))
    }

    @Test
    fun `cold-start recovery stays quiet once a sync completed or no account exists`() {
        assertFalse(shouldRecoverLibrarySyncOnStart(loggedIn = true, lastLikedSyncTimeMs = 1756300000000L))
        assertFalse(shouldRecoverLibrarySyncOnStart(loggedIn = false, lastLikedSyncTimeMs = 0L))
    }
}
