package iad1tya.echo.music.migration

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.aura.migration.source.amazon.AmazonMusicClient
import com.aura.migration.source.amazon.AmazonMusicException
import com.aura.migration.source.amazon.AmazonMusicWeb
import dagger.hilt.android.AndroidEntryPoint
import iad1tya.echo.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Amazon Music sign-in in its own plain window (the same shape as SpotifyLoginActivity: a native layout
 * with one WebView, because a Compose-hosted login page once showed only its background on the owner's
 * phone).
 *
 * The user signs in on the real Amazon page. Each time a page of a Music marketplace finishes loading,
 * the WebView's cookies are minimised to the renewal allowlist; once an account cookie is there,
 * [AmazonMusicClient.connect] proves the session (config.json → pandaToken → signed-in user) and stores
 * it encrypted. Event-driven only: no timer, no polling (AGENTS rule 7). Whatever happens, the full Amazon
 * cookie set is removed from the WebView's jar on the way out — only the minimal session is kept.
 */
@AndroidEntryPoint
class AmazonMusicLoginActivity : ComponentActivity() {

    @Inject
    lateinit var client: AmazonMusicClient

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private var startHost: String = AmazonMusicWeb.DEFAULT_MUSIC_HOST
    private var connecting = false
    private var finished = false
    private var lastFailedFingerprint: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startHost = intent.getStringExtra(EXTRA_MUSIC_HOST)?.takeIf { AmazonMusicWeb.isMusicHost(it) }
            ?: AmazonMusicWeb.DEFAULT_MUSIC_HOST
        val background = Color.rgb(18, 18, 18)
        window.statusBarColor = background
        window.navigationBarColor = background

        webView = WebView(this).apply {
            setBackgroundColor(background)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // The desktop web player, with the same identity the renewal calls replay later.
            settings.userAgentString = AmazonMusicWeb.DESKTOP_USER_AGENT
            if (Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) =
                    tryConnect(url, force = false)

                override fun onPageFinished(view: WebView, url: String?) = tryConnect(url, force = false)

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Timber.tag(TAG).w("renderer gone (crashed=%s)", detail.didCrash())
                    finishLogin(connected = false)
                    return true
                }
            }
        }
        status = TextView(this).apply {
            setTextColor(Color.rgb(200, 200, 200))
            textSize = 13f
            setPadding(dp(16), dp(4), dp(16), dp(8))
            text = getString(R.string.migrate_amazon_login_waiting)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            fitsSystemWindows = true
            addView(topBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finishLogin(connected = false)
            }
        })

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl("https://$startHost/")
    }

    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(4), dp(8), dp(4))
        addView(barButton("←", sizeSp = 22f) { finishLogin(connected = false) })
        addView(
            TextView(context).apply {
                text = getString(R.string.migrate_source_amazon)
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(barButton(getString(R.string.migrate_amazon_login_done)) { tryConnect(webView.url, force = true) })
    }

    private fun barButton(label: String, sizeSp: Float = 14f, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.rgb(37, 209, 218))
        textSize = sizeSp
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(10), dp(12), dp(10), dp(12))
        setOnClickListener { onClick() }
    }

    /**
     * Tries to connect from the current page. Automatic tries only happen on a Music marketplace page with
     * an account cookie and a cookie set that has not already failed; the button forces one more try.
     */
    private fun tryConnect(url: String?, force: Boolean) {
        if (connecting || finished) return
        val host = AmazonMusicWeb.musicHostOf(url) ?: if (force) startHost else return
        val cookieHeader = CookieManager.getInstance().getCookie("https://$host/")
        val cookies = AmazonMusicWeb.renewalCookies(cookieHeader)
        if (!AmazonMusicWeb.hasAccountCookie(cookies)) {
            if (force) status.text = getString(R.string.migrate_amazon_not_signed_in)
            return
        }
        val fingerprint = cookies.hashCode()
        if (!force && fingerprint == lastFailedFingerprint) return
        connecting = true
        status.text = getString(R.string.migrate_amazon_login_checking)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    client.connect(host, cookieHeader, AmazonMusicWeb.DESKTOP_USER_AGENT)
                }
            }
            connecting = false
            result.onSuccess {
                Timber.tag(TAG).i("Amazon Music session saved")
                setResult(RESULT_OK)
                finishLogin(connected = true)
            }.onFailure { e ->
                // Only the failure KIND reaches the shared log: never a cookie, header or response text.
                Timber.tag(TAG).w(
                    "Amazon Music connect failed: %s",
                    (e as? AmazonMusicException)?.reason ?: e.javaClass.simpleName,
                )
                lastFailedFingerprint = fingerprint
                status.text = getString(amazonErrorText(e))
            }
        }
    }

    private fun finishLogin(connected: Boolean) {
        if (finished) return
        finished = true
        if (!connected) setResult(RESULT_CANCELED)
        runCatching { webView.stopLoading() }
        expireAmazonWebCookies()
        finish()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (!finished) {
            finished = true
            expireAmazonWebCookies()
        }
        runCatching {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "AmazonMusicLogin"
        private const val EXTRA_MUSIC_HOST = "music_host"

        fun intent(context: Context, musicHost: String): Intent =
            Intent(context, AmazonMusicLoginActivity::class.java).putExtra(EXTRA_MUSIC_HOST, musicHost)
    }
}
