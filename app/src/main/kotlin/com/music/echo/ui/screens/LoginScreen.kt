

package iad1tya.echo.music.ui.screens

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.music.innertube.YouTube
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalSyncUtils
import iad1tya.echo.music.R
import iad1tya.echo.music.applicationScope
import iad1tya.echo.music.constants.AccountChannelHandleKey
import iad1tya.echo.music.constants.AccountEmailKey
import iad1tya.echo.music.constants.AccountNameKey
import iad1tya.echo.music.constants.DataSyncIdKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.VisitorDataKey
import iad1tya.echo.music.db.MusicDatabaseEntryPoint
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.isLoggedCookie
import iad1tya.echo.music.utils.isLoginTargetUrl
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.utils.shouldCompleteLogin
import iad1tya.echo.music.utils.shouldRescueHandshake
import iad1tya.echo.music.utils.shouldRescueHandshakeArmed
import iad1tya.echo.music.utils.resetAuthWebViewSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * HALLAZGO-061: process-level latch. The completion below runs on the application scope so it
 * SURVIVES the LoginScreen going away — but two recovery paths (the in-page detection and the
 * disposal safety net) can fire close together, so only the first CAS winner actually runs.
 * Reset in `finally` so a later logout -> login cycle in the same process can complete again.
 */
private val loginCompletionRunning = AtomicBoolean(false)

/**
 * Registry #190: how often the jar watcher polls the WebView cookie jar while the post-password
 * chain is wedged. Polling the jar (not URL callbacks) is what makes the completion immune to
 * every variant of the white screen: the jar is the truth, the URL is just where the WebView
 * happens to be stuck.
 */
private const val JAR_WATCH_INTERVAL_MS = 500L

/**
 * Registry #190: how long a minted Google account session (password accepted) is given to
 * finish minting the YouTube session before the app re-loads the handshake itself — the same
 * passive fast-forward the account picker triggers manually, which the owner confirmed works
 * on the S26 Ultra.
 */
private const val HANDSHAKE_RESCUE_GRACE_MS = 4000L

/**
 * Registry #190: total rescue attempts per screen visit. Each rescue re-loads the handshake
 * URL; if Google keeps failing to carry the session over to YouTube the loop stops instead
 * of reloading forever (the owner can still use the account picker, which forces the same
 * passive flow from a trusted chooser).
 */
private const val MAX_HANDSHAKE_RESCUES = 3

/**
 * Registry #182: the sign-in entry point. InnerTune — the reference implementation this flow
 * descends from — enters Google sign-in through the FULL YouTube handshake (ltmpl=music,
 * service=youtube, passive=true, continue → youtube.com/signin?action_handle_signin=true →
 * music.youtube.com), not a bare `continue=music.youtube.com`. That extra handshake leg is what
 * makes Google mint the .youtube.com session cookie (SAPISID) the whole login detection keys on;
 * with the bare continue the WebView can land on music.youtube.com without it, detection never
 * fires, and the app "se queda allí donde inicio sesión y no hace nada" (the owner's report).
 */
private fun youTubeServiceLoginUrl(emailHint: String? = null): String {
    val base = "https://accounts.google.com/ServiceLogin" +
        "?ltmpl=music&service=youtube&passive=true" +
        "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
    return if (emailHint.isNullOrBlank()) {
        base
    } else {
        base + "&Email=${Uri.encode(emailHint)}&login_hint=${Uri.encode(emailHint)}"
    }
}

/**
 * HALLAZGO-061: validates the fresh YouTube session and persists the account. Runs on the
 * APPLICATION scope, not the composition scope: the owner's v2.0.11 log proved the old
 * composition-scoped launch got cancelled when the user left the screen during the silent
 * ~1.5-5 s validation window, so the cookie persisted but the library syncs never ran.
 *
 * [onFinished] is invoked on the main thread with the validation result; pass null when the
 * caller (the disposal safety net) has no UI left to update. The visitor/dataSync states are
 * read AFTER the propagation delay because the JavascriptInterface callbacks deliver those
 * values asynchronously during the wait.
 */
private fun completeLogin(
    context: Context,
    syncUtils: SyncUtils,
    cookie: String,
    visitorDataState: MutableState<String>,
    dataSyncIdState: MutableState<String>,
    onFinished: ((Boolean) -> Unit)?,
) {
    if (!loginCompletionRunning.compareAndSet(false, true)) return
    context.applicationScope.launch {
        try {
            // HALLAZGO-028: right after Google sign-in the session is still propagating on
            // YouTube's side — account_menu answered 500 backendError at 500 ms (owner then had
            // to press "Accede" again and go back). 1500 ms matches SimpMusic's proven delay,
            // and InnerTube.accountMenu now retries 5xx on a 1 s/2 s clock too.
            delay(1500)

            val visitorData = visitorDataState.value
            val dataSyncId = dataSyncIdState.value

            YouTube.cookie = cookie
            YouTube.dataSyncId = dataSyncId
            YouTube.visitorData = visitorData

            Timber.d("Login: YouTube object initialized, validating...")

            YouTube.accountInfo().onSuccess { info ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        MusicDatabaseEntryPoint.get(context)
                            .clearArtistAccountSyncMarkers()
                    }
                }.onFailure { e ->
                    Timber.w(e, "Login: could not clear the artist account-sync markers")
                }

                context.dataStore.edit { prefs ->
                    prefs[InnerTubeCookieKey] = cookie
                    if (visitorData.isNotEmpty()) prefs[VisitorDataKey] = visitorData
                    if (dataSyncId.isNotEmpty()) prefs[DataSyncIdKey] = dataSyncId
                    prefs[AccountNameKey] = info.name
                    prefs[AccountEmailKey] = info.email.orEmpty()
                    prefs[AccountChannelHandleKey] = info.channelHandle.orEmpty()
                }
                syncUtils.syncLibraryAfterLogin()

                Timber.d("Login: Successfully logged in as ${info.name}")
                withContext(Dispatchers.Main) { onFinished?.invoke(true) }
            }.onFailure {
                Timber.e(it, "Login: Authentication validation failed")
                reportException(it)
                withContext(Dispatchers.Main) { onFinished?.invoke(false) }
            }
        } finally {
            loginCompletionRunning.set(false)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val syncUtils = LocalSyncUtils.current
    val context = LocalContext.current
    // HALLAZGO-061: keep the State objects themselves, not just the delegated values — the
    // process-scoped completion and the disposal safety net must read the LATEST values after
    // this composition is gone.
    val visitorDataState = rememberPreference(VisitorDataKey, "")
    var visitorData by visitorDataState
    val dataSyncIdState = rememberPreference(DataSyncIdKey, "")
    var dataSyncId by dataSyncIdState
    val innerTubeCookieState = rememberPreference(InnerTubeCookieKey, "")
    val hasCompletedLoginState = remember { mutableStateOf(false) }
    val skin = rememberAuraPanelSkin()
    var showManualCookieDialog by remember { mutableStateOf(false) }

    // Held in state so the account-picker callback can reload it. The WebView loads the full
    // handshake ServiceLogin URL (#182); the picker only PRE-FILLS the email so the user skips
    // typing it.
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // System account picker (AndroidX framework, NOT Google Play Services) — enumerates the phone's
    // synced Google accounts with NO permission and NO GMS, so it works on the FOSS flavor too. It only
    // returns the chosen email; Google's own web sign-in still handles the password/2FA. login_hint is
    // the standard OIDC param the sign-in page honours to pre-select/pre-fill that account.
    val accountPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (!email.isNullOrBlank()) {
            // Same full-handshake URL (#182), with the chosen account pre-filled.
            webViewRef?.loadUrl(youTubeServiceLoginUrl(email))
        }
    }

    fun launchAccountPicker() {
        runCatching {
            val intent = AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null,
            )
            accountPicker.launch(intent)
        }.onFailure { Timber.w(it, "Account picker unavailable") }
    }

    // #182: ONE detection helper, called from BOTH WebView callbacks AND the #190 jar watcher.
    // Reads/writes go through the State objects (not the delegated vars) so every caller always
    // sees the latest values. The hasCompletedLogin latch + the process-level CAS inside
    // completeLogin keep it idempotent no matter how many times the paths fire.
    fun tryCompleteLoginWithCookie(pageCookie: String?) {
        if (!shouldCompleteLogin(pageCookie, hasCompletedLoginState.value)) return
        // Persist the cookie right away (App's cookie watcher picks the session up live) and
        // latch so the completion fires once per screen visit.
        innerTubeCookieState.value = pageCookie!!
        hasCompletedLoginState.value = true

        // HALLAZGO-061: completion runs on the application scope — leaving the screen during
        // validation can no longer cancel it.
        completeLogin(context, syncUtils, pageCookie, visitorDataState, dataSyncIdState) { ok ->
            if (ok) {
                webViewRef?.apply {
                    stopLoading()
                    clearHistory()
                    clearCache(true)
                    clearFormData()
                }
                // The completion also finishes after the user left on their own — only
                // navigate when still ON the login screen.
                if (navController.currentDestination?.route == "login") {
                    navController.navigateUp()
                }
            } else {
                // HALLAZGO-061 fix D: the old failure path was completely silent — the user
                // was left staring at a WebView that never goes back, with no idea the
                // validation failed.
                hasCompletedLoginState.value = false
                Toast.makeText(context, R.string.login_validation_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun tryCompleteLoginFromPage(url: String?) {
        if (!isLoginTargetUrl(url)) return
        tryCompleteLoginWithCookie(CookieManager.getInstance().getCookie(url))
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (skin.enabled && skin.darkGround) Modifier.background(AuraPalette.Ground)
                else Modifier,
            ),
    ) {
        if (skin.enabled && skin.darkGround) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            ) {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = null,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = AuraPalette.OnGround,
                    )
                }
                Text(
                    text = stringResource(R.string.login),
                    style = AuraType.ScreenTitle,
                    color = AuraPalette.OnGround,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = { launchAccountPicker() }) {
                    Text(
                        stringResource(R.string.login_use_phone_account),
                        color = AuraPalette.Teal,
                    )
                }
                androidx.compose.material3.TextButton(onClick = { showManualCookieDialog = true }) {
                    Text(
                        stringResource(R.string.login_manual_cookie),
                        color = AuraPalette.Teal,
                    )
                }
            }
        } else {
            TopAppBar(
                title = { Text(stringResource(R.string.login)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = null,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = { launchAccountPicker() }) {
                        Text(stringResource(R.string.login_use_phone_account))
                    }
                    androidx.compose.material3.TextButton(onClick = { showManualCookieDialog = true }) {
                        Text(stringResource(R.string.login_manual_cookie))
                    }
                },
            )
        }

        if (showManualCookieDialog) {
            ManualCookieDialog(
                onDismiss = { showManualCookieDialog = false },
                onSubmit = { cookieValue ->
                    // Reuse the same completion path as the WebView capture. shouldCompleteLogin
                    // only accepts strings containing the YouTube session markers (SAPISID,
                    // HSID, SSID...), so a stray paste is rejected and the dialog stays open
                    // with the text intact for the user to fix it.
                    tryCompleteLoginWithCookie(cookieValue)
                    if (hasCompletedLoginState.value) {
                        showManualCookieDialog = false
                    }
                },
            )
        }

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
                    webViewClient = object : WebViewClient() {
                        // #182: fires on EVERY URL change — the whole post-login redirect chain,
                        // not just the final page-finished event. InnerTune detects login here
                        // for exactly this reason: onPageFinished alone is a single-shot race
                        // that the cookie flush can lose ("se queda allí y no hace nada").
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            tryCompleteLoginFromPage(url)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")
                            // Second detection chance: the session cookie can finish flushing
                            // after the last URL change. Same latched helper, so it is safe to
                            // run on every page.
                            tryCompleteLoginFromPage(url)
                        }

                        // PARITY BLINDAGE (audit 2026-09-05): targetSdk 36 — without this
                        // override a dead renderer KILLS THE APP. SpotifyLoginScreen already
                        // has it; the Google login is the last visible WebView without it.
                        // Log (INFO → app.log) and leave the screen — never crash over a login.
                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: android.webkit.RenderProcessGoneDetail,
                        ): Boolean {
                            Timber.e("Login: WebView renderer gone (crashed=${detail.didCrash()}), leaving screen")
                            view.post { navController.navigateUp() }
                            return true
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    addJavascriptInterface(
                        LoginSessionBridge(
                            onVisitorData = { if (it != null) visitorData = it },
                            onDataSyncId = { if (it != null) dataSyncId = it.substringBefore("||") },
                        ),
                        "Android",
                    )
                    webViewRef = this
                    // Owner report 2026-09-04 (flicker blanco↔contenido making login impossible):
                    // the screen used to load the handshake over whatever jar the previous visit
                    // left behind. A stale .google.com SAPISID with no .youtube.com one made the
                    // #190 rescue reload the handshake every 4s — wiping the half-typed password
                    // form each time. Clear the jar BEFORE the first load (same pattern the
                    // Spotify login already uses; storage stays — Google's continuity signals
                    // keep CAPTCHAs away, only cookies reset) and load only when it is clean.
                    resetAuthWebViewSession(
                        context = webViewContext,
                        webView = this,
                        clearCookies = true,
                        clearStorage = false,
                    ) {
                        // Full YouTube handshake URL, not a bare continue (#182 — see
                        // youTubeServiceLoginUrl).
                        loadUrl(youTubeServiceLoginUrl())
                    }
                }
            },
        )
    }

    // #190: the jar watcher — the completion no longer depends on WHERE the WebView is stuck.
    // The owner's stable showed every variant: the chain wedges on a blank page after the
    // password, the URL never reaches music.youtube.com, and the #189 interstitial push did
    // not help because the wedge can sit BEFORE the YouTube leg (no .youtube.com cookie at
    // all). So the screen now watches the cookie jar itself, which is the truth:
    //  - .youtube.com SAPISID present -> the session InnerTube needs is minted: complete the
    //    login and return to the interface right away, white screen or not.
    //  - .google.com SAPISID present but no YouTube one for HANDSHAKE_RESCUE_GRACE_MS -> the
    //    password was accepted but the chain died mid-flight: re-load the handshake URL so
    //    Google fast-forwards passively through its own session — the exact flow the account
    //    picker triggers, which the owner confirmed works on the S26 Ultra. Bounded attempts.
    // Cancelled automatically when the screen leaves composition; the HALLAZGO-061 disposal
    // net below still catches a session minted in the very last instant.
    LaunchedEffect(Unit) {
        var rescueCount = 0
        var googleSessionSince = 0L
        // Owner report 2026-09-04: the rescue must only act on a session minted AFTER this
        // screen opened. The jar is cleared on open (see the WebView factory), but the clear
        // completes asynchronously — so "armed" means we have SEEN the jar without a Google
        // session at least once. Until then a residue cookie can only age out silently, never
        // trigger a reload; once armed, any .google.com SAPISID is provably fresh (#190 + flicker).
        var rescueArmed = false
        while (true) {
            delay(JAR_WATCH_INTERVAL_MS)
            if (hasCompletedLoginState.value) break
            val youTubeCookie = runCatching {
                CookieManager.getInstance().getCookie("https://www.youtube.com")
            }.getOrNull()
            // #190: a .youtube.com session can mint while the page is stuck anywhere in the
            // chain — complete from the jar, do not wait for the URL to reach music.
            tryCompleteLoginWithCookie(youTubeCookie)
            if (hasCompletedLoginState.value) break
            val googleCookie = runCatching {
                CookieManager.getInstance().getCookie("https://accounts.google.com")
            }.getOrNull()
            if (!isLoggedCookie(googleCookie)) rescueArmed = true
            if (shouldRescueHandshakeArmed(rescueArmed, googleCookie, youTubeCookie)) {
                val now = SystemClock.elapsedRealtime()
                if (googleSessionSince == 0L) {
                    googleSessionSince = now
                } else if (now - googleSessionSince >= HANDSHAKE_RESCUE_GRACE_MS) {
                    if (rescueCount < MAX_HANDSHAKE_RESCUES) {
                        rescueCount++
                        // INFO, not DEBUG (audit 2026-09-05): the rescue RELOADS the page — what
                        // the owner sees as "parpadea como loco y desaparece la página". AppLogger
                        // only persists INFO+, so at debug level the flicker left NO trace in the
                        // shared app.log and the cause was indistinguishable from a server-side
                        // redirect loop. This is the single most valuable line for remote diagnosis.
                        Timber.i("Login: handshake rescue reloading the page (#190 flicker trace, rescue $rescueCount/$MAX_HANDSHAKE_RESCUES)")
                        webViewRef?.loadUrl(youTubeServiceLoginUrl())
                        googleSessionSince = 0L
                    } else {
                        Timber.i("Login: rescue budget exhausted, leaving the screen to the owner (#190)")
                        break
                    }
                }
            } else {
                googleSessionSince = 0L
            }
        }
    }

    // HALLAZGO-061 safety net: the user can leave BEFORE onPageFinished ever matches the logged-in
    // page (back press while the post-login redirect still loads). If the WebView cookie already
    // holds a session that was neither completed nor persisted, finish the login from here on the
    // application scope — no navigation callback, there is no UI left to update. Skipped when the
    // persisted cookie is already a logged session (revisiting the screen while logged in).
    DisposableEffect(Unit) {
        onDispose {
            if (!hasCompletedLoginState.value && !isLoggedCookie(innerTubeCookieState.value)) {
                val webCookie = runCatching {
                    CookieManager.getInstance().getCookie("https://music.youtube.com")
                }.getOrNull()
                if (isLoggedCookie(webCookie)) {
                    Timber.d("Login screen disposed with an uncompleted session, finishing on the application scope (HALLAZGO-061)")
                    completeLogin(context, syncUtils, webCookie!!, visitorDataState, dataSyncIdState, null)
                }
            }
        }
    }

    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }
}

/**
 * BETA-043: last-resort "manual cookie" login, ported from SimpMusic's
 * `DevLogInBottomSheet` (commit maxrave-dev/SimpMusic). Bypasses the WebView entirely —
 * the owner pastes the YouTube `Cookie` header they exported from their browser's
 * DevTools (Application ▸ Cookies ▸ https://music.youtube.com). The string is fed to the
 * same `tryCompleteLoginWithCookie` helper the WebView capture uses, so the rest of the
 * pipeline (InnerTubeCookieKey persistence + `completeLogin` validation) is unchanged.
 * Values only, no user data, regola 4 AGENTS — the owner is in control of the input.
 */
@Composable
private fun ManualCookieDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_manual_cookie_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.login_manual_cookie_body),
                    color = AuraPalette.OnGroundMuted,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text("VISITOR_INFO1_LIVE=...; SID=...; HSID=...; SSID=...; APISID=...; SAPISID=...; LOGIN_INFO=...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onSubmit(value) },
                enabled = value.isNotBlank(),
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
