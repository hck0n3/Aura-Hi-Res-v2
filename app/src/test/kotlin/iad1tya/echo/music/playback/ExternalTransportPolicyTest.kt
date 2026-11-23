package iad1tya.echo.music.playback

import androidx.media3.common.Player
import iad1tya.echo.music.playback.ExternalTransportPolicy.Kind
import iad1tya.echo.music.playback.ExternalTransportPolicy.Route
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 786: next / previous / seek from the widget, the notification, headset buttons and Android Auto obey remote mode
 * and the Listen Together guest lock, like the in-app buttons (row 603).
 */
class ExternalTransportPolicyTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the seek commands are recognised and the rest are left alone`() {
        assertEquals(Kind.NEXT, ExternalTransportPolicy.kindOf(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals(Kind.NEXT, ExternalTransportPolicy.kindOf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertEquals(Kind.PREVIOUS, ExternalTransportPolicy.kindOf(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertEquals(Kind.PREVIOUS, ExternalTransportPolicy.kindOf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        for (seek in listOf(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
        )) {
            assertEquals(Kind.SEEK, ExternalTransportPolicy.kindOf(seek))
        }
        // PLAY_PAUSE keeps its own vetoes (rows 27, 422, 556); stop, volume and the rest are not transport.
        assertNull(ExternalTransportPolicy.kindOf(Player.COMMAND_PLAY_PAUSE))
        assertNull(ExternalTransportPolicy.kindOf(Player.COMMAND_STOP))
        assertNull(ExternalTransportPolicy.kindOf(Player.COMMAND_SET_VOLUME))
    }

    @Test
    fun `a guest is refused, a remote gets next and previous, and nothing changes otherwise`() {
        for (kind in Kind.entries) {
            assertEquals(Route.LOCAL, ExternalTransportPolicy.route(kind, listenTogetherGuest = false, remoteActive = false))
            assertEquals(Route.REFUSE_GUEST, ExternalTransportPolicy.route(kind, listenTogetherGuest = true, remoteActive = false))
            assertEquals(Route.REFUSE_GUEST, ExternalTransportPolicy.route(kind, listenTogetherGuest = true, remoteActive = true))
        }
        assertEquals(Route.REMOTE, ExternalTransportPolicy.route(Kind.NEXT, listenTogetherGuest = false, remoteActive = true))
        assertEquals(Route.REMOTE, ExternalTransportPolicy.route(Kind.PREVIOUS, listenTogetherGuest = false, remoteActive = true))
        assertEquals(Route.REFUSE_REMOTE, ExternalTransportPolicy.route(Kind.SEEK, listenTogetherGuest = false, remoteActive = true))
    }

    @Test
    fun `the session callback routes external transport before any player task`() {
        val cb = main("playback/MediaLibrarySessionCallback.kt")
        val body = cb.substringAfter("override fun onPlayerCommandRequest(")
        val routing = body.indexOf("val transport = ExternalTransportPolicy.kindOf(playerCommand)")
        assertTrue(routing > 0)
        assertTrue("before the PLAY vetoes and super", routing < body.indexOf("if (PHANTOM_PLAY_VETO_ENABLED &&"))
        assertTrue(body.contains("runCatching { service.listenTogetherManager.shouldBlockPlaybackChanges() }.getOrDefault(false)"))
        assertTrue(body.contains("ExternalTransportPolicy.route(transport, guest, iad1tya.echo.music.devicesync.RemotePlayback.active)"))
        assertTrue(body.contains("if (transport == ExternalTransportPolicy.Kind.NEXT) iad1tya.echo.music.devicesync.RemotePlayback.next()"))
        assertTrue(body.contains("else iad1tya.echo.music.devicesync.RemotePlayback.previous()"))
        assertTrue(body.contains("\"START_CAUSE=external_%s_%s from=%s\", transport.name.lowercase(), route.name.lowercase(), controller.packageName,"))
    }

    @Test
    fun `the widget's buttons take the same route`() {
        val service = main("playback/MusicService.kt")
        val start = service.substringAfter("override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {")
            .substringBefore("return super.onStartCommand(intent, flags, startId)")
        assertTrue(start.contains("if (!routeWidgetTransport(ExternalTransportPolicy.Kind.NEXT)) player.seekToNext()"))
        assertTrue(start.contains("if (!routeWidgetTransport(ExternalTransportPolicy.Kind.PREVIOUS)) player.seekToPrevious()"))
        val pause = start.indexOf("} else if (iad1tya.echo.music.devicesync.RemotePlayback.playing.value && iad1tya.echo.music.devicesync.RemotePlayback.pause()) {")
        assertTrue("a playing remote is paused by the widget", pause > 0)
        assertTrue(pause < start.indexOf("} else if (iad1tya.echo.music.devicesync.RemotePlayback.play()) {"))
        assertTrue(start.contains("START_CAUSE=widget_remote_pause"))
        val helper = service.substringAfter("private fun routeWidgetTransport(kind: ExternalTransportPolicy.Kind): Boolean {")
        assertTrue(helper.contains("::listenTogetherManager.isInitialized && listenTogetherManager.shouldBlockPlaybackChanges()"))
        assertTrue(helper.contains("ExternalTransportPolicy.route(kind, guest, iad1tya.echo.music.devicesync.RemotePlayback.active)"))
        assertTrue(helper.contains("START_CAUSE=widget_%s_%s"))
    }
}
