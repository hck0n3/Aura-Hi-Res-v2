

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
import iad1tya.echo.music.utils.isLoggedCookie
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
    var innerTubeCookie by innerTubeCookieState
    val hasCompletedLoginState = remember { mutableStateOf(false) }
    var hasCompletedLogin by hasCompletedLoginState
    val skin = rememberAuraPanelSkin()

    // Held in state so the account-picker callback can reload it. The WebView still loads the normal
    // ServiceLogin URL by default; the picker only PRE-FILLS the email so the user skips typing it.
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
            val hinted = "https://accounts.google.com/ServiceLogin" +
                "?continue=https%3A%2F%2Fmusic.youtube.com" +
                "&Email=${Uri.encode(email)}&login_hint=${Uri.encode(email)}"
            webViewRef?.loadUrl(hinted)
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
                        override fun onPageFinished(view: WebView, url: String?) {
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                            val pageCookie = if (url?.startsWith("https://music.youtube.com") == true)
                                CookieManager.getInstance().getCookie(url) else null
                            if (shouldCompleteLogin(pageCookie, hasCompletedLogin)) {
                                // Persist the cookie right away (App's cookie watcher picks the
                                // session up live) and latch so this fires once per screen visit.
                                innerTubeCookie = pageCookie!!
                                hasCompletedLogin = true

                                // HALLAZGO-061: completion runs on the application scope — leaving
                                // the screen during validation can no longer cancel it.
                                completeLogin(context, syncUtils, pageCookie, visitorDataState, dataSyncIdState) { ok ->
                                    if (ok) {
                                        webViewRef?.apply {
                                            stopLoading()
                                            clearHistory()
                                            clearCache(true)
                                            clearFormData()
                                        }
                                        // The completion also finishes after the user left on their
                                        // own — only navigate when still ON the login screen.
                                        if (navController.currentDestination?.route == "login") {
                                            navController.navigateUp()
                                        }
                                    } else {
                                        // HALLAZGO-061 fix D: the old failure path was completely
                                        // silent — the user was left staring at a WebView that
                                        // never goes back, with no idea the validation failed.
                                        hasCompletedLogin = false
                                        Toast.makeText(context, R.string.login_validation_failed, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
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
                    loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
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
