package iad1tya.echo.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.SQLException
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import coil3.imageLoader
import com.google.common.util.concurrent.MoreExecutors
import com.music.innertube.PlaybackMetricsSession
import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import dagger.hilt.android.AndroidEntryPoint
import iad1tya.echo.music.MainActivity
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AudioNormalizationKey
import iad1tya.echo.music.constants.AudioOffload
import iad1tya.echo.music.constants.AudioQualityKey
import iad1tya.echo.music.constants.AutoDownloadOnLikeKey
import iad1tya.echo.music.constants.AutoSkipNextOnErrorKey
import iad1tya.echo.music.constants.CrossfadeCurveKey
import iad1tya.echo.music.constants.CrossfadeDurationKey
import iad1tya.echo.music.constants.CrossfadeEnabledKey
import iad1tya.echo.music.constants.CrossfadeGaplessKey
import iad1tya.echo.music.constants.DisableLoadMoreWhenRepeatAllKey
import iad1tya.echo.music.constants.DiscordActivityNameKey
import iad1tya.echo.music.constants.DiscordActivityTypeKey
import iad1tya.echo.music.constants.DiscordAdvancedModeKey
import iad1tya.echo.music.constants.DiscordButton1TextKey
import iad1tya.echo.music.constants.DiscordButton1VisibleKey
import iad1tya.echo.music.constants.DiscordButton2TextKey
import iad1tya.echo.music.constants.DiscordButton2VisibleKey
import iad1tya.echo.music.constants.DiscordStatusKey
import iad1tya.echo.music.constants.DiscordTokenKey
import iad1tya.echo.music.constants.DiscordUseDetailsKey
import iad1tya.echo.music.constants.EnableDiscordRPCKey
import iad1tya.echo.music.constants.EnableLastFMScrobblingKey
import iad1tya.echo.music.constants.EnhancedShuffleKey
import iad1tya.echo.music.constants.ExportedFileUrisKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.HistoryDuration
import iad1tya.echo.music.constants.IpVersionKey
import iad1tya.echo.music.constants.KeepGenreLaneKey
import iad1tya.echo.music.constants.LastFMUseNowPlaying
import iad1tya.echo.music.constants.MediaSessionConstants.CommandToggleLike
import iad1tya.echo.music.constants.MediaSessionConstants.CommandToggleRepeatMode
import iad1tya.echo.music.constants.MediaSessionConstants.CommandToggleShuffle
import iad1tya.echo.music.constants.MediaSessionConstants.CommandToggleStartRadio
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.PauseListenHistoryKey
import iad1tya.echo.music.constants.PauseOnMute
import iad1tya.echo.music.constants.PersistentQueueKey
import iad1tya.echo.music.constants.PersistentShuffleAcrossQueuesKey
import iad1tya.echo.music.constants.PlayerVolumeKey
import iad1tya.echo.music.constants.PreventDuplicateTracksInQueueKey
import iad1tya.echo.music.constants.PreviousQueueOfferKey
import iad1tya.echo.music.constants.RememberShuffleAndRepeatKey
import iad1tya.echo.music.constants.RepeatModeKey
import iad1tya.echo.music.constants.ResumeOnBluetoothConnectKey
import iad1tya.echo.music.constants.SafeVolumeEnabledKey
import iad1tya.echo.music.constants.ScrobbleDelayPercentKey
import iad1tya.echo.music.constants.ScrobbleDelaySecondsKey
import iad1tya.echo.music.constants.ScrobbleMinSongDurationKey
import iad1tya.echo.music.constants.ShowLyricsKey
import iad1tya.echo.music.constants.ShuffleModeKey
import iad1tya.echo.music.constants.ShufflePlaylistFirstKey
import iad1tya.echo.music.constants.SimilarContent
import iad1tya.echo.music.constants.SkipSilenceInstantKey
import iad1tya.echo.music.constants.SkipSilenceKey
import iad1tya.echo.music.constants.SpatialAudioEnabledKey
import iad1tya.echo.music.constants.SpatialAudioProfileKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.EnhancedShuffleContextEntity
import iad1tya.echo.music.db.entities.EnhancedShufflePlayedEntity
import iad1tya.echo.music.db.entities.Event
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.db.entities.LyricsEntity
import iad1tya.echo.music.db.entities.RelatedSongMap
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.di.DownloadCache
import iad1tya.echo.music.di.PlayerCache
import iad1tya.echo.music.eq.EqualizerService
import iad1tya.echo.music.eq.audio.CustomEqualizerAudioProcessor
import iad1tya.echo.music.eq.audio.NormalizationGainAudioProcessor
import iad1tya.echo.music.eq.audio.SpatialAudioProfile
import iad1tya.echo.music.eq.audio.SpatialOutputKind
import iad1tya.echo.music.eq.audio.TruePeakLimiterAudioProcessor
import iad1tya.echo.music.eq.audio.dbToLinear
import iad1tya.echo.music.eq.audio.effectiveLoudnessDb
import iad1tya.echo.music.eq.audio.isPlayingLoudnessFrozen
import iad1tya.echo.music.eq.audio.loudnessMakeupDb
import iad1tya.echo.music.eq.audio.normalizationMultiplier
import iad1tya.echo.music.eq.audio.safeVolumeGainWithEqPreamp
import iad1tya.echo.music.eq.data.EQProfileRepository
import iad1tya.echo.music.extensions.SilentHandler
import iad1tya.echo.music.extensions.collect
import iad1tya.echo.music.extensions.collectLatest
import iad1tya.echo.music.extensions.currentMetadata
import iad1tya.echo.music.extensions.findNextMediaItemById
import iad1tya.echo.music.extensions.mediaItems
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.setOffloadEnabled
import iad1tya.echo.music.extensions.toEnum
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.extensions.toPersistQueue
import iad1tya.echo.music.extensions.toQueue
import iad1tya.echo.music.lyrics.LyricsHelper
import iad1tya.echo.music.models.PersistPlayerState
import iad1tya.echo.music.models.PersistQueue
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.MusicService.Companion.TAG
import iad1tya.echo.music.playback.audio.AudioOffloadGate
import iad1tya.echo.music.playback.audio.SilenceDetectorAudioProcessor
import iad1tya.echo.music.playback.queues.EmptyQueue
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.LocalAlbumRadio
import iad1tya.echo.music.playback.queues.Queue
import iad1tya.echo.music.playback.queues.YouTubeAlbumRadio
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.playback.queues.filterExplicit
import iad1tya.echo.music.playback.queues.filterNonMusicForAutoQueue
import iad1tya.echo.music.playback.queues.filterVideoSongs
import iad1tya.echo.music.utils.CoilBitmapLoader
import iad1tya.echo.music.utils.DiscordRPC
import iad1tya.echo.music.utils.NetworkConnectivityObserver
import iad1tya.echo.music.utils.ScrobbleManager
import iad1tya.echo.music.utils.ShareLinks
import iad1tya.echo.music.utils.SyncUtils
import iad1tya.echo.music.utils.YTPlayerUtils
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.exportedFileUriExists
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.localeAwareContext
import iad1tya.echo.music.utils.parseExportedFileUriMap
import iad1tya.echo.music.utils.privacySafeSummary
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.widget.EchoMusicWidgetManager
import iad1tya.echo.music.widget.MusicWidgetReceiver
import iad1tya.echo.music.widget.PlaylistWidgetReceiver
import iad1tya.echo.music.widget.TurntableWidgetReceiver
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Plan C1, phase 4 (row 352): PLAYBACK ERROR RECOVERY (per-song retry budget, the handlers for each
 * error class — renderer, 416 range, page reload, expired URL, generic I/O, unresolvable song — the final
 * failure and the aggressive cache clear) moved out of MusicService.kt into its own file. Byte-for-byte the
 * members they were, now internal extensions; the members they read went from private to internal.
 * onPlayerError (an override) stays in MusicService and calls these exactly as before. Behaviour unchanged.
 */
internal fun MusicService.performAggressiveCacheClear(mediaId: String) {
    Timber.tag(TAG).d("Performing aggressive cache clear for $mediaId")


    songUrlCache.remove(mediaId)


    try {
        playerCache.removeResource(mediaId)
        // GHOST-BYTES FIX (owner directive 2026-09-03, Spotify-like cache): the streamed bytes of a
        // song live under StreamCacheKeys ("yt-stream-<videoId>-<itag>"), NOT under the mediaId.
        // removeResource(mediaId) removed a key that streaming never writes — the real bytes
        // stayed orphaned on disk, still counted by the storage bar but never served again, and
        // the NEXT play of the song re-downloaded everything (the "cada canción consume datos
        // otra vez" report). Drop the song's actual listen-cache keys too. Same class as the
        // "En caché" audit (2026-08-29): every consumer must agree with StreamCacheKeys or the
        // listen-cache becomes invisible.
        StreamCacheKeys.keysOf(playerCache.keys, mediaId).forEach { key ->
            runCatching { playerCache.removeResource(key) }
                .onFailure { Timber.tag(TAG).d(it, "ghost-purge: removeResource($key) failed (non-fatal)") }
        }
        Timber.tag(TAG).d("Cleared player cache for $mediaId")
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to clear player cache for $mediaId")
    }


    try {
        YTPlayerUtils.forceRefreshForVideo(mediaId)
        Timber.tag(TAG).d("Cleared decryption caches for $mediaId")
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to clear decryption caches for $mediaId")
    }
}

internal fun MusicService.hasExceededRetryLimit(mediaId: String): Boolean {
    val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
    return currentRetries >= MAX_RETRY_PER_SONG
}

internal fun MusicService.incrementRetryCount(mediaId: String) {
    val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
    currentMediaIdRetryCount[mediaId] = currentRetries + 1
    Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
}

internal fun MusicService.resetRetryCount(mediaId: String) {
    currentMediaIdRetryCount.remove(mediaId)
    recentlyFailedSongs.remove(mediaId)
}

internal fun MusicService.markSongAsFailed(mediaId: String) {
    recentlyFailedSongs.add(mediaId)
    currentMediaIdRetryCount.remove(mediaId)

    
    failedSongsClearJob?.cancel()
    failedSongsClearJob = scope.launch {
        delay(5 * 60 * 1000L)
        recentlyFailedSongs.clear()
        Timber.tag(TAG).d("Cleared recently failed songs list")
    }
}

internal fun MusicService.handleAudioRendererError(mediaId: String?) {
    if (mediaId == null) {
        handleFinalFailure()
        return
    }

    incrementRetryCount(mediaId)

    retryJob?.cancel()
    retryJob = scope.launch {
        try {
            
            val wasPlaying = player.playWhenReady
            player.pause()
            Timber.tag(TAG).d("Paused playback due to AudioTrack error")

            
            
            delay(RETRY_DELAY_MS * 3) 

            
            if (!playerInitialized.value) {
                Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                return@launch
            }

            val currentIndex = player.currentMediaItemIndex
            if (currentIndex != C.INDEX_UNSET) {
                
                val currentPosition = player.currentPosition
                player.seekTo(currentIndex, currentPosition)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")

                
                if (wasPlaying) {
                    delay(500) 
                    if (hasAudioFocus && playerInitialized.value) {
                        if (castConnectionHandler?.isCasting?.value != true) {
                            player.play()
                        }
                    }
                }
            } else {
                Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                handleFinalFailure()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
            handleFinalFailure()
        }
    }
}

internal fun MusicService.handleRangeNotSatisfiableError(mediaId: String?) {
    if (mediaId == null) {
        handleFinalFailure()
        return
    }

    incrementRetryCount(mediaId)

    retryJob?.cancel()
    retryJob = scope.launch {
        try {
            performAggressiveCacheClear(mediaId)

            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, 0)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 416 error (from position 0)")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "handleRangeNotSatisfiableError retry failed")
            reportException(e)
        }
    }
}

internal fun MusicService.handlePageReloadError(mediaId: String?) {
    if (mediaId == null) {
        handleFinalFailure()
        return
    }

    incrementRetryCount(mediaId)

    retryJob?.cancel()
    retryJob = scope.launch {
        try {
            Timber.tag(TAG).d("Handling page reload error for $mediaId")

            performAggressiveCacheClear(mediaId)

            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after page reload error")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "handlePageReloadError retry failed")
            reportException(e)
        }
    }
}

internal fun MusicService.handleExpiredUrlError(mediaId: String?) {
    if (mediaId == null) {
        handleFinalFailure()
        return
    }

    incrementRetryCount(mediaId)
    StreamHealth.expiredUrlRecovery()
    // Aggregate pipeline health at the exact moment a mid-stream URL expiry is recovered — the
    // line carries counters only (no titles/ids/URLs), so it is safe for the shared app.log.
    Timber.tag(TAG).i(StreamHealth.snapshot())


    songUrlCache.remove(mediaId)
    Timber.tag(TAG).d("Cleared cached URL for $mediaId")

    
    try {
        YTPlayerUtils.forceRefreshForVideo(mediaId)
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to clear decryption caches")
    }

    retryJob?.cancel()
    retryJob = scope.launch {
        try {
            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 403 error")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "handleExpiredUrlError retry failed")
            reportException(e)
        }
    }
}

internal fun MusicService.handleGenericIOError(mediaId: String?) {
    if (mediaId == null) {
        handleFinalFailure()
        return
    }

    incrementRetryCount(mediaId)

    retryJob?.cancel()
    retryJob = scope.launch {
        try {
            performAggressiveCacheClear(mediaId)

            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after generic IO error")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "handleGenericIOError retry failed")
            reportException(e)
        }
    }
}

internal fun MusicService.handleFinalFailure() {
    if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
        Timber.tag(TAG).d("All recovery attempts exhausted, auto-skipping to next track")
        skipOnError()
    } else {
        Timber.tag(TAG).d("All recovery attempts exhausted, stopping playback")
        stopOnError()
    }
}

// Surface + auto-skip an UNSERVEABLE song (fix #3). Shows a brief message with the real reason and
// SKIPS past the track REGARDLESS of the AutoSkipNextOnErrorKey toggle — an unresolvable song must
// never silently pause forever or loop in a fake "no internet" state. Runs on the player callback
// thread (main looper), so Toast is safe here.
internal fun MusicService.handleUnresolvableSong(mediaId: String?, reason: String?) {
    val base = "Canción no disponible"
    val clean = reason?.trim()?.takeIf {
        it.isNotEmpty() &&
            it != getString(R.string.error_unknown) &&
            it != getString(R.string.error_no_internet) &&
            it != getString(R.string.error_timeout)
    }
    val msg = if (clean != null) "$base: $clean" else base
    runCatching { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    if (mediaId != null) markSongAsFailed(mediaId)
    skipOnError()
}
