package iad1tya.echo.music.ui.screens.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.WelcomeDoneKey
import iad1tya.echo.music.ui.component.AuraOutlinedButton
import iad1tya.echo.music.ui.component.AuraTopAppBar
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.launch

/** Route of «Conecta tu música»; the `onboarding_` prefix keeps the bottom bar away (registry row 494). */
const val WELCOME_CONNECT_ROUTE = "onboarding_connect"

/** Route of «Listo», the welcome's last screen. */
const val WELCOME_DONE_ROUTE = "onboarding_done"

private val OptionShape = RoundedCornerShape(20.dp)

/**
 * Registry row 871 (plan phase 2): «Conecta tu música», after the licence. Every row opens a screen that already existed
 * (nothing new behind them) and every one is optional; «Más tarde» / «Continuar» goes to «Listo». The private cloud is
 * not offered yet: it arrives with plan phase 5 (beta 4), and a row with nothing behind it would be a dead tap.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WelcomeConnectScreen(navController: NavController) {
    // After opening any option the bottom button reads «Continuar» rather than «Más tarde».
    var usedAny by rememberSaveable { mutableStateOf(false) }
    fun open(route: String) {
        usedAny = true
        navController.navigate(route)
    }

    Scaffold(
        topBar = {
            AuraTopAppBar(title = {
                Text(stringResource(R.string.welcome_connect_title), fontWeight = FontWeight.Bold)
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { ActionLog.tap(ActionId.WELCOME_CONNECT_NEXT); navController.navigate(WELCOME_DONE_ROUTE) },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                ) {
                    Text(stringResource(if (usedAny) R.string.welcome_continue else R.string.welcome_later))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.welcome_connect_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConnectOption(
                    icon = R.drawable.login,
                    title = R.string.welcome_connect_google,
                    description = R.string.welcome_connect_google_desc,
                    onClick = { ActionLog.tap(ActionId.WELCOME_CONNECT_GOOGLE); open("settings/ytm_sync?onboarding=true") },
                )
                ConnectOption(
                    icon = R.drawable.library_music,
                    title = R.string.welcome_connect_folder,
                    description = R.string.welcome_connect_folder_desc,
                    onClick = { ActionLog.tap(ActionId.WELCOME_CONNECT_FOLDER); open("local_songs") },
                )
                // Two existing destinations behind one row: the Spotify import and the playlist migration hub.
                ConnectOption(
                    icon = R.drawable.playlist_add,
                    title = R.string.welcome_connect_services,
                    description = R.string.welcome_connect_services_desc,
                    onClick = null,
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AuraOutlinedButton(onClick = {
                            ActionLog.tap(ActionId.WELCOME_CONNECT_SPOTIFY); open("settings/spotify_import?onboarding=true")
                        }) {
                            Icon(painterResource(R.drawable.ic_spotify), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.welcome_connect_spotify))
                        }
                        AuraOutlinedButton(onClick = { ActionLog.tap(ActionId.WELCOME_CONNECT_OTHER); open("migration") }) {
                            Text(stringResource(R.string.welcome_connect_other_service))
                        }
                    }
                }
                ConnectOption(
                    icon = R.drawable.restore,
                    title = R.string.welcome_connect_restore,
                    description = R.string.welcome_connect_restore_desc,
                    onClick = { ActionLog.tap(ActionId.WELCOME_CONNECT_RESTORE); open("settings/backup_restore") },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ConnectOption(
    icon: Int,
    title: Int,
    description: Int,
    onClick: (() -> Unit)?,
    extra: @Composable () -> Unit = {},
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            extra()
        }
    }
    val modifier = Modifier.fillMaxWidth()
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = OptionShape, color = color) { body() }
    } else {
        Surface(modifier = modifier, shape = OptionShape, color = color) { body() }
    }
}

/**
 * Registry row 871: «Listo», the end of the welcome. Reaching it marks the welcome done ([WelcomeDoneKey]) so it is never
 * shown again; «Empezar a escuchar» goes to Inicio, where the welcome tour fires as before (owner: «el tutorial de
 * último», row 209 e).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeDoneScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { context.dataStore.edit { it[WelcomeDoneKey] = true } }
    Scaffold(
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        ActionLog.tap(ActionId.WELCOME_DONE_START)
                        scope.launch {
                            context.dataStore.edit { it[WelcomeDoneKey] = true }
                            navController.navigate(Screens.Home.route) {
                                popUpTo(WELCOME_CONNECT_ROUTE) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                ) { Text(stringResource(R.string.welcome_done_start)) }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.done),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.welcome_done_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_done_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }
}
