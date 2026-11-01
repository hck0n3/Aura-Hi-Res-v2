package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import iad1tya.echo.music.ui.component.VolumeSlider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.R
import iad1tya.echo.music.devicesync.DeviceSyncHub

/**
 * "Mis dispositivos" (row 528): the user's other paired devices, what one of them is playing, and the two hand-over moves —
 * take it here / send mine there — plus play-pause and next for the remote one. Everything goes through [DeviceSyncHub]; with no
 * engine paired the sheet just points at the settings page.
 */
@Composable
fun ColumnScope.AuraDevicesSheetContent(
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val devices by DeviceSyncHub.devices.collectAsState()
    val remote by DeviceSyncHub.remoteNow.collectAsState()
    val thisId by DeviceSyncHub.thisDeviceId.collectAsState()
    val others = devices.filter { it.id != thisId }
    val controller = DeviceSyncHub.controller

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SheetHeader(
            title = stringResource(R.string.device_sync_sheet_title),
            subtitle = if (others.isEmpty()) stringResource(R.string.device_sync_sheet_empty) else "",
        )

        val playingElsewhere = remote?.takeIf { it.deviceId != thisId && it.current != null }
        if (playingElsewhere != null) {
            val now = playingElsewhere.current!!
            val where = if (playingElsewhere.playing) R.string.device_sync_playing_on else R.string.device_sync_paused_on
            DownloadSheetRow(
                icon = AuraIcons.Play,
                title = stringResource(R.string.device_sync_take),
                description = stringResource(where, playingElsewhere.deviceName) + " · " + now.title,
                onClick = {
                    controller?.takeOverFrom(playingElsewhere.deviceId)
                    onDismiss()
                },
                enabled = controller != null,
            )
            DownloadSheetRow(
                icon = if (playingElsewhere.playing) AuraIcons.Pause else AuraIcons.Play,
                title = stringResource(R.string.device_sync_play_pause),
                description = playingElsewhere.deviceName,
                onClick = {
                    controller?.control(playingElsewhere.deviceId, if (playingElsewhere.playing) "pause" else "play")
                },
                enabled = controller != null,
            )
            DownloadSheetRow(
                icon = AuraIcons.SkipNext,
                title = stringResource(R.string.device_sync_next),
                description = playingElsewhere.deviceName,
                onClick = { controller?.control(playingElsewhere.deviceId, "next") },
                enabled = controller != null,
            )
            // Row 593 (owner 2026-10-31: «no veo en el menú de volumen cómo controlar el volumen remoto»): the other device's volume, here with
            // its other controls. It sends at most ~8 times a second while dragging and always the last value.
            var remoteVolume by remember(playingElsewhere.deviceId) {
                mutableFloatStateOf(playingElsewhere.volume.takeIf { it >= 0f } ?: 1f)
            }
            val lastSentAt = remember(playingElsewhere.deviceId) { LongArray(1) }
            fun sendVolume() {
                lastSentAt[0] = System.currentTimeMillis()
                controller?.control(playingElsewhere.deviceId, "volume", levelPermille = (remoteVolume * 1000).toInt())
            }
            VolumeSlider(
                value = remoteVolume,
                onValueChange = { v ->
                    remoteVolume = v.coerceIn(0f, 1f)
                    if (System.currentTimeMillis() - lastSentAt[0] >= 120L) sendVolume()
                },
                onValueChangeFinished = { sendVolume() },
                enabled = controller != null,
                accentColor = AuraPalette.Teal,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }

        others.forEach { device ->
            DownloadSheetRow(
                icon = AuraIcons.Cast,
                title = device.name,
                description = stringResource(R.string.device_sync_send),
                onClick = {
                    controller?.sendTo(device.id)
                    onDismiss()
                },
                enabled = controller != null,
            )
        }

        if (others.isEmpty() && playingElsewhere == null) {
            DownloadSheetRow(
                icon = AuraIcons.Settings,
                title = stringResource(R.string.device_sync_sheet_open_settings),
                description = "",
                onClick = {
                    onDismiss()
                    navController.navigate("settings/device_sync")
                },
            )
        }
    }
}
