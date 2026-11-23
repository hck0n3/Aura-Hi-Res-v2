package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review C3 of the device-control fixes: in-app buttons that called the player directly did nothing (and said nothing) while another
 * device is controlled from here — the remote-mode guard pauses any local start. They go through PlayerConnection / the service now.
 */
class RemoteInAppButtonsGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    @Test
    fun `the album Play button of the current album reaches the controlled device`() {
        for (path in listOf(
            "app/src/main/kotlin/com/music/echo/ui/newui/AuraAlbumScreen.kt",
            "app/src/main/kotlin/com/music/echo/ui/screens/AlbumScreen.kt",
        )) {
            val src = read(path)
            assertFalse(path, src.contains("playerConnection.player.play()"))
            assertTrue(path, src.contains("playerConnection.play()"))
        }
    }

    @Test
    fun `tapping an automix row asks the service first, so the mirror is neither moved nor started`() {
        for (path in listOf(
            "app/src/main/kotlin/com/music/echo/ui/newui/AuraQueue.kt",
            "app/src/main/kotlin/com/music/echo/ui/player/Queue.kt",
        )) {
            assertTrue(path, read(path).contains("if (at >= 0 && !playerConnection.service.refuseQueueEditInRemoteMode()) {"))
        }
    }
}
