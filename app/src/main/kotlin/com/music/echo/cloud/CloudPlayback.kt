package iad1tya.echo.music.cloud

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

/*
 * Registry row 892 — playing a cloud song: «streaming por HTTP en 127.0.0.1 hacia la caché de media3».
 *
 *  1. The stream resolver (StreamResolution.kt) sees a cloud id ([isCloudSongId]). Every byte already on disk (a
 *     download, or a complete copy in the listen cache) plays as is: no daemon, no network. Otherwise the app's offline
 *     rules apply exactly as for a direct URL (Modo sin conexión, no network, the offline session: the offline queue
 *     takes over). Otherwise [resolve] starts the daemon and that cloud's read-only serve (on demand, [CloudStreams]) and
 *     hands media3 `http://127.0.0.1:<port>/<path>`: the URL lives in that DataSpec only, never persisted or logged.
 *  2. [CloudRoutingDataSource] sends a cloud song (its cache key is its id) through [cacheChain]: the downloads cache
 *     read-only, then the listen cache keyed by the song id (so a replay or a seek back is served from disk), then
 *     [CloudLoopbackDataSource], which adds the Authorization header at open time (from [CloudStreams], never in the
 *     DataSpec media3 keeps) through a client that never uses a proxy (row 864).
 *  3. While a resolved stream is open the daemon is held ([RcloneDaemon.retainForStream]) — from [CloudRoutingDataSource]'s
 *     open, right after the resolve, so a song partly in the listen cache still has its serve when the cached part ends —
 *     and released on close; the daemon's idle stop (row 888) then ends the serve with it.
 * A dead end says why and the song is skipped: «Vuelve a conectar tu nube» (the account was disconnected or its
 * permission expired), «Esta canción ya no está en tu nube» (not in the index any more).
 */

/** A cloud song that cannot be streamed: [reason] null = not in the index. Carries no path or URL. */
class CloudPlaybackException(val reason: CloudFailure?) : IOException("cloud stream ${reason?.name ?: "NOT_INDEXED"}") {
    /** The account must be connected again (disconnected, permission withdrawn or expired). */
    val needsReconnect: Boolean get() = reason == CloudFailure.RECONNECT || reason == CloudFailure.DENIED
}

object CloudPlayback {
    /** Every byte of [id] is in [cache] (a complete download or listen-cache copy): it plays with no network. */
    fun isComplete(cache: Cache, id: String): Boolean = runCatching {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(id))
        length > 0 && cache.isCached(id, 0, length)
    }.getOrDefault(false)

    /** Loader thread (it already blocks on resolves): the serve URL of [songId], starting the daemon if needed. */
    fun resolve(context: Context, dataSpec: DataSpec, songId: String): DataSpec =
        when (val target = runBlocking(Dispatchers.IO) { CloudStreams.target(context, songId) }) {
            is CloudStreams.Target.Ready -> dataSpec.withUri(Uri.parse(target.url))
            CloudStreams.Target.NotIndexed -> throw CloudPlaybackException(null)
            is CloudStreams.Target.Failed -> throw CloudPlaybackException(target.failure)
        }

    /** The cloud songs' cache chain (see the file comment, step 2). */
    fun cacheChain(downloadCache: Cache, playerCache: Cache): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(CloudLoopbackDataSource.Factory()),
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** [default] for every song but a cloud one, which goes through [cacheChain]. */
    fun routing(default: DataSource.Factory, downloadCache: Cache, playerCache: Cache): DataSource.Factory =
        CloudRoutingDataSource.Factory(default, cacheChain(downloadCache, playerCache))
}

/** Loopback only, never through a proxy the user set for the catalog (row 864), so the credentials stay on the phone. */
internal object CloudLoopbackHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
}

/**
 * Opens a serve URL of this process with its Authorization header and holds the daemon while it is open. Any other URI
 * goes to [upstream]; with none (the playback chain), it is refused: a cloud song never reaches another network.
 */
class CloudLoopbackDataSource(private val upstream: DataSource?) : DataSource {
    private val loopback: DataSource = OkHttpDataSource.Factory(CloudLoopbackHttp.client).createDataSource()
    private var current: DataSource? = null
    private var retained = false

    override fun addTransferListener(transferListener: TransferListener) {
        loopback.addTransferListener(transferListener)
        upstream?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val authorization = CloudStreams.authorizationFor(dataSpec.uri)
        if (authorization == null) {
            val other = upstream ?: throw IOException("cloud stream not resolved")
            current = other
            return other.open(dataSpec)
        }
        retained = RcloneDaemon.retainForStream()
        // The daemon (and so this serve) is gone: never dial a dead port (a ConnectException reads as «no internet»).
        if (!retained) throw IOException("cloud stream ended")
        current = loopback
        return loopback.open(dataSpec.withAdditionalHeaders(mapOf("Authorization" to authorization)))
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        (current ?: throw IOException("not open")).read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = current
        current = null
        try {
            source?.close()
        } finally {
            if (retained) {
                retained = false
                RcloneDaemon.releaseStream()
            }
        }
    }

    class Factory(private val upstream: DataSource.Factory? = null) : DataSource.Factory {
        override fun createDataSource(): DataSource = CloudLoopbackDataSource(upstream?.createDataSource())
    }
}

/** Above the caches: a cloud song (its key is its id) goes to [cloud], every other song to [default], untouched. */
class CloudRoutingDataSource(private val default: DataSource, private val cloud: DataSource) : DataSource {
    private var current: DataSource? = null
    private var retained = false

    override fun addTransferListener(transferListener: TransferListener) {
        default.addTransferListener(transferListener)
        cloud.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target = if (dataSpec.key?.isCloudSongId() == true) cloud else default
        // A song resolved to its serve holds the daemon for the whole stream, the cached part included (bytes on disk
        // keep their own URI and hold nothing).
        if (target === cloud && CloudStreams.authorizationFor(dataSpec.uri) != null) retained = RcloneDaemon.retainForStream()
        current = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        (current ?: throw IOException("not open")).read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = current
        current = null
        try {
            source?.close()
        } finally {
            if (retained) {
                retained = false
                RcloneDaemon.releaseStream()
            }
        }
    }

    class Factory(private val default: DataSource.Factory, private val cloud: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = CloudRoutingDataSource(default.createDataSource(), cloud.createDataSource())
    }
}
