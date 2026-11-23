package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 745 — the room keeps one «now» per device. A device that publishes its paused queue must not hide the device that really
 * plays: the remote shown is the newest other device that PLAYS; with none playing, the newest report, as with a single «now».
 */
class RemoteNowPerDeviceTest {
    private val now = 1_000_000L

    private fun state(device: String, playing: Boolean, at: Long) =
        NowState(listOf(NowItem("s-$device", "T", "A", "")), 0, 0L, playing, device, device, at)

    @Test
    fun `a newer paused report does not hide the device that plays`() {
        val tablet = state("tablet", playing = true, at = now - 15_000)
        val phone = state("phone", playing = false, at = now - 1_000)
        assertEquals(tablet, RemotePlayback.pickRemote(listOf(tablet, phone), "pc", now))
    }

    @Test
    fun `the newest of several playing devices wins`() {
        val a = state("a", playing = true, at = now - 30_000)
        val b = state("b", playing = true, at = now - 5_000)
        assertEquals(b, RemotePlayback.pickRemote(listOf(a, b), "pc", now))
    }

    @Test
    fun `a playing report that went quiet does not count as playing - the newest report is shown, as with one now`() {
        val quiet = state("a", playing = true, at = now - RemotePlayback.FRESH_MS - 1)
        val paused = state("b", playing = false, at = now - 10_000)
        assertEquals(paused, RemotePlayback.pickRemote(listOf(quiet, paused), "pc", now))
    }

    @Test
    fun `this device's own report is never the remote`() {
        val own = state("pc", playing = true, at = now)
        assertNull(RemotePlayback.pickRemote(listOf(own), "pc", now))
        val other = state("tablet", playing = false, at = now - 60_000)
        assertEquals(other, RemotePlayback.pickRemote(listOf(own, other), "pc", now))
        assertNull(RemotePlayback.pickRemote(listOf(state("", playing = true, at = now)), "pc", now))
    }

    @Test
    fun `the engine uses the per-device map only when the room announced it, and keeps the single now otherwise`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val engine = File(root, "app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt").readText()
        assertTrue(engine.contains("perDeviceNow = msg[\"nows\"] is JsonArray"))
        assertTrue("an older room still hands its single now", engine.contains("} else (msg[\"now\"] as? JsonObject)?.let { setRemoteNow(it) }"))
        val set = engine.substringAfter("private fun setRemoteNow(state: JsonObject) {").substringBefore("private fun showRemoteNow(")
        assertTrue(set.contains("if (perDeviceNow) {"))
        assertTrue(set.contains("RemotePlayback.pickRemote(remoteNows.values, deviceId, serverNow())"))
        assertTrue("old server: today's last-writer rule", set.contains("} else showRemoteNow(if (now.deviceId == deviceId) null else now)"))
    }
}
