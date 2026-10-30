package iad1tya.echo.music.ui.screens.settings.integrations

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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import iad1tya.echo.music.edge.EDGE_DEFAULT_URL
import iad1tya.echo.music.edge.EDGE_PUBLIC_KEY
import iad1tya.echo.music.edge.EdgeClient
import iad1tya.echo.music.edge.EdgeEnabledKey
import iad1tya.echo.music.edge.EdgePublicKeyKey
import iad1tya.echo.music.edge.EdgeUrlKey
import iad1tya.echo.music.edge.RemoteConfigs
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.IntegrationCard
import iad1tya.echo.music.ui.component.IntegrationCardItem
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.launch

/**
 * Phase A/B of docs/PLAN_WORKER_9_FUNCIONES.md — "Servicios Aura": the one place that shows what the edge Worker is doing and lets the
 * user switch it all off. With no address (the default) nothing here contacts anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraServicesSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val skin: AuraPanelSkin = rememberAuraPanelSkin()
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.surface

    var enabled by rememberPreference(EdgeEnabledKey, true)
    var address by rememberPreference(EdgeUrlKey, "")
    var publicKey by rememberPreference(EdgePublicKeyKey, "")
    var state by rememberSaveable { mutableStateOf(EdgeClient.lastState) }
    var showAddress by rememberSaveable { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    if (showAddress) {
        ServiceTextDialog(
            title = stringResource(R.string.aura_services_address),
            initial = address,
            help = stringResource(R.string.aura_services_address_help),
            onSave = { address = it.trim(); showAddress = false },
            onDismiss = { showAddress = false },
        )
    }
    if (showKey) {
        ServiceTextDialog(
            title = stringResource(R.string.aura_services_key),
            initial = publicKey,
            help = stringResource(R.string.aura_services_key_help),
            onSave = { publicKey = it.trim(); showKey = false },
            onDismiss = { showKey = false },
        )
    }

    val stateText = when (state) {
        EdgeClient.State.Off -> stringResource(R.string.aura_services_state_off)
        EdgeClient.State.NoAddress -> stringResource(R.string.aura_services_state_no_address)
        EdgeClient.State.NoKey -> stringResource(R.string.aura_services_state_no_key)
        EdgeClient.State.NeverChecked -> stringResource(R.string.aura_services_state_never)
        EdgeClient.State.Ok -> stringResource(R.string.aura_services_state_ok)
        EdgeClient.State.Failed -> stringResource(R.string.aura_services_state_failed)
    }
    val diagFailed = stringResource(R.string.aura_services_diag_failed)
    val diagNoAddress = stringResource(R.string.aura_services_diag_no_address)
    val notice = RemoteConfigs.current?.notice

    Box(Modifier.fillMaxSize().background(ground)) {
        Column(
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.aura_services_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                IntegrationCard(
                    title = stringResource(R.string.aura_services_title),
                    items = buildList {
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.done),
                                title = { Text(stringResource(R.string.aura_services_enable)) },
                                description = { Text(stringResource(R.string.aura_services_enable_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = enabled,
                                        onCheckedChange = { enabled = it },
                                        thumbContent = {
                                            Icon(
                                                painter = painterResource(id = if (enabled) R.drawable.check else R.drawable.close),
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                            )
                                        },
                                    )
                                },
                                onClick = { enabled = !enabled },
                            ),
                        )
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.cloud),
                                title = { Text(stringResource(R.string.aura_services_address)) },
                                description = {
                                    Text(
                                        address.ifBlank { EDGE_DEFAULT_URL },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = { showAddress = true },
                            ),
                        )
                        if (EDGE_PUBLIC_KEY.isBlank()) {
                            add(
                                IntegrationCardItem(
                                    icon = painterResource(R.drawable.key),
                                    title = { Text(stringResource(R.string.aura_services_key)) },
                                    description = {
                                        Text(
                                            if (publicKey.isBlank()) stringResource(R.string.aura_services_key_none)
                                            else stringResource(R.string.aura_services_key_set),
                                        )
                                    },
                                    onClick = { showKey = true },
                                ),
                            )
                        }
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.sync),
                                title = { Text(stringResource(R.string.aura_services_status)) },
                                description = { Text(stateText) },
                            ),
                        )
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.sync),
                                title = { Text(stringResource(R.string.aura_services_check_now)) },
                                onClick = { scope.launch { state = EdgeClient.refresh(context) } },
                            ),
                        )
                        add(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.cloud),
                                title = { Text(stringResource(R.string.aura_services_diag_send)) },
                                description = { Text(stringResource(R.string.aura_services_diag_desc)) },
                                onClick = {
                                    scope.launch {
                                        EdgeClient.sendDiagnostics(context).fold(
                                            onSuccess = { code ->
                                                Toast.makeText(context, context.getString(R.string.aura_services_diag_done, code), Toast.LENGTH_LONG).show()
                                            },
                                            onFailure = {
                                                val msg = if (it.message == "no-address") diagNoAddress else diagFailed
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            },
                                        )
                                    }
                                },
                            ),
                        )
                    },
                )
                if (notice != null) {
                    Spacer(Modifier.height(16.dp))
                    IntegrationCard(
                        title = stringResource(R.string.aura_services_notice),
                        items = listOf(
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.info),
                                title = { Text(notice.title.ifBlank { stringResource(R.string.aura_services_notice) }) },
                                description = { Text(notice.text) },
                            ),
                        ),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ServiceTextDialog(
    title: String,
    initial: String,
    help: String,
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
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(400) },
            label = { Text(title) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
