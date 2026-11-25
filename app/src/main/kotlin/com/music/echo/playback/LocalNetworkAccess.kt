package iad1tya.echo.music.playback

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Android 17 local network protection (registry row 864). An app that targets API 37 needs the runtime permission
 * ACCESS_LOCAL_NETWORK (group «Dispositivos cercanos») for any traffic to or from a LAN address, incoming connections
 * included. Aura has two such paths: [CastMediaRelay] (the TV connects to the phone to fetch a video it cannot fetch
 * itself) and the user's own proxy (Ajustes ▸ Contenido ▸ Proxy) when it points at a LAN host, which carries all the
 * YouTube traffic. Device sync and Listen Together go to their internet servers, and the Cast session itself is run by
 * Google Play services. Below Android 17 the permission does not exist and local network access stays open, so nothing
 * is asked there.
 */
internal object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Android 17 (API 37): the first release that enforces the permission, for apps that target it. */
    const val ENFORCED_FROM_SDK = 37

    fun isEnforced(sdkInt: Int): Boolean = sdkInt >= ENFORCED_FROM_SDK

    fun isGranted(context: Context): Boolean =
        !isEnforced(Build.VERSION.SDK_INT) ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * A LAN destination: private IPv4 (10/8, 172.16/12, 192.168/16), link-local (169.254/16, fe80::/10) or IPv6
     * unique-local (fc00::/7). Loopback is the phone itself, not the LAN.
     */
    fun isLanAddress(address: InetAddress): Boolean =
        address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)

    /**
     * Whether the proxy stored as "host:port" (ProxyUrlKey, split the way toInetSocketAddress does) is on the LAN while
     * the permission is missing on Android 17. May resolve DNS: call off the main thread. False below API 37 without
     * resolving anything, and false for a host that does not resolve (the proxy then fails on its own, as before).
     */
    fun proxyNeedsPermission(context: Context, hostPort: String): Boolean {
        if (isGranted(context)) return false
        val host = hostPort.substringBefore(':').trim()
        if (host.isEmpty()) return false
        return runCatching { InetAddress.getAllByName(host).any(::isLanAddress) }.getOrDefault(false)
    }
}
