package iad1tya.echo.music.playback

import androidx.media3.exoplayer.offline.Download
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner / users 2026-10-19: *«cada vez que actualizan la app pierden acceso al contenido descargado… y que todo lo
 * descargado o en caché no tenga que descargarse de nuevo»*. The library flag and the bytes are two records of one fact;
 * these pin the reconcile between them, the guards that stop an unfinished ATTEMPT from un-listing a finished download or
 * deleting its bytes, and the placement of the caches (what survives an update).
 */
class OfflineIntegrityTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    // ---- the pure reconcile ----------------------------------------------------------------------

    @Test
    fun `bytes on disk that the library does not list are listed`() {
        val plan = OfflineIntegrity.plan(
            roomDownloaded = setOf("a"),
            indexState = mapOf("a" to Download.STATE_COMPLETED, "b" to Download.STATE_COMPLETED),
            bytesComplete = setOf("a", "b"),
            anyBytes = setOf("a", "b"),
        )
        assertEquals(setOf("b"), plan.markDownloaded)
        assertTrue(plan.unmarkDownloaded.isEmpty())
    }

    @Test
    fun `a song listed as downloaded with nothing on disk and no record is un-listed`() {
        val plan = OfflineIntegrity.plan(
            roomDownloaded = setOf("a", "gone"),
            indexState = mapOf("a" to Download.STATE_COMPLETED),
            bytesComplete = setOf("a"),
            anyBytes = setOf("a"),
        )
        assertEquals(setOf("gone"), plan.unmarkDownloaded)
    }

    @Test
    fun `a partial or pending download is never un-listed and a pending one is never re-listed`() {
        val plan = OfflineIntegrity.plan(
            roomDownloaded = setOf("partial", "queued"),
            indexState = mapOf("queued" to Download.STATE_QUEUED, "busy" to Download.STATE_DOWNLOADING),
            bytesComplete = setOf("busy"),
            anyBytes = setOf("partial", "busy"),
        )
        assertTrue("partial bytes keep the flag", "partial" !in plan.unmarkDownloaded)
        assertTrue("a pending download keeps the flag", "queued" !in plan.unmarkDownloaded)
        assertTrue("a song still downloading is not listed yet", "busy" !in plan.markDownloaded)
    }

    @Test
    fun `a cache that lost everything un-lists everything it listed, honestly`() {
        val plan = OfflineIntegrity.plan(
            roomDownloaded = (1..30).map { "s$it" }.toSet(),
            indexState = emptyMap(),
            bytesComplete = emptySet(),
            anyBytes = emptySet(),
        )
        assertEquals(30, plan.unmarkDownloaded.size)
        assertTrue(plan.markDownloaded.isEmpty())
    }

    // ---- the guards -----------------------------------------------------------------------------

    @Test
    fun `a failed or stopped attempt does not un-list a song whose bytes are complete`() {
        val listener = text("app/src/main/kotlin/com/music/echo/playback/DownloadUtil.kt")
            .substringAfter("scope.launch {\n                            when (download.state)")
            .substringBefore("override fun onDownloadRemoved")
        assertTrue(listener.contains("Download.STATE_FAILED,\n                                Download.STATE_STOPPED"))
        assertTrue(
            "FAILED/STOPPED only un-list when the bytes are NOT complete",
            listener.contains("if (!OfflineIntegrity.isFullyOnDisk(downloadCache, download.request.id))"),
        )
        assertTrue("REMOVING always un-lists", listener.contains("Download.STATE_REMOVING ->"))
        assertTrue("a re-confirmed download keeps its first date", listener.contains("markDownloadedKeepingDate("))
    }

    @Test
    fun `cancel all in the download notification does not delete bytes that are already complete`() {
        val service = text("app/src/main/kotlin/com/music/echo/playback/ExoDownloadService.kt")
        val cancel = service.substringAfter("REMOVE_ALL_PENDING_DOWNLOADS) {").substringBefore("return super.onStartCommand")
        assertTrue(cancel.contains("OfflineIntegrity.isFullyOnDisk(downloadUtil.downloadCache, download.request.id)"))
        assertTrue(cancel.contains("downloadManager.removeDownload(download.request.id)"))
    }

    @Test
    fun `the reconcile runs at startup from the download util and logs counts only`() {
        val util = text("app/src/main/kotlin/com/music/echo/playback/DownloadUtil.kt")
        assertTrue(util.contains("OfflineIntegrity.run(context, database, downloadCache, playerCache, downloads.value)"))
        val audit = text("app/src/main/kotlin/com/music/echo/playback/OfflineIntegrity.kt")
        val logLines = audit.lines().filter { "Timber.tag(TAG)" in it || "OFFLINE_AUDIT" in it }.joinToString("\n")
        assertTrue(logLines.contains("OFFLINE_AUDIT_DROP"))
        for (forbidden in listOf("title", "artist", "request.id", "mediaId")) {
            assertFalse("the audit must not log $forbidden", audit.substringAfter("Timber.tag(TAG).i(").substringBefore(".onFailure").contains("$forbidden,"))
        }
    }

    // ---- what survives an update ------------------------------------------------------------------

    @Test
    fun `both caches live in files and the media3 index in databases, never in the cache dir`() {
        val module = text("app/src/main/kotlin/com/music/echo/di/AppModule.kt")
        assertTrue(module.contains("context.filesDir.resolve(\"exoplayer\")"))
        assertTrue(module.contains("context.filesDir.resolve(\"download\")"))
        assertFalse("a cache the system may clear under storage pressure", module.contains("cacheDir.resolve(\"download\")"))
        assertTrue(module.contains("NoOpCacheEvictor()"))
    }

    @Test
    fun `no startup path deletes the caches because the version changed`() {
        val app = text("app/src/main/kotlin/com/music/echo/App.kt")
        assertFalse(app.contains("VERSION_CODE") && app.contains("exoplayer") && app.contains("deleteRecursively"))
        // The one-shot legacy purge must not match the companion video keys.
        assertTrue(app.contains("StreamCacheKeys.isLegacyRotatingKey(key)"))
    }

    @Test
    fun `the legacy purge matches rotating o-keys and never the companion video or a real song`() {
        assertTrue(StreamCacheKeys.isLegacyRotatingKey("yt-stream-o-AbCdEfGhIjKlMnOpQrStUvWx-251"))
        assertFalse("a real 11-char id with its itag", StreamCacheKeys.isLegacyRotatingKey("yt-stream-dQw4w9WgXcQ-251"))
        assertFalse("a real id with no itag", StreamCacheKeys.isLegacyRotatingKey("yt-stream-dQw4w9WgXcQ"))
        assertFalse("the companion video bytes", StreamCacheKeys.isLegacyRotatingKey("yt-stream-yt-video-dQw4w9WgXcQ-18"))
        assertFalse("a key that is not a listen key", StreamCacheKeys.isLegacyRotatingKey("dQw4w9WgXcQ"))
    }

    @Test
    fun `cloud backup and device transfer take the library but never the caches, so a restore is reconciled not trusted`() {
        for (file in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val xml = text("app/src/main/res/xml/$file")
            assertTrue("$file excludes the playback cache", xml.contains("path=\"./exoplayer\""))
            assertTrue("$file excludes the download cache", xml.contains("path=\"./download\""))
            assertTrue("$file excludes the media3 index", xml.contains("path=\"exoplayer_internal.db\""))
        }
    }
}
