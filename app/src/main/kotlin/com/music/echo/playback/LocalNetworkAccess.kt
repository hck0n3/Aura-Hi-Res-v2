package iad1tya.echo.music.playback

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 local network protection (registry row 864). An app that targets API 37 needs the runtime permission
 * ACCESS_LOCAL_NETWORK (group «Dispositivos cercanos») for any traffic to or from a LAN address, incoming connections
 * included. Aura's only such traffic is [CastMediaRelay]: the TV connects to the phone to fetch a video it cannot fetch
 * itself. Device sync and Listen Together go to their internet servers, and the Cast session itself is run by Google Play
 * services. Below Android 17 the permission does not exist and local network access stays open, so nothing is asked there.
 */
internal object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Android 17 (API 37): the first release that enforces the permission, for apps that target it. */
    const val ENFORCED_FROM_SDK = 37

    fun isEnforced(sdkInt: Int): Boolean = sdkInt >= ENFORCED_FROM_SDK

    fun isGranted(context: Context): Boolean =
        !isEnforced(Build.VERSION.SDK_INT) ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}
