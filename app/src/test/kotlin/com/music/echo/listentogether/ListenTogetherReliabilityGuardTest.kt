package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 744 (P25B): source guards for the Listen Together fixes that live in Android-only code (the bridge drives a media3
 * player, the dialog is Compose). Each literal below is grepped from the source it pins.
 */
class ListenTogetherReliabilityGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val bridge by lazy { text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt") }

    @Test
    fun theSpeedTrimEndsWhenItsGapIsClosed() {
        assertTrue(bridge.contains("val closeMs = SyncCorrection.trimDurationMs(errorMs, action.speed)"))
        assertTrue(bridge.contains("delay(closeMs)"))
    }

    @Test
    fun theHostPublishesItsQueueInPlaybackOrder() {
        assertTrue(bridge.contains("next = { i -> timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle) },"))
        assertTrue(bridge.contains("previous = { i -> timeline.getPreviousWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle) },"))
        assertTrue("toggling shuffle republishes the queue", bridge.contains("override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {"))
    }

    @Test
    fun everyDevicePlaysAtOneXInsideARoomAndGetsItsSpeedBackAfter() {
        assertTrue(bridge.contains("const val ROOM_TEMPO = 1f"))
        assertTrue("the guest's base speed is the room's", bridge.contains("val userTempo = ROOM_TEMPO"))
        assertTrue(bridge.contains("scope.launch { lockTempoInRoom() }"))
        assertTrue("a player attached inside a room is locked too", bridge.contains("if (session.state.value.inRoom) lockRoomTempo(player)"))
        assertTrue(bridge.contains("if (inRoom) lockRoomTempo(listeningTo) else unlockRoomTempo(listeningTo)"))
        val menu = text("app/src/main/kotlin/com/music/echo/ui/menu/PlayerMenu.kt")
        assertTrue("the dialog says why the speed control is gone", menu.contains("R.string.listen_together_tempo_locked"))
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            assertTrue(dir, text("app/src/main/res/$dir/echo_strings.xml").contains("name=\"listen_together_tempo_locked\""))
        }
    }
}
