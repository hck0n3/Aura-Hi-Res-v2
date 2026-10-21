package iad1tya.echo.music.utils.webplayer

import android.os.SystemClock
import com.music.innertube.YouTube
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.cipher.RendererRecoveryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Last-resort YouTube stream URL resolver: loads YouTube's real WATCH page, signed in with the user's account, in a hidden
 * WebView and captures the signed googlevideo.com URL it requests, WITHOUT letting audio play inside the WebView. Since the
 * 2026-08-23 owner directive it is NOT part of the normal cascade: [YTPlayerUtils] calls it ONLY for an age-gated song of a
 * signed-in account, after the whole client chain (see AgeGateChain) has failed. Nothing generic reaches it.
 *
 * This sidesteps cipher/bot-detection entirely: YouTube's OWN JavaScript resolves its own
 * signature/n-transform/PoToken challenge, exactly as it would in a real browser tab, because it
 * IS YouTube's real player running in a real WebView — the same reason a reference app (RiPlay)
 * never hits these failures. Unlike that app, this does NOT become the primary playback engine:
 * it hands a bare URL back to the existing ExoPlayer pipeline and gets destroyed, so the
 * Superpowered DSP / EQ / crossfade path is completely unaffected.
 */
object EmbeddedPlayerUrlResolver {
    private const val TAG = "Metrolist_EmbeddedPlayerResolver"

    // Additional budget layered ON TOP of YTPlayerUtils.RESOLVE_TIMEOUT_MS — this only ever runs
    // after that 30s cascade (times up to 2-3 attempts) has already fully completed, so it must
    // stay short: the user has already been waiting.
    private const val TIMEOUT_MS = 15_000L

    data class ResolvedStream(
        val streamUrl: String,
        val requestHeaders: Map<String, String>,
    )

    // Separate failure domain from the cipher/PoToken WebViews (CipherDeobfuscator's own instance)
    // — a renderer death here must not lock out, or be locked out by, unrelated WebView work.
    private val rendererRecoveryPolicy = RendererRecoveryPolicy()

    // One WebView at a time (a prefetch and the song being played must not spin two), and a song that just failed is not
    // retried for a while: skips, prefetches and retries would otherwise re-run a 10 s WebView for the same dead song.
    private val gate = Mutex()
    private val failures = AgeWebViewFailureMemory()

    /**
     * Never throws. Returns null on any failure (timeout, renderer death, backoff window active,
     * no audio-only request ever observed) so the caller falls through to its existing
     * StreamResolutionException dead-end — identical behavior to before this fallback existed.
     */
    suspend fun tryResolve(videoId: String): ResolvedStream? {
        if (YouTube.cookie.isNullOrBlank()) return null // the watch page only unlocks with the account's session
        if (failures.recentlyFailed(videoId, SystemClock.elapsedRealtime())) {
            Timber.tag(TAG).d("AGE_WEBVIEW status=skipped reason=recent_failure")
            return null
        }
        return gate.withLock {
            val now = SystemClock.elapsedRealtime()
            if (!rendererRecoveryPolicy.shouldAttempt(now)) {
                Timber.tag(TAG).d("AGE_WEBVIEW status=skipped reason=backoff")
                return@withLock null
            }
            try {
                val result = EmbeddedPlayerWebView.resolve(
                    context = CipherDeobfuscator.appContext,
                    videoId = videoId,
                    cookie = YouTube.cookie,
                    timeoutMs = TIMEOUT_MS,
                )
                rendererRecoveryPolicy.onSuccess()
                failures.recordSuccess(videoId)
                Timber.tag(TAG).i("AGE_WEBVIEW status=captured ms=${SystemClock.elapsedRealtime() - now}")
                result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rendererRecoveryPolicy.onFailure(SystemClock.elapsedRealtime())
                failures.recordFailure(videoId, SystemClock.elapsedRealtime())
                // Only the failure class: the message can carry URLs. Never the video id or any header.
                Timber.tag(TAG).w("AGE_WEBVIEW status=failed reason=${e.javaClass.simpleName} ms=${SystemClock.elapsedRealtime() - now}")
                null
            }
        }
    }
}
