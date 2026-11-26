package iad1tya.echo.music.cloud

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials

/*
 * Registry rows 891-892 — one read-only HTTP server per connected cloud (`rclone serve http` started through rc), on
 * 127.0.0.1:<random free port> behind a random user/password pair (16 + 32 random bytes), started on demand inside a
 * daemon use and gone with the daemon when it idles out (row 888). The tag reader of the index and the player read
 * files through it with HTTP Range requests; media3's cache keeps the bytes (no rclone VFS cache).
 *
 * The URL carries the file's path, so it lives in memory only: never persisted, never logged. The credentials never
 * leave this object: [authorizationFor] hands the header to the loopback data source at open time.
 */
object CloudStreams {
    /** A serve of one remote on one daemon run ([client] identifies the run: a new daemon means a new serve). */
    class Serve internal constructor(
        internal val client: RcClient,
        val port: Int,
        internal val authorization: String,
    ) {
        override fun toString() = "Serve(port=$port)"
    }

    private val serves = ConcurrentHashMap<String, Serve>()
    private val lock = Mutex()

    /** Inside [RcloneDaemon.use]: this run's serve of [remote], started when there is none yet. */
    suspend fun ensureServe(client: RcClient, remote: String): Serve = lock.withLock {
        serves[remote]?.takeIf { it.client === client }?.let { return@withLock it }
        var failure: IOException? = null
        // The free port is probed and released before rclone binds it: if another app took it meanwhile, once more.
        repeat(2) {
            val port = RcloneDaemon.freeLoopbackPort()
            val user = RcloneDaemon.randomToken(16)
            val pass = RcloneDaemon.randomToken(32)
            try {
                withContext(Dispatchers.IO) { client.call(RcRequests.serveStart(remote, port, user, pass)) }
                val serve = Serve(client, port, Credentials.basic(user, pass))
                serves[remote] = serve
                return@withLock serve
            } catch (e: RcException) {
                failure = e
            }
        }
        throw failure ?: IOException("cloud serve did not start")
    }

    /** `http://127.0.0.1:<port>/<path, percent-encoded>`. In memory only. */
    fun url(serve: Serve, path: String): String = "http://127.0.0.1:${serve.port}/${CloudPaths.encode(path)}"

    /** The Authorization header for a URL of a serve of this process, or null (not a serve URL, or a serve now gone). */
    fun authorizationFor(uri: Uri): String? {
        if (uri.scheme != "http" || uri.host != "127.0.0.1") return null
        val port = uri.port
        return serves.values.firstOrNull { it.port == port }?.authorization
    }

    /** What the player (row 892) or the downloader (row 893) gets for a cloud song id. */
    sealed interface Target {
        /** [url] carries the path: never log it ([toString] leaves it out). */
        class Ready(val url: String) : Target {
            override fun toString() = "Ready"
        }

        /** The song is not in the index (the cloud's folder was unchosen, or the file went away). */
        data object NotIndexed : Target

        data class Failed(val failure: CloudFailure) : Target
    }

    /** Starts the daemon and the serve if needed (blocking up to the daemon's start). Off the main thread. */
    suspend fun target(context: Context, songId: String): Target {
        val record = CloudLibraryStore.read(context).files.firstOrNull { it.id == songId } ?: return Target.NotIndexed
        return try {
            RcloneDaemon.use(context) { client -> Target.Ready(url(ensureServe(client, record.remote), record.path)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Target.Failed(failureOf(e))
        }
    }

    internal fun failureOf(e: Throwable): CloudFailure = when (e) {
        is RcloneDaemon.NoBinaryException -> CloudFailure.NO_BINARY
        // The account was disconnected (fs/fs.go ErrorNotFoundInConfigFile): its songs stay, they need it connected again.
        is RcException -> if (NOT_CONFIGURED in e.rcError) CloudFailure.RECONNECT else CloudLoginPolicy.classify(e.rcError)
        else -> CloudFailure.OTHER
    }

    private const val NOT_CONFIGURED = "didn't find section in config file"
}
