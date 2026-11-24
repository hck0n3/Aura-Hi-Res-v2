package iad1tya.echo.music.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.Material3SettingsItem
import iad1tya.echo.music.utils.OpenLinkServices
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

/**
 * Row 676: the rows of Ajustes ▸ Contenido ▸ Abrir enlaces that say which links Aura opens. YouTube / YouTube Music
 * and song.link are always on; each other service has its own switch (off by default). Every row shows what the
 * system «Abrir de forma predeterminada» page says about its domains, read again when the user comes back from it.
 */
@Composable
fun openLinkServiceItems(): List<Material3SettingsItem> {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val youTubeState = remember(refresh) { OpenLinkServices.linkState(context, OpenLinkServices.YOUTUBE_HOSTS) }
    val songLinkState = remember(refresh) { OpenLinkServices.linkState(context, OpenLinkServices.SONG_LINK_HOSTS) }
    val enabled = remember(refresh) { OpenLinkServices.Service.entries.associateWith { OpenLinkServices.isEnabled(context, it) } }
    val states = remember(refresh) {
        OpenLinkServices.Service.entries.associateWith { OpenLinkServices.linkState(context, it.hosts) }
    }

    val fixed = listOf(
        Material3SettingsItem(
            icon = painterResource(R.drawable.web_link),
            title = { Text(stringResource(R.string.open_links_youtube_title)) },
            description = { Text(linkStateText(youTubeState)) },
        ),
        Material3SettingsItem(
            icon = painterResource(R.drawable.web_link),
            title = { Text(stringResource(R.string.open_links_songlink_title)) },
            description = { Text(linkStateText(songLinkState)) },
        ),
    )
    val services = OpenLinkServices.Service.entries.map { service ->
        val on = enabled[service] == true
        val toggle = { value: Boolean ->
            OpenLinkServices.setEnabled(context, service, value)
            refresh++
        }
        Material3SettingsItem(
            icon = painterResource(R.drawable.web_link),
            title = { Text(stringResource(R.string.open_links_service_title, service.displayName)) },
            description = {
                Text(if (on) linkStateText(states[service] ?: OpenLinkServices.LinkState.UNKNOWN) else stringResource(R.string.open_links_service_off))
            },
            trailingContent = {
                Switch(
                    checked = on,
                    onCheckedChange = { toggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
            onClick = { ActionLog.tap(ActionId.OPENLINKS_SERVICE); toggle(!on) },
        )
    }
    return fixed + services
}

@Composable
private fun linkStateText(state: OpenLinkServices.LinkState): String = stringResource(
    when (state) {
        OpenLinkServices.LinkState.ALL -> R.string.open_links_state_all
        OpenLinkServices.LinkState.SOME -> R.string.open_links_state_some
        OpenLinkServices.LinkState.NONE -> R.string.open_links_state_none
        OpenLinkServices.LinkState.UNKNOWN -> R.string.open_links_state_unknown
    },
)
