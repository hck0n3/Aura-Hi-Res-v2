package iad1tya.echo.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.FidelityModeKey
import iad1tya.echo.music.utils.rememberPreference

/**
 * Row 902 — «Modo Fidelidad» pauses the DSP from the playback side (MusicService + FidelityMode). Every
 * control it pauses says so next to itself instead of silently doing nothing: no hidden placebo.
 */
@Composable
fun rememberFidelityModeOn(): Boolean = rememberPreference(FidelityModeKey, defaultValue = false).value

/** One line under a paused control's description. Renders nothing while the mode is off. */
@Composable
fun FidelityPausedNote(fidelityOn: Boolean) {
    if (!fidelityOn) return
    Text(
        text = stringResource(R.string.fidelity_mode_paused),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** Banner for the equalizer screen: none of its controls is heard while the mode is on. */
@Composable
fun FidelityModeEqBanner() {
    if (!rememberFidelityModeOn()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.fidelity_mode_eq_banner_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.fidelity_mode_eq_banner_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
