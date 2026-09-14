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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import android.widget.Toast
import iad1tya.echo.music.ui.newui.AuraType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    var showManualCookieDialog by remember { mutableStateOf(false) }
    // LAYER 1/2 (owner log analysis 2026-09-05: page loads to 100%, JS runs, the form IS in the
    // server HTML — yet the owner sees black). The placeholder covers the dark pre-paint (a
    // WebView without its own background + the app's dark theme = black indistinguishable from
    // "nothing happens"); loginBroken arms the escape panel when the live-DOM watcher confirms
    // the page truly has nothing visible after a reload.
    var showLoadingPlaceholder by remember { mutableStateOf(true) }
    var loginBroken by remember { mutableStateOf(false) }

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
            actions = {
                IconButton(onClick = { showManualCookieDialog = true }) {
                    Icon(
                        painterResource(R.drawable.content_copy),
                        contentDescription = stringResource(R.string.login_manual_cookie),
                    )
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

        if (showManualCookieDialog) {
            val scope = rememberCoroutineScope()
            SpotifyManualCookieDialog(
                onDismiss = { showManualCookieDialog = false },
                onSubmit = { spDc, spKey ->
                    if (spDc.isNotBlank()) {
                        scope.launch {
                            runCatching {
                                SpotifyImportRepository
                                    .get(context).connectWithCookies(spDc, spKey)
                            }.onSuccess {
                                showManualCookieDialog = false
                                if (navController.currentDestination?.route == "spotify_login") {
                                    navController.navigateUp()
                                }
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    context, R.string.spotify_login_failed,
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    } else {
                        android.widget.Toast.makeText(
                            context, R.string.spotify_manual_cookie_no_spdc,
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                },
            )
        }

        // LAYER 1/2 surface: the WebView plus (over it) the loading placeholder and — only when
        // the live-DOM watcher confirms a truly dead page after one reload — the escape panel
        // with the three exits a normal user needs (retry / system browser / manual cookie).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
        ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { webViewContext ->
                WebView(webViewContext).apply {
                    webViewRef = this
                    // Device DevTools check (2026-09-13): the login page reported
                    // document.visibilityState == "hidden" while on screen, so Chromium painted only
                    // the page background — the dark empty screen. Forcing the page active made it
                    // visible. Keep this WebView explicitly resumed.
                    keepWebViewResumed(this)
                    // LAYER 1 (2026-09-05 diagnosis): a WebView without its own background is
                    // algorithmic-darkening prey on One UI (light parent theme + targetSdk 36),
                    // and the dark pre-paint over the app's near-black ground read as "no
                    // muestra nada". Chrome paints WHITE before content — a future render wedge
                    // now shows as a WHITE hole (visible, reportable) instead of black silence.
                    setBackgroundColor(android.graphics.Color.WHITE)
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
                        // The device test showed the login form present in the DOM (visible, sized)
                        // while the screen stayed dark: the dark app theme lets the WebView darken
                        // the page algorithmically. The login page brings its own colors.
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            isAlgorithmicDarkeningAllowed = false
                        }
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
                            Timber.i("SpotifyLogin: page started (webview ${view.width}x${view.height})")
                            // LAYER 1: the placeholder owns the visual until the page commits a
                            // real paint — the owner never again stares at featureless dark.
                            showLoadingPlaceholder = true
                            loginBroken = false
                            captureCookies()
                        }

                        override fun onPageCommitVisible(view: WebView, url: String?) {
                            // First visual commit of the page: hand the surface to it right away.
                            showLoadingPlaceholder = false
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            captureCookies()
                            // LAYER 1: first commit → hand the surface to the page.
                            showLoadingPlaceholder = false
                            // LAYER 2 — LIVE-DOM WATCHER (3 snapshots at +1.5s/+3s/+4.5s).
                            // The c28fd7c probe only asked "does #__next exist" and only logged
                            // failures — the owner's log showed a 100% load with ZERO probe
                            // trace, so the DOM state was unobservable. This watcher reports
                            // REAL VISIBILITY: the login control's rect + computed style + body
                            // background + viewport. Colors/ints only (regla 4 AGENTS).
                            // Decision rules: photo 1 always logged (the shared app.log finally
                            // shows the truth); a second photo with no visible control (or an
                            // off-screen/zero rect) reloads ONCE (a fresh compositor frame is
                            // the wedge treatment); a third equally-empty photo arms the
                            // escape panel (retry / system browser / manual cookie).
                            if (!captured && url != null && url.contains("accounts.spotify.com")) {
                                val js = """
                                    (function(){
                                        var e = document.querySelector('[data-testid="login-username"],[data-testid="login-button"]');
                                        var el;
                                        if (!e) { el = 'none'; }
                                        else {
                                            var r = e.getBoundingClientRect(); var c = getComputedStyle(e);
                                            el = [Math.round(r.x),Math.round(r.y),Math.round(r.width),Math.round(r.height)].join(',') + ' ' + c.display + '/' + c.visibility + '/' + c.opacity;
                                        }
                                        return JSON.stringify({
                                            next: document.getElementById('__next') ? document.getElementById('__next').childElementCount : -1,
                                            el: el,
                                            bg: getComputedStyle(document.body).backgroundColor,
                                            vp: window.innerWidth + 'x' + window.innerHeight
                                        });
                                    })()
                                """.trimIndent()
                                var photos = 0
                                listOf(1500L, 3000L, 4500L).forEachIndexed { i, delayMs ->
                                    view.postDelayed({
                                        if (captured || url != lastRescuedUrl && i > 0) return@postDelayed
                                        view.evaluateJavascript(js) { raw ->
                                            if (captured) return@evaluateJavascript
                                            photos++
                                            val json = raw?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "null"
                                            Timber.i("SpotifyLogin: dom-photo $photos $json")
                                            val visible = json.contains("login-username") || json.contains("login-button")
                                            val noneOrBroken = json.contains("\"el\":\"none\"") ||
                                                Regex("\"el\":\"-?\\d+,-?\\d+,0,0").containsMatchIn(json) ||
                                                json.contains("\"el\":null")
                                            when {
                                                visible && !noneOrBroken -> {
                                                    // Real, sized, on-screen controls: the page lives.
                                                    loginBroken = false
                                                }
                                                photos == 2 && lastRescuedUrl != url -> {
                                                    lastRescuedUrl = url
                                                    Timber.w("SpotifyLogin: photo 2 shows no visible login UI, reloading once (compositor-wedge treatment)")
                                                    view.postDelayed({ if (!captured) view.reload() }, 400L)
                                                }
                                                photos >= 3 -> {
                                                    Timber.w("SpotifyLogin: login page still shows nothing after reload — arming the escape panel")
                                                    loginBroken = true
                                                }
                                            }
                                        }
                                    }, delayMs)
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
                            // onPageFinished is not always delivered on Samsung builds (the log shows
                            // 100% with no finish) — never keep the placeholder over a loaded page.
                            if (newProgress >= 100) showLoadingPlaceholder = false
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

            // LAYER 1: loading placeholder — owns the visual until the first real paint.
            if (showLoadingPlaceholder && !loginBroken) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = AuraPalette.Teal,
                        trackColor = AuraPalette.TrackEmpty,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.spotify_connecting_web),
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                    )
                }
            }

            // LAYER 2/3: escape panel — only after the watcher confirmed a dead page post-reload.
            if (loginBroken) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.spotify_login_unrenderable_title),
                        style = AuraType.RowTitle,
                        color = AuraPalette.OnGround,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.spotify_login_unrenderable_body),
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.TextButton(onClick = {
                        loginBroken = false
                        showLoadingPlaceholder = true
                        webViewRef?.reload()
                    }) {
                        Text(stringResource(R.string.spotify_login_retry))
                    }
                    androidx.compose.material3.TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(SpotifyAuth.LOGIN_URL),
                                ),
                            )
                        }.onFailure {
                            android.widget.Toast.makeText(
                                context, R.string.spotify_login_failed,
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) {
                        Text(stringResource(R.string.spotify_login_open_in_browser))
                    }
                    androidx.compose.material3.TextButton(onClick = { showManualCookieDialog = true }) {
                        Text(stringResource(R.string.login_manual_cookie))
                    }
                }
            }
        }
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

/**
 * BETA-043: last-resort "manual cookie" login for Spotify, ported from SimpMusic's
 * `DevLogInBottomSheet`. Bypasses the WebView entirely — the owner opens open.spotify.com
 * in their browser, copies the full Cookie header, and pastes it here. The cookie is parsed
 * the same way `captureCookies` would, so the rest of the pipeline (latch, persistence via
 * SpotifyImportRepository) is unchanged.
 */
@Composable
private fun SpotifyManualCookieDialog(
    onDismiss: () -> Unit,
    onSubmit: (spDc: String, spKey: String) -> Unit,
) {
    var cookieText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    iad1tya.echo.music.ui.component.AuraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_manual_cookie_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.spotify_manual_cookie_body),
                    color = AuraPalette.OnGroundMuted,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = cookieText,
                    onValueChange = { cookieText = it },
                    placeholder = { Text("sp_dc=xxxxxxxxxx; sp_key=yyyyyy; ...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val spDc = cookieText.split(";")
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("sp_dc=") }
                        ?.removePrefix("sp_dc=").orEmpty()
                    val spKey = cookieText.split(";")
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("sp_key=") }
                        ?.removePrefix("sp_key=").orEmpty()
                    onSubmit(spDc, spKey)
                },
                enabled = cookieText.contains("sp_dc="),
            ) {
                Text(stringResource(R.string.login_manual_cookie_apply))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
