package iad1tya.echo.music.ui.newui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.devicesync.DeviceSyncHandoffKey
import iad1tya.echo.music.devicesync.DeviceSyncHub
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * "Continuar aquí" (row 533, owner 2026-10-30: *«estoy reproduciendo… me voy a la tablet que ya está sincronizada y no veo que el
 * reproductor refleje lo mismo para continuar»*). The engine already received what the other device plays, but the only place that
 * showed it was a sheet behind the «Mis dispositivos» button — nothing offered it by itself, and a device with an empty queue has no
 * player to show it in. This card slides in at the top of the window, on any screen, when ANOTHER paired device is playing and THIS
 * one is not, with one button that brings the playback here from the same second.
 *
 * Quiet by design: it only shows for a state the other device published in the last [FRESH_MS] (a phone that died mid-song stops
 * heartbeating, so its card goes away), only while this device is silent, and the ✕ hides it until that device stops and starts again.
 * The 5-second tick that checks freshness runs ONLY while the card could show (AGENTS rule 7).
 */
@Composable
fun AuraHandoffBanner(modifier: Modifier = Modifier) {
    val remote by DeviceSyncHub.remoteNow.collectAsState()
    val thisId by DeviceSyncHub.thisDeviceId.collectAsState()
    val connection by DeviceSyncHub.connection.collectAsState()
    val (handoffOn) = rememberPreference(DeviceSyncHandoffKey, true)
    val playerConnection = LocalPlayerConnection.current
    val localPlaying by remember(playerConnection) { playerConnection?.isPlaying ?: MutableStateFlow(false) }.collectAsState(false)

    var dismissedFor by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableLongStateOf(0L) }

    val state = remote
    val candidate = state != null && state.playing && state.deviceId != thisId && handoffOn &&
        connection is DeviceSyncHub.Connection.Connected && !localPlaying
    // The remote stopped (or came back from silence): a new play shows the card again.
    LaunchedEffect(state?.deviceId, state?.playing) {
        if (state == null || !state.playing) dismissedFor = null
    }
    // Freshness is re-read every 5 s, and only while the card could be on screen.
    LaunchedEffect(candidate) {
        while (candidate) {
            delay(TICK_MS)
            tick++
        }
    }
    // `tick >= 0` only reads the state so the 5 s tick re-evaluates freshness.
    val fresh = state != null && tick >= 0L && DeviceSyncHub.serverNow() - state.atMs <= FRESH_MS
    val visible = candidate && fresh && state != null && dismissedFor != state.deviceId

    Box(modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp, start = 12.dp, end = 12.dp)) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val s = state ?: return@AnimatedVisibility
            val song = s.current ?: return@AnimatedVisibility
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AuraPalette.GroundRaised.copy(alpha = 0.96f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                AsyncImage(
                    model = song.cover.ifBlank { null },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(AuraPalette.OnGround.copy(alpha = 0.08f)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.device_sync_playing_on, s.deviceName.ifBlank { "—" }),
                        style = AuraType.MiniArtist,
                        color = AuraPalette.OnGroundMuted,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                    Text(
                        text = song.title,
                        style = AuraType.RowTitle,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                }
                TextButton(
                    onClick = {
                        DeviceSyncHub.controller?.takeOverFrom(s.deviceId)
                        dismissedFor = s.deviceId
                    },
                ) { Text(stringResource(R.string.device_sync_continue_here), color = AuraPalette.Teal) }
                TextButton(onClick = { dismissedFor = s.deviceId }) {
                    Text(stringResource(R.string.close), color = AuraPalette.OnGroundMuted)
                }
            }
        }
    }
}

private const val TICK_MS = 5_000L

/** The other device heartbeats every 20 s while it plays (see the engine); three missed beats and it is gone. */
internal const val FRESH_MS = 70_000L
