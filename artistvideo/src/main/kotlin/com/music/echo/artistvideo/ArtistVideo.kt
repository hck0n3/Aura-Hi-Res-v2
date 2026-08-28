package iad1tya.echo.music.artistvideo

import android.content.Context
import android.net.ConnectivityManager
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import okhttp3.OkHttpClient
import java.util.Locale

/**
 * Bounded LRU disk cache dedicated to artist videos, keyed by the media URL.
 *
 * Cache audit H3 (HALLAZGO-037): every artist-screen visit used to re-download the whole video
 * (autoplay, muted, looped). With a URL-keyed disk cache a repeated visit is served from disk.
 * Lives in [Context.getCacheDir] (OS-evictable) with a 128 MB LRU cap, process-wide singleton,
 * separate from the audio @PlayerCache / @DownloadCache and the canvas-video cache directories.
 */
object ArtistVideoCache {
    private const val MAX_BYTES = 128L * 1024 * 1024 // 128 MB LRU cap
    @Volatile private var cache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache {
        return cache ?: SimpleCache(
            context.applicationContext.cacheDir.resolve("artist_video"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }
}

/**
 * Cache audit H3 (HALLAZGO-037): the artist video autoplays in a muted loop on every artist
 * screen visit — over mobile data that is a full video download with no user decision, so it
 * only starts on an unmetered network (the screen falls back to the static artist image).
 * Pure decision — the live metered reading stays at the call site.
 */
fun artistVideoNetworkAllowed(isActiveNetworkMetered: Boolean): Boolean = !isActiveNetworkMetered

private fun artistVideoNetworkMetered(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    return cm?.isActiveNetworkMetered ?: false
}

@Composable
fun ArtistVideo(
    videoUrl: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // Cache audit H3 (HALLAZGO-037): no artist video over mobile data — see artistVideoNetworkAllowed.
    val networkAllowed = remember { artistVideoNetworkAllowed(artistVideoNetworkMetered(context)) }
    if (!networkAllowed) return

    var isVideoReady by remember(videoUrl) { mutableStateOf(false) }

    val okHttpClient = remember { OkHttpClient.Builder().build() }
    val mediaSourceFactory =
        remember(okHttpClient) {
            val upstreamFactory =
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                )
            // Cache audit H3 (HALLAZGO-037): serve a repeated artist video from disk instead of
            // re-downloading it on every visit. Cache is keyed by the media URL by default.
            val cacheDataSourceFactory =
                CacheDataSource.Factory()
                    .setCache(ArtistVideoCache.get(context))
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            DefaultMediaSourceFactory(cacheDataSourceFactory)
        }
    val exoPlayer =
        remember(videoUrl) {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        false,
                    )
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = true
                }
        }

    DisposableEffect(exoPlayer, videoUrl) {
        val listener =
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(videoUrl, exoPlayer) {
        val normalized = videoUrl.trim()
        val mimeType =
            when {
                normalized.lowercase(Locale.ROOT).contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                normalized.lowercase(Locale.ROOT).contains("mp4") -> MimeTypes.VIDEO_MP4
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
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "videoAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onClick() },
    ) {
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
            modifier = Modifier
                .matchParentSize()
                .alpha(videoAlpha),
        )
    }
}
