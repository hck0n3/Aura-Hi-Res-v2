/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.utils

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

fun clearPlaybackWebAuthSession(context: Context) {
    clearWebAuthStorage(context)
    val cookieManager = CookieManager.getInstance()
    cookieManager.removeSessionCookies(null)
    cookieManager.removeAllCookies(null)
    cookieManager.flush()
}

suspend fun clearWebAuthSession(context: Context) {
    withContext(Dispatchers.Main.immediate) {
        clearWebAuthStorage(context)
        val cookieManager = CookieManager.getInstance()
        suspendCancellableCoroutine<Unit> { continuation ->
            cookieManager.removeSessionCookies {
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }
}

/**
 * Resets a login WebView before it loads its auth URL.
 *
 * [clearStorage] wipes WebStorage/WebViewDatabase GLOBALLY (every origin, including the file
 * origins the cipher/potoken WebViews use) — keep it true only where a full identity reset is
 * the point (the Spotify login, whose whole contract is "log in as someone else"). The Google
 * login only needs the cookie JAR gone: its stale-session problem is cookies, and leaving
 * storage intact keeps Google's device continuity signals (fewer CAPTCHAs on retry).
 */
fun resetAuthWebViewSession(
    context: Context,
    webView: WebView,
    clearCookies: Boolean = true,
    clearStorage: Boolean = true,
    onReady: () -> Unit,
) {
    webView.stopLoading()
    webView.clearHistory()
    webView.clearFormData()
    webView.clearCache(true)
    if (clearStorage) {
        clearWebAuthStorage(context)
    }

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)
    if (!clearCookies) {
        onReady()
        return
    }

    // Bounded delivery: every caller loads its login URL from [onReady], so an OEM WebView build
    // that drops the cookie callbacks (they are async and unguaranteed) leaves the WebView stuck
    // on its background color forever — the owner's "pantalla en negro". 3s is far above the
    // normal milliseconds-scale completion; the worst case is loading over a half-cleaned jar.
    val ready = java.util.concurrent.atomic.AtomicBoolean(false)
    fun fireOnce() {
        if (ready.compareAndSet(false, true)) onReady()
    }
    cookieManager.removeSessionCookies {
        cookieManager.removeAllCookies {
            cookieManager.flush()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            fireOnce()
        }
    }
    webView.postDelayed({ fireOnce() }, 3_000L)
}

private fun clearWebAuthStorage(context: Context) {
    val appContext = context.applicationContext
    WebStorage.getInstance().deleteAllData()
    WebViewDatabase.getInstance(appContext).apply {
        clearFormData()
        clearHttpAuthUsernamePassword()
        clearUsernamePassword()
    }
}
