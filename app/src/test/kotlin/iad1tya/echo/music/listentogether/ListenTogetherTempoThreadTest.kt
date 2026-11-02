package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 636: ending a Listen together room crashed the app ("Player is accessed on the wrong thread").
 * The bridge collects the room on Dispatchers.Default; Media3 throws for any player access off main, and
 * restoring the user's playback speed when the room ends touched the player straight from that collector.
 */
class ListenTogetherTempoThreadTest {
    private val source =
        File("src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt").readText()

    @Test
    fun restoringTheSpeedAfterTheRoomEndsRunsOnTheMainThread() {
        assertTrue(
            "the room collector must restore the speed through onMain { } (Media3 is main-thread only)",
            source.contains("onMain { restoreUserTempo() }"),
        )
    }

    @Test
    fun noOtherCallToRestoreUserTempoRunsOutsideTheMainThread() {
        // Allowed callers: the main-wrapped one above and applyTransport(), whose whole body is withContext(Main.immediate).
        val calls = Regex("""restoreUserTempo\(([^)]*)\)""").findAll(source).map { it.value }.toList()
        val definition = calls.count { it.contains("player: Player?") }
        val wrapped = calls.count { it == "restoreUserTempo()" }
        val fromApplyTransport = calls.count { it == "restoreUserTempo(player)" }
        assertEquals("the definition", 1, definition)
        assertEquals("the one main-wrapped call", 1, wrapped)
        assertEquals("the call inside applyTransport", 1, fromApplyTransport)
        assertTrue(source.indexOf("restoreUserTempo(player)") > source.indexOf("private suspend fun applyTransport("))
    }
}
