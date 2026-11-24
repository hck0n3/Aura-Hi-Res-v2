package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.playback.OfflinePlaybackNotice
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import iad1tya.echo.music.utils.rememberPreference

/**
 * Registry row 795: the full player's «SIN CONEXIÓN · SONANDO TU MÚSICA SIN CONEXIÓN» under the header, while the player goes
 * on with offline music only (the automatic session or the manual switch, [OfflinePlaybackNotice]). It never blocks anything;
 * a tap opens [AuraOfflinePlaybackDialog]. Read from the service's flow: nothing ticks or polls (AGENTS rule 7).
 * [playsHere] is false while casting or controlling another device (row 537): what is heard then is not this phone's offline
 * music, so the sign is not shown.
 */
@Composable
internal fun AuraOfflinePlaybackChip(playsHere: Boolean, modifier: Modifier = Modifier) {
    val service = (LocalPlayerConnection.current ?: return).service
    val notice by service.offlinePlaybackNotice.collectAsState()
    val shown = notice.visible && playsHere
    var showDialog by rememberSaveable { mutableStateOf(false) }
    // The session ended with the explanation open: it closes, and does not pop up again on the next session.
    LaunchedEffect(shown) { if (!shown) showDialog = false }
    if (showDialog && shown) AuraOfflinePlaybackDialog(notice = notice, onDismiss = { showDialog = false })
    if (!shown) return

    val label = stringResource(R.string.offline_player_chip)
    val description = stringResource(R.string.offline_player_chip_cd)
    val open = {
        ActionLog.tap(ActionId.PLR_OFFLINE_NOTICE)
        showDialog = true
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .tvFocusable(rememberIsTvOrCar(), shape)
            .clip(shape)
            // Inside the sheet's drag like the sleep-timer chip next to it: fires on lift however the thumb rolled
            // (rows 372/396/513), one firing path; TalkBack and the D-pad call the same action.
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
        AuraIconGlyph(AuraIcons.CloudOff, null, size = 12.dp, tint = AuraPalette.Teal)
        AuraTechnicalText(text = label.uppercase(), color = AuraPalette.Teal)
    }
}

/**
 * Row 795: the mini player's short form of the same sign («SIN CONEXIÓN» in the artist line). Not a button of its own: a tap
 * on the pill opens the full player, where the chip explains it. [visible]: the notice is on and this device is the one playing.
 */
@Composable
internal fun AuraMiniOfflineBadge(visible: Boolean) {
    if (!visible) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AuraIconGlyph(AuraIcons.CloudOff, null, size = 11.dp, tint = AuraPalette.Teal)
        AuraTechnicalText(text = stringResource(R.string.offline_player_mini).uppercase(), color = AuraPalette.Teal)
    }
}

/**
 * What the sign means. With the manual switch, «Volver online» turns it off (the user's own switch, row 284); the automatic
 * session has no button here — it ends by itself when the network is back, and the offline home keeps its «Reintentar en línea».
 */
@Composable
private fun AuraOfflinePlaybackDialog(
    notice: OfflinePlaybackNotice,
    onDismiss: () -> Unit,
) {
    var offlineMode by rememberPreference(OfflineModeKey, false)
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.offline_player_notice_title)) },
        buttons = {
            if (notice.offersGoOnline) {
                TextButton(onClick = {
                    ActionLog.tap(ActionId.PLR_OFFLINE_GO_ONLINE)
                    offlineMode = false
                    onDismiss()
                }) { Text(stringResource(R.string.offline_disable)) }
            }
            TextButton(onClick = { ActionLog.tap(ActionId.PLR_OFFLINE_NOTICE_CLOSE); onDismiss() }) { Text(stringResource(R.string.close)) }
        },
    ) {
        Text(
            stringResource(
                if (notice.offersGoOnline) R.string.offline_player_notice_manual else R.string.offline_player_notice_auto,
            ),
        )
    }
}
