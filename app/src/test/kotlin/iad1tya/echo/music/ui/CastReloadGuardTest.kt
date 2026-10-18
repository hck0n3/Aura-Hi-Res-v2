package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 446 (owner 2026-10-18, Cast): the TV went black with sound, then flipped back to music by itself, a skipped
 * track came back to the previous one, and the timeline did not continue. The Cast handler lives in the gms flavour (not
 * compiled by the FOSS unit-test job), so these read the source: each fix is pinned where it lives.
 */
class CastReloadGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private val cast get() = src("app/src/gms/kotlin/com/music/echo/playback/CastConnectionHandler.kt")

    @Test
    fun `the local re-prepare of the track the TV shows as video does not leave video mode`() {
        val service = src("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        assertTrue(service.contains("videoCoordinator.isCastVideoTrack(mediaItem.mediaId)"))
        val coordinator = src("app/src/main/kotlin/com/music/echo/playback/VideoModeCoordinator.kt")
        assertTrue(coordinator.contains("internal var castVideoMediaId: String? = null"))
        // armed when the switch goes to Video under Cast, forgotten when video mode ends or the session does
        val arm = coordinator.substringAfter("private fun armVideoForCast()").substringBefore("}\n")
        assertTrue(arm.contains("castVideoMediaId = service.player.currentMediaItem?.mediaId"))
        assertTrue(coordinator.substringAfter("fun exitVideoMode()").substringBefore("service.clearSoftVideoState()").contains("castVideoMediaId = null"))
        assertTrue(coordinator.substringAfter("fun reapplyVideoAfterCast()").take(120).contains("castVideoMediaId = null"))
        assertTrue(cast.contains("musicService.videoCoordinator.castVideoMediaId = metadata.id"))
    }

    @Test
    fun `a mode flip never drags the TV back to a track it has left`() {
        val reconcile = cast.substringAfter("private fun reconcileVideoMode()").substringBefore("private fun loadMediaWithQueue")
        assertTrue(reconcile.contains("val tvTrack = loadedCurrentMediaId"))
        assertTrue(reconcile.contains("current.id != tvTrack"))
    }

    @Test
    fun `an unchanged TV is not reloaded and a reload keeps the TV's own position`() {
        val loadMedia = cast.substringAfter("fun loadMedia(metadata: AppMediaMetadata)").substringBefore("private fun wantsVideo")
        assertTrue(loadMedia.contains("metadata.id == loadedCurrentMediaId && wantsVideo(metadata.id) == loadedCurrentAsVideo"))
        val load = cast.substringAfter("private fun loadMediaWithQueue").substringBefore("fun play()")
        assertTrue(load.contains("val tvHadTrack = loadedCurrentMediaId == metadata.id"))
        assertTrue(load.contains("tvPosition?.takeIf { tvHadTrack }"))
    }

    @Test
    fun `the queue around the current track is built concurrently, not one resolve after another`() {
        val load = cast.substringAfter("private fun loadMediaWithQueue").substringBefore("fun play()")
        assertTrue(load.contains("coroutineScope {"))
        assertTrue(load.contains("awaitAll()"))
        assertFalse("la construcción secuencial volvería a tardar ~10 s", load.contains("for (prevItem in prevItems)"))
    }

    @Test
    fun `the shared log can say why a TV went black`() {
        assertTrue(cast.contains("\"CAST_ERROR type=%s reason=%s detail=%s asVideo=%b\""))
        assertTrue(cast.contains("\"CAST_STATE state=%s idleReason=%s video=%b items=%d\""))
        assertTrue(cast.contains("\"CAST_LOAD items=%d wantVideo=%b fellBack=%b start=%ds\""))
    }
}
