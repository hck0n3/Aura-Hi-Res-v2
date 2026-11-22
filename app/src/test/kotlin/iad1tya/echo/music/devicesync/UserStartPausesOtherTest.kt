package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22: «la música que sonaba en el dispositivo 2 se pasó sola al 1». Row 594 pauses the other device when this one starts
 * playing; it now does so only for a start the user asked for in this app, never for an automatic one.
 */
class UserStartPausesOtherTest {
    private fun remote() = NowState(listOf(NowItem("a", "t", "x", "")), 0, 0L, true, "tablet", "Tab", 1_000_000L)

    private fun pause(sinceUserStart: Long) =
        RemotePlayback.shouldPauseOther(true, remote(), "phone", true, true, 60_000L, 1_010_000L, sinceUserStart)

    @Test
    fun `a start nobody asked for in the app leaves the other device playing`() {
        assertFalse(pause(Long.MAX_VALUE))
        assertFalse("a tap long ago does not explain this start", pause(RemotePlayback.USER_START_WINDOW_MS + 1))
        assertFalse(pause(-1L))
    }

    @Test
    fun `a start the user just asked for still pauses the other device`() {
        assertTrue(pause(0L))
        assertTrue(pause(RemotePlayback.USER_START_WINDOW_MS))
    }

    @Test
    fun `a tap is used up by the start it explains`() {
        RemotePlayback.noteUserStart("test", nowMs = 5_000L)
        assertEquals(2_000L, RemotePlayback.consumeUserStart(nowMs = 7_000L))
        assertEquals(Long.MAX_VALUE, RemotePlayback.consumeUserStart(nowMs = 8_000L))
    }

    @Test
    fun `the in-app play entry points note the tap and the engine passes it on`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        fun read(p: String) = File(root, p).readText()
        val pc = read("app/src/main/kotlin/com/music/echo/playback/PlayerConnection.kt")
        for (entry in listOf("queue", "toggle", "play", "next", "previous")) {
            assertTrue("falta $entry", pc.contains("RemotePlayback.noteUserStart(\"$entry\")"))
        }
        assertTrue(read("app/src/main/kotlin/com/music/echo/ui/newui/AuraQueue.kt").contains("RemotePlayback.noteUserStart(\"queue_row\")"))
        assertTrue(read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt").contains("RemotePlayback.noteUserStart(\"controller_select\")"))
        val e = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
        assertTrue(e.contains("val sinceUserStart = if (startedNow) RemotePlayback.consumeUserStart(nowMs) else Long.MAX_VALUE"))
        assertTrue(e.contains("msSinceUserStart = sinceUserStart,"))
    }
}
