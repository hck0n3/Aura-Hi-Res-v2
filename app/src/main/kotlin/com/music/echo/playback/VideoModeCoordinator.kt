package iad1tya.echo.music.playback

import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.DownloadService
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ExportedFileUrisKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.extensions.currentMetadata
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.utils.YTPlayerUtils
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.exportedFileUriExists
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.parseExportedFileUriMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Stateful video-mode collaborator extracted verbatim from [MusicService] (HALLAZGO-021, RONDA 3
 * FASE B paso 3). Owns the video-mode state (tracked items, muxed flags, swap generation,
 * stuck-recovery bookkeeping, speculative-prefetch budget, per-song tail of the exported registry)
 * and its orchestration: toggle/enter/apply/swap/disarm/exit, the auto-advance prebuild, the
 * speculative prefetch and the stuck recovery.
 *
 * Every DECISION is delegated to the pure [VideoModePlanning] core (characterization-locked in
 * VideoModePlanningTest); every side effect runs through [service]. The coordinator never
 * reassigns the main player: the dual-player instant-swap publish, the media-source factory and
 * the swap-perf instrumentation stay in the service — they are the player-swap heart and read
 * this coordinator's state directly.
 */
class VideoModeCoordinator(private val service: MusicService) {

    /**
     * Bumped on every enter/exit so an in-flight video URL resolve from a previous toggle cannot
     * swap the source after the user has already left (or re-entered) video mode.
     */
    internal val videoSwapGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * Session cap for ALL speculative video-URL prefeches (audio-only mode). Always bounded — never
     * uncapped after first video use — so cipher/PoToken contention cannot hammer every video-capable
     * track transition and cut audible audio (heat/battery + stutter rule).
     */
    private var speculativeVideoPrefetches = 0

    /**
     * YouTube Music's Song/Video switch is a PREFERENCE, not a per-track state (owner directive
     * 2026-09-13: "la lógica entre cambiar de música a video y viceversa, igual que YouTube Music").
     * Once the user picks Video it sticks: a track with no video plays as audio with its cover, and
     * the next track that HAS a video comes back in video automatically. Only an explicit switch back
     * to Song ([exitVideoMode]) clears it. Before, the first audio-only track disarmed video mode for
     * good and every later video played as audio.
     */
    @Volatile
    internal var stickyVideoPreferred = false

    internal val preloadedVideoOriginalUris = mutableMapOf<String, String>()

    // Ids whose cached/resolved video URL is a MUXED stream (audio embedded in the video file):
    // createMediaSource must NOT merge a separate audio source for them (double audio). Mirrors the
    // entries in [MusicService.videoUrlCache] that came from YTPlayerUtils.adaptiveVideoStreamNewPipe
    // reporting isMuxed=true (the adaptive video-only picks are NOT in this set — they get the audio merge).
    internal val newPipeMuxedVideoIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // Multi-item video tracking (generalizes the single videoModeMediaId). Every mediaId here has its player
    // MediaItem URI currently set to a VIDEO stream — the playing video track AND any UPCOMING item that was
    // pre-built for a seamless auto-advance (see prebuildNextVideoItem). createMediaSource is authoritative
    // off this map: any id present → it builds the MergingMediaSource (video-only + merged audio), so an item
    // can become a video source BEFORE it is current and the transition needs no swap on the running track.
    // ConcurrentHashMap because createMediaSource may be invoked off the main thread.
    internal data class VideoTrackState(
        val videoUrl: String,
        val originalAudioUri: String?,
        val isMuxedPodcast: Boolean,
    )
    internal val videoModeItems = java.util.concurrent.ConcurrentHashMap<String, VideoTrackState>()
    // Ids with a video-URL resolve currently in flight (dedupe; cleared in a finally / on exit).
    private val prebuildingIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** One re-prepare per [mediaId] when video stalls in BUFFERING/IDLE (debounced). */
    private val videoStuckRecoveryAttemptedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    internal var videoStuckRecoveryJob: Job? = null

    // Best VIDEO-ONLY height to request for video mode. On Android TV (big screen, detected server-side via
    // UiModeManager in DeviceForm.isTelevision) we derive the target height from a LIVE bandwidth estimate so
    // video STARTS at a sustainable resolution instead of always demanding 1080p — the root cause of TV video
    // stalling like the network is failing (a single fixed-resolution ProgressiveMediaSource can't drop). The
    // chosen video-only stream is MERGED with a separate audio track (see createMediaSourceFactory). Phones/
    // tablets get null → YTPlayerUtils keeps its existing metered-aware cap (720p WiFi / 360p data), unchanged.
    private val videoModeMaxHeight: Int?
        get() = if (iad1tya.echo.music.utils.DeviceForm.isTelevision(service)) bandwidthAwareVideoHeight() else null

    // Map the current downstream bandwidth estimate to a TV video-only target height. Capped at the TV 1080
    // ceiling. NOTE: DefaultBandwidthMeter.bitrateEstimate never actually returns 0 — before any real transfer
    // it hands back a synthetic country/network INITIAL estimate (~4-8 Mbps), so a cold TV must not read that
    // as "fast enough for 1080". The 1080 gate is therefore raised to 8 Mbps so only a genuinely strong,
    // measured estimate yields 1080; a cold synthetic estimate now yields <=720 (safe). The tier's maxVideoDim
    // is aligned in createExoPlayer to allow up to 1080 (1920 wide) on TV so the chosen track is never silently
    // rejected. The existing 720p (null) fallback in the resolve paths still covers a failed selection.
    private fun bandwidthAwareVideoHeight(): Int {
        val estimate = runCatching {
            androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(service).bitrateEstimate
        }.getOrDefault(0L)
        // Tier table characterization-locked in VideoModePlanning.bandwidthAwareVideoHeight.
        return VideoModePlanning.bandwidthAwareVideoHeight(estimate)
    }

    fun toggleVideoMode() {
        // High-Performance Mode defaults to audio-only, but if the user explicitly taps the Video toggle,
        // we should respect their intent and let them switch. We no longer block it here.
        if (service._videoMode.value) {
            exitVideoMode()
        } else {
            enterVideoModeInternal(forceExplicit = true)
        }
    }

    /**
     * Force video mode ON for the current item (no-op if already on). Used when opening from
     * Vídeos exportados — playback should start in video; the user can still exit later.
     *
     * [forceFromUserTap] bypasses the High-Performance Mode block: a deliberate tap on a video
     * poster is an explicit user request and must always work, even on low-end devices.
     */
    fun enterVideoModeIfNeeded(forceFromUserTap: Boolean = false) {
        // Pure gate in VideoModePlanning.videoEntryAllowed: High-Performance Mode blocks implicit
        // entries but never an explicit user tap, and TV/car devices are exempt from the block.
        if (!VideoModePlanning.videoEntryAllowed(
                forceFromUserTap = forceFromUserTap,
                performanceModeOn = iad1tya.echo.music.utils.PerformanceMode.isOn(service),
                isTvOrCar = iad1tya.echo.music.utils.DeviceForm.isTvOrCar(service)
            )
        ) {
            return
        }
        if (service._videoMode.value) return
        enterVideoModeInternal(forceExplicit = forceFromUserTap)
    }

    private fun enterVideoModeInternal(forceExplicit: Boolean = false) {
        // 2026-08-23 owner directive: only SimpMusic providers are allowed. Video mode resolves
        // muxed streams via TVHTML5 (not a SimpMusic provider), so every entry point is gated here.
        if (!MusicService.VIDEO_PROVIDERS_ENABLED) {
            Timber.tag(MusicService.TAG).i("Video mode disabled: only SimpMusic providers are allowed")
            return
        }
        service.userExplicitlyExitedVideo = false
        service.userHasUsedVideo = true
        stickyVideoPreferred = true
        service.player.currentMediaItem?.mediaId?.let { service.resetRetryCount(it) }
        service.videoSwapMeasureStart()
        val gen = videoSwapGeneration.incrementAndGet()
        if (!service.tryInstantVideoSwap()) {
            service.teardownInstantVideoSwap("video mode on via normal path")
            service._videoMode.value = true
            // Do NOT pause downloads until swapToVideo actually commits. Pausing here and then
            // early-outing (no video / resolve fail) left Exo downloads frozen and the player
            // download icon stuck with no tap response.
            applyVideoToCurrent(swapGeneration = gen, forceExplicit = forceExplicit)
        } else {
            pauseOfflineDownloadsForVideoPlayback()
        }
        val nextIdx = service.player.nextMediaItemIndex
        if (nextIdx != C.INDEX_UNSET) {
            runCatching { service.player.getMediaItemAt(nextIdx).mediaId }.getOrNull()
                ?.let { prebuildNextVideoItem(nextIdx, it) }
        }
    }

    // NOTE (historical): a prior attempt "pre-swapped" the next item's URI to the video stream while the
    // single videoModeMediaId still pointed at the CURRENT track. Because that id wasn't recognised as a
    // video item, createMediaSource built it via the DEFAULT factory, whose audio ResolvingDataSource (keyed
    // on the mediaId) overrode the pre-set video URI back to the AUDIO stream → the next item played
    // audio-only, and swapToVideo then saw uri == url and returned WITHOUT prepare() → blank/frozen video.
    // The fix (prebuildNextVideoItem + the videoModeItems map read in createMediaSource) marks the upcoming
    // id as a video item FIRST, so its source is built through videoFactory (which honours the video URI
    // directly) and the ResolvingDataSource only ever touches the SEPARATE merged audio sub-source.

    private fun isVideoDownloadComplete(songId: String): Boolean {
        val vidKey = videoDownloadMediaId(songId)
        val cachedLength = androidx.media3.datasource.cache.ContentMetadata
            .getContentLength(service.downloadCache.getContentMetadata(vidKey))
        // Pure length semantics in VideoModePlanning.videoDownloadCacheComplete; the byte-range
        // isCached check (stateful, needs the cache) stays here.
        return VideoModePlanning.videoDownloadCacheComplete(cachedLength) &&
            service.downloadCache.isCached(vidKey, 0, cachedLength)
    }

    /**
     * Local MP4 from AudioExportService (Vídeos exportados). Muxed A+V — play offline with
     * [swapToVideo] `isMuxed = true`, never YouTube resolve.
     */
    internal fun exportedMuxedVideoUri(songId: String): String? {
        if (songId.isBlank()) return null
        if (songId !in exportedVideoIds()) return null
        val raw = runBlocking(Dispatchers.IO) {
            service.dataStore.data.first()[ExportedFileUrisKey].orEmpty()
        }
        val uri = parseExportedFileUriMap(raw)[songId] ?: return null
        return uri.takeIf { exportedFileUriExists(service, it) }
    }

    private fun exportedVideoIds(): List<String> =
        service.dataStore.get(ExportedVideoIdsKey, "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * True when [songId] is LISTED as an exported video but [exportedMuxedVideoUri] came back null —
     * i.e. its file mapping was never saved, or the file was since moved/deleted. Distinguishing this
     * from "never was an exported video" matters: [applyVideoToCurrent] used to treat both cases the
     * same and fall through to an ONLINE YouTube video resolve for an id whose whole reason for being
     * in "Vídeos exportados" is that it is a private/offline export — that resolve would either fail
     * outright or, worse, succeed against an audio-only stream that the muxed-video playback path then
     * fails to read as a container (owner-reported CONTAINER_3003 / "video no encontrado"). Callers use
     * this to short-circuit with an honest "file missing" outcome instead of a confusing network retry.
     */
    private fun isRegisteredExportedVideoMissingFile(songId: String): Boolean =
        songId.isNotBlank() && songId in exportedVideoIds() && exportedMuxedVideoUri(songId) == null

    internal fun scheduleVideoStuckRecoveryCheck() {
        videoStuckRecoveryJob?.cancel()
        if (!service._videoMode.value || service._videoUrl.value.isNullOrEmpty()) return
        videoStuckRecoveryJob = service.scope.launch {
            // Idle-only recovery waits a beat for a transient IDLE→BUFFERING transition after a swap.
            delay(5_000)
            withContext(Dispatchers.Main) { maybeRecoverStuckVideo() }
        }
    }

    /**
     * Re-prepare once when video mode is on but the pipeline is dead ([Player.STATE_IDLE]) while the
     * user still wants playback. **Must not treat [Player.STATE_BUFFERING] as stuck** — normal video
     * rebuffers often last several seconds; calling `prepare()` there forced a full restart and looked
     * exactly like "se traba y al rato continúa" (0.6.161).
     */
    private fun maybeRecoverStuckVideo() {
        if (!service._videoMode.value || service._videoUrl.value.isNullOrEmpty()) return
        if (!service.player.playWhenReady) return
        val state = service.player.playbackState
        val id = service.player.currentMediaItem?.mediaId ?: return
        val now = System.currentTimeMillis()
        val last = videoStuckRecoveryAttemptedAt[id] ?: 0L
        // Pure gate in VideoModePlanning.stuckVideoShouldReprepare (characterization-locked):
        // ONLY STATE_IDLE counts as stuck — BUFFERING is a normal rebuffer (0.6.161) — and at most
        // one re-prepare per id per 30s.
        if (!VideoModePlanning.stuckVideoShouldReprepare(
                videoModeOn = service._videoMode.value,
                videoUrlPresent = !service._videoUrl.value.isNullOrEmpty(),
                playWhenReady = service.player.playWhenReady,
                isStateIdle = state == Player.STATE_IDLE,
                currentIdMatchesVideoMediaId = id == service.videoModeMediaId,
                msSinceLastAttempt = now - last
            )
        ) return
        videoStuckRecoveryAttemptedAt[id] = now
        Timber.tag(MusicService.TAG).w("Video stuck in IDLE — re-preparing $id")
        val playing = service.player.playWhenReady
        service.player.prepare()
        service.player.playWhenReady = playing
    }

    /** Resolve the current track's muxed video URL and swap its source in-place (audio is never stopped). */
    internal fun applyVideoToCurrent(
        armModeWhenReady: Boolean = false,
        swapGeneration: Int = videoSwapGeneration.get(),
        forceExplicit: Boolean = false,
    ) {
        val item = service.player.currentMediaItem ?: return
        val id = item.mediaId
        // Restore any OTHER tracked video items (the previous track, or a stale pre-built one) to audio;
        // the current id is about to be (re)swapped to video below.
        restoreVideoTracksExcept(id)

        // Decision tree lives in VideoModePlanning (RONDA 3 FASE B paso 2): the service snapshots
        // everything the tree reads, the pure core picks the branch, the service runs the effects.
        val snapshot = VideoModePlanning.ApplySnapshot(
            podcastVideoUrl = service.player.currentMetadata?.podcastVideoUrl,
            exportedVideoUri = exportedMuxedVideoUri(id),
            registeredExportedMissingFile = isRegisteredExportedVideoMissingFile(id),
            isHttpOrLocalId = id.startsWith("http", ignoreCase = true) || id.isLocalMediaId(),
            forceExplicit = forceExplicit,
            isVideoSong = service.player.currentMetadata?.isVideoSong == true,
            offlineOnly = service.dataStore.get(OfflineModeKey, false),
            videoDownloaded = isVideoDownloadComplete(id),
            cachedUrl = VideoModePlanning.cachedVideoUrl(MusicService.videoUrlCache[id], System.currentTimeMillis()),
            armModeWhenReady = armModeWhenReady,
            videoModeOn = service._videoMode.value,
            muxedFlagForId = id in newPipeMuxedVideoIds,
            offlineCacheUri = offlineVideoCacheUri(id),
        )
        when (val action = VideoModePlanning.decideApply(snapshot)) {
            VideoModePlanning.ApplyAction.NoOp -> return
            VideoModePlanning.ApplyAction.DisarmKeepAudio -> {
                // No video possible for this item (direct/local track, or a YouTube track that is
                // not a video song while sticky video is armed): drop to audio cleanly — no failed
                // resolution toast and no stuck spinner over the cover.
                disarmVideoModeKeepAudio()
                return
            }
            is VideoModePlanning.ApplyAction.DisarmMissingExportedFile -> {
                Timber.tag(MusicService.TAG).w("Exported video file missing/moved for $id — not attempting online resolve")
                if (action.showToast) {
                    Toast.makeText(service, "Video no encontrado — puede que se haya movido o eliminado", Toast.LENGTH_SHORT).show()
                }
                disarmVideoModeKeepAudio()
                return
            }
            is VideoModePlanning.ApplyAction.DisarmOfflineNotDownloaded -> {
                if (action.showToast) {
                    Toast.makeText(
                        service,
                        service.getString(R.string.error_offline_not_downloaded),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                disarmVideoModeKeepAudio()
                return
            }
            is VideoModePlanning.ApplyAction.Swap -> {
                if (action.armModeFirst) {
                    service._videoMode.value = true
                }
                if (action.url == snapshot.cachedUrl) {
                    service.videoSwapMark("applyVideoToCurrent: URL cache HIT")
                }
                swapToVideo(id, action.url, isMuxed = action.isMuxed)
                return
            }
            VideoModePlanning.ApplyAction.LiveResolve -> {
                // Fall through to the live resolve below — audio keeps playing during resolve.
            }
        }
        service.videoSwapMark("applyVideoToCurrent: URL cache MISS → live resolve")
        // Keep playing audio during resolve when arming lazily; spinner only if already in video mode.
        if (!armModeWhenReady) {
            service._videoUrl.value = null  // spinner while resolving
        }
        service.scope.launch(Dispatchers.IO) {
            val maxH = videoModeMaxHeight
            var url: String? = null
            var muxed = false
            var innerTubeResult: Result<String>? = null
            // FAST PATH FIRST — the music↔video toggle used to sit here while the burned InnerTube
            // client class chewed through its multi-client resolve budget and only THEN fell back to
            // the extractor. PipePipe (SimpMusic's own provider, ANDROID_VR extraction) is NOT burned,
            // returns adaptive video-only formats and answers in one extraction call — mirroring the
            // audio path, where the extractor is already the primary URL source. InnerTube remains as
            // fallback for devices where it still works.
            val picked = YTPlayerUtils.adaptiveVideoStreamNewPipe(id, service.connectivityManager, maxH).getOrNull()
            if (picked != null && !picked.first.isNullOrEmpty()) {
                url = picked.first
                muxed = VideoModePlanning.muxedFlagAfterResolve(fromPipePipe = true, pipePipeMuxed = picked.second)
                if (muxed) newPipeMuxedVideoIds.add(id) else newPipeMuxedVideoIds.remove(id)
                Timber.tag(MusicService.TAG).i("Video mode: resolved via PipePipe adaptive muxed=$muxed (fast path)")
            } else {
                var result = runCatching { YTPlayerUtils.videoStreamUrlDiag(id, service.connectivityManager, maxH) }
                    .getOrElse { Result.failure(it) }
                // TV robustness: if 1080p video-only selection failed at runtime, fall back to the default
                // (720p) resolution so video mode never black-screens (no regression vs. phone/tablet).
                if (VideoModePlanning.shouldRetryWithoutHeightCap(maxH, result.getOrNull())) {
                    result = runCatching { YTPlayerUtils.videoStreamUrlDiag(id, service.connectivityManager, null) }
                        .getOrElse { Result.failure(it) }
                }
                innerTubeResult = result
                url = result.getOrNull()
                if (!url.isNullOrEmpty()) {
                    // InnerTube worked — a stale muxed flag must not poison the video-only merge path.
                    muxed = VideoModePlanning.muxedFlagAfterResolve(fromPipePipe = false, pipePipeMuxed = false)
                    if (!muxed) newPipeMuxedVideoIds.remove(id)
                }
            }
            val resolvedUrl = url
            withContext(Dispatchers.Main) {
                if (videoSwapGeneration.get() != swapGeneration) return@withContext
                if (service.player.currentMediaItem?.mediaId != id) return@withContext
                if (resolvedUrl.isNullOrEmpty()) {
                    if (!armModeWhenReady) {
                        disarmVideoModeKeepAudio()
                        val ex = innerTubeResult?.exceptionOrNull()
                        val reason = ex?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "sin formato de video"
                        Toast.makeText(service, "Video falló — $reason", Toast.LENGTH_LONG).show()
                    }
                    return@withContext
                }
                MusicService.videoUrlCache[id] = resolvedUrl to VideoModePlanning.videoUrlCacheExpiry(System.currentTimeMillis())
                if (armModeWhenReady) {
                    service._videoMode.value = true
                } else if (!service._videoMode.value) {
                    return@withContext
                }
                swapToVideo(id, resolvedUrl, isMuxed = muxed)
            }
        }
    }

    /** Drop video chrome and keep audio. Safe to call when downloads were never paused. */
    private fun disarmVideoModeKeepAudio() {
        service._videoMode.value = false
        service._videoUrl.value = null
        resumeOfflineDownloadsAfterVideoPlayback()
    }

    /** Swap the current item's source URI to [url] (the muxed stream) so the factory builds a video source
     * rendered on the main player. Keeps position + play state. */
    private fun swapToVideo(id: String, url: String, isMuxed: Boolean = false) {
        service.videoSwapMark("swapToVideo entry")
        val idx = service.player.currentMediaItemIndex
        val item = service.player.currentMediaItem ?: return
        if (item.mediaId != id) return
        // Prefer a previously-captured original audio URI (pre-built entry or preload map) so we never store
        // the video URL itself as the "audio" URI for an item that is already showing video.
        val origUri = videoModeItems[id]?.originalAudioUri
            ?.takeUnless { it.contains("googlevideo.com", ignoreCase = true) }
            ?: preloadedVideoOriginalUris.remove(id)
                ?.takeUnless { it.contains("googlevideo.com", ignoreCase = true) }
            ?: item.localConfiguration?.uri?.toString()
                ?.takeUnless { it.contains("googlevideo.com", ignoreCase = true) }
            ?: id
        service.videoModeOriginalUri = origUri
        service.videoModeMediaId = id
        // Podcast video is a single muxed stream (has audio) → don't merge a 2nd audio; YouTube is video-only.
        service.videoModeIsMuxedPodcast = isMuxed
        // Register in the shared map so createMediaSource builds this item's video+audio source (the map, not
        // the single id, is now authoritative there).
        videoModeItems[id] = VideoTrackState(url, origUri, isMuxed)

        val playing = service.player.playWhenReady
        val sameUri = item.localConfiguration?.uri?.toString() == url

        // Muxed local/export: audio path often already points at this same file via ResolvingDataSource.
        // Early-return without rebuild left videoModeItems set but the player still on the audio factory
        // → surface stayed black / toggle looked like "needs internet". Always rebuild for muxed.
        if (sameUri && !isMuxed) {
            service._videoUrl.value = url
            if (playing) service.player.playWhenReady = true
            // Only re-prepare from IDLE. Calling prepare() while BUFFERING restarts the pipeline and
            // looks like "se traba / buffering y al rato sigue" (same class of bug as maybeRecoverStuckVideo).
            if (playing && service.player.playbackState == Player.STATE_IDLE) {
                service.player.prepare()
                service.player.playWhenReady = true
            }
            pauseOfflineDownloadsForVideoPlayback()
            scheduleVideoStuckRecoveryCheck()
            return
        }

        val pos = service.player.currentPosition
        // Tag forces MediaItem inequality when URI is unchanged (muxed export already playing as audio).
        // For muxed swaps this used to be a bare string, which made MediaItem.metadata (tag as? MediaMetadata)
        // return null downstream — the queue list, mini player and QueueMenu all read that accessor for
        // title/artist/thumbnail/duration, so they went blank/crashed. Force inequality via a nonce on the
        // REAL metadata object instead, so every real field stays intact.
        val videoItem = item.buildUpon()
            .setUri(url)
            // VIDEO CACHE KEY (2026-09-05, the "No media id" crash from the owner's log): the
            // old setCustomCacheKey(null) kept the video out of the audio's downloadCache key
            // (audit FASE 2-B #1) but ALSO killed the resolver's identity — dataSpec.key was
            // null and the load crashed with IllegalStateException: No media id, which is what
            // broke video mode on the S26 (log 13:40/15:06 "Instant-video pre-player error").
            // The dedicated "yt-video-<videoId>" key keeps the bytes OUT of the audio key
            // (different namespace) while giving the resolver a stable id (the resolver strips
            // the prefix — see createDataSourceFactory) AND making the video cache actually
            // readable on replay (the 2026-09-04 audit found the old rotating-URL keys were a
            // write-only ghost: every view re-downloaded the whole video).
            .setCustomCacheKey("yt-video-${item.mediaId}")
            .setTag(
                if (isMuxed) item.metadata?.copy(videoSwapNonce = System.nanoTime()) ?: item.localConfiguration?.tag
                else item.localConfiguration?.tag
            )
            .build()
        service.player.replaceMediaItem(idx, videoItem)
        // Video swap: seek keyframe-aligned (CLOSEST_SYNC) so the first video frame decodes sooner — an EXACT
        // seek must decode every frame from the previous keyframe up to pos before it can show anything.
        // Restored to DEFAULT (EXACT) immediately so ONLY this swap seek is keyframe-aligned; all audio seeks
        // stay exact. In practice capable-only: video mode is force-off in High-Performance Mode. Audio-only
        // playback never reaches swapToVideo, so the audio path is byte-identical.
        service.player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        service.player.seekTo(idx, pos)
        service.player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.DEFAULT)
        // Muxed same-URI rebuild must prepare even from READY so ProgressiveMediaSource takes over.
        // YouTube (different URI, same tag/mediaId) also requires prepare() so ExoPlayer actually
        // recreates the MediaPeriod instead of ignoring the URI change on the active window.
        service.player.prepare()
        service._videoUrl.value = url
        pauseOfflineDownloadsForVideoPlayback()
        scheduleVideoStuckRecoveryCheck()
        // Keep playWhenReady as the user left it. A previous "wait for 2.5s buffered" gate set
        // playWhenReady=false, which (a) stopped playback for several seconds on every enter and
        // (b) was then captured by exitVideoMode as "user paused" so the next enter never resumed
        // and the cover overlay never received onRenderedFirstFrame.
        if (playing) {
            service.player.playWhenReady = true
        }
    }

    /**
     * Restore tracked video items (wherever they sit in the queue) back to their normal audio source,
     * EXCEPT [keepId] (the one that should stay a video source). Pass null to restore ALL (leaving video
     * mode). Only the CURRENT item, if restored, does a prepare(); non-current items are replaced in place
     * with no effect on the running track. Keeps the single-field bookkeeping consistent with what remains.
     */
    internal fun restoreVideoTracksExcept(keepId: String?) {
        val toRestore = videoModeItems.keys.filter { it != keepId }
        for (vid in toRestore) {
            val state = videoModeItems.remove(vid) ?: continue
            // YouTube audio items use the mediaId as URI. If we lost originalAudioUri (rapid
            // toggles storing the video URL as "original"), fall back to the id so restore still
            // leaves a resolvable audio source instead of a googlevideo URI with videoMode off.
            val origUri = state.originalAudioUri
                ?.takeUnless { it.contains("googlevideo.com", ignoreCase = true) }
                ?: vid.takeUnless { it.startsWith("http", ignoreCase = true) || it.isLocalMediaId() }
                ?: continue
            for (i in 0 until service.player.mediaItemCount) {
                val it = runCatching { service.player.getMediaItemAt(i) }.getOrNull() ?: continue
                if (it.mediaId == vid) {
                    val isCurrent = i == service.player.currentMediaItemIndex
                    val pos = if (isCurrent) service.player.currentPosition else 0L
                    val playing = service.player.playWhenReady
                    service.player.replaceMediaItem(i, it.buildUpon().setUri(origUri).build())
                    if (isCurrent) {
                        service.player.seekTo(i, pos)
                        service.player.playWhenReady = playing
                        service.player.prepare()
                    }
                    break
                }
            }
        }
        val kept = keepId?.let { videoModeItems[it] }
        if (kept != null) {
            service.videoModeMediaId = keepId
            service.videoModeOriginalUri = kept.originalAudioUri
            service.videoModeIsMuxedPodcast = kept.isMuxedPodcast
        } else {
            service.videoModeMediaId = null
            service.videoModeOriginalUri = null
            service.videoModeIsMuxedPodcast = false
        }
    }

    /**
     * AUTO-ADVANCE fast path: pre-build the UPCOMING item ([nextIdx]/[nextId]) as a video (Merging) source
     * BEFORE it becomes current, so the auto-advance transition needs NO replaceMediaItem/prepare on the
     * running track (that in-place rebuild forced STATE_BUFFERING → the brief stop). We resolve the next
     * track's video URL (reusing videoUrlCache) and, on the main thread, replace ONLY the next (non-current)
     * item's URI with the video URL + register it in [videoModeItems] so createMediaSource builds video+audio
     * directly when media3 preloads/plays that window.
     *
     * Fully guarded so it can never regress audio or the on-demand toggle: only genuine YouTube video songs,
     * NEVER the current/running item, and a graceful no-op if resolution fails or the queue moved — in which
     * case the transition simply falls back to the on-demand swap (applyVideoToCurrent → brief spinner).
     */
    internal fun prebuildNextVideoItem(nextIdx: Int, nextId: String) {
        if (nextId.isEmpty() || nextId.isLocalMediaId() || nextId.startsWith("http", ignoreCase = true)) return
        if (videoModeItems.containsKey(nextId)) return // already pre-built as video
        // Local exported MP4 — no network; register as muxed before the YT path.
        val exportedNext = exportedMuxedVideoUri(nextId)
        if (!exportedNext.isNullOrEmpty()) {
            val item = runCatching { service.player.getMediaItemAt(nextIdx) }.getOrNull() ?: return
            if (item.mediaId != nextId) return
            val origUri = item.localConfiguration?.uri?.toString()
            videoModeItems[nextId] = VideoTrackState(exportedNext, origUri, true)
            if (origUri != exportedNext) {
                service.player.replaceMediaItem(
                    nextIdx,
                    item.buildUpon()
                        .setUri(exportedNext)
                        .setTag(item.metadata?.copy(videoSwapNonce = System.nanoTime()) ?: item.localConfiguration?.tag)
                        .build(),
                )
            }
            return
        }
        // Only genuine YouTube VIDEO songs can be shown as merged video-only + audio. Anything else
        // (audio-only song, podcast, non-video) falls back to the on-demand path at its own transition.
        val nextMeta = runCatching { service.player.getMediaItemAt(nextIdx).metadata }.getOrNull()
        if (nextMeta?.isVideoSong != true) return
        if (!prebuildingIds.add(nextId)) return // a resolve for this id is already in flight
        service.scope.launch(Dispatchers.IO) {
            try {
                val maxH = videoModeMaxHeight
                var muxed = nextId in newPipeMuxedVideoIds
                var url = MusicService.videoUrlCache[nextId]?.takeIf { it.second > System.currentTimeMillis() }?.first
                if (url.isNullOrEmpty()) {
                    // PipePipe FIRST (mirrors applyVideoToCurrent): the fast, unburned SimpMusic
                    // provider — InnerTube only as fallback for devices where it still resolves.
                    val picked = YTPlayerUtils.adaptiveVideoStreamNewPipe(nextId, service.connectivityManager, maxH).getOrNull()
                    if (picked != null && !picked.first.isNullOrEmpty()) {
                        url = picked.first
                        muxed = picked.second
                        if (muxed) newPipeMuxedVideoIds.add(nextId) else newPipeMuxedVideoIds.remove(nextId)
                    } else {
                        url = runCatching { YTPlayerUtils.videoStreamUrl(nextId, service.connectivityManager, maxH) }.getOrNull()
                        // TV robustness: if 1080p came back empty, fall back to the default resolution.
                        if (url.isNullOrEmpty() && maxH != null) {
                            url = runCatching { YTPlayerUtils.videoStreamUrl(nextId, service.connectivityManager, null) }.getOrNull()
                        }
                        if (!url.isNullOrEmpty()) {
                            muxed = false
                            newPipeMuxedVideoIds.remove(nextId)
                        }
                    }
                }
                val resolved = url
                if (resolved.isNullOrEmpty()) return@launch
                MusicService.videoUrlCache[nextId] = resolved to VideoModePlanning.videoUrlCacheExpiry(System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    // Re-validate on the main thread: still in video mode, the target is still the NEXT item
                    // (never the current/running one — that would re-introduce the stall), not already built.
                    if (!service._videoMode.value) return@withContext
                    if (videoModeItems.containsKey(nextId)) return@withContext
                    val idx = service.player.nextMediaItemIndex
                    if (idx == C.INDEX_UNSET || idx == service.player.currentMediaItemIndex) return@withContext
                    val item = runCatching { service.player.getMediaItemAt(idx) }.getOrNull() ?: return@withContext
                    if (item.mediaId != nextId) return@withContext
                    val origUri = item.localConfiguration?.uri?.toString()
                    // Register FIRST so the createMediaSource triggered by replaceMediaItem sees the video
                    // state and builds video+audio (not audio-only — the earlier pre-swap failure).
                    videoModeItems[nextId] = VideoTrackState(resolved, origUri, muxed)
                    if (origUri != resolved) {
                        // Replace ONLY the upcoming (non-current) item → no STATE_BUFFERING on the running track.
                        // Dedicated yt-video-<id> key (2026-09-05 "No media id" crash + ghost video
                        // cache — same cure as swapToVideo: stable identity for the resolver, video
                        // bytes under their own readable cache key, never the audio downloadCache one).
                        service.player.replaceMediaItem(
                            idx,
                            item.buildUpon().setUri(resolved).setCustomCacheKey("yt-video-$nextId").build(),
                        )
                    }
                }
            } finally {
                prebuildingIds.remove(nextId)
            }
        }
    }

    /**
     * Speculatively resolve the CURRENT track's video URL into [MusicService.videoUrlCache] so the first
     * toggle is instant. NEVER touches the player/audio graph — a failed resolve is swallowed and the toggle
     * still falls back to the live resolve exactly as today. Gated to avoid rate-limit / mid-song stutter:
     *   - video mode currently OFF
     *   - at most 8 speculative resolves per session (ALWAYS — never uncapped after first video use)
     *   - before first video use: capable devices only (not High-Performance / LOW / ULTRA)
     *   - skip when audio stream resolve or cipher/PoToken is busy (mutex contention cuts audio)
     *   - skip when [iad1tya.echo.music.utils.ThermalManager.isHot]
     *   - a genuine YouTube VIDEO song (isVideoSong == true)
     */
    internal fun prefetchCurrentVideoUrl() {
        // A toggle-to-video is only possible from audio; when already in video mode the swap has run.
        if (service._videoMode.value) return
        // DATA SAVER: no speculative video-URL resolves — the toggle resolves on demand instead.
        if (service.dataSaverEnabled) return
        // Heat: never start speculative cipher work on a thermally throttled device.
        if (iad1tya.echo.music.utils.ThermalManager.isHot.value) return
        // Do not contend with the live audio resolve (loader thread) for shared WebView mutexes.
        if (service.audioStreamResolveInFlight.get() > 0 || YTPlayerUtils.isStreamResolveBusy) return
        // Cheap in-memory checks FIRST: bail on a non-video / local / direct-URL track BEFORE paying for the
        // PerformanceMode reads in the first-toggle gate below (those only matter for a genuine video song).
        val id = service.player.currentMediaItem?.mediaId ?: return
        if (id.isEmpty() || id.isLocalMediaId() || id.startsWith("http", ignoreCase = true)) return
        if (service.player.currentMetadata?.isVideoSong != true) return
        // ALWAYS cap speculative resolves for the session (audio-only listeners + post-first-use alike).
        if (speculativeVideoPrefetches >= 8) return
        // Before the user has opened video once: only capable devices pay the speculative cipher cost.
        if (!service.userHasUsedVideo) {
            val perfMode = iad1tya.echo.music.utils.PerformanceMode.isOn(service)
            val tier = iad1tya.echo.music.utils.PerformanceMode.effectiveTier(service)
            val capable = !perfMode &&
                tier != iad1tya.echo.music.utils.DeviceTier.LOW &&
                tier != iad1tya.echo.music.utils.DeviceTier.ULTRA
            if (!capable) return
        }
        // Already resolved and still fresh → the toggle is already instant; nothing to do.
        val cached = MusicService.videoUrlCache[id]?.takeIf { it.second > System.currentTimeMillis() }?.first
        if (!cached.isNullOrEmpty()) return
        if (!prebuildingIds.add(id)) return // a resolve for this id is already in flight (dedupe)
        speculativeVideoPrefetches++
        service.scope.launch(Dispatchers.IO) {
            try {
                // Bail if audio resolve grabbed the locks while we were queued.
                if (service.audioStreamResolveInFlight.get() > 0 || YTPlayerUtils.isStreamResolveBusy) return@launch
                if (iad1tya.echo.music.utils.ThermalManager.isHot.value) return@launch
                val maxH = videoModeMaxHeight
                var url: String? = null
                // PipePipe FIRST (mirrors applyVideoToCurrent): the fast, unburned SimpMusic
                // provider — InnerTube only as fallback for devices where it still resolves.
                val picked = YTPlayerUtils.adaptiveVideoStreamNewPipe(id, service.connectivityManager, maxH).getOrNull()
                if (picked != null && !picked.first.isNullOrEmpty()) {
                    url = picked.first
                    if (picked.second) newPipeMuxedVideoIds.add(id) else newPipeMuxedVideoIds.remove(id)
                } else {
                    url = runCatching { YTPlayerUtils.videoStreamUrl(id, service.connectivityManager, maxH) }.getOrNull()
                    // TV robustness: if 1080p came back empty, fall back to the default resolution (matches
                    // applyVideoToCurrent / prebuildNextVideoItem) so the pre-resolved URL is never black-screened.
                    if (url.isNullOrEmpty() && maxH != null) {
                        url = runCatching { YTPlayerUtils.videoStreamUrl(id, service.connectivityManager, null) }.getOrNull()
                    }
                    if (!url.isNullOrEmpty()) {
                        newPipeMuxedVideoIds.remove(id)
                    }
                }
                val resolved = url
                // Same TTL as the on-demand resolve → applyVideoToCurrent's cache read accepts it as fresh.
                if (!resolved.isNullOrEmpty()) {
                    MusicService.videoUrlCache[id] = resolved to VideoModePlanning.videoUrlCacheExpiry(System.currentTimeMillis())
                    // If the user is looking at the expanded player right now, also warm the connection
                    // (fully re-gated inside: unmetered + capable + video song + once per URL) and attempt
                    // the instant-swap pre-prepare (same trigger moment; delayed so it never competes with
                    // the just-started track's own buffering; every hard gate re-checked at fire time).
                    if (service.playerSheetExpanded) {
                        withContext(Dispatchers.Main) {
                            service.maybeWarmVideoConnection()
                            service.scheduleInstantVideoPrepare(MusicService.INSTANT_VIDEO_PREPARE_DELAY_MS)
                        }
                    }
                }
            } finally {
                prebuildingIds.remove(id)
            }
        }
    }

    /** Leaves video mode: restore the current track to audio (playback continues at the same position).
     *  The video→audio path itself is UNTOUCHED by the instant-swap feature; the teardown below only
     *  releases a speculative pre-player (normally none exists while video mode is on — defensive), and
     *  the trailing schedule merely re-arms the speculative pre-prepare for a possible re-toggle. */
    fun exitVideoMode() {
        if (!service._videoMode.value && service.videoModeMediaId == null && videoModeItems.isEmpty()) return
        service.userExplicitlyExitedVideo = true
        stickyVideoPreferred = false
        videoSwapGeneration.incrementAndGet()
        videoStuckRecoveryJob?.cancel()
        service.teardownInstantVideoSwap("exit video mode")
        val leavingId = service.player.currentMediaItem?.mediaId
        leavingId?.let { service.resetRetryCount(it) }
        service._videoMode.value = false
        service._videoUrl.value = null
        prebuildingIds.clear()
        restoreVideoTracksExcept(null)   // restore ALL tracked video items (current + any pre-built) to audio
        // Resume offline pipeline + flush any download deferred while watching.
        resumeOfflineDownloadsAfterVideoPlayback()
        runCatching { flushAllPendingSongDownloads(service) }
            .onFailure { Timber.tag(MusicService.TAG).w(it, "flush all pending song downloads failed") }
        // Re-arm the instant-swap pre-prepare (fully re-gated inside) so toggling video back on soon after
        // is instant again; delayed so it never competes with the audio restore's own re-prepare.
        if (service.playerSheetExpanded) service.scheduleInstantVideoPrepare(MusicService.INSTANT_VIDEO_PREPARE_DELAY_MS)
    }

    /**
     * Offline Exo downloads share the same network as the live video+audio merge. Pause them for the
     * whole video session so they cannot 403/bandwidth-fight the playing mux (owner hitch reports).
     */
    internal fun pauseOfflineDownloadsForVideoPlayback() {
        runCatching {
            DownloadService.sendPauseDownloads(service, ExoDownloadService::class.java, false)
        }.onFailure { Timber.tag(MusicService.TAG).w(it, "pause downloads for video failed") }
    }

    private fun resumeOfflineDownloadsAfterVideoPlayback() {
        runCatching {
            DownloadService.sendResumeDownloads(service, ExoDownloadService::class.java, false)
        }.onFailure { Timber.tag(MusicService.TAG).w(it, "resume downloads after video failed") }
    }
}
