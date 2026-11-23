package iad1tya.echo.music.ui.newui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 766-767: list rows must not rebuild flows on every recomposition.
 */
class ListPerformanceGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `AuraDownloadTick remembers its download flow per song`() {
        val tick = main("ui/newui/AuraContent.kt")
            .substringAfter("fun AuraDownloadTick(")
            .substringBefore("when (download?.state) {")
        assertTrue(tick.contains("val downloadFlow = remember(downloadUtil, songId) { downloadUtil.getDownload(songId) }"))
        assertTrue(tick.contains("val download by downloadFlow.collectAsState(initial = null)"))
        assertFalse(tick.contains("getDownload(songId).collectAsState("))
    }
}
