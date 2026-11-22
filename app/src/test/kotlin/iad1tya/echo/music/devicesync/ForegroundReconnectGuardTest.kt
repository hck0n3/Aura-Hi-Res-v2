package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22: «controlo el dispositivo 2 desde el 1, vuelvo a la app y el 1 pierde el control». Coming back to the app reconnects at
 * once (no backoff of up to 60 s) and decides again what the player shows — event-driven, no polling.
 */
class ForegroundReconnectGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    @Test
    fun `the backoff wait can be cut short and an old wake-up is dropped first`() {
        val c = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncClient.kt")
        val wait = c.substringAfter("val wait = (2 shl attempt.coerceAtMost(5)).coerceAtMost(60)").substringBefore("fun reconnectNow()")
        assertTrue(wait.indexOf("wake.tryReceive()") in 0 until wait.indexOf("for (left in wait downTo 1)"))
        assertTrue(wait.contains("if (withTimeoutOrNull(1_000) { wake.receive() } != null) break"))
        assertFalse("no fixed sleep left in the countdown", wait.contains("delay(1_000)"))
        assertTrue(c.contains("wake.trySend(Unit)"))
    }

    @Test
    fun `the app coming to the foreground reaches the engine`() {
        val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val fg = service.substringAfter("fun onAppForegrounded() {").substringBefore("\n    }")
        assertTrue(fg.contains("deviceSync?.onAppForegrounded()"))
        val e = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
        val engineFg = e.substringAfter("fun onAppForegrounded() {").substringBefore("fun stop()")
        assertTrue(engineFg.contains("client.reconnectNow()"))
        assertTrue(engineFg.contains("evaluateRemote()"))
    }
}
