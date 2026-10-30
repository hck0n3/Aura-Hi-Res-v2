package iad1tya.echo.music.ui.newui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.devicesync.DeviceSyncHandoffKey
import iad1tya.echo.music.devicesync.DeviceSyncHub
import iad1tya.echo.music.devicesync.NowState
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The other device, on this one — like Spotify Connect (rows 533 / 534).
 *
 * 533 (owner 2026-10-30: *«me voy a la tablet… no veo que el reproductor refleje lo mismo para continuar»*): the engine received what
 * the other device plays, but only a sheet behind a button read it, and a device with an empty queue has no player at all. This card
 * slides in at the top of the window, on any screen, when ANOTHER paired device is playing and THIS one is silent.
 *
 * 534 (owner 2026-10-30: *«quiero que la línea de tiempo y la canción la siga en el otro dispositivo como lo hace Spotify y que pueda
 * controlar al otro»*): it is now a remote. The song and its timeline follow the other device — the position is the one it published,
 * moved on by the time that has passed, so the bar runs smoothly with no traffic between heartbeats — and play / pause, previous, next
 * and a drag on the bar are sent to THAT device. «Continuar aquí» brings the playback to this one from the same second.
 *
 * Quiet by design: only a state published in the last [FRESH_MS] counts while it plays (a phone that died mid-song stops
 * heartbeating, so its card goes away); a PAUSED remote stays while you are controlling it (otherwise pausing it from here would
 * make the card vanish, and there would be no way to resume it); the ✕ hides it until that device stops and starts again. The
 * 5-second freshness tick and the 1-second timeline tick run ONLY while the card can be seen (AGENTS rule 7).
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
    var lastControlAt by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(0L) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val state = remote
    val other = state != null && state.deviceId != thisId && handoffOn &&
        connection is DeviceSyncHub.Connection.Connected && !localPlaying
    // `tick >= 0` only reads the state so the 5 s tick re-evaluates how long ago the user last controlled the remote.
    val controlling = tick >= 0L && lastControlAt > 0L && System.currentTimeMillis() - lastControlAt < CONTROL_HOLD_MS
    val candidate = other && state != null && (state.playing || controlling)

    // The remote stopped on its own (not because of us): a later play shows the card again.
    LaunchedEffect(state?.deviceId, state?.playing, controlling) {
        if (state == null || (!state.playing && !controlling)) dismissedFor = null
    }
    LaunchedEffect(candidate) {
        while (candidate) {
            delay(FRESHNESS_TICK_MS)
            tick++
        }
    }
    // A paused remote has no heartbeat to be fresh against: only a playing one must have spoken recently.
    val fresh = state != null && (!state.playing || DeviceSyncHub.serverNow() - state.atMs <= FRESH_MS)
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
            fun send(command: String, positionMs: Long? = null) {
                lastControlAt = System.currentTimeMillis()
                DeviceSyncHub.controller?.control(s.deviceId, command, positionMs)
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AuraPalette.GroundRaised.copy(alpha = 0.97f))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .animateContentSize(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = song.cover.ifBlank { null },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(AuraPalette.OnGround.copy(alpha = 0.08f)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (s.playing) R.string.device_sync_playing_on else R.string.device_sync_paused_on,
                                s.deviceName.ifBlank { "—" },
                            ),
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
                        if (song.artist.isNotBlank()) {
                            Text(
                                text = song.artist,
                                style = AuraType.MiniArtist,
                                color = AuraPalette.OnGroundMuted,
                                maxLines = 1,
                                overflow = AuraDefaultOverflow,
                            )
                        }
                    }
                    AuraIconButton(
                        icon = if (s.playing) AuraIcons.Pause else AuraIcons.Play,
                        contentDescription = stringResource(R.string.device_sync_play_pause),
                        onClick = { send(if (s.playing) "pause" else "play") },
                        size = 26.dp,
                    )
                    IconButton(onClick = {
                        dismissedFor = s.deviceId
                        lastControlAt = 0L
                    }) {
                        Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.close), tint = AuraPalette.OnGroundMuted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                RemoteTimeline(
                    state = s,
                    seekable = expanded,
                    onSeek = { positionMs -> send("seek", positionMs) },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (expanded) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AuraIconButton(
                                icon = AuraIcons.SkipPrevious,
                                contentDescription = stringResource(R.string.device_sync_previous),
                                onClick = { send("prev") },
                                size = 26.dp,
                            )
                            AuraIconButton(
                                icon = AuraIcons.SkipNext,
                                contentDescription = stringResource(R.string.device_sync_next),
                                onClick = { send("next") },
                                size = 26.dp,
                            )
                        }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                    TextButton(onClick = {
                        DeviceSyncHub.controller?.takeOverFrom(s.deviceId)
                        dismissedFor = s.deviceId
                        lastControlAt = 0L
                    }) { Text(stringResource(R.string.device_sync_continue_here), color = AuraPalette.Teal) }
                }
            }
        }
    }
}

/**
 * The other device's timeline: the position it published, moved on by the time that has passed (both on the server's clock, so the two
 * devices' clocks do not matter). Thin line when collapsed; a draggable bar with the times when [seekable]. Only this small composable
 * ticks, once a second, and only while the other device plays.
 */
@Composable
private fun RemoteTimeline(state: NowState, seekable: Boolean, onSeek: (Long) -> Unit) {
    val duration = state.durationMs
    if (duration <= 0L) return
    var position by remember(state) { mutableLongStateOf(state.estimatedPositionMs(DeviceSyncHub.serverNow())) }
    LaunchedEffect(state) {
        position = state.estimatedPositionMs(DeviceSyncHub.serverNow())
        while (state.playing) {
            delay(TIMELINE_TICK_MS)
            position = state.estimatedPositionMs(DeviceSyncHub.serverNow())
        }
    }
    val shown = position.coerceIn(0L, duration)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: (shown.toFloat() / duration)
    if (seekable) {
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = AuraPalette.Teal,
                activeTrackColor = AuraPalette.Teal,
                inactiveTrackColor = AuraPalette.OnGround.copy(alpha = 0.18f),
            ),
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(((dragging ?: 0f) * duration).toLong().takeIf { dragging != null } ?: shown), style = AuraType.MiniArtist, color = AuraPalette.OnGroundMuted)
            Text(clock(duration), style = AuraType.MiniArtist, color = AuraPalette.OnGroundMuted)
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(AuraPalette.OnGround.copy(alpha = 0.18f))) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(AuraPalette.Teal))
        }
    }
}

/** `m:ss` (or `h:mm:ss`). */
internal fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val FRESHNESS_TICK_MS = 5_000L
private const val TIMELINE_TICK_MS = 1_000L

/** After the last order sent from here, a paused remote keeps its card this long, so it can be resumed from here. */
private const val CONTROL_HOLD_MS = 10 * 60_000L

/** The other device heartbeats every 20 s while it plays (see the engine); three missed beats and it is gone. */
internal const val FRESH_MS = 70_000L
