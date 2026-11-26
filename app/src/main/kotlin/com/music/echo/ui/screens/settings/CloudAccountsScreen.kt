package iad1tya.echo.music.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.cloud.CloudAccountStatus
import iad1tya.echo.music.cloud.CloudFailure
import iad1tya.echo.music.cloud.CloudIndexPolicy
import iad1tya.echo.music.cloud.CloudLoginState
import iad1tya.echo.music.cloud.CloudProvider
import iad1tya.echo.music.ui.component.AuraTopAppBar
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.Material3SettingsGroup
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import iad1tya.echo.music.viewmodels.CloudAccountsViewModel

/**
 * Registry row 890 — Ajustes ▸ Cuentas y sincronización ▸ «Nube (OneDrive y Dropbox)». Connect (provider's own sign-in
 * page in a browser tab), see whether each account still answers, disconnect. Read-only: nothing here writes to a cloud.
 * Leaving the page cancels a sign-in in progress (the ViewModel goes with the page).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val viewModel: CloudAccountsViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    var confirmDisconnect by remember { mutableStateOf<CloudProvider?>(null) }

    // rclone's local sign-in page opens once per sign-in; «Abrir de nuevo» opens it again.
    val waiting = ui.login as? CloudLoginState.WaitingForBrowser
    var openedFor by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(waiting?.authUrl) {
        val url = waiting?.authUrl ?: return@LaunchedEffect
        if (openedFor != url.hashCode()) {
            openedFor = url.hashCode()
            openCloudSignInPage(context, url)
        }
    }

    Scaffold(
        topBar = {
            AuraTopAppBar(
                title = { Text(stringResource(R.string.cloud_title)) },
                navigationIcon = {
                    IconButton(onClick = { ActionLog.tap(ActionId.CLOUD_BACK); navController.navigateUp() }, onLongClick = null) {
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.cloud_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            when {
                ui.available == false -> Text(stringResource(R.string.cloud_unavailable), color = MaterialTheme.colorScheme.error)
                ui.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.cloud_checking))
                }
                ui.problem != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cloud_error_generic), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { ActionLog.tap(ActionId.CLOUD_RETRY); viewModel.refresh() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            val canConnect = ui.available == true && !ui.loading && !ui.login.busy
            Material3SettingsGroup(
                items = CloudProvider.entries.map { provider ->
                    cloudRow(
                        provider = provider,
                        status = ui.accounts[provider],
                        canConnect = canConnect,
                        onConnect = {
                            if (provider == CloudProvider.ONEDRIVE) ActionLog.tap(ActionId.CLOUD_CONNECT_ONEDRIVE)
                            else ActionLog.tap(ActionId.CLOUD_CONNECT_DROPBOX)
                            viewModel.connect(provider)
                        },
                        onDisconnect = {
                            if (provider == CloudProvider.ONEDRIVE) ActionLog.tap(ActionId.CLOUD_DISCONNECT_ONEDRIVE)
                            else ActionLog.tap(ActionId.CLOUD_DISCONNECT_DROPBOX)
                            confirmDisconnect = provider
                        },
                    )
                },
            )

            CloudLoginPanel(
                login = ui.login,
                onOpenAgain = { url -> ActionLog.tap(ActionId.CLOUD_LOGIN_REOPEN); openCloudSignInPage(context, url) },
                onCancel = { ActionLog.tap(ActionId.CLOUD_LOGIN_CANCEL); viewModel.cancelLogin() },
                onRetry = { provider -> ActionLog.tap(ActionId.CLOUD_LOGIN_RETRY); viewModel.connect(provider) },
            )

            // Row 891: the music of each account that answers (or could not be checked right now).
            val withMusic = CloudProvider.entries.filter {
                ui.accounts[it] == CloudAccountStatus.CONNECTED || ui.accounts[it] == CloudAccountStatus.UNCHECKED
            }
            if (withMusic.isNotEmpty()) {
                val folderRows = withMusic.map { provider ->
                    cloudFoldersRow(provider, ui.folders[provider].orEmpty().size, enabled = !ui.indexing) {
                        if (provider == CloudProvider.ONEDRIVE) ActionLog.tap(ActionId.CLOUD_FOLDERS_ONEDRIVE)
                        else ActionLog.tap(ActionId.CLOUD_FOLDERS_DROPBOX)
                        viewModel.openFolders(provider)
                    }
                }
                val indexRow = if (withMusic.any { ui.folders[it].orEmpty().isNotEmpty() }) {
                    cloudIndexRow(ui) { ActionLog.tap(ActionId.CLOUD_INDEX_NOW); viewModel.indexNow() }
                } else {
                    null
                }
                Material3SettingsGroup(items = folderRows + listOfNotNull(indexRow))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    ui.browser?.let { browser ->
        CloudFolderDialog(
            browser = browser,
            onOpen = { path -> ActionLog.tap(ActionId.CLOUD_FOLDERS_OPEN); viewModel.browse(path) },
            onUp = { ActionLog.tap(ActionId.CLOUD_FOLDERS_UP); viewModel.browseUp() },
            onToggle = { path -> ActionLog.tap(ActionId.CLOUD_FOLDERS_TOGGLE); viewModel.toggleFolder(path) },
            onSave = { ActionLog.tap(ActionId.CLOUD_FOLDERS_SAVE); viewModel.saveFolders() },
            onDismiss = { ActionLog.tap(ActionId.CLOUD_FOLDERS_CANCEL); viewModel.closeFolders() },
        )
    }

    confirmDisconnect?.let { provider ->
        AlertDialog(
            onDismissRequest = { confirmDisconnect = null },
            title = { Text(stringResource(R.string.cloud_disconnect_title, provider.label)) },
            text = { Text(stringResource(R.string.cloud_disconnect_body)) },
            confirmButton = {
                TextButton(onClick = {
                    ActionLog.tap(ActionId.CLOUD_DISCONNECT_CONFIRM)
                    confirmDisconnect = null
                    viewModel.disconnect(provider)
                }) { Text(stringResource(R.string.cloud_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { ActionLog.tap(ActionId.CLOUD_DISCONNECT_CANCEL); confirmDisconnect = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** One provider: «Conectar …» when there is no account (or it stopped answering), its name and state when there is. */
@Composable
private fun cloudRow(
    provider: CloudProvider,
    status: CloudAccountStatus?,
    canConnect: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
): Material3SettingsItem {
    val connectText = stringResource(
        if (provider == CloudProvider.ONEDRIVE) R.string.cloud_connect_onedrive else R.string.cloud_connect_dropbox,
    )
    val noteText = stringResource(
        if (provider == CloudProvider.ONEDRIVE) R.string.cloud_onedrive_note else R.string.cloud_dropbox_note,
    )
    val disconnectText = stringResource(R.string.cloud_disconnect)
    val disconnectButton: @Composable () -> Unit = {
        OutlinedButton(onClick = onDisconnect, enabled = canConnect) { Text(disconnectText) }
    }
    return when (status) {
        null -> Material3SettingsItem(
            icon = painterResource(R.drawable.cloud),
            title = { Text(connectText) },
            description = { Text(noteText) },
            enabled = canConnect,
            onClick = onConnect,
        )
        CloudAccountStatus.RECONNECT -> {
            val reconnectText = stringResource(R.string.cloud_reconnect)
            Material3SettingsItem(
                icon = painterResource(R.drawable.cloud),
                title = { Text(connectText) },
                description = { Text(reconnectText, color = MaterialTheme.colorScheme.error) },
                trailingContent = disconnectButton,
                enabled = canConnect,
                keepDescriptionLegible = true,
                onClick = onConnect,
            )
        }
        CloudAccountStatus.CONNECTED, CloudAccountStatus.UNCHECKED -> {
            val stateText = stringResource(
                if (status == CloudAccountStatus.CONNECTED) R.string.cloud_connected else R.string.cloud_unchecked,
            )
            Material3SettingsItem(
                icon = painterResource(R.drawable.cloud),
                title = { Text(provider.label) },
                description = { Text(stateText) },
                trailingContent = disconnectButton,
            )
        }
    }
}

/** Row 891: «Elegir carpetas de música» of one account, with how many are chosen. */
@Composable
private fun cloudFoldersRow(provider: CloudProvider, chosen: Int, enabled: Boolean, onClick: () -> Unit): Material3SettingsItem {
    val stateText = if (chosen == 0) stringResource(R.string.cloud_folders_none) else stringResource(R.string.cloud_folders_count, chosen)
    val descText = "${provider.label} · $stateText"
    return Material3SettingsItem(
        icon = painterResource(R.drawable.folder_managed),
        title = { Text(stringResource(R.string.cloud_choose_folders)) },
        description = { Text(descText) },
        enabled = enabled,
        onClick = onClick,
    )
}

/** Row 891: «Actualizar música de la nube», with what the last run found. */
@Composable
private fun cloudIndexRow(ui: CloudAccountsViewModel.Ui, onClick: () -> Unit): Material3SettingsItem {
    val result = ui.indexResult
    val stateText = when {
        ui.indexing -> stringResource(R.string.cloud_index_running)
        ui.indexFailed -> stringResource(R.string.cloud_error_generic)
        result == null -> stringResource(R.string.cloud_index_hint)
        result.complete -> stringResource(R.string.cloud_index_done, result.songs)
        else -> stringResource(R.string.cloud_index_done, result.songs) + " " + stringResource(R.string.cloud_index_partial)
    }
    val progress: (@Composable () -> Unit)? = if (ui.indexing) {
        { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
    } else {
        null
    }
    return Material3SettingsItem(
        icon = painterResource(R.drawable.sync),
        title = { Text(stringResource(R.string.cloud_index_now)) },
        description = { Text(stateText) },
        trailingContent = progress,
        enabled = !ui.indexing,
        onClick = onClick,
    )
}

/**
 * Row 891: browse an account's folders (folders only, the first [CloudIndexPolicy.FOLDER_BROWSER_LIMIT]) and tick the
 * ones with music. Tapping a name opens it; the box ticks it (a ticked folder takes everything under it). «Guardar»
 * saves and indexes.
 */
@Composable
private fun CloudFolderDialog(
    browser: CloudAccountsViewModel.FolderBrowser,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val location = if (browser.path.isEmpty()) browser.provider.label else "${browser.provider.label} / ${browser.path}"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_choose_folders)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(location, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUp, enabled = browser.path.isNotEmpty()) { Text(stringResource(R.string.cloud_folders_up)) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(browser.path) },
                ) {
                    Checkbox(checked = browser.path in browser.chosen, onCheckedChange = { onToggle(browser.path) })
                    Text(
                        stringResource(if (browser.path.isEmpty()) R.string.cloud_folders_whole else R.string.cloud_folders_this),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                val folders = browser.folders
                when {
                    folders == null -> CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(24.dp))
                    browser.failed -> Text(stringResource(R.string.cloud_error_generic), color = MaterialTheme.colorScheme.error)
                    folders.isEmpty() -> Text(stringResource(R.string.cloud_folders_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(folders, key = { it.path }) { folder ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { onOpen(folder.path) },
                            ) {
                                Checkbox(checked = folder.path in browser.chosen, onCheckedChange = { onToggle(folder.path) })
                                Text(folder.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Icon(painterResource(R.drawable.arrow_forward), contentDescription = null)
                            }
                        }
                    }
                }
                if (browser.truncated) {
                    Text(
                        stringResource(R.string.cloud_folders_truncated, CloudIndexPolicy.FOLDER_BROWSER_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CloudLoginPanel(
    login: CloudLoginState,
    onOpenAgain: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: (CloudProvider) -> Unit,
) {
    when (login) {
        CloudLoginState.Idle -> Unit
        is CloudLoginState.Starting, is CloudLoginState.Finishing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(if (login is CloudLoginState.Starting) R.string.cloud_login_starting else R.string.cloud_login_finishing),
                modifier = Modifier.weight(1f),
            )
            if (login is CloudLoginState.Starting) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
        is CloudLoginState.WaitingForBrowser -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cloud_login_waiting))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenAgain(login.authUrl) }) { Text(stringResource(R.string.cloud_login_open_again)) }
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
        is CloudLoginState.Connected -> Text(stringResource(R.string.cloud_login_connected, login.provider.label))
        is CloudLoginState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(failureText(login.reason)), color = MaterialTheme.colorScheme.error)
            if (login.reason != CloudFailure.NO_BINARY) {
                OutlinedButton(onClick = { onRetry(login.provider) }) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

private fun failureText(reason: CloudFailure): Int = when (reason) {
    CloudFailure.NO_BINARY -> R.string.cloud_unavailable
    CloudFailure.NO_NETWORK -> R.string.cloud_error_no_network
    CloudFailure.CANCELLED -> R.string.cloud_error_cancelled
    CloudFailure.TIMEOUT -> R.string.cloud_error_timeout
    CloudFailure.DENIED -> R.string.cloud_error_denied
    CloudFailure.RECONNECT -> R.string.cloud_error_reconnect
    CloudFailure.OTHER -> R.string.cloud_error_generic
}

/** The provider's sign-in, through rclone's local page, in a browser tab (a plain browser if no tab can open). */
private fun openCloudSignInPage(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
        .recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
