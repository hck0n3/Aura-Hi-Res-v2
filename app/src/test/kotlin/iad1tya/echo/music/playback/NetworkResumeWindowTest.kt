package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 800 (owner decision H12, 2026-11-24): after the network dead-end pause, the music may resume by itself only if
 * the connection is back within 5 minutes (it was 30). Later it re-buffers and stays paused. The constant is private
 * to MusicService, which no JVM test can reach, so it is pinned as source text.
 */
class NetworkResumeWindowTest {

    private val service: String by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
            .also { assertTrue("MusicService.kt moved", it.isFile) }.readText()
    }

    @Test
    fun `the auto-resume window after a network cut is five minutes`() {
        val value = Regex("""private const val STALE_RESUME_WINDOW_MS = (\d+) \* (\d+) \* (\d+)L""").find(service)
            ?: error("STALE_RESUME_WINDOW_MS not found")
        val ms = value.groupValues.drop(1).map { it.toLong() }.reduce(Long::times)
        assertEquals(5 * 60 * 1000L, ms)
        assertEquals("one declaration only", 1, Regex("""const val STALE_RESUME_WINDOW_MS""").findAll(service).count())
    }

    @Test
    fun `the network resume is the one gated by that window`() {
        val retry = service.substringAfter("private fun triggerRetry() {").substringBefore("\n    }\n")
        assertTrue(retry.contains("val fresh = System.currentTimeMillis() - pausedByNetworkAtMs <= STALE_RESUME_WINDOW_MS"))
        val gate = retry.substringAfter("val fresh = ")
        assertTrue("the resume still requires the fresh check", gate.indexOf("if (fresh && ") in 0..200)
        assertTrue(gate.indexOf("START_CAUSE=network_resume") > gate.indexOf("if (fresh && "))
    }
}
