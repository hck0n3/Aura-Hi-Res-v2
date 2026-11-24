package iad1tya.echo.music.ui.screens.settings.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.devicesync.DeviceSyncHub
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.IntegrationCard
import iad1tya.echo.music.ui.component.IntegrationCardItem
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

/**
 * Row 807 (owner 2026-11-24, H10) — the removals the library sync's mass-deletion brake held from another device, as counts per kind,
 * with «Aplicar» (remove them here too) and «Descartar» (keep them and put them back on the other devices). Reached from Ajustes ▸
 * Sincronizar dispositivos and from the «Revisar» of the notice shown when the brake holds a batch (MainActivity).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeldRemovalsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val skin: AuraPanelSkin = rememberAuraPanelSkin()
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.surface
    val held by DeviceSyncHub.heldRemovals.collectAsState()

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
            Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.device_sync_held_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (held.isEmpty()) {
                    Text(
                        stringResource(R.string.device_sync_held_none),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    val rows = held.entries.sortedBy { KIND_ORDER.indexOf(it.key).let { i -> if (i < 0) KIND_ORDER.size else i } }
                    IntegrationCard(
                        title = stringResource(R.string.device_sync_held_title),
                        items = rows.map { (kind, count) ->
                            val label = stringResource(kindLabel(kind))
                            IntegrationCardItem(
                                icon = painterResource(R.drawable.delete),
                                title = { Text(label) },
                                description = { Text(count.toString()) },
                            )
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                ActionLog.tap(ActionId.DEVSET_HELD_DISCARD)
                                DeviceSyncHub.controller?.resolveHeldRemovals(apply = false)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.device_sync_held_discard)) }
                        Button(
                            onClick = {
                                ActionLog.tap(ActionId.DEVSET_HELD_APPLY)
                                DeviceSyncHub.controller?.resolveHeldRemovals(apply = true)
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.device_sync_held_apply)) }
                    }
                }
            }
        }

        iad1tya.echo.music.ui.component.AuraTopAppBar(
            title = { Text(stringResource(R.string.device_sync_held_title)) },
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

/** The key families of LibrarySyncCore, in the order the screen lists them. */
private val KIND_ORDER = listOf("lk", "pl", "pm", "ab", "pn", "dk")

private fun kindLabel(kind: String): Int = when (kind) {
    "lk" -> R.string.device_sync_held_kind_likes
    "dk" -> R.string.device_sync_held_kind_dislikes
    "pl" -> R.string.device_sync_held_kind_playlists
    "pm" -> R.string.device_sync_held_kind_members
    "ab" -> R.string.device_sync_held_kind_albums
    "pn" -> R.string.device_sync_held_kind_pins
    else -> R.string.device_sync_held_kind_other
}
