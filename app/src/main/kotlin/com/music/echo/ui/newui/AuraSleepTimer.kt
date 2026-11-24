package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.playback.SleepTimerMath
import iad1tya.echo.music.ui.component.SleepTimerDialog
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import kotlinx.coroutines.delay
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

/**
 * The sleep timer in one tap (UX audit P22C, registry row 734: it took ~5 taps, from the player menu's collapsed
 * «Reproducción» section to the dialog). The same service calls as [SleepTimerDialog]; the dialog's own row stays
 * where it was (more lengths, turn off).
 *
 * @param onPicked called after a preset is applied — the menu closes, and the player's chip shows the countdown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AuraSleepTimerPresets(
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = (LocalPlayerConnection.current ?: return).service
    val endsAt by service.sleepTimerEndsAt.collectAsState()
    val afterSong by service.sleepTimerAfterSong.collectAsState()
    AuraGlassSlab(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        ) {
            AuraIconGlyph(AuraIcons.Timer, null, size = 18.dp, tint = AuraPalette.OnGround.copy(alpha = 0.75f))
            AuraTechnicalText(text = stringResource(R.string.sleep_timer).uppercase(), color = AuraPalette.OnGroundMuted)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            SleepTimerMath.PRESET_MINUTES.forEach { minutes ->
                AuraChip(
                    text = pluralStringResource(R.plurals.minute, minutes, minutes),
                    selected = false,
                    glass = true,
                    onClick = {
                        ActionLog.tap(ActionId.SLEEP_START)
                        service.startSleepTimer(minutes)
                        onPicked()
                    },
                )
            }
            AuraChip(
                text = stringResource(R.string.end_of_song),
                selected = afterSong,
                glass = true,
                onClick = {
                    ActionLog.tap(ActionId.SLEEP_END_OF_SONG)
                    service.startSleepTimerAfterSong()
                    onPicked()
                },
            )
            if (endsAt != null || afterSong) {
                AuraChip(
                    text = stringResource(R.string.sleep_timer_turn_off),
                    selected = false,
                    glass = true,
                    onClick = {
                        ActionLog.tap(ActionId.SLEEP_TURN_OFF)
                        service.cancelSleepTimer()
                        onPicked()
                    },
                )
            }
        }
    }
}

/**
 * The full player's sign that the sleep timer is on: «APAGADO · 23:41» (or «· fin de la canción») under the header,
 * only while a timer runs. Tapping it opens [SleepTimerDialog] (turn off, «+15 min», another length).
 *
 * Heat/battery (AGENTS rule 7): the countdown wakes ONCE per second, on the second, and only while [visible] (the
 * player is expanded) and the app is at least STARTED; with the player collapsed or the screen off nothing ticks. The
 * «end of song» mode has no countdown and never ticks.
 */
@Composable
internal fun AuraSleepTimerChip(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val service = (LocalPlayerConnection.current ?: return).service
    val endsAt by service.sleepTimerEndsAt.collectAsState()
    val afterSong by service.sleepTimerAfterSong.collectAsState()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    if (showDialog) SleepTimerDialog(onDismiss = { showDialog = false })
    val deadline = endsAt
    if (deadline == null && !afterSong) return

    var leftMs by remember(deadline) {
        mutableLongStateOf(deadline?.let { (it - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L) } ?: 0L)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(deadline, visible, lifecycle) {
        if (deadline == null || !visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val left = deadline - android.os.SystemClock.elapsedRealtime()
                leftMs = left.coerceAtLeast(0L)
                if (left <= 0L) break
                delay(SleepTimerMath.nextTickDelayMs(left))
            }
        }
    }

    val label = if (deadline != null) {
        stringResource(R.string.sleep_timer_chip_minutes, SleepTimerMath.format(leftMs))
    } else {
        stringResource(R.string.sleep_timer_chip_after_song)
    }
    val description = stringResource(R.string.sleep_timer_chip_cd, label)
    val open = { showDialog = true }
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .tvFocusable(rememberIsTvOrCar(), shape)
            .clip(shape)
            // Inside the sheet's drag like the header's buttons: fires on lift however the thumb rolled (rows 372/396/513),
            // one firing path; TalkBack and the D-pad call the same action.
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    open()
                    true
                }
            }
            .onKeyEvent { event ->
                val select = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
                if (select && event.type == KeyEventType.KeyUp) {
                    open()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .unstealableClick(open)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        AuraIconGlyph(AuraIcons.Timer, null, size = 12.dp, tint = AuraPalette.Teal)
        AuraTechnicalText(text = label.uppercase(), color = AuraPalette.Teal)
    }
}
