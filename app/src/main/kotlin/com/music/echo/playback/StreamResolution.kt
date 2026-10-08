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
import iad1tya.echo.music.playback.MusicService.Companion.CHUNK_LENGTH
import iad1tya.echo.music.playback.MusicService.Companion.ERROR_CODE_NO_STREAM
import iad1tya.echo.music.playback.MusicService.Companion.TAG
import iad1tya.echo.music.playback.MusicService.Companion.URL_REFRESH_AHEAD_MS
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
 * Plan C1, phase 3 (row 352): STREAM RESOLUTION and the URL cache (the data-source factory that resolves
 * each song to a playable URL, expiry and near-expiry refresh, the persisted song-URL cache, the
 * fully-cached fast path, clearing a song cache, getStreamUrl) moved out of MusicService.kt into its own
 * file. Byte-for-byte the members they were, now internal extensions; the members they read went from
 * private to internal. Behaviour unchanged.
 */
/**
 * FIX B1: authoritative absolute expiry (epoch millis) for a resolved stream URL. Prefers the googlevideo
 * `expire=` query param (unix SECONDS); falls back to [storedExpireMillis] (already an absolute-millis
 * value computed at resolve time — e.g. for Saavn/Qobuz URLs that carry no expire param), and to a
 * conservative now+5h if both are missing/already past. Never throws.
 */
internal fun MusicService.streamUrlExpiryMillis(url: String, storedExpireMillis: Long): Long {
    val fromUrl = runCatching {
        Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { it * 1000L }
    }.getOrNull()
    val now = System.currentTimeMillis()
    return when {
        fromUrl != null && fromUrl > now -> fromUrl
        storedExpireMillis > now -> storedExpireMillis
        else -> now + 5L * 60 * 60 * 1000
    }
}

/**
 * FIX B1: persist the (non-expired, LRU-bounded) songUrlCache to DataStore so a resolved stream URL
 * survives a process restart / app update — the first play/resume after an update then serves the cached
 * URL instead of re-running the slow resolver. Best-effort, off the main thread; never throws.
 */
internal fun MusicService.persistSongUrlCache() {
    // 2026-08-23 owner directive (SimpMusic model): stream URLs are NOT persisted. SimpMusic
    // resolves fresh on every play; persisted URLs from a burnt session were the source of the
    // first-play 403s on cold start. The cache stays in-memory only.
    Timber.tag(TAG).d("persistSongUrlCache skipped (SimpMusic-model: URLs live in memory only)")
}

/**
 * FIX B1 (disabled 2026-08-23): persist the (non-expired, LRU-bounded) songUrlCache to DataStore so
 * a resolved stream URL survives a process restart / app update. See persistSongUrlCache().
 */
@Suppress("unused")
internal fun MusicService.persistSongUrlCacheDisabled() {
    scope.launch(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val entries = songUrlCache.entries
                .filter { it.value.expiresAt > now }
                .sortedByDescending { it.value.expiresAt } // freshest-expiring first ≈ most-recent (LRU proxy)
                .take(SONG_URL_CACHE_PERSIST_MAX)
            val json = org.json.JSONObject()
            for (e in entries) {
                val o = org.json.JSONObject()
                    .put("u", e.value.url)
                    .put("e", streamUrlExpiryMillis(e.value.url, e.value.expiresAt))
                // "q"  = what was DELIVERED (the container guard's input).
                // "rq" = what was REQUESTED (staleness only — see [CachedStream]).
                // Both omitted when unknown; an absent field reads back as null = unknown, never as a pin.
                e.value.delivered?.let { q -> o.put("q", q.name) }
                e.value.requested?.let { q -> o.put("rq", q.name) }
                json.put(e.key, o)
            }
            dataStore.edit { it[iad1tya.echo.music.constants.SongUrlCacheBlobKey] = json.toString() }
        }.onFailure { Timber.tag(TAG).d(it, "persistSongUrlCache failed (non-fatal)") }
    }
}

/**
 * FIX B1: on cold start, load the persisted songUrlCache. Only NON-expired entries (with a 60s safety
 * margin) are restored, so we never serve a stale URL; putIfAbsent never clobbers a fresher live resolve.
 * Best-effort, off the main thread; never throws.
 */
internal fun MusicService.loadPersistedSongUrlCache() {
    // 2026-08-23 owner directive (SimpMusic model): restoration is disabled. URLs persisted by
    // previous (burnt) sessions were served on cold start and produced the first-play 403s before
    // the fresh resolver even ran. SimpMusic always resolves fresh; we do the same.
    Timber.tag(TAG).i("Persisted stream URL restoration disabled (SimpMusic-model: fresh resolve)")
}

/**
 * FIX B1 (disabled 2026-08-23): on cold start, load the persisted songUrlCache. See
 * loadPersistedSongUrlCache().
 */
@Suppress("unused")
internal fun MusicService.loadPersistedSongUrlCacheDisabled() {
    scope.launch(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first()
            val blob = prefs[iad1tya.echo.music.constants.SongUrlCacheBlobKey]
                ?.takeIf { it.isNotBlank() } ?: return@runCatching
            // The global quality, read from the SAME snapshot as the blob (so it can't race the quality
            // collector, whose first emit deliberately returns early). It is compared ONLY against what was
            // REQUESTED ("rq"), never against what was delivered: changing the quality while the service is
            // dead must drop those entries (nothing clears them otherwise), but a fallback entry — requested
            // LOSSLESS, delivered Opus — is perfectly good and must SURVIVE. Comparing the delivered quality
            // here would bin nearly every entry a Hi-Res user has on every cold start = #28's slow first play.
            // DATA SAVER: compare against the EFFECTIVE quality (forced Opus while ON), so entries
            // requested at a higher tier are dropped exactly like after a manual quality change —
            // otherwise a cached Hi-Res URL would keep serving Hi-Res bytes past the switch.
            val globalQuality = if (prefs[iad1tya.echo.music.constants.DataSaverEnabledKey] == true) {
                iad1tya.echo.music.constants.AudioQuality.OPUS
            } else {
                prefs[AudioQualityKey].toEnum(iad1tya.echo.music.constants.AudioQuality.OPUS)
            }
            val json = org.json.JSONObject(blob)
            val safeNow = System.currentTimeMillis() + 60_000L
            val keys = json.keys()
            var restored = 0
            while (keys.hasNext()) {
                val k = keys.next()
                val o = json.optJSONObject(k) ?: continue
                val u = o.optString("u", "")
                val e = o.optLong("e", 0L)
                // A blob written by an older version has neither field: keep them null (UNKNOWN) rather than
                // guessing. The resolver then falls back to the global audioQuality and the dbFormat container
                // guard decides — so an OLD blob still serves its URLs (#28 fast path) and can never pin a
                // replay to a stale quality. No migration, no crash: an unparsable name also degrades to null.
                val q = o.parseQuality("q")
                val rq = o.parseQuality("rq")
                // Drop ONLY what we know was REQUESTED at a quality the user no longer wants. Unknown is kept
                // on purpose: dropping it would wipe every existing user's cache on the upgrade to this
                // version and re-create the exact slow-first-play complaint of #28.
                if (rq != null && rq != globalQuality) continue
                if (u.isNotEmpty() && e > safeNow) {
                    songUrlCache.putIfAbsent(k, CachedStream(u, e, delivered = q, requested = rq))
                    restored++
                }
            }
            Timber.tag(TAG).d("Restored $restored persisted stream URL(s) from DataStore")
        }.onFailure { Timber.tag(TAG).d(it, "loadPersistedSongUrlCache failed (non-fatal)") }
    }
}

/**
 * REFRESH-AHEAD (SimpMusic-model port, "Intelligent Cache" pillar): when a cached stream URL has
 * less than [URL_REFRESH_AHEAD_MS] of life left, serve it as-is (zero latency for the play about
 * to start) while renewing the URL in the background, so any SUBSEQUENT open — a mid-track 403
 * recovery (handleExpiredUrlError), a seek re-open, a re-prepare — picks up a URL with its full
 * validity again. Best effort: a failed renewal leaves the original URL valid until its own
 * expiry, and the existing expired-URL recovery still covers the worst case.
 */
internal fun MusicService.refreshUrlIfNearExpiry(mediaId: String, cached: CachedStream) {
    if (cached.expiresAt - System.currentTimeMillis() > URL_REFRESH_AHEAD_MS) return
    if (!urlRefreshInFlight.add(mediaId)) return
    scope.launch(Dispatchers.IO) {
        try {
            val playback = YTPlayerUtils.playerResponseForPlayback(
                videoId = mediaId,
                audioQuality = cached.delivered ?: audioQuality,
                connectivityManager = connectivityManager,
                context = this@refreshUrlIfNearExpiry,
            ).getOrNull()
            val freshUrl = playback?.streamUrl
            if (!freshUrl.isNullOrBlank()) {
                // The entry may have been cleared or replaced while this renewal was in flight
                // (a quality change clears + re-resolves; a fresh resolve stamps its own entry).
                // Resurrecting the stale URL over that would break the container guard — abort.
                val entryNow = songUrlCache[mediaId]
                if (entryNow == null || entryNow.requested != cached.requested) return@launch
                // Derive the delivered quality from the NEW response with the SAME predicate the
                // container guard uses (isFinalLossless/isFinalSaavn — registry lesson #40). The
                // LOSSLESS cascade is nondeterministic (#78): if this renewal landed on a different
                // quality than what is playing, stamping it would make delivered disagree with the
                // actual bytes. Discard the renewal instead; the current URL stays valid until its
                // own expiry and handleExpiredUrlError still covers the worst case.
                val freshMime = playback.format.mimeType
                val freshDelivered = DeliveredQuality.fromMimeType(freshMime)
                if (cached.delivered != null && freshDelivered != cached.delivered) {
                    Timber.tag(TAG).d("Refresh-ahead discarded: delivered quality drifted on renewal")
                    return@launch
                }
                songUrlCache[mediaId] = CachedStream(
                    url = freshUrl,
                    expiresAt = System.currentTimeMillis() + (playback.streamExpiresInSeconds * 1000L),
                    delivered = freshDelivered,
                    requested = cached.requested,
                )
                persistSongUrlCache()
                StreamHealth.refreshAheadCompleted()
                Timber.tag(TAG).d("Refresh-ahead renewed stream URL")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Refresh-ahead renewal failed; keeping current URL")
        } finally {
            urlRefreshInFlight.remove(mediaId)
        }
    }
}

internal fun MusicService.fullyCachedListenUri(mediaId: String, preferredItag: Int?): android.net.Uri? {
    fun complete(key: String): Boolean {
        val length = androidx.media3.datasource.cache.ContentMetadata
            .getContentLength(playerCache.getContentMetadata(key))
        return length > 0 && playerCache.isCached(key, 0, length)
    }
    val streamKeys = StreamCacheKeys.keysOf(playerCache.keys, mediaId)
    val preferredKey = preferredItag?.let { StreamCacheKeys.build(mediaId, it.toString()) }
    val completeKey = preferredKey?.takeIf { it in streamKeys && complete(it) }
        ?: streamKeys.firstOrNull { complete(it) }
    if (completeKey != null) {
        val itag = completeKey.substringAfterLast('-').takeIf { it.toIntOrNull() != null }
            ?: return null
        return "https://listen-cache.googlevideo.com/videoplayback?itag=$itag".toUri()
    }
    if (complete(mediaId)) return "https://listen-cache.aura.invalid/$mediaId".toUri()
    return null
}

internal fun MusicService.createDataSourceFactory(): DataSource.Factory {
    return ResolvingDataSource.Factory(
        DefaultDataSource.Factory(this, createCacheDataSource())
    ) { dataSpec ->
        // VIDEO CACHE KEY normalization (2026-09-05, the "No media id" crash): video items
        // carry a DEDICATED customCacheKey "yt-video-<videoId>" (stable per-video cache —
        // see maybePrepareInstantVideoSwap). The resolver's identity is the videoId itself:
        // the song's URL cache, bypass sets and quality guards all key on the plain id, so
        // the prefix is stripped HERE before anything else looks at it. The DataSpec keeps
        // the full yt-video- key for the CacheDataSource layer (that is the point — video
        // bytes cached under their own key), only the RESOLUTION identity is normalized.
        val mediaId = (dataSpec.key ?: error("No media id"))
            .removePrefix("yt-video-")
        if (mediaId.isLocalMediaId()) return@Factory dataSpec
        // Podcast episodes (and any direct-URL media) are already a playable audio stream — play
        // the URL straight through instead of resolving it through YouTube.
        if (mediaId.startsWith("http://", ignoreCase = true) || mediaId.startsWith("https://", ignoreCase = true)) {
            // Offline mode: direct URLs still need the network — refuse them. Same ronda 9
            // reasoning as the offlineModeOn gate below: a genuine connectivity loss refuses
            // immediately instead of only after the network fetch times out.
            if (dataStore.get(OfflineModeKey, false) || !hasLiveInternetConnection()) {
                throw PlaybackException(
                    getString(R.string.error_offline_not_downloaded),
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                )
            }
            return@Factory dataSpec.withUri(mediaId.toUri())
        }



        var shouldBypassCache = bypassCacheForQualityChange.contains(mediaId)

        val cachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(downloadCache.getContentMetadata(mediaId))
        val isFullyDownloaded = cachedLength != androidx.media3.common.C.LENGTH_UNSET.toLong() && cachedLength > 0 && downloadCache.isCached(mediaId, 0, cachedLength)

        val isCurrentlyPlaying = currentPlayingMediaId == mediaId

        // FULLY-DOWNLOADED short-circuit — a *complete* downloaded file is container-locked as a whole, so
        // it's always safe to serve WITHOUT the DB container check (its container can't drift with the
        // global quality) and we skip the runBlocking DB read below. PARTIAL downloads are deliberately NOT
        // served here: their tail is still missing, and if the user switched global quality mid-download the
        // tail would arrive in a new container (old-container prefix + new-container tail = garbled /
        // ERROR_CODE_PARSING_CONTAINER_MALFORMED). Partials fall through to the dbFormat container-mismatch
        // guard, then get served (container matches) or bypassed+refetched (mismatch) by the post-guard
        // downloadCache handling below. A playerCache / songUrlCache hit is likewise NOT served here.
        if (!shouldBypassCache && isFullyDownloaded) {
            if (downloadCache.isCached(
                    mediaId,
                    dataSpec.position,
                    if (dataSpec.length >= 0) dataSpec.length else 1
                )
            ) {
                // Download-served: no background network (duration / related prefetch) — the user
                // downloaded this exactly to avoid data use.
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                return@Factory dataSpec
            }
        }

        // Exported SAF/file URI (AudioExportService) — playable with zero network, same as a download.
        val exportedUri = runBlocking(Dispatchers.IO) {
            val raw = dataStore.data.first()[ExportedFileUrisKey].orEmpty()
            parseExportedFileUriMap(raw)[mediaId]
        }
        if (!exportedUri.isNullOrBlank() && exportedFileUriExists(this, exportedUri)) {
            scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
            return@Factory dataSpec.withUri(exportedUri.toUri())
        }

        // Ronda 9 (dueño): "detecta cuando las canciones dejen de cargar por [falta de] red y pon
        // la cola en modo offline". Before this, losing connectivity WITHOUT the user manually
        // flipping "Modo sin conexión" still let every non-cached song attempt a full network
        // resolve — it only gave up after the 15s connect / 30s read OkHttp timeout threw a
        // player error, which is exactly what read as "se queda cargando". [hasLiveInternetConnection]
        // asks the SYSTEM directly at this exact moment rather than trusting the cached
        // isNetworkConnected StateFlow for something this consequential (a hard gate that
        // refuses ALL non-cached playback) — see its own KDoc for why: that flow briefly read
        // "false" while Wi-Fi was perfectly connected (HALLAZGO, ronda 9 follow-up) because
        // losing the phone's OTHER network (mobile data, torn down once Wi-Fi took over) doesn't
        // mean losing connectivity in general.
        val offlineModeOn = dataStore.get(OfflineModeKey, false) || !hasLiveInternetConnection()

        // Read Room NOW — BEFORE serving any playerCache/songUrlCache hit — for the container-mismatch guard
        // below, which decides whether the CACHED BYTES may be served or must be bypassed+refetched.
        // Timed (slow-start telemetry): this runBlocking sits on the loader thread ahead of every
        // resolve, so its cost is folded into the RESOLVE_TIMING db= stage.
        val dbFormatReadStartMs = android.os.SystemClock.elapsedRealtime()
        val dbFormat = runBlocking(Dispatchers.IO) { database.format(mediaId).firstOrNull() }
        val dbFormatReadMs = android.os.SystemClock.elapsedRealtime() - dbFormatReadStartMs

        // FULL LISTEN-CACHE REPLAY (owner report 2026-09-13: "si pongo una canción, cambio a otra y
        // vuelvo a poner la que ya escuché, la vuelve a descargar"). Every cache-hit branch below
        // still needed a LIVE songUrlCache entry; without one (expired URL, Qobuz/Saavn short TTL,
        // restart) the song was re-RESOLVED, and a nondeterministic LOSSLESS lookup landing on a
        // different container purged the cached bytes → full re-download. A song whose bytes are
        // COMPLETE on disk needs no network at all: serve it straight from playerCache under the
        // exact key its writer used. Skipped for an explicit refetch (quality bypass / forced
        // Opus), for video items, and when downloadCache holds any span of this id (the outer
        // download layer must never mix its bytes with listen bytes).
        if (!shouldBypassCache &&
            forceOpusForMediaId != mediaId &&
            dataSpec.key?.startsWith("yt-video-") != true &&
            downloadCache.getCachedSpans(mediaId).isEmpty()
        ) {
            fullyCachedListenUri(mediaId, dbFormat?.itag)?.let { cachedUri ->
                Timber.tag(TAG).i("LISTEN-CACHE full replay for $mediaId — no resolve, no network")
                // Offline (owner directive 2026-09-13: "si está en caché pueda reproducirla sin
                // internet"): a complete listen-cache copy is as playable offline as a download,
                // so it is served even with Modo sin conexión ON — with the offline metadata path.
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = offlineModeOn) }
                StreamHealth.cacheHit()
                return@Factory dataSpec.withUri(cachedUri)
            }
        }

        // Strict offline: ONLY a full downloadCache hit, a valid exported URI or a COMPLETE listen-cache
        // copy (served just above) may play. A partial cache / songUrlCache / YT resolve all need the
        // network — refuse them while OfflineModeKey is ON.
        if (offlineModeOn) {
            throw PlaybackException(
                getString(R.string.error_offline_not_downloaded),
                null,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            )
        }

        // refetchCurrentInOpus() forces this track to Opus, overriding both the global quality and the
        // "locked" quality of the currently-playing track (below).
        val forceOpus = forceOpusForMediaId == mediaId
        // Mid-song container lock, scoped to the SESSION — deliberately read from songUrlCache, NOT from the
        // persisted FormatEntity. dbFormat is a DB row that outlives the process, so pinning to it made the
        // lock permanent: a song first played at OPUS re-pinned to OPUS on every later replay, forever, and
        // re-upserted the opus row — the container guard below could never fire against it either, since it
        // was derived from the very row it compares. A live cache entry instead means "this session already
        // resolved this URL", which is exactly the mid-song case worth protecting: the quality collector
        // preserves ONLY the playing track's entry when a quality change clears the map, so an in-flight track
        // keeps its container while every replay finds no entry → falls through to the global quality.
        // DELIVERED, not requested: this value is compared against dbFormat by the guard below, and dbFormat
        // describes what was actually served.
        val cachedQuality = songUrlCache[mediaId]?.delivered
        val lockedQuality = when {
            forceOpus -> iad1tya.echo.music.constants.AudioQuality.OPUS
            isCurrentlyPlaying && cachedQuality != null -> cachedQuality
            else -> audioQuality
        }

        if (!shouldBypassCache && !isFullyDownloaded && dbFormat != null) {
            val isLosslessCache = dbFormat.codecs == "flac"
            val isSaavnCache = dbFormat.codecs == "mp4a.40.2" || dbFormat.mimeType.contains("mp4", ignoreCase = true)

            // REPLAY LOCK (owner report 2026-09-04): with a global LOSSLESS/SAAVN quality,
            // YouTube only ever DELIVERS opus, so every quiet replay of an already-listened
            // song compared "wanted lossless" vs "delivered opus" → mismatch → purge ALL the
            // song's cached bytes → full re-download. On every replay. The replay of a song
            // whose bytes are already on disk must target the container that IS on disk —
            // only the SONG CURRENTLY PLAYING re-checks against the global quality (its
            // container was chosen a moment ago, a quality switch there is a real user action).
            // The playing song keeps `lockedQuality = cachedQuality` (delivered, above); a
            // replay without cached bytes falls to the global target as before.
            val replayHasCachedBytes = StreamCacheKeys.keysOf(playerCache.keys, mediaId).isNotEmpty() ||
                playerCache.keys.any { it.startsWith("yt-stream-$mediaId-") }
            // RESTART SURVIVAL (audit 2026-09-04, the gap that made the replay lock a
            // placebo after every restart): `cachedQuality` comes from songUrlCache —
            // MEMORY ONLY (URL persistence is deliberately off, the SimpMusic model), so
            // after a restart it is null for everything and the replay fell to the GLOBAL
            // quality → mismatch vs the persisted opus → purge + full re-download of the
            // already-cached song. The persisted record of the delivered container is
            // dbFormat itself — the SAME source the guard compares against — so deriving
            // the replay target from it can never self-deceive: a cache written as opus
            // replays as opus.
            val replayTarget = when {
                cachedQuality != null -> cachedQuality
                isLosslessCache -> iad1tya.echo.music.constants.AudioQuality.LOSSLESS
                isSaavnCache -> iad1tya.echo.music.constants.AudioQuality.SAAVN
                else -> iad1tya.echo.music.constants.AudioQuality.OPUS
            }
            val effectiveTarget = when {
                isCurrentlyPlaying -> lockedQuality
                replayHasCachedBytes -> replayTarget
                else -> lockedQuality
            }

            val cacheMatchesTarget = when (effectiveTarget) {
                iad1tya.echo.music.constants.AudioQuality.LOSSLESS -> isLosslessCache
                iad1tya.echo.music.constants.AudioQuality.SAAVN -> isSaavnCache
                iad1tya.echo.music.constants.AudioQuality.OPUS -> !isLosslessCache && !isSaavnCache
            }

            if (!cacheMatchesTarget) {
                shouldBypassCache = true
                Timber.tag(TAG).i("Quality changed to $lockedQuality for $mediaId. Clearing playerCache to prevent container mismatch.")
                playerCache.removeResource(mediaId)
                // GHOST-BYTES FIX 2026-09-03: the listen bytes live under yt-stream-* keys, not
                // mediaId — purge those too or the old-container bytes stay orphaned on disk while
                // the next open re-downloads the whole song (data waste + phantom storage usage).
                // NOTE (audit FASE 2-A #4, deliberate): keysOf covers the song's VIDEO bytes too
                // (same songId) — a quality change/corruption of the AUDIO also drops the cached
                // video, which will re-download on next view. Coherent with "one song = its keys"
                // and with clearSongCache's explicit refetch intent; documented so a future reader
                // doesn't mistake it for an oversight.
                StreamCacheKeys.keysOf(playerCache.keys, mediaId).forEach { key ->
                    runCatching { playerCache.removeResource(key) }
                }
            }
        }

        // CONTAINER-CHECKED cache hit — only NOW serve a partial-download / playerCache / songUrlCache
        // entry. The container-mismatch guard above has either confirmed the cached container matches the
        // target quality or set shouldBypassCache (and ghost-removed the mismatched playerCache entry) to
        // force a fresh fetch. So no cache hit is ever served with a container mismatch (garbled audio).
        if (!shouldBypassCache) {
            // PARTIAL download whose container the guard above confirmed matches the target quality — serve
            // the cached bytes (the CacheDataSource fills the missing tail from the network in the same,
            // matching container). A mismatching partial set shouldBypassCache above and skips this block.
            if (downloadCache.isCached(
                    mediaId,
                    dataSpec.position,
                    if (dataSpec.length >= 0) dataSpec.length else 1
                )
            ) {
                // Partial download served from downloadCache: same offline intent as the full hit above.
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                return@Factory dataSpec
            }

            // HONEST AUDIO CACHE HIT (adversarial audit FASE 2-A #1): the old isCached(mediaId)
            // never hit because streamed bytes live under yt-stream-* keys; the first fix
            // (any key of the song) over-counted VIDEO keys — video itags (137/136/muxed 22)
            // also land in playerCache now, and dbFormat only reflects the last AUDIO delivery,
            // so a video-only cache counted as an audio hit (StreamHealth lied, and a stale
            // URL could be returned without re-resolve). Derive the EXACT audio key from the
            // fresh URL we are about to serve and check only that one. If no fresh URL exists
            // there is nothing to serve anyway (the block below needs it to return).
            val freshUrlEntry = songUrlCache[mediaId]?.takeIf { it.expiresAt > System.currentTimeMillis() }
            // STABLE IDENTITY FIX (2026-09-04): the probe key must be the one the WRITER used —
            // yt-stream-<mediaId>-<itag>. The old probe derived the `id=` param of the live URL,
            // which is the ROTATING o-XXXX stream id: intra-session it matched (same URL), but it
            // probed a key no writer would ever use after the factory fix. Same identity in writer
            // and reader or the hit test lies again.
            val audioCacheKey = freshUrlEntry?.url?.let { url ->
                val uri = url.toUri()
                if (uri.host?.endsWith("googlevideo.com") == true) {
                    uri.getQueryParameter("itag")?.let { itag ->
                        StreamCacheKeys.build(mediaId, itag)
                    }
                } else {
                    null
                }
            }
            if (playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH) ||
                (audioCacheKey != null && playerCache.isCached(audioCacheKey, dataSpec.position, CHUNK_LENGTH))
            ) {
                freshUrlEntry?.let {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    StreamHealth.cacheHit()
                    refreshUrlIfNearExpiry(mediaId, it)
                    return@Factory dataSpec.withUri(it.url.toUri())
                }
                // FIX C (#28.2): cached BYTES are present but we have no fresh stream URL (e.g. after an
                // app-update restart, when songUrlCache started empty). Do NOT delete the cached bytes and
                // force a full re-download — that was the "ghost cache" churn that made every song slow
                // after an update (and why "clear song cache" wrongly seemed to help). Instead KEEP the
                // cached bytes and fall through to re-resolve ONLY the URL below; the fresh URI is stored in
                // songUrlCache and returned, and the CacheDataSource serves the cached bytes while fetching
                // just the missing tail from the refreshed URI.
                Timber.tag(TAG).w("Ghost cache entry for $mediaId — keeping cached bytes, re-resolving URL only")
            }

            songUrlCache[mediaId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                StreamHealth.cacheHit()
                refreshUrlIfNearExpiry(mediaId, it)
                return@Factory dataSpec.withUri(it.url.toUri())
            }
        }

        if (shouldBypassCache) {
            Timber.tag("MusicService").i("BYPASSING CACHE for $mediaId due to quality change")
        }

        Timber.tag("MusicService").i("FETCHING STREAM: $mediaId | quality=$lockedQuality")
        val resolveStartMs = android.os.SystemClock.elapsedRealtime()
        StreamHealth.freshResolveStarted()
        val playbackData = try {
            audioStreamResolveInFlight.incrementAndGet()
            runBlocking(Dispatchers.IO) {
                val dbSongReadStartMs = android.os.SystemClock.elapsedRealtime()
                val dbSong = database.song(mediaId).firstOrNull()
                val knownArtist = dbSong?.artists?.joinToString { it.name }?.replace(" - Topic", "")
                val knownTitle = dbSong?.song?.title
                val knownDuration = dbSong?.song?.duration?.let { if (it > 0) it * 1000L else null }
                // Both loader-thread Room reads (format above + song here) reported as RESOLVE_TIMING db=.
                val preResolveDbMs = dbFormatReadMs + (android.os.SystemClock.elapsedRealtime() - dbSongReadStartMs)

                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = lockedQuality,
                    connectivityManager = connectivityManager,
                    context = this@createDataSourceFactory,
                    knownArtist = knownArtist,
                    knownTitle = knownTitle,
                    knownDurationMs = knownDuration,
                    preResolveDbMs = preResolveDbMs
                )
            }
        } finally {
            audioStreamResolveInFlight.decrementAndGet()
        }.getOrElse { throwable ->
            StreamHealth.freshResolveFailed()
            when (throwable) {
                // UNRESOLVABLE SONG dead-end (fix #1): region-locked, premium/members-only,
                // deleted-but-listed, age-restricted-for-guests, no playable format/URL, or the
                // resolution timed out. This is NOT a network problem — map it to NO_STREAM carrying
                // the real reason, so onPlayerError skips the song with a message instead of looping
                // forever in a fake "no internet" state. NEVER map this to a network code.
                is iad1tya.echo.music.utils.YTPlayerUtils.StreamResolutionException -> {
                    throw PlaybackException(
                        throwable.reason,
                        throwable,
                        ERROR_CODE_NO_STREAM
                    )
                }

                is PlaybackException -> throw throwable

                is java.net.ConnectException, is java.net.UnknownHostException -> {
                    throw PlaybackException(
                        getString(R.string.error_no_internet),
                        throwable,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    )
                }

                is java.net.SocketTimeoutException -> {
                    throw PlaybackException(
                        getString(R.string.error_timeout),
                        throwable,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    )
                }

                else -> throw PlaybackException(
                    getString(R.string.error_unknown),
                    throwable,
                    PlaybackException.ERROR_CODE_REMOTE_ERROR
                )
            }
        }

        // Reaching here means the resolve succeeded (every getOrElse branch throws), so close the
        // StreamHealth timing window started above.
        StreamHealth.freshResolveCompleted(android.os.SystemClock.elapsedRealtime() - resolveStartMs)
        // Owner 2026-10-06 — a song resolved: the session and the network work, so earlier CONTENT
        // failures of other songs are real (UnavailableRegistry), and this one is playable again.
        iad1tya.echo.music.utils.UnavailableSongs.recordSuccess(mediaId)

        val nonNullPlayback = requireNotNull(playbackData) {
            getString(R.string.error_unknown)
        }
        run {
            val format = nonNullPlayback.format

            val isFinalLossless = DeliveredQuality.isLossless(format.mimeType)
            val isFinalSaavn = DeliveredQuality.isSaavn(format.mimeType)

            if (dbFormat != null && !shouldBypassCache) {
                val cacheIsLossless = dbFormat.codecs == "flac"
                val cacheIsSaavn = dbFormat.codecs == "mp4a.40.2" || dbFormat.mimeType.contains("mp4", ignoreCase = true)

                if (isFinalLossless != cacheIsLossless || isFinalSaavn != cacheIsSaavn) {
                    Timber.tag(TAG).w("Format fallback detected AFTER fetch. Clearing playerCache to prevent mismatch crash.")
                    playerCache.removeResource(mediaId)
                    // GHOST-BYTES FIX 2026-09-03: same as the pre-fetch guard above — the real
                    // listen bytes sit under yt-stream-* keys; purge them or the stale-container
                    // bytes remain orphaned on disk AND the re-open re-downloads everything.
                    StreamCacheKeys.keysOf(playerCache.keys, mediaId).forEach { key ->
                        runCatching { playerCache.removeResource(key) }
                    }

                    // Don't throw when this is the FIRST open of a fresh period (position 0). There the
                    // extractor has not been sniffed yet, so it re-sniffs these very bytes and handles
                    // whatever container arrives — the throw is pure harm: a gratuitous fatal error plus
                    // a re-prepare (one audible cut) at the START of a LOSSLESS/SAAVN track whose fuzzy
                    // Qobuz/Saavn lookup (a 9s-timeout external search — routine, and nondeterministic
                    // per attempt) landed on a different container than the persisted row.
                    //
                    // From the media3 1.10.1 bytecode: BundledExtractorsAdapter.init sniffs the extractor
                    // ONCE and caches it for the period's life; ProgressiveMediaPeriod.startLoading gates
                    // setLoadPosition on `prepared`, so a fresh period's first open is position 0. The
                    // implication is one-way: position 0 ⇒ (almost always) not-yet-committed. It is NOT a
                    // biconditional — a prepared, unknown-length/unseekable period can re-open at 0 via
                    // configureRetry(0,0); that needs !isLengthKnown, unreachable for these
                    // Content-Length'd streams, and if it ever hit, the mismatched bytes reach the
                    // committed extractor which raises ParserException → the same fatal recovery we
                    // produce today. So this only ever degrades to current behaviour, never worse.
                    //
                    // HONEST SCOPE (registry #57 stays OPEN): a MID-SONG re-resolve always re-opens at a
                    // NONZERO offset, so the throw below still fires for every mid-song container flip —
                    // exactly the "corta microsegundos y aparece más adelante" case. This change only
                    // removes the cut at track START (the explicitly-tapped-song residue). On OPUS the
                    // whole fallback roulette is skipped so neither can fire — the testable prediction.
                    //
                    // Falling through instead still repairs everything: playerCache.removeResource above
                    // already dropped the stale-container bytes, and execution reaches the FormatEntity
                    // upsert below, which corrects the row. bypassCacheForQualityChange is unnecessary
                    // here — the loop it guards against only existed because the throw pre-empted that
                    // upsert.
                    if (isCurrentlyPlaying && dataSpec.position != 0L) {
                        // Registry #57: do NOT throw mid-song — that forced an audible cut/restart.
                        // Cache bytes were already purged above; falling through upserts the corrected
                        // FormatEntity so the next open uses the right container without a hard restart.
                        bypassCacheForQualityChange.add(mediaId)
                        Timber.tag(TAG).w(
                            "Format changed mid-stream for $mediaId — purged cache and continuing without restart",
                        )
                    }
                }
            }

            // Keep any loudness we already had if this (re)fetch doesn't carry it — e.g. the
            // auto-download on "like" re-stores the format and can come back WITHOUT loudness;
            // overwriting the real value with null made normalization fall back to the default and
            // drop the volume.
            val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb ?: dbFormat?.loudnessDb
            val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb ?: dbFormat?.perceptualLoudnessDb
            // Preserve a previously-measured loudness too: a re-fetch (e.g. the like/auto-download) must
            // not wipe the cached measurement (it would force a needless re-measure on the next play).
            val measuredLoudnessDb = dbFormat?.measuredLoudnessDb

            Timber.tag(TAG).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb, measuredLoudnessDb: $measuredLoudnessDb")
            if (loudnessDb == null && perceptualLoudnessDb == null) {
                Timber.tag(TAG).w("No loudness data available from YouTube for video: $mediaId")
            }

            // Prime Safe Volume from THIS same player-response (no extra network, no Room wait)
            // BEFORE open() returns, so the first decoded sample is already at the locked level.
            lockLoudnessIfCurrent(mediaId, loudnessDb, perceptualLoudnessDb, measuredLoudnessDb)

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        // Derive the codec safely. split("codecs=")[1] threw IndexOutOfBounds for a
                        // mimeType with no codecs parameter; and an empty codec reads back as OPUS, which
                        // makes the format guard mis-fire on EVERY open for a LOSSLESS/SAAVN user — the
                        // #57 mechanism. Fall back to the container, then to the row we already have.
                        codecs = codecsFromMimeType(format.mimeType, dbFormat?.codecs),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength ?: 0L,
                        loudnessDb = loudnessDb,
                        perceptualLoudnessDb = perceptualLoudnessDb,
                        measuredLoudnessDb = measuredLoudnessDb,
                        playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    )
                )
            }
            scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

            
            if (bypassCacheForQualityChange.remove(mediaId)) {
                Timber.tag("MusicService").d("Cleared bypass cache flag for $mediaId after fresh fetch")
            }

            val streamUrl = nonNullPlayback.streamUrl

            // Stamp the quality that was DELIVERED, derived from the response with the SAME predicate the
            // container guard uses (isFinalLossless/isFinalSaavn) — NEVER `lockedQuality`, which is only what
            // we ASKED for. Fallback is routine (LOSSLESS -> Qobuz fails -> Saavn fails -> Opus), so stamping
            // the request would make this entry disagree with the FormatEntity describing the same stream:
            // the guard would then see a container mismatch for the very track that is playing, purge its
            // cached bytes and re-resolve on every re-open — #28 all over again, on a loop.
            val deliveredQuality = DeliveredQuality.fromMimeType(format.mimeType)
            songUrlCache[mediaId] = CachedStream(
                url = streamUrl,
                expiresAt = System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L),
                delivered = deliveredQuality,
                // NOT `lockedQuality`: its middle branch IS the cached DELIVERED value, so stamping it here
                // would feed a delivered value straight back into the field whose only reader asks "what did
                // the user ASK for?" — re-overloading the field this type exists to split. `forceOpus` is
                // genuinely the request for this track (and dropping it at cold start is right: a per-session
                // refetch must not survive a restart); otherwise the request is the global quality.
                requested = if (forceOpus) iad1tya.echo.music.constants.AudioQuality.OPUS else audioQuality,
            )
            // FIX B1 (#28.1): persist the freshly-resolved URL (whole cache snapshot) so it survives a
            // restart / app update. Off the main thread; never blocks this resolve.
            persistSongUrlCache()

            // Embedded-player fallback URLs (see EmbeddedPlayerUrlResolver) carry the exact
            // request headers YouTube's own embedded player sent — replay them defensively,
            // since googlevideo URLs can 403 without the right per-client User-Agent (see
            // videoOkHttpClient below) and we don't control which client generated this URL.
            val fallbackHeaders = nonNullPlayback.fallbackRequestHeaders
            return@Factory if (!fallbackHeaders.isNullOrEmpty()) {
                dataSpec.buildUpon().setUri(streamUrl.toUri()).setHttpRequestHeaders(fallbackHeaders).build()
            } else {
                dataSpec.withUri(streamUrl.toUri())
            }
        }
    }
}

/**
 * Refetch ("volver a obtener"): drop everything that would let the NEXT play of [songId] serve the OLD
 * audio. Clearing songUrlCache is the load-bearing half — the resolver returns a cached-URL hit long
 * before it would re-ask YouTube, so removing the bytes alone changes nothing. The persisted mirror is
 * rewritten in the same breath or the stale URL simply returns on the next cold start.
 *
 * #28 forbids dropping cached BYTES *implicitly* (when we merely lack a URL — the "ghost cache" churn);
 * this path is the user explicitly asking for a fresh stream, which is the orthogonal case. The per-mediaId
 * lookup shape and the persistence invariant it protects are untouched.
 *
 * Disk work runs off the main thread (onStartCommand is Main); the caches are concurrent, so no lock.
 */
internal fun MusicService.clearSongCache(songId: String) {
    songUrlCache.remove(songId)
    persistSongUrlCache()
    scope.launch(Dispatchers.IO) {
        runCatching { playerCache.removeResource(songId) }
            .onFailure { Timber.tag(TAG).d(it, "clearSongCache: playerCache removal failed (non-fatal)") }
        // GHOST-BYTES FIX 2026-09-03: "volver a obtener" must also drop the REAL listen bytes
        // (yt-stream-* keys), or the old stream would still sit on disk while the refetch
        // re-downloads — the user asked for a fresh stream, not for a second copy.
        StreamCacheKeys.keysOf(playerCache.keys, songId).forEach { key ->
            runCatching { playerCache.removeResource(key) }
                .onFailure { Timber.tag(TAG).d(it, "clearSongCache: yt-stream removal failed (non-fatal)") }
        }
    }
    Timber.tag(TAG).i("clearSongCache: dropped cached stream URL + bytes for $songId")
}

internal suspend fun MusicService.getStreamUrl(mediaId: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = mediaId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
            ).getOrNull()
            playbackData?.streamUrl
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to get stream URL for Cast")
            null
        }
    }
}
