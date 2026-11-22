package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22: «en modo sin conexión las canciones aparecen como disponibles pero no suenan». One definition of
 * "plays with zero network", the resolver's own (registry 485), used by every place that answers the question.
 */
class OfflinePlayabilityTest {

    private fun source(
        isLocal: Boolean = false,
        download: Boolean = false,
        exported: Boolean = false,
        downloadSpan: Boolean = false,
        listen: Boolean = false,
    ) = OfflinePlayability.source(
        isLocal = isLocal,
        downloadComplete = { download },
        exportedFileExists = { exported },
        downloadHasAnySpan = { downloadSpan },
        listenCacheComplete = { listen },
    )

    @Test
    fun `the resolver order decides the source`() {
        assertEquals(OfflinePlayability.Source.LOCAL, source(isLocal = true, download = true, listen = true))
        assertEquals(OfflinePlayability.Source.DOWNLOAD, source(download = true, downloadSpan = true, exported = true))
        assertEquals(OfflinePlayability.Source.EXPORTED, source(exported = true, listen = true))
        assertEquals(OfflinePlayability.Source.LISTEN_CACHE, source(listen = true))
    }

    /** The resolver never mixes download bytes with listen bytes: with any download span it refuses the listen copy. */
    @Test
    fun `a complete listen copy does not count while the download cache holds a partial of the song`() {
        assertNull(source(listen = true, downloadSpan = true))
    }

    @Test
    fun `an export whose file is gone and partial copies do not play offline`() {
        assertNull(source(exported = false))
        assertNull(source(downloadSpan = true))
        assertNull(source())
    }

    @Test
    fun `cheap answers short-circuit the expensive lookups`() {
        var listenAsked = false
        var exportAsked = false
        val result = OfflinePlayability.source(
            isLocal = false,
            downloadComplete = { true },
            exportedFileExists = { exportAsked = true; false },
            downloadHasAnySpan = { true },
            listenCacheComplete = { listenAsked = true; true },
        )
        assertEquals(OfflinePlayability.Source.DOWNLOAD, result)
        assertFalse(exportAsked)
        assertFalse(listenAsked)
    }

    @Test
    fun `only the caches count as every byte on disk`() {
        assertTrue(OfflinePlayability.isCacheSource(OfflinePlayability.Source.DOWNLOAD))
        assertTrue(OfflinePlayability.isCacheSource(OfflinePlayability.Source.LISTEN_CACHE))
        assertFalse(OfflinePlayability.isCacheSource(OfflinePlayability.Source.EXPORTED))
        assertFalse(OfflinePlayability.isCacheSource(OfflinePlayability.Source.LOCAL))
        assertFalse(OfflinePlayability.isCacheSource(null))
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
    fun `the offline skip and the fully-cached test read the one predicate`() {
        val service = main("playback/MusicService.kt")
        assertTrue(service.contains("private fun isPlayableOffline(mediaId: String, exportedUris: Map<String, String>): Boolean =\n        offlinePlayableSource(mediaId, exportedUris) != null"))
        assertTrue(service.contains("OfflinePlayability.isCacheSource(offlinePlayableSource(mediaId, emptyMap()))"))
        assertTrue(service.contains("downloadHasAnySpan = { downloadCache.getCachedSpans(mediaId).isNotEmpty() }"))
    }

    /** The predicate mirrors the resolver: if one of these moves, OfflinePlayability must move with it. */
    @Test
    fun `the resolver still applies the conditions the predicate mirrors`() {
        val resolver = main("playback/StreamResolution.kt")
        val factory = resolver.substring(resolver.indexOf("internal fun MusicService.createDataSourceFactory()"))
        val download = factory.indexOf("if (!shouldBypassCache && isFullyDownloaded) {")
        val exported = factory.indexOf("if (!exportedUri.isNullOrBlank() && exportedFileUriExists(this, exportedUri)) {")
        val listen = factory.indexOf("downloadCache.getCachedSpans(mediaId).isEmpty()")
        val refusal = factory.indexOf("OfflineRejectionException(offlineRejection)")
        assertTrue(download in 0 until exported)
        assertTrue(exported in 0 until listen)
        assertTrue(listen in 0 until refusal)
    }

    @Test
    fun `the offline list shows an exported MP3 only while its file exists`() {
        val library = main("ui/newui/AuraOfflineSearch.kt")
        assertTrue(library.contains("uris[song.id]?.let { exportedFileUriExists(context, it) } == true"))
        assertTrue(library.contains("withContext(Dispatchers.IO)"))
    }
}
