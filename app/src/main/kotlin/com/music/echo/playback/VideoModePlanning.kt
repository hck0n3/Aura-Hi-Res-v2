package iad1tya.echo.music.playback

import iad1tya.echo.music.utils.DeviceTier

/**
 * Pure decision core for video mode extracted from [MusicService] (HALLAZGO-021, RONDA 3 FASE B
 * paso 2). Video mode is INTEGRATED into the main player (one engine): videoMode = sticky on/off
 * intent; the current track's source flips between its audio stream and its muxed/merged video
 * stream without ever pausing the music.
 *
 * The service keeps every side effect (flipping _videoMode, swapToVideo, toasts, disarming,
 * launching the live resolve, the URL cache map itself, the stuck-recovery job); this object only
 * decides WHAT to do given a snapshot of the state, so the decision tree that accumulated the
 * podcast/exported/offline/sticky-video fixes can be characterized without a device.
 *
 * The decisions pinned here:
 *  1. entry gate — High-Performance Mode blocks implicit entries but NEVER an explicit user tap,
 *     and TV/car devices are exempt from the block.
 *  2. TV bandwidth-aware height — 1080 only for a genuinely strong MEASURED link (≥8 Mbps; the
 *     cold synthetic estimate is ~4-8 Mbps and must land ≤720), ceiling 1080.
 *  3. tier maxVideoDim — aligned with (2): TV ULTRA/LOW raise the cap to 1920 so the 1080 track
 *     (1920 wide) is never silently REJECTED by the track selector (looked like an endless stall).
 *  4. applyVideoToCurrent tree — podcast → exported → missing-exported-file → http/local →
 *     not-a-video-song → offline/downloaded → URL cache → live resolve, each with its exact
 *     arm/NoOp/disarm semantics.
 *  5. error classification — a failure of the video track drops the stale URL and falls back to
 *     AUDIO instead of hitting retry-limit/stopOnError.
 *  6. stuck recovery — re-prepare at most once per 30s per id, ONLY in STATE_IDLE (BUFFERING is a
 *     normal rebuffer, not stuck — 0.6.161 "se traba y al rato continúa").
 */
object VideoModePlanning {

    // Mirror the media3 constant by value so the core stays Android-free in unit tests.
    const val LENGTH_UNSET = -1L // C.LENGTH_UNSET

    /** Resolved video URLs stay valid this long (stream expiry). */
    const val URL_CACHE_TTL_MS = 5 * 60 * 1000L

    /** Stuck-video recovery: at most one re-prepare per id per this window. */
    const val STUCK_RECOVERY_DEBOUNCE_MS = 30_000L

    /** Idle-only recovery waits this long for a transient IDLE→BUFFERING transition after a swap. */
    const val STUCK_RECOVERY_CHECK_DELAY_MS = 5_000L

    /** TV video-only request ceiling. */
    const val TV_HEIGHT_CEILING = 1080

    /** Region 1 — entry gate for [MusicService.enterVideoModeIfNeeded]. */
    fun videoEntryAllowed(
        forceFromUserTap: Boolean,
        performanceModeOn: Boolean,
        isTvOrCar: Boolean,
    ): Boolean = forceFromUserTap || !performanceModeOn || isTvOrCar

    /**
     * Region 2 — TV bandwidth-aware video-only target height. DefaultBandwidthMeter.bitrateEstimate
     * never returns 0 — before any real transfer it hands back a synthetic INITIAL estimate
     * (~4-8 Mbps), so the 1080 gate is raised to 8 Mbps: only a genuinely strong, measured
     * estimate yields 1080; a cold synthetic estimate lands ≤720 (safe).
     */
    fun bandwidthAwareVideoHeight(estimateBps: Long): Int = when {
        estimateBps >= 8_000_000L -> 1080
        estimateBps >= 3_500_000L -> 720
        estimateBps >= 1_500_000L -> 480
        else -> 360
    }.coerceAtMost(TV_HEIGHT_CEILING)

    /**
     * Region 3 — maxVideoDim for the video track selector. On TV the video path can request up to
     * 1080p (1920 wide); a 1280 cap would silently REJECT that track → no video renders and it
     * looks like an endless network stall. So on TV, ULTRA/LOW raise to 1920; non-TV low tiers
     * keep the tighter 1280 cap. HIGH is uncapped.
     */
    fun maxVideoDimForTier(tier: DeviceTier, isTv: Boolean): Int = when (tier) {
        DeviceTier.ULTRA -> if (isTv) 1920 else 1280
        DeviceTier.LOW -> if (isTv) 1920 else 1280
        DeviceTier.MID -> 1920
        DeviceTier.HIGH -> Int.MAX_VALUE
    }

    /** Region 4a — the video URL cache read: an entry is a hit only while its expiry is in the future. */
    fun cachedVideoUrl(entry: Pair<String, Long>?, nowMs: Long): String? =
        entry?.takeIf { it.second > nowMs }?.first

    /** Region 4a — the video URL cache write expiry. */
    fun videoUrlCacheExpiry(resolvedAtMs: Long): Long = resolvedAtMs + URL_CACHE_TTL_MS

    /**
     * Region 5 — error classification. The failing item is the video track when video mode is on,
     * OR the id is the one currently swapped to video, OR it is a tracked/pre-built video item.
     * Such failures fall back to audio; they must never hit retry-limit/stopOnError.
     */
    fun isVideoFailure(
        mediaId: String?,
        videoModeOn: Boolean,
        videoModeMediaId: String?,
        isTrackedVideoItem: Boolean,
    ): Boolean = videoModeOn ||
        (videoModeMediaId != null && mediaId == videoModeMediaId) ||
        (mediaId != null && isTrackedVideoItem)

    /** Region 4b — download-cache completeness (the isCached range check stays in the service). */
    fun videoDownloadCacheComplete(cachedLength: Long): Boolean =
        cachedLength != LENGTH_UNSET && cachedLength > 0

    /** Region 4c — TV robustness: a failed height-capped selection retries with the default cap. */
    fun shouldRetryWithoutHeightCap(maxHeight: Int?, resolvedUrl: String?): Boolean =
        maxHeight != null && resolvedUrl.isNullOrEmpty()

    /** Region 4d — muxed-flag bookkeeping after a successful resolve. InnerTube (video-only merge
     *  path) must clear a stale muxed flag; PipePipe reports the truth. */
    fun muxedFlagAfterResolve(fromPipePipe: Boolean, pipePipeMuxed: Boolean): Boolean =
        if (fromPipePipe) pipePipeMuxed else false

    /** Region 6 — stuck-video recovery gate. BUFFERING is deliberately NOT stuck (0.6.161). */
    fun stuckVideoShouldReprepare(
        videoModeOn: Boolean,
        videoUrlPresent: Boolean,
        playWhenReady: Boolean,
        isStateIdle: Boolean,
        currentIdMatchesVideoMediaId: Boolean,
        msSinceLastAttempt: Long,
    ): Boolean = videoModeOn && videoUrlPresent && playWhenReady && isStateIdle &&
        currentIdMatchesVideoMediaId && msSinceLastAttempt >= STUCK_RECOVERY_DEBOUNCE_MS

    /**
     * Region 4e — the [MusicService.applyVideoToCurrent] decision tree. Snapshot of everything the
     * tree reads; [offlineCacheUri] is the pure `echo-cache-video://<id>` construction.
     */
    data class ApplySnapshot(
        val podcastVideoUrl: String?,
        val exportedVideoUri: String?,
        val registeredExportedMissingFile: Boolean,
        val isHttpOrLocalId: Boolean,
        val forceExplicit: Boolean,
        val isVideoSong: Boolean,
        val offlineOnly: Boolean,
        val videoDownloaded: Boolean,
        val cachedUrl: String?,
        val armModeWhenReady: Boolean,
        val videoModeOn: Boolean,
        val muxedFlagForId: Boolean,
        val offlineCacheUri: String,
    )

    sealed interface ApplyAction {
        /** Flip the source to [url]. [armModeFirst] = set videoMode=true before swapping. */
        data class Swap(val url: String, val isMuxed: Boolean, val armModeFirst: Boolean) : ApplyAction

        /** Silent disarm: no video possible for this item; keep playing audio, no toast. */
        data object DisarmKeepAudio : ApplyAction

        /** The id IS a registered exported video but its file is gone — honest outcome, no online resolve. */
        data class DisarmMissingExportedFile(val showToast: Boolean) : ApplyAction

        /** Offline-only (or downloaded-flag path) but the video is not actually downloaded. */
        data class DisarmOfflineNotDownloaded(val showToast: Boolean) : ApplyAction

        /** Nothing cached — go resolve the video URL live (audio keeps playing during resolve). */
        data object LiveResolve : ApplyAction

        /** Early return: the lazy-arm path found no reason to act (video mode off, nothing to do). */
        data object NoOp : ApplyAction
    }

    fun decideApply(s: ApplySnapshot): ApplyAction {
        // Video PODCAST episode: it already carries a direct video stream — swap immediately, no
        // YouTube resolution (the id here is an http audio URL, which the resolver can't handle).
        // Deliberately the ONLY branch with no videoModeOn gate.
        if (!s.podcastVideoUrl.isNullOrEmpty()) {
            return ApplyAction.Swap(s.podcastVideoUrl, isMuxed = true, armModeFirst = s.armModeWhenReady)
        }
        // Exported MP4 (Vídeos exportados): local muxed file — offline video without YT resolve.
        // Must run BEFORE the isVideoSong gate: library rows often lack that flag after export.
        if (!s.exportedVideoUri.isNullOrEmpty()) {
            if (!s.armModeWhenReady && !s.videoModeOn) return ApplyAction.NoOp
            return ApplyAction.Swap(s.exportedVideoUri, isMuxed = true, armModeFirst = s.armModeWhenReady)
        }
        // Listed as an exported video but the file is missing/moved — do NOT fall through to an
        // online resolve (that produced a confusing container-parse failure, owner CONTAINER_3003).
        if (s.registeredExportedMissingFile) {
            return ApplyAction.DisarmMissingExportedFile(showToast = s.armModeWhenReady || s.videoModeOn)
        }
        // A direct/local track with no video stream (audio-only podcast reached while sticky video
        // is still armed) can't show video — disarm silently (no stuck spinner over the cover).
        if (s.isHttpOrLocalId) return ApplyAction.DisarmKeepAudio
        // A YouTube track that is NOT a video song can't show video → sticky-video drops to audio
        // cleanly (no resolution attempt, no "Video falló" toast). An explicit user tap overrides.
        if (!s.forceExplicit && !s.isVideoSong) return ApplyAction.DisarmKeepAudio
        if (s.offlineOnly || s.videoDownloaded) {
            if (!s.videoDownloaded) {
                return ApplyAction.DisarmOfflineNotDownloaded(
                    showToast = !s.armModeWhenReady && s.videoModeOn
                )
            }
            if (!s.armModeWhenReady && !s.videoModeOn) return ApplyAction.NoOp
            return ApplyAction.Swap(s.offlineCacheUri, isMuxed = false, armModeFirst = s.armModeWhenReady)
        }
        // Verbatim dead branch (the block above returns on every offlineOnly path) — kept so the
        // characterization matches the service line for line.
        if (s.offlineOnly) return ApplyAction.NoOp
        if (!s.cachedUrl.isNullOrEmpty()) {
            if (!s.armModeWhenReady && !s.videoModeOn) return ApplyAction.NoOp
            return ApplyAction.Swap(s.cachedUrl, isMuxed = s.muxedFlagForId, armModeFirst = s.armModeWhenReady)
        }
        return ApplyAction.LiveResolve
    }
}
