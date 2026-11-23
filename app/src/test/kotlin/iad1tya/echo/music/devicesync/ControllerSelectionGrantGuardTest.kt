package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review P2 of the device-control fixes: a song picked on Android Auto while another device is controlled from here plays HERE. The
 * grant given when the selection arrives can expire while a slow branch resolves, so it is renewed when the items are handed to media3.
 */
class ControllerSelectionGrantGuardTest {
    @Test
    fun `the grant is renewed with the resolved items`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val cb = File(root, "app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt").readText()
        val set = cb.substringAfter("override fun onSetMediaItems(").substringBefore("private fun drawableUri(")
        val grant = set.indexOf("if (resolved.mediaItems.isNotEmpty()) iad1tya.echo.music.devicesync.RemotePlayback.grantLocalStart()")
        assertTrue(grant >= 0)
        assertTrue("after the external queue is adopted, right before the items are returned", grant > set.indexOf("service.adoptExternalQueue("))
        assertTrue(set.substring(grant).lines()[1].trim() == "resolved")
    }
}
