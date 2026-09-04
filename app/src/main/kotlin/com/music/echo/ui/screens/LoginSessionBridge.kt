package iad1tya.echo.music.ui.screens

import android.webkit.JavascriptInterface

/**
 * The named JavaScript bridge of the YouTube login WebView (installed as "Android").
 *
 * Previously this was an ANONYMOUS object, which gave it an unpredictable R8 name — every other
 * JS-facing WebView of the repo (CipherWebView, SolverWebView, PoTokenWebView) is a named class
 * with a matching -keepclassmembers rule in proguard-rules.pro, and this one was the lone
 * exception: if R8 renamed/removed the members, the page's
 * `Android.onRetrieveVisitorData(...)` calls would silently die and visitorData/dataSyncId would
 * never reach the login flow (row 29 fallbacks cover playback, but the login's own derivation
 * path depends on these callbacks). A named class makes the ProGuard rule expressible.
 */
class LoginSessionBridge(
    private val onVisitorData: (String?) -> Unit,
    private val onDataSyncId: (String?) -> Unit,
) {
    @JavascriptInterface
    fun onRetrieveVisitorData(newVisitorData: String?) {
        if (newVisitorData != null) {
            onVisitorData(newVisitorData)
        }
    }

    @JavascriptInterface
    fun onRetrieveDataSyncId(newDataSyncId: String?) {
        if (newDataSyncId != null) {
            onDataSyncId(newDataSyncId.substringBefore("||"))
        }
    }
}
