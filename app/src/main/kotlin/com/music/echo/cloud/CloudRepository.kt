package iad1tya.echo.music.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.extensions.isInternetConnected
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The private cloud, as the rest of Aura sees it (registry row 890). Wave 1: accounts and listing. Wave 2 fills
 * [serveHttpUrl] (streaming over 127.0.0.1 into the media3 cache) and adds the library.
 *
 * Every call starts the daemon on demand and lets it stop by itself when idle (row 888). Logs carry the provider and an
 * outcome only: never a path, a file name, a token, the sign-in URL or an account name (rule 4).
 */
@Singleton
class CloudRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** False on an ABI the packaging has no rclone for: the page says «no disponible en este aparato». */
    fun isAvailable(): Boolean = RcloneBinary.path(context) != null

    /** Remote names rclone has in the encrypted config. */
    suspend fun listRemotes(): List<String> = rc { RcResponses.remotes(it.call(RcRequests.listRemotes())) }

    /** One folder of [remote] (a remote name, e.g. [CloudProvider.remoteName]); [path] "" is the root. */
    suspend fun list(remote: String, path: String): List<CloudEntry> =
        rc { RcResponses.entries(it.call(RcRequests.list(remote, path))) }

    /** Wave 2: the loopback HTTP url media3 streams [remote] from. Not served yet. */
    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun serveHttpUrl(remote: String): String? = null

    /** The account behind [provider] answers a listing of its root; null when it works. */
    suspend fun check(provider: CloudProvider): CloudFailure? = try {
        list(provider.remoteName, "")
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        failureOf(e).also { Timber.tag(TAG).w("cloud: %s check failed (%s)", provider.label, it) }
    }

    /** Forgets the account: its section of rclone.conf is deleted. Nothing in the cloud is touched. */
    suspend fun disconnect(provider: CloudProvider) {
        rc { it.call(RcRequests.deleteRemote(provider.remoteName)) }
        Timber.tag(TAG).i("cloud: %s disconnected", provider.label)
    }

    /**
     * Signs in to [provider] in a browser tab. [onState] receives every state (WaitingForBrowser carries the local page
     * the screen opens). Bounded by [CloudLoginPolicy.OAUTH_TIMEOUT_MS] and cancellable: cancelling the calling
     * coroutine (the screen left, «Cancelar») stops rclone's OAuth webserver and the job and removes the half-made remote.
     */
    suspend fun connect(provider: CloudProvider, onState: (CloudLoginState) -> Unit): CloudLoginState {
        var state: CloudLoginState = CloudLoginState.Idle
        fun on(event: CloudLoginEvent) {
            state = CloudLoginMachine.reduce(state, event)
            onState(state)
        }
        on(CloudLoginEvent.Start(provider))
        if (!isAvailable()) {
            on(CloudLoginEvent.Fail(CloudFailure.NO_BINARY))
            return state
        }
        if (!context.isInternetConnected()) {
            on(CloudLoginEvent.Fail(CloudFailure.NO_NETWORK))
            return state
        }
        Timber.tag(TAG).i("cloud: %s sign-in started", provider.label)
        var daemonReached = false
        try {
            RcloneDaemon.use(context) { client ->
                daemonReached = true
                withContext(Dispatchers.IO) { signIn(client, provider, ::on) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            on(CloudLoginEvent.Fail(failureOf(e)))
        } finally {
            val outcome = state
            if (daemonReached && outcome !is CloudLoginState.Connected) {
                withContext(NonCancellable) { cleanUp(provider) }
            }
            Timber.tag(TAG).i(
                "cloud: %s sign-in %s",
                provider.label,
                when (outcome) {
                    is CloudLoginState.Connected -> "connected"
                    is CloudLoginState.Failed -> "failed (${outcome.reason})"
                    else -> "cancelled"
                },
            )
        }
        return state
    }

    private suspend fun signIn(client: RcClient, provider: CloudProvider, on: (CloudLoginEvent) -> Unit) {
        // A sign-in left half-way by an earlier attempt still holds port 53682: free it first.
        runCatching { client.call(RcRequests.oauthStop()) }
        val parameters = CloudLoginPolicy.createParameters(provider)
        val jobId = RcResponses.jobId(client.call(RcRequests.createRemoteAsync(provider.remoteName, provider.type, parameters)))
        if (jobId < 0) return on(CloudLoginEvent.Fail(CloudFailure.OTHER))

        val job = awaitJob(client, jobId, on) ?: return
        if (!job.success) return on(CloudLoginEvent.Fail(CloudLoginPolicy.classify(job.error)))
        on(CloudLoginEvent.BrowserDone)

        var out = RcResponses.configOut(job.output)
        var asked = 0
        while (out.state.isNotEmpty()) {
            val question = out.question
            val answer = question?.let { CloudLoginPolicy.answer(provider, it) }
            if (answer == null || ++asked > CloudLoginPolicy.MAX_QUESTIONS) return on(CloudLoginEvent.Fail(CloudFailure.OTHER))
            out = RcResponses.configOut(client.call(RcRequests.continueConfig(provider.remoteName, out.state, answer, parameters)))
        }
        // The token works: the root of the account lists.
        try {
            client.call(RcRequests.list(provider.remoteName, ""))
        } catch (e: RcException) {
            return on(CloudLoginEvent.Fail(CloudLoginPolicy.classify(e.rcError)))
        }
        on(CloudLoginEvent.Verified)
    }

    /** Waits for rclone's sign-in page, hands it to the screen, then waits for the job. null = timed out / failed. */
    private suspend fun awaitJob(client: RcClient, jobId: Long, on: (CloudLoginEvent) -> Unit): RcResponses.JobStatus? {
        val started = System.currentTimeMillis()
        var pageShown = false
        while (System.currentTimeMillis() - started < CloudLoginPolicy.OAUTH_TIMEOUT_MS) {
            currentCoroutineContext().ensureActive()
            val job = RcResponses.jobStatus(client.call(RcRequests.jobStatus(jobId)))
            if (job.finished) return job
            if (!pageShown) {
                val status = RcResponses.oauthStatus(client.call(RcRequests.oauthStatus()))
                val url = status.authUrl
                if (status.running && url != null) {
                    on(CloudLoginEvent.AuthUrl(url))
                    if (!CloudLoginPolicy.isLoopbackAuthUrl(url)) return null
                    pageShown = true
                } else if (System.currentTimeMillis() - started > CloudLoginPolicy.AUTH_URL_WAIT_MS) {
                    on(CloudLoginEvent.Fail(CloudFailure.OTHER))
                    return null
                }
            }
            delay(CloudLoginPolicy.POLL_MS)
        }
        on(CloudLoginEvent.Timeout)
        return null
    }

    /** After anything but success: rclone's webserver and job stopped, the half-made remote removed. */
    private suspend fun cleanUp(provider: CloudProvider) {
        runCatching {
            rc { client ->
                runCatching { client.call(RcRequests.oauthStop()) }
                runCatching { client.call(RcRequests.deleteRemote(provider.remoteName)) }
            }
        }
    }

    private suspend fun <T> rc(block: (RcClient) -> T): T =
        RcloneDaemon.use(context) { client -> withContext(Dispatchers.IO) { block(client) } }

    private fun failureOf(e: Throwable): CloudFailure = when (e) {
        is RcloneDaemon.NoBinaryException -> CloudFailure.NO_BINARY
        is RcException -> CloudLoginPolicy.classify(e.rcError)
        is IOException -> if (context.isInternetConnected()) CloudFailure.OTHER else CloudFailure.NO_NETWORK
        else -> CloudFailure.OTHER
    }

    private companion object {
        const val TAG = "Cloud"
    }
}
