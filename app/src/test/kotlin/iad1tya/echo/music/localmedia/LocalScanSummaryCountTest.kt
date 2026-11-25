package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 855 (owner test #36, 2026-11-24): the scan said «59 canciones encontradas» and Biblioteca ▸ Local listed 58.
 * The summary counted every registered track, videos included, while Local lists audio only (row 583). Now the found
 * count is the audio, and videos / files left out are said apart.
 */
class LocalScanSummaryCountTest {

    private val root: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the found count is the audio Local lists and the videos are counted apart`() {
        val summary = localScanSummary(
            scannedIsVideo = List(58) { false } + true,
            removedSongs = 2,
            keptMissing = 1,
            skippedUnsupported = 3,
            skippedShort = 4,
        )
        assertEquals(58, summary.scannedSongs)
        assertEquals(1, summary.scannedVideos)
        assertEquals(2, summary.removedSongs)
        assertEquals(1, summary.keptMissing)
        assertEquals(3, summary.skippedUnsupported)
        assertEquals(4, summary.skippedShort)

        val empty = localScanSummary(emptyList(), removedSongs = 0, keptMissing = 0, skippedUnsupported = 0, skippedShort = 0)
        assertEquals(LocalSongScanSummary(scannedSongs = 0, removedSongs = 0), empty)
    }

    @Test
    fun `the scan builds its summary that way and the sheet says the rest`() {
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        assertTrue(scanner.contains("summary = localScanSummary(\n                scannedIsVideo = snapshot.tracks.map(LocalTrackRecord::isVideo),"))
        assertFalse("the old count included the videos Local does not list", scanner.contains("scannedSongs = snapshot.tracks.size"))
        assertTrue(scanner.contains("if (!SupportedLocalAudio.isSupported(displayName, mimeType)) {\n                    skippedUnsupported++"))
        assertTrue(
            "the short ones are the exact complement of the scan's duration clause",
            scanner.contains("selection = selectionWith(\"(\${MediaStore.Audio.Media.DURATION} IS NULL OR NOT (\$durationFloor))\"),"),
        )
        assertTrue(scanner.contains("val selection = selectionWith(durationFloor)"))
        assertTrue("counting the short ones never fails the scan", scanner.contains("}.getOrDefault(0)"))

        val list = text("app/src/main/kotlin/com/music/echo/viewmodels/LocalSongsViewModel.kt")
        assertTrue("Local lists audio only (row 583)", list.contains("database.localSongs().map { list -> list.filterNot { it.song.isVideo } }"))

        val screen = text("app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt")
        for (key in listOf("local_songs_scan_videos", "local_songs_scan_skipped_unsupported", "local_songs_scan_skipped_short")) {
            assertTrue(key, screen.contains("pluralStringResource(R.plurals.$key, it, it)"))
            for (values in listOf("values", "values-es")) {
                assertTrue("$values $key", text("app/src/main/res/$values/echo_strings.xml").contains("<plurals name=\"$key\">"))
            }
        }
    }
}
