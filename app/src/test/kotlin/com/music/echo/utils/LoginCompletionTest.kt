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
    fun `the cookie watcher fires on account switch between two logged sessions`() {
        // Registry #182: switching account A -> B (both logged) must re-sync the library; the
        // old edge-only rule left it pinned to the previous account.
        val otherAccountCookie = "HSID=zzz; SSID=yyy; SAPISID=other456; __Secure-3PSAPISID=other456"
        assertTrue(shouldSyncOnCookieChange(loggedCookie, otherAccountCookie))
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
    fun `the login target is the music youtube origin`() {
        assertTrue(isLoginTargetUrl("https://music.youtube.com"))
        assertTrue(isLoginTargetUrl("https://music.youtube.com/library"))
        assertTrue(isLoginTargetUrl("https://music.youtube.com/?foo=bar"))
    }

    @Test
    fun `the rescue fires when google is minted but youtube is not`() {
        // #190: password accepted (.google.com SAPISID) but the YouTube leg never minted.
        val googleCookie = "SAPISID=xyz; HSID=g1; SSID=g2"
        assertTrue(shouldRescueHandshake(googleCookie, null))
        assertTrue(shouldRescueHandshake(googleCookie, ""))
        assertTrue(shouldRescueHandshake(googleCookie, "VISITOR_INFO1_LIVE=abc"))
    }

    @Test
    fun `no rescue while the password is still being typed`() {
        // No .google.com session yet — credential entry must never be interrupted.
        assertFalse(shouldRescueHandshake(null, null))
        assertFalse(shouldRescueHandshake("", null))
        assertFalse(shouldRescueHandshake("SIDCC=abc", null))
    }

    @Test
    fun `no rescue once the youtube session exists or google is absent`() {
        val googleCookie = "SAPISID=xyz; HSID=g1; SSID=g2"
        val youTubeCookie = "SAPISID=yt; VISITOR_INFO1_LIVE=abc; __Secure-3PSAPISID=yt"
        // The YouTube session minted: nothing left to rescue, the completion takes over.
        assertFalse(shouldRescueHandshake(googleCookie, youTubeCookie))
        // No Google session: nothing to fast-forward through.
        assertFalse(shouldRescueHandshake(null, youTubeCookie))
    }

    @Test
    fun `google and other origins are not the login target`() {
        assertFalse(isLoginTargetUrl(null))
        assertFalse(isLoginTargetUrl(""))
        assertFalse(isLoginTargetUrl("https://accounts.google.com/v3/signin/identifier"))
        assertFalse(isLoginTargetUrl("https://www.youtube.com/"))
        assertFalse(isLoginTargetUrl("http://music.youtube.com"))
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
