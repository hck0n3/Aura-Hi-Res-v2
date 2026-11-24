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

    @Test
    fun `a later pass only checks the uris it has not answered yet`() {
        val known = mutableMapOf<String, Boolean>()
        val asked = mutableListOf<String>()
        val check: (String) -> Boolean = { asked += it; true }
        exportedIdsWithFile(listOf("a", "b"), mapOf("a" to "u1", "b" to "u2"), known, check)
        asked.clear()
        // An export writes its id first: no uri yet, so nothing is checked.
        assertEquals(listOf("a", "b"), exportedIdsWithFile(listOf("c", "a", "b"), mapOf("a" to "u1", "b" to "u2"), known, check))
        assertEquals(emptyList<String>(), asked)
        // Then its uri: only that one is checked.
        val uris = mapOf("a" to "u1", "b" to "u2", "c" to "u3")
        assertEquals(listOf("c", "a", "b"), exportedIdsWithFile(listOf("c", "a", "b"), uris, known, check))
        assertEquals(listOf("u3"), asked)
    }

    @Test
    fun `a re-exported song checks its new uri and the old one is forgotten`() {
        val known = mutableMapOf<String, Boolean>()
        val onDisk = setOf("u1")
        exportedIdsWithFile(listOf("a", "b"), mapOf("a" to "u1", "b" to "u2"), known) { it in onDisk }
        assertEquals(mapOf("u1" to true), known)
        // «b» exported again to a new file: the old uri is dropped and the new one checked.
        val kept = exportedIdsWithFile(listOf("b", "a"), mapOf("a" to "u1", "b" to "u4"), known) { it == "u4" }
        assertEquals(listOf("b", "a"), kept)
        assertEquals(mapOf("u1" to true, "u4" to true), known)
    }

    @Test
    fun `a song re-exported to the same uri after a pass that found it missing is listed again`() {
        val known = mutableMapOf<String, Boolean>()
        val onDisk = mutableSetOf("u1")
        val asked = mutableListOf<String>()
        val check: (String) -> Boolean = { asked += it; it in onDisk }
        // «b» was deleted in a file manager: the pass hides it.
        assertEquals(listOf("a"), exportedIdsWithFile(listOf("a", "b"), mapOf("a" to "u1", "b" to "u2"), known, check))
        // Exported again under the same name: SAF hands back the same uri and the id moves to the front.
        onDisk += "u2"
        asked.clear()
        val kept = exportedIdsWithFile(listOf("b", "a"), mapOf("b" to "u2", "a" to "u1"), known, check)
        assertEquals(listOf("b", "a"), kept)
        // Only the file that was missing is checked again; the one found before is not.
        assertEquals(listOf("u2"), asked)
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
        // The answers live per collection, and a superseded pass stops at the next file.
        assertTrue(helper.contains("val known = HashMap<String, Boolean>()"))
        assertTrue(helper.contains("parseExportedFileUriMap(urisRaw), known) {"))
        assertTrue(helper.contains("ensureActive()"))
        // One shared check per ViewModel, null until it answers and reset once no list holds it.
        assertTrue(helper.contains("stateIn(scope, SharingStarted.WhileSubscribed(EXPORTED_ON_DEVICE_KEEP_MS, 0L), null)"))
    }

    @Test
    fun `Biblioteca and the automatic list both read the filtered ids`() {
        val library = main("viewmodels/LibraryViewModels.kt")
        val auto = main("viewmodels/AutoPlaylistViewModel.kt")
        for (vm in listOf(library, auto)) {
            assertTrue(vm.contains("private val exportedIdsOnDevice = exportedSongIdsOnDeviceIn(viewModelScope, context)"))
            assertTrue(vm.contains("if (ids == null) return@flatMapLatest flowOf(emptyList())"))
            assertFalse(vm.contains("exportedSongIdsOnDevice(context)"))
            assertFalse(vm.contains("ExportedSongIdsKey"))
        }
        assertTrue(library.contains("SongFilter.EXPORTED -> exportedIdsOnDevice.flatMapLatest { ids ->"))
        assertTrue(auto.contains("\"exported\" -> exportedIdsOnDevice.flatMapLatest { ids ->"))
    }
}
