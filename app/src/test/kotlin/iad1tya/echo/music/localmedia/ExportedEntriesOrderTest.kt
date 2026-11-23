package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 777: the scanner used to append newly scanned videos at the END of the exported lists and then
 * `take(ExportedEntriesCap)`, so with the lists full it dropped the NEWEST entries and trimmed the shared
 * ExportedFileUrisKey from the wrong end. It now keeps AudioExportService's order: newest first, oldest leaves.
 */
class ExportedEntriesOrderTest {

    @Test
    fun `a newly scanned id goes in front and the oldest one is trimmed`() {
        val existing = listOf("newest", "middle", "oldest")
        val ordered = newestFirstIds(existing, scanned = listOf("scanned"))
        assertEquals(listOf("scanned", "newest", "middle", "oldest"), ordered)
        assertEquals(listOf("scanned", "newest", "middle"), ordered.take(3))
    }

    @Test
    fun `an id that is already listed keeps its place`() {
        val ordered = newestFirstIds(listOf("a", "b", "c"), scanned = listOf("c", "d", "d"))
        assertEquals(listOf("d", "a", "b", "c"), ordered)
    }

    @Test
    fun `the uri map is trimmed from the old end too`() {
        val existing = linkedMapOf("newest" to "content://1", "oldest" to "content://2")
        val ordered = newestFirstUris(existing, scanned = linkedMapOf("scanned" to "scanned"))
        assertEquals(listOf("scanned", "newest", "oldest"), ordered.keys.toList())
        assertEquals(listOf("scanned", "newest"), ordered.entries.take(2).map { it.key })
    }

    @Test
    fun `a listed id that is scanned again keeps its place with the scanned value`() {
        val existing = linkedMapOf("a" to "content://a", "b" to "content://b")
        val ordered = newestFirstUris(existing, scanned = linkedMapOf("b" to "b"))
        assertEquals(listOf("a" to "content://a", "b" to "b"), ordered.entries.map { it.key to it.value })
    }

    @Test
    fun `the scanner writes both exported lists through the newest-first helpers`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val scanner = File(root, "app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt").readText()
        assertTrue(scanner.contains("val currentVideoIds = newestFirstIds("))
        assertTrue(scanner.contains("val currentUrisMap = newestFirstUris("))
        assertTrue(scanner.contains("prefs[ExportedVideoIdsKey] = currentVideoIds.take(ExportedEntriesCap)"))
        assertTrue(scanner.contains("prefs[ExportedFileUrisKey] = currentUrisMap.entries.take(ExportedEntriesCap)"))
    }
}
