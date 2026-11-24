package iad1tya.echo.music.playback

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 828 (audit suspicion S-003): after more than three retries, `triggerRetry` promised to «refresh stream URL» but
 * only seeked to the same position, so the re-prepare went through the resolver and was served the SAME cached URL.
 * The branch now drops the current song's `songUrlCache` entry before `prepare()`. MusicService cannot run in a JVM
 * test, so the branch is pinned as source text.
 */
class TriggerRetryUrlRefreshTest {

    private val service: String by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
            .also { assertTrue("MusicService.kt moved", it.isFile) }.readText()
    }

    private val retry: String by lazy {
        service.substringAfter("private fun triggerRetry() {").substringBefore("\n    }\n")
    }

    @Test
    fun `the refresh branch drops the current song's cached URL before re-preparing`() {
        val branch = retry.substringAfter("if (retryCount > 3) {").substringBefore("player.prepare()")
        assertTrue("refresh branch not found", branch.contains("attempting to refresh stream URL"))
        assertTrue(branch.contains("player.currentMediaItem?.mediaId?.let { songUrlCache.remove(it) }"))
        assertTrue("the cache is dropped before prepare()", retry.indexOf("songUrlCache.remove(") < retry.indexOf("player.prepare()"))
    }

    @Test
    fun `only the URL is dropped, never the cached bytes`() {
        listOf("playerCache", "downloadCache", "clearSongCache(").forEach {
            assertTrue("$it touched in triggerRetry", !retry.contains(it))
        }
    }
}
