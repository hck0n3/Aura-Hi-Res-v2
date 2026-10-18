package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 389 (owner 2026-10-15, third report): «cuando cambio entre música y vídeo o viceversa no es tan
 * rápido como YouTube Music».
 *
 * Video → Song used to rebuild the playing item (new source + prepare(): the audio halts and refills its start gate).
 * The soft exit switches the video renderer off and leaves the merged source — and the audio — alone. These pin the
 * cases where it must NOT be used (the rebuild is the correct exit there) and that the toggle really goes through it.
 */
class SoftVideoExitTest {
    private fun allowed(
        enabled: Boolean = true,
        isMuxedPodcast: Boolean = false,
        hasSeparateAudioSource: Boolean = true,
        isMuxedStream: Boolean = false,
        onlyCurrentItemTracked: Boolean = true,
        crossfadeLive: Boolean = false,
        casting: Boolean = false,
        playerReady: Boolean = true,
        isLocalExportedFile: Boolean = false,
    ) = VideoModePlanning.softExitAllowed(
        enabled, isMuxedPodcast, hasSeparateAudioSource, isMuxedStream,
        onlyCurrentItemTracked, crossfadeLive, casting, playerReady, isLocalExportedFile,
    )

    @Test
    fun `the plain case — YouTube video-only plus the app's own audio, healthy player — goes soft`() {
        assertTrue(allowed())
    }

    @Test
    fun `a muxed stream keeps the rebuild because its audio is lower quality than the normal stream`() {
        assertFalse(allowed(isMuxedStream = true))
        assertFalse(allowed(isMuxedPodcast = true))
        assertFalse(allowed(hasSeparateAudioSource = false))
    }

    @Test
    fun `an exported local video goes soft too — its audio is the best one, so there is nothing to rebuild`() {
        // Owner 2026-10-18: a cut when switching Song <-> Video on an exported video, offline.
        assertTrue(allowed(isMuxedPodcast = true, isLocalExportedFile = true))
        assertTrue(allowed(isMuxedStream = true, isLocalExportedFile = true))
    }

    @Test
    fun `an exported video still keeps the rebuild whenever any other doubt holds`() {
        assertFalse(allowed(isLocalExportedFile = true, enabled = false))
        assertFalse(allowed(isLocalExportedFile = true, onlyCurrentItemTracked = false))
        assertFalse(allowed(isLocalExportedFile = true, crossfadeLive = true))
        assertFalse(allowed(isLocalExportedFile = true, casting = true))
        assertFalse(allowed(isLocalExportedFile = true, playerReady = false))
    }

    @Test
    fun `any doubt keeps the rebuild`() {
        assertFalse(allowed(enabled = false))
        assertFalse(allowed(onlyCurrentItemTracked = false))
        assertFalse(allowed(crossfadeLive = true))
        assertFalse(allowed(casting = true))
        assertFalse(allowed(playerReady = false))
    }

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun playback(name: String): String {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/playback/$name")
        assertTrue("$name se movió — actualiza este test", f.isFile)
        return f.readText()
    }

    @Test
    fun `the toggle takes the soft exit first and re-entry re-enables instead of rebuilding`() {
        val coordinator = playback("VideoModeCoordinator.kt")
        assertTrue(coordinator.contains("if (!softExitToSong()) exitVideoMode()"))
        assertTrue(coordinator.contains("if (service.trySoftVideoReenter()) return"))
        // The hard exit forgets the soft state before it rebuilds.
        val hard = coordinator.substring(coordinator.indexOf("fun exitVideoMode()"))
        assertTrue(hard.indexOf("service.clearSoftVideoState()") in 0 until hard.indexOf("restoreVideoTracksExcept(null)"))
    }

    @Test
    fun `the soft item is rebuilt as audio when another track is current and after a pause`() {
        val service = playback("MusicService.kt")
        val advance = service.substring(service.indexOf("internal fun applyAutoAdvanceSideEffects()"))
        assertTrue(advance.take(900).contains("hardRestoreSoftVideo(\"another track is current\")"))
        assertTrue(service.contains("scheduleSoftVideoRestoreOnPause(playWhenReady)"))
        // An error while soft is an AUDIO failure: leave the soft state before the video classification runs.
        val error = service.substring(service.indexOf("override fun onPlayerError(error: PlaybackException) {\n        super.onPlayerError"))
        assertTrue(error.indexOf("hardRestoreSoftVideo(\"player error while soft\")") in 0 until error.indexOf("VideoModePlanning.isVideoFailure"))
        // The video track type is what is switched, nothing else.
        assertTrue(service.contains("setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)"))
    }

    @Test
    fun `the first toggle no longer pays a cold resolve because a busy audio resolve ended the prefetch for good`() {
        val coordinator = playback("VideoModeCoordinator.kt")
        assertTrue(coordinator.contains("schedulePrefetchRetry(id)"))
        val service = playback("MusicService.kt")
        // Opening the player is the moment the toggle becomes plausible.
        val expanded = service.substring(service.indexOf("fun setPlayerSheetExpanded(expanded: Boolean)"))
        assertTrue(expanded.take(1500).contains("videoCoordinator.prefetchCurrentVideoUrl()"))
    }
}
