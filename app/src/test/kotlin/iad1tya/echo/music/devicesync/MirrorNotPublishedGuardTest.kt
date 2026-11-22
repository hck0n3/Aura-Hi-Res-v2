package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22 («vuelvo a la app y el mini reproductor muestra lo de aquí»): on reconnecting, this device announced the other one's
 * mirrored queue, paused, as its own and hid the device that really plays (the room keeps one «now»). A mirror that never played here is
 * never published — not by onConnected's forced publish, not by the next tick.
 */
class MirrorNotPublishedGuardTest {
    private val engine: String = run {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt").readText()
    }

    @Test
    fun `publishing stops at a held mirror, after a real play has cleared it`() {
        val publish = engine.substringAfter("private suspend fun publishNow(force: Boolean) {").substringBefore("client.send(")
        val cleared = publish.indexOf("mirrorHeld = false")
        val gate = publish.indexOf("if (mirrorHeld) return")
        assertTrue("a playing state clears the mirror first", cleared in 0 until gate)
        assertTrue("before the forced publish can go out", gate < publish.indexOf("if (!force && !changed && !due) return"))
    }

    @Test
    fun `the mirror marks itself and playing here ends it`() {
        val mirror = engine.substringAfter("private suspend fun mirrorRemote(").substringBefore("service.playQueue(")
        assertTrue(mirror.contains("mirrorHeld = true"))
        val eval = engine.substringAfter("private suspend fun evaluateRemote() {").substringBefore("val startedNow")
        assertTrue(eval.contains("if (localPlaying) {\n            lastLocalPlayingAt = nowMs\n            mirrorHeld = false"))
    }
}
