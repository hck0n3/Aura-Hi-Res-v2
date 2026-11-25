package iad1tya.echo.music.playback

import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 864 — targetSdk 37 (Android 17) blocks traffic to and from LAN addresses unless the app holds
 * ACCESS_LOCAL_NETWORK. Two paths need it: the Cast video relay (the TV connects to the phone) and the user's proxy when
 * it is on the LAN (all YouTube traffic goes through it). The permission is declared for both flavors, asked when a TV
 * is picked and when a LAN proxy is saved or switched on, and the relay is not handed out without it (the video then
 * plays as audio with its notice instead of a URL that only times out on the TV). Source-text checks for the parts that
 * only exist on a device or in the gms source set, which these JVM tests do not compile.
 */
class LocalNetworkPermissionGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String): String {
        val f = File(root, path)
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    @Test
    fun `the permission name and the first enforcing release are the platform ones`() {
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LocalNetworkAccess.PERMISSION)
        assertEquals(37, LocalNetworkAccess.ENFORCED_FROM_SDK)
        assertFalse(LocalNetworkAccess.isEnforced(36))
        assertTrue(LocalNetworkAccess.isEnforced(37))
        assertTrue(LocalNetworkAccess.isEnforced(38))
    }

    @Test
    fun `the shared manifest declares the permission for both flavors`() {
        assertTrue(
            "the FOSS build needs it too, for a LAN proxy",
            read("app/src/main/AndroidManifest.xml")
                .contains("<uses-permission android:name=\"android.permission.ACCESS_LOCAL_NETWORK\" />"),
        )
    }

    @Test
    fun `LAN addresses are told apart from internet and loopback ones`() {
        fun lan(literal: String) = LocalNetworkAccess.isLanAddress(InetAddress.getByName(literal))
        listOf("192.168.1.10", "10.0.0.2", "172.16.5.4", "172.31.255.1", "169.254.3.3", "fe80::1", "fd12:3456::1", "fc00::1")
            .forEach { assertTrue("$it is on the LAN", lan(it)) }
        listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "127.0.0.1", "::1", "2001:4860:4860::8888")
            .forEach { assertFalse("$it is not on the LAN", lan(it)) }
    }

    @Test
    fun `a LAN proxy is checked at start and asked for when saved or switched on`() {
        val app = read("app/src/main/kotlin/com/music/echo/App.kt")
        val proxySet = app.indexOf("YouTube.proxy = Proxy(type, it.toInetSocketAddress())")
        assertTrue("the proxy is no longer installed in App.kt", proxySet >= 0)
        val check = app.indexOf("LocalNetworkAccess.proxyNeedsPermission(this@App, it)", proxySet)
        assertTrue("the start-up LAN proxy check is gone", check > proxySet)
        assertTrue(app.contains("Timber.w(\"PROXY_LAN_BLOCKED local network permission not granted\")"))

        val settings = read("app/src/main/kotlin/com/music/echo/ui/screens/settings/ContentSettings.kt")
        assertTrue(settings.contains("rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())"))
        assertTrue(settings.contains("withContext(Dispatchers.IO) { LocalNetworkAccess.proxyNeedsPermission(context, hostPort) }"))
        assertTrue(settings.contains("runCatching { localNetworkLauncher.launch(LocalNetworkAccess.PERMISSION) }"))
        val save = settings.indexOf("onProxyUrlChange(tempProxyUrl)")
        assertTrue("saving the proxy no longer asks", settings.indexOf("askLocalNetworkForProxy(tempProxyUrl)", save) > save)
        assertTrue(settings.contains("onCheckedChange = { onProxyEnabledChange(it); if (it) askLocalNetworkForProxy(proxyUrl) },"))
        assertTrue(settings.contains("onClick = { onProxyEnabledChange(!proxyEnabled); if (!proxyEnabled) askLocalNetworkForProxy(proxyUrl) }"))
    }

    @Test
    fun `a LAN proxy blocked at start is told to the user, not only logged`() {
        val app = read("app/src/main/kotlin/com/music/echo/App.kt")
        val check = app.indexOf("LocalNetworkAccess.proxyNeedsPermission(this@App, it)")
        val warn = app.indexOf("Timber.w(\"PROXY_LAN_BLOCKED local network permission not granted\")", check)
        val notice = app.indexOf("ActionNotices.postWhenShown(", check)
        assertTrue("the start-up check no longer posts the notice", warn > check && notice > warn)
        assertTrue(
            app.substring(notice, app.indexOf(')', notice) + 1)
                .contains("ActionNotices.Kind.LAN_PROXY_NEEDS_PERMISSION"),
        )
        // Its button opens Ajustes ▸ Contenido, where the Proxy group shows the permission line.
        assertEquals("settings/content", ActionNotices.Kind.LAN_PROXY_NEEDS_PERMISSION.route)
    }

    @Test
    fun `the start-up notice waits for the first screen instead of being dropped`() = runBlocking {
        val poster = launch(start = CoroutineStart.UNDISPATCHED) {
            ActionNotices.postWhenShown(ActionNotices.Kind.LAN_PROXY_NEEDS_PERMISSION)
        }
        assertTrue("no screen yet: the notice must wait", poster.isActive)
        val got = CompletableDeferred<ActionNotices.Kind>()
        val screen = launch(start = CoroutineStart.UNDISPATCHED) { ActionNotices.events.collect { got.complete(it) } }
        assertEquals(ActionNotices.Kind.LAN_PROXY_NEEDS_PERMISSION, withTimeout(1_000) { got.await() })
        withTimeout(1_000) { poster.join() }
        screen.cancelAndJoin()
    }

    @Test
    fun `the proxy row explains a missing permission and its tap asks for it or opens the app settings`() {
        val settings = read("app/src/main/kotlin/com/music/echo/ui/screens/settings/ContentSettings.kt")
        // Re-checked when the proxy changes and on resume (back from the system dialog or settings page), not polled.
        assertTrue(settings.contains("produceState(false, proxyEnabled, proxyUrl, proxyLanRecheck)"))
        assertTrue(settings.contains("if (event == Lifecycle.Event.ON_RESUME) proxyLanRecheck++"))
        val toggle = settings.indexOf("title = { Text(stringResource(R.string.enable_proxy)) },")
        val line = settings.indexOf("if (proxyEnabled && proxyNeedsLocalNetwork) {", toggle)
        val config = settings.indexOf("title = { Text(stringResource(R.string.config_proxy)) },", toggle)
        assertTrue("the permission line must sit right under the proxy switch", line in (toggle + 1) until config)
        val row = settings.substring(line, config)
        assertTrue(row.contains("R.string.proxy_local_network_needed"))
        assertTrue(row.contains("runCatching { proxyLanTapLauncher.launch(LocalNetworkAccess.PERMISSION) }"))
        val launcher = settings.substringAfter("val proxyLanTapLauncher = rememberLauncherForActivityResult(")
            .substringBefore("Material3SettingsGroup(")
        assertTrue(launcher.contains("if (!granted && !lanRationaleBeforeAsk && !rationaleNow) {"))
        assertTrue(launcher.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))

        val es = read("app/src/main/res/values-es/echo_strings.xml")
        assertTrue(
            es.contains(
                "<string name=\"proxy_local_network_needed\">Android pide permiso de red local para usar este proxy. " +
                    "Toca para darlo.</string>",
            ),
        )
        assertTrue(es.contains("<string name=\"proxy_local_network_notice\">"))
        val en = read("app/src/main/res/values/echo_strings.xml")
        assertTrue(en.contains("<string name=\"proxy_local_network_needed\">"))
        assertTrue(en.contains("<string name=\"proxy_local_network_notice\">"))
        assertTrue(read("app/src/main/res/values-es/notice_actions_strings.xml").contains("<string name=\"notice_action_view_proxy\">Ver proxy</string>"))
        assertTrue(read("app/src/main/res/values/notice_actions_strings.xml").contains("<string name=\"notice_action_view_proxy\">View proxy</string>"))
    }

    @Test
    fun `the relay is never handed out without local network access`() {
        val handler = read("app/src/gms/kotlin/com/music/echo/playback/CastConnectionHandler.kt")
        val start = handler.indexOf("private fun relayFor(url: String): String? {")
        assertTrue("relayFor not found", start >= 0)
        val gate = handler.indexOf("if (!LocalNetworkAccess.isGranted(context)) {", start)
        val relayBuilt = handler.indexOf("CastMediaRelay(musicService.videoOkHttpClient, address)", start)
        assertTrue("the permission gate is gone from relayFor", gate > start)
        assertTrue("the gate must come before the relay is built", relayBuilt > gate)
    }

    @Test
    fun `granting local network access puts a video lost to it back on video`() {
        val handler = read("app/src/gms/kotlin/com/music/echo/playback/CastConnectionHandler.kt")
        val relay = handler.indexOf("private fun relayFor(url: String): String? {")
        assertTrue(
            "relayFor no longer records why it refused",
            handler.indexOf("relayRefusedForPermission = true", relay) > relay,
        )
        assertTrue(handler.contains("val lackedLocalNetwork = videoFellBack && relayRefusedForPermission"))
        // Granted while the load was being built: keep Video and replay the mode check instead of leaving video mode.
        val duringLoad = handler.indexOf("if (lackedLocalNetwork && LocalNetworkAccess.isGranted(context)) {")
        assertTrue("the granted-during-load branch is gone", duringLoad >= 0)
        val fallback = handler.indexOf("} else if (videoFellBack) {", duringLoad)
        assertTrue("the fallback branch moved", fallback > duringLoad)
        assertTrue(handler.substring(duringLoad, fallback).contains("reconcilePending = true"))
        assertTrue(handler.contains("videoLostToLocalNetwork = if (lackedLocalNetwork) metadata.id else null"))
        // Granted after the fallback: the switch goes back to Video as a tap would.
        val granted = handler.indexOf("fun onLocalNetworkGranted() {")
        assertTrue("onLocalNetworkGranted is gone", granted >= 0)
        assertTrue(
            handler.indexOf("musicService.enterVideoModeIfNeeded(forceFromUserTap = true)", granted) > granted,
        )
        val button = read("app/src/gms/kotlin/com/music/echo/ui/component/CastButton.kt")
        assertTrue(button.contains("if (granted) castHandler?.onLocalNetworkGranted()"))
    }

    @Test
    fun `picking a TV asks for local network access and still connects`() {
        val button = read("app/src/gms/kotlin/com/music/echo/ui/component/CastButton.kt")
        assertTrue(button.contains("rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())"))
        val selected = button.indexOf("onRouteSelected = { route ->")
        assertTrue("onRouteSelected not found", selected >= 0)
        val ask = button.indexOf("localNetworkLauncher.launch(LocalNetworkAccess.PERMISSION)", selected)
        val connect = button.indexOf("castHandler?.connectToRoute(route)", selected)
        assertTrue("the request is gone from the route pick", ask > selected)
        assertTrue("connecting must not wait for the answer", connect > ask)
    }
}
