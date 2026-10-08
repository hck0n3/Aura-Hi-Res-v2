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
import iad1tya.echo.music.playback.MusicService.Companion.CROSSFADE_PRELOAD_LEAD_MS
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
 * Plan C1, phase 2 (row 352): the CROSSFADE engine (scheduling, tail-silence detection, the secondary
 * player, the swap and its gain curves, cleanup) moved out of MusicService.kt into its own file. Every
 * function is byte-for-byte the member it was, now an `internal` extension of MusicService; the members
 * they read went from private to internal. Behaviour unchanged — the registry's crossfade rows (e.g. #308) and the
 * crossfade invariant (duration, curve, math of the fade) still live in this exact code.
 */
/**
 * AIMP-style smooth entry: wait until audio is actually rendering, then a short ~400ms sine ramp
 * so the skip is not a slam. Owner: songs were taking too long to start — the old 1.6s quieterstep
 * swell (first quarter almost silent) felt like the track had not begun. Volume-only.
 *
 * 🔴 2026-09-17 — *"a veces las canciones inician cortadas cuando cambio a mano, y en Android
 * Auto también"*. Eran DOS agujeros de esta función, los dos con el volumen como víctima; el
 * razonamiento completo y los umbrales están en [ManualFadeIn]:
 *
 *  - la espera muda miraba `isPlaying`, que es FALSO mientras el foco está suprimido (ducking, un
 *    aviso del coche) aunque ya esté saliendo audio, y aguantaba hasta 8 s con el volumen en 0 —
 *    hasta ocho segundos de canción sonando a cero, y encima al agotarse rampaba desde cero otra
 *    vez. Ahora mira el estado real y la espera muda dura poco más de un segundo; si se agota se
 *    devuelve el volumen ENTERO de golpe, porque perder el fundido es mejor que perder la entrada;
 *  - la rampa cortaba con `if (isCrossfading) break` y el `finally` restauraba solo
 *    `if (!isCrossfading)`: la MISMA condición, así que un crossfade encima dejaba el volumen
 *    clavado en el escalón que tocara. Ahora se restaura siempre (salvo que una entrada más nueva
 *    ya sea la dueña del volumen); el crossfade fija el suyo en su primer tic, así que no le quita
 *    nada.
 */
internal fun MusicService.fadeInOnManualChange() {
    manualFadeInJob?.cancel()
    if (!::playerVolume.isInitialized) return
    if (!ManualFadeIn.worthFading(isMuted.value, playerVolume.value)) return
    val target = playerVolume.value
    lateinit var self: Job
    self = scope.launch {
        try {
            player.volume = 0f
            // WAIT for the audio to actually RENDER before ramping (bounded): a wall-clock ramp from
            // the transition callback finished into SILENCE and the real audio then slammed in.
            var waited = 0L
            while (isActive &&
                ManualFadeIn.shouldKeepWaiting(
                    waitedMs = waited,
                    audible = ManualFadeIn.audible(
                        ready = player.playbackState == Player.STATE_READY,
                        playWhenReady = player.playWhenReady,
                    ),
                )
            ) {
                delay(ManualFadeIn.WAIT_POLL_MS)
                waited += ManualFadeIn.WAIT_POLL_MS
            }
            // Se agotó la espera: la canción ya lleva sonando un rato y rampar desde cero AHORA es
            // exactamente el corte del que se queja. El `finally` devuelve el volumen entero.
            if (!isActive ||
                !ManualFadeIn.audible(
                    ready = player.playbackState == Player.STATE_READY,
                    playWhenReady = player.playWhenReady,
                )
            ) {
                return@launch
            }
            // Audible on the first step (~−12 dB), full level in ~400ms. Equal-power sine, no
            // smootherstep hold-at-silence.
            val stepTime = ManualFadeIn.RAMP_MS / ManualFadeIn.STEPS
            for (i in 1..ManualFadeIn.STEPS) {
                if (!isActive) break
                player.volume = target * ManualFadeIn.stepGain(i)
                delay(stepTime)
            }
        } finally {
            runCatching {
                // Exact restore, mute-aware — never strand the volume below the user's setting.
                // IDENTITY guard: on rapid skips a NEWER fade may already own the volume (it just set
                // 0f); a cancelled older job restoring FULL volume after that would kill the new
                // fade-in. Only the job still registered as current restores.
                if (::playerVolume.isInitialized) {
                    ManualFadeIn.finalVolume(
                        isCurrentJob = manualFadeInJob === self,
                        muted = isMuted.value,
                        userVolume = playerVolume.value,
                    )?.let { player.volume = it }
                }
            }
        }
    }
    manualFadeInJob = self
}

internal fun MusicService.scheduleCrossfade() {
    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null
    crossfadePreloadJob?.cancel()
    crossfadePreloadJob = null
    crossfadeReadyJob?.cancel()
    crossfadeReadyJob = null
    crossfadeTailArmJob?.cancel()
    crossfadeTailArmJob = null
    tailQuietRecheckJob?.cancel()
    tailQuietRecheckJob = null
    // Tail-silence detection is only valid inside the fade window this call is about to (re)compute —
    // disarm on every (re)schedule (track change, seek, queue change) so a stale arm can't fire.
    playerSilenceProcessors[player]?.tailDetectEnabled = false
    // Release any incoming player we preloaded for a transition that's no longer happening (user
    // skipped, seeked, queue changed) so we never leak a second ExoPlayer.
    if (!isCrossfading) {
        // REUSE a still-valid preload (thermal audit): scheduleCrossfade fires from ~6 event sites
        // (playWhenReady flips, rebuffer→READY, in-song seeks...), and unconditionally tearing the
        // buffered secondary down meant building 2-4 full ExoPlayers per song — each with native
        // processor init, an O(N) queue copy and up to 12 s of re-buffering (network + decode heat).
        // Keep it ONLY when:
        //  • the TIMELINE VERSION is unchanged (a counter bumped by every onTimelineChanged): the
        //    secondary holds a queue COPY that becomes the LIVE queue at the swap, so ANY timeline
        //    mutation — append, remove, drag-reorder past the next item, replaceMediaItem with the
        //    same id (Opus refetch, video URI) — makes the copy stale. Same-target+same-count alone
        //    provably missed reorders and replacements (adversarial round);
        //  • the next target still matches (shuffle reorder without timeline change);
        //  • AND every early-return below would NOT fire — a kept player is only legal on the path
        //    that reaches the trigger scheduling, otherwise it sits prepared with NO trigger job
        //    (an orphan holding codecs + 12 s of buffer indefinitely).
        val keepPreload = secondaryPlayer?.let { sec ->
            val targetIdx = CrossfadePlanning.crossfadeTargetIndex(
                player.repeatMode == REPEAT_MODE_ONE,
                player.currentMediaItemIndex,
                player.nextMediaItemIndex
            )
            val liveTarget = if (targetIdx != C.INDEX_UNSET && targetIdx < player.mediaItemCount) {
                runCatching { player.getMediaItemAt(targetIdx).mediaId }.getOrNull()
            } else null
            CrossfadePlanning.shouldKeepPreload(
                liveTargetMediaId = liveTarget,
                timelineVersionUnchanged = secondaryTimelineVersion == timelineVersion,
                secondaryMediaId = runCatching { sec.currentMediaItem?.mediaId }.getOrNull(),
                highPerformanceModeHint = highPerformanceModeHint,
                crossfadeEnabled = crossfadeEnabled,
                videoMode = _videoMode.value,
                durationMs = player.duration,
                crossfadeDurationMs = crossfadeDuration.toLong(),
                gaplessBypass = crossfadeGapless && isNextItemGapless()
            )
        } == true
        if (!keepPreload) {
            secondaryPlayer?.let {
                // Silence too — this is the MOST frequent teardown of the three (it runs whenever a
                // preloaded incoming player is discarded: skip, seek, queue change), so omitting it here
                // leaked a HashMap entry keyed by a released ExoPlayer on nearly every user interaction.
                playerSilenceProcessors.remove(it)
                playerNormProcessors.remove(it)
                playerLimiterProcessors.remove(it)
                playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
                it.stop()
                // NO clearMediaItems: redundant before release() and a mutation-race trigger (see
                // secondaryPlayerListener teardown / CRASH_REPORTS #2).
                it.release()
            }
            // Inside the if — an unconditional null here ORPHANED the kept player (nulled without
            // release, rebuilt from scratch anyway): the exact leak this block exists to prevent.
            secondaryPlayer = null
        }
    }
    // High-Performance Mode: crossfade is force-disabled (crossfadeEnabled already reflects this via the
    // perf-gated flow at collect time). This explicit, cheap @Volatile guard makes the intent robust and
    // self-documenting — transitions fall back to normal gapless/simple playback (a single decoder, no
    // second ExoPlayer) on weak/TV/car devices. No-op on capable devices: perf mode off → hint false →
    // falls through to the unchanged 9s equal-power crossfade path below. The cleanup above still ran, so
    // any incoming player preloaded before perf mode toggled on is released rather than leaked.
    if (highPerformanceModeHint) return
    if (!crossfadeEnabled || player.duration == C.TIME_UNSET) return
    if (player.duration <= crossfadeDuration) {
        traceCrossfade("skip-short", "dur=${player.duration}ms <= fade window — no blend possible")
        return
    }
    // Crossfade builds a SECOND ExoPlayer and copies the queue into it; the video item (a cache-less
    // muxed source with no TextureView attached on the secondary player) would break. Skip crossfade
    // entirely while video mode is on.
    if (_videoMode.value) return
    if (crossfadeGapless && isNextItemGapless()) {
        traceCrossfade("gapless-bypass", "same-album pair -> deliberate gapless advance (Ajustes)")
        return
    }
    if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) {
        // Last item with NO next: if auto-radio (infinite queue) is on, seed it NOW — early, while this song
        // still has time left — so a real crossfade INTO the first radio song is possible. A bare return here
        // is why the infinite queue used to continue with a hard cut. appendSeed() re-arms scheduleCrossfade()
        // once the items land, so the fade then targets the freshly-appended next song.
        if (!radioSeedInFlight && autoLoadMoreHint &&
            player.currentMediaItem?.mediaId != null
        ) {
            startRadioSeamlessly()
        }
        return
    }

    val targetMediaId = player.currentMediaItem?.mediaId

    // PER-SONG TAIL MEMORY: if a previous play measured this song's trailing silence, anchor the
    // trigger at (musical end - fade window) instead of (file end - fade window) — the decay covers
    // the last seconds of MUSIC and completes right as the music ends; the silent tail never plays.
    // Clamped so a bad hint can never pull the trigger absurdly early; no hint → live tail tiers
    // below remain the first-play path.
    val tailHint = CrossfadePlanning.effectiveTailHint(
        rawHintMs = targetMediaId?.let { tailSilenceHintMs[it] } ?: 0L,
        durationMs = player.duration,
        crossfadeDurationMs = crossfadeDuration.toLong()
    )
    val triggerTime = CrossfadePlanning.triggerTimeMs(
        player.duration, tailHint, crossfadeDuration.toLong()
    )
    // Already INSIDE the fade window (near-end seek; radio items landing during the last seconds and
    // re-arming this schedule): fire the fade NOW instead of bailing. The old `return` here is why a
    // late re-arm could still end in a hard cut — every guard re-runs inside startCrossfade anyway.
    val delayMs = CrossfadePlanning.triggerDelayMs(triggerTime, player.currentPosition)
    if (tailHint > 0) {
        traceCrossfade("hint-anchor", "learnedTail=${tailHint}ms — fade covers the last music, not the silence")
    }

    // Preload (build + buffer) the incoming player a few seconds BEFORE the fade so it's already
    // playing the instant the fade starts. This removes the occasional cut/gap on slow networks,
    // where the incoming player used to begin buffering only when the fade had already started.
    val preloadDelay = CrossfadePlanning.preloadDelayMs(delayMs, CROSSFADE_PRELOAD_LEAD_MS)
    crossfadePreloadJob = scope.launch {
        delay(preloadDelay)
        if (isActive && !isCrossfading && player.isPlaying &&
            player.currentMediaItem?.mediaId == targetMediaId
        ) {
            val targetIndex = CrossfadePlanning.crossfadeTargetIndex(
                player.repeatMode == REPEAT_MODE_ONE,
                player.currentMediaItemIndex,
                player.nextMediaItemIndex
            )
            prepareSecondaryPlayer(targetIndex)
        }
    }

    // TAIL DETECTION — arm the detector for the final stretch of THIS track (its own job so the
    // window can be WIDER than the preload lead). Two tiers fire the fade at the end of the MUSIC
    // instead of the FILE:
    //  • true silence (≥3.5s under ~-42 dBFS): the audible content is over — long silent tails no
    //    longer produce a dead gap before the next song;
    //  • "musical end" (≥2.5s under ~-25 dBFS): the song entered its mastered fade-out / quiet ending
    //    — the crossfade starts THERE, over a still-audible ending, so the blend (old going down +
    //    new rising on top) is actually HEARD. Position-guarded in the handler.
    // Window: the WHOLE track (owner order — no silent gap ever; mid-song safety lives in the
    // handler's position-tiered thresholds). Measure-only. HONEST
    // SCOPE: media3 only feeds custom processors on the 16-bit INT pipeline (Opus/AAC/16-bit FLAC —
    // the vast majority of content); the hi-res FLOAT pipeline (24-bit on capable devices) bypasses
    // the whole custom chain — covered since 0.6.131 by the sink-level tap (ForwardingAudioSink →
    // measureExternal), so FLOAT content is measured too.
    // WHOLE-TRACK arming (owner order: the transition must fire NO MATTER how many seconds of silence
    // the song carries — never a dead gap). The position-tiered handler keeps mid-song safety: far from
    // the end TRUE silence needs ≥7s continuous (a skit/grand-pause can't fire), near the end 3.5s, and
    // the -25dB "musical end" tier only acts inside (fade+4s). Same-track re-arms preserve counters
    // (identity check + the processor no longer wipes state on a brief disarm); a new track resets.
    // Cost: per-frame abs+compare on the already-hot audio thread — trivial vs decode/EQ.
    playerSilenceProcessors[player]?.let {
        val armId = player.currentMediaItem?.mediaId
        if (tailArmedMediaId != armId) {
            it.resetTracking()
            tailArmedMediaId = armId
            leadHintTrustedForArmedTrack = player.currentPosition <= 2_000L
        } else if (player.currentPosition > 2_000L && it.leadingSilenceUsOrNegative() < 0L) {
            // Same-track re-arm past the intro with nothing finalized yet: a seek moved counting away
            // from the beginning — the eventual finalize would be interior audio, not the intro.
            leadHintTrustedForArmedTrack = false
        }
        it.tailDetectEnabled = true
    }

    crossfadeTriggerJob = scope.launch {
        delay(delayMs)
        if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId) {
            startCrossfade()
        }
    }
}

internal fun MusicService.isNextItemGapless(): Boolean {
    val current = player.currentMediaItem?.mediaMetadata ?: return false
    val nextIndex = player.nextMediaItemIndex
    if (nextIndex == C.INDEX_UNSET) return false
    val next = player.getMediaItemAt(nextIndex).mediaMetadata
    return current.albumTitle != null && current.albumTitle == next.albumTitle
}

/** Per-song tail memory writer. The measurement semantics are pure ([CrossfadePlanning.classifyTailHint]:
 *  sub-2s "tails" CLEAR any stale learned value, implausible values (> half the song) are ignored
 *  outright); only the map itself (size cap, put/remove) lives here. */
internal fun MusicService.storeTailHint(mediaId: String?, hintMs: Long, durationMs: Long) {
    if (mediaId == null) return
    when (CrossfadePlanning.classifyTailHint(hintMs, durationMs)) {
        CrossfadePlanning.TailHintAction.Ignore -> return
        CrossfadePlanning.TailHintAction.ClearEntry -> tailSilenceHintMs.remove(mediaId)
        CrossfadePlanning.TailHintAction.Store -> {
            if (tailSilenceHintMs.size > 400) tailSilenceHintMs.clear()
            tailSilenceHintMs[mediaId] = hintMs
        }
    }
}

/**
 * One shareable-log line per DISTINCT crossfade event (deduped per track+event so the many
 * reschedules can't spam). Turns every "esta transición falló" report into an attributable verdict
 * in Ajustes ▸ Registros: fade fired (which tier), swap committed, or WHY it cut instead.
 */
internal fun MusicService.traceCrossfade(event: String, detail: String) {
    val id = player.currentMediaItem?.mediaId ?: "?"
    val key = "$id:$event"
    if (key == lastCrossfadeTraceKey) return
    lastCrossfadeTraceKey = key
    runCatching {
        Timber.tag(TAG).i("CROSSFADE_TRACE id=%s ev=%s %s", id, event, detail)
        iad1tya.echo.music.utils.PlaybackLogManager.log(
            iad1tya.echo.music.utils.PlaybackLogLevel.INFO,
            "CROSSFADE_TRACE",
            "id=$id ev=$event $detail"
        )
    }
}

/**
 * TAIL-DETECTION handler (Main thread). The CURRENT player's detector — armed for the final stretch
 * (≤30s) by [scheduleCrossfade]'s tail-arm job — reported one of its two tiers:
 *  • TRUE SILENCE (≥3.5s under ~-42 dBFS; ≥7s when still far from the end — mid-song skit/pause
 *    safety): the audible content ended, fire the fade now, every extra second is dead air;
 *  • "MUSICAL END" (≥2.5s under ~-25 dBFS): the mastered fade-out — fire only within (fade+4s) of
 *    the real end so the blend covers a STILL-AUDIBLE ending (the audible-crossfade segue); earlier
 *    fires defer to a live re-check at the moment the fade would be due.
 * Every normal crossfade guard re-runs inside [startCrossfade]; disarms itself so one detection fires
 * at most one fade; stale fires after a swap/seek no-op via disarm-on-reschedule + live-state checks.
 */
internal fun MusicService.onTailSilenceDetected() {
    val proc = playerSilenceProcessors[player] ?: return
    if (!proc.tailDetectEnabled) return
    val silentNow = proc.isCurrentlySilent()
    val quietNow = proc.isCurrentlyQuiet()
    // Main-hop recheck: if audible content resumed between the audio-thread fire and this hop, that
    // was a mid-tail pause, not the end of the music — keep the detector armed and bail (a LATER
    // episode in the window can still fire).
    if (!silentNow && !quietNow) return
    // INTRO SILENCE ≠ TAIL. Whole-track arming means opening dead air also accumulates. After ≥7s the
    // far-from-end path used to fire a "tail" crossfade and SKIP the song (owner log 0.6.163:
    // OjMLBY2cVPk / "A Man You Would Write About" — tier=silence remaining≈291s ~7s after start).
    // leadingSilenceUs is finalized only at the FIRST loud frame: while it is still negative we have
    // never heard music on this arm, so this silence cannot be a trailing tail. Stay armed; do NOT
    // schedule the 7s recheck (that recheck was the skip). After music starts, notifiedThisSilence
    // resets and a real end-of-song silence can fire normally.
    if (silentNow && proc.leadingSilenceUsOrNegative() < 0L) {
        return
    }
    // TIER GATE (pure decision in CrossfadePlanning.tailTierDecision, characterization-locked):
    // far from the end, TRUE silence must persist LONGER (7s vs 3.5s) before firing — a
    // skit/grand-pause can't skip real music — and rechecks until the persistence is met; near
    // the end it fires anywhere (nothing audible remains). The −25dB "musical end" tier only
    // acts inside (fade+4s) of the real end and defers to a live recheck until then. HOLD bails
    // armed without a recheck loop while paused (frozen position/counters would re-arm forever);
    // the file-end trigger remains the fallback after resume.
    when (
        val tier = CrossfadePlanning.tailTierDecision(
            silentNow = silentNow,
            isPlaying = player.isPlaying,
            durationMs = player.duration,
            currentPositionMs = player.currentPosition,
            silenceDurationMs = proc.silenceDurationUs() / 1_000L,
            crossfadeDurationMs = crossfadeDuration.toLong()
        )
    ) {
        CrossfadePlanning.TailDecision.Hold -> return
        is CrossfadePlanning.TailDecision.Recheck -> {
            tailQuietRecheckJob?.cancel()
            tailQuietRecheckJob = scope.launch {
                delay(tier.delayMs)
                onTailSilenceDetected() // re-evaluates LIVE state; bails if the music resumed
            }
            return
        }
        CrossfadePlanning.TailDecision.Fire -> {}
    }
    proc.tailDetectEnabled = false
    if (isCrossfading || !crossfadeEnabled || highPerformanceModeHint || _videoMode.value) return
    if (!player.isPlaying) return
    if (crossfadeGapless && isNextItemGapless()) {
        traceCrossfade("gapless-bypass", "same-album pair -> deliberate gapless advance (Ajustes)")
        return
    }
    if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) return
    // LEARN this song's tail for the per-song memory (silent tier ONLY: its run start is the true end
    // of audible content; the quiet tier's run start is the START of a still-audible mastered fade-out
    // — anchoring 5s before THAT would cut real music on later plays). trailing ≈ remaining + run; the
    // sink buffer skews it ≤~0.5s toward "earlier", which can only trim threshold-level noise. The EOS
    // snapshot in cleanupCrossfade refines this with the exact value when the decoder reached EOS.
    if (silentNow) {
        val dur = player.duration
        if (dur != C.TIME_UNSET) {
            storeTailHint(
                player.currentMediaItem?.mediaId,
                (dur - player.currentPosition) + proc.silenceDurationUs() / 1_000L,
                dur
            )
        }
    }
    // Deliberately do NOT cancel crossfadeTriggerJob: if this early fade can't actually start (the
    // secondary misses its READY window, or the user pauses during the bounded wait) the file-end-
    // anchored trigger must survive as the fallback — cancelling it here left the track with NO fade at
    // all. If the early fade DOES start, beginCrossfadeSwap cancels the stale jobs at the commit point.
    traceCrossfade(
        "tail-fire",
        "tier=${if (silentNow) "silence" else "quiet"} remaining=${
            player.duration.takeIf { it != C.TIME_UNSET }?.minus(player.currentPosition) ?: -1
        }ms"

internal fun MusicService.startCrossfade() {
    if (isCrossfading) return
    // Tail detection's job is done the moment any fade actually starts (either path) — disarm.
    playerSilenceProcessors[player]?.tailDetectEnabled = false

    
    
    // Live values — NOT runBlocking dataStore reads: two blocking disk reads here, right at the
    // crossfade trigger, stuttered the smooth transition. player.repeatMode/shuffleModeEnabled mirror
    // the persisted settings already.
    val savedRepeatMode = player.repeatMode
    val savedShuffleEnabled = player.shuffleModeEnabled

    
    val targetIndex = CrossfadePlanning.crossfadeTargetIndex(
        savedRepeatMode == REPEAT_MODE_ONE,
        player.currentMediaItemIndex,
        player.nextMediaItemIndex
    )
    if (targetIndex == C.INDEX_UNSET) return

    // Reuse the player we preloaded (already buffering ahead) if present; otherwise build it now.
    if (secondaryPlayer == null) {
        prepareSecondaryPlayer(targetIndex)
    }
    val secPlayer = secondaryPlayer ?: return

    // START-CLIP FIX: never fade in a half-buffered incoming player. On the normal preloaded path the
    // secondary is already STATE_READY (buffered at position 0) with the full ~12 s lead, so we swap
    // immediately — byte-identical to before. On the LATE-ARMED path (radio just appended the next item,
    // a near-end seek, or the streamed duration arrived late) the secondary was built with ~0 ms buffered;
    // flipping playWhenReady and swapping NOW left the incoming still resolving its URL / buffering while
    // the OUTGOING player — capped to its current item — hit STATE_ENDED, so the join went silent and the
    // new song's first moment was clipped. Instead, wait (bounded) for STATE_READY, THEN swap + fade from a
    // clean position 0. If it can't ready within the bound, fall through to media3's single-player
    // auto-advance (a clean hard cut) rather than a clipped pop-in. Curve/duration untouched.
    if (secPlayer.playbackState == Player.STATE_READY) {
        beginCrossfadeSwap(secPlayer, savedShuffleEnabled)
        return
    }
    val targetMediaId = player.currentMediaItem?.mediaId
    crossfadeReadyJob?.cancel()
    crossfadeReadyJob = scope.launch {
        // DYNAMIC bound (owner: "algunas transiciones las corta"): the old fixed 2.5s gave up long
        // before slow-resolving incoming tracks were ready (a Lossless resolve alone can take longer)
        // and fell to a HARD CUT. Wait as long as the OUTGOING still has audible time left (~800ms
        // floor to land the swap) — a late, shorter blend always beats a cut.
        var waited = 0L
        while (isActive && secPlayer.playbackState != Player.STATE_READY) {
            val dur = player.duration
            val remaining = if (dur == C.TIME_UNSET) 0L else dur - player.currentPosition
            if (!CrossfadePlanning.readyWaitShouldContinue(
                    outgoingRemainingMs = remaining,
                    isPlaying = player.isPlaying,
                    sameTrackStillCurrent = player.currentMediaItem?.mediaId == targetMediaId
                )
            ) break
            delay(50)
            waited += 50
        }
        // Abort if the world moved on while we waited (user skipped/paused, a new crossfade armed, the
        // current track changed, or this secondary was already released) — never swap a stale transition.
        if (!isActive || isCrossfading || secondaryPlayer !== secPlayer ||
            !player.isPlaying || player.currentMediaItem?.mediaId != targetMediaId
        ) return@launch
        if (secPlayer.playbackState == Player.STATE_READY) {
            beginCrossfadeSwap(secPlayer, savedShuffleEnabled)
        } else {
            // Single-player path will hard-cut — make the failure VISIBLE in the shareable log.
            traceCrossfade("cut-not-ready", "waited=${waited}ms incoming never READY (slow resolve/buffer)")
        }
    }
}

/** Flip the (already-READY) incoming player on and run the swap + fade. Extracted so both the fast path
 *  and the bounded ready-wait in [startCrossfade] share one swap site. */
internal fun MusicService.beginCrossfadeSwap(secPlayer: ExoPlayer, savedShuffleEnabled: Boolean) {
    if (isCrossfading) return
    secPlayer.playWhenReady = true

    performCrossfadeSwap()

    // The fade COMMITTED — kill the file-end-anchored jobs NOW. They deliberately survive the
    // can't-start paths (secondary missed READY, pause during the bounded wait) as the fallback, but
    // once the swap really happened they are stale — and under REPEAT_ONE the swapped-in player plays
    // the SAME mediaId, so the old trigger's mediaId guard would pass after a short (≤8s) fade ended
    // and audibly RESTART the song mid-play. Cancelling at the commit point closes that hole while
    // keeping the fallback intact.
    crossfadeTriggerJob?.cancel()
    crossfadeTriggerJob = null
    crossfadePreloadJob?.cancel()
    crossfadePreloadJob = null
    crossfadeTailArmJob?.cancel()
    crossfadeTailArmJob = null
    tailQuietRecheckJob?.cancel()
    tailQuietRecheckJob = null

    traceCrossfade("swap-ok", "blend running (curve+duration per Ajustes)")

    // A crossfade swap IS a natural auto-advance — but it reaches the next track through a path that
    // never fires onMediaItemTransition. Everything that normally happens there must be mirrored here or
    // it silently stops working in the app's DEFAULT configuration (crossfade ON): scrobbling, Cast
    // follow-along, SponsorBlock segments, upcoming-track prefetch, and pulling the next page of a long
    // playlist/album (whose absence made the queue fall into the infinite radio instead of continuing).
    applyAutoAdvanceSideEffects()
    // Same ordering rule as the transition path: trace BEFORE either recording block below, or the
    // line always reads "repeat=YES" and tells us nothing.
    traceNoRepeat("crossfade-swap")
    // REPEAT_ONE swaps the SAME track in over and over; treating those as fresh advances paginated the
    // queue on every loop (a page fetched + appended per repeat). The transition path suppresses
    // pagination on repeats for exactly this reason — mirror it.
    maybeLoadMoreQueuePages(isRepeatTransition = player.repeatMode == REPEAT_MODE_ONE)

    // Linear-play recording under crossfade: the swap path skips onMediaItemTransition, so without
    // this a LINEAR listen (shuffle off, crossfade on — every auto-advance is a swap) left no trace in
    // the persistent context memory, and activating shuffle later replayed songs heard minutes before.
    // Mirrors the ungated insert in onMediaItemTransition; the shuffle branch below records its own.
    if (!savedShuffleEnabled) {
        val linearId = player.currentMediaItem?.mediaId ?: player.currentMetadata?.id
        val linearCtx = shuffleContextId
        if (enhancedShuffleHint && linearCtx != null && linearId != null) {
            val now = System.currentTimeMillis()
            scope.launch(enhancedShuffleWriteDispatcher) {
                recordPlayedSafely("ENHANCED_SHUFFLE") { database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(linearCtx, linearId, now)) }
            }
        }
    }

    if (savedShuffleEnabled) {
        // Enhanced Shuffle: the crossfade swap advances the queue via a path that SKIPS
        // onMediaItemTransition — where B5 + the persistent no-repeat bookkeeping normally record the
        // just-started song as played. With crossfade ON (every auto-advance is a swap) that recording
        // NEVER ran, so shufflePlayedIds stayed near-empty and applyShuffleOrder below kept re-shuffling a
        // pool where nothing was marked played → already-heard songs resurfaced as the "next" song
        // (reported: the shuffle jumps to a song that isn't the right continuation). Record the song the
        // swap just made current here, mirroring onMediaItemTransition's B5 block, BEFORE re-applying the
        // order so played songs correctly sink and the cycle-exhaustion self-reset counts them.
        val playedId = player.currentMediaItem?.mediaId ?: player.currentMetadata?.id
        playedId?.let { shufflePlayedIds.add(it) }
        // ARTIST SPACING mirror: this path skips onMediaItemTransition, so without this line the
        // artist history would only ever fill with crossfade OFF — i.e. never, for this owner.
        rememberShuffleArtist(player.currentMediaItem)
        val ctx = shuffleContextId
        if (enhancedShuffleHint && ctx != null && playedId != null) {
            val now = System.currentTimeMillis()
            scope.launch(enhancedShuffleWriteDispatcher) {
                recordPlayedSafely("ENHANCED_SHUFFLE") { database.insertEnhancedPlayed(EnhancedShufflePlayedEntity(ctx, playedId, now)) }
            }
        }

        // Enhanced Shuffle — cycle exhaustion. The add above may have just COMPLETED the context (this
        // swap made the last unplayed song current). This is the ONLY place that's knowable in time
        // under crossfade: the swap path skips onMediaItemTransition (where the early-handoff lives),
        // and a plain applyShuffleOrder here would run its all-played self-reset SYNCHRONOUSLY — wiping
        // the memory before any later check could observe the exhaustion (verified: that made a
        // scheduleCrossfade-time check dead code). A swap is by definition a NATURAL auto-advance, so
        // the early-handoff's AUTO-only semantics hold. On exhaustion: MARK the lap complete (the
        // memory is kept — it resets only when the user re-activates shuffle on this list), detach the
        // context, seed the infinite radio while this last song still plays, and SKIP this swap's
        // re-shuffle — the self-reset would un-sink the played tail; appendSeed() re-applies the order
        // once the radio items land (unplayed radio sorts ahead; the tail stays sunk).
        val exhaustCtx = shuffleContextId
        // player.shuffleModeEnabled: LIVE check on top of the captured savedShuffleEnabled — the swap
        // can run up to 2.5s after capture (READY-wait), and if the user turned shuffle OFF in that
        // window this destructive branch (memory wipe + radio) must not fire on an un-shuffled queue.
        if (enhancedShuffleHint && exhaustCtx != null && player.shuffleModeEnabled &&
            player.repeatMode == REPEAT_MODE_OFF && autoLoadMoreHint &&
            !radioSeedInFlight && isEnhancedContextExhausted()
        ) {
            markEnhancedContextCycleComplete(exhaustCtx)
            shuffleContextId = null
            startRadioSeamlessly()
            // KNOWN bounded edge: until appendSeed re-applies the order, the swapped-in player keeps
            // media3's own random shuffle order — if this LAST song ends before the seed lands (very
            // short song + slow network) one already-played song may briefly replay, then the radio
            // takes over (its items sort ahead; the tail stays sunk via the !radioSeedInFlight reset
            // gate). Accepted: bounded, self-healing, and never silence.
        } else {
            // LAP-COMPLETION probe: with the stale-skip below, the all-played self-reset and the
            // cycle-complete mark inside applyShuffleOrder would be unreachable in configs where the
            // exhaustion handoff above does not fire (repeat-all, continuation off) — shuffle would
            // simply never re-shuffle again. O(1) precheck first so the common mid-lap swap stays
            // scan-free; the O(N) confirm runs at most once per completed lap. REPEAT_ONE loops the
            // same song and needs no re-shuffle; radioSeedInFlight defers to the seed's own re-apply.
            if (!shuffleOrderStale &&
                player.repeatMode != REPEAT_MODE_ONE &&
                !radioSeedInFlight &&
                shufflePlayedIds.size >= player.mediaItemCount &&
                player.mediaItemCount > 0
            ) {
                val allPlayed = (0 until player.mediaItemCount).all { i ->
                    runCatching { player.getMediaItemAt(i).mediaId }.getOrNull()?.let { it in shufflePlayedIds } != false
                }
                if (allPlayed) shuffleOrderStale = true
            }
            if (shuffleOrderStale) {
                // Re-apply ONLY when something order-relevant actually changed (an append, a toggle,
                // the DB seed landing, a manual SEEK, a completed lap). Re-running the full O(N)
                // scoring + sort + spacing on the MAIN thread at EVERY song boundary — during the 5 s
                // dual-player fade, with media3 then re-broadcasting the whole shuffle order to
                // Android Auto over Binder — was the per-boundary burst car users heard as
                // micro-stutters. Deferring the just-played sink is safe within a lap because the
                // incoming player CARRIES the in-force curated order (see prepareSecondaryPlayer):
                // every unplayed item stays ahead of the play head, so no-repeat holds going forward
                // and the sink lands on the next real mutation.
                shuffleOrderStale = false
                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }
        }
    }
}

/** Build the incoming player (full queue, seeked to [targetIndex], muted, buffering) WITHOUT swapping. */
internal fun MusicService.prepareSecondaryPlayer(targetIndex: Int) {
    if (secondaryPlayer != null || isCrossfading) return
    if (targetIndex == C.INDEX_UNSET) return

    // INSTANT VIDEO SWAP: the crossfade secondary and the speculative video pre-player must NEVER
    // coexist (max 2 ExoPlayers, same envelope as before the feature). Crossfade wins — the video
    // pre-player is pure speculation; the toggle falls back to the normal swap path.
    teardownInstantVideoSwap("crossfade secondary player preparing")

    val sec = createExoPlayer(isSecondary = true)
    sec.addListener(secondaryPlayerListener)

    // QUEUE COPY — the secondary BECOMES the live player at the swap (performCrossfadeSwap does
    // `player = nextPlayer`), so it genuinely needs the WHOLE queue: everything the user can seek back
    // to and everything still to come. Preparing "only the next item" would destroy the queue once per
    // song. What CAN go is the per-item cost of reading it.
    //
    // ONE timeline read + ONE reusable Window, exactly as in shuffleItemKeys: `getMediaItemAt(i)` is
    // `getCurrentTimeline().getWindow(i, sharedWindow).mediaItem` and `mediaItemCount` is
    // `getCurrentTimeline().getWindowCount()` (both verified in the media3-common 1.10.1 bytecode), so
    // the old loop paid an application-thread check and a playbackInfo hop per item, N+1 times over,
    // on the Main thread, once per song, on a queue the infinite radio only grows. The ArrayList is
    // also pre-sized now: mutableListOf() started at capacity 10 and doubled its way up, which on a
    // four-figure queue is ~9 reallocations plus the array copies behind them.
    //
    // IDENTICAL RESULT: same MediaItem instances, same order, same count, handed to the same
    // setMediaItems call. Deliberately NOT wrapped in runCatching — today this loop has no catch
    // either, and swallowing a failure here would hand the secondary a PARTIAL queue that becomes the
    // live one at the swap. Reusing one Window is what media3 itself does across successive
    // getMediaItemAt calls; `.mediaItem` is a reference read, so the next getWindow cannot disturb it.
    val liveTimeline = player.currentTimeline
    val itemCount = liveTimeline.windowCount
    val copyWindow = Timeline.Window()
    val items = ArrayList<MediaItem>(itemCount)
    for (i in 0 until itemCount) {
        items.add(liveTimeline.getWindow(i, copyWindow).mediaItem)
    }
    sec.setMediaItems(items)
    // Stamp which live-timeline version this COPY mirrors — the reuse check in scheduleCrossfade
    // compares against it. Read BEFORE this function's own player reads complete; onTimelineChanged
    // runs on this same Main thread, so no mutation can interleave mid-copy.
    secondaryTimelineVersion = timelineVersion
    val incomingId = items.getOrNull(targetIndex)?.mediaId
    // PER-SONG INTRO MEMORY — APPLICATION DISABLED (owner, 2026-08-18): "La Isla Bonita" measured
    // ~4s of sub-threshold intro and every later crossfade into it silently skipped straight to 0:04,
    // which read as the song randomly jumping ahead. The -42dBFS detector can't tell true dead air
    // from a quiet-but-real intro, and a wrong measurement then applies on EVERY future play, not just
    // once — too high a cost for a background polish feature. leadSilenceHintMs is still LEARNED below
    // (harmless, unread) so this can be re-enabled behind a toggle later without rebuilding the
    // detector; only the seek that SPENT the hint is removed. Always start incoming audio at 0.
    sec.seekTo(targetIndex, 0L)
    sec.volume = 0f
    sec.repeatMode = player.repeatMode
    sec.shuffleModeEnabled = player.shuffleModeEnabled
    // CARRY THE IN-FORCE SHUFFLE ORDER ONTO THE SECONDARY. Without this, each secondary rolls media3's
    // OWN uniform-random permutation (setMediaItems + shuffleModeEnabled builds a fresh
    // DefaultShuffleOrder) — the curated order lives only inside the LIVE ExoPlayer object and dies
    // with it at the swap. The per-boundary re-apply used to repaint it microseconds after every swap,
    // which masked this; with the stale-skip in place nothing repaints it, and shuffle would degenerate
    // to memoryless random-with-replacement: repeats mid-lap, no artist spacing, premature radio
    // handoff — the exact bug class rows 90/92/94/96/101/102 exist to prevent. The walk is the same
    // pointer chase playNext uses; O(N), no scoring, no Binder re-broadcast (the secondary is not the
    // MediaSession player), and the copy is made microseconds after setMediaItems on this same Main
    // thread, so the length cannot mismatch.
    if (player.shuffleModeEnabled && items.isNotEmpty()) {
        runCatching {
            // The SAME Timeline instance the copy above walked — not a second `player.currentTimeline`
            // read. onTimelineChanged runs on this same Main thread, so nothing can have mutated
            // between the two, and sharing the reference makes that guarantee structural instead of a
            // comment. The order walk itself is unchanged, and so is the order it produces.
            val order = IntArray(items.size)
            var oi = 0
            var w = liveTimeline.getFirstWindowIndex(true)
            while (w != C.INDEX_UNSET && oi < order.size) {
                order[oi++] = w
                w = liveTimeline.getNextWindowIndex(w, Player.REPEAT_MODE_OFF, true)
            }
            if (oi == order.size) {
                sec.setShuffleOrder(DefaultShuffleOrder(order, System.currentTimeMillis()))
            }
        }
    }
    // Carry the USER's playback settings across the swap. Speed/pitch and the chosen audio output are
    // set on the player object, not on a preference — so with crossfade ON (the default) every
    // auto-advance silently reset 1.25x/+2 semitones back to normal and dropped the selected output.
    sec.playbackParameters = player.playbackParameters
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        preferredDeviceId?.let { id ->
            runCatching {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .find { it.id == id }
                    ?.let { sec.setPreferredAudioDevice(it) }
            }
        }
    }

    // FIX B: pre-level the incoming track BEFORE sec.prepare() primes its first buffers. The secondary
    // shares the NormalizationGainAudioProcessor.gain static, which still holds the OUTGOING track's
    // value — so without this the incoming track primes at the wrong (often louder) level and then
    // ramps when the async prime below lands ("enters loud then corrects"). After Fix A the incoming
    // format is usually already cached, so resolve it synchronously here and set the per-instance gain
    // up front. If it isn't cached yet, the async scope.launch below resolves it (existing fallback).
    // Resolve the incoming gain from the IN-MEMORY hint cache (populated by setupLoudnessEnhancer on every
    // track start + by the upcoming-track preload, Fix A) — NO disk read on this thread. prepareSecondaryPlayer
    // runs on Dispatchers.Main, where a runBlocking Room/DataStore read stutters the transition / risks ANR
    // (the comment in startCrossfade documents that exact regression). Cache miss → the async fallback below
    // resolves it off-main before the fade.
    var primedSyncGain = false
    if (incomingId != null && (normalizationEnabledHint || safeVolumeEnabledHint)) {
        loudnessHintCache[incomingId]?.let { loudnessDb ->
            val mult = normalizationMultiplier(loudnessDb, enabled = true)
            val makeup = dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true))
            playerNormProcessors[sec]?.instanceGain = mult
            playerLimiterProcessors[sec]?.setInstanceMakeup(makeup, null)
            // Prime Safe Volume on the incoming player's live EQ processor so a loud track is attenuated
            // from the FIRST fade-in sample (else it swells in at full native level, then drops at swap).
            // MUST be the SAME full gain (attenuation x makeup) the main path applies when this track
            // becomes current — priming only the attenuate half would make a quiet track fade in lower
            // than it plays a moment later, i.e. an audible jump at the swap. No fade timing/curve here.
            if (safeVolumeEnabledHint) playerEqProcessors[sec]?.applySafeVolume(true, safeVolumeAppliedGain(mult * makeup))
            primedSyncGain = true
            Timber.tag(TAG).d("Crossfade: pre-leveled incoming $incomingId from cache (loudnessDb=$loudnessDb)")
        }
    }

    sec.playWhenReady = false // buffer ahead silently; startCrossfade flips this on at the fade
    sec.prepare()
    secondaryPlayer = sec

    // Prime the incoming player to ITS OWN track's normalization so the moment the fade starts it's
    // already at the right level (the shared companion statics still hold the OUTGOING track's values).
    // Fallback for the not-yet-cached case: only runs if the synchronous pre-level above didn't set the
    // gain (so it never overwrites an already-set instanceGain with a default).
    if (incomingId != null && !primedSyncGain) {
        scope.launch {
            val normalize = withContext(Dispatchers.IO) { dataStore.data.map { it[AudioNormalizationKey] ?: true }.first() }
            if (!normalize && !safeVolumeEnabledHint) return@launch
            val fmt = withContext(Dispatchers.IO) { database.format(incomingId).first() }
            val loudnessDb = effectiveLoudnessDb(fmt?.loudnessDb, fmt?.perceptualLoudnessDb, fmt?.measuredLoudnessDb)
            loudnessHintCache[incomingId] = loudnessDb
            withContext(Dispatchers.Main) {
                if (secondaryPlayer === sec || (player === sec && isCrossfading)) {
                    val mult = normalizationMultiplier(loudnessDb, enabled = true)
                    val makeup = dbToLinear(loudnessMakeupDb(loudnessDb, enabled = true))
                    playerNormProcessors[sec]?.instanceGain = mult
                    playerLimiterProcessors[sec]?.setInstanceMakeup(makeup, null)
                    if (safeVolumeEnabledHint) playerEqProcessors[sec]?.applySafeVolume(true, safeVolumeAppliedGain(mult * makeup))
                    if (player === sec) {
                        lastAppliedGain = mult
                        lastAppliedMakeup = makeup
                        lastNormalizedId = incomingId
                    }
                }
            }
        }
    }
}

internal fun MusicService.performCrossfadeSwap() {
    isCrossfading = true
    val nextPlayer = secondaryPlayer ?: return
    // Observation-only mirror for the UI (set AFTER the null-guard so it can't strand true); does not
    // alter the swap. Also drop any per-track Opus force from a refetch on the OUTGOING track: the swap
    // moves us to the next track via a path that skips onMediaItemTransition, so it must be cleared here.
    _isCrossfading.value = true
    forceOpusForMediaId = null
    // Bookkeeping only — no fade math, curve or duration is touched here. This callback-skipping path is
    // also the ONLY writer gap for currentPlayingMediaId (:3548 in onMediaItemTransition is the other, and
    // the only one): without this, after ANY swap the field still names the OUTGOING track for the whole of
    // the incoming one, so the resolver's `isCurrentlyPlaying` is false while that track plays. Its single
    // reader then falls back to the GLOBAL quality, the container guard compares that against a dbFormat
    // describing a fallback container, mismatches, and purges the playing track's cached bytes on every
    // re-open. With crossfade ON (the default here) every advance is a swap, so that was permanent.
    currentPlayingMediaId = nextPlayer.currentMediaItem?.mediaId
    // The UI's song identity ALSO only has two writers, and the other one lives in onEvents behind
    // TIMELINE_CHANGED/POSITION_DISCONTINUITY — neither fires for a swap, and the service listener is
    // attached to the incoming player only further down (after its own transition already happened).
    // Without this, with crossfade ON the widget/notification/Android-Auto kept showing the PREVIOUS
    // song's title, artist, artwork and like state while the progress bar advanced against the new one.
    nextPlayer.currentMetadata?.let { currentMediaMetadata.value = it }
    val currentPlayer = player

    // LEARN the outgoing track's intro silence (finalized at its first loud frame): next time this
    // song ENTERS a crossfade, the incoming player starts right at its music — the rise is heard over
    // real audio instead of dead intro air. Undercount-safe by construction (frames before arming are
    // simply not counted), so a stored skip can never eat music.
    playerSilenceProcessors[currentPlayer]?.leadingSilenceUsOrNegative()?.takeIf { it >= 0 }?.let { us ->
        val ms = us / 1_000L
        val id = currentPlayer.currentMediaItem?.mediaId
        if (id != null && id == tailArmedMediaId && leadHintTrustedForArmedTrack && ms in 1_000..20_000) {
            if (leadSilenceHintMs.size > 400) leadSilenceHintMs.clear()
            leadSilenceHintMs[id] = ms
        }
    }

    fadingPlayer = currentPlayer
    // Observation-only, for the lyrics view: this is the track the user KEEPS HEARING for the length of
    // the fade even though the incoming one was published above. Recorded here so the lyrics can stay on
    // it (and on its clock) until cleanupCrossfade commits. Null metadata simply leaves the override off,
    // i.e. the lyrics behave exactly as they did before. Nothing below this line changes.
    _crossfadeOutgoingMetadata.value = currentPlayer.currentMetadata
    // Pin the OUTGOING player to its current normalization (the companion statics still hold its
    // values right now) so when setupLoudnessEnhancer re-writes them for the incoming track, the
    // fading player keeps its own level instead of "pumping" to the new track's gain.
    playerNormProcessors[currentPlayer]?.instanceGain = NormalizationGainAudioProcessor.gain
    playerLimiterProcessors[currentPlayer]?.setInstanceMakeup(TruePeakLimiterAudioProcessor.loudnessMakeup, null)
    player = nextPlayer
    _playerFlow.value = player
    currentEqProcessor = playerEqProcessors[nextPlayer]
    val incomingIdNow = nextPlayer.currentMediaItem?.mediaId
    if (incomingIdNow != null && (normalizationEnabledHint || safeVolumeEnabledHint)) {
        loudnessHintCache[incomingIdNow]?.let { ld ->
            lastAppliedGain = normalizationMultiplier(ld, enabled = true)
            lastAppliedMakeup = dbToLinear(loudnessMakeupDb(ld, enabled = true))
        }
        lastNormalizedId = incomingIdNow
    }
    secondaryPlayer = null

    fadingPlayer?.removeListener(this)

    // Stop the outgoing player from auto-advancing into the NEXT track as it fades out. It still
    // holds the full queue, so when the current song ends mid-fade it would start the next song —
    // which the incoming player is ALSO playing → "the next track plays twice at once" at the start
    // of the transition.
    //
    // MUST NOT mutate the fading playlist (removeMediaItems/clearMediaItems): that races media3's
    // evaluateMediaItemTransitionReason and throws a bare IllegalStateException on the main Handler
    // (CRASH_REPORTS #2 + #5 — Xiaomi users mid-playlist with crossfade ON). pauseAtEndOfMediaItems
    // parks the player at EOS without touching the timeline; release() in cleanupCrossfade frees it.
    try {
        fadingPlayer?.let { fp ->
            fp.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            fp.pauseAtEndOfMediaItems = true
        }
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "crossfade: failed to park fading player at end of item")
    }


    player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isCrossfading && fadingPlayer != null) {
                if (isPlaying) {
                    fadingPlayer?.play()
                } else {
                    fadingPlayer?.pause()
                }
            } else {
                player.removeListener(this)
            }
        }
    })

    nextPlayer.removeListener(secondaryPlayerListener)
    nextPlayer.addListener(this)


    try {
        (mediaSession as MediaSession).player = player
    } catch (e: Exception) {
        timber.log.Timber.e(e, "Failed to swap player in MediaSession")
    }

    crossfadeJob = scope.launch {
        val configured = crossfadeDuration.toLong()
        // OUTGOING decay must COMPLETE within the fading track's audible life (owner: "la que sale
        // NO baja"): a fade that starts late — dynamic ready-wait, tail fire near the end — used to
        // run its full configured length, so the outgoing's file ENDED while its ramp was still near
        // full volume: heard as no decay at all. Cap its ramp to the actual remaining time
        // (pure decision in CrossfadePlanning.outgoingFadeDurationMs, characterization-locked).
        val fpRemaining = fadingPlayer?.let { fp ->
            val d = fp.duration
            if (d == C.TIME_UNSET) null else (d - fp.currentPosition).coerceAtLeast(0L)
        }
        val durOut = CrossfadePlanning.outgoingFadeDurationMs(configured, fpRemaining)
        val durIn = configured
        val curve = try { dataStore.get(CrossfadeCurveKey, 4) } catch (e: Exception) { 4 }
        // NIVEL BASE DEL BLEND = el volumen DEL USUARIO, no el que tuviera el reproductor saliente.
        //
        // Leía `fadingPlayer.volume`, y ese valor podía venir de una rampa de entrada manual
        // abandonada a medias (ver [fadeInOnManualChange]): entonces las DOS rampas se escalaban por,
        // digamos, 0.3, y la canción ENTRANTE sonaba a un tercio durante toda la mezcla — el "empieza
        // cortada" pegándose de una canción a la siguiente. El volumen del usuario es lo que ese
        // `fadingPlayer.volume` pretendía leer siempre; ahora se lee de donde vive de verdad.
        val startVolume = try {
            if (::playerVolume.isInitialized) {
                if (isMuted.value) 0f else playerVolume.value
            } else {
                fadingPlayer?.volume ?: 1f
            }
        } catch (e: Exception) { 1f }
        // Because LUFS Normalization is fixed and active, tracks play at roughly -14 LUFS,
        // leaving massive natural headroom. Thus, two tracks summing during an equal-power crossfade
        // will NEVER clip the Android mixer (they'll sum to ~-11 LUFS). We can safely remove the
        // old volume dip hack and keep the multiplier at 1.0f for a perfectly transparent blend.
        val xfHeadroom = 1f

        try {
            // DUAL-CLOCK blend. The old single stepped loop had a `while (!player.isPlaying) delay`
            // that FROZE the whole fade while the incoming track buffered its start (routine on
            // streamed/Lossless songs) — the outgoing sat pinned at full volume for those seconds and
            // then died with its ramp barely begun: the owner's exact "la que entra está bien pero la
            // que sale no baja". Now each side runs on ITS OWN playback clock:
            //  • OUTGOING advances only while the fading player actually renders → its decay is always
            //    audible, always completes before its content ends, freezes correctly on user pause;
            //  • INCOMING advances only while the new player renders → a buffering start can neither
            //    freeze the outgoing nor slam the incoming in at mid-level.
            var outElapsed = 0L
            var inElapsed = 0L
            var lastT = android.os.SystemClock.elapsedRealtime()
            var safety = 0L
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                val dt = (now - lastT).coerceAtLeast(0L)
                lastT = now
                safety += dt
                val fp = fadingPlayer
                if (fp?.isPlaying == true) outElapsed += dt
                if (player.isPlaying) inElapsed += dt
                // Outgoing counts as fully faded when it's gone (null/ended) — never stalls the loop.
                val outDone = fp == null || fp.playbackState == Player.STATE_ENDED
                val outP = if (outDone) 1f else (outElapsed / durOut.toFloat()).coerceAtMost(1f)
                val inP = (inElapsed / durIn.toFloat()).coerceAtMost(1f)
                val fadeIn = crossfadeGains(curve, inP).first
                val fadeOut = crossfadeGains(curve, outP).second

                try {
                    // Both players smoothly fade without needing to dynamically duck their headroom
                    player.volume = startVolume * fadeIn * xfHeadroom
                    fp?.volume = startVolume * fadeOut * xfHeadroom
                } catch (e: Exception) { break }

                // Release the lyrics pin on AUDIBILITY, not on ramp progress. Without this the pin
                // survives until cleanupCrossfade — which waits for BOTH ramps (the incoming one can
                // lag seconds behind, or freeze on buffering/pause) — so the panel keeps showing the
                // OUTGOING song's lyrics over a track that is already playing: the owner's "aparecen
                // letras que no son de esa canción si no de otras". The predicate + its unit tests
                // live in CrossfadeLyricsPin; this call is the wiring that was missing.
                if (_crossfadeOutgoingMetadata.value != null &&
                    CrossfadeLyricsPin.shouldRelease(
                        pinned = true,
                        outgoingGone = outDone,
                        outgoingCurveGain = fadeOut,
                        outgoingDetectedSilent = fp?.let {
                            playerSilenceProcessors[it]?.isCurrentlySilent()
                        } ?: false,
                    )
                ) {
                    _crossfadeOutgoingMetadata.value = null
                }

                if (inP >= 1f && outP >= 1f) break
                if (safety > durIn + durOut + 30_000L) break // pathological stall — bail to cleanup
                delay(40)
            }
        } finally {
            // ALWAYS end the crossfade cleanly — even if it's cancelled (skip/stop) mid-fade, which
            // throws from delay() and would otherwise skip the restore and leave the surviving player
            // silent for the rest of the session. Restore it to the user's real volume + tear down.
            runCatching {
                player.volume = when {
                    !::playerVolume.isInitialized -> startVolume
                    isMuted.value -> 0f
                    else -> playerVolume.value
                }
            }
            runCatching { cleanupCrossfade() }
        }
    }
}

/**
 * Gain pair (incoming, outgoing) for crossfade progress [p] in 0..1, per the selected style.
 *  0 = Linear: straight amplitude ramp (1 - p); amplitude sum never exceeds 1.0.
 *  1 = Smooth/equal-power (default): sin/cos keep incoming^2 + outgoing^2 = 1 (constant power), so
 *      both tracks carry the SAME power through the blend — the natural, even crossfade.
 *  2 = Long S-curve: equal-power but eased timing (very gradual in/out).
 *  3 = Exponential (quick): each track dominates its half, snappier handover.
 */
internal fun MusicService.crossfadeGains(curve: Int, p: Float): Pair<Float, Float> {
    return CrossfadeMath.getGains(curve, p)
}

internal fun MusicService.cleanupCrossfade() {
    // The crossfade is over: clear the surviving player's per-instance normalization overrides so it
    // resumes following the shared companion statics, and reset the de-dup guard so the next track
    // (re)normalizes normally via setupLoudnessEnhancer.
    playerNormProcessors[player]?.instanceGain = null
    playerLimiterProcessors[player]?.setInstanceMakeup(null, null)
    // Incoming track is already the audible one. Do NOT clear lastNormalizedId: that disarmed
    // the freeze for the rest of the song, so liking it (auto-download) re-levelled mid-play.
    // Refine the per-song tail memory with the EXACT end-of-stream measurement when the decoder
    // reached EOS (it runs ahead of the playback clock, so this is usually available even though the
    // silent tail itself never audibly played). Read BEFORE stop() — duration/item may reset after.
    fadingPlayer?.let { fp ->
        val trailingUs = playerSilenceProcessors[fp]?.trailingSilenceUsOrNegative() ?: -1L
        if (trailingUs >= 0) {
            runCatching { storeTailHint(fp.currentMediaItem?.mediaId, trailingUs / 1_000L, fp.duration) }
        }
    }
    fadingPlayer?.stop()
    // NO clearMediaItems: this teardown fires at fade end — often the exact moment the outgoing
    // player's own content ENDS (the 0.6.133 durOut cap makes that overlap routine). A playlist
    // mutation landing while its transition machinery evaluates the ended/auto transition hits
    // media3's bare "impossible state" IllegalStateException in evaluateMediaItemTransitionReason
    // (retraced client crash, CRASH_REPORTS #2). release() below frees everything anyway.
    fadingPlayer?.let {
        // Bookkeeping only — no fade math touched. Silence was the one map this teardown forgot, so every
        // crossfade left a dead entry holding a released ExoPlayer for the whole session.
        playerSilenceProcessors.remove(it)
        playerNormProcessors.remove(it)
        playerLimiterProcessors.remove(it)
        playerEqProcessors.remove(it)?.let { eq -> equalizerService.removeAudioProcessor(eq) }
    }
    fadingPlayer?.release()
    fadingPlayer = null
    isCrossfading = false
    _isCrossfading.value = false // observation-only mirror for the UI; does not alter the swap
    // The fade committed: the incoming track is now the audible one, so the lyrics view stops following
    // the outgoing song and returns to the live one. Observation-only, like the mirror above.
    _crossfadeOutgoingMetadata.value = null
    // Collect the quality-change survivor here rather than at the swap: the fade is over and fadingPlayer is
    // already stopped/cleared/released above, so dropping its URL entry cannot trigger a re-open. Needed
    // because performCrossfadeSwap skips onMediaItemTransition, and with crossfade ON every advance is a
    // swap — so the transition-based collector would never run and the pin would outlive its track.
    qualityPinnedMediaId?.let { pinned ->
        if (pinned != player.currentMediaItem?.mediaId) {
            songUrlCache.remove(pinned)
            qualityPinnedMediaId = null
            persistSongUrlCache()
        }
    }
}
