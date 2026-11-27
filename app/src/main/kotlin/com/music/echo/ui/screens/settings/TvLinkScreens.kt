package iad1tya.echo.music.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalSyncUtils
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.DataSyncIdKey
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.VisitorDataKey
import iad1tya.echo.music.tvlink.TvLinkCode
import iad1tya.echo.music.tvlink.TvLinkKeys
import iad1tya.echo.music.tvlink.TvLinkRelay
import iad1tya.echo.music.tvlink.TvLinkSession
import iad1tya.echo.music.ui.component.AuraTopAppBar
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.QrImage
import iad1tya.echo.music.ui.screens.completeLogin
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.isLoggedCookie
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Registry row 907 — Ajustes ▸ Cuentas ▸ «Iniciar sesión con el móvil» (on the TV, or on any device without a Google session).
 * Shows a one-use code as a QR + text, waits on ONE socket for the phone's sealed session, then signs in through the SAME
 * `completeLogin` the normal login uses (cookie + visitorData + dataSyncId → validation → InnerTubeCookieKey). No ticker: one
 * timer per code closes the socket when it expires.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvLinkReceiveScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val syncUtils = LocalSyncUtils.current
    // Not saved across a rotation on purpose: the key pair below lives only in memory, so a recreated screen shows a new code.
    var code by remember { mutableStateOf(TvLinkCode.generate()) }
    val keys = remember(code) { TvLinkKeys.generate() }
    val receiver = remember { TvLinkRelay.Receiver() }
    val state by receiver.state.collectAsState()
    val device = remember { TvLinkRelay.thisDevice() }
    val (ownVisitorData) = rememberPreference(VisitorDataKey, "")
    // null = not tried yet; set once completeLogin answers for the session that arrived with [code].
    var signInOk by remember(code) { mutableStateOf<Boolean?>(null) }
    var importing by remember(code) { mutableStateOf(false) }

    DisposableEffect(code) {
        receiver.start(code, device, keys)
        onDispose { receiver.stop() }
    }
    LaunchedEffect(code) {
        delay(TvLinkCode.TTL_MS + 5_000)
        receiver.expire()
    }
    val received = state as? TvLinkRelay.Receiver.State.Received
    LaunchedEffect(received) {
        val session = received?.session ?: return@LaunchedEffect
        if (importing || signInOk != null) return@LaunchedEffect
        importing = true
        receiver.stop()
        completeLogin(
            context,
            syncUtils,
            session.cookie,
            { session.visitorData.ifEmpty { ownVisitorData } },
            { session.dataSyncId },
        ) { ok ->
            importing = false
            signInOk = ok
            if (ok) {
                Toast.makeText(context, context.getString(R.string.tvlink_signed_in, session.fromDevice), Toast.LENGTH_LONG).show()
                // The completion also finishes after the user left on their own (it runs on the application scope): only
                // navigate while this screen is still on top, as LoginScreen does.
                if (navController.currentDestination?.route == "settings/accounts/tv_link_receive") navController.navigateUp()
            }
        }
    }

    TvLinkScaffold(navController, scrollBehavior, stringResource(R.string.tvlink_receive_title)) {
        Text(stringResource(R.string.tvlink_receive_howto), style = MaterialTheme.typography.bodyMedium)
        QrImage(text = TvLinkCode.link(code), size = 240.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            TvLinkCode.pretty(code),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        val status = when {
            signInOk == false -> stringResource(R.string.tvlink_sign_in_failed)
            received != null -> stringResource(R.string.tvlink_signing_in, received.session.fromDevice)
            else -> when (state) {
                TvLinkRelay.Receiver.State.Connecting -> stringResource(R.string.tvlink_connecting)
                is TvLinkRelay.Receiver.State.Waiting -> stringResource(R.string.tvlink_waiting)
                TvLinkRelay.Receiver.State.Expired -> stringResource(R.string.tvlink_expired)
                TvLinkRelay.Receiver.State.AlreadyUsed -> stringResource(R.string.tvlink_used)
                TvLinkRelay.Receiver.State.Invalid -> stringResource(R.string.tvlink_sign_in_failed)
                TvLinkRelay.Receiver.State.Disconnected -> stringResource(R.string.tvlink_disconnected)
                is TvLinkRelay.Receiver.State.Received -> ""
            }
        }
        Text(status, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        val ended = signInOk == false || state is TvLinkRelay.Receiver.State.Expired ||
            state is TvLinkRelay.Receiver.State.AlreadyUsed || state is TvLinkRelay.Receiver.State.Invalid
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state is TvLinkRelay.Receiver.State.Disconnected) {
                Button(onClick = { receiver.start(code, device, keys) }) { Text(stringResource(R.string.tvlink_reconnect)) }
            }
            if (ended || state is TvLinkRelay.Receiver.State.Disconnected) {
                OutlinedButton(onClick = { code = TvLinkCode.generate() }) { Text(stringResource(R.string.tvlink_new_code)) }
            }
        }
        Text(
            stringResource(R.string.tvlink_receive_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private sealed interface SendPhase {
    data object Idle : SendPhase
    data object Checking : SendPhase
    class Confirm(val code: String, val device: String, val tvKey: ByteArray) : SendPhase
    data class Sending(val code: String, val device: String) : SendPhase
    data class Sent(val device: String, val delivered: Boolean) : SendPhase
    data class Failed(val why: TvLinkRelay.Failure) : SendPhase
}

/**
 * Registry row 907 — Ajustes ▸ Cuentas ▸ «Iniciar sesión en la TV» (signed in only), also opened by the TV's QR
 * (`echomusic://tvlogin?c=…`, MainActivity). The code is typed (or arrives from the QR); the phone first opens the TV's sealed
 * hello and asks, naming that device, before anything is sent. Nothing is ever sent on its own: only «Enviar sesión» does it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvLinkSendScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    initialCode: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf(initialCode?.let(TvLinkCode::normalize)?.let(TvLinkCode::pretty).orEmpty()) }
    var phase by remember { mutableStateOf<SendPhase>(SendPhase.Idle) }
    val (cookie) = rememberPreference(InnerTubeCookieKey, "")
    val signedIn = isLoggedCookie(cookie)

    fun check() {
        val normalized = TvLinkCode.normalize(input) ?: return
        phase = SendPhase.Checking
        scope.launch {
            phase = when (val r = TvLinkRelay.helloOf(normalized)) {
                is TvLinkRelay.Outcome.Ok -> SendPhase.Confirm(normalized, r.value.device, r.value.publicKey)
                is TvLinkRelay.Outcome.Failed -> SendPhase.Failed(r.why)
            }
        }
    }

    // A QR opened from the camera fills the code and asks straight away (it still waits for «Enviar sesión»).
    LaunchedEffect(Unit) { if (signedIn && TvLinkCode.normalize(input) != null && initialCode != null) check() }

    TvLinkScaffold(navController, scrollBehavior, stringResource(R.string.tvlink_send_title)) {
        if (!signedIn) {
            Text(stringResource(R.string.sync_login_required), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { navController.navigate("login") }) { Text(stringResource(R.string.login)) }
        } else {
            Text(stringResource(R.string.tvlink_send_howto), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = TvLinkCode.typing(it)
                    if (phase !is SendPhase.Sending) phase = SendPhase.Idle
                },
                label = { Text(stringResource(R.string.tvlink_code_label)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            when (val p = phase) {
                SendPhase.Idle -> Button(onClick = { check() }, enabled = TvLinkCode.normalize(input) != null) {
                    Text(stringResource(R.string.tvlink_continue))
                }
                SendPhase.Checking -> Text(stringResource(R.string.tvlink_checking))
                is SendPhase.Confirm -> {
                    Text(stringResource(R.string.tvlink_confirm, p.device), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            phase = SendPhase.Sending(p.code, p.device)
                            scope.launch {
                                val prefs = context.dataStore.data.first()
                                val session = TvLinkSession(
                                    cookie = prefs[InnerTubeCookieKey].orEmpty(),
                                    visitorData = prefs[VisitorDataKey].orEmpty(),
                                    dataSyncId = prefs[DataSyncIdKey].orEmpty(),
                                    fromDevice = TvLinkRelay.thisDevice(),
                                )
                                phase = when (val r = TvLinkRelay.send(p.code, p.tvKey, session)) {
                                    is TvLinkRelay.Outcome.Ok -> SendPhase.Sent(p.device, r.value)
                                    is TvLinkRelay.Outcome.Failed -> SendPhase.Failed(r.why)
                                }
                            }
                        }) { Text(stringResource(R.string.tvlink_send)) }
                        OutlinedButton(onClick = { phase = SendPhase.Idle }) { Text(stringResource(android.R.string.cancel)) }
                    }
                }
                is SendPhase.Sending -> Text(stringResource(R.string.tvlink_checking))
                is SendPhase.Sent -> Text(
                    stringResource(if (p.delivered) R.string.tvlink_sent else R.string.tvlink_sent_later, p.device),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                is SendPhase.Failed -> {
                    Text(
                        stringResource(
                            when (p.why) {
                                TvLinkRelay.Failure.NO_LINK -> R.string.tvlink_err_no_link
                                TvLinkRelay.Failure.WRONG_CODE -> R.string.tvlink_err_wrong_code
                                TvLinkRelay.Failure.ALREADY_USED -> R.string.tvlink_err_used
                                TvLinkRelay.Failure.TOO_BIG, TvLinkRelay.Failure.NETWORK -> R.string.tvlink_err_network
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { check() }, enabled = TvLinkCode.normalize(input) != null) {
                        Text(stringResource(R.string.tvlink_continue))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TvLinkScaffold(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            AuraTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }, onLongClick = null) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
