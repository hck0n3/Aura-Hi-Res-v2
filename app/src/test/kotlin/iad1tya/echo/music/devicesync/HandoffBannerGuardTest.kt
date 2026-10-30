package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry rows 533 / 534 — the paired device that is NOT playing is offered «Continuar aquí» and becomes a remote (owner 2026-10-30). */
class HandoffBannerGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    @Test
    fun `the banner lives in the window root, not in the player sheet, so an idle device shows it too`() {
        val main = read("app/src/main/kotlin/com/music/echo/MainActivity.kt")
        assertTrue(main.contains("AuraHandoffBanner(modifier = Modifier.align(Alignment.TopCenter))"))
    }

    @Test
    fun `it shows only for a fresh state of another device while this one is silent`() {
        val s = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraHandoffBanner.kt")
        assertTrue(s.contains("state.deviceId != thisId"))
        assertTrue(s.contains("!localPlaying"))
        assertTrue(s.contains("DeviceSyncHub.Connection.Connected"))
        assertTrue(s.contains("DeviceSyncHub.serverNow() - state.atMs <= FRESH_MS"))
        val fresh = Regex("FRESH_MS = (\\d[\\d_]*)L").find(s)!!.groupValues[1].replace("_", "").toLong()
        assertTrue("tres latidos de 20 s como mucho", fresh in 40_000L..90_000L)
    }

    @Test
    fun `the freshness tick runs only while the card could show (battery, AGENTS rule 7)`() {
        val s = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraHandoffBanner.kt")
        assertTrue(s.contains("LaunchedEffect(candidate)"))
        assertTrue(s.contains("while (candidate)"))
    }

    @Test
    fun `the button takes over from the device that plays`() {
        val s = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraHandoffBanner.kt")
        assertTrue(s.contains("DeviceSyncHub.controller?.takeOverFrom(s.deviceId)"))
    }

    @Test
    fun `the card controls the other device and keeps a paused remote while it is being controlled`() {
        val s = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraHandoffBanner.kt")
        assertTrue(s.contains("DeviceSyncHub.controller?.control(s.deviceId, command, positionMs)"))
        assertTrue(s.contains("send(if (s.playing) \"pause\" else \"play\")"))
        assertTrue(s.contains("send(\"seek\", positionMs)"))
        assertTrue("un remoto en pausa se queda mientras lo controlas", s.contains("(state.playing || controlling)"))
    }

    @Test
    fun `the timeline ticks only for a playing remote`() {
        val s = read("app/src/main/kotlin/com/music/echo/ui/newui/AuraHandoffBanner.kt")
        assertTrue(s.contains("while (state.playing)"))
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
