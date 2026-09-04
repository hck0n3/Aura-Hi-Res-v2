package iad1tya.echo.music.ui.screens

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import iad1tya.echo.music.R
import iad1tya.echo.music.applicationScope
import iad1tya.echo.music.spotify.SpotifyAuth
import iad1tya.echo.music.spotifyimport.SpotifyImportRepository
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.resetAuthWebViewSession
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * # Spotify Login — full-screen structural clone of the working Google login.
 *
 * OWNER REPORT (2026-09-04, after 2 failed fixes on the sheet): "cuando toco iniciar sesión
 * Spotify abre la ventana pero no aparece nada, solo un fondo oscuro". Every previous attempt
 * kept the login inside a Material3 ModalBottomSheet — a SEPARATE ComponentDialog window with
 * a transparent background (verified in the m3 1.5.0-alpha18 bytecode), its own soft-input
 * mode, decor-fits and IME handling. The ONLY visible WebView that works on the owner's S26
 * Ultra is the Google login — a plain NavHost screen in the Activity's window. This screen is
 * a STRUCTURAL CALCO of that login: same window, same container (Column fillMaxSize +
 * weight(1f) AndroidView, no clip), same insets, same settings, same cleanup-on-open. The only
 * Spotify-specific parts are the destination URL, the passive sp_dc capture (persisted via
 * SpotifyImportRepository.get() + applicationScope — the exact LoginScreen/MusicDatabase
 * pattern) and the full c28fd7c instrumentation — if this screen fails, the structural diff
 * with the working Google login is ZERO and the cause is Spotify's server, which app.log names.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val skin = rememberAuraPanelSkin()
    val cookieManager = remember { CookieManager.getInstance() }

    // The sp_dc capture can fire from three callbacks — latch it (Google's pattern).
    var captured by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastRescuedUrl by remember { mutableStateOf<String?>(null) }

    fun captureCookies() {
        if (captured) return
        val cookies = readSpotifyLoginCookies(cookieManager)
        val spDc = cookies["sp_dc"].orEmpty()
        if (spDc.isBlank()) return
        captured = true
        cookieManager.flush()
        Timber.i("SpotifyLogin: sp_dc captured, persisting via repository")
        // Google's exact completion pattern: applicationScope (survives this screen) + the
        // singleton repository reached through its EntryPoint — no screen-scoped ViewModel.
        context.applicationScope.launch {
            runCatching {
                SpotifyImportRepository.get(context).connectWithCookies(
                    spDc = spDc,
                    spKey = cookies["sp_key"].orEmpty(),
                )
            }.onSuccess {
                Timber.i("SpotifyLogin: session persisted, leaving")
                if (navController.currentDestination?.route == "spotify_login") {
                    navController.navigateUp()
                }
            }.onFailure { e ->
                Timber.e(e, "SpotifyLogin: session validation failed")
                captured = false
            }
        }
    }

    BackHandler { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() else navController.navigateUp() }

    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (skin.enabled && skin.darkGround) Modifier.background(AuraPalette.Ground)
                else Modifier,
            ),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.spotify_login_title)) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
        Text(
            text = stringResource(R.string.spotify_waiting_for_login),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = if (skin.enabled && skin.darkGround) AuraPalette.OnGroundMuted else androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
            factory = { webViewContext ->
                WebView(webViewContext).apply {
                    webViewRef = this
                    settings.apply {
                        javaScriptEnabled = true
                        // The Spotify SPA uses localStorage; the Google login does not.
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        // REAL identity minus "; wv" (c28fd7c — a mobile-Chrome UA consistent
                        // with the WebView's own client hints; the frozen Chrome/Windows UA was
                        // placebo #1 of this saga).
                        userAgentString = userAgentString.replace("; wv", "")
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            // PASSIVE capture (c28fd7c): never veto navigation.
                            captureCookies()
                            return false
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            Timber.i("SpotifyLogin: page started")
                            captureCookies()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            captureCookies()
                            // Live-DOM probe (c28fd7c): the SSR paints the form without JS, so
                            // "is the page alive" = #__next childElementCount > 0. One retry per URL.
                            if (!captured && url != null && url.contains("accounts.spotify.com")) {
                                view.evaluateJavascript(
                                    "(document.getElementById('__next') ? document.getElementById('__next').childElementCount : -1)"
                                ) { children ->
                                    val live = children?.trim()?.toIntOrNull() ?: -1
                                    if (live <= 0 && lastRescuedUrl != url) {
                                        lastRescuedUrl = url
                                        Timber.w("SpotifyLogin: login DOM empty after load (next children=$live), reloading once")
                                        view.postDelayed({ if (!captured) view.reload() }, 1500L)
                                    }
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                // Code + description only, no URL (row-4 rule: user data stays out of logs).
                                Timber.e("SpotifyLogin: main-frame error ${error.errorCode}: ${error.description}")
                            }
                            super.onReceivedError(view, request, error)
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            if (request.isForMainFrame) {
                                val host = request.url?.host ?: "unknown"
                                Timber.e("SpotifyLogin: HTTP ${errorResponse.statusCode} on $host")
                            }
                            super.onReceivedHttpError(view, request, errorResponse)
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail,
                        ): Boolean {
                            // targetSdk 36: without this override a dead renderer KILLS THE APP.
                            Timber.e("SpotifyLogin: renderer gone (crashed=${detail.didCrash()}), leaving screen")
                            view.post { navController.navigateUp() }
                            return true
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            // Hydration crashes of the login SPA surface ONLY here.
                            val text = msg.message()
                            if (text.length > 220) {
                                Timber.w("SpotifyLogin console[${msg.messageLevel()}]: ${text.substring(0, 220)}…")
                            } else {
                                Timber.w("SpotifyLogin console[${msg.messageLevel()}]: $text")
                            }
                            return true
                        }

                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            if (newProgress in listOf(25, 50, 100)) {
                                Timber.i("SpotifyLogin: progress $newProgress%")
                            }
                        }
                    }

                    // Same cleanup-on-open as the sheet (Spotify's contract is a full identity
                    // reset — clearStorage=true; the Google login uses false to keep its
                    // continuity signals. Both share resetAuthWebViewSession's bounded CAS).
                    resetAuthWebViewSession(
                        context = webViewContext,
                        webView = this,
                        clearCookies = true,
                        clearStorage = true,
                    ) {
                        Timber.i("SpotifyLogin: cleanup delivered, loading login URL")
                        loadUrl(SpotifyAuth.LOGIN_URL)
                    }
                }
            },
        )
    }
}

/** Same cookie sweep the sheet used (open/accounts/spotify origins), read-only. */
private fun readSpotifyLoginCookies(cookieManager: CookieManager): Map<String, String> {
    val urls = linkedSetOf(
        "https://open.spotify.com",
        "https://accounts.spotify.com",
        "https://spotify.com",
    )
    val cookies = linkedMapOf<String, String>()
    cookieManager.flush()
    urls.forEach { url ->
        cookieManager.getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@forEach
                val key = part.substring(0, separator).trim()
                val value = part.substring(separator + 1).trim()
                if (key.isNotBlank()) cookies[key] = value
            }
    }
    return cookies
}
