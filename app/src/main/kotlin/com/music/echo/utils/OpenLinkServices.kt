package iad1tya.echo.music.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build

/**
 * Row 676 (owner 2026-11-23): by default Aura only opens YouTube, YouTube Music and song.link links. Every other
 * music service has its own `activity-alias` in the manifest, disabled by default, and the user switches it on in
 * Ajustes ▸ Contenido ▸ Abrir enlaces. The alias' enabled state IS the setting (no DataStore key), so it survives
 * updates and cannot drift from what Android actually routes.
 *
 * Android 12+ never lets an app select its own unverified links: once a service is on, the user still has to add
 * its domains in the system «Abrir de forma predeterminada» page. [linkState] reads what the user chose there.
 */
object OpenLinkServices {

    enum class Service(val displayName: String, val aliasClass: String, val hosts: List<String>) {
        SPOTIFY("Spotify", "iad1tya.echo.music.OpenLinksSpotifyAlias", listOf("open.spotify.com", "spotify.link", "spotify.app.link")),
        APPLE_MUSIC("Apple Music", "iad1tya.echo.music.OpenLinksAppleMusicAlias", listOf("music.apple.com", "geo.music.apple.com")),
        DEEZER("Deezer", "iad1tya.echo.music.OpenLinksDeezerAlias", listOf("deezer.com", "www.deezer.com", "link.deezer.com", "deezer.page.link")),
        TIDAL("Tidal", "iad1tya.echo.music.OpenLinksTidalAlias", listOf("tidal.com", "listen.tidal.com")),
        SOUNDCLOUD("SoundCloud", "iad1tya.echo.music.OpenLinksSoundCloudAlias", listOf("soundcloud.com", "m.soundcloud.com", "on.soundcloud.com")),
        AMAZON_MUSIC("Amazon Music", "iad1tya.echo.music.OpenLinksAmazonMusicAlias", listOf("music.amazon.com")),
    }

    /** Always on (declared on MainActivity itself). */
    val YOUTUBE_HOSTS = listOf("music.youtube.com", "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
    val SONG_LINK_HOSTS = listOf("song.link", "album.link", "odesli.co")

    enum class LinkState { ALL, SOME, NONE, UNKNOWN }

    /** [selected]: one entry per host, true when Android opens it with Aura. */
    fun summarize(selected: List<Boolean>, linkHandlingAllowed: Boolean): LinkState {
        if (selected.isEmpty()) return LinkState.UNKNOWN
        if (!linkHandlingAllowed) return LinkState.NONE
        return when (selected.count { it }) {
            0 -> LinkState.NONE
            selected.size -> LinkState.ALL
            else -> LinkState.SOME
        }
    }

    fun isEnabled(context: Context, service: Service): Boolean = runCatching {
        context.packageManager.getComponentEnabledSetting(ComponentName(context, service.aliasClass)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }.getOrDefault(false)

    fun setEnabled(context: Context, service: Service, enabled: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, service.aliasClass),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { timber.log.Timber.w(it, "OpenLinkServices: could not switch %s", service.name) }
    }

    /** What the system «Abrir de forma predeterminada» page says about [hosts]; [LinkState.UNKNOWN] before Android 12. */
    fun linkState(context: Context, hosts: List<String>): LinkState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return LinkState.UNKNOWN
        val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return LinkState.UNKNOWN
        val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }.getOrNull()
            ?: return LinkState.UNKNOWN
        val selected = hosts.map { host ->
            val s = state.hostToStateMap[host]
            s == DomainVerificationUserState.DOMAIN_STATE_SELECTED || s == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
        }
        return summarize(selected, state.isLinkHandlingAllowed)
    }
}
