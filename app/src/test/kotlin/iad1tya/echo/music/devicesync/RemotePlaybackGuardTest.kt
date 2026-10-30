package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 533 / 534 / 537 — the other device's playback is shown and controlled by THIS device's own player (owner 2026-10-30:
 * *«el flotante no lo quiero… que arriba de la portada aparezca continuar desde aquí, y que los controles de mi reproductor controlen
 * a donde se está reproduciendo, como Spotify»*).
 */
class RemotePlaybackGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    private fun remote(playing: Boolean, deviceId: String = "phone", atMs: Long = 1_000_000L) = NowState(
        items = listOf(NowItem("a", "t", "x", "")), index = 0, positionMs = 5_000, playing = playing,
        deviceId = deviceId, deviceName = "Móvil", atMs = atMs, durationMs = 200_000,
    )

    // ── when the player becomes a remote ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `it is a remote only for ANOTHER device that is playing and recently heard, while this one is silent`() {
        val now = 1_030_000L
        fun active(r: NowState?, thisId: String = "tablet", handoff: Boolean = true, connected: Boolean = true, local: Boolean = false, control: Boolean = false) =
            RemotePlayback.shouldBeActive(r, thisId, handoff, connected, local, control, now)
        assertTrue(active(remote(playing = true)))
        assertFalse("nothing published", active(null))
        assertFalse("it is this very device", active(remote(true, deviceId = "tablet")))
        assertFalse("handoff switched off", active(remote(true), handoff = false))
        assertFalse("not connected", active(remote(true), connected = false))
        assertFalse("this device is playing its own music", active(remote(true), local = true))
        assertFalse("three heartbeats missed: the phone is gone", active(remote(true, atMs = 900_000L)))
    }

    @Test
    fun `a paused remote stays only while it is being controlled from here`() {
        val now = 1_030_000L
        assertFalse(RemotePlayback.shouldBeActive(remote(false), "tablet", true, true, false, controllingRecently = false, serverNowMs = now))
        assertTrue(RemotePlayback.shouldBeActive(remote(false, atMs = 1L), "tablet", true, true, false, controllingRecently = true, serverNowMs = now))
    }

    @Test
    fun `commands do nothing and say so when there is no remote, so the local player keeps its own buttons`() {
        RemotePlayback.update(null, 0L)
        assertFalse(RemotePlayback.play())
        assertFalse(RemotePlayback.pause())
        assertFalse(RemotePlayback.toggle())
        assertFalse(RemotePlayback.next())
        assertFalse(RemotePlayback.previous())
        assertFalse(RemotePlayback.seekTo(1000))
        assertFalse(RemotePlayback.continueHere())
    }

    @Test
    fun `with a remote the optimistic play state flips at once and the timeline follows the other device`() {
        RemotePlayback.update(remote(playing = true), serverNowMs = 1_005_000L)
        assertTrue(RemotePlayback.active)
        assertEquals(10_000L, RemotePlayback.position.value) // 5 s published + 5 s since
        assertEquals(200_000L, RemotePlayback.duration.value)
        RemotePlayback.pause()
        assertFalse("the button answers before the other device does", RemotePlayback.playing.value)
        RemotePlayback.tick(1_020_000L)
        assertEquals("paused: the bar does not run", 10_000L, RemotePlayback.position.value)
        RemotePlayback.seekTo(42_000)
        assertEquals(42_000L, RemotePlayback.position.value)
        RemotePlayback.update(null, 0L)
        assertFalse(RemotePlayback.active)
    }

    // ── wiring ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the floating card is gone and the player itself carries the remote`() {
        val main = read("app/src/main/kotlin/com/music/echo/MainActivity.kt")
        assertFalse("sin flotante", main.contains("AuraHandoffBanner"))
        assertFalse(File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraHandoffBanner.kt").exists())
        val player = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt")
        assertTrue(player.contains("val isCasting = castingReal || remoteActive"))
        assertTrue(player.contains("val castPosition = if (remoteActive) remotePosition else castPositionReal"))
        assertTrue("continuar aquí arriba de la portada", player.contains("RemotePlayback.continueHere()"))
        assertEquals("play/pausa de las dos pantallas llegan al otro dispositivo", 2, Regex("isCasting -> if \\(castIsPlaying\\) outsidePause\\(\\) else outsidePlay\\(\\)").findAll(player).count())
        assertTrue(player.contains("outsideSeek(it)"))
        val shell = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraShell.kt")
        assertTrue(shell.contains("val isCasting = castingReal || remoteActive"))
        assertTrue(shell.contains("RemotePlayback.toggle()"))
    }

    @Test
    fun `every transport entry point of the player connection routes to the remote first`() {
        val pc = read("app/src/main/kotlin/com/music/echo/playback/PlayerConnection.kt")
        for (call in listOf("toggle()", "play()", "pause()", "seekTo(position)", "next()", "previous()")) {
            assertTrue("falta RemotePlayback.$call", pc.contains("if (iad1tya.echo.music.devicesync.RemotePlayback.$call) return"))
        }
    }

    @Test
    fun `a device that only shows the other one never announces it as its own, and loads it without playing`() {
        val e = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
        assertTrue(e.contains("if (RemotePlayback.active) return"))
        assertTrue(e.contains("playWhenReady = false,\n                isRestore = true,"))
        assertTrue("el tic de 1 s solo mientras el otro suena", e.contains("while (isActive && RemotePlayback.active && RemotePlayback.playing.value)"))
    }

    @Test
    fun `the device switcher button sits in the bottom bar, not in the crowded quick-access row (owner 2026-10-30)`() {
        val queue = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraQueue.kt")
        val player = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt")
        assertTrue(queue.contains("AuraIconGlyph(AuraIcons.Device, null, size = 21.dp, tint = tint)"))
        assertTrue("solo aparece con el dispositivo emparejado", queue.contains("showDevices = pairedBy != iad1tya.echo.music.devicesync.DeviceSyncHub.PairedBy.None"))
        assertTrue("ya no está arriba", !player.contains("AuraIcons.Device"))
    }
}
