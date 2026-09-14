package iad1tya.echo.music.ui.screens

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.EntryPointAccessors
import iad1tya.echo.music.R
import iad1tya.echo.music.applicationScope
import iad1tya.echo.music.constants.DataSyncIdKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.VisitorDataKey
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.YtmSyncWorker
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.isLoggedCookie
import iad1tya.echo.music.utils.isLoginTargetUrl
import iad1tya.echo.music.utils.resetAuthWebViewSession
import iad1tya.echo.music.utils.shouldCompleteLogin
import iad1tya.echo.music.utils.shouldRescueHandshakeArmed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * YouTube Music (Google) login in its own plain window, like [iad1tya.echo.music.spotify.SpotifyLoginActivity].
 *
 * Inside the app window the login page loaded but was never seen on the owner's Galaxy (blank page).
 * This activity keeps the proven flow of the old LoginScreen — full ServiceLogin handshake (#182),
 * detection on every URL change and from the cookie jar (#190), bounded handshake rescue, validation
 * on the application scope (HALLAZGO-061), account picker and manual cookie — with a native layout
 * that has nothing drawn over the WebView.
 */
class YouTubeLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    @Volatile private var visitorData = ""
    @Volatile private var dataSyncId = ""
    @Volatile private var completed = false

    private val syncUtils: SyncUtils by lazy {
        EntryPointAccessors.fromApplication(applicationContext, YtmSyncWorker.YtmSyncEntryPoint::class.java).syncUtils()
    }

    private val accountPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!email.isNullOrBlank()) webView.loadUrl(youTubeServiceLoginUrl(email))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = Color.rgb(15, 15, 15)
        window.statusBarColor = background
        window.navigationBarColor = background

        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            if (visitorData.isEmpty()) visitorData = prefs[VisitorDataKey].orEmpty()
            if (dataSyncId.isEmpty()) dataSyncId = prefs[DataSyncIdKey].orEmpty()
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            if (Build.VERSION.SDK_INT >= 33) settings.isAlgorithmicDarkeningAllowed = false
            webViewClient = object : WebViewClient() {
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) =
                    tryCompleteFromPage(url)

                override fun onPageFinished(view: WebView, url: String?) {
                    view.loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                    view.loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")
                    tryCompleteFromPage(url)
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Timber.e("YouTubeLoginActivity: renderer gone (crashed=${detail.didCrash()})")
                    finish()
                    return true
                }
            }
            addJavascriptInterface(
                LoginSessionBridge(
                    onVisitorData = { if (it != null) visitorData = it },
                    onDataSyncId = { if (it != null) dataSyncId = it.substringBefore("||") },
                ),
                "Android",
            )
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(background)
                fitsSystemWindows = true
                addView(topBar())
                addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            },
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        // Clean cookie jar before the first load (stale half-sessions caused the flicker, registry #202);
        // storage stays for Google's continuity signals.
        resetAuthWebViewSession(context = this, webView = webView, clearCookies = true, clearStorage = false) {
            webView.loadUrl(youTubeServiceLoginUrl())
        }
        startJarWatcher()
    }

    // Two rows so the labels never overlap: back + title, then the two secondary actions.
    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(8), dp(4))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(barButton("←", 22f) { finish() })
                addView(
                    TextView(context).apply {
                        text = getString(R.string.login)
                        setTextColor(Color.WHITE)
                        textSize = 18f
                        setTypeface(typeface, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            },
        )
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                addView(barButton(getString(R.string.login_use_phone_account)) { launchAccountPicker() })
                addView(barButton(getString(R.string.login_manual_cookie)) { showManualCookieDialog() })
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun barButton(label: String, sizeSp: Float = 14f, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.rgb(255, 68, 68))
        textSize = sizeSp
        maxLines = 1
        setPadding(dp(10), dp(12), dp(10), dp(12))
        setOnClickListener { onClick() }
    }

    private fun launchAccountPicker() {
        runCatching {
            accountPicker.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null))
        }.onFailure { Timber.w(it, "Account picker unavailable") }
    }

    private fun tryCompleteFromPage(url: String?) {
        if (!isLoginTargetUrl(url)) return
        tryCompleteWithCookie(CookieManager.getInstance().getCookie(url))
    }

    private fun tryCompleteWithCookie(cookie: String?) {
        if (!shouldCompleteLogin(cookie, completed)) return
        completed = true
        val sessionCookie = cookie!!
        applicationScope.launch { dataStore.edit { it[InnerTubeCookieKey] = sessionCookie } }
        completeLogin(applicationContext, syncUtils, sessionCookie, { visitorData }, { dataSyncId }) { ok ->
            if (isFinishing || isDestroyed) return@completeLogin
            if (ok) {
                finish()
            } else {
                completed = false
                Toast.makeText(this, R.string.login_validation_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Registry #190: the cookie jar is the truth — complete from it wherever the page is stuck, and
    // re-load the handshake (bounded) when Google accepted the password but never minted the YouTube session.
    private fun startJarWatcher() {
        lifecycleScope.launch {
            var rescueCount = 0
            var googleSessionSince = 0L
            var rescueArmed = false
            while (!completed) {
                delay(JAR_WATCH_INTERVAL_MS)
                val youTubeCookie = runCatching { CookieManager.getInstance().getCookie("https://www.youtube.com") }.getOrNull()
                tryCompleteWithCookie(youTubeCookie)
                if (completed) break
                val googleCookie = runCatching { CookieManager.getInstance().getCookie("https://accounts.google.com") }.getOrNull()
                if (!isLoggedCookie(googleCookie)) rescueArmed = true
                if (shouldRescueHandshakeArmed(rescueArmed, googleCookie, youTubeCookie)) {
                    val now = SystemClock.elapsedRealtime()
                    if (googleSessionSince == 0L) {
                        googleSessionSince = now
                    } else if (now - googleSessionSince >= HANDSHAKE_RESCUE_GRACE_MS) {
                        if (rescueCount < MAX_HANDSHAKE_RESCUES) {
                            rescueCount++
                            Timber.i("Login: handshake rescue reloading the page (rescue $rescueCount/$MAX_HANDSHAKE_RESCUES)")
                            webView.loadUrl(youTubeServiceLoginUrl())
                            googleSessionSince = 0L
                        } else {
                            break
                        }
                    }
                } else {
                    googleSessionSince = 0L
                }
            }
        }
    }

    private fun showManualCookieDialog() {
        val input = EditText(this).apply {
            hint = "SID=…; HSID=…; SSID=…; APISID=…; SAPISID=…"
            minLines = 3
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.login_manual_cookie_title)
            .setMessage(R.string.login_manual_cookie_body)
            .setView(input)
            .setPositiveButton(R.string.login_manual_cookie_apply) { _, _ -> tryCompleteWithCookie(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        // HALLAZGO-061 safety net: the user left while a minted session was not completed yet.
        if (!completed) {
            val webCookie = runCatching { CookieManager.getInstance().getCookie("https://music.youtube.com") }.getOrNull()
            if (isLoggedCookie(webCookie)) {
                val appContext = applicationContext
                val sync = syncUtils
                val lastVisitorData = visitorData
                val lastDataSyncId = dataSyncId
                appContext.applicationScope.launch {
                    val stored = appContext.dataStore.data.first()[InnerTubeCookieKey]
                    if (!isLoggedCookie(stored)) {
                        completeLogin(appContext, sync, webCookie!!, { lastVisitorData }, { lastDataSyncId }, null)
                    }
                }
            }
        }
        runCatching {
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
