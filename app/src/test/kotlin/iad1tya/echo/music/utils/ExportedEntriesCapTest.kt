package iad1tya.echo.music.utils

import iad1tya.echo.music.constants.ExportedEntriesCap
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 694: past the cap the OLDEST export silently leaves «Exportadas» and, with its uri gone, stops playing offline.
 * It was 1000, written as a bare number in five places by two writers that must agree. One constant, raised.
 */
class ExportedEntriesCapTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the cap is far above the old 1000`() {
        assertTrue(ExportedEntriesCap >= 5_000)
    }

    @Test
    fun `every writer of the exported lists uses the shared cap`() {
        val service = text("app/src/main/kotlin/com/music/echo/playback/AudioExportService.kt")
        assertTrue(service.contains("preferences[ExportedSongIdsKey] = updated.take(ExportedEntriesCap)"))
        assertTrue(service.contains("preferences[ExportedVideoIdsKey] = updated.take(ExportedEntriesCap)"))
        assertTrue(service.contains("map.entries.take(ExportedEntriesCap - 1)"))
        assertFalse(service.contains("take(999)"))
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        assertTrue(scanner.contains("prefs[ExportedVideoIdsKey] = currentVideoIds.take(ExportedEntriesCap)"))
        assertTrue(scanner.contains("prefs[ExportedFileUrisKey] = currentUrisMap.entries.take(ExportedEntriesCap)"))
        assertFalse(scanner.contains("take(1000)"))
    }
}
