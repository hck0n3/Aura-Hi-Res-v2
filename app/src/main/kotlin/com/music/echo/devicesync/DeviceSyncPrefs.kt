package iad1tya.echo.music.devicesync

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Row 528 — device sync settings. The code never leaves the device but as its SHA-256 (see [SyncCode]). */
val DeviceSyncServerKey = stringPreferencesKey("deviceSyncServer")
val DeviceSyncCodeKey = stringPreferencesKey("deviceSyncCode")
val DeviceSyncDeviceIdKey = stringPreferencesKey("deviceSyncDeviceId")
val DeviceSyncDeviceNameKey = stringPreferencesKey("deviceSyncDeviceName")
val DeviceSyncShuffleKey = booleanPreferencesKey("deviceSyncShuffle")
val DeviceSyncHandoffKey = booleanPreferencesKey("deviceSyncHandoff")

/** Row 531 — pair by the license (no code to type). On by default; the typed code, when there is one, wins. */
val DeviceSyncAutoKey = booleanPreferencesKey("deviceSyncAuto")

/** Aura's own sync Worker. The user never has to know it exists; the field in Ajustes is only for someone who runs their own. */
const val DEVICE_SYNC_DEFAULT_SERVER = "https://aura-sync.toberto4000.workers.dev"

/** Row 536 — keep likes, dislikes and playlists equal between the user's devices through the Worker. On by default. */
val DeviceSyncLibraryKey = booleanPreferencesKey("deviceSyncLibrary")
