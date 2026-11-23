package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 694 (owner 2026-11-23, «que no perdamos nada»): deleting a local song row also deletes its likes, its playlist
 * entries and its listening history (foreign-key cascade). So a scan that could not see the device must never delete:
 * no permission, a null cursor and a failing query are FAILED scans, and a scan that finds no audio at all while the
 * library lists local audio keeps it.
 */
class LocalScanNeverWipesTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val scanner by lazy { text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt") }

    @Test
    fun `an empty scan keeps the library, a scan that finds audio still removes what is gone`() {
        assertTrue(refusesEmptyScan(scannedAudio = 0, libraryLocalAudio = 12))
        assertFalse("one file found: the normal sync runs", refusesEmptyScan(scannedAudio = 1, libraryLocalAudio = 12))
        assertFalse("nothing to keep", refusesEmptyScan(scannedAudio = 0, libraryLocalAudio = 0))
    }

    @Test
    fun `a null cursor is a failed scan, never zero tracks`() {
        assertFalse("the old `?.use` turned a null cursor into an empty scan", scanner.contains(")?.use { cursor ->"))
        assertTrue(scanner.contains("?: throw LocalScanFailedException(\"MediaStore returned no audio cursor\")).use { cursor ->"))
        assertTrue(scanner.contains("?: throw LocalScanFailedException(\"MediaStore returned no video cursor\")).use { cursor ->"))
    }

    @Test
    fun `permission and query failures stop the scan before the transaction that deletes`() {
        val scan = scanner.substringAfter("suspend fun scanDevice(").substringBefore("database.withTransaction {")
        assertTrue(scan.contains("if (!hasAudioReadPermission()) throw LocalScanFailedException("))
        assertTrue(scan.contains("throw LocalScanFailedException(\"MediaStore query failed\", cause = e)"))
        val transaction = scanner.substringAfter("database.withTransaction {").substringBefore("pruneLocalAlbums()")
        assertTrue(
            "the empty-scan refusal runs before any delete",
            transaction.indexOf("refusesEmptyScan(") in 0 until transaction.indexOf("deleteSongsByIds"),
        )
        assertFalse("no unconditional wipe of every local song", transaction.contains("clearLocalSongs()"))
    }

    @Test
    fun `the screen says nothing was removed instead of a raw exception message`() {
        val viewModel = text("app/src/main/kotlin/com/music/echo/viewmodels/LocalSongsViewModel.kt")
        assertTrue(viewModel.contains("is LocalScanFailedException ->"))
        assertTrue(viewModel.contains("R.string.local_songs_scan_empty_kept"))
        assertTrue(viewModel.contains("R.string.local_songs_scan_nothing_removed"))
        for (values in listOf("values", "values-es")) {
            val strings = text("app/src/main/res/$values/echo_strings.xml")
            assertTrue("$values", strings.contains("name=\"local_songs_scan_nothing_removed\""))
            assertTrue("$values", strings.contains("name=\"local_songs_scan_empty_kept\""))
        }
        assertTrue(
            "the scan sheet shows the message",
            text("app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt")
                .contains("text = scanState.errorMessage.orEmpty()"),
        )
    }
}
