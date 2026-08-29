package iad1tya.echo.music.utils

/**
 * HALLAZGO-061: pure decision helpers for the WebView login flow.
 *
 * The owner logged in, the cookie persisted, but the library stayed empty: the completion that
 * enqueues the four library syncs ran in the LoginScreen's composition scope, so leaving the
 * screen (or the system reclaiming the app during the silent validation window) cancelled it
 * before it ever ran. These predicates let every recovery path (in-screen detection, the
 * screen-disposal safety net, the App cookie watcher, the cold-start check) agree on the same
 * "is this a logged-in session / is there new work to do" answers without duplicating the rules.
 */

/** A cookie string that carries a signed-in YouTube Music session (SAPISID present). */
fun isLoggedCookie(cookie: String?): Boolean =
    !cookie.isNullOrBlank() && cookie.contains("SAPISID")

/**
 * The WebView landed on the post-login destination: the page whose cookies hold the signed-in
 * session. Checked on EVERY URL change (doUpdateVisitedHistory) and on page-finished, so the
 * detection gets multiple chances instead of one single-shot race (registry #182 — InnerTune,
 * the reference implementation this flow descends from, detects on doUpdateVisitedHistory).
 */
fun isLoginTargetUrl(url: String?): Boolean =
    url != null && url.startsWith("https://music.youtube.com")

/**
 * The WebView just finished a page on music.youtube.com: should the login completion run now?
 * Only once per screen visit ([hasCompleted] is the in-screen latch) and only when the page
 * cookie is a real logged-in session.
 */
fun shouldCompleteLogin(pageCookie: String?, hasCompleted: Boolean): Boolean =
    !hasCompleted && isLoggedCookie(pageCookie)

/**
 * App-level cookie watcher: did the persisted cookie just change into work that needs a library
 * sync? True on the logged-out -> logged-in edge AND on a logged-in -> logged-in change with a
 * DIFFERENT cookie (account switch A -> B): the old code only fired on the first edge, so
 * switching accounts left the library synced to the previous account (registry #182). A cold
 * start that merely re-emits the already-persisted cookie still does NOT re-fire the syncs.
 */
fun shouldSyncOnCookieChange(oldCookie: String?, newCookie: String?): Boolean =
    isLoggedCookie(newCookie) && (!isLoggedCookie(oldCookie) || oldCookie != newCookie)

/**
 * Cold-start recovery: the cookie says logged-in but no liked-songs sync ever completed
 * (its timestamp was never stamped). That is the exact stuck state left behind when the login
 * completion was cancelled mid-flight — enqueue the library syncs once to heal it.
 */
fun shouldRecoverLibrarySyncOnStart(loggedIn: Boolean, lastLikedSyncTimeMs: Long): Boolean =
    loggedIn && lastLikedSyncTimeMs <= 0L

/**
 * Registry #190: the rescue decision for a wedged post-password chain. Google has minted the
 * ACCOUNT session (password accepted — SAPISID present on .google.com) but the YouTube session
 * InnerTube needs has not appeared in the jar yet, so the redirect chain is stuck somewhere
 * between accounts.google.com and music.youtube.com (the owner sees a white screen). The cure
 * is the same flow the account picker triggers manually: re-load the handshake URL and let
 * Google fast-forward passively through its own session.
 *
 * SAFETY: while the password (or a 2FA step) is still being typed there is no .google.com
 * session yet, so this never interrupts credential entry — forgetAccount clears the whole
 * WebView jar at logout, so a .google.com SAPISID can only mean the auth just SUCCEEDED.
 */
fun shouldRescueHandshake(googleCookie: String?, youTubeCookie: String?): Boolean =
    !isLoggedCookie(youTubeCookie) && isLoggedCookie(googleCookie)
