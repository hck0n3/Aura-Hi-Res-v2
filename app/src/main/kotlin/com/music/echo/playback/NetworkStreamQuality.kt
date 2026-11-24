package iad1tya.echo.music.playback

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.stringPreferencesKey
import iad1tya.echo.music.utils.PrefsBridge

/**
 * Registry row 811 (owner H6b, 2026-11-24): a streaming quality for Wi-Fi and another for mobile data.
 *
 * Streaming has two real audio tiers today (the per-quality pickers were retired and every stream resolves through the
 * YouTube Opus cascade): the full pick (itag 251, ~160 kbps) and the Data Saver pick (itag 250, ~70 kbps). Each network
 * chooses one. Absent = [Level.HIGH] on both, which is what every stream got before with Data Saver off, so nothing
 * changes on update; Data Saver ON still forces the saver pick everywhere (YTPlayerUtils.findFormat).
 *
 * Read once per stream resolution from the active network's capabilities: no network callback, no polling.
 */
object NetworkStreamQuality {
    val WifiKey = stringPreferencesKey("streamQualityWifi")
    val MobileKey = stringPreferencesKey("streamQualityMobile")

    enum class Level { HIGH, SAVER }

    /** Mobile data, or anything else (Wi-Fi, Ethernet, unknown) — the «Wi-Fi» choice covers every non-cellular network. */
    enum class Network { MOBILE, OTHER }

    fun parse(stored: String?): Level = Level.entries.firstOrNull { it.name == stored } ?: Level.HIGH

    /** Pure: does the network the stream is about to use want the saver pick? [network] is asked only when the two differ. */
    fun wantsSaver(wifi: String?, mobile: String?, network: () -> Network): Boolean {
        val onWifi = parse(wifi)
        val onMobile = parse(mobile)
        if (onWifi == onMobile) return onWifi == Level.SAVER
        return (if (network() == Network.MOBILE) onMobile else onWifi) == Level.SAVER
    }

    fun currentNetwork(connectivityManager: ConnectivityManager): Network = runCatching {
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (caps != null &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ) {
            Network.MOBILE
        } else {
            Network.OTHER
        }
    }.getOrDefault(Network.OTHER)

    /** For a stream about to be resolved now. Before the first preferences snapshot both read as absent = HIGH. */
    fun saverForCurrentNetwork(connectivityManager: ConnectivityManager): Boolean =
        wantsSaver(PrefsBridge.peek(WifiKey), PrefsBridge.peek(MobileKey)) { currentNetwork(connectivityManager) }
}
