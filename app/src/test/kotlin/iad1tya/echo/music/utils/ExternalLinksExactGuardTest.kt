package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 846 (owner 2026-11-24, Odesli points 1 and 2): a link shared from another service opens the exact song without
 * Odesli's retired API. Source-text checks on purpose: every path here runs against the network.
 */
class ExternalLinksExactGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    /** Odesli's keyless API answers 401 PUBLIC_API_ACCESS_DEPRECATED: no request to it (or to its private website API) remains. */
    @Test
    fun `no request to the Odesli API remains`() {
        val sources = File(root, "app/src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            assertFalse("${file.name} calls api.song.link", text.contains("api.song.link"))
            assertFalse("${file.name} calls api.odesli.co", text.contains("api.odesli.co"))
        }
        val links = main("utils/ExternalMusicLinks.kt")
        assertFalse(links.contains("odesliYouTubeMusicId"))
        assertFalse(links.contains("DirectVideo"))
        assertFalse(main("MainActivity.kt").contains("Resolved.DirectVideo"))
    }

    /** The song.link forms Aura itself shares still open without any network request. */
    @Test
    fun `song_link links Aura shares are still read locally`() {
        val activity = main("MainActivity.kt")
        assertTrue(activity.contains("ExternalMusicLinks.songLinkVideoId(uri)?.let { videoId ->"))
        assertTrue(activity.contains("ExternalMusicLinks.songLinkWrappedYouTubeUrl(opened.toString())?.toUri() ?: opened"))
    }
}
