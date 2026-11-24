package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 801 (H30, owner 2026-11-24): «Exportadas» does not list MP3s already deleted from the phone. */
class ExportedSongsOnDeviceTest {

    @Test
    fun `only ids whose file exists are kept, in export order`() {
        val uris = mapOf("a" to "content://a", "b" to "content://b", "c" to "content://c")
        val onDisk = setOf("content://a", "content://c")
        assertEquals(listOf("c", "a"), exportedIdsWithFile(listOf("c", "b", "a"), uris) { it in onDisk })
    }

    @Test
    fun `an id without a stored file uri is not listed, like Descargadas`() {
        var asked = 0
        val kept = exportedIdsWithFile(listOf("x"), emptyMap()) { asked++; true }
        assertEquals(emptyList<String>(), kept)
        assertEquals(0, asked)
    }

    @Test
    fun `each file is checked once per pass`() {
        val asked = mutableListOf<String>()
        exportedIdsWithFile(listOf("a", "b"), mapOf("a" to "u1", "b" to "u2")) { asked += it; true }
        assertEquals(listOf("u1", "u2"), asked)
    }

    // ---- wiring -----------------------------------------------------------------------------------

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    @Test
    fun `the check uses the resolver's existence test off the main thread, keyed on ids and uris`() {
        val helper = main("utils/ExportedSongsOnDevice.kt")
        assertTrue(helper.contains("exportedFileUriExists(context, it)"))
        assertTrue(helper.contains("withContext(Dispatchers.IO)"))
        assertTrue(helper.contains("it[ExportedSongIdsKey].orEmpty() to it[ExportedFileUrisKey].orEmpty()"))
        assertTrue(helper.contains(".distinctUntilChanged()"))
    }

    @Test
    fun `Biblioteca and the automatic list both read the filtered ids`() {
        val library = main("viewmodels/LibraryViewModels.kt")
        assertTrue(library.contains("SongFilter.EXPORTED -> exportedSongIdsOnDevice(context).flatMapLatest { ids ->"))
        assertFalse(library.contains("ExportedSongIdsKey"))
        val auto = main("viewmodels/AutoPlaylistViewModel.kt")
        assertTrue(auto.contains("\"exported\" -> exportedSongIdsOnDevice(context).flatMapLatest { ids ->"))
        assertFalse(auto.contains("ExportedSongIdsKey"))
    }
}
