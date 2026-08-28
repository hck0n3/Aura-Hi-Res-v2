package iad1tya.echo.music.playback

import iad1tya.echo.music.playback.VideoModePlanning.ApplyAction
import iad1tya.echo.music.playback.VideoModePlanning.ApplySnapshot
import iad1tya.echo.music.utils.DeviceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization locks for the video-mode decision regions (RONDA 3 FASE B paso 2). Every value
 * is the CURRENT behavior of MusicService — the podcast/exported/offline/sticky-video fixes are
 * regression-registry material. Any drift must fail here before it ships.
 */
class VideoModePlanningTest {

    // ── region 1: entry gate ────────────────────────────────────────────────────────────────────

    @Test
    fun `perf mode blocks implicit entries but never an explicit tap or tv-car`() {
        assertFalse(VideoModePlanning.videoEntryAllowed(false, true, false))
        assertTrue(VideoModePlanning.videoEntryAllowed(true, true, false))
        assertTrue(VideoModePlanning.videoEntryAllowed(false, true, true))
        assertTrue(VideoModePlanning.videoEntryAllowed(false, false, false))
    }

    // ── region 2: TV bandwidth-aware height ─────────────────────────────────────────────────────

    @Test
    fun `tv height tiers - 1080 only for a genuinely strong measured link`() {
        assertEquals(1080, VideoModePlanning.bandwidthAwareVideoHeight(8_000_000L))
        assertEquals(1080, VideoModePlanning.bandwidthAwareVideoHeight(50_000_000L))
        assertEquals(720, VideoModePlanning.bandwidthAwareVideoHeight(7_999_999L))
        assertEquals(720, VideoModePlanning.bandwidthAwareVideoHeight(4_000_000L)) // cold synthetic estimate
        assertEquals(480, VideoModePlanning.bandwidthAwareVideoHeight(3_499_999L))
        assertEquals(480, VideoModePlanning.bandwidthAwareVideoHeight(1_500_000L))
        assertEquals(360, VideoModePlanning.bandwidthAwareVideoHeight(1_499_999L))
        assertEquals(360, VideoModePlanning.bandwidthAwareVideoHeight(0L))
    }

    // ── region 3: tier maxVideoDim ──────────────────────────────────────────────────────────────

    @Test
    fun `tv raises ultra-low cap to 1920 so the 1080 track is never rejected`() {
        assertEquals(1920, VideoModePlanning.maxVideoDimForTier(DeviceTier.ULTRA, true))
        assertEquals(1920, VideoModePlanning.maxVideoDimForTier(DeviceTier.LOW, true))
        assertEquals(1280, VideoModePlanning.maxVideoDimForTier(DeviceTier.ULTRA, false))
        assertEquals(1280, VideoModePlanning.maxVideoDimForTier(DeviceTier.LOW, false))
        assertEquals(1920, VideoModePlanning.maxVideoDimForTier(DeviceTier.MID, false))
        assertEquals(Int.MAX_VALUE, VideoModePlanning.maxVideoDimForTier(DeviceTier.HIGH, false))
    }

    // ── region 4a: URL cache ────────────────────────────────────────────────────────────────────

    @Test
    fun `url cache hit only while the expiry is in the future`() {
        val now = 1_000_000L
        assertEquals("u", VideoModePlanning.cachedVideoUrl("u" to now + 1, now))
        assertNull(VideoModePlanning.cachedVideoUrl("u" to now, now)) // expiry == now is stale
        assertNull(VideoModePlanning.cachedVideoUrl("u" to now - 1, now))
        assertNull(VideoModePlanning.cachedVideoUrl(null, now))
    }

    @Test
    fun `url cache expiry is the 5 minute ttl`() {
        assertEquals(1_300_000L, VideoModePlanning.videoUrlCacheExpiry(1_000_000L))
    }

    // ── region 5: error classification ──────────────────────────────────────────────────────────

    @Test
    fun `video failure when mode on id matches or item tracked`() {
        assertTrue(VideoModePlanning.isVideoFailure("x", true, null, false))
        assertTrue(VideoModePlanning.isVideoFailure("x", false, "x", false))
        assertTrue(VideoModePlanning.isVideoFailure("x", false, null, true))
        assertFalse(VideoModePlanning.isVideoFailure("x", false, "y", false))
        assertFalse(VideoModePlanning.isVideoFailure(null, false, null, true)) // null id never tracked
        assertFalse(VideoModePlanning.isVideoFailure(null, false, "x", false))
    }

    // ── region 4b: download completeness ────────────────────────────────────────────────────────

    @Test
    fun `download cache complete only with a positive known length`() {
        assertTrue(VideoModePlanning.videoDownloadCacheComplete(1L))
        assertFalse(VideoModePlanning.videoDownloadCacheComplete(0L))
        assertFalse(VideoModePlanning.videoDownloadCacheComplete(VideoModePlanning.LENGTH_UNSET))
    }

    // ── region 4c/4d: resolve fallbacks ─────────────────────────────────────────────────────────

    @Test
    fun `height-capped selection retries with the default cap only when it failed`() {
        assertTrue(VideoModePlanning.shouldRetryWithoutHeightCap(1080, null))
        assertTrue(VideoModePlanning.shouldRetryWithoutHeightCap(1080, ""))
        assertFalse(VideoModePlanning.shouldRetryWithoutHeightCap(1080, "url"))
        assertFalse(VideoModePlanning.shouldRetryWithoutHeightCap(null, null)) // phone path: no cap, no retry
    }

    @Test
    fun `muxed flag follows pipewire truth and is cleared by innertube`() {
        assertTrue(VideoModePlanning.muxedFlagAfterResolve(fromPipePipe = true, pipePipeMuxed = true))
        assertFalse(VideoModePlanning.muxedFlagAfterResolve(fromPipePipe = true, pipePipeMuxed = false))
        assertFalse(VideoModePlanning.muxedFlagAfterResolve(fromPipePipe = false, pipePipeMuxed = true))
    }

    // ── region 6: stuck recovery ────────────────────────────────────────────────────────────────

    @Test
    fun `stuck recovery only in idle with url armed and debounce elapsed`() {
        assertTrue(
            VideoModePlanning.stuckVideoShouldReprepare(
                videoModeOn = true, videoUrlPresent = true, playWhenReady = true,
                isStateIdle = true, currentIdMatchesVideoMediaId = true,
                msSinceLastAttempt = 30_000L
            )
        )
        assertFalse(
            VideoModePlanning.stuckVideoShouldReprepare(
                videoModeOn = true, videoUrlPresent = true, playWhenReady = true,
                isStateIdle = true, currentIdMatchesVideoMediaId = true,
                msSinceLastAttempt = 29_999L
            )
        )
        // BUFFERING is a normal rebuffer, NOT stuck (0.6.161).
        assertFalse(
            VideoModePlanning.stuckVideoShouldReprepare(
                videoModeOn = true, videoUrlPresent = true, playWhenReady = true,
                isStateIdle = false, currentIdMatchesVideoMediaId = true,
                msSinceLastAttempt = 60_000L
            )
        )
        assertFalse(
            VideoModePlanning.stuckVideoShouldReprepare(
                videoModeOn = false, videoUrlPresent = true, playWhenReady = true,
                isStateIdle = true, currentIdMatchesVideoMediaId = true,
                msSinceLastAttempt = 60_000L
            )
        )
    }

    // ── region 4e: applyVideoToCurrent tree ─────────────────────────────────────────────────────

    private fun snapshot(
        podcastVideoUrl: String? = null,
        exportedVideoUri: String? = null,
        registeredExportedMissingFile: Boolean = false,
        isHttpOrLocalId: Boolean = false,
        forceExplicit: Boolean = false,
        isVideoSong: Boolean = true,
        offlineOnly: Boolean = false,
        videoDownloaded: Boolean = false,
        cachedUrl: String? = null,
        armModeWhenReady: Boolean = false,
        videoModeOn: Boolean = true,
        muxedFlagForId: Boolean = false,
        offlineCacheUri: String = "echo-cache-video://id",
    ) = ApplySnapshot(
        podcastVideoUrl, exportedVideoUri, registeredExportedMissingFile, isHttpOrLocalId,
        forceExplicit, isVideoSong, offlineOnly, videoDownloaded, cachedUrl,
        armModeWhenReady, videoModeOn, muxedFlagForId, offlineCacheUri
    )

    @Test
    fun `podcast swaps immediately - the only branch with no video-mode gate`() {
        val a = VideoModePlanning.decideApply(snapshot(podcastVideoUrl = "http://pod.mp4", videoModeOn = false))
        assertEquals(ApplyAction.Swap("http://pod.mp4", isMuxed = true, armModeFirst = false), a)
        val b = VideoModePlanning.decideApply(
            snapshot(podcastVideoUrl = "http://pod.mp4", armModeWhenReady = true, videoModeOn = false)
        )
        assertEquals(ApplyAction.Swap("http://pod.mp4", isMuxed = true, armModeFirst = true), b)
    }

    @Test
    fun `exported mp4 swaps when armed or already on - noop when lazily off`() {
        val on = VideoModePlanning.decideApply(snapshot(exportedVideoUri = "file://v.mp4"))
        assertEquals(ApplyAction.Swap("file://v.mp4", isMuxed = true, armModeFirst = false), on)
        val arm = VideoModePlanning.decideApply(
            snapshot(exportedVideoUri = "file://v.mp4", armModeWhenReady = true, videoModeOn = false)
        )
        assertEquals(ApplyAction.Swap("file://v.mp4", isMuxed = true, armModeFirst = true), arm)
        val off = VideoModePlanning.decideApply(snapshot(exportedVideoUri = "file://v.mp4", videoModeOn = false))
        assertEquals(ApplyAction.NoOp, off)
    }

    @Test
    fun `missing exported file disarms with honest toast - never an online resolve`() {
        val toast = VideoModePlanning.decideApply(snapshot(registeredExportedMissingFile = true))
        assertEquals(ApplyAction.DisarmMissingExportedFile(showToast = true), toast)
        val silent = VideoModePlanning.decideApply(
            snapshot(registeredExportedMissingFile = true, videoModeOn = false, armModeWhenReady = false)
        )
        assertEquals(ApplyAction.DisarmMissingExportedFile(showToast = false), silent)
    }

    @Test
    fun `http or local ids and non-video songs disarm silently - explicit tap overrides the song gate`() {
        assertEquals(ApplyAction.DisarmKeepAudio, VideoModePlanning.decideApply(snapshot(isHttpOrLocalId = true)))
        assertEquals(ApplyAction.DisarmKeepAudio, VideoModePlanning.decideApply(snapshot(isVideoSong = false)))
        // forceExplicit passes the song gate and falls through to the resolve path.
        assertEquals(
            ApplyAction.LiveResolve,
            VideoModePlanning.decideApply(snapshot(isVideoSong = false, forceExplicit = true))
        )
    }

    @Test
    fun `offline without the download disarms - toast only when already in video mode`() {
        val toast = VideoModePlanning.decideApply(snapshot(offlineOnly = true, videoDownloaded = false))
        assertEquals(ApplyAction.DisarmOfflineNotDownloaded(showToast = true), toast)
        val lazySilent = VideoModePlanning.decideApply(
            snapshot(offlineOnly = true, videoDownloaded = false, armModeWhenReady = true)
        )
        assertEquals(ApplyAction.DisarmOfflineNotDownloaded(showToast = false), lazySilent)
    }

    @Test
    fun `downloaded video swaps from the cache uri - noop when lazily off`() {
        val on = VideoModePlanning.decideApply(snapshot(videoDownloaded = true))
        assertEquals(ApplyAction.Swap("echo-cache-video://id", isMuxed = false, armModeFirst = false), on)
        val off = VideoModePlanning.decideApply(snapshot(videoDownloaded = true, videoModeOn = false))
        assertEquals(ApplyAction.NoOp, off)
    }

    @Test
    fun `url cache hit swaps with the stored muxed flag - noop when lazily off`() {
        val hit = VideoModePlanning.decideApply(snapshot(cachedUrl = "https://v", muxedFlagForId = true))
        assertEquals(ApplyAction.Swap("https://v", isMuxed = true, armModeFirst = false), hit)
        val off = VideoModePlanning.decideApply(snapshot(cachedUrl = "https://v", videoModeOn = false))
        assertEquals(ApplyAction.NoOp, off)
    }

    @Test
    fun `nothing cached and online - live resolve`() {
        assertEquals(ApplyAction.LiveResolve, VideoModePlanning.decideApply(snapshot()))
    }

    @Test
    fun `branch precedence - podcast beats exported beats missing-file beats http beats song gate`() {
        val a = VideoModePlanning.decideApply(
            snapshot(podcastVideoUrl = "http://pod.mp4", exportedVideoUri = "file://v.mp4", isHttpOrLocalId = true)
        )
        assertTrue(a is ApplyAction.Swap && a.url == "http://pod.mp4")
        val b = VideoModePlanning.decideApply(
            snapshot(exportedVideoUri = "file://v.mp4", registeredExportedMissingFile = true, isHttpOrLocalId = true)
        )
        assertTrue(b is ApplyAction.Swap && b.url == "file://v.mp4")
        val c = VideoModePlanning.decideApply(
            snapshot(registeredExportedMissingFile = true, isHttpOrLocalId = true)
        )
        assertTrue(c is ApplyAction.DisarmMissingExportedFile)
        val d = VideoModePlanning.decideApply(snapshot(isHttpOrLocalId = true, isVideoSong = false))
        assertEquals(ApplyAction.DisarmKeepAudio, d)
    }
}
