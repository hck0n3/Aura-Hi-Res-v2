package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Aura se abrió sola» can only be diagnosed from the log the owner shares if every door that starts or stops playback names itself.
 * One `START_CAUSE=<cause>` INFO line per path; only causes, package names and ints — no title, artist or id (AGENTS rule 4).
 */
class StartCauseLogGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    @Test
    fun `every start and stop path names its cause`() {
        val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        for (cause in listOf(
            "route_resume", "focus_loss_pause", "focus_resume", "network_resume", "error_skip\"", "error_skip_chain_pause",
            "offline_skip", "error_stop", "volume_resume", "widget\"", "widget_remote", "blocked_remote_mode",
        )) {
            assertTrue("falta START_CAUSE=$cause", service.contains("START_CAUSE=$cause"))
        }
        val cb = read("app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt")
        assertTrue(cb.contains("START_CAUSE=external_%s from=%s"))
        assertTrue(cb.contains("START_CAUSE=external_play_%s from=%s"))
        assertTrue(cb.contains("START_CAUSE=external_play_rejected_echo from=%s"))
        val engine = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
        for (cause in listOf("remote_command cmd=%s", "take\"", "handoff_pause", "sent_away_pause")) {
            assertTrue("falta START_CAUSE=$cause", engine.contains("START_CAUSE=$cause"))
        }
        assertTrue(read("app/src/main/kotlin/com/music/echo/devicesync/RemotePlayback.kt").contains("START_CAUSE=tap entry=%s"))
    }

    @Test
    fun `no cause line carries the song`() {
        val all = listOf(
            "app/src/main/kotlin/com/music/echo/playback/MusicService.kt",
            "app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt",
            "app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt",
            "app/src/main/kotlin/com/music/echo/devicesync/RemotePlayback.kt",
        ).flatMap { read(it).lines() }.filter { it.contains("START_CAUSE=") }
        assertTrue(all.size >= 20)
        for (line in all) {
            assertFalse(line, Regex("title|artist|mediaId|currentMediaItem|metadata", RegexOption.IGNORE_CASE).containsMatchIn(line))
        }
    }
}
