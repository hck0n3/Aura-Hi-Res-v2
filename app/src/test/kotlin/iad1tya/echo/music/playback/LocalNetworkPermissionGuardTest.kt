package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 864 — targetSdk 37 (Android 17) blocks traffic to and from LAN addresses unless the app holds
 * ACCESS_LOCAL_NETWORK. The Cast video relay is exactly that (the TV connects to the phone). The permission is declared
 * for the gms build, asked when a TV is picked, and the relay is not handed out without it (the video then plays as
 * audio with its notice instead of a URL that only times out on the TV). Source-text checks for the parts that only
 * exist on a device or in the gms source set, which these JVM tests do not compile.
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
    fun `the gms manifest declares the permission and the shared manifest does not`() {
        assertTrue(
            read("app/src/gms/AndroidManifest.xml")
                .contains("<uses-permission android:name=\"android.permission.ACCESS_LOCAL_NETWORK\" />"),
        )
        assertFalse(
            "FOSS has no Cast: the permission belongs to the gms manifest only",
            read("app/src/main/AndroidManifest.xml").contains("ACCESS_LOCAL_NETWORK"),
        )
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
