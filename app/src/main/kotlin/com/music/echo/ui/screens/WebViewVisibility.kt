package iad1tya.echo.music.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Keeps a visible login WebView in Chromium's "visible" page state.
 *
 * On the owner's Galaxy the login pages loaded fully (form present in the DOM) but reported
 * `document.visibilityState == "hidden"`, so Chromium painted only the page background — the blank or
 * dark login screen. This resumes the WebView when it is created, whenever it is attached to the window
 * and whenever the hosting activity resumes, and re-sends the view visibility once attached so the
 * page state is recomputed.
 */
internal fun keepWebViewResumed(webView: WebView) {
    fun resume() {
        webView.onResume()
        webView.resumeTimers()
    }

    resume()
    val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) resume()
    }
    webView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            resume()
            (webView.context.findActivity() as? LifecycleOwner)?.lifecycle?.addObserver(lifecycleObserver)
            webView.post {
                webView.visibility = View.INVISIBLE
                webView.visibility = View.VISIBLE
                resume()
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            (webView.context.findActivity() as? LifecycleOwner)?.lifecycle?.removeObserver(lifecycleObserver)
        }
    })
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
