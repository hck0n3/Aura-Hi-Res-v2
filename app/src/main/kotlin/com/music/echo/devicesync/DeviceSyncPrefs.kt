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
