package iad1tya.echo.music.cloud

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 893 — «Descargar» on a cloud song (alone, or with its album or list) stores it in the app's own download
 * store through the same loopback serve: it shows in «Descargado», counts on Home, plays offline and is removed like any
 * download. Pinned on the source (no Android SDK in the unit job).
 */
class CloudOfflineDownloadTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the downloader resolves a cloud song to its serve and fetches it over the loopback client`() {
        val util = main("playback/DownloadUtil.kt")
        val factory = util.substringAfter("private val dataSourceFactory =").substringBefore("val downloadNotificationHelper")
        assertTrue(
            "loopback first, the chunked OkHttp path for everything else",
            factory.contains("iad1tya.echo.music.cloud.CloudLoopbackDataSource.Factory(ChunkingDataSourceFactory("),
        )
        val resolver = factory.substringAfter("val mediaId = dataSpec.key ?: error(\"No media id\")")
        val cloud = resolver.indexOf("if (iad1tya.echo.music.cloud.CloudSongIds.isCloud(mediaId)) {")
        assertTrue("before any YouTube resolution", cloud in 0 until resolver.indexOf("if (isVideoDownloadId(mediaId)) {"))
        assertTrue(resolver.contains("return@Factory iad1tya.echo.music.cloud.CloudPlayback.resolve(context, dataSpec, mediaId)"))
        // The bytes stay under the song id: no custom key factory, the request carries the id, never the URL.
        assertFalse(util.contains("setCacheKeyFactory"))
        val actions = main("playback/SongDownloadActions.kt")
        assertTrue(actions.contains("val audio = DownloadRequest.Builder(songId, songId.toUri())\n        .setCustomCacheKey(songId)"))
        assertTrue("removing works as for every song", actions.contains("DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, songId, false)"))
    }

    @Test
    fun `a downloaded cloud song is listed, counted and plays offline like any download`() {
        val dao = main("db/DatabaseDao.kt")
        assertTrue("«Descargado» lists every downloaded row", dao.contains("@Query(\"SELECT * FROM song WHERE isDownloaded = 1 ORDER BY dateDownload\")"))
        assertTrue(main("playback/DownloadUtil.kt").contains("database.markDownloadedKeepingDate(download.request.id, LocalDateTime.now())"))
        // Offline: the download cache answers before the network (resolver) and for the offline queue (playability).
        val resolver = main("playback/StreamResolution.kt")
        assertTrue(resolver.contains("iad1tya.echo.music.cloud.CloudPlayback.isComplete(downloadCache, mediaId) ||"))
        assertTrue(main("playback/MusicService.kt").contains("downloadComplete = { OfflineIntegrity.isFullyOnDisk(downloadCache, mediaId) },"))
        assertTrue(main("cloud/CloudPlayback.kt").contains(".setCache(downloadCache)\n            .setCacheWriteDataSinkFactory(null)"))
    }

    @Test
    fun `the player's download button downloads a cloud song instead of calling it a phone file`() {
        val player = main("ui/newui/AuraPlayer.kt")
        assertTrue(player.contains("val isPhoneTrack = isLocalTrack && !meta.id.isCloudMediaId()"))
        for (use in listOf("val downloadActive = !isPhoneTrack &&", "val downloadHeld = !isPhoneTrack &&", "val downloadDone = isPhoneTrack ||", "isPhoneTrack -> Unit")) {
            assertTrue(use, player.contains(use))
        }
        assertEquals("the download block no longer reads isLocalTrack", 0, Regex("""val download(Active|Held|Done) = !?isLocalTrack""").findAll(player).count())
        // The song menu's «Descargar» has no phone-file gate: it reaches the same enqueue.
        val menu = main("ui/menu/SongMenu.kt")
        assertTrue(menu.contains("ActionLog.tap(ActionId.SONGMENU_DOWNLOAD)\n                                    enqueueSongDownloads("))
    }

    @Test
    fun `the automatic download on like keeps its no-mobile-data rule for cloud songs too`() {
        val util = main("playback/DownloadUtil.kt")
        val like = util.substringAfter("fun downloadOnLike(").substringBefore("fun flushPendingLikeDownloads()")
        assertTrue(like.contains("if (!adopted && metered) {"))
        assertTrue(like.contains("PendingLikeDownloads.add(appContext, PendingLikeDownloads.Entry(songId, title, isVideoSong))"))
    }
}
