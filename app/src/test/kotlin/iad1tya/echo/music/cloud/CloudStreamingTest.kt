package iad1tya.echo.music.cloud

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 892 — a cloud song streams from the cloud's read-only serve on 127.0.0.1 into media3's caches, the URL
 * and the credentials stay in memory, and every failure says why. The media3/Android side has no SDK in the unit job:
 * pinned on the source; the pure parts run here.
 */
class CloudStreamingTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()
    private val cloudDir = File(root, "app/src/main/kotlin/com/music/echo/cloud")

    @Test
    fun `the resolver plays what is on disk, applies the offline rules, then streams from the serve`() {
        val resolver = main("playback/StreamResolution.kt").substringAfter("internal fun MusicService.createDataSourceFactory(): DataSource.Factory {")
        val cloud = resolver.indexOf("if (iad1tya.echo.music.cloud.CloudSongIds.isCloud(mediaId)) {")
        assertTrue("a cloud id is handled first", cloud in 0 until resolver.indexOf("if (mediaId.isLocalMediaId()) return@Factory dataSpec"))
        val branch = resolver.substring(cloud, resolver.indexOf("if (mediaId.isLocalMediaId()) return@Factory dataSpec"))
        val onDisk = branch.indexOf("CloudPlayback.isComplete(downloadCache, mediaId)")
        val offline = branch.indexOf("dataStore.get(OfflineModeKey, false) -> OfflineRejectionException.Kind.MANUAL")
        val stream = branch.indexOf("CloudPlayback.resolve(this, dataSpec, mediaId)")
        assertTrue("disk, then offline rules, then the serve", onDisk in 0 until offline && offline < stream)
        assertTrue(branch.contains("CloudPlayback.isComplete(playerCache, mediaId)"))
        assertTrue("offline: the existing offline-queue path", branch.contains("!hasLiveInternetConnection() -> OfflineRejectionException.Kind.NO_NETWORK"))
        assertTrue(branch.contains("OfflineRejectionException(OfflineRejectionException.Kind.NO_NETWORK)"))
        assertTrue("signed out", branch.contains("e.needsReconnect -> R.string.cloud_play_reconnect"))
        assertTrue(branch.contains("e.reason == null -> R.string.cloud_play_missing"))
        assertTrue("a dead end is skipped, never retried as no-internet", branch.contains("throw PlaybackException(getString(message), e, ERROR_CODE_NO_STREAM)"))
        assertTrue(
            "through the app's caches",
            resolver.contains("DefaultDataSource.Factory(this, iad1tya.echo.music.cloud.CloudPlayback.routing(createCacheDataSource(), downloadCache, playerCache))"),
        )
        for (dir in listOf("values", "values-es")) {
            val xml = File(root, "app/src/main/res/$dir/strings.xml").readText()
            assertTrue(xml.contains("<string name=\"cloud_play_reconnect\">"))
            assertTrue(xml.contains("<string name=\"cloud_play_missing\">"))
        }
        assertTrue(File(root, "app/src/main/res/values-es/strings.xml").readText().contains("<string name=\"cloud_play_reconnect\">Vuelve a conectar tu nube</string>"))
    }

    @Test
    fun `cloud bytes go through the downloads cache and the listen cache, keyed by the song id`() {
        val playback = main("cloud/CloudPlayback.kt")
        val chain = playback.substringAfter("fun cacheChain(downloadCache: Cache, playerCache: Cache): DataSource.Factory =").substringBefore("\n    /**")
        assertTrue(chain.indexOf(".setCache(downloadCache)") < chain.indexOf(".setCache(playerCache)"))
        assertTrue("downloads are only read while streaming", chain.contains(".setCacheWriteDataSinkFactory(null)"))
        assertEquals("the listen cache writes", 1, Regex("""setCacheWriteDataSinkFactory""").findAll(chain).count())
        assertTrue(chain.contains(".setUpstreamDataSourceFactory(CloudLoopbackDataSource.Factory())"))
        assertTrue("routed by the cache key, which is the song id", playback.contains("val target = if (dataSpec.key?.isCloudSongId() == true) cloud else default"))
    }

    @Test
    fun `the credentials are added at open time, over a client that never uses a proxy, and the daemon is held while reading`() {
        val playback = main("cloud/CloudPlayback.kt")
        assertTrue(playback.contains(".proxy(Proxy.NO_PROXY)"))
        val open = playback.substringAfter("class CloudLoopbackDataSource(").substringBefore("override fun read(")
        assertTrue(open.contains("val authorization = CloudStreams.authorizationFor(dataSpec.uri)"))
        assertTrue(open.contains("retained = RcloneDaemon.retainForStream()"))
        assertTrue(open.contains("loopback.open(dataSpec.withAdditionalHeaders(mapOf(\"Authorization\" to authorization)))"))
        assertTrue("no other network for a cloud song", open.contains("val other = upstream ?: throw IOException(\"cloud stream not resolved\")"))
        val close = playback.substringAfter("override fun close() {\n        val source = current").substringBefore("class Factory(")
        assertTrue(close.contains("RcloneDaemon.releaseStream()"))
        // A serve that is gone (daemon stopped) is never dialled: a ConnectException would read as «no internet».
        assertTrue(open.contains("if (!retained) throw IOException(\"cloud stream ended\")"))
        // The whole resolved stream holds the daemon, from the routing open, so the cached prefix of a song cannot outlast
        // the idle stop before the loopback part opens.
        val routing = playback.substringAfter("class CloudRoutingDataSource(").substringBefore("class Factory(")
        assertTrue(
            routing.contains(
                "if (target === cloud && CloudStreams.authorizationFor(dataSpec.uri) != null) retained = RcloneDaemon.retainForStream()",
            ),
        )
        assertTrue(routing.substringAfter("override fun close() {").contains("RcloneDaemon.releaseStream()"))
        // Holding never STARTS the daemon: only a resolve (inside a use) does.
        val daemon = main("cloud/RcloneDaemon.kt")
        val retain = daemon.substringAfter("fun retainForStream(): Boolean = runBlocking {").substringBefore("fun releaseStream()")
        assertTrue(retain.contains("if (running?.process?.isAlive != true) return@withLock false"))
        assertFalse(retain.contains("start("))
        assertTrue(retain.contains("idleJob?.cancel()") && retain.contains("activeUses++"))
        // The serve URL is only for a serve of this process: 127.0.0.1 and a port it started.
        val streams = main("cloud/CloudStreams.kt")
        assertTrue(streams.contains("if (uri.scheme != \"http\" || uri.host != \"127.0.0.1\") return null"))
        assertTrue(streams.contains("serves[remote]?.takeIf { it.client === client }"))
    }

    @Test
    fun `cloud URLs, paths and credentials are never logged or persisted`() {
        assertEquals("Ready", CloudStreams.Target.Ready("http://127.0.0.1:1/Music/secret.flac").toString())
        assertFalse(CloudPlaybackException(CloudFailure.RECONNECT).message!!.contains("/"))
        assertEquals("cloud stream NOT_INDEXED", CloudPlaybackException(null).message)
        for (file in cloudDir.listFiles()!!.filter { it.extension == "kt" }) {
            val text = file.readText()
            val logs = Regex("""Timber\.tag\(TAG\)\.[a-z]\(([^)]*)\)""").findAll(text).map { it.groupValues[1] }.toList()
            for (line in logs) {
                for (bad in listOf("url", "path", "authorization", "user", "pass", "title", "artist", "rcError", "entry", "record")) {
                    assertFalse("${file.name} logs $bad: $line", line.substringAfter("\",", "").contains(bad, ignoreCase = true))
                }
            }
            assertFalse("${file.name}: no other logger", Regex("""\b(Log\.[diwev]|println|AppLogger)\b""").containsMatchIn(text))
        }
        for (name in listOf("CloudStreams.kt", "CloudPlayback.kt", "CloudTagReader.kt")) {
            val text = File(cloudDir, name).readText()
            assertFalse("$name logs nothing", text.contains("Timber"))
            assertFalse("$name persists nothing", text.contains("writeText") || text.contains("dataStore") || text.contains("SharedPreferences"))
        }
        // The URL is built in two places only: the player's target and the index's tag read.
        val builders = cloudDir.listFiles()!!.filter { it.extension == "kt" }
            .sumOf { Regex("""CloudStreams\.url\(""").findAll(it.readText()).count() + Regex("""\burl\(ensureServe""").findAll(it.readText()).count() }
        assertEquals(2, builders)
        // A download request carries the song id only, never the URL (row 893 relies on it).
        assertTrue(main("playback/SongDownloadActions.kt").contains("val audio = DownloadRequest.Builder(songId, songId.toUri())"))
    }

    @Test
    fun `a cloud id is the user's own file, never a catalogue id, but it is not offline by itself`() {
        val intents = main("utils/LocalMediaIntents.kt")
        val local = intents.substringAfter("fun String.isLocalMediaId(): Boolean {").substringBefore("\n}")
        assertTrue(local.contains("iad1tya.echo.music.cloud.CloudSongIds.SCHEME -> true"))
        assertTrue(intents.contains("fun String.isCloudMediaId(): Boolean = iad1tya.echo.music.cloud.CloudSongIds.isCloud(this)"))
        assertEquals("aura-cloud", CloudSongIds.SCHEME)
        val service = main("playback/MusicService.kt")
        assertTrue("only bytes on disk make it play offline", service.contains("isLocal = mediaId.isLocalMediaId() && !mediaId.isCloudMediaId(),"))
        assertFalse("the service never names the cloud package (row 888)", service.contains("iad1tya.echo.music.cloud"))
    }
}
