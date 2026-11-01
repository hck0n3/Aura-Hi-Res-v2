package iad1tya.echo.music.devicesync

import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 617: inside a Listen Together room the remote control stands down. */
class RemoteInRoomTest {
    @Test
    fun theSourceWiresTheRoomIntoTheDecision() {
        val engine = java.io.File("src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt").readText()
        assertTrue(engine.contains("inListenRoom = runCatching { service.listenTogetherManager.isInRoom }"))
        val rp = java.io.File("src/main/kotlin/com/music/echo/devicesync/RemotePlayback.kt").readText()
        assertTrue(rp.contains("if (inListenRoom) return false"))
    }
}
