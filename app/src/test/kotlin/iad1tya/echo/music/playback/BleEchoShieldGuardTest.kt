package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bluetooth LE Audio headsets / speakers and hearing aids replay PLAY on reconnect like A2DP does, so connecting one
 * must open the same registry-422 echo window. The resume-on-connect setting keeps reacting to the same routes as before.
 */
class BleEchoShieldGuardTest {
    private val service: String = run {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
    }

    @Test
    fun `LE Audio and hearing aid routes stamp the echo window`() {
        val block = service.substringAfter("val echoRoute = hasPlaybackRoute ||").substringBefore("if (hasPlaybackRoute) {")
        for (type in listOf("TYPE_BLE_HEADSET", "TYPE_BLE_SPEAKER", "TYPE_HEARING_AID")) {
            assertTrue("falta $type en el escudo del eco", block.contains("AudioDeviceInfo.$type"))
        }
        assertTrue(block.contains("if (echoRoute) lastRouteConnectedAtMs = System.currentTimeMillis()"))
        assertEquals(1, Regex("lastRouteConnectedAtMs = System.currentTimeMillis\\(\\)").findAll(service).count())
    }

    @Test
    fun `the resume-on-connect branch still needs a classic playback route`() {
        val branch = service.substringAfter("if (echoRoute) lastRouteConnectedAtMs").substringBefore("applyEqForCurrentOutput()")
        assertTrue(branch.contains("if (hasPlaybackRoute) {"))
        assertTrue(branch.contains("DeliberatePausePolicy.mayResumeOnRouteConnect("))
    }
}
