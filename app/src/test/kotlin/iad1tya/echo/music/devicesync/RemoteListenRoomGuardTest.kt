package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review P5 of the device-control fixes: a Listen Together guest that joins while another device is controlled from here had its first
 * sync play() paused by the remote-mode guard. Inside a room the player belongs to the room (row 617): neither guard blocks it.
 */
class RemoteListenRoomGuardTest {
    @Test
    fun `both remote-mode guards stand aside inside a listen-together room`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val service = File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
        assertTrue(service.contains("get() = ::listenTogetherManager.isInitialized && listenTogetherManager.isInRoom"))
        assertTrue(service.contains("if (!remoteRedirectBypass && !inListenRoom &&\n            iad1tya.echo.music.devicesync.RemotePlayback.blocksSoundingNow("))
        assertTrue(service.contains("if (playWhenReady && !remoteRedirectBypass && !inListenRoom && iad1tya.echo.music.devicesync.RemotePlayback.blocksLocalStartNow()) {"))
        assertEquals("only the row-556 redirect in playQueue reads RemotePlayback without the room exemption", 1,
            Regex("!remoteRedirectBypass && iad1tya").findAll(service).count())
    }
}
