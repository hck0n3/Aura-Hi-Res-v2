

package iad1tya.echo.music.ui.screens

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import iad1tya.echo.music.utils.isHandshakeInterstitialUrl
import iad1tya.echo.music.utils.isLoggedCookie
import iad1tya.echo.music.utils.isLoginTargetUrl
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.utils.shouldCompleteLogin
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
 * Registry #189: grace window given to the interstitial's own JavaScript redirect before the
 * app pushes the final leg itself. Long enough that a healthy redirect always wins the race
 * (it fires within ~a second of the cookie landing), short enough that the owner never reads
 * the pause as "the app does nothing".
 */
private const val INTERSTITIAL_REDIRECT_GRACE_MS = 3000L

/**
 * Registry #189: total budget of scheduled interstitial checks per WebView. Each check that
 * finds no minted session schedules the next (slow network), so the cap bounds the loop and
 * guarantees the login screen never polls forever on a genuinely dead page.
 */
private const val MAX_INTERSTITIAL_CHECKS = 10

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

    // #182: ONE detection helper, called from BOTH WebView callbacks. Reads/writes go through
    // the State objects (not the delegated vars) so the callbacks always see the latest values.
    // The hasCompletedLogin latch + the process-level CAS inside completeLogin keep it
    // idempotent no matter how many times the callbacks fire.
    fun tryCompleteLoginFromPage(url: String?) {
        if (!isLoginTargetUrl(url)) return
        val pageCookie = CookieManager.getInstance().getCookie(url)
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
                            scheduleFinalLegCheckIfNeeded(view, url)
                            tryCompleteLoginFromPage(url)
                        }

                        // #189: the interstitial (www.youtube.com/signin) is a blank page whose
                        // redirect to music.youtube.com is driven by page JavaScript, and on
                        // some WebView builds that script never fires — the URL never reaches
                        // the login-target detection and the owner stares at the white screen
                        // ("cuando uno inicia sesión la app se queda en blanco"). The session
                        // cookies arrive with the interstitial's response headers, so after a
                        // short grace window a minted jar means the only thing missing is the
                        // redirect itself: push the final leg ourselves. URL-change callbacks
                        // CANNOT be the trigger (a stuck page produces no further URL changes),
                        // so the check is scheduled on the view; if the natural redirect won
                        // the race the check sees a non-interstitial URL and no-ops. Bounded by
                        // [interstitialChecksScheduled] so a genuinely dead page (no session
                        // minted, e.g. still waiting on a slow network) never loops forever.
                        private var interstitialChecksScheduled = 0
                        private fun scheduleFinalLegCheckIfNeeded(view: WebView, url: String?) {
                            if (!isHandshakeInterstitialUrl(url)) return
                            if (interstitialChecksScheduled >= MAX_INTERSTITIAL_CHECKS) return
                            interstitialChecksScheduled++
                            view.postDelayed({
                                // The natural redirect already moved the page on — nothing to do.
                                if (!isHandshakeInterstitialUrl(view.url)) return@postDelayed
                                val jar = runCatching {
                                    CookieManager.getInstance().getCookie("https://www.youtube.com")
                                }.getOrNull()
                                if (!isLoggedCookie(jar)) {
                                    // Session not minted yet (slow network keeps waiting) —
                                    // keep checking while the attempt budget lasts.
                                    scheduleFinalLegCheckIfNeeded(view, view.url)
                                    return@postDelayed
                                }
                                Timber.d("Login: interstitial did not redirect by itself, pushing the final leg to music.youtube.com (#189)")
                                view.loadUrl("https://music.youtube.com")
                            }, INTERSTITIAL_REDIRECT_GRACE_MS)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")
                            // #189: backup scheduling path — if the history callback never saw
                            // the interstitial URL (fragment-only navigation, redirect quirks),
                            // the finished page still arms the final-leg check. Same budget,
                            // same guards; the helper no-ops when already scheduled.
                            scheduleFinalLegCheckIfNeeded(view, url)
                            // Second detection chance: the session cookie can finish flushing
                            // after the last URL change. Same latched helper, so it is safe to
                            // run on every page.
                            tryCompleteLoginFromPage(url)
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
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onRetrieveVisitorData(newVisitorData: String?) {
                            if (newVisitorData != null) {
                                visitorData = newVisitorData
                            }
                        }
                        @JavascriptInterface
                        fun onRetrieveDataSyncId(newDataSyncId: String?) {
                            if (newDataSyncId != null) {
                                dataSyncId = newDataSyncId.substringBefore("||")
                            }
                        }
                    }, "Android")
                    webViewRef = this
                    // Full YouTube handshake URL, not a bare continue (#182 — see
                    // youTubeServiceLoginUrl).
                    loadUrl(youTubeServiceLoginUrl())
                }
            },
        )
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
