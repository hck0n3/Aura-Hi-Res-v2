package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit R1: every place that starts a new queue bumps [MusicService.queueGeneration], so a radio seed still in flight for the
 * old one settles as a stale no-op instead of appending into the new one. Source checks: the race needs a real player.
 */
class QueueGenerationGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `playNext on an empty player is a new queue`() {
        val playNext = main("playback/MusicService.kt").substringAfter("fun playNext(items: List<MediaItem>) {").substringBefore("\n        }\n")
        val bump = playNext.indexOf("queueGeneration++")
        assertTrue(bump > 0 && bump < playNext.indexOf("adoptDirectQueue(items = items, title = null)"))
    }

    @Test
    fun `the dismiss gesture ends the queue`() {
        val dismiss = main("ui/newui/AuraPlayer.kt").substringAfter("DESTRUCTIVE GESTURE, PRESERVED VERBATIM")
            .substringAfter("onDismiss = {").substringBefore("\n        },")
        val bump = dismiss.indexOf("playerConnection.service.queueGeneration++")
        assertTrue(bump >= 0 && bump < dismiss.indexOf("playerConnection.player.clearMediaItems()"))
    }
}
