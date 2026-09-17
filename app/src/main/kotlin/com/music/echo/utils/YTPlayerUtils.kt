

package iad1tya.echo.music.utils

import android.net.ConnectivityManager
import android.util.Log
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import iad1tya.echo.music.utils.BotDetectionMitigator
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import iad1tya.echo.music.constants.AudioQuality
import iad1tya.echo.music.utils.cipher.CipherDeobfuscator
import iad1tya.echo.music.utils.webplayer.EmbeddedPlayerUrlResolver
import iad1tya.echo.music.utils.YTPlayerUtils.MAIN_CLIENT
import iad1tya.echo.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import iad1tya.echo.music.utils.YTPlayerUtils.validateStatus
import iad1tya.echo.music.utils.potoken.PoTokenGenerator
import iad1tya.echo.music.utils.potoken.PoTokenResult
import iad1tya.echo.music.utils.sabr.EjsNTransformSolver
import iad1tya.echo.music.utils.PlaybackLogLevel
import iad1tya.echo.music.utils.PlaybackLogManager
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first

/**
 * HALLAZGO-056: a resolution cancelled because its owner went away (user left the screen, skipped
 * the track, closed the sheet) is teardown, not a playback failure. The BETA-012 log persisted
 * these at WARNING/ERROR ("Parent job is Cancelling", "The coroutine scope left the composition",
 * bare InterruptedExceptions from cancelled blocking calls), burying the real failures in the
 * shared log. Callers log these at debug only — AppLogger persists INFO and above, so they no
 * longer reach filesDir/logs/app.log.
 */
fun isBenignResolveCancellation(e: Throwable?): Boolean =
    e is CancellationException || e is InterruptedException

/**
 * HALLAZGO-035: video-export URL preference. "Export as video" resolved ONLY through the burned
 * InnerTube TVHTML5 path (videoStreamUrlDiag) while in-app video mode plays fine through NewPipe's
 * ANDROID_VR extraction — so the export row stayed dead. NewPipe wins; a blank/missing NewPipe URL
 * falls back to whatever InnerTube delivered.
 */
fun pickVideoExportUrl(newPipeUrl: String?, innerTubeUrl: String?): String? =
    newPipeUrl?.takeIf { it.isNotBlank() } ?: innerTubeUrl?.takeIf { it.isNotBlank() }

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"

    /**
     * Records WHICH client failed and WHY, at a level [AppLogger] persists.
     *
     * "No reproduce" was undiagnosable because every client failure went through `.getOrNull()` and
     * the reason was thrown away — the log showed a song that did not play and nothing else. With one
     * line per attempted client, the shared log now spells the cascade out ("ANDROID_MUSIC 403, then
     * TVHTML5 parse error, then IOS ok"), which is the difference between "YouTube changed something,
     * ship a fix" and "one song is region-locked, ignore it".
     *
     * WARNING, not ERROR: a single client failing is normal and expected — that is exactly what the
     * fallback cascade is for. Only the final give-up is an error. Video id + exception class +
     * message only; no URL, which would carry the credentialed query string.
     */
    private fun logClientFailure(clientName: String, videoId: String, error: Throwable) {
        // HALLAZGO-056: a client attempt cut short by teardown (scope left, track skipped) is not a
        // client failure — persisting it as WARNING flooded the shared log with noise. Debug only.
        if (isBenignResolveCancellation(error)) {
            Timber.tag("RESOLVE_FAIL").d(
                "client=$clientName videoId=$videoId cancelled (${error.javaClass.simpleName})"
            )
            return
        }
        Timber.tag("RESOLVE_FAIL").w(
            "client=$clientName videoId=$videoId ${error.javaClass.simpleName}: ${error.message?.take(180)}"
        )
    }
    private const val TAG = "YTPlayerUtils"

    private var hasShownLosslessToast = false
    private var hasShownSaavnToast = false


    // OPUS-ONLY STREAMING (owner directive 2026-09-05, Echo-Music reference — EchoMusicApp/
    // Echo-Music plays YouTube as Opus-only): the whole Opus family leads — 774 (Opus 256k
    // premium), 251 (160k), 250 (70k), 249 (50k), 139 (mobile Opus) — and NO AAC/other codec
    // sits between them. The old order interleaved 141 (AAC 256k) between 774 and 251, so a
    // track without the premium itag silently played AAC; with this order it degrades inside
    // the Opus family first. AAC (141/140) stays AFTER every Opus option as the cross-family
    // fallback for videos that simply ship no Opus rendition, vorbis (171) after it, and the
    // muxed MP4 progressives (22, 18) keep their emergency-tail role (bot-limited extractions
    // sometimes hand over ONLY itag 18 — a playing low-quality stream beats a perfect silent
    // one). This changes selection only; resolution, cache keys (itag-keyed) and quality
    // mapping are untouched.
    // CORRECTION (2026-09-13 audit, verified against YouTube's format table): itag 139 is NOT Opus — it is
    // AAC-HE 48 kbps in audio/mp4. Placed before 141/140 it made a track whose Opus URL was missing fall to
    // 48 kbps AAC instead of 128 kbps AAC (140). The Opus family (774/251/250/249) still leads; the AAC
    // fallback now degrades from highest to lowest bitrate.
    private val AUDIO_ITAG_PREFERENCE = listOf(774, 251, 250, 249, 141, 140, 139, 171, 22, 18)

    // The signature timestamp (sts) is a per-PLAYER-VERSION constant — identical for every video until
    // YouTube rotates player.js (rare, ~weekly). Recomputing it for every song runs NewPipe's JS engine
    // over the ~2.8 MB player.js each time, which is multi-second on weak (TV) CPUs and needlessly
    // repeats work on the critical path of every song change. Memoize the successful value for a bounded
    // window (same 6 h horizon the player.js disk cache already tolerates) so only the first song pays
    // it. Only SUCCESSES are cached — a failure (which also carries the early age-restriction hint)
    // always re-runs, so nothing is lost.
    @Volatile private var cachedSignatureTimestamp: Int? = null
    @Volatile private var cachedSignatureTimestampAtMs: Long = 0L
    private const val SIGNATURE_TIMESTAMP_TTL_MS = 6 * 60 * 60 * 1000L

    // ASYNC sts (slow-start fix): ONE in-flight computation shared by every concurrent resolve (playback,
    // crossfade prefetch and preload overlap routinely) and by the startup prewarm. Deliberately DETACHED
    // from any single resolve's scope: when a resolve finishes without ever needing the sts (the main
    // client, ANDROID_VR, discards it), the parse keeps running here and lands in the memo cache for the
    // next resolve instead of holding the finished resolve hostage until the ~2.8 MB player.js parse ends.
    // The underlying NewPipe call is blocking and non-cancellable anyway, so structured cancellation could
    // not stop it either. A COMPLETED deferred is never reused: a success is served by the memo cache
    // above, and a failure must re-run (failures are deliberately not cached — see the comment there).
    private val stsScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    @Volatile private var stsInFlight: Deferred<SignatureTimestampResult>? = null

    private fun signatureTimestampAsync(videoId: String): Deferred<SignatureTimestampResult> {
        // Fresh memoized value → complete instantly, no coroutine machinery at all.
        val cached = cachedSignatureTimestamp
        if (cached != null &&
            SystemClock.elapsedRealtime() - cachedSignatureTimestampAtMs < SIGNATURE_TIMESTAMP_TTL_MS
        ) {
            return CompletableDeferred(SignatureTimestampResult(cached, isAgeRestricted = false))
        }
        stsInFlight?.takeIf { it.isActive }?.let { return it }
        synchronized(this) {
            stsInFlight?.takeIf { it.isActive }?.let { return it }
            val started = stsScope.async { getSignatureTimestampOrNull(videoId) }
            stsInFlight = started
            return started
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                // HOST ONLY, never the URI. This selector serves the client that fetches googlevideo
                // stream URLs, whose query string carries `pot=` and `sig=`. OkHttp happens to pass a
                // query-stripped Address.url today, so this did not leak — but it is one library
                // behaviour change away from writing credentials into the file the user shares, and
                // the attached throwable's own message was going in unbounded. Do not restore `$uri`.
                Timber.tag(TAG).e("Proxy connection failed for host=${uri?.host}: ${ioe?.javaClass?.simpleName}: ${ioe?.message?.take(180)}")
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // FIX B2 (#28.1): the validateStatus() HEAD probe gets its OWN short-timeout client so a slow/blocked
    // candidate URL fails FAST (≈4-5s) instead of stalling start-up all the way to RESOLVE_TIMEOUT_MS (30s).
    // Shares the connection pool / dns / proxy config of httpClient (newBuilder), only the timeouts differ —
    // the MAIN streaming client's 15s timeouts (used by the real byte fetch) are left untouched.
    private val validateHttpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    /**
     * Speculative video-URL prefetch must not contend with a live audio resolve for cipher/PoToken
     * WebViews — that contention starves the ExoPlayer loader thread and cuts mid-song on video tracks.
     */
    val isStreamResolveBusy: Boolean
        get() = CipherDeobfuscator.isBusy || poTokenGenerator.isBusy

    /**
     * Warm up the poToken WebView ahead of the first playback so the first song starts faster. Safe to
     * call any time (no-ops if the session isn't ready yet); never throws.
     *
     * GATE FIX (slow-start): this used to bail when MAIN_CLIENT alone didn't use web PoTokens — but
     * MAIN_CLIENT is ANDROID_VR (useWebPoTokens = false), so the "prewarm" was a silent NO-OP for
     * everyone, and the first song that fell back to a web client (WEB_REMIX, TVHTML5) paid the cold
     * WebView + botguard init (~2-5s, 8s cap) on the loader thread. Prewarm whenever ANY client in the
     * resolve chain needs web PoTokens. Callers decide the tier policy (MusicService gates it to
     * MID/HIGH so low-RAM devices don't pay a startup WebView).
     */
    fun prewarmPoToken() {
        runCatching {
            if (!MAIN_CLIENT.useWebPoTokens && STREAM_FALLBACK_CLIENTS.none { it.useWebPoTokens }) return@runCatching
            val isLoggedIn = YouTube.cookie != null
            // Fallback to visitorData when a logged-in user has a null dataSyncId (e.g. the 0.6.104 migration
            // wrongly cleared it, and it is only re-derived at login) so playback still resolves instead of
            // failing with a null session id. Recovers already-broken 0.6.104 users without a re-login.
            val sessionId = (if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData) ?: YouTube.visitorData
            if (!sessionId.isNullOrBlank()) poTokenGenerator.prewarm(sessionId)
        }
    }

    /**
     * Warm the cipher player.js + WebView ahead of the first play so the first song's URL resolution
     * doesn't pay the ~2.8 MB player.js fetch + WebView-create + JS discovery on the critical path.
     * This fills [iad1tya.echo.music.utils.cipher.PlayerJsFetcher]'s cache (shared by the sig-deobf and
     * EJS n-transform paths) and pre-creates the reused cipher WebView; both stay warm and are reused
     * for every subsequent song. Best-effort — never throws, never blocks. Call off the main thread.
     */
    suspend fun prewarmCipher() {
        runCatching { CipherDeobfuscator.prewarm() }
            .onFailure { Timber.tag(TAG).d("Cipher prewarm skipped: ${it.message}") }
    }

    /**
     * Startup prewarm for the signature timestamp: kicks the shared, memoized async sts computation so
     * the first song's sts-using clients (WEB_REMIX & co.) find it already cached instead of paying
     * NewPipe's ~2.8 MB player.js fetch/parse on the critical path. Fire-and-forget — returns
     * immediately, never throws. The video id is arbitrary (the sts is a per-player-version constant).
     */
    fun prewarmSignatureTimestamp() {
        runCatching { signatureTimestampAsync("dQw4w9WgXcQ") }
    }

    
    // 2026-08-23: rebuilt on the working SimpMusic fingerprint (see YouTubeClient.kt). The old
    // ANDROID_VR_1_43_32 persona was part of the burned Echo Music inheritance — every resolve
    // with it ended in LOGIN_REQUIRED "confirm you're not a bot" in the owner's device logs.
    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_65_10

    // For VIDEO mode we need a client that returns muxed (video+audio) progressive formats — the music/VR
    // clients only return adaptive (separate) streams, so the video URL came back null and only audio
    // played. TVHTML5 reliably exposes itag 18 (360p) / 22 (720p) muxed without web PoTokens.
    private val VIDEO_CLIENT: YouTubeClient = TVHTML5


    private val METADATA_CLIENT: YouTubeClient = WEB

    // 2026-08-23, owner directive: ONLY the providers SimpMusic uses — nothing else may ask YouTube
    // for audio. SimpMusic plays fine on the owner's device/network; every other persona here was
    // either inherited from Echo Music (YouTube-banned) or dependent on broken machinery:
    //   TVHTML5  — UNPLAYABLE "Se debe volver a cargar la página" in every owner log (bot-check)
    //   WEB_REMIX — signatureCipher deobfuscation fails (ParsingException) → its URLs 403 on fetch
    //   MOBILE   — Echo Music-era ANDROID persona, never observed winning
    //   WEB      — same web lineage, useSignatureTimestamp + no direct URLs
    //   ANDROID_VR_NO_AUTH — exact duplicate of MAIN_CLIENT
    // ANDROID_VR_1_65_10 (MAIN_CLIENT) and IOS 19.45.4 are SimpMusic's own fingerprints, copied
    // verbatim from its APK dex; both return direct stream URLs (no cipher, no web poToken), which
    // removes the whole cipher/poToken attack surface from the audio path.
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        IOS
    )
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val isSaavnStream: Boolean = false,
        // Set ONLY when streamUrl came from EmbeddedPlayerUrlResolver's last-resort fallback: the
        // exact request headers YouTube's own embedded player sent for this URL. googlevideo URLs
        // can 403 without the right per-client User-Agent (see MusicService.videoOkHttpClient), and
        // since we don't control which client YouTube's web player used internally, replaying its
        // real headers is the defensive choice. Read once at the resolving call site in
        // MusicService.createDataSourceFactory() — NOT persisted to songUrlCache/Room, so a later
        // re-open that serves the cached URL without re-resolving loses the header replay.
        val fallbackRequestHeaders: Map<String, String>? = null,
    )

    /**
     * Thrown when a song genuinely CANNOT be served by any client — region-locked, premium/members-only,
     * deleted-but-listed, age-restricted-for-guests, some music-video-only / podcast ids, no playable
     * format / no stream URL, or the whole resolution timed out. This is a DEAD-END, NOT a network
     * problem: the loader maps it to [MusicService.ERROR_CODE_NO_STREAM] (never a network code), so the
     * song fails FAST, ONCE, with a clear message + auto-skip instead of blocking the loader for minutes
     * or looping in a fake "no internet" state. [reason] carries the real playability reason for the UI.
     */
    class StreamResolutionException(
        val reason: String,
        cause: Throwable? = null,
    ) : Exception(reason, cause)

    // Hard cap on the WALL-CLOCK time the YouTube resolve (the 12-client fallback loop) may spend before
    // we give up and surface a NO_STREAM dead-end. Only the YouTube resolve is bounded by this — the
    // Qobuz/Saavn budgets in playerResponseForPlayback are independent and unchanged.
    private const val RESOLVE_TIMEOUT_MS = 30_000L

    /**
     * playabilityStatus values meaning "YouTube refused because our SESSION is bad", as opposed to "this
     * video does not exist / is region-blocked / is age-gated". Matched on the STRUCTURED status, never on
     * `reason`: YouTube localises the reason text server-side (the owner's log shows Spanish — "Inicia
     * sesión", "Es necesario volver a cargar la página"), so string matching would work in one language
     * and silently fail in every other.
     *
     * AGE_CHECK_REQUIRED / CONTENT_CHECK_REQUIRED are deliberately NOT here. Age gating is a property of
     * the CONTENT, not of a dead cookie, and retrying it anonymously is guaranteed to fail: isLoggedIn is
     * derived from YouTube.cookie (still set), so the retry re-takes the login-gated WEB_CREATOR branch and
     * never reaches the guest TVHTML5_SIMPLY_EMBEDDED_PLAYER path that exists for exactly this case. Net
     * effect would be 60s instead of 30s before the skip — strictly worse than not retrying.
     */
    private val AUTH_SHAPED_STATUSES = setOf("LOGIN_REQUIRED")

    /**
     * Rechazos con forma de IDENTIDAD, que [AUTH_SHAPED_STATUSES] no puede describir.
     *
     * El registro del dueño (2026-09-16, estable 2.0.42, sesión iniciada) trae 36 vídeos distintos con
     * cero éxitos, todos `UNPLAYABLE` en ANDROID_VR y HTTP 400 en IOS, sin una sola línea de cifrado: las
     * dos identidades del camino de audio mueren antes de firmar. En el mismo teléfono y la misma red, sin
     * sesión, la reproducción iba bien. `UNPLAYABLE` no entra en AUTH_SHAPED_STATUSES porque casi siempre
     * describe al CONTENIDO — y meterlo ahí haría que cada vídeo retirado o bloqueado por región gastase
     * un reintento anónimo inútil. Lo que distingue una cosa de la otra es la racha sobre vídeos
     * DISTINTOS, y eso es lo único que mide [IdentityRejectionTracker].
     *
     * A nivel de proceso a propósito: la señal es "esta sesión está siendo rechazada", no "esta canción
     * falló", así que la tercera canción distinta ya dispara la recuperación que hoy no llega nunca.
     */
    internal val identityRejections = iad1tya.echo.music.utils.IdentityRejectionTracker()

    /**
     * Per-resolve stage timing (slow-start telemetry). ONE instance per playerResponseForPlayback call,
     * threaded down the pipeline; exactly ONE summary line is emitted per completed resolve (success,
     * failure or cancellation) via Timber + PlaybackLogManager, so the shareable playback log turns every
     * slow start into attributable per-stage data. PRIVACY: only the video id and timings/counters/client
     * names are logged — never titles, artists or any other user-identifying data (registry lesson).
     * Overhead is trivial: SystemClock.elapsedRealtime deltas into plain Long fields — no allocations in
     * the client loop; the summary string is built once, at emit time.
     */
    class ResolveTiming(private val videoId: String) {
        private val startedAtMs = SystemClock.elapsedRealtime()
        var dbMs: Long = -1        // caller's pre-resolve Room reads (MusicService runBlocking blocks)
        var qobuzMs: Long = -1     // LOSSLESS-only Qobuz block wall time (-1 = block not entered)
        var saavnMs: Long = -1     // SAAVN/lossless-fallback Saavn block wall time (-1 = not entered)
        var stsMs: Long = -1       // time BLOCKED awaiting the async signature timestamp (-1 = never awaited)
        var potMs: Long = 0        // PoToken generation (initial + lazy) wall time
        var playerMs: Long = 0     // cumulative /player call wall time
        var playerCalls: Int = 0   // /player calls attempted (main + fallbacks, across retries)
        var urlMs: Long = 0        // findUrlOrNull (cipher/NewPipe URL extraction) wall time
        var headMs: Long = 0       // cumulative validateStatus HEAD wall time
        var headCount: Int = 0
        var ntrMs: Long = 0        // n-transform wall time (EJS solver + cipher retry)
        var attempts: Int = 0      // resolvePlaybackData attempts (1 + guest/anonymous retries)
        var winner: String? = null // source that served: "qobuz" / "saavn" / a YouTube client name
        var success: Boolean = false

        fun addStsWait(deltaMs: Long) {
            stsMs = (if (stsMs < 0) 0L else stsMs) + deltaMs
        }

        fun emit() {
            val total = SystemClock.elapsedRealtime() - startedAtMs
            fun ms(v: Long) = if (v < 0) "-" else "${v}ms"
            val line = "id=$videoId total=${total}ms ok=$success db=${ms(dbMs)} sts=${ms(stsMs)} " +
                "pot=${potMs}ms qobuz=${ms(qobuzMs)} saavn=${ms(saavnMs)} " +
                "clients=$playerCalls player=${playerMs}ms url=${urlMs}ms " +
                "head=${headCount}x/${headMs}ms ntr=${ntrMs}ms attempts=$attempts winner=${winner ?: "-"}"
            Timber.tag(TAG).i("RESOLVE_TIMING %s", line)
            PlaybackLogManager.log(PlaybackLogLevel.INFO, "RESOLVE_TIMING", line)
        }
    }

    // NOTE: deliberately NOT an object-level field. Resolves overlap — the media3 loader, the queue
    // PRELOAD loop, downloads, export and preview all resolve concurrently, and a preload's reset would
    // routinely erase the playing track's result (or vice versa) across a window up to RESOLVE_TIMEOUT_MS.
    // The flag is created per call and threaded down instead.

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null,
        knownDurationMs: Long? = null,
        isDownload: Boolean = false,
        // RINGTONE-ONLY opt-in: pick the SMALLEST audio format instead of the Hi-Res one (the ringtone
        // trimmer keeps a few seconds anyway, so transfer size matters more than bitrate). Every other
        // caller keeps the default (false) and its selection is byte-identical to before.
        preferSmallestAudio: Boolean = false,
        // TELEMETRY ONLY: wall time the caller already spent on its own pre-resolve DB reads (the
        // MusicService resolver's runBlocking Room blocks), folded into the RESOLVE_TIMING line. -1 = n/a.
        preResolveDbMs: Long = -1,
    ): Result<PlaybackData> {
        // Slow-start telemetry: exactly ONE summary line per resolve, emitted in `finally` so a caller's
        // timeout-cancellation still leaves an attributable line (those ARE the slow starts). The line
        // carries the video id + stage timings only — never titles/artists.
        val timing = ResolveTiming(videoId)
        timing.dbMs = preResolveDbMs
        try {
            val result = playerResponseForPlaybackImpl(
                videoId, playlistId, audioQuality, connectivityManager, context,
                knownArtist, knownTitle, knownDurationMs, isDownload, preferSmallestAudio, timing,
            )
            timing.success = result.isSuccess
            return result
        } finally {
            timing.emit()
        }
    }

    private suspend fun playerResponseForPlaybackImpl(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context?,
        knownArtist: String?,
        knownTitle: String?,
        knownDurationMs: Long?,
        isDownload: Boolean,
        preferSmallestAudio: Boolean,
        timing: ResolveTiming,
    ): Result<PlaybackData> {
        val showFallbackToast = context?.let {
            it.dataStore.data.first()[iad1tya.echo.music.constants.ShowAudioFallbackToastKey]
        } ?: true

        // 2026-08-23 owner directive: LOSSLESS (Qobuz) and SAAVN (JioSaavn) are non-SimpMusic providers
        // and are removed — resolve them through the YouTube cascade instead of failing.
        @Suppress("NAME_SHADOWING")
        val audioQuality = when (audioQuality) {
            AudioQuality.LOSSLESS, AudioQuality.SAAVN -> AudioQuality.OPUS
            else -> audioQuality
        }

        // Qobuz (LOSSLESS) and JioSaavn (SAAVN) resolution removed (owner directive 2026-09-14): every
        // quality resolves through the YouTube Opus cascade below.

        // BOUND the YouTube resolve (fix #4). The 12-client fallback loop can, for an unserveable song,
        // spend a long time hopping clients + HEAD-validating dead URLs — which the user saw as an endless
        // "loading" / fake "no internet". Cap the wall-clock: on timeout, surface a NO_STREAM dead-end so
        // the loader SKIPS the song instead of hanging. NOTE: resolvePlaybackData's internal runCatching
        // swallows the timeout's CancellationException into a Result.failure, so withTimeoutOrNull returns
        // that failed Result (not null); we detect the swallowed cancellation and remap it to a typed
        // StreamResolutionException so the loader routes it to NO_STREAM (never a network error).
        // Per-call, not shared: see the note next to AUTH_SHAPED_STATUSES.
        val authShaped = java.util.concurrent.atomic.AtomicBoolean(false)
        suspend fun boundedResolve(noLogin: Boolean = false): Result<PlaybackData> {
            val timeoutReason = "La canción tardó demasiado en resolverse"
            authShaped.set(false)
            val r = kotlinx.coroutines.withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                resolvePlaybackData(videoId, playlistId, audioQuality, connectivityManager, preferSmallestAudio = preferSmallestAudio, noLogin = noLogin, authShaped = authShaped, timing = timing)
            } ?: return Result.failure(StreamResolutionException(timeoutReason))
            return if (r.exceptionOrNull() is java.util.concurrent.CancellationException) {
                Result.failure(StreamResolutionException(timeoutReason))
            } else r
        }

        val firstAttempt = boundedResolve()
        // Cualquier acierto limpia la racha: unas pocas canciones legítimamente no disponibles a lo
        // largo de una tarde normal no deben sumarse hasta parecer un rechazo de identidad.
        firstAttempt.onSuccess { identityRejections.recordSuccess() }

        if (firstAttempt.isFailure && YouTube.cookie == null) {
            Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
            PlaybackLogManager.log(PlaybackLogLevel.BOT, "Playback failed for guest", "Triggering bot detection mitigation (rotating guest session)")
            BotDetectionMitigator.rotateGuestSession()
            val retryResult = boundedResolve()
            retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            return finishWithEmbeddedFallback(videoId, retryResult)
        }

        // SIGNED IN and the failure is auth-shaped: retry this ONE song anonymously.
        //
        // Recovery used to be gated on `cookie == null` — guests only — so a logged-in user whose cookie had
        // gone stale had no way back: every track burned the whole 12-client loop up to RESOLVE_TIMEOUT_MS
        // and surfaced as "song unavailable". Nothing detects a dead cookie on its own (forgetAccount is
        // manual), so the only escape was signing out and back in by hand.
        //
        // The account is NOT touched: `noLogin` is threaded per request (same shape as browse(noLogin)),
        // never a temporary write to the shared YouTube.cookie — playback and the crossfade prefetch resolve
        // concurrently, so interleaved save/restore could strand the cookie at null and sign the user out.
        //
        // Independent of the cipher/player-rotation path: playabilityStatus is decided server-side before
        // any signature work, so this survives a correct cipher config.
        if (firstAttempt.isFailure && YouTube.cookie != null &&
            (authShaped.get() || identityRejections.isIdentityShaped)
        ) {
            Timber.tag(TAG).w("Auth-shaped failure while signed in — rotating the guest session and retrying anonymously")
            PlaybackLogManager.log(
                PlaybackLogLevel.BOT,
                "Auth-shaped playback failure while signed in",
                "Your saved YouTube session looks expired — refreshing an anonymous session and retrying this song",
            )
            // Rotate the guest session BEFORE the anonymous retry, exactly as the guest branch above does.
            // The failing signed-in request already fell through login-free clients (MAIN_CLIENT = ANDROID_VR
            // carries no cookie/poToken), so a plain LOGIN_REQUIRED here usually means YouTube has soft-flagged
            // the IP / device / visitorData, NOT just the stored cookie — and a plain anonymous retry would
            // reuse that same flagged visitorData and fail identically. refreshVisitorData() gets a clean
            // anonymous identity. It touches ONLY visitorData (anonymous device id); the account cookie and
            // dataSyncId are never written, so the user stays signed in for library-aware calls.
            runCatching { BotDetectionMitigator.rotateGuestSession() }
            val anonResult = boundedResolve(noLogin = true)
            if (anonResult.isSuccess) {
                Timber.tag(TAG).w("Anonymous retry succeeded — the stored cookie or the old guest session was stale")
                BotDetectionMitigator.notifyPlaybackSuccess()
                PlaybackLogManager.log(
                    PlaybackLogLevel.BOT,
                    "Anonymous retry succeeded",
                    "Playback recovered without the login cookie; signing in again would restore library-aware results",
                )
                return anonResult
            }
        }

        firstAttempt.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
        return finishWithEmbeddedFallback(videoId, firstAttempt)
    }

    /**
     * Called exactly ONCE per [playerResponseForPlaybackImpl] call, only at its true last-resort
     * exit point (never inside [resolvePlaybackData]/[boundedResolve], which can run up to 3 times
     * per song — hooking there would fire the fallback up to 3x, stacking ~10s each). A success
     * here is behaviorally identical to a normal cascade success from the caller's point of view;
     * a null (fallback also failed/timed out/backed off) preserves today's exact failure, just
     * ~10s later.
     */
    private suspend fun finishWithEmbeddedFallback(videoId: String, result: Result<PlaybackData>): Result<PlaybackData> {
        // 2026-08-23, owner directive: audio may ONLY be requested through the SimpMusic providers
        // (MAIN_CLIENT ANDROID_VR 1.65.10 + IOS fallback). The embedded-player scrape was a non-Simp
        // request shape that every owner log showed hard-blocked anyway, and it cost ~10s of WebView
        // per failing song. Disabled outright — the cascade result stands as-is.
        return result
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferVideo: Boolean = false,
        videoMaxHeight: Int? = null,
        preferSmallestAudio: Boolean = false,
        // Resolve this ONE song without the login cookie (stale-session recovery). Threaded down to every
        // YouTube.player call below rather than mutating the shared YouTube.cookie, which is not safe:
        // playback and the crossfade prefetch resolve concurrently.
        noLogin: Boolean = false,
        // Reports back whether any client refused for session reasons. Caller-owned so concurrent
        // resolves cannot clobber each other.
        authShaped: java.util.concurrent.atomic.AtomicBoolean? = null,
        // Slow-start telemetry sink, owned by playerResponseForPlayback. Null for the video-mode paths
        // (videoStreamUrl/videoStreamUrlDiag), which then simply record nothing.
        timing: ResolveTiming? = null,
    ): Result<PlaybackData> = runCatching {
        if (timing != null) timing.attempts++
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data", "Video: $videoId")
        
        
        println("[PLAYBACK_DEBUG] playerResponseForPlayback called: videoId=$videoId, playlistId=$playlistId")
        
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

        // `&& !noLogin` is what makes the anonymous retry ACTUALLY anonymous. Without it this recompute
        // stayed true on the retry, so the poToken below was minted with the account's dataSyncId (:599)
        // and the request went out WITHOUT the cookie — a session bound to an account, sent with no auth,
        // which YouTube rejects as LOGIN_REQUIRED. That is precisely what the owner's Redmi log shows:
        // "Auth-shaped failure ... retrying anonymously" immediately followed by another LOGIN_REQUIRED.
        // With this, the whole resolve behaves as a guest on the retry — visitorData poToken, guest client
        // selection, no logged-in metadata fetch — which is how logged-out playback normally works.
        val isLoggedIn = YouTube.cookie != null && !noLogin
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}${if (noLogin) " (anonymous retry)" else ""}")


        // ASYNC sts (slow-start fix): started NOW so it computes in parallel with the network calls, and
        // awaited ONLY at clients that actually send it (client.useSignatureTimestamp — InnerTube discards
        // it otherwise, so the request bytes per client are identical to before). The old eager call
        // serialized a potentially multi-second cold NewPipe player.js parse ahead of EVERY first resolve
        // for a value the main client (ANDROID_VR) throws away. Only WHEN the work happens changed.
        val stsDeferred = signatureTimestampAsync(videoId)
        suspend fun awaitSts(): SignatureTimestampResult {
            val stsWaitStartMs = SystemClock.elapsedRealtime()
            val sts = stsDeferred.await()
            timing?.addStsWait(SystemClock.elapsedRealtime() - stsWaitStartMs)
            return sts
        }

        
        var poToken: PoTokenResult? = null
        // `?: visitorData` is NOT redundant — it is the recovery path for registry #29. dataSyncId is only
        // ever derived AT LOGIN (unlike visitorData, which is re-fetched), so a logged-in account left with a
        // null dataSyncId produces sessionId == null, no poToken is generated for any poToken-requiring
        // client, those clients fail, and the resolve burns its 30s budget hopping between them — surfacing
        // to the user as "this song is unavailable". prewarmPoToken already had this fallback; the REAL
        // resolve path did not, which is why the recovery never actually reached anyone.
        val sessionId = (if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData) ?: YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            Timber.tag(logTag).d("Generating PoToken for MAIN_CLIENT with sessionId")
            val potStartMs = SystemClock.elapsedRealtime()
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully")
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
            }
            timing?.let { it.potMs += SystemClock.elapsedRealtime() - potStartMs }
        }

        
        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying ${MAIN_CLIENT.clientName} (Main)")

        // Run the main stream request and the optional metadata request IN PARALLEL — they are independent
        // network calls, so overlapping them (instead of back-to-back) cuts the start time. The metadata
        // (watch-history + loudness audioConfig) needs no stream poToken; it is time-capped so it can never
        // delay playback, and audioConfig/videoDetails fall back to the main response when it is null.
        val resolved = kotlinx.coroutines.coroutineScope {
            val metadataDeferred = if (isLoggedIn) {
                async(kotlinx.coroutines.Dispatchers.IO) {
                    // The sts wait gets its OWN bounded budget so a cold player.js parse can't consume
                    // the metadata fetch's 3s window (that starved the side-fetch on first-resolve cold
                    // starts → watch-history/audioConfig silently fell back to the main response). If the
                    // sts isn't ready in time the request goes WITHOUT it — InnerTube simply omits
                    // playbackContext, and the metadata fields we consume don't need signed formats.
                    // (Raw await, not awaitSts: this parallel wait must not pollute the sts= metric.)
                    val stsForMeta = if (METADATA_CLIENT.useSignatureTimestamp) {
                        kotlinx.coroutines.withTimeoutOrNull(2500L) {
                            runCatching { stsDeferred.await().timestamp }.getOrNull()
                        }
                    } else null
                    kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        runCatching {
                            YouTube.player(
                                videoId, playlistId, METADATA_CLIENT,
                                stsForMeta,
                                null, noLogin = noLogin,
                            ).getOrNull()
                        }.getOrNull()
                    }
                }
            } else null
            // Video mode needs a client that actually returns VIDEO adaptive formats. MAIN_CLIENT
            // (ANDROID_VR) is audio-focused and returns no usable video, so for video we query
            // VIDEO_CLIENT (TVHTML5), which reliably returns adaptive video. The player then merges this
            // video-only track with the (separately resolved, MAIN_CLIENT) audio track. Audio is untouched.
            // getOrNull (NOT getOrThrow): a MAIN_CLIENT failure / non-OK response must NOT abort the whole
            // resolution (fix #5). A null (or non-OK) main response now falls through to the
            // STREAM_FALLBACK_CLIENTS loop (startIndex is forced to 0 below when main is null), so
            // region-locked / members-only / deleted-but-listed songs still get EVERY fallback client
            // instead of dead-ending on the very first client.
            val main = run {
                // 2026-08-23: the logged-in skip that used to live here was a workaround for the OLD
                // burned ANDROID_VR_1_43_32 persona (hard LOGIN_REQUIRED 100% of the time). With the
                // SimpMusic 1.65.10 fingerprint the evidence flipped — owner's device log 14:15 shows
                // MAIN_CLIENT winning for logged-in resolves — while the skip kept routing logged-in
                // songs into the burned TVHTML5/WEB_REMIX fallbacks. MAIN_CLIENT now always gets its
                // shot; ANDROID_VR is loginSupported=false so it carries no cookie even when signed in.
                // Await the async sts only when this client sends it (VIDEO_CLIENT/TVHTML5 does;
                // MAIN_CLIENT/ANDROID_VR never) — the wait (if any) lands in sts=, the call in player=.
                val mainClient = if (preferVideo) VIDEO_CLIENT else MAIN_CLIENT
                val mainSts = if (mainClient.useSignatureTimestamp) awaitSts().timestamp else null
                val mainCallStartMs = SystemClock.elapsedRealtime()
                // .onFailure BEFORE .getOrNull(): the main client's real error (403, a parse break, a
                // rejected poToken) used to be destroyed here, so a fleet-wide main-client outage and a
                // single region-locked song produced the SAME log line — "no response". Naming the
                // client and the exception is what makes those two distinguishable without guessing.
                val response = if (preferVideo) {
                    YouTube.player(
                        videoId, playlistId, VIDEO_CLIENT,
                        mainSts, null, noLogin = noLogin,
                    ).onFailure { logClientFailure(VIDEO_CLIENT.clientName, videoId, it) }.getOrNull()
                } else {
                    YouTube.player(
                        videoId, playlistId, MAIN_CLIENT,
                        mainSts, poToken?.playerRequestPoToken, noLogin = noLogin,
                    ).onFailure { logClientFailure(MAIN_CLIENT.clientName, videoId, it) }.getOrNull()
                }
                timing?.let {
                    it.playerMs += SystemClock.elapsedRealtime() - mainCallStartMs
                    it.playerCalls++
                }
                response
            }
            main to metadataDeferred?.await()
        }
        var mainPlayerResponse = resolved.first
        var metadataResponse: PlayerResponse? = resolved.second

        
        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse?.playabilityStatus?.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse?.playabilityStatus?.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse?.videoDetails?.title}, videoId=${mainPlayerResponse?.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse?.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse?.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        
        
        
        
        
        val mainStatus = mainPlayerResponse?.playabilityStatus?.status
        val isAgeRestrictedFromResponse = mainStatus != null && mainStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {

            Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
            Timber.tag(TAG).i("Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null, noLogin = noLogin).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        } else if (isAgeRestrictedFromResponse && !isLoggedIn) {
            // GUEST age-restricted path (fix #5): WEB_CREATOR is login-gated, so signed-out users used to
            // dead-end on age-restricted content. The TV embedded player commonly serves age-gated streams
            // WITHOUT auth — try it so age restriction isn't a guaranteed dead-end for guests.
            Timber.tag(logTag).d("Age-restricted (guest), trying embedded player TVHTML5_SIMPLY_EMBEDDED_PLAYER")
            Timber.tag(TAG).i("Age-restricted (guest): using TVHTML5_SIMPLY_EMBEDDED_PLAYER for videoId=$videoId")
            val embedResponse = YouTube.player(videoId, playlistId, TVHTML5_SIMPLY_EMBEDDED_PLAYER, null, null, noLogin = noLogin).getOrNull()
            if (embedResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("Embedded player works for age-restricted (guest) content")
                mainPlayerResponse = embedResponse
                usedAgeRestrictedClient = TVHTML5_SIMPLY_EMBEDDED_PLAYER
            }
        }

        // NOTE (fix #5): a null mainPlayerResponse is NO LONGER a hard failure/abort. We fall through to
        // the STREAM_FALLBACK_CLIENTS loop (startIndex forced to 0 below when main is null) so every
        // fallback client still gets a chance instead of dead-ending here.
        if (mainPlayerResponse == null) {
            Timber.tag(logTag).w("MAIN_CLIENT returned no response; continuing into fallback clients from index 0")
        }



        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse?.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse?.videoDetails
        val playbackTracking = metadataResponse?.playbackTracking ?: mainPlayerResponse?.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        // Carries the most recent real playability reason seen while iterating clients, so an
        // all-clients-exhausted dead-end can surface WHY (region-locked, members-only, …) to the user.
        var lastPlayabilityReason: String? = null
        var retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null


        val currentStatus = mainPlayerResponse?.playabilityStatus?.status
        var isAgeRestricted = currentStatus != null && currentStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        )

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content is still age-restricted (status: $currentStatus), will try fallback clients")
            Timber.tag(TAG).i("Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }


        val isPrivateTrack = mainPlayerResponse?.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"




        val startIndex = when {
            mainPlayerResponse == null -> 0   // no main response to reuse → straight into the fallback clients
            isPrivateTrack -> 1
            isAgeRestricted -> 0
            else -> -1
        }

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            
            val client: YouTubeClient
            if (clientIndex == -1) {
                
                client = if (preferVideo) VIDEO_CLIENT else (usedAgeRestrictedClient ?: MAIN_CLIENT)
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from ${if (preferVideo) "VIDEO_CLIENT" else "MAIN_CLIENT"}: ${client.clientName}")
            } else {
                
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying fallback [${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}]", client.clientName)

                // `&& YouTube.cookie == null` dropped: it was redundant — before noLogin, isLoggedIn WAS
                // (cookie != null), so `!isLoggedIn && cookie == null` reduced to `cookie == null`, i.e.
                // just `!isLoggedIn`. Now that isLoggedIn also encodes noLogin, keeping the raw cookie check
                // would WRONGLY try login-required clients on the anonymous retry (global cookie still set),
                // sending them with no auth → LOGIN_REQUIRED again. `!isLoggedIn` alone is correct in both.
                if (client.loginRequired && !isLoggedIn) {

                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                
                if (client.useWebPoTokens && poToken == null && sessionId != null) {
                    Timber.tag(logTag).d("Lazily generating PoToken for fallback web client: ${client.clientName}")
                    val lazyPotStartMs = SystemClock.elapsedRealtime()
                    try {
                        poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "Lazy PoToken generation failed")
                    }
                    timing?.let { it.potMs += SystemClock.elapsedRealtime() - lazyPotStartMs }
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")

                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                if (client.useWebPoTokens && clientPoToken == null) {
                    // This client type REQUIRES a poToken to avoid the bot check — sending it without
                    // one is a predictable LOGIN_REQUIRED, indistinguishable in the shared log from "we
                    // sent a token and YouTube rejected it anyway" (a much more serious, server-side
                    // signal). Without this line both looked like the same bare "Client failed".
                    Timber.tag(logTag).w("Sending ${client.clientName} WITHOUT a poToken (generation unavailable/failed)")
                    PlaybackLogManager.log(
                        PlaybackLogLevel.WARNING,
                        "No poToken",
                        "${client.clientName} request sent with no poToken"
                    )
                }

                // Await the async sts only for clients that send it (null otherwise — identical request,
                // since InnerTube already discarded it for !useSignatureTimestamp clients).
                val clientSigTimestamp =
                    if (wasOriginallyAgeRestricted || !client.useSignatureTimestamp) null
                    else awaitSts().timestamp
                val clientCallStartMs = SystemClock.elapsedRealtime()
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken, noLogin = noLogin)
                        .onFailure { logClientFailure(client.clientName, videoId, it) }
                        .getOrNull()
                timing?.let {
                    it.playerMs += SystemClock.elapsedRealtime() - clientCallStartMs
                    it.playerCalls++
                }
            }

            
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                val resolvedClientName = if (clientIndex == -1) (if (preferVideo) VIDEO_CLIENT else MAIN_CLIENT).clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName
                Timber.tag(logTag).d("Player response status OK for client: $resolvedClientName")
                PlaybackLogManager.log(PlaybackLogLevel.INFO, "Player response OK", resolvedClientName)

                
                val hasDirectUrls = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.url.isNullOrEmpty() } == true
                val hasSignatureCipher = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() } == true

                Timber.tag(logTag).d("URL check: hasDirectUrls=$hasDirectUrls, hasSignatureCipher=$hasSignatureCipher")

                
                val responseToUse = streamPlayerResponse

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                        preferVideo,
                        videoMaxHeight,
                        preferSmallestAudio,
                    )

                if (format == null) {
                    // playabilityStatus OK does not guarantee a usable format: guest/restricted
                    // sessions can return OK with an adaptiveFormats list that has nothing audio
                    // playable in it. Without this, that case looked identical in the shared log to
                    // "this client was never tried" — undistinguishable from a real bot-block.
                    val fmtCount = responseToUse.streamingData?.adaptiveFormats?.size ?: 0
                    Timber.tag(logTag).w("No suitable format found for client: $resolvedClientName despite OK status (adaptiveFormats=$fmtCount)")
                    PlaybackLogManager.log(
                        PlaybackLogLevel.WARNING,
                        "No usable format",
                        "$resolvedClientName OK but adaptiveFormats=$fmtCount had none usable"
                    )
                    continue
                }

                Timber.tag(logTag).d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                val urlStartMs = SystemClock.elapsedRealtime()
                val urlResult = findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                streamUrl = urlResult?.url
                timing?.let { it.urlMs += SystemClock.elapsedRealtime() - urlStartMs }
                if (streamUrl == null) {
                    // Distinguishes, without logging the URL/cipher itself: "format had nothing to
                    // work with" (hasUrl=false hasCipher=false — an OK response that omitted stream
                    // data) from "we had a cipher and deobfuscation genuinely failed" (hasCipher=true).
                    // These look identical as a bare "Stream URL not found" and were the open question
                    // this whole resolve chain could not previously answer from the shared log alone.
                    val hasUrl = !format.url.isNullOrEmpty()
                    val hasCipher = !format.signatureCipher.isNullOrEmpty() || !format.cipher.isNullOrEmpty()
                    Timber.tag(logTag).w(
                        "Stream URL not found: client=$resolvedClientName itag=${format.itag} hasUrl=$hasUrl hasCipher=$hasCipher"
                    )
                    PlaybackLogManager.log(
                        PlaybackLogLevel.WARNING,
                        "No stream URL",
                        "$resolvedClientName itag=${format.itag} hasUrl=$hasUrl hasCipher=$hasCipher"
                    )
                    continue
                }

                
                val currentClient = if (clientIndex == -1) {
                    if (preferVideo) VIDEO_CLIENT else (usedAgeRestrictedClient ?: MAIN_CLIENT)
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                
                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                
                // NewPipe-sourced URLs carry an ALREADY-deobfuscated "n" — re-running the transform
                // on them scrambles a good value and the fetch 403s. Only transform InnerTube-sourced URLs.
                val nAlreadyDecoded = urlResult?.nAlreadyDeobfuscated == true
                val needsNTransform = !nAlreadyDecoded && (currentClient.useWebPoTokens || streamUrl?.let { Regex("[?&]n=").containsMatchIn(it) } == true)
                if (needsNTransform) {
                    try {
                        Timber.tag(logTag).d("Applying n-transform to stream URL for ${currentClient.clientName}")
                        val ntrStartMs = SystemClock.elapsedRealtime()
                        val transformed = EjsNTransformSolver.transformNParamInUrl(streamUrl!!)
                        timing?.let { it.ntrMs += SystemClock.elapsedRealtime() - ntrStartMs }
                        if (transformed != streamUrl) {
                            streamUrl = transformed
                            Timber.tag(logTag).d("N-transform applied successfully")
                        }
                    } catch (e: Exception) {
                        Timber.tag(logTag).e(e, "N-transform failed: ${e.message}")
                    }
                }

                
                
                if (currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null) {
                    Timber.tag(logTag).d("Appending pot= parameter to stream URL")
                    val separator = if ("?" in streamUrl!!) "&" else "?"
                    streamUrl = "${streamUrl}${separator}pot=${poToken.streamingDataPoToken}"
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).d("Stream expiration time not found")
                    continue
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                
                val urlHost = try { java.net.URL(streamUrl).host } catch (e: Exception) { "unknown" }
                Timber.tag(logTag).d("Stream URL host: $urlHost, pot length: ${poToken?.streamingDataPoToken?.length ?: 0}")

                
                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1 || isPrivatelyOwned) {
                    
                    if (isPrivatelyOwned) {
                        Timber.tag(logTag).d("Skipping validation for privately owned track: ${currentClient.clientName}")
                        println("[PLAYBACK_DEBUG] Using stream without validation for PRIVATELY_OWNED_TRACK")
                    } else {
                        Timber.tag(logTag).d("Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    }
                    // WHICH CLIENT ACTUALLY SERVED THE STREAM — the single most useful playback
                    // diagnostic in the app, and it used to go through android.util.Log, which reaches
                    // logcat and NOTHING else. A customer cannot send logcat. Timber routes it into
                    // app.log, so "no reproduce" reports now say which of the twelve clients worked (or
                    // that none did) instead of leaving the owner to guess the cascade.
                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId, private=$isPrivatelyOwned")
                    timing?.winner = currentClient.clientName
                    break
                }

                val headStartMs = SystemClock.elapsedRealtime()
                val headOk = validateStatus(streamUrl!!, currentClient.userAgent)
                timing?.let {
                    it.headMs += SystemClock.elapsedRealtime() - headStartMs
                    it.headCount++
                }
                if (headOk) {
                    // FIX B3 (#28.1): SHORT-CIRCUIT — the moment ANY client (including the MAIN client at
                    // clientIndex == -1, tried FIRST) yields a validated, directly-usable URL we break out of
                    // the loop and return it immediately, WITHOUT probing the remaining fallback clients. The
                    // full fallback chain still runs only when the main client fails to validate.
                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    PlaybackLogManager.log(PlaybackLogLevel.INFO, "Stream validated", currentClient.clientName)

                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    timing?.winner = currentClient.clientName
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")

                    // Same guard as the first pass: never re-transform an already-deobfuscated NewPipe URL.
                    val needsNTransformFallback = !nAlreadyDecoded && (currentClient.useWebPoTokens || streamUrl?.let { Regex("[?&]n=").containsMatchIn(it) } == true)
                    if (needsNTransformFallback) {
                        var nTransformWorked = false

                        
                        try {
                            val ntrRetryStartMs = SystemClock.elapsedRealtime()
                            val nTransformed = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                            timing?.let { it.ntrMs += SystemClock.elapsedRealtime() - ntrRetryStartMs }
                            if (nTransformed != streamUrl) {
                                Timber.tag(logTag).d("CipherDeobfuscator n-transform applied, re-validating...")
                                val retryHeadStartMs = SystemClock.elapsedRealtime()
                                val retryHeadOk = validateStatus(nTransformed, currentClient.userAgent)
                                timing?.let {
                                    it.headMs += SystemClock.elapsedRealtime() - retryHeadStartMs
                                    it.headCount++
                                }
                                if (retryHeadOk) {
                                    Timber.tag(logTag).d("N-transformed URL VALIDATED OK!")
                                    streamUrl = nTransformed
                                    nTransformWorked = true
                                    Timber.tag(TAG).i("Playback: client=${currentClient.clientName}, videoId=$videoId (cipher n-transform)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(logTag).e(e, "CipherDeobfuscator n-transform error")
                        }

                        if (nTransformWorked) {
                            timing?.winner = currentClient.clientName
                            break
                        }
                    }
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status ?: "Unknown"
                val reason = streamPlayerResponse?.playabilityStatus?.reason ?: "No reason"
                // Remember the real reason (e.g. region/premium/members) so an all-clients-exhausted
                // dead-end can tell the user WHY instead of a generic failure.
                streamPlayerResponse?.playabilityStatus?.reason?.let { lastPlayabilityReason = it }
                if (status in AUTH_SHAPED_STATUSES) authShaped?.set(true)
                // Solo con sesión iniciada: sin cookie no hay identidad que YouTube pueda rechazar,
                // y la rama de invitado ya tiene su propia rotación unas líneas más arriba.
                if (status == "UNPLAYABLE" && YouTube.cookie != null) {
                    identityRejections.recordRejection(videoId)
                }
                Timber.tag(logTag).d("Player response status not OK: $status, reason: $reason")
                PlaybackLogManager.log(PlaybackLogLevel.WARNING, "Client failed: ${client.clientName}", "$status: $reason")
                
                
                Timber.tag(logTag).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            // DEAD-END (fix #1): no client could serve this song. Typed so the loader maps it to NO_STREAM
            // (skip + message), NEVER a network code — carry the real reason when we captured one.
            throw StreamResolutionException(lastPlayabilityReason ?: "No hay ninguna fuente disponible para esta canción")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            // DEAD-END (fix #1): carry the real playability reason (region/premium/members/…) so the loader
            // maps it to NO_STREAM with that reason instead of a generic REMOTE_ERROR silent pause.
            throw StreamResolutionException(errorReason ?: lastPlayabilityReason ?: "Esta canción no está disponible")
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw StreamResolutionException(lastPlayabilityReason ?: "No se pudo obtener el stream de esta canción")
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw StreamResolutionException(lastPlayabilityReason ?: "No hay un formato reproducible para esta canción")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            throw StreamResolutionException(lastPlayabilityReason ?: "No se pudo obtener el enlace de esta canción")
        }

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl?.take(100)}...")
        }
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }.onFailure { e ->
        // HALLAZGO-056: teardown cancellations (scope left the composition, track skipped, preview
        // dismissed) used to be persisted here as ERROR "Playback failed" — pure noise that buried
        // the real failures in the shared log. They drop to debug; only genuine failures stay ERROR.
        if (isBenignResolveCancellation(e)) {
            Timber.tag(logTag).d("Playback resolution cancelled for videoId=$videoId: ${e.message}")
            return@onFailure
        }
        Timber.tag(logTag).e(e, "Playback resolution failed")
        PlaybackLogManager.log(PlaybackLogLevel.ERROR, "Playback failed", "${e::class.simpleName}: ${e.message}")


        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }
    
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) 
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    /**
     * Resolves a muxed (video+audio) progressive stream URL for [videoId], reusing the same
     * multi-client + cipher pipeline as audio. Returns null if no muxed format is available.
     */
    suspend fun videoStreamUrl(
        videoId: String,
        connectivityManager: ConnectivityManager,
        videoMaxHeight: Int? = null,
    ): String? = resolvePlaybackData(
        videoId = videoId,
        audioQuality = AudioQuality.OPUS,
        connectivityManager = connectivityManager,
        preferVideo = true,
        videoMaxHeight = videoMaxHeight,
    ).getOrNull()?.streamUrl

    /**
     * Diagnostic variant of [videoStreamUrl]: returns the failure REASON instead of swallowing it, plus
     * a count of how many video adaptive formats the chosen client actually exposed — so the UI can show
     * exactly why video mode failed (no format vs network vs playability vs decipher).
     */
    suspend fun videoStreamUrlDiag(
        videoId: String,
        connectivityManager: ConnectivityManager,
        videoMaxHeight: Int? = null,
    ): Result<String> = resolvePlaybackData(
        videoId = videoId,
        audioQuality = AudioQuality.OPUS,
        connectivityManager = connectivityManager,
        preferVideo = true,
        videoMaxHeight = videoMaxHeight,
    ).mapCatching { it.streamUrl }

    /**
     * HALLAZGO-035: the SHARED video resolution for every export/download-as-video path (format
     * chooser probe, AudioExportService mux, DownloadUtil video downloads). NewPipe's ANDROID_VR
     * extraction first — the SAME live source in-app video mode uses — with the old InnerTube
     * [videoStreamUrlDiag] (TVHTML5, burned) only as fallback. The mux maps `0:v:0 + 1:a:0`, so a
     * muxed NewPipe result (itag 22/18) still works: the separate higher-quality audio track wins.
     * The blocking NewPipe call hops to [Dispatchers.IO] — callers may invoke this from Main.
     */
    suspend fun videoStreamUrlForExport(
        videoId: String,
        connectivityManager: ConnectivityManager,
        videoMaxHeight: Int? = null,
    ): Result<String> {
        val pipe = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            adaptiveVideoStreamNewPipe(videoId, connectivityManager, videoMaxHeight)
        }
        val pipeUrl = pipe.getOrNull()?.url
        if (!pipeUrl.isNullOrBlank()) return Result.success(pipeUrl)
        Timber.tag(logTag).w(
            "NewPipe video resolve failed for export (videoId=$videoId): " +
                "${pipe.exceptionOrNull()?.javaClass?.simpleName}; falling back to InnerTube diag",
        )
        val diagUrl = videoStreamUrlDiag(videoId, connectivityManager, videoMaxHeight).getOrNull()
        return pickVideoExportUrl(pipeUrl, diagUrl)?.let { Result.success(it) }
            ?: Result.failure(StreamResolutionException("No video stream available for this song"))
    }

    /**
     * SimpMusic-provider fallback for video mode, now ADAPTIVE like YouTube itself. With the
     * PipePipeExtractor fork (extraction client ANDROID_VR, not burned by the bot-check) the full
     * video-only set arrives (itag 137/136/135/134...) instead of ONLY muxed itag 18 (fixed 360p).
     * Picks the best H.264 video-only stream at/below the connection-dependent cap — the SAME rule
     * InnerTube's findFormat uses (WiFi up to 720p, mobile data up to 360p, TV passes its own cap) —
     * and the caller MERGES it with the track's normal audio (real HD video + full audio). Only when
     * a video has no video-only format at all (extremely rare) it falls back to muxed itag 22 ?: 18,
     * which carries audio. Returns (streamUrl, isMuxed); callers register muxed results so the
     * MediaSource factory skips the audio merge.
     */
    /**
     * Lo que [adaptiveVideoStreamNewPipe] elegió. El **itag** viaja con la URL porque sin él no se puede
     * DESCARTAR un formato que ya reventó: el modo video reintentaba el mismo itag 136 tres veces
     * (registro del dueño 2026-09-16) y los tres intentos eran el mismo intento. Ver [iad1tya.echo.music.playback.VideoFormatFallback].
     */
    data class PickedVideo(val url: String, val isMuxed: Boolean, val itag: Int)

    /**
     * @param excludeItags itags que NO se pueden volver a elegir para este video (ya fallaron el sniff
     *        del contenedor en esta sesión). Si los excluidos se comen todos los video-only, cae solo
     *        al muxed — que es exactamente el siguiente peldaño de la escalera.
     * @param forceMuxed salta directamente al progresivo 22/18, el peldaño final: lleva el audio dentro
     *        y es el que más veces sobrevive cuando los adaptativos no se dejan leer.
     */
    fun adaptiveVideoStreamNewPipe(
        videoId: String,
        connectivityManager: ConnectivityManager,
        maxHeight: Int? = null,
        excludeItags: Set<Int> = emptySet(),
        forceMuxed: Boolean = false,
    ): Result<PickedVideo> = runCatching {
        val streams = NewPipeExtractor.newPipePlayer(videoId)
        val itags = streams.map { it.first }
        Timber.tag(logTag).d("PipePipe video fallback lookup returned itags=$itags")
        // Video-only H.264 (avc1) itags → real pixel height, per YouTube's format table.
        val avcHeights = mapOf(138 to 2160, 137 to 1080, 136 to 720, 135 to 480, 134 to 360, 133 to 240, 160 to 144)
        val candidates = streams
            .filterNot { it.first in excludeItags }
            .mapNotNull { s -> avcHeights[s.first]?.let { h -> Triple(s.first, h, s.second) } }
            .sortedByDescending { it.second }
        val cap = maxHeight ?: if (connectivityManager.isActiveNetworkMetered) 360 else 720
        // Best stream that fits the cap; if the network cap is below every available format, take the
        // smallest one instead of failing (video always plays, never above the bandwidth budget).
        val pick = if (forceMuxed) null else (candidates.firstOrNull { it.second <= cap } ?: candidates.lastOrNull())
        if (pick != null) {
            val excludedNote = if (excludeItags.isEmpty()) "" else ", excluding=$excludeItags"
            Timber.tag(logTag).i(
                "PipePipe adaptive video picked itag=${pick.first} (${pick.second}p, cap=${cap}p$excludedNote)",
            )
            PickedVideo(pick.third, isMuxed = false, itag = pick.first)
        } else {
            val muxed = iad1tya.echo.music.playback.VideoFormatFallback.MUXED_ITAGS
                .filterNot { it in excludeItags }
                .firstNotNullOfOrNull { want -> streams.firstOrNull { it.first == want }?.let { want to it.second } }
                ?: throw IllegalStateException("PipePipe returned no usable video stream (itags=$itags)")
            val why = if (forceMuxed) "last rung of the fallback ladder" else "no video-only formats left"
            Timber.tag(logTag).i("PipePipe adaptive video fell back to muxed itag=${muxed.first} ($why)")
            PickedVideo(muxed.second, isMuxed = true, itag = muxed.first)
        }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferVideo: Boolean = false,
        videoMaxHeight: Int? = null,
        preferSmallestAudio: Boolean = false,
    ): PlayerResponse.StreamingData.Format? {
        if (preferVideo) {
            // Video mode resolves an ADAPTIVE VIDEO-ONLY stream (no audio) — MusicService MERGES it with the
            // track's normal audio source, giving real HD. Muxed (streamingData.formats) only reliably offers
            // 360p (itag 18); the 720p muxed (itag 22) is gone for most videos. Video-only adaptive, by
            // contrast, exposes 360/480/720/1080 for virtually every video. Pick by REAL pixel height and the
            // connection: WiFi up to 720p, mobile data up to 360p (lighter, fewer stalls). Prefer H.264/mp4
            // (widest ExoPlayer compatibility, smooth on low-end), else any video-only. null only if a video
            // has no video-only format at all (extremely rare) → caller keeps audio + "no disponible".
            val metered = connectivityManager.isActiveNetworkMetered
            // On TV (big screen) the caller passes an explicit cap (1080p Full HD) so video mode reaches true FHD
            // via a VIDEO-ONLY adaptive stream (merged with a separate audio track in MusicService). Phones/tablets
            // pass null → keep the existing metered-aware cap (720p WiFi / 360p data) EXACTLY as before.
            val targetHeight = videoMaxHeight ?: if (metered) 360 else 720
            
            // Search BOTH adaptiveFormats (video-only) and formats (muxed) to ensure we always find a video stream if one exists.
            val allFormats = (playerResponse.streamingData?.adaptiveFormats ?: emptyList()) + 
                             (playerResponse.streamingData?.formats ?: emptyList())
                             
            val videoOnly = allFormats
                .filter { !it.url.isNullOrEmpty() || !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() }
                .filter { !it.isAudio && it.mimeType.startsWith("video/") }
                
            if (videoOnly.isNullOrEmpty()) return null
            // H.264 ONLY = widest hardware-decode compatibility on low-end. Match the codec token (avc1/avc3),
            // NOT the "mp4" container — AV1 is ALSO delivered as video/mp4 (codecs="av01...") and many low-end
            // devices can't hardware-decode it (→ decode error or stuttering software decode). VP9 excluded too.
            val avc = videoOnly.filter {
                val mt = it.mimeType.lowercase()
                (mt.contains("avc1") || mt.contains("avc3")) && !mt.contains("av01") && !mt.contains("vp9") && !mt.contains("vp09")
            }
            val pool = if (avc.isNotEmpty()) avc else videoOnly
            // Highest quality at or below the target height; if none qualifies, the lowest available.
            return pool.filter { (it.height ?: 0) <= targetHeight }.maxByOrNull { it.height ?: 0 }
                ?: pool.minByOrNull { it.height ?: Int.MAX_VALUE }
        }

        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        // NOTE (audit L10): we deliberately do NOT cap audio bitrate on mobile data — this is a Hi-Res player,
        // so audio always streams at full quality (only VIDEO downgrades on metered, above). The `when` below
        // is intentionally uniform; audio quality is honoured via the original-stream preference.
        //
        // REGION-SAFE fallback: prefer the ORIGINAL (untagged) audio track, BUT on auto-dub regions/accounts
        // YouTube tags EVERY adaptive audio format with an audioTrack, so `isOriginal` matches nothing and the
        // old code returned null → the song failed to play ("works on my device, not on others"). Fall back to
        // a non-auto-dubbed track, then to ANY audio, so playback always resolves. The dev-device path is
        // unchanged (original still wins when it exists).
        val audioFormats = playerResponse.streamingData?.adaptiveFormats?.filter { it.isAudio }

        // EVERY VIDEO MUST BE PLAYABLE AS AUDIO — owner report 2026-09-16: "algunos videos solo
        // reproducían video". When a response carries no audio-only track, the progressive (muxed) list
        // still does: itag 18/22 hold picture and sound in one file. `isAudio` is `width == null`, so a
        // progressive stream reads as "not audio" and this resolver never looked at it — while the video
        // branch above deliberately searches BOTH lists. That asymmetry is the whole bug: the video path
        // found a stream, the audio path found nothing, and the song ended up playable only as video.
        //
        // Runs ONLY when there is no adaptive audio at all, so every normal track resolves exactly as
        // before. The choice of WHICH progressive stream lives in ProgressiveAudioFallback so it can be
        // tested without a device.
        if (audioFormats.isNullOrEmpty()) {
            val progressive = playerResponse.streamingData?.formats.orEmpty()
            val metered = connectivityManager.isActiveNetworkMetered
            val saverOn = PrefsBridge.peek(iad1tya.echo.music.constants.DataSaverEnabledKey) == true
            val index =
                iad1tya.echo.music.playback.ProgressiveAudioFallback.pickIndex(
                    streams =
                        progressive.map {
                            iad1tya.echo.music.playback.ProgressiveAudioFallback.Stream(
                                itag = it.itag,
                                bitrate = it.bitrate,
                                // A progressive entry without an audio track would play as silence, which
                                // is worse than the failure it replaces.
                                hasAudioTrack = it.audioSampleRate != null ||
                                    it.audioChannels != null ||
                                    it.audioQuality != null,
                                hasUrl = !it.url.isNullOrEmpty() ||
                                    !it.signatureCipher.isNullOrEmpty() ||
                                    !it.cipher.isNullOrEmpty(),
                            )
                        },
                    // Progressive means fetching a picture nobody watches, so stay small when bytes cost
                    // money or when the caller only keeps a few seconds anyway (ringtone trimmer).
                    preferSmallest = preferSmallestAudio || saverOn || metered,
                )
            if (index == null) {
                Timber.tag(logTag).d("No audio-only format and no progressive stream carrying audio")
                return null
            }
            val picked = progressive[index]
            Timber.tag(logTag).i(
                "No audio-only format for this video — playing progressive itag ${picked.itag} " +
                    "(${picked.bitrate} bps); its video track is simply not rendered",
            )
            return picked
        }

        val audioPool = audioFormats?.filter { it.isOriginal }?.takeIf { it.isNotEmpty() }
            ?: audioFormats?.filter { it.audioTrack?.isAutoDubbed == false }?.takeIf { it.isNotEmpty() }
            ?: audioFormats
        if (audioPool != null && audioPool.none { it.isOriginal }) {
            Timber.tag(logTag).w("No original audio track (auto-dub region) — using non-dubbed/any fallback")
        }

        // RINGTONE-ONLY (preferSmallestAudio): the trimmer keeps a few seconds, so fetch the smallest
        // transferable audio stream (e.g. ~50kbps Opus itag 249) instead of the Hi-Res pick. Applied
        // after the same original/non-dubbed pool selection so region behaviour is identical.
        if (preferSmallestAudio) {
            val smallest = audioPool?.minByOrNull { it.bitrate }
            if (smallest != null) {
                Timber.tag(logTag).d("Selected SMALLEST format (ringtone): ${smallest.mimeType}, bitrate: ${smallest.bitrate}")
            } else {
                Timber.tag(logTag).d("No suitable audio format found (ringtone/smallest)")
            }
            return smallest
        }

        // DATA SAVER (audit 2026-09-13): the switch forced the OPUS tier but the selection below still took
        // the highest-bitrate Opus (itag 251, ~160 kbps), so audio saved nothing at all. With Data Saver ON,
        // take the ~70 kbps Opus rendition (itag 250) when the track ships it, else the lowest-bitrate Opus
        // at or above ~48 kbps. OFF → the full-quality pick below, unchanged.
        val dataSaverOn = PrefsBridge.peek(iad1tya.echo.music.constants.DataSaverEnabledKey) == true
        if (dataSaverOn) {
            val opus = audioPool?.filter { it.mimeType.startsWith("audio/webm") }.orEmpty()
            val saver = opus.firstOrNull { it.itag == 250 }
                ?: opus.filter { it.bitrate >= 48_000 }.minByOrNull { it.bitrate }
            if (saver != null) {
                Timber.tag(logTag).d("Data Saver: selected ${saver.mimeType}, bitrate: ${saver.bitrate}")
                return saver
            }
        }

        val format = audioPool
            ?.maxByOrNull {
                var score = it.bitrate.toFloat()
                // If Opus is requested, Opus (audio/webm) is vastly superior in codec efficiency.
                // We multiply its bitrate by 2.0 to ensure 160kbps Opus (itag 251) definitively
                // beats 256kbps AAC (itag 141), preserving the true Hi-Res Opus stream.
                if (audioQuality == AudioQuality.OPUS && it.mimeType.startsWith("audio/webm")) {
                    score *= 2.0f
                }
                score
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }
    
    // 2026-08-23: the HEAD probe MUST replay the User-Agent of the client that resolved the URL.
    // googlevideo rejects requests whose UA does not match the persona that asked for the stream:
    // with the SimpMusic fingerprint (ANDROID_VR 1.65.10) /player finally returned direct URLs again,
    // but this probe went out with a Firefox web UA and every candidate died as 403 before ExoPlayer
    // (which DOES replay the resolving client's UA) ever fetched a byte. The old web-UA default was
    // one of the reasons the burned Echo Music cascade looked identical to a hard block.
    private fun validateStatus(url: String, userAgent: String): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)
                .header("User-Agent", userAgent)

            // Do NOT attach YouTube.cookie here. The main resolver client (ANDROID_VR) is loginSupported=false
            // and the real ExoPlayer byte fetch (OkHttpDataSource) sends NO cookie, so this validation HEAD
            // must mirror it. Attaching a stale/foreign cookie (e.g. one reinstalled by a backup restore, or an
            // expired session) makes googlevideo answer 401/403 on the HEAD → a perfectly playable URL is
            // discarded → all clients exhausted → NO_STREAM → "no reproduce". An invalid cookie here is
            // strictly worse than none, and the cookie adds nothing to a HEAD on a session-less googlevideo URL.

            // Close the Response on every path (.use) — a HEAD still carries a body/connection that
            // otherwise leaks into the pool on each stream validation.
            // FIX B2: use the SHORT-timeout validation client so a dead/slow candidate fails fast.
            validateHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val isSuccessful = response.isSuccessful
                Timber.tag(logTag).d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})")
                if (isSuccessful) return true
                // Some googlevideo edge nodes answer 403/405 to a bare HEAD even for URLs a real
                // ranged GET serves fine. One GET with a 1-byte Range decides it: 200/206 = playable.
                if (response.code == 403 || response.code == 405) {
                    val ranged = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .header("Range", "bytes=0-0")
                        .build()
                    validateHttpClient.newCall(ranged).execute().use { getResponse ->
                        val ok = getResponse.isSuccessful
                        Timber.tag(logTag).d("Stream URL ranged-GET validation: ${if (ok) "Success" else "Failed"} (${getResponse.code})")
                        return ok
                    }
                }
                return false
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        // Reuse the memoized sts if still fresh — it's the same for every video until the player rotates,
        // so this skips the per-song hop into NewPipe (regex over the ~2.8 MB player JS, plus the
        // first-call download/parse) for a value that never changes between songs.
        val cached = cachedSignatureTimestamp
        if (cached != null &&
            android.os.SystemClock.elapsedRealtime() - cachedSignatureTimestampAtMs < SIGNATURE_TIMESTAMP_TTL_MS) {
            Timber.tag(logTag).d("Signature timestamp (cached): $cached")
            return SignatureTimestampResult(cached, isAgeRestricted = false)
        }
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                cachedSignatureTimestamp = timestamp
                cachedSignatureTimestampAtMs = android.os.SystemClock.elapsedRealtime()
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Timber.tag(TAG).i("Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    // Network-shaped failures (offline start, flaky link) are expected operating
                    // conditions, not defects — a Crashlytics non-fatal per offline launch is noise.
                    // Real parse/extractor failures still report.
                    val networkShaped = error is java.io.IOException || error.cause is java.io.IOException
                    if (!networkShaped) reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    /**
     * Result of the stream-URL lookup. [nAlreadyDeobfuscated] is true when the URL came from the
     * NewPipe/StreamInfo extractor: its throttling ("n") parameter is ALREADY deobfuscated by the
     * extractor, so the caller must NOT run the n-transform on it again (re-running it scrambles a
     * good value — that turns a perfectly fetchable URL into a 403).
     */
    data class StreamUrlResult(
        val url: String,
        val nAlreadyDeobfuscated: Boolean,
    )

    suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): StreamUrlResult? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        // SIMPMUSIC MODEL (verified 2026-08-23 against SimpMusic v1.7.0 source, core@dae3ce98 —
        // Extractor.android.kt + YouTube.kt player()): SimpMusic does NOT play the URLs that the
        // InnerTube player response carries. It takes the stream URLs from the NewPipe extractor
        // (StreamInfo.getInfo → audioStreams/videoStreams/videoOnlyStreams), matches them to the
        // chosen format BY ITAG, and plays those. Those URLs fetch cleanly with a plain HTTP client
        // (SimpMusic's OkHttpDataSource has NO User-Agent override at all), which is exactly the
        // class of URL our fetch needs. So NewPipe is the PRIMARY provider here — the InnerTube
        // direct URL and the cipher paths remain only as fallbacks if the extractor has no match.
        if (!skipNewPipe) {
            val npStartMs = SystemClock.elapsedRealtime()
            val newPipeStreams = runCatching { NewPipeExtractor.newPipePlayer(videoId) }.getOrDefault(emptyList())
            // Log the RETURNED itags only (never the URLs — their query strings carry credentials).
            // "no match" alone could not say whether the extractor came back empty (blocked) or with
            // streams that simply lack the requested itag (fixable by picking another audio itag,
            // exactly what SimpMusic does).
            Timber.tag(logTag).d(
                "NewPipe stream lookup requested itag=${format.itag}, returned itags=${newPipeStreams.map { it.first }} in ${SystemClock.elapsedRealtime() - npStartMs}ms"
            )
            // SIMPMUSIC SELECTION (StreamRepositoryImpl.getStream): exact itag first, then ANY usable
            // audio itag. Their formatList.find { it.isAudio && url != null } becomes a fixed
            // preference order over the audio itags the extractor may carry.
            val newPipeUrl = newPipeStreams.firstOrNull { it.first == format.itag }?.second
                ?: AUDIO_ITAG_PREFERENCE.firstNotNullOfOrNull { wanted ->
                    newPipeStreams.firstOrNull { it.first == wanted }?.second
                }
            if (newPipeUrl != null) {
                return StreamUrlResult(newPipeUrl, nAlreadyDeobfuscated = true)
            }
        } else {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
        }


        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return StreamUrlResult(format.url!!, nAlreadyDeobfuscated = false)
        }


        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return StreamUrlResult(customDeobfuscatedUrl, nAlreadyDeobfuscated = false)
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }


        Timber.tag(logTag).e("No stream URL available from any provider for itag=${format.itag}")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}
