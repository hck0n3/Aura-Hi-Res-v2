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
import iad1tya.echo.music.playback.MusicService.Companion.CONTEXT_DROP_MIN_SURVIVORS
import iad1tya.echo.music.playback.MusicService.Companion.CONTEXT_ENRICH_PER_BATCH
import iad1tya.echo.music.playback.MusicService.Companion.CONTEXT_ENRICH_WAIT_MS
import iad1tya.echo.music.playback.MusicService.Companion.CONTEXT_STUDY_ARTISTS
import iad1tya.echo.music.playback.MusicService.Companion.ENRICH_BEFORE_SCORE_MS
import iad1tya.echo.music.playback.MusicService.Companion.GENRE_LEARN_PER_RUN
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
 * Plan C1, phase 1 (row 351): the radio/continuation ENGINE — what plays when a queue ends (mood, the
 * finished collection's own continuation, last-song radio, related, the replay last resort) — moved out of
 * MusicService.kt (13 000+ lines) into its own file. The body is byte-for-byte the method it was; only
 * `this@MusicService` became `this@startRadioSeamlessly` and the members it reads went from private to
 * internal. Behaviour is unchanged; see docs/REGRESSION_REGISTRY.md rows #22, #34, #60, #95, #299–#313,
 * #321/#324 and #344/#346 for what this engine guarantees.
 */
fun MusicService.startRadioSeamlessly() {
    // Offline mode: never seed radio / related / YouTube next — that is network by definition.
    if (dataStore.get(OfflineModeKey, false)) {
        resumeAfterSeed = false
        advanceIntoRadioRequested = false
        return
    }

    if (!playerInitialized.value) {
        Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
        resumeAfterSeed = false // never reach the finally on this early return; don't leave it armed
        advanceIntoRadioRequested = false
        return
    }

    // A B3 head-start (or a prior call) is already fetching — do NOT launch a second seed (registry #60).
    // Callers that need to jump into the result must [requestAdvanceIntoRadio] first.
    //
    // Ronda 10 — see [radioSeedInFlightGeneration]'s own doc comment: only a claim for the CURRENT
    // generation blocks a new one. A stale claim left over from a queue the user already moved past
    // (a NEW playQueue() bumped queueGeneration since it was made) no longer counts — it will settle
    // as a harmless no-op on its own (its own generation check in appendSeed already covers that),
    // and must not stop the CURRENT queue from ever getting its own seed.
    if (radioSeedInFlight && radioSeedInFlightGeneration == queueGeneration) {
        return
    }

    // Fila #311 — see [initialLoadPendingGeneration]. The current queue's own list is still loading:
    // seeding now would seed from the PREVIOUS queue's anchor/pool. resumeAfterSeed and
    // advanceIntoRadioRequested are deliberately left armed — [seedAfterInitialLoad] honours them.
    if (initialLoadPendingGeneration == queueGeneration) {
        return
    }

    val currentMediaMetadata = player.currentMetadata ?: run {
        resumeAfterSeed = false
        advanceIntoRadioRequested = false
        return
    }
    val currentMediaId = currentMediaMetadata.id

    // Claimed SYNCHRONOUSLY — that is the whole point of the guard. `scope` is the NON-immediate
    // Dispatchers.Main, so `launch` always posts and the body runs in a LATER main-thread message. Setting
    // the flag inside the coroutine therefore leaves the guard unclaimed for the rest of the CURRENT
    // dispatch, and media3's ListenerSet delivers every callback of one player update in a single
    // synchronous flush: onMediaItemTransition (B3 pre-seed) and onEvents (scheduleCrossfade -> seed) both
    // see `false` and BOTH fire. The second seed's appendSeed then removes the tail the first just appended,
    // while sessionPlayedIds has already burned those ids via the #22 no-repeat filter — the batch is lost
    // for good, and the crossfade gets armed twice against an index the second seed deletes.
    //
    // The cancelled-scope leak this used to guard against is handled by invokeOnCompletion below, which
    // runs even when the coroutine body never starts.
    radioSeedInFlight = true
    // Captured synchronously, same moment as the claim above: this is the generation appendSeed()
    // below checks against before it ever touches the player, so a NEW playQueue()/adoptExternalQueue()
    // landing while this seed is still in flight makes its eventual append a no-op instead of
    // overwriting the new queue's tail with the OLD context's songs (see [queueGeneration]).
    val seedGeneration = queueGeneration
    // See [radioSeedInFlightGeneration]'s own doc comment: this claim is now tagged with the
    // generation it belongs to, so a LATER claim (a newer queue's own seed) can tell this one is
    // stale without waiting for it, and this one's own completion won't clear a later claim's state.
    radioSeedInFlightGeneration = seedGeneration
    // Ronda 10 — see [radioOriginContextId]'s own doc comment.
    val originContextId = radioOriginContextId

    val seedJob = scope.launch(SilentHandler) {
        // Resolve the YouTube videoId to seed the radio from. For a normal online track the mediaId IS the
        // videoId. For a LOCAL library track (content://) or a direct-URL (http) podcast the mediaId is NOT a
        // YouTube id — tryRadio/tryRelated would fail and we'd loop forever on the replay last-resort instead
        // of getting real infinite radio. So look the current song up on YouTube by "title artist" and seed
        // from that match. Runs on IO (network). Null → no YouTube identity found → skip radio, fall through
        // to the replay last-resort. (Kept off the player thread; only addMediaItems below runs on Main.)
        val seedVideoId: String? = withContext(Dispatchers.IO) {
            if (!currentMediaId.isLocalMediaId() &&
                !currentMediaId.startsWith("http", ignoreCase = true)
            ) {
                currentMediaId
            } else {
                val artistText = currentMediaMetadata.artists.joinToString(" ") { it.name }
                val query = listOf(currentMediaMetadata.title, artistText)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                if (query.isBlank()) null
                else runCatching {
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        ?.items?.filterIsInstance<SongItem>()?.firstOrNull()?.id
                }.getOrNull()
            }
        }

        // Genre-aware continuation: build the CONTEXT PROFILE of the finished collection ONCE (first
        // re-seed of this context), from the WHOLE radioSeedPool — playlist/album/EP/single uniformly
        // (a 1-track single profiles too; only a pure radio, pool empty, has nothing to profile).
        // Off the player thread (IO), runCatching → a failed build leaves the profile null and every
        // consumer behaves exactly as today. Also fire-and-forget enrich of the CONTEXT's own artists
        // (WiFi-only, GenreCache bounds to 40 + semaphore 4 — mirrors the Path A learn site) so the
        // profile's coverage of THIS context warms up within the session. No cache-bias violation:
        // we enrich exactly the population we score (registry #39).
        // Rebuild on EVERY re-seed, not once per context: the profile froze the genre shares at build
        // time, and the enrich below keeps teaching GenreCache new artists — so a profile built from a
        // cold cache (fresh install, WiFi-only gate, first listen to a genre) stayed blind for the
        // whole context life while the cluster step right next to it already saw the fresh snapshot,
        // and the off-context sink judged with stale shares. The build is pure and bounded, runs on
        // IO, fires only at re-seed time (not per boundary), and the `radioSeedPool !== pool` identity
        // guard below discards a build that a context switch overtook. Monotonic cache growth means an
        // active profile can only get MORE informed — fail-neutral in every direction.
        if (radioSeedPool.isNotEmpty()) {
            runCatching {
                val pool = radioSeedPool
                val built = withContext(Dispatchers.IO) {
                    val genres = iad1tya.echo.music.reco.GenreCache.snapshot(this@startRadioSeamlessly)
                    iad1tya.echo.music.reco.ContextProfile.build(
                        pool.map { mm ->
                            iad1tya.echo.music.reco.ContextProfile.Track(
                                artists = mm.artists.map { it.name },
                                title = mm.title,
                                album = mm.album?.title,
                            )
                        },
                        genres,
                    )
                }
                // IDENTITY guard: playQueue may have swapped the context while we were parked on IO
                // (it reassigns radioSeedPool and nulls the profile). Assigning then would pin the OLD
                // playlist's profile onto the NEW context for its whole life — steer toward the wrong
                // genres. The pool reference changes ONLY at that reassignment site, so === is exact.
                if (radioSeedPool !== pool) return@runCatching
                contextProfile = built
                scope.launch(Dispatchers.IO + SilentHandler) {
                    // Most frequent primary artists first (ContextPattern.studyArtists), then everyone
                    // else: GenreCache spends its bounded lookups in this order, and the artists with
                    // the most tracks are the ones that decide the profile's genre shares.
                    val names = (
                        iad1tya.echo.music.reco.ContextPattern.studyArtists(
                            pool.mapNotNull { it.artists.firstOrNull()?.name },
                            CONTEXT_STUDY_ARTISTS,
                        ) + pool.flatMap { it.artists }.map { it.name }
                        ).filter { it.isNotBlank() }.distinctBy { it.trim().lowercase() }
                    if (names.isNotEmpty()) {
                        runCatching {
                            iad1tya.echo.music.reco.GenreCache.enrich(this@startRadioSeamlessly, names, onlyWifi = true)
                        }
                    }
                }
            }
        }

        // Appends a batch after the current item, re-orders it by the user's taste, and — if we were waiting
        // at a TRUE end-of-queue (resumeAfterSeed armed) — advances into it + resumes. Returns true if it
        // actually appended anything. Wrapped by the callers so a failure simply falls through to the next
        // source. The !isPlaying resume guard (NOT == STATE_ENDED): addMediaItems can move the player out of
        // STATE_ENDED into READY-paused, which would make a STATE_ENDED check false and leave the music
        // stopped; !isPlaying still resumes then, yet won't yank playback if the user already started
        // something else during the async fetch.
        suspend fun appendSeed(
            rawItems: List<MediaItem>,
            // Owner log 2026-10-05 — candidate id → the lane of the pattern seed that brought it
            // (ContextPattern.inheritedLanes). Only ever an ORDER hint for an unknown-genre candidate
            // inside the pattern; empty for every non-pattern caller (byte-identical to before).
            inheritedLanes: Map<String, String> = emptyMap(),
        ): Boolean {
            // Owner 2026-10-06 — never append a song known to be unavailable (UnavailableRegistry): it
            // would only fail, toast and skip. An empty result is the normal "this source gave nothing".
            val playableItems = rawItems.filterNot { iad1tya.echo.music.utils.UnavailableSongs.isUnavailable(it.mediaId) }
            if (playableItems.isEmpty()) return false
            // Ronda 10 — see [radioOriginContextId]'s own doc comment. Hard-drop, same strength as
            // maybeLoadMoreQueuePages' own sessionPlayedIds filter: a persistent, cross-restart
            // "already served after THIS collection" memory. Never below 1 candidate collapses the
            // batch to nothing when the whole batch was already heard last time — that degrades to
            // "nothing new this attempt", which the caller already treats as a normal empty batch
            // (falls through to the next source / the replay last-resort), never a crash or silence.
            val items = if (originContextId != null) {
                val playedBefore = withContext(Dispatchers.IO) {
                    runCatching { database.radioContinuationPlayedIds(originContextId) }.getOrDefault(emptyList())
                }.toHashSet()
                if (playedBefore.isEmpty()) playableItems else playableItems.filterNot { it.mediaId in playedBefore }
            } else {
                playableItems
            }
            if (items.isEmpty()) return false
            // ENRICH BEFORE SCORING (see [ENRICH_BEFORE_SCORE_MS]) — the ROOT CAUSE of the genre
            // mixing, as opposed to the two mitigations further down (the CTX_SINK partition and the
            // unknown-genre nudge). Both that partition and orderedByTaste's own steer read a
            // GenreCache SNAPSHOT; taking it before this batch's own artists had ever been looked up
            // meant scoring a batch we knew nothing about. Launched on `scope` so the timeout cancels
            // only the WAIT — the run itself survives and still fills the cache for the next batch,
            // which is exactly the fire-and-forget behaviour it replaces. Skipped when the steer is
            // off (the snapshot is unused) and when the player is parked at a true end of queue
            // waiting for these very items (resumeAfterSeed), where a wait would be silence.
            //
            // FIRST, before `liveIndex` below: this suspends for up to a second and a half, and that
            // index must be read from the LIVE player as late as possible — capturing it and then
            // waiting is precisely the staleness its own comment exists to prevent.
            // Ronda 10 ("mejora el algoritmo de lo relacionado"): a single-song anchor (radioAnchorId,
            // no collection profile) needs the same pre-scoring enrichment as a collection profile does,
            // so the anchor lane check below (and NOT just later pagination) has real genre data on the
            // very FIRST radio batch instead of only from the second batch onward.
            val steerNeedsGenres = contextSteerActive && keepGenreLaneHint &&
                (contextProfile?.active == true || radioAnchorId != null)
            // A batch that will be laid out by the collection's genre PATTERN (same gate as
            // `followPattern` below). Owner log 2026-10-04: a one-genre playlist's continuation scored
            // 49 candidates with only 21 known and 28 UNKNOWN — the 12-artist budget learns a quarter
            // of a 50-song batch, and every unknown can only be ordered after the pattern, never
            // placed in it. These batches get a larger budget (CONTEXT_ENRICH_PER_BATCH, primary
            // artists first, two GenreCache runs of ≤15) and a longer wait. Inaudible: this seed runs
            // while the collection's last song still plays (B3 / crossfade early seed); the parked-at-
            // the-end case (resumeAfterSeed) still skips the wait entirely.
            val patternBatch = contextSteerActive && keepGenreLaneHint &&
                contextProfile?.let { it.active && it.genreShare.isNotEmpty() } == true
            // Owner log 2026-10-05 11:55 — at a TRUE end of queue (resumeAfterSeed: the player is parked
            // waiting for these very items) the wait below would be silence, so it is still skipped; but
            // the lookup used to be skipped WITH it, so that batch — 52 candidates, 42 of unknown genre —
            // taught the cache nothing, and neither could the next one benefit. Now the lookup always
            // runs (same budget, same Wi-Fi/mobile preference inside GenreCache); only the WAIT depends
            // on resumeAfterSeed.
            if (steerNeedsGenres) {
                val waitForGenres = !resumeAfterSeed
                val anchorArtists = radioAnchorMetadata?.artists.orEmpty().map { it.name }
                    .filter { it.isNotBlank() }.distinct()
                val candidateArtists = if (patternBatch) {
                    (items.mapNotNull { it.metadata?.artists?.firstOrNull()?.name } +
                        items.flatMap { it.metadata?.artists.orEmpty() }.map { it.name })
                        .filter { it.isNotBlank() }
                        .distinctBy { it.trim().lowercase() }
                        .take(CONTEXT_ENRICH_PER_BATCH)
                } else {
                    (anchorArtists +
                        items.flatMap { it.metadata?.artists.orEmpty() }.map { it.name })
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(GENRE_LEARN_PER_RUN)
                }
                if (candidateArtists.isNotEmpty()) {
                    val enrichJob = scope.launch(Dispatchers.IO + SilentHandler) {
                        runCatching {
                            // Ronda 10 (dueño: Elvis Crespo -> YouTube's own "next" devolvió puro
                            // cristiano/alabanza tras varias pruebas seguidas con Jesús Adrián Romero
                            // — evidencia real: CTX_GENRE reportó "completed=true" pero ningún
                            // CTX_SINK siguió, o sea anchorLane terminó null pese al enrich). Causa:
                            // GenreCache.enrich() aborta el lote entero tras 3 fallos SEGUIDOS
                            // (MAX_CONSECUTIVE_FAILURES) — con 4 llamadas en paralelo, unos pocos
                            // artistas de nicho sin ficha en iTunes bastan para tumbar el lote ANTES
                            // de que le toque el turno al ancla, dejándolo sin resolver igual que a
                            // los demás. El ancla es la ÚNICA búsqueda de la que depende este filtro
                            // — se enriquece aparte primero, con su propio contador de fallos, para
                            // que una racha de candidatas desconocidas nunca la deje afuera.
                            if (anchorArtists.isNotEmpty()) {
                                iad1tya.echo.music.reco.GenreCache.enrich(
                                    this@startRadioSeamlessly, anchorArtists, onlyWifi = true,
                                )
                            }
                            iad1tya.echo.music.reco.GenreCache.enrich(
                                this@startRadioSeamlessly, candidateArtists, onlyWifi = true,
                            )
                            // GenreCache learns ≤15 unknown artists per run and skips the ones it now
                            // knows, so a second run reaches the rest of a pattern batch's budget.
                            if (patternBatch) {
                                iad1tya.echo.music.reco.GenreCache.enrich(
                                    this@startRadioSeamlessly, candidateArtists, onlyWifi = true,
                                )
                            }
                        }
                    }
                    if (waitForGenres) {
                        val waitMs = if (patternBatch) CONTEXT_ENRICH_WAIT_MS else ENRICH_BEFORE_SCORE_MS
                        val waited = withTimeoutOrNull(waitMs) { enrichJob.join() } != null
                        Timber.tag(TAG).i(
                            "CTX_GENRE enrich-before-score: %d artists, completed=%b",
                            candidateArtists.size, waited,
                        )
                    } else {
                        Timber.tag(TAG).i(
                            "CTX_GENRE enrich-in-background (parked at end, no wait): %d artists",
                            candidateArtists.size,
                        )
                    }
                }
            }
            // Recompute the index from the LIVE player at append time (not a stale value captured before the
            // network fetch), so we never remove items relative to a position that has since moved.
            val liveIndex = player.currentMediaItemIndex
            val itemCount = player.mediaItemCount
            // Order FIRST, then decide. orderedByTaste() can return an EMPTY list (e.g. every candidate is a
            // hard-disliked artist), and the old order — remove-then-append — truncated the queue to the
            // current track and appended nothing, leaving the resume seekTo() pointing past the end. Never
            // destroy the tail before we know we have something to put in its place.
            // OFF-CONTEXT DROP (the reason "smart queue" predictions felt unrelated): the steering
            // term is a bounded NUDGE — clamped to [-4,+6] against an index-dominated key, it can
            // displace a candidate ~10 ranks but can never REMOVE it, so when YouTube returns a bad
            // batch for a niche context (salsa, worship), the wrong songs still played, just slightly
            // later. This drops candidates whose genre is KNOWN and has ZERO share in the context —
            // and only those. Hard constraints, in order of the registry rules they serve:
            //  • unknown-genre candidates stay ELIGIBLE untouched (#39/#41: a cache-derived signal
            //    must never drop unknowns, or the infinite queue collapses onto the library);
            //  • candidates from EVERY context lane survive (any share > 0), so a mixed playlist
            //    never collapses onto its dominant genre;
            //  • the drop applies only when >= CONTEXT_DROP_MIN_SURVIVORS candidates survive (was 10
            //    while this batch was the one big injection; see that constant) — otherwise the batch
            //    is kept unfiltered, and appendSeed's callers already fall through to the next source,
            //    so never-silence holds;
            //  • gated on the same user toggle (default ON) that gates the shipped pagination drop,
            //    making the defense symmetric instead of new; ONE GenreCache snapshot per batch.
            val profile = contextProfile
            // Owner 2026-10-04 — the collection's continuation follows its OWN genre pattern
            // (ContextPattern). Same gate as the profile branch below, so a cold/inactive profile
            // stays byte-identical to before.
            val followPattern = contextSteerActive && keepGenreLaneHint &&
                profile != null && profile.active && profile.genreShare.isNotEmpty()
            val laneOrdered = if (followPattern && profile != null) {
                val genres = withContext(Dispatchers.IO) {
                    runCatching { iad1tya.echo.music.reco.GenreCache.snapshot(this@startRadioSeamlessly) }
                        .getOrDefault(emptyMap())
                }
                // SINK, never drop. iTunes labels vary WITHIN a genre ("Salsa y Tropical" vs "Pop" on
                // Marc Anthony himself), so an exact-lane DROP removed legitimate adjacent artists —
                // worse than the weak steering it replaced. This is an UNCONDITIONAL stable partition
                // (no survivor threshold, no removal): known-off-context candidates go to the batch
                // tail AND their ids are handed to orderedByTaste, which is what actually keeps them
                // there. Tail position by itself is not enough — the pull cap lifts up to 8 ranks and
                // the exploration quota promotes fresh artists to the front. Never-silence holds
                // trivially: nothing is removed, so the batch can never shrink to empty here.
                val (inContext, offContext) = items.partition { mi ->
                    val m = mi.metadata
                    // Context ARTISTS are never off-context, whatever label iTunes gave them — the
                    // profile's own steerTerm has the same precedence, and dropping/sinking an artist
                    // who is IN the playlist would be self-evidently wrong (finding: frozen shares vs
                    // fresh snapshot resolved playlist artists into lanes the profile never counted).
                    val ctxArtist = m?.artists?.any { a -> a.name.trim().lowercase() in profile.artistSet } == true
                    if (ctxArtist) return@partition true
                    val lane = iad1tya.echo.music.reco.GenreLane.laneOfTrack(
                        genres,
                        m?.artists?.firstOrNull()?.name.orEmpty(),
                        m?.title.orEmpty(),
                        m?.album?.title,
                    )
                    when {
                        // Unknown lane: untouched (#39/#41 — cache-derived signal never judges unknowns).
                        lane == null -> true
                        (profile.genreShare[lane] ?: 0.0) > 0.0 -> true
                        // CHRISTIAN is the strict keyword lane, and an artist NAME alone can fabricate
                        // it ("Cristian Castro"). Sink only when the track's OWN text earns the lane;
                        // any other origin keeps steerTerm's +6 push (shipped behaviour), never a sink.
                        lane == iad1tya.echo.music.reco.GenreLane.CHRISTIAN ->
                            !iad1tya.echo.music.reco.GenreLane.isKeywordChristian(
                                m?.title.orEmpty(), null, m?.album?.title,
                            )
                        else -> false
                    }
                }
                if (offContext.isNotEmpty()) {
                    // OWNER DIRECTIVE (2026-09-04): "la cola infinita mete una canción que nada
                    // que ver". The old SINK-never-drop sent known-off-context candidates to the
                    // batch tail — but the queue consumes the WHOLE batch, so the intruder
                    // played anyway (the owner heard it, skipped it, "y luego sigue bien").
                    // With ≥10 in-context survivors (the threshold the original design
                    // documented), KNOWN-off-context candidates are now DROPPED — unknowns
                    // stay untouched (#39/#41) and never-silence holds: below the threshold
                    // the sink keeps its old job, and callers fall through to the next source
                    // when a batch comes back empty.
                    // Owner 2026-10-04: "si la playlist es de un solo género, la cola tiene que seguir
                    // con ese género". The ≥10 bar dated from when this batch was the ONE big injection
                    // and the queue then paginated a single-song radio; the collection's continuation
                    // now re-seeds through its own pattern every batch (tryContextRadio → EmptyQueue),
                    // so a smaller, cleaner batch only means the next re-seed comes a little sooner.
                    // Unknown-genre candidates still count as survivors and are never dropped (#39/#41).
                    if (inContext.size >= CONTEXT_DROP_MIN_SURVIVORS) {
                        Timber.tag(TAG).i(
                            "CTX_SINK appendSeed: dropped %d/%d off-context candidates (%d in-context survivors)",
                            offContext.size, items.size, inContext.size,
                        )
                        inContext to offContext.mapNotNullTo(HashSet()) { it.mediaId }
                    } else {
                        Timber.tag(TAG).i(
                            "CTX_SINK appendSeed: sank %d/%d off-context candidates to the tail (only %d in-context)",
                            offContext.size, items.size, inContext.size,
                        )
                        (inContext + offContext) to offContext.mapNotNullTo(HashSet()) { it.mediaId }
                    }
                } else {
                    inContext to emptySet<String>()
                }
            } else if (
                // Ronda 10 ("mejora el algoritmo de lo relacionado", dueño: Bob Marley -> mezcla desde
                // el primer lote): a single-song radio (no collection, so no [profile]) used to append
                // its FIRST batch with zero genre-lane protection at all — the lane filter only existed
                // in the pagination path (maybeLoadMoreQueuePages), so anything YouTube's own radio
                // algorithm mixed in from batch 1 played unfiltered. Same "sink, never silently drop
                // below threshold" contract as the collection branch above, keyed on the ANCHOR song's
                // own lane (radioAnchorMetadata, fixed once — see radioAnchorMetadata's own doc) instead
                // of a genreShare map, since a single song has no share distribution to consult.
                contextSteerActive && keepGenreLaneHint && radioAnchorId != null
            ) {
                val genres = withContext(Dispatchers.IO) {
                    runCatching { iad1tya.echo.music.reco.GenreCache.snapshot(this@startRadioSeamlessly) }
                        .getOrDefault(emptyMap())
                }
                // Ronda 10 (Elvis Crespo → puro cristiano): the unknown-anchor branch logs too, so
                // "the anchor has no genre" and "nothing to remove" no longer look identical.
                anchorLanePartition(items, radioAnchorMetadata, genres, "appendSeed")
                    ?: (items to emptySet<String>())
            } else items to emptySet<String>()
            // Row 344 (owner: "después cambia a otro idioma") + row 345b ("cumbia cristiana… no continúa con el
            // mismo género; quiero que sea exacta sin importar género, religión o idioma"): a finished
            // collection keeps its LANGUAGE (TitleLanguage, the track's own text) and its STYLES
            // (GenreLane families; the collection's own name first). Unknown language/style is never judged
            // (#39/#41), artists of the collection are exempt, and the drop needs >= CONTEXT_DROP_MIN_SURVIVORS
            // survivors — otherwise the misfits only sink to the tail.
            val languageOrdered = if (
                contextSteerActive && keepGenreLaneHint &&
                iad1tya.echo.music.reco.CollectionContinuation.isCollection(radioAnchorId != null, radioSeedPool.size)
            ) {
                val dominant = collectionLanguage()
                val styleGenres = withContext(Dispatchers.IO) {
                    runCatching { iad1tya.echo.music.reco.GenreCache.snapshot(this@startRadioSeamlessly) }
                        .getOrDefault(emptyMap())
                }
                val pool = radioSeedPool
                val allowedStyles = iad1tya.echo.music.reco.CollectionContinuation.allowedStyles(
                    trackStyles = pool.map { mm ->
                        iad1tya.echo.music.reco.GenreLane.styleOfTrack(
                            styleGenres, mm.artists.firstOrNull()?.name, mm.title, mm.album?.title,
                        )
                    },
                    titleStyle = radioSeedTitle?.let {
                        iad1tya.echo.music.reco.GenreLane.styleOfTrack(emptyMap(), null, it)
                    },
                )
                val collectionArtists = pool
                    .flatMap { mm -> mm.artists.map { it.name.trim().lowercase() } }
                    .toHashSet()
                var offLanguageCount = 0
                var offStyleCount = 0
                val (fits, misfits) = laneOrdered.first.partition { mi ->
                    val m = mi.metadata
                    if (m?.artists?.any { it.name.trim().lowercase() in collectionArtists } == true) {
                        return@partition true
                    }
                    val offLanguage = iad1tya.echo.music.reco.CollectionContinuation.offLanguage(
                        dominant,
                        listOfNotNull(m?.title, m?.album?.title).joinToString(" "),
                    )
                    val offStyle = iad1tya.echo.music.reco.CollectionContinuation.offStyle(
                        allowedStyles,
                        iad1tya.echo.music.reco.GenreLane.styleOfTrack(
                            styleGenres, m?.artists?.firstOrNull()?.name, m?.title, m?.album?.title,
                        ),
                    )
                    if (offLanguage) offLanguageCount++
                    if (offStyle) offStyleCount++
                    !offLanguage && !offStyle
                }
                when {
                    misfits.isEmpty() -> laneOrdered
                    fits.size >= CONTEXT_DROP_MIN_SURVIVORS -> {
                        Timber.tag(TAG).i(
                            "CTX_COLLECTION_FIT dropped %d/%d (language=%d style=%d styles=%s, %d left)",
                            misfits.size, laneOrdered.first.size, offLanguageCount, offStyleCount,
                            allowedStyles?.sorted()?.joinToString("+") ?: "unknown", fits.size,
                        )
                        fits to (laneOrdered.second + misfits.mapNotNull { it.mediaId })
                    }
                    else -> {
                        Timber.tag(TAG).i(
                            "CTX_COLLECTION_FIT sank %d/%d (language=%d style=%d styles=%s, only %d left)",
                            misfits.size, laneOrdered.first.size, offLanguageCount, offStyleCount,
                            allowedStyles?.sorted()?.joinToString("+") ?: "unknown", fits.size,
                        )
                        (fits + misfits) to (laneOrdered.second + misfits.mapNotNull { it.mediaId })
                    }
                }
            } else {
                laneOrdered
            }
            val toAppend = languageOrdered.first.orderedByTaste(
                languageOrdered.second,
                followContextPattern = followPattern,
                inheritedLanes = inheritedLanes,
            )
            if (toAppend.isEmpty()) return false
            // STALE SEED GUARD (ronda 10, ver [queueGeneration]): a NEW queue landed while this seed's
            // network fetch was in flight — mutating the player now would overwrite THAT queue's tail
            // with this seed's (old context's) songs. Bail exactly like the empty-batch case above; the
            // caller's own "no radio source worked" handling already covers this outcome cleanly.
            if (queueGeneration != seedGeneration) {
                Timber.tag(TAG).i("QUEUE_RACE_GUARD appendSeed aborted stale mine=%d now=%d", seedGeneration, queueGeneration)
                return false
            }
            // Truncate the tail ONLY when playing in order. `liveIndex` is a TIMELINE index, but under
            // shuffle playback follows the shuffle order, so "everything after liveIndex" is an arbitrary
            // slice — not the played tail. Starting a radio from the middle of a shuffled 50-track queue
            // would delete ~46 songs the user had not heard yet. In shuffle we only append; the shuffle
            // order is rebuilt below and the no-repeat filter already stops played tracks coming back.
            if (!player.shuffleModeEnabled && itemCount > liveIndex + 1) {
                player.removeMediaItems(liveIndex + 1, itemCount)
            }
            player.addMediaItems(liveIndex + 1, toAppend)
            sessionPlayedIds.addAll(toAppend.mapNotNull { it.mediaId }) // NO-REPEAT: record what we appended
            sessionPlayedDedupKeys.addAll(toAppend.mapNotNull { it.dedupKeyOrNull() })
            // Ronda 10 — see [radioOriginContextId]'s own doc comment. Fire-and-forget, off the
            // player thread: this is memory for the NEXT time the collection is replayed, never
            // something the current playback needs to wait on.
            if (originContextId != null) {
                val now = System.currentTimeMillis()
                val rows = toAppend.mapNotNull { it.mediaId }
                    .map { iad1tya.echo.music.db.entities.RadioContinuationPlayedEntity(originContextId, it, now) }
                if (rows.isNotEmpty()) {
                    scope.launch(Dispatchers.IO + SilentHandler) {
                        recordPlayedSafely("RADIO_CONTINUATION") { database.insertRadioContinuationPlayed(rows) }
                    }
                }
            }
            _mixActive.value = true
            if (player.shuffleModeEnabled) {
                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }
            // STATE_ENDED arms resumeAfterSeed and waits for !isPlaying. Manual Next also arms
            // advanceIntoRadioRequested so we SEEK into the radio while the last finite track is
            // still audibly playing — otherwise Next appears to do nothing until the song ends.
            if ((resumeAfterSeed && !player.isPlaying) || advanceIntoRadioRequested) {
                resumeAfterSeed = false
                advanceIntoRadioRequested = false
                player.seekTo(liveIndex + 1, 0)
                player.playWhenReady = true
                player.play()
            }
            // A successful append created/changed the "next item" — re-arm the crossfade so the infinite-queue
            // continuation transitions smoothly (especially when we seeded EARLY because this was the last
            // item with no next). scheduleCrossfade() is idempotent (cancel + reset).
            scheduleCrossfade()
            return true
        }

        // C3 — a source that THROWS used to vanish into getOrDefault(false), so app.log never said why the
        // infinite queue failed (row 53), and a cancelled seed job kept calling the network. Cancellation
        // ends the job (invokeOnCompletion below still releases the flags); anything else is logged
        // without its message (it can carry a URL / video id — AGENTS.md rule 4) and falls through as before.
        fun radioSourceFailed(source: String, e: Throwable): Boolean {
            if (e is CancellationException) throw e
            Timber.tag(TAG).w("RADIO_SOURCE %s failed: %s", source, privacySafeSummary(e))
            return false
        }

        // Row 344 — the continuation of a finished album/playlist (not a single-song radio).
        fun inCollection(): Boolean =
            iad1tya.echo.music.reco.CollectionContinuation.isCollection(radioAnchorId != null, radioSeedPool.size)

        // Row 344 — the fallback seed: the playing song only while it belongs to the collection.
        fun collectionFallbackSeed(): String? {
            if (contextSeedHistoryPool !== radioSeedPool) {
                contextSeedHistory.clear()
                contextSeedHistoryPool = radioSeedPool
            }
            return iad1tya.echo.music.reco.CollectionContinuation.fallbackSeed(
                current = seedVideoId,
                collection = radioSeedPool.map { it.id }
                    .filter { !it.isLocalMediaId() && !it.startsWith("http", ignoreCase = true) },
                used = contextSeedHistory,
                anchored = radioAnchorId != null,
                random = randomSeedSource,
            )
        }

        // Source 1 — a proper radio queue seeded from the last song the user heard (or, for a local/direct-URL
        // track, from its resolved YouTube match). No seed id → nothing to seed from → let a later source /
        // the replay last-resort handle it.
        suspend fun tryRadio(): Boolean = runCatching {
            // Genre-aware continuation: an automatic last-song seed still continues the finished context,
            // so steer its batches toward the context profile (no-op while the profile is null/inactive).
            contextSteerActive = true
            // ANCHORED SEEDING (owner directive 2026-09-13): the song the user started from comes first, so
            // a re-seed continues THAT song's content instead of the drift of the last radio pick. Only when
            // the anchor's radio has nothing unheard left does the last song seed, as before.
            // Row 344: inside a finished album/playlist the fallback seeds from the COLLECTION, never from a
            // radio pick that is already playing (that is how each round drifted off the previous drift).
            val collectionFallback = collectionFallbackSeed()
            val seeds = listOfNotNull(radioAnchorId, collectionFallback).distinct()
            if (seeds.isEmpty()) return@runCatching false
            for (seed in seeds) {
                val radioQueue = YouTubeQueue(endpoint = WatchEndpoint(videoId = seed), automaticRadio = true)
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(dataStore.get(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                        .filterNonMusicForAutoQueue()
                }
                if (initialStatus.title != null) queueTitle = initialStatus.title
                val items = initialStatus.items.filter { it.mediaId != seed && it.mediaId != currentMediaId }
                if (appendSeed(items)) {
                    // Row 344: a collection never paginates a single-song radio — next round re-seeds from it.
                    currentQueue = if (inCollection()) EmptyQueue else radioQueue
                    if (inCollection()) contextSeedHistory.add(seed)
                    return@runCatching true
                }
            }
            false
        }.getOrElse { radioSourceFailed("radio", it) }

        // Source 2 — "related" songs of the last song (a different YT endpoint; recovers when radio is empty).
        suspend fun tryRelated(): Boolean = runCatching {
            val seed = collectionFallbackSeed() ?: return@runCatching false
            contextSteerActive = true // same automatic-continuation reasoning as tryRadio
            val nextResult = withContext(Dispatchers.IO) {
                YouTube.next(WatchEndpoint(videoId = seed)).getOrNull()
            }
            val relatedEndpoint = nextResult?.relatedEndpoint ?: return@runCatching false
            val relatedPage = withContext(Dispatchers.IO) { YouTube.related(relatedEndpoint).getOrNull() }
            val items = relatedPage?.songs.orEmpty()
                .filter { it.id != seed && it.id != currentMediaId }
                .map { it.toMediaItem() }
                .filterExplicit(dataStore.get(HideExplicitKey, false))
                .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                .filterNonMusicForAutoQueue()
            val ok = appendSeed(items)
            // CRITICAL for endlessness: the related page is FINITE. Re-point currentQueue at a radio seeded
            // from the genuine last song AND PRIME it (getInitialStatus sets `continuation`, so hasNextPage()
            // is true and the onMediaItemTransition pagination keeps loading forever). hasNextPage() is false
            // on a fresh un-loaded YouTubeQueue, so without priming pagination wouldn't fire. Best-effort: if
            // priming fails, the always-on STATE_ENDED net still re-seeds when this finite batch ends.
            if (ok && inCollection()) {
                // Row 344: finite, like every collection round — the next one re-seeds from the collection.
                contextSeedHistory.add(seed)
                currentQueue = EmptyQueue
            } else if (ok) {
                val rq = YouTubeQueue(endpoint = WatchEndpoint(videoId = seed), automaticRadio = true)
                runCatching { withContext(Dispatchers.IO) { rq.getInitialStatus() } }
                    .onFailure { radioSourceFailed("prime", it) }
                currentQueue = rq
            }
            ok
        }.getOrElse { radioSourceFailed("related", it) }

        // Source 0 — ACTIVE MOOD. When the user has selected a Home mood, seed the infinite radio from that
        // mood's Home feed (YouTube.home(params)) instead of the last song. The songs still flow through
        // orderedByTaste() (relatedness/taste order + recently-played exclusion) so the invariant holds. The
        // mood pool is finite, so we point currentQueue at EmptyQueue: when this batch nears its end the
        // last-item radio-seed net re-invokes startRadioSeamlessly → tryMood() again → a fresh mood batch,
        // keeping it endless AND all-mood, one bounded home() fetch per re-seed. Null mood → returns false →
        // falls through to today's last-song seeding unchanged.
        suspend fun tryMood(): Boolean = runCatching {
            val moodParams = activeMoodParams ?: return@runCatching false
            // #34 — an EXPLICIT mood steer always beats the finished context: stop context steering
            // before this batch is ordered, so the mood's own character is preserved.
            contextSteerActive = false
            val page = withContext(Dispatchers.IO) {
                YouTube.home(params = moodParams).getOrNull()
            } ?: return@runCatching false
            val items = page.sections
                .flatMap { it.items }
                .filterIsInstance<SongItem>()
                .distinctBy { it.id }
                .filter { it.id != currentMediaId }
                .map { it.toMediaItem() }
                .filterExplicit(dataStore.get(HideExplicitKey, false))
                .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                .filterNonMusicForAutoQueue()
            val ok = appendSeed(items)
            if (ok) {
                activeMoodTitle?.let { queueTitle = it }
                // Finite pool → no pagination from a stale last-song queue; the end-of-queue net re-seeds the mood.
                currentQueue = EmptyQueue
            }
            ok
        }.getOrElse { radioSourceFailed("mood", it) }

        // Source 0.5 — CONTEXT multi-seed. When the user started from an album/playlist/list
        // (radioSeedPool > 1), seed the infinite radio from a REPRESENTATIVE SAMPLE of that collection (not
        // just the last song) so the continuation keeps the collection's artist/genre MIX. Picks up to 4
        // distinct-artist seeds (always incl. the current/last song), fetches each one's YouTube radio page,
        // round-robin MERGES + dedupes them, then the shared appendSeed() taste-orders + no-repeat-filters +
        // re-arms crossfade. currentQueue is primed from a seed so the Path A pagination keeps going. Pool <= 1
        // or empty (a pure radio) → returns false → the last-song tryRadio handles it, unchanged. Bounded
        // (<= 4 seed getInitialStatus + 1 prime), off the player thread; only ever runs on a RE-SEED when a
        // finite collection ends.
        suspend fun tryContextRadio(): Boolean = runCatching {
            if (radioSeedPool.size <= 1) return@runCatching false
            // Genre-aware continuation: this IS the context's own continuation — steer its batches
            // toward the context profile (no-op while the profile is null/inactive).
            contextSteerActive = true
            val profile = runCatching { tasteProfile() }.getOrNull()
            // Only online YouTube ids are usable as radio seeds (skip local content:// and direct-URL http).
            fun iad1tya.echo.music.models.MediaMetadata.ytId(): String? =
                id.takeIf { !it.isLocalMediaId() && !it.startsWith("http", ignoreCase = true) }
            // #34 — seed from the LIVE recently-played TAIL (what JUST played), not the play-time first-page
            // snapshot: for a genre-ordered playlist the tail is the genre the user hears at the end, so the
            // continuation matches it (radioSeedPool page 1 = the head genre → felt unrelated). Falls back to
            // radioSeedPool if the timeline read is empty. Safe: runs on the Main-dispatched scope before IO.
            val liveIdx = player.currentMediaItemIndex
            val tailPool: List<iad1tya.echo.music.models.MediaMetadata> =
                if (liveIdx >= 0) {
                    (maxOf(0, liveIdx - 24)..liveIdx).mapNotNull {
                        runCatching { player.getMediaItemAt(it).metadata }.getOrNull()
                    }
                } else emptyList()
            // RE-SEED ANCHOR (kills compounding drift): only tail items that BELONG to the original
            // context count as "tail" — on a re-seed the live tail is the previously appended RADIO
            // songs, and seeding from them made each re-seed drift off the drift of the last one.
            // Intersecting keeps #34's intent exactly (the tail OF THE CONTEXT as it played); when the
            // whole live tail is already radio, fall back to the context pool itself (recent-last order).
            // Gated on the ACTIVE context profile like every other genre-aware step, so a null/inactive
            // profile leaves the seed selection byte-identical to today (fail-neutral rule).
            val steerActive = contextProfile?.active == true
            val contextIds = radioSeedPool.mapTo(HashSet()) { it.id }
            // Row 344: anchored ALWAYS, not only with an active profile. Ungated, a niche album whose genre
            // iTunes does not know seeded its re-seeds from the radio picks already playing — the drift
            // the owner heard ("después mete otra cosa… y cambia a otro idioma y otro género").
            val anchoredTail = tailPool.filter { it.id in contextIds }
            // Recent-first so the DISTINCT-artist reps come from the END of what was playing, not the start.
            // WHOLE-CONTENT STUDY (owner directive 2026-09-13: "que estudie el contenido total de lo que
            // estoy escuchando"): the anchored tail leads (what JUST played), then EVERY other track of the
            // collection follows, so the seed set can reach the album/playlist's full artist range instead
            // of only the last 25 positions.
            val tailIds = anchoredTail.mapTo(HashSet()) { it.id }
            val contextPool = if (anchoredTail.isEmpty()) {
                radioSeedPool.asReversed()
            } else {
                anchoredTail.asReversed() + radioSeedPool.filter { it.id !in tailIds }.asReversed()
            }
            // Distinct primary artist → one representative track (recent-first order).
            val byArtist = LinkedHashMap<String, iad1tya.echo.music.models.MediaMetadata>()
            contextPool.forEach { mm ->
                val key = mm.artists.firstOrNull()?.name?.lowercase() ?: return@forEach
                if (mm.ytId() != null) byArtist.putIfAbsent(key, mm)
            }
            // Prefer higher-taste artists for the (bounded) seed set.
            val ranked = byArtist.values.sortedByDescending { mm ->
                if (profile == null) 0.0 else profile.scoreNames(mm.artists.map { it.name }, mm.title)
            }
            // GENRE-CLUSTER REPRESENTATIVES: cluster the WHOLE context by KNOWN genre (GenreCache lane;
            // unknown-genre tracks form no cluster but stay eligible via the artist/track paths below) and
            // pick one representative per cluster — ACROSS clusters by context share (largest first),
            // WITHIN a cluster by global taste. So a mixed playlist seeds its real genre mix instead of
            // whatever 4 artists the tail happened to hold, and a pure salsa playlist still seeds all-salsa.
            // Gated on the ACTIVE context profile (fail-neutral: inactive → empty → seeds exactly as today).
            val clusters: Map<String, List<iad1tya.echo.music.models.MediaMetadata>> =
                if (steerActive) runCatching {
                    val genres = withContext(Dispatchers.IO) {
                        iad1tya.echo.music.reco.GenreCache.snapshot(this@startRadioSeamlessly)
                    }
                    val byLane = LinkedHashMap<String, MutableList<iad1tya.echo.music.models.MediaMetadata>>()
                    radioSeedPool.forEach { mm ->
                        if (mm.ytId() == null) return@forEach
                        val lane = iad1tya.echo.music.reco.GenreLane.laneOfTrack(
                            genres, mm.artists.firstOrNull()?.name, mm.title, mm.album?.title,
                        ) ?: return@forEach
                        byLane.getOrPut(lane) { mutableListOf() }.add(mm)
                    }
                    byLane
                }.getOrDefault(emptyMap()) else emptyMap()
            val clusterReps: List<String> =
                if (clusters.isNotEmpty()) runCatching {
                    clusters.values
                        .sortedByDescending { it.size }
                        .mapNotNull { tracks ->
                            // Variety between sessions (2026-09-04): the cluster representative
                            // comes from the TOP-2 taste tracks, picked at random — the dominant
                            // lane keeps its strongest material in play, but not the SAME id
                            // every single session.
                            val top = tracks.sortedByDescending { mm ->
                                if (profile == null) 0.0 else profile.scoreNames(mm.artists.map { it.name }, mm.title)
                            }.take(2)
                            top.randomOrNull(randomSeedSource)?.ytId()
                        }
                }.getOrDefault(emptyList()) else emptyList()
            // Seeds (up to 4 distinct ids) that capture the RANGE: the current/last song first ("more like
            // what just played"), then one representative per context GENRE CLUSTER (largest share first),
            // then one per DISTINCT ARTIST (recent-first, taste-ranked), then more distinct recent TRACKS
            // (so a SINGLE-ARTIST ALBUM still multi-seeds across its range).
            // OWNER DIRECTIVE (2026-09-04): "si vuelvo a poner la misma lista y termina la cola
            // infinita, DEBE SER DIFERENTE a la que ya generó — si no se pierde la experiencia".
            // The old selection was fully DETERMINISTIC (maxByOrNull taste → the same 4 seeds →
            // the same YouTube RDAMVM mixes → the same queue every session). Entropy is added
            // WITHOUT touching the dominant lane: within each cluster the representative comes
            // from the TOP-2 taste (random), and the cluster ORDER is shuffled — same fetches
            // (≤4), same genre coverage, a different window into YouTube's mix every time.
            val perArtistIds = ranked.mapNotNull { it.ytId() }
            val poolIds = contextPool.mapNotNull { it.ytId() }
            val clusterRepsShuffled = clusterReps.shuffled(randomSeedSource)
            // PATTERN SEEDS (owner 2026-10-04: "no quiero que la cola continúe con la última canción que
            // escuchó de la playlist; quiero que antes de empezar estudie la playlist para saber con qué
            // seguir"). When the profile knows the collection's genres, the seeds ARE its genre pattern:
            // ContextPattern.seedLanes hands out the 5 seed slots in proportion to the genre shares
            // (a one-genre list seeds only that genre; 50/30/20 seeds 3/1/1 — dominant first), each slot
            // takes a track of that genre (taste top-3, random; a different artist per slot when
            // possible), and the LAST song no longer leads — it is just one more track of the list. The
            // tracks used are remembered for this collection so each re-seed opens a different window
            // into it instead of re-fetching the same YouTube mixes (whose songs no-repeat already
            // burned). Empty when the profile is inactive or knows no genres → the selection below is
            // byte-identical to before (fail-neutral rule).
            if (contextSeedHistoryPool !== radioSeedPool) {
                contextSeedHistory.clear()
                contextSeedHistoryPool = radioSeedPool
            }
            // Owner log 2026-10-05 — the lane each pattern seed was chosen FOR, so the candidates its radio
            // page brings can inherit it when iTunes knows nothing about them (ContextPattern.inheritedLanes).
            val seedLaneOf = HashMap<String, String>()
            val patternSeeds: List<String> = if (clusters.isNotEmpty()) runCatching {
                val shares = contextProfile?.genreShare.orEmpty()
                val chosen = ArrayList<String>()
                val chosenArtists = HashSet<String>()
                for (lane in iad1tya.echo.music.reco.ContextPattern.seedLanes(shares, 5)) {
                    val laneTracks = clusters[lane] ?: continue
                    val byTaste = laneTracks
                        .filter { mm -> mm.ytId()?.let { it !in chosen } == true }
                        .sortedByDescending { mm ->
                            if (profile == null) 0.0 else profile.scoreNames(mm.artists.map { it.name }, mm.title)
                        }
                    val unused = byTaste.filter { it.id !in contextSeedHistory }.ifEmpty { byTaste }
                    val newArtist = unused.filter { mm ->
                        val artist = mm.artists.firstOrNull()?.name?.trim()?.lowercase()
                        artist == null || artist !in chosenArtists
                    }.ifEmpty { unused }
                    val pick = newArtist.take(3).randomOrNull(randomSeedSource) ?: continue
                    val id = pick.ytId() ?: continue
                    chosen.add(id)
                    seedLaneOf[id] = lane
                    pick.artists.firstOrNull()?.name?.trim()?.lowercase()?.let { chosenArtists.add(it) }
                }
                contextSeedHistory.addAll(chosen)
                chosen
            }.getOrDefault(emptyList()) else emptyList()
            val patternSeeded = patternSeeds.isNotEmpty()
            // The PLAYING song leads the seed set only while it belongs to the collection — once the radio is
            // playing its own picks, seeding from one of them is exactly the compounding drift this avoids.
            // Up to 5 seeds (was 4) so a varied collection is represented across more of its range.
            val seeds = if (patternSeeded) {
                // Exactness over breadth: only when the pattern found a single seed is the set topped up
                // from the rest of the collection (still its own tracks), so the merge has two sources.
                if (patternSeeds.size >= 2) patternSeeds
                else (patternSeeds + perArtistIds + poolIds).distinct().take(2)
            } else {
                // Row 344: rotate through the collection like the pattern seeds do, so every round of the
                // same album/playlist opens new mixes of ITS songs instead of re-fetching the same ones.
                iad1tya.echo.music.reco.CollectionContinuation.rotateSeeds(
                    candidates = listOfNotNull(seedVideoId?.takeIf { it in contextIds }) +
                        clusterRepsShuffled + perArtistIds + poolIds,
                    used = contextSeedHistory,
                    max = 5,
                ).also { contextSeedHistory.addAll(it) }
            }
            if (seeds.size < 2) return@runCatching false // truly one usable track → let tryRadio do last-song
            // Fetch each seed's radio page, off the player thread. With an ACTIVE profile the per-seed
            // cap grows 12 → 16 (headroom so the context steering in orderedByTaste has material to
            // demote into; still <= 4 fetches); inactive keeps today's 12 exactly (fail-neutral rule).
            val perSeedCap = if (steerActive) 16 else 12
            val perSeed = withContext(Dispatchers.IO) {
                seeds.map { sv ->
                    runCatching {
                        YouTubeQueue(endpoint = WatchEndpoint(videoId = sv)).getInitialStatus()
                            .items.filter { it.mediaId != sv && it.mediaId != currentMediaId }.take(perSeedCap)
                    }.getOrDefault(emptyList())
                }
            }
            // Round-robin MERGE so no single seed dominates; dedupe by id. Visit EVERY position up to the
            // longest page (never break early on a no-add pass) so a unique item after an intra-page duplicate
            // is not stranded. mediaId is non-null (media3 @NonNull).
            val merged = ArrayList<MediaItem>()
            val seen = HashSet<String>()
            val maxSize = perSeed.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxSize) {
                for (lst in perSeed) {
                    if (i < lst.size && seen.add(lst[i].mediaId)) merged.add(lst[i])
                }
            }
            val items = merged
                .filterExplicit(dataStore.get(HideExplicitKey, false))
                .filterVideoSongs(dataStore.get(HideVideoSongsKey, false) || dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false))
                .filterNonMusicForAutoQueue()
            // Same round-robin order as `merged`, so a candidate two seeds share inherits from the one that
            // put it in the batch. Empty unless the seeds came from the pattern (fail-neutral).
            val inheritedLanes = if (patternSeeded) {
                iad1tya.echo.music.reco.ContextPattern.inheritedLanes(
                    seedLanes = seeds.map { seedLaneOf[it] },
                    pages = perSeed.map { page -> page.map { it.mediaId } },
                )
            } else {
                emptyMap()
            }
            val ok = appendSeed(items, inheritedLanes) // appendSeed already runs orderedByTaste + records no-repeat + crossfade
            if (ok && patternSeeded) {
                // Owner 2026-10-04 — the WHOLE continuation follows the collection's pattern, not just its
                // first batch. Priming a single-song radio here (below) made every later batch paginate
                // that ONE song's radio, filtered by the lane of whatever happened to be playing: a mixed
                // playlist collapsed onto one genre after the first batch. A FINITE queue instead (the
                // same mechanism the mood seed uses) lets the end-of-batch net — B3 at the last item,
                // scheduleCrossfade's early seed, and the always-on STATE_ENDED net — call this function
                // again, which re-reads the pattern and opens new seeds. One fewer request per batch,
                // too: the prime fetch is skipped.
                currentQueue = EmptyQueue
                Timber.tag(TAG).i(
                    "CTX_PATTERN seeds=%d lanes=%d (pattern-seeded continuation)",
                    seeds.size,
                    iad1tya.echo.music.reco.ContextPattern.patternShares(contextProfile?.genreShare.orEmpty()).size,
                )
            } else if (ok) {
                // Row 344 (owner 2026-10-07: "cuando terminó el álbum la cola no continuó como debería"):
                // this used to prime a radio of ONE seed and let the pagination run it forever, filtered by
                // the lane of whatever was playing — unfiltered once that song's genre was unknown, which is
                // every song of a niche album. Same finite queue as the pattern branch: each round re-seeds
                // from the collection's own tracks (rotated). One request fewer per round, too.
                currentQueue = EmptyQueue
                Timber.tag(TAG).i("CTX_COLLECTION seeds=%d (collection-seeded continuation)", seeds.size)
            }
            ok
        }.getOrElse { radioSourceFailed("context", it) }

        // Mood (if active) first, then radio, then related. If a transient hiccup left us empty, wait
        // briefly and try again — so a momentary network blip / rate-limit window at the exact
        // end-of-queue moment never permanently stops the music. THREE attempts, not two: a single
        // 2.5s retry was observed (owner diagnostic) to still land inside a longer-than-transient
        // failure window, so the exported-playlist context fell all the way to the replay
        // last-resort instead of ever reaching real radio — even though the seed video id was
        // valid and playable moments earlier. The extra 5s attempt costs nothing on the common case
        // (mood/radio/related already succeeded and this block never runs).
        var appended = tryMood() || tryContextRadio() || tryRadio() || tryRelated()
        if (!appended) {
            kotlinx.coroutines.delay(2500)
            appended = tryMood() || tryContextRadio() || tryRadio() || tryRelated()
        }
        if (!appended) {
            kotlinx.coroutines.delay(5000)
            appended = tryMood() || tryContextRadio() || tryRadio() || tryRelated()
        }
        // Autoplay chips: the seed just landed (appendSeed ran) → refresh the queue-footer
        // suggestions for THIS seed. Bounded: no-ops if this seed's chips are already loaded.
        if (appended) seedVideoId?.let { refreshAutoplaySuggestions(it) }
        // Absolute last resort: at a TRUE end-of-queue, never leave the user in silence — replay the queue.
        if (!appended && (resumeAfterSeed || advanceIntoRadioRequested) &&
            !player.isPlaying && player.mediaItemCount > 0
        ) {
            // Named WHY, not just THAT: "yielded nothing" alone was indistinguishable between "no
            // YouTube identity for this track" (seedVideoId null — expected for some local/http
            // items) and "had a valid seed but every source rejected/errored 3 times" (a real,
            // investigable failure — this is what silently produced the exported-playlist
            // "infinite loop" complaint, since replaying a short curated list reads as a loop).
            Timber.tag(TAG).w(
                "Radio seed yielded nothing after 3 attempts (hasSeed=${seedVideoId != null}, " +
                    "poolSize=${radioSeedPool.size}, mood=${activeMoodParams != null}); " +
                    "replaying current queue so playback never stops"
            )
            resumeAfterSeed = false
            advanceIntoRadioRequested = false
            player.seekTo(0, 0)
            player.play()
        }
    }
    // Release the claim from a completion handler rather than a `finally`. invokeOnCompletion fires even
    // when the coroutine body NEVER RAN (a launch on an already-cancelled scope completes immediately), so
    // the flag can no longer stick true and silently kill every re-seed path for the rest of the process.
    seedJob.invokeOnCompletion {
        // Ronda 10 — see [radioSeedInFlightGeneration]'s own doc comment: only clear the shared
        // flags if NOBODY newer has claimed them since. A later, still-running seed job (a NEWER
        // queue's own legitimate attempt) already overwrote radioSeedInFlightGeneration with its own
        // generation — this stale job's completion must not clear its state out from under it.
        if (radioSeedInFlightGeneration == seedGeneration) {
            radioSeedInFlight = false
            resumeAfterSeed = false
            advanceIntoRadioRequested = false
        }
    }
}
