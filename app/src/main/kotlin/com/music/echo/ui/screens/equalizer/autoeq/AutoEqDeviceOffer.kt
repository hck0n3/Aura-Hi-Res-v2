package iad1tya.echo.music.ui.screens.equalizer.autoeq

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import iad1tya.echo.music.R
import iad1tya.echo.music.eq.autoeq.AutoEqNameMatcher
import iad1tya.echo.music.eq.autoeq.AutoEqOfflineCatalog
import iad1tya.echo.music.eq.autoeq.AutoEqOfflineEntry
import iad1tya.echo.music.eq.data.EqDeviceProfileStore
import iad1tya.echo.music.ui.component.AuraAlertDialog
import iad1tya.echo.music.ui.component.AuraOutlinedButton
import iad1tya.echo.music.ui.screens.equalizer.axion.AxionEqViewModel
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The current Bluetooth output and its automatic correction: the one assigned, or the catalogue's match for its name. */
data class DeviceAutoEqInfo(
    val output: EqDeviceProfileStore.Output,
    val assigned: AutoEqOfflineEntry?,
    val match: AutoEqNameMatcher.Match?,
    val offered: Boolean,
    val autoApplyOn: Boolean,
)

/**
 * Bumps a counter every time an audio output is added or removed (AudioDeviceCallback: event-driven, nothing polls —
 * AGENTS rule 7). Registering also reports the devices already connected, so the first value arrives at once.
 */
@Composable
fun rememberAudioOutputChanges(): Int {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val am = context.getSystemService<AudioManager>()
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { tick++ }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { tick++ }
        }
        am?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { am?.unregisterAudioDeviceCallback(callback) }
    }
    return tick
}

/** Reads (off the main thread) the current output; null unless it is Bluetooth. Loads the catalogue only to match. */
suspend fun loadDeviceAutoEqInfo(context: Context): DeviceAutoEqInfo? = withContext(Dispatchers.IO) {
    val store = EqDeviceProfileStore
    val output = store.currentOutput(context)
    if (!output.isBluetooth) return@withContext null
    val assigned = store.assignedAutoEqLine(context, output.key)?.let(AutoEqOfflineCatalog::parseLine)
    val match = if (assigned == null) AutoEqNameMatcher.match(output.name, AutoEqOfflineCatalog.load(context)) else null
    DeviceAutoEqInfo(
        output = output,
        assigned = assigned,
        match = match,
        offered = store.wasAutoEqOffered(context, output.key),
        autoApplyOn = store.isAutoApplyEnabled(context),
    )
}

/** Assigns [entry] to the output and switches the correction on now (the EQ too, as applying a profile by hand does). */
private fun acceptDeviceAutoEq(context: Context, viewModel: AxionEqViewModel, outputKey: String, entry: AutoEqOfflineEntry) {
    EqDeviceProfileStore.assignAutoEq(context, outputKey, entry.toLine())
    EqDeviceProfileStore.markAutoEqOffered(context, outputKey)
    viewModel.syncDeviceAutoEq(outputKey, enableEq = true)
}

/**
 * Registry row 915: «Corregir el sonido para <modelo>», offered ONCE per Bluetooth headphone whose name is in the
 * catalogue (accepted or dismissed, it is not asked again; Ajustes ▸ Sonido ▸ Auto-EQ keeps the same choice under
 * «Este auricular»). Hosted once in MainActivity, over whatever screen is showing.
 */
@Composable
fun AutoEqDeviceOfferHost() {
    val context = LocalContext.current
    val changes = rememberAudioOutputChanges()
    var offer by remember { mutableStateOf<DeviceAutoEqInfo?>(null) }
    LaunchedEffect(changes) {
        val store = EqDeviceProfileStore
        val output = withContext(Dispatchers.IO) { store.currentOutput(context) }
        // Cheap checks first: the catalogue is only read for a Bluetooth output that was never offered.
        val worthLooking = output.isBluetooth &&
            withContext(Dispatchers.IO) {
                !store.wasAutoEqOffered(context, output.key) &&
                    store.assignedAutoEqLine(context, output.key) == null &&
                    store.isAutoApplyEnabled(context)
            }
        offer = if (worthLooking) loadDeviceAutoEqInfo(context)?.takeIf { it.match != null } else null
    }
    val current = offer ?: return
    val match = current.match ?: return
    val viewModel: AxionEqViewModel = hiltViewModel()
    val maxBoost = remember { EqDeviceProfileStore.autoEqMaxBoostDb(context).toInt() }
    fun answered() {
        EqDeviceProfileStore.markAutoEqOffered(context, current.output.key)
        offer = null
    }
    AuraAlertDialog(
        onDismissRequest = { ActionLog.tap(ActionId.AUTOEQ_OFFER_DISMISS); answered() },
        title = { Text(stringResource(R.string.autoeq_offer_title, match.model)) },
        text = { Text(stringResource(R.string.autoeq_offer_body, match.entry.source, maxBoost)) },
        confirmButton = {
            TextButton(onClick = {
                ActionLog.tap(ActionId.AUTOEQ_OFFER_ACCEPT)
                acceptDeviceAutoEq(context, viewModel, current.output.key, match.entry)
                answered()
            }) { Text(stringResource(R.string.autoeq_offer_accept)) }
        },
        dismissButton = {
            TextButton(onClick = { ActionLog.tap(ActionId.AUTOEQ_OFFER_DISMISS); answered() }) {
                Text(stringResource(R.string.autoeq_offer_dismiss))
            }
        },
    )
}

/**
 * «Este auricular» in the Auto-EQ screen (registry row 915): the automatic correction of the connected Bluetooth
 * headphone — what is assigned (with «Quitar»), or the catalogue's match (with «Corregir automáticamente», also after the
 * offer was dismissed) — plus the boost cap. Shows nothing unless a Bluetooth output is connected.
 */
@Composable
fun AutoEqDeviceSection(viewModel: AxionEqViewModel) {
    val context = LocalContext.current
    val changes = rememberAudioOutputChanges()
    var refresh by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf<DeviceAutoEqInfo?>(null) }
    var maxBoost by remember { mutableIntStateOf(EqDeviceProfileStore.autoEqMaxBoostDb(context).toInt()) }
    LaunchedEffect(changes, refresh) { info = loadDeviceAutoEqInfo(context) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        info?.let { current ->
            Text(
                stringResource(R.string.autoeq_device_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            val assigned = current.assigned
            val match = current.match
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        assigned != null -> stringResource(
                            R.string.autoeq_device_active,
                            AutoEqNameMatcher.splitVariant(assigned.name).first,
                            assigned.source,
                        )
                        match != null -> stringResource(R.string.autoeq_device_available, match.model, match.entry.source)
                        else -> stringResource(R.string.autoeq_device_none)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                when {
                    assigned != null -> AuraOutlinedButton(onClick = {
                        ActionLog.tap(ActionId.AUTOEQ_DEVICE_REMOVE)
                        EqDeviceProfileStore.assignAutoEq(context, current.output.key, null)
                        viewModel.syncDeviceAutoEq(current.output.key)
                        refresh++
                    }) { Text(stringResource(R.string.autoeq_device_remove)) }
                    match != null -> AuraOutlinedButton(
                        enabled = current.autoApplyOn,
                        onClick = {
                            ActionLog.tap(ActionId.AUTOEQ_DEVICE_APPLY)
                            acceptDeviceAutoEq(context, viewModel, current.output.key, match.entry)
                            refresh++
                        },
                    ) { Text(stringResource(R.string.autoeq_device_apply)) }
                }
            }
            if (!current.autoApplyOn) {
                Text(stringResource(R.string.autoeq_device_auto_apply_off), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Text(stringResource(R.string.autoeq_max_boost), style = MaterialTheme.typography.bodySmall, color = muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 6, 9).forEach { db ->
                    FilterChip(
                        selected = maxBoost == db,
                        onClick = {
                            ActionLog.tap(ActionId.AUTOEQ_MAX_BOOST)
                            maxBoost = db
                            EqDeviceProfileStore.setAutoEqMaxBoostDb(context, db.toFloat())
                            // Re-applies the correction of this headphone with the new cap (only while it is on).
                            viewModel.syncDeviceAutoEq(current.output.key, force = true)
                        },
                        label = { Text(stringResource(R.string.autoeq_max_boost_value, db)) },
                    )
                }
            }
        }
    }
}
