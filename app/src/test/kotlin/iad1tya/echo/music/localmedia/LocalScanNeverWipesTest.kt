package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertTrue(scan.contains("if (!hasAudioReadPermission()) {"))
        assertTrue(scan.contains("throw LocalScanFailedException(\"audio read permission not granted\", missingPermission = true)"))
        assertTrue(scan.contains("throw LocalScanFailedException(\"MediaStore query failed\", cause = e)"))
        val transaction = scanner.substringAfter("database.withTransaction {").substringBefore("pruneLocalAlbums()")
        assertTrue(
            "the empty-scan refusal runs before any delete",
            transaction.indexOf("refusesEmptyScan(") in 0 until transaction.indexOf("deleteSongsByIds"),
        )
        assertFalse("no unconditional wipe of every local song", transaction.contains("clearLocalSongs()"))
        assertTrue(
            "only songs on a mounted volume are deleted",
            transaction.indexOf("splitMissingByVolume(removedIds, recordedVolumes, volumePresent)") in
                0 until transaction.indexOf("removable.chunked(SqlBatchSize).forEach(::deleteSongsByIds)"),
        )
        assertTrue("the guards step aside only on the confirmed removal", transaction.contains("if (!removeMissing && refusesEmptyScan("))
    }

    /** Row 694: an SD card taken out (or a USB drive) is a missing volume, not missing files. */
    @Test
    fun `songs on a volume that is not mounted are kept, songs with no recorded volume go as before`() {
        val (removable, kept) = splitMissingByVolume(
            missingIds = listOf("internal-gone", "sd-song", "never-recorded"),
            volumeOf = mapOf("internal-gone" to "external_primary", "sd-song" to "1a2b-3c4d"),
            isVolumePresent = { it == "external_primary" },
        )
        assertEquals(listOf("internal-gone", "never-recorded"), removable)
        assertEquals(listOf("sd-song"), kept)
    }

    @Test
    fun `the storage root before Android 10 comes from the absolute path`() {
        assertEquals("/storage/emulated/0", storageRootOf("/storage/emulated/0/Music/a.mp3"))
        assertEquals("/storage/1A2B-3C4D", storageRootOf("/storage/1A2B-3C4D/Music/a.mp3"))
        assertNull(storageRootOf(null))
        assertNull(storageRootOf("/sdcard/Music/a.mp3"))
        assertNull(storageRootOf("/storage/emulated/0"))
    }

    @Test
    fun `the recorded volumes stay on this phone and the kept songs keep theirs`() {
        assertTrue(scanner.contains("File(context.noBackupFilesDir, \"local_song_volumes.tsv\")"))
        assertTrue(scanner.contains("keptOnAbsentVolume.forEach { id -> recordedVolumes[id]?.let { put(id, it) } }"))
        assertTrue("an unreadable volume list keeps, never deletes", scanner.contains("MediaStore.getExternalVolumeNames(context) }.getOrNull() ?: return { false }"))
    }

    /**
     * Row 755: after a refused empty scan the confirmation offers to remove the songs it kept, and the confirmed scan removes
     * EVERY local song it does not see — local videos too. The count kept and shown is that set, not only the audio.
     */
    @Test
    fun `the count a refused empty scan keeps is everything the confirmed removal would delete`() {
        val transaction = scanner.substringAfter("database.withTransaction {").substringBefore("pruneLocalAlbums()")
        assertTrue(transaction.contains("val removedIds = existingLocalIds.filterNot(scannedIdSet::contains)"))
        assertTrue(transaction.contains("throw LocalScanFailedException(\"scan found no audio\", keptSongs = removedIds.size)"))
        assertTrue("the confirmed scan removes exactly that set", transaction.contains("removedIds to emptyList<String>()"))
        val viewModel = text("app/src/main/kotlin/com/music/echo/viewmodels/LocalSongsViewModel.kt")
        assertTrue("and the dialog counts it", viewModel.contains("missingSongs = (error as? LocalScanFailedException)?.keptSongs ?: 0"))
    }

    @Test
    fun `removing the songs that are no longer on the phone needs an explicit confirmation`() {
        val screen = text("app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt")
        assertTrue(screen.contains("onRemoveMissing = { viewModel.scanDevice(scanConfig, removeMissing = true) }"))
        val dialog = screen.substringAfter("if (confirmRemoveMissing) {").substringBefore("dismissButton")
        assertTrue("the removal runs only from the dialog's confirm button", dialog.contains("onRemoveMissing()"))
        assertEquals("one call site", 1, Regex("onRemoveMissing\\(\\)").findAll(screen).count())
        for (values in listOf("values", "values-es")) {
            val strings = text("app/src/main/res/$values/echo_strings.xml")
            for (key in listOf(
                "local_songs_missing_kept", "local_songs_remove_missing_action", "local_songs_remove_missing_confirm",
                "local_songs_remove_missing_confirm_button", "local_songs_scan_no_permission",
            )) {
                assertTrue("$values $key", strings.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun `the screen says nothing was removed instead of a raw exception message`() {
        val viewModel = text("app/src/main/kotlin/com/music/echo/viewmodels/LocalSongsViewModel.kt")
        assertTrue(viewModel.contains("is LocalScanFailedException ->"))
        assertTrue(viewModel.contains("R.string.local_songs_scan_empty_kept"))
        assertTrue(viewModel.contains("R.string.local_songs_scan_nothing_removed"))
        assertTrue("the missing permission says why", viewModel.contains("error.missingPermission -> context.getString(R.string.local_songs_scan_no_permission)"))
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
