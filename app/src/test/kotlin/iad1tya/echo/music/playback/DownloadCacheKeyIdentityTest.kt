package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 694: the offline reconcile (rows 666/690, [OfflineIntegrity.plan]) compares three ids as if they were ONE — the
 * key the bytes live under in the download cache, Media3's download id, and the Room song id. It un-lists a COMPLETED
 * download whose id has no bytes while the cache has bytes of other songs. If one download path ever keyed its bytes
 * under anything else (a prefix, a URL, another field), every song downloaded that way would read as "COMPLETED without
 * bytes" and drop out of «Descargadas» at the next start. This pins the identity on every place that can create a
 * download, and on the code that reads it back.
 */
class DownloadCacheKeyIdentityTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val anyBuilder = Regex("""DownloadRequest\s*\.\s*Builder\(""")

    /** `DownloadRequest.Builder(ID, ID.toUri())` followed by its chain up to `.build()`. */
    private val identityBuilder = Regex(
        """DownloadRequest\s*\.\s*Builder\(\s*([\w.]+)\s*,\s*([\w.]+)\.toUri\(\)\s*\)(.*?)\.build\(\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    @Test
    fun `every download request keys its bytes under its own id`() {
        var builders = 0
        val offenders = mutableListOf<String>()
        File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val src = file.readText()
                val path = file.relativeTo(root).path.replace(File.separatorChar, '/')
                val total = anyBuilder.findAll(src).count()
                if (total == 0) return@forEach
                builders += total
                val matched = identityBuilder.findAll(src).toList()
                if (matched.size != total) offenders += "$path: ${total - matched.size} request(s) not built as Builder(id, id.toUri())"
                matched.forEach { m ->
                    val (id, uriId, chain) = m.destructured
                    if (id != uriId) offenders += "$path: id $id but uri $uriId"
                    val key = Regex("""\.setCustomCacheKey\(\s*([^)]*?)\s*\)""").find(chain)?.groupValues?.get(1)
                    // No custom key = Media3 keys by the uri, which is the id itself; a custom key must be that id.
                    if (key != null && key != id) offenders += "$path: id $id but cache key $key"
                }
            }
        assertTrue("no download request found — the pattern no longer matches the code", builders > 0)
        assertTrue("download bytes keyed away from their id: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the download side reads and writes the same id`() {
        val util = text("app/src/main/kotlin/com/music/echo/playback/DownloadUtil.kt")
        assertTrue("the resolver serves the request id", util.contains("val mediaId = dataSpec.key ?: error(\"No media id\")"))
        assertTrue("Room is marked with the request id", util.contains("database.markDownloadedKeepingDate(download.request.id, LocalDateTime.now())"))
        assertFalse("a custom key factory on the DownloadManager would re-key every download", util.contains("setCacheKeyFactory"))
        val adoption = text("app/src/main/kotlin/com/music/echo/playback/ListenCacheAdoption.kt")
        assertTrue("an adopted listen copy lands under the song id", adoption.contains(".setKey(songId)"))
        val integrity = text("app/src/main/kotlin/com/music/echo/playback/OfflineIntegrity.kt")
        assertTrue(integrity.contains("val audioKeys = downloadCache.keys.filterTo(HashSet()) { !isVideoDownloadId(it) }"))
        assertTrue(integrity.contains("val room = database.downloadedSongIdsOnce().toHashSet()"))
    }
}
