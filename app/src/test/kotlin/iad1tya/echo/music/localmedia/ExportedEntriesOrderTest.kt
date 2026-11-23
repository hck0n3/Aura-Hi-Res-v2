package iad1tya.echo.music.localmedia

import iad1tya.echo.music.constants.ExportedEntriesCap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 777: ExportedFileUrisKey is one capped map shared by real exports (YouTube id -> SAF uri, which nothing can
 * recreate) and by the scanner's device videos (content uri -> itself, rediscovered on every scan). A scan must
 * never push a real export out, and an identical second scan must write the same lists.
 */
class ExportedEntriesOrderTest {

    private fun scan(existing: Map<String, String>, scanned: Map<String, String>): Map<String, String> =
        withScannedUrisAppended(existing, scanned).entries.take(ExportedEntriesCap).associate { it.key to it.value }

    private fun scanIds(existing: List<String>, scanned: List<String>): List<String> =
        withScannedIdsAppended(existing, scanned).take(ExportedEntriesCap)

    private fun deviceVideos(count: Int): Map<String, String> =
        (1..count).associate { "content://media/external/video/media/$it" to "content://media/external/video/media/$it" }

    @Test
    fun `a newly scanned id goes behind the listed ones`() {
        val ordered = withScannedIdsAppended(listOf("newest", "middle", "oldest"), scanned = listOf("scanned"))
        assertEquals(listOf("newest", "middle", "oldest", "scanned"), ordered)
    }

    @Test
    fun `an id that is already listed keeps its place`() {
        val ordered = withScannedIdsAppended(listOf("a", "b", "c"), scanned = listOf("c", "d", "d"))
        assertEquals(listOf("a", "b", "c", "d"), ordered)
    }

    @Test
    fun `a listed id that is scanned again keeps its place with the scanned value`() {
        val existing = linkedMapOf("a" to "content://a", "b" to "content://b")
        val ordered = withScannedUrisAppended(existing, scanned = linkedMapOf("b" to "b"))
        assertEquals(listOf("a" to "content://a", "b" to "b"), ordered.entries.map { it.key to it.value })
    }

    @Test
    fun `scanned device videos never evict an exported song uri`() {
        val exports = (1..300).associate { "song$it" to "content://com.android.externalstorage.documents/document/$it" }
        val firstScan = scan(exports, deviceVideos(4_800))
        assertEquals(ExportedEntriesCap, firstScan.size)
        exports.forEach { (id, uri) -> assertEquals(uri, firstScan[id]) }
    }

    @Test
    fun `a nearly full map keeps every export when the scan alone passes the cap`() {
        val exports = (1..4_900).associate { "song$it" to "content://com.android.externalstorage.documents/document/$it" }
        val firstScan = scan(exports, deviceVideos(ExportedEntriesCap + 200))
        exports.forEach { (id, uri) -> assertEquals(uri, firstScan[id]) }
        assertEquals(firstScan.toList(), scan(firstScan, deviceVideos(ExportedEntriesCap + 200)).toList())
    }

    @Test
    fun `scanned videos never evict an exported video id and a second scan changes nothing`() {
        val exported = (1..4_900).map { "video$it" }
        val scanned = deviceVideos(ExportedEntriesCap + 200).keys.toList()
        val firstScan = scanIds(exported, scanned)
        assertEquals(exported, firstScan.take(exported.size))
        assertEquals(firstScan, scanIds(firstScan, scanned))
    }

    @Test
    fun `the scanner writes both exported lists through the append helpers`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val scanner = File(root, "app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt").readText()
        assertTrue(scanner.contains("val currentVideoIds = withScannedIdsAppended("))
        assertTrue(scanner.contains("val currentUrisMap = withScannedUrisAppended("))
        assertTrue(scanner.contains("prefs[ExportedVideoIdsKey] = currentVideoIds.take(ExportedEntriesCap)"))
        assertTrue(scanner.contains("prefs[ExportedFileUrisKey] = currentUrisMap.entries.take(ExportedEntriesCap)"))
    }
}
