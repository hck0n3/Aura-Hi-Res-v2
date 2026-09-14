package iad1tya.echo.music.spotify

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import iad1tya.echo.music.R
import iad1tya.echo.music.spotifyimport.SpotifyImportRepository
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Spotify login in its own plain window: a native layout with a single WebView and nothing else.
 *
 * The Compose login screen loaded the page (form present in the DOM) but the owner's Galaxy only ever
 * showed the page's dark background — something in the app window kept the page from being seen. This
 * activity has no Compose, no glass/haze layers and no overlays, so the page is the only thing on screen.
 * The session is captured from the `sp_dc` cookie (checked on every navigation and once per second, since
 * Spotify moves between steps without full page loads) and saved through [SpotifyImportRepository].
 */
class SpotifyLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var saving = false
    private var finished = false

    private val cookiePoll = object : Runnable {
        override fun run() {
            captureSession()
            if (!finished) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = Color.rgb(18, 18, 18)
        window.statusBarColor = background
        window.navigationBarColor = background

        webView = WebView(this).apply {
            setBackgroundColor(background)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            if (Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = captureSession()

                override fun onPageFinished(view: WebView, url: String?) = captureSession()

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Timber.e("SpotifyLoginActivity: renderer gone (crashed=${detail.didCrash()})")
                    finishLogin()
                    return true
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            fitsSystemWindows = true
            addView(topBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finishLogin()
            }
        })

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)
        // Start from a clean jar (a stale session would log straight into the wrong account). The load
        // waits for the removal, with a fallback in case the callback is never delivered.
        var loaded = false
        val load = {
            if (!loaded) {
                loaded = true
                webView.loadUrl(SpotifyAuth.LOGIN_URL)
                handler.postDelayed(cookiePoll, 1_000L)
            }
        }
        cookies.removeAllCookies { handler.post(load) }
        handler.postDelayed(load, 2_000L)
    }

    // Two rows so nothing overlaps on a phone: back + title, then the two secondary actions.
    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(8), dp(4))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(barButton("←", sizeSp = 22f) { finishLogin() })
                addView(
                    TextView(context).apply {
                        text = getString(R.string.spotify_login_title)
                        setTextColor(Color.WHITE)
                        textSize = 18f
                        setTypeface(typeface, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            },
        )
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                addView(barButton(getString(R.string.login_manual_cookie)) { showManualCookieDialog() })
                addView(
                    barButton(getString(R.string.spotify_login_open_in_browser)) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyAuth.LOGIN_URL))) }
                    },
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun barButton(label: String, sizeSp: Float = 14f, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.rgb(30, 215, 96))
        textSize = sizeSp
        maxLines = 1
        setPadding(dp(10), dp(12), dp(10), dp(12))
        setOnClickListener { onClick() }
    }

    private fun readSpotifyCookies(): Map<String, String> {
        val manager = CookieManager.getInstance()
        val cookies = linkedMapOf<String, String>()
        listOf("https://open.spotify.com", "https://accounts.spotify.com", "https://spotify.com").forEach { url ->
            manager.getCookie(url)?.split(";")?.forEach { part ->
                val separator = part.indexOf('=')
                if (separator > 0) cookies[part.substring(0, separator).trim()] = part.substring(separator + 1).trim()
            }
        }
        return cookies
    }

    private fun captureSession() {
        if (saving || finished) return
        val cookies = readSpotifyCookies()
        val spDc = cookies["sp_dc"].orEmpty()
        if (spDc.isBlank()) return
        saveSession(spDc, cookies["sp_key"].orEmpty())
    }

    private fun saveSession(spDc: String, spKey: String) {
        if (saving || finished) return
        saving = true
        CookieManager.getInstance().flush()
        lifecycleScope.launch {
            runCatching { SpotifyImportRepository.get(this@SpotifyLoginActivity).connectWithCookies(spDc, spKey) }
                .onSuccess {
                    Timber.i("SpotifyLoginActivity: session saved")
                    setResult(RESULT_OK)
                    finishLogin()
                }
                .onFailure { e ->
                    Timber.e(e, "SpotifyLoginActivity: session validation failed")
                    Toast.makeText(this@SpotifyLoginActivity, R.string.spotify_login_failed, Toast.LENGTH_LONG).show()
                    saving = false
                }
        }
    }

    private fun showManualCookieDialog() {
        val input = EditText(this).apply {
            hint = "sp_dc=…; sp_key=…"
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.login_manual_cookie_title)
            .setMessage(R.string.spotify_manual_cookie_body)
            .setView(input)
            .setPositiveButton(R.string.login_manual_cookie_apply) { _, _ ->
                val parts = input.text.toString().split(";").map { it.trim() }
                val spDc = parts.firstOrNull { it.startsWith("sp_dc=") }?.removePrefix("sp_dc=").orEmpty()
                val spKey = parts.firstOrNull { it.startsWith("sp_key=") }?.removePrefix("sp_key=").orEmpty()
                if (spDc.isBlank()) {
                    Toast.makeText(this, R.string.spotify_manual_cookie_no_spdc, Toast.LENGTH_LONG).show()
                } else {
                    saveSession(spDc, spKey)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun finishLogin() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        finish()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
