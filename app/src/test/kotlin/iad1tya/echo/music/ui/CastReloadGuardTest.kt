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

    // ── Registry row 449 (owner 2026-10-19): the video never showed (black), the app flipped back to Song on its own and the
    //    timeline did not advance. The Cast handler is a gms-only file, so each piece is pinned at its source.

    @Test
    fun `a video URL is only handed to the TV after rehearsing the TV's own fetch, and falls back to the phone relay`() {
        val resolve = cast.substringAfter("private suspend fun resolveCastVideoUrl").substringBefore("private var relay")
        assertTrue("cada fuente pasa por el ensayo", resolve.contains("deliverableVideoUrl(fromNewPipe, \"newpipe\")") &&
            resolve.contains("deliverableVideoUrl(fromInnerTube, \"innertube\")"))
        val deliverable = resolve.substringAfter("private fun deliverableVideoUrl")
        assertTrue("1.º: como lo pide la tele (su User-Agent)", deliverable.contains("CastVideoProbe.RECEIVER_USER_AGENT"))
        assertTrue("2.º: como lo pide el teléfono (UA por cliente)", deliverable.contains("CastVideoProbe.probe(musicService.videoOkHttpClient, url, null)"))
        assertTrue("3.º: lo que el teléfono sí puede leer lo retransmite él", deliverable.contains("relayFor(url)"))
        assertTrue("una URL que no se puede leer de ningún modo no se manda", deliverable.contains("if (!viaPhone.playable) return null"))
        assertTrue(cast.contains("CastMediaRelay(musicService.videoOkHttpClient, address)"))
        // closed with the session and when a load is not video
        assertTrue(cast.substringAfter("override fun onSessionEnded").substringBefore("override fun onSessionResuming").contains("closeRelay()"))
        assertTrue(cast.substringAfter("fun release()").contains("closeRelay()"))
        assertTrue(cast.contains("if (!loadedCurrentAsVideo) closeRelay()"))
    }

    @Test
    fun `the log says which way the video reached the TV without ever writing a URL or a title`() {
        assertTrue(cast.contains("\"CAST_VIDEO_SRC source=%s way=receiver status=%d type=%s kb=%d ms=%d\""))
        assertTrue(cast.contains("\"CAST_VIDEO_SRC source=%s way=phone status=%d type=%s kb=%d ms=%d\""))
        assertTrue(cast.contains("\"CAST_VIDEO_SRC source=%s relay=%b\""))
        assertTrue(cast.contains("\"CAST_VIDEO_FAILED why=%s\""))
        // No CAST_* line interpolates anything ($) and none of them passes the url/title as an argument
        val lines = cast.lines()
        lines.forEachIndexed { i, line ->
            if (line.contains("\"CAST_")) {
                assertFalse("línea ${i + 1} interpola datos", line.contains("\$"))
                val args = lines.drop(i + 1).take(3).joinToString(" ")
                assertFalse("línea ${i + 1} registra la URL", Regex("\\b(url|videoUrl|fromNewPipe|fromInnerTube|relayed)\\b[,)]").containsMatchIn(args))
                assertFalse("línea ${i + 1} registra el título", args.contains(".title"))
            }
        }
    }

    @Test
    fun `a video the TV cannot play puts the app back on Song with the audio instead of following the receiver to another track`() {
        // (a) IDLE with reason ERROR on the very item loaded as video
        val status = cast.substringAfter("override fun onStatusUpdated()").substringBefore("override fun onMediaError")
        assertTrue(status.contains("mediaStatus.idleReason == MediaStatus.IDLE_REASON_ERROR"))
        assertTrue(status.contains("erroredId == failedId"))
        assertFalse("la respuesta rápida de una URL rechazada cae dentro de la ventana de recarga", status.substringAfter("mediaStatus.idleReason == MediaStatus.IDLE_REASON_ERROR").substringBefore(") {").contains("isReloadingQueue"))
        // (b) the receiver skipped on from a video that never played, and the phone did not ask for it
        val advance = cast.substringAfter("private fun advancedFromUnplayedVideo").substringBefore("private fun handleCastItemChanged")
        assertTrue(advance.contains("videoItemPlayed"))
        assertTrue(advance.contains("PHONE_SKIP_GRACE_MS"))
        assertTrue(advance.contains("VIDEO_START_GRACE_MS"))
        assertTrue(status.contains("!advancedFromFailedVideo && currentItemId != -1"))
        for (skip in listOf("client.queueNext(", "client.queuePrev(", "client.queueJumpToItem(")) {
            val before = cast.substringBefore(skip).takeLast(160)
            assertTrue("$skip marca el salto como del teléfono", before.contains("lastPhoneSkipAtMs = android.os.SystemClock.elapsedRealtime()"))
        }
        // the recovery: toast, audio of the SAME track from where the video was, switch back to Song
        val recover = cast.substringAfter("private fun recoverFromFailedCastVideo").substringBefore("private fun reconcileVideoMode")
        assertTrue(recover.contains("cast_video_unavailable"))
        assertTrue(recover.contains("forceAudio = true"))
        assertTrue(recover.contains("musicService.exitVideoMode()"))
        assertTrue("una sola recuperación por carga", recover.contains("if (videoFailureHandled) return"))
        assertTrue(cast.contains("val wantVideo = !forceAudio && wantsVideo(metadata.id)"))
    }

    @Test
    fun `the timeline follows the TV the moment its state changes and the ticker only reads a playing TV`() {
        val status = cast.substringAfter("override fun onStatusUpdated()").substringBefore("override fun onMediaError")
        assertTrue(status.contains("_castPosition.value = client.approximateStreamPosition"))
        val ticker = cast.substringAfter("private fun startPositionUpdates()").substringBefore("private fun stopPositionUpdates()")
        assertTrue(ticker.contains("client.mediaStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING"))
        assertTrue("un solo ticker de 500 ms, no por fotograma", ticker.contains("delay(500)") && ticker.split("scope.launch").size == 2)
    }

    @Test
    fun `the Cast handler object exists from service creation so nothing captures null`() {
        val service = src("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val onCreate = service.substringAfter("override fun onCreate() {").substringBefore("setListener(object : Listener")
        assertTrue(onCreate.contains("castConnectionHandler = CastConnectionHandler(this, scope, this)"))
        val init = service.substringAfter("private fun initializeCast()").substringBefore("override fun onPositionDiscontinuity")
        assertTrue("GMS sigue sin arrancar hasta la primera reproducción", init.contains("castConnectionHandler?.initialize()"))
        assertTrue(init.contains("if (castConnectionHandler == null)"))
    }
}
