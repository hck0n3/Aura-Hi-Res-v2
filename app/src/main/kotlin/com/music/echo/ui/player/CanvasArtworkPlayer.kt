package iad1tya.echo.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.okhttp.OkHttpDataSource
import android.content.Context
import android.net.ConnectivityManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.util.Locale
import android.view.ViewGroup
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT

/**
 * HALLAZGO-027 (RONDA 3, second pass): the full-player tree composes the instant a drag-up
 * starts (BottomSheet's content gate clears 1dp off the collapsed bound), so building this
 * dedicated ExoPlayer used to land inside the flip gesture's first frames. Waiting for the sheet
 * to settle moves the build off the gesture; the canvas fades in on its first rendered frame
 * anyway, so the extra delay is invisible.
 */
private const val CANVAS_PLAYER_SETTLE_DELAY_MS = 300L

/**
 * Bounded LRU disk cache dedicated to animated-cover (canvas) videos, keyed by the media URL.
 *
 * All tracks of the same album resolve to the SAME animated-cover URL, so without a URL-keyed
 * disk cache every song in an album re-downloaded the identical video from the network (wasting
 * mobile data). With this cache a repeated URL is served from disk instead of being re-fetched.
 *
 * Lives in [Context.getFilesDir] (owner directive 2026-09-03: keep everything the app downloads
 * for playback out of cacheDir — the OS may erase cacheDir whenever storage runs low, which made
 * the animated covers silently vanish and re-download; filesDir survives like the song cache),
 * with a 256 MB LRU cap, and is a process-wide singleton so it never collides with the audio
 * @PlayerCache / @DownloadCache directories. Migration is not needed: cacheDir contents are
 * disposable by contract, and a fresh cache simply re-downloads on first use.
 */
object CanvasVideoCache {
    private const val MAX_BYTES = 256L * 1024 * 1024 // 256 MB LRU cap
    @Volatile private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache {
        return cache ?: SimpleCache(
            context.applicationContext.filesDir.resolve("canvas_video"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }
}

@Composable
fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appInForeground = iad1tya.echo.music.ui.utils.rememberIsAppInForeground()
    // High-Performance Mode forces the LOW decode path (1280px, no forced bitrate) even on capable hardware.
    val deviceTier = remember { iad1tya.echo.music.utils.PerformanceMode.effectiveTier(context) }
    // Cache audit H3 (HALLAZGO-037): the canvas is a muted decorative loop — on mobile data it
    // must not force the heaviest variant. Read once per canvas entry (re-read on each song).
    val networkMetered = remember { canvasNetworkMetered(context) }
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback ?: return

    // HALLAZGO-027 (second pass): build nothing until the flip settles (see
    // CANVAS_PLAYER_SETTLE_DELAY_MS). The early return keeps the OkHttpClient, media-source
    // factory and the dedicated ExoPlayer out of composition during the gesture's frames; once
    // ready, everything below composes exactly as before.
    var playerReady by remember { mutableStateOf(false) }
    if (!playerReady) {
        LaunchedEffect(Unit) {
            delay(CANVAS_PLAYER_SETTLE_DELAY_MS)
            playerReady = true
        }
        return
    }

    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    var videoAspectRatio by remember(initial) { mutableStateOf(1f) }

    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .proxy(YouTube.proxy)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                    val clientParam = request.url.queryParameter("c")?.trim().orEmpty()
                    val isWeb =
                        clientParam.startsWith("WEB", ignoreCase = true) ||
                            clientParam.startsWith("WEB_REMIX", ignoreCase = true) ||
                            request.url.toString().contains("c=WEB", ignoreCase = true)

                    val userAgent =
                        when {
                            clientParam.startsWith("WEB", ignoreCase = true) ||
                                clientParam.startsWith("WEB_REMIX", ignoreCase = true) -> YouTubeClient.USER_AGENT_WEB

                            clientParam.startsWith("IOS", ignoreCase = true) -> YouTubeClient.IOS.userAgent

                            clientParam.startsWith("ANDROID_VR", ignoreCase = true) -> YouTubeClient.ANDROID_VR_NO_AUTH.userAgent

                            clientParam.startsWith("ANDROID", ignoreCase = true) -> YouTubeClient.MOBILE.userAgent

                            else -> YouTubeClient.USER_AGENT_WEB
                        }

                    val builder = request.newBuilder().header("User-Agent", userAgent)
                    if (isWeb) {
                        builder.header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
                        builder.header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
                    }

                    chain.proceed(builder.build())
                }
                // HALLAZGO-019 (FASE 22): per-phase timeouts, no callTimeout (canvas video streams
                // through ExoPlayer; the 30s read bound caps packet stalls only).
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    val mediaSourceFactory =
        remember(okHttpClient) {
            val upstreamFactory =
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                )
            // Serve a repeated canvas URL (same animated cover across an album) from disk instead
            // of re-downloading it every song. Cache is keyed by the media URL by default, so a
            // genuinely different canvas (different URL) still triggers its own download.
            val cacheDataSourceFactory =
                CacheDataSource.Factory()
                    .setCache(CanvasVideoCache.get(context))
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            DefaultMediaSourceFactory(cacheDataSourceFactory)
        }
    val exoPlayer =
        remember(initial) {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                // E2: only force max bitrate on HIGH-tier devices; cap resolution on LOW so weaker phones
                // don't overheat decoding a 4K canvas.
                // Cache audit H3 (HALLAZGO-037): metered networks get the capped path too — the
                // forced-highest-bitrate variant of a muted loop is pure data waste on mobile.
                val isHighTier = deviceTier == iad1tya.echo.music.utils.DeviceTier.HIGH
                val isLowOrUltraTier = deviceTier == iad1tya.echo.music.utils.DeviceTier.LOW ||
                    deviceTier == iad1tya.echo.music.utils.DeviceTier.ULTRA
                val tsBuilder = trackSelectionParameters
                    .buildUpon()
                    .setForceHighestSupportedBitrate(canvasForceHighestBitrate(isHighTier, networkMetered))
                if (canvasCapResolution(isLowOrUltraTier, networkMetered)) {
                    tsBuilder.setMaxVideoSize(1280, 1280)
                }
                trackSelectionParameters = tsBuilder.build()
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false,
                )
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = isPlaying
            }
        }

    LaunchedEffect(isPlaying, appInForeground) {
        // E1: don't decode the canvas video while the app is backgrounded / screen off (saves battery/heat).
        val shouldPlay = isPlaying && appInForeground
        if (exoPlayer.playWhenReady != shouldPlay) {
            exoPlayer.playWhenReady = shouldPlay
        }
    }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val next =
                        when (currentUrl) {
                            primary -> fallback
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        currentUrl = next
                        isVideoReady = false 
                    }
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoAspectRatio = videoSize.width.toFloat() / videoSize.height
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        val mimeType =
            when {
                normalized.contains(".m3u8", ignoreCase = true) || 
                normalized.lowercase(Locale.ROOT).split('?').first().endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
                normalized.lowercase(Locale.ROOT).contains(".mp4") -> MimeTypes.VIDEO_MP4
                primary != null && currentUrl == primary -> {
                    
                    
                    if (normalized.contains("apple.com") || normalized.contains("music.apple") || !normalized.contains(".mp4")) {
                        MimeTypes.APPLICATION_M3U8
                    } else {
                        MimeTypes.VIDEO_MP4
                    }
                }
                fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        val mediaItem =
            MediaItem.Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        isVideoReady = false
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = isPlaying && appInForeground
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha"
    )

    AndroidView(
        factory = { viewContext ->
            AspectRatioFrameLayout(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                
                val textureView = TextureView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
                addView(textureView)
                exoPlayer.setVideoTextureView(textureView)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.setAspectRatio(videoAspectRatio)
        },
        modifier = modifier.alpha(alpha),
    )
}

/**
 * Cache audit H3 (HALLAZGO-037): canvas video track-selection policy. Forcing the highest
 * supported bitrate is only worth it on a HIGH-tier device over an unmetered network — the
 * canvas is a muted decorative loop, so on mobile data the heaviest variant is pure waste.
 * Pure decision — the tier/network readings stay at the call site.
 */
fun canvasForceHighestBitrate(isHighTier: Boolean, isMetered: Boolean): Boolean =
    isHighTier && !isMetered

/** Cache audit H3 (HALLAZGO-037): cap the canvas resolution on weak tiers OR metered networks. */
fun canvasCapResolution(isLowOrUltraTier: Boolean, isMetered: Boolean): Boolean =
    isLowOrUltraTier || isMetered

private fun canvasNetworkMetered(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    return cm?.isActiveNetworkMetered ?: false
}
