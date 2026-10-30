package iad1tya.echo.music.ui.screens.settings.integrations

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.devicesync.DeviceSyncAutoKey
import iad1tya.echo.music.devicesync.DeviceSyncCodeKey
import iad1tya.echo.music.devicesync.DeviceSyncDeviceNameKey
import iad1tya.echo.music.devicesync.DeviceSyncHandoffKey
import iad1tya.echo.music.devicesync.DeviceSyncHub
import iad1tya.echo.music.devicesync.DeviceSyncLibraryKey
import iad1tya.echo.music.devicesync.DeviceSyncServerKey
import iad1tya.echo.music.devicesync.DeviceSyncShuffleKey
import iad1tya.echo.music.devicesync.SyncCode
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.IntegrationCard
import iad1tya.echo.music.ui.component.IntegrationCardItem
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.rememberPreference

/**
 * Row 528 — "Sincronizar dispositivos": pairs this device with the user's others through THEIR Worker (docs/worker/sync).
 * The screen only edits preferences; the engine in the playback service watches them and (re)connects on its own, so
 * there is no "connect" button to forget to press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val skin: AuraPanelSkin = rememberAuraPanelSkin()
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.surface

    var server by rememberPreference(DeviceSyncServerKey, "")
    var code by rememberPreference(DeviceSyncCodeKey, "")
    var deviceName by rememberPreference(DeviceSyncDeviceNameKey, "")
    var shuffle by rememberPreference(DeviceSyncShuffleKey, true)
    var handoff by rememberPreference(DeviceSyncHandoffKey, true)
    var auto by rememberPreference(DeviceSyncAutoKey, true)
    var library by rememberPreference(DeviceSyncLibraryKey, true)

    val connection by DeviceSyncHub.connection.collectAsState()
    val devices by DeviceSyncHub.devices.collectAsState()
    val thisId by DeviceSyncHub.thisDeviceId.collectAsState()
    val pairedBy by DeviceSyncHub.pairedBy.collectAsState()

    var showServer by rememberSaveable { mutableStateOf(false) }
    var showName by rememberSaveable { mutableStateOf(false) }
    var showEnterCode by rememberSaveable { mutableStateOf(false) }

    if (showServer) {
        TextFieldDialog(
            title = stringResource(R.string.device_sync_server),
            initial = server,
            label = stringResource(R.string.device_sync_server_hint),
            help = stringResource(R.string.device_sync_server_help),
            onSave = { server = it.trim(); showServer = false },
            onDismiss = { showServer = false },
        )
    }
    if (showName) {
        TextFieldDialog(
            title = stringResource(R.string.device_sync_name),
            initial = deviceName,
            label = stringResource(R.string.device_sync_name),
            help = null,
            onSave = { deviceName = it.trim().take(40); showName = false },
            onDismiss = { showName = false },
        )
    }
    if (showEnterCode) {
        val invalid = stringResource(R.string.device_sync_code_invalid)
        TextFieldDialog(
            title = stringResource(R.string.device_sync_code_enter),
            initial = "",
            label = stringResource(R.string.device_sync_code_dialog_hint),
            help = null,
            onSave = { typed ->
                val normalized = SyncCode.normalize(typed)
                if (normalized == null) {
                    Toast.makeText(context, invalid, Toast.LENGTH_SHORT).show()
                } else {
                    code = normalized
                    showEnterCode = false
                }
            },
            onDismiss = { showEnterCode = false },
        )
    }

    val normalizedCode = SyncCode.normalize(code)
    val statusText = when (val c = connection) {
        DeviceSyncHub.Connection.Off -> stringResource(R.string.device_sync_status_off)
        DeviceSyncHub.Connection.Connecting -> stringResource(R.string.device_sync_status_connecting)
        DeviceSyncHub.Connection.Connected -> stringResource(R.string.device_sync_status_connected, devices.size.coerceAtLeast(1))
        is DeviceSyncHub.Connection.Retrying -> stringResource(R.string.device_sync_status_retrying, c.inSeconds)
    }
    val codeCopied = stringResource(R.string.device_sync_code_copied)
    val pairedText = when (pairedBy) {
        DeviceSyncHub.PairedBy.License -> stringResource(R.string.device_sync_paired_license)
        DeviceSyncHub.PairedBy.Code -> stringResource(R.string.device_sync_paired_code)
        DeviceSyncHub.PairedBy.None ->
            if (auto) stringResource(R.string.device_sync_auto_need_email) else stringResource(R.string.device_sync_paired_none)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
    ) {
        Column(
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.device_sync_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )

                IntegrationCard(
                    title = stringResource(R.string.device_sync_title),
                    items = buildList {
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.sync),
                                title = { Text(stringResource(R.string.device_sync_status)) },
                                description = { Text(statusText) },
                            )
                        )
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.person),
                                title = { Text(stringResource(R.string.device_sync_paired_title)) },
                                description = { Text(pairedText) },
                            )
                        )
                        add(
                            switchItem(
                                title = stringResource(R.string.device_sync_auto),
                                description = stringResource(R.string.device_sync_auto_desc),
                                checked = auto,
                                onChange = { auto = it },
                            )
                        )
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.cloud),
                                title = { Text(stringResource(R.string.device_sync_server)) },
                                description = {
                                    Text(
                                        server.ifBlank { stringResource(R.string.device_sync_server_default) },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = { showServer = true },
                            )
                        )
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.key),
                                title = { Text(stringResource(R.string.device_sync_code)) },
                                description = {
                                    Text(
                                        normalizedCode?.let(SyncCode::pretty)
                                            ?: stringResource(R.string.device_sync_code_none)
                                    )
                                },
                                onClick = if (normalizedCode != null) {
                                    {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("code", SyncCode.pretty(normalizedCode)))
                                        Toast.makeText(context, codeCopied, Toast.LENGTH_SHORT).show()
                                    }
                                } else null,
                            )
                        )
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.add),
                                title = { Text(stringResource(R.string.device_sync_code_create)) },
                                onClick = { code = SyncCode.normalize(SyncCode.generate()).orEmpty() },
                            )
                        )
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.edit),
                                title = { Text(stringResource(R.string.device_sync_code_enter)) },
                                onClick = { showEnterCode = true },
                            )
                        )
                        if (normalizedCode != null) {
                            add(
                                IntegrationCardItem(
                                    icon = painterResource(R.drawable.close),
                                    title = { Text(stringResource(R.string.device_sync_code_remove)) },
                                    onClick = { code = "" },
                                )
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                IntegrationCard(
                    title = stringResource(R.string.settings),
                    items = listOf(
                        IntegrationCardItem(
                            icon = painterResource(R.drawable.person),
                            title = { Text(stringResource(R.string.device_sync_name)) },
                            description = {
                                Text(deviceName.ifBlank { stringResource(R.string.device_sync_name_default) })
                            },
                            onClick = { showName = true },
                        ),
                        switchItem(
                            title = stringResource(R.string.device_sync_shuffle),
                            description = stringResource(R.string.device_sync_shuffle_desc),
                            checked = shuffle,
                            onChange = { shuffle = it },
                        ),
                        switchItem(
                            title = stringResource(R.string.device_sync_handoff),
                            description = stringResource(R.string.device_sync_handoff_desc),
                            checked = handoff,
                            onChange = { handoff = it },
                        ),
                        switchItem(
                            title = stringResource(R.string.device_sync_library),
                            description = stringResource(R.string.device_sync_library_desc),
                            checked = library,
                            onChange = { library = it },
                        ),
                    ),
                )

                if (devices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    IntegrationCard(
                        title = stringResource(R.string.device_sync_devices),
                        items = devices.map { d ->
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.cast),
                                title = { Text(d.name) },
                                description = if (d.id == thisId) {
                                    { Text(stringResource(R.string.device_sync_this_device)) }
                                } else null,
                            )
                        },
                    )
                }

                Text(
                    stringResource(R.string.device_sync_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        iad1tya.echo.music.ui.component.AuraTopAppBar(
            title = { Text(stringResource(R.string.device_sync_title)) },
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
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ground,
                scrolledContainerColor = if (skin.enabled && skin.darkGround)
                    AuraPalette.GroundRaised
                else
                    MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

@Composable
private fun switchItem(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
): IntegrationCardItem = IntegrationCardItem(
    icon = painterResource(R.drawable.done),
    title = { Text(title) },
    description = { Text(description) },
    trailingContent = {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = {
                Icon(
                    painter = painterResource(id = if (checked) R.drawable.check else R.drawable.close),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            },
        )
    },
    onClick = { onChange(!checked) },
)

@Composable
private fun TextFieldDialog(
    title: String,
    initial: String,
    label: String,
    help: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(title) },
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onSave(text) }) { Text(stringResource(android.R.string.ok)) }
        },
    ) {
        if (help != null) {
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(200) },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
