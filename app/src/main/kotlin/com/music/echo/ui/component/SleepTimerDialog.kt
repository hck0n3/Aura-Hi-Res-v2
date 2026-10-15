package iad1tya.echo.music.ui.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import kotlinx.coroutines.delay

private val SLEEP_TIMER_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90, 120)

/**
 * Sleep timer picker (owner 2026-10-13: "el temporizador lo quiero devuelta"). One implementation for the
 * classic player and the Aura player; the timer itself lives in the service (MusicService.startSleepTimer),
 * so it keeps running with the screen off and when this dialog is gone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val service = (LocalPlayerConnection.current ?: return).service
    val endsAt by service.sleepTimerEndsAt.collectAsState()
    val afterSong by service.sleepTimerAfterSong.collectAsState()

    // Remaining time, refreshed once a second ONLY while this dialog is open and a timer is running.
    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(endsAt) {
        val deadline = endsAt ?: return@LaunchedEffect
        while (true) {
            remainingMs = (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (remainingMs <= 0L) break
            delay(1_000L)
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        buttons = {
            if (endsAt != null || afterSong) {
                TextButton(onClick = {
                    service.cancelSleepTimer()
                    onDismiss()
                }) { Text(stringResource(R.string.sleep_timer_turn_off)) }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    ) {
        if (endsAt != null) {
            val totalSeconds = (remainingMs / 1000L).toInt()
            Text(
                stringResource(
                    R.string.sleep_timer_stops_in,
                    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                ),
            )
        } else if (afterSong) {
            Text(stringResource(R.string.sleep_timer_stops_after_song))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SLEEP_TIMER_MINUTES.forEach { minutes ->
                AssistChip(
                    onClick = {
                        service.startSleepTimer(minutes)
                        onDismiss()
                    },
                    label = { Text(pluralStringResource(R.plurals.minute, minutes, minutes)) },
                )
            }
            AssistChip(
                onClick = {
                    service.startSleepTimerAfterSong()
                    onDismiss()
                },
                label = { Text(stringResource(R.string.end_of_song)) },
            )
        }
    }
}
