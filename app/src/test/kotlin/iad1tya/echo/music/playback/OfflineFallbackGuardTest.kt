package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22: «que Aura detecte que no hay internet por cualquier motivo y pase el reproductor y su cola a
 * modo sin conexión, para que la siguiente canción nunca se quede cargando». Source-text checks on purpose: the
 * routing needs a real player, a real loader thread and a real loss of network, none of which runs in a JVM test.
 */
class OfflineFallbackGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val service by lazy { main("playback/MusicService.kt") }
    private val resolver by lazy { main("playback/StreamResolution.kt") }

    private val onPlayerError by lazy {
        val start = service.indexOf("override fun onPlayerError(error: PlaybackException) {\n        super.onPlayerError(error)")
        assertTrue("onPlayerError not found", start >= 0)
        val end = service.indexOf("\n    }\n", start)
        service.substring(start, end)
    }

    /** Both refusals of the resolver carry the marker cause, so onPlayerError can recognise them through media3's wrapping. */
    @Test
    fun `every offline refusal of the resolver carries its own marker`() {
        assertFalse(
            "a refusal without the marker is classified as a generic I/O error again",
            resolver.contains("getString(R.string.error_offline_not_downloaded),\n                    null,"),
        )
        assertFalse(resolver.contains("getString(R.string.error_offline_not_downloaded),\n                null,"))
        assertTrue(resolver.contains("OfflineRejectionException(directRejection)"))
        assertTrue(resolver.contains("OfflineRejectionException(offlineRejection)"))
    }

    private val rejectionHandler by lazy {
        service.substring(
            service.indexOf("private fun handleOfflineRejection(kind: OfflineRejectionException.Kind, mediaId: String?, error: PlaybackException) {"),
        ).substringBefore("\n    }\n")
    }

    /**
     * An offline refusal is routed BEFORE the retry limit and BEFORE the generic I/O fallthrough (whose handler purges the
     * song's bytes), and its handler never purges, never counts a retry of the song.
     */
    @Test
    fun `an offline refusal goes to the offline skip and never reaches the purge`() {
        val branchStart = onPlayerError.indexOf("handleOfflineRejection(offlineRejection, mediaId, error)")
        val retryLimit = onPlayerError.indexOf("if (mediaId != null && hasExceededRetryLimit(mediaId)) {")
        val generic = onPlayerError.indexOf("handleGenericIOError(mediaId)")
        assertTrue(branchStart > 0)
        assertTrue("before the retry limit", branchStart < retryLimit)
        assertTrue("the offline branch must come before the generic I/O handler", branchStart < generic)
        assertTrue(rejectionHandler.contains("continueWithOfflineQueue(stopIfNothing = kind == OfflineRejectionException.Kind.MANUAL)"))
        assertFalse(rejectionHandler.contains("performAggressiveCacheClear"))
        assertFalse(rejectionHandler.contains("incrementRetryCount"))
        assertFalse(rejectionHandler.contains("handleGenericIOError"))

        val helper = service.substring(service.indexOf("private fun continueWithOfflineQueue(stopIfNothing: Boolean) {"))
            .substringBefore("\n    }\n")
        assertTrue(helper.contains("findNextOfflinePlayableIndex()"))
        assertTrue(helper.contains("skipToOfflinePlayableSong(offlineTargetIndex)"))
        assertTrue(helper.contains("if (stopIfNothing) stopOnError() else waitOnNetworkError()"))
        assertFalse(helper.contains("performAggressiveCacheClear"))
    }

    // ---- the player's offline session ------------------------------------------------------------------

    @Test
    fun `the resolver refuses at once while the session is on and ends it on a fresh resolve`() {
        assertTrue(resolver.contains("-> OfflineRejectionException.Kind.SESSION"))
        assertTrue(resolver.contains("PlaybackOfflineSession.shouldRejectResolve("))
        val success = resolver.indexOf("StreamHealth.freshResolveCompleted(")
        assertTrue(success > 0)
        assertTrue(resolver.indexOf("noteNetworkResolveSucceeded()", success) in success until success + 300)
    }

    /** Order: only a real CONNECTIVITY failure feeds the session, before the bounded retry. */
    @Test
    fun `connectivity failures with evidence continue with the offline queue`() {
        val networkSession = onPlayerError.indexOf("PlaybackErrorClassifier.isConnectivityFailure(error) && noteOfflineConnectivityFailure(mediaId) -> {")
        val boundedRetry = onPlayerError.indexOf("PlaybackErrorClassifier.isNetworkRelatedError(error) -> {")
        val offline = onPlayerError.indexOf("!isNetworkConnected.value -> {")
        assertTrue(networkSession in 1 until offline)
        assertTrue(offline < boundedRetry)
        assertTrue(onPlayerError.substring(networkSession, offline).contains("continueWithOfflineQueue(stopIfNothing = false)"))
    }

    /**
     * C5: a YouTube-side "no stream" (resolver timeout, cipher, 429) is the unavailable-song verdict as before, never
     * evidence. Review 2026-11-23 (3): a "no stream" whose every client failed for lack of internet is NOT that verdict —
     * the branch steps aside and it takes the connectivity branches.
     */
    @Test
    fun `a no-stream failure never feeds the offline session`() {
        val verdict = "PlaybackErrorClassifier.isNoStreamError(error) && isNetworkConnected.value &&"
        val verdictAt = onPlayerError.indexOf(verdict)
        assertTrue(verdictAt > 0)
        val noStream = onPlayerError.substring(verdictAt).substringBefore("PlaybackErrorClassifier.isAudioRendererError(error) -> {")
        assertTrue(noStream.substringBefore("-> {").contains("!PlaybackErrorClassifier.isConnectivityFailure(error)"))
        assertFalse(noStream.contains("noteOffline"))
        assertFalse(onPlayerError.contains("offline_no_stream"))
        assertFalse("evidence is connectivity only", service.contains("validationLost"))
    }

    /**
     * C2: media3 resolves the next item minutes ahead, so a refusal can surface after its reason stopped holding. The
     * handler re-checks it LIVE and loads the item again (once) instead of skipping; "no network" is only noted when the
     * live check confirms it.
     */
    @Test
    fun `a stale refusal is re-checked live and retried instead of skipped`() {
        assertTrue(rejectionHandler.contains("OfflineRejectionException.Kind.MANUAL -> dataStore.get(OfflineModeKey, false)"))
        assertTrue(rejectionHandler.contains("OfflineRejectionException.Kind.NO_NETWORK -> !systemHasNetwork"))
        assertTrue(rejectionHandler.contains("OfflineRejectionException.Kind.SESSION -> PlaybackOfflineSession.shouldRejectResolve("))
        val stale = rejectionHandler.indexOf("if (!stillHolds && mediaId != null && staleRejectionRetryId != mediaId) {")
        val prepare = rejectionHandler.indexOf("player.prepare()", stale)
        val noted = rejectionHandler.indexOf("if (kind == OfflineRejectionException.Kind.NO_NETWORK) noteOfflineNoNetwork()")
        assertTrue(stale > 0 && prepare > stale)
        assertTrue("the session is only noted after the live check failed to clear it", noted > prepare)
        assertFalse("never resumes a pause", rejectionHandler.contains("player.play()"))
        assertTrue(service.contains("staleRejectionRetryId = null\n            maybeEndOfflineSessionOnReady()"))
    }

    @Test
    fun `entering and leaving are event-driven and say so once`() {
        assertTrue(service.contains("if (!isConnected && !hasLiveInternetConnection()) noteOfflineNoNetwork()"))
        assertTrue(service.contains("Timber.tag(TAG).i(\"OFFLINE_SESSION enter reason=%s\", reason.log)"))
        assertTrue(service.contains("Timber.tag(TAG).i(\"OFFLINE_SESSION exit reason=%s\", how)"))
        assertTrue(service.contains("R.string.offline_session_started") && service.contains("R.string.offline_session_ended"))
        // Leaving re-prepares a waiting queue with the reconnection semantics: only a NETWORK pause is resumed.
        assertTrue(service.contains("if (how != \"resolved\" && waitingForNetworkConnection.value) triggerRetry()"))
        for (values in listOf("values", "values-es")) {
            val strings = File(root, "app/src/main/res/$values/strings.xml").readText()
            assertTrue("$values", strings.contains("name=\"offline_session_started\"") && strings.contains("name=\"offline_session_ended\""))
        }
    }

    /** C4: every way back ends the session — no waiting for a false→true edge, no fresh-resolve-only exit. */
    @Test
    fun `every sign of a working network ends the session`() {
        assertTrue(service.contains("if (validated && PlaybackOfflineSession.onNetworkChanged()) onOfflineSessionExited(\"validated\")"))
        // (b) The counters' current value at collection is not an event: a cold start with no network keeps its session.
        assertTrue(service.contains("NetworkState.capsEpoch.drop(1).collect {"))
        assertTrue(service.contains("NetworkState.epoch.drop(1).collect {"))
        assertTrue(service.contains("if (PlaybackOfflineSession.onNetworkChanged()) onOfflineSessionExited(\"network_back\")"))
        val state = main("utils/NetworkState.kt")
        assertTrue("capsEpoch is bumped AFTER validated is re-read", state.contains("refresh()\n                _capsEpoch.update { it + 1 }"))
        // A network item that plays — a cached URL included — proves the internet works.
        val ready = service.substring(service.indexOf("private fun maybeEndOfflineSessionOnReady() {")).substringBefore("\n    }\n")
        // (2) + (f): clears the failures with no session too; O(1) when there is nothing to clear; cache work on IO.
        assertTrue(ready.contains("if (!PlaybackOfflineSession.hasEvidence()) return"))
        assertTrue(ready.contains("withContext(Dispatchers.IO) {"))
        assertTrue(ready.contains("offlinePlayableSource(mediaId, exportedUris) != null"))
        assertTrue(ready.contains("if (playsOffline) return@launch"))
        assertTrue(ready.contains("if (PlaybackOfflineSession.onNetworkWorked()) onOfflineSessionExited(\"network_item_ready\")"))
        // Hard cap: ONE timer, armed with the evidence, never a loop.
        val cap = service.substring(service.indexOf("private fun armOfflineSessionExpiry() {")).substringBefore("\n    }\n")
        assertTrue(cap.contains("offlineSessionExpiryJob?.cancel()"))
        assertTrue(cap.contains("if (PlaybackOfflineSession.expireIfDue(hasLiveInternetConnection())) onOfflineSessionExited(\"expired\")"))
        assertFalse(cap.contains("while"))
        assertTrue(service.substring(service.indexOf("private fun noteOfflineConnectivityFailure(mediaId: String?): Boolean {")).substringBefore("\n    }\n").contains("armOfflineSessionExpiry()"))
    }

    /** C6 + «Reintentar en línea»: the switch turned off and the user end it at once. */
    @Test
    fun `the switch and the user end the session`() {
        assertTrue(service.contains(".collect { auto -> if (!auto && PlaybackOfflineSession.forceExit()) onOfflineSessionExited(\"auto_off\") }"))
        assertTrue(service.contains("PlaybackOfflineSession.userRetries.collect { onOfflineSessionExited(\"user_retry\") }"))
        val home = main("ui/screens/OfflineHome.kt")
        assertTrue(home.contains("if (automatic && playbackOffline && online) {"))
        assertTrue(home.contains("PlaybackOfflineSession.requestRetryOnline()"))
        assertTrue(home.contains("R.string.offline_retry_online"))
        for (values in listOf("values", "values-es")) {
            assertTrue(File(root, "app/src/main/res/$values/strings.xml").readText().contains("name=\"offline_retry_online\""))
        }
    }

    /** Row 284: the automatic mode never writes the owner's switch, and the session is memory only. */
    @Test
    fun `the session never writes the manual offline switch`() {
        val session = main("playback/PlaybackOfflineSession.kt")
        assertFalse("the session is memory only", session.contains("dataStore"))
        assertFalse(session.contains("[OfflineModeKey] ="))
        assertFalse(session.contains(".edit {"))
        assertFalse(service.contains("[OfflineModeKey] = true"))
        assertFalse(service.contains("[OfflineModeKey] = !"))
    }

    /** Ronda 9 / row 284: being online never requires VALIDATED; VALIDATED is only evidence for the session. */
    @Test
    fun `network state still reads online without requiring validation`() {
        val state = main("utils/NetworkState.kt")
        val readNow = state.substring(state.indexOf("private fun readNow("))
        assertFalse(readNow.substringBefore("\n    }\n").contains("VALIDATED"))
    }

    @Test
    fun `one stall timer per buffering episode, from both player callbacks`() {
        val state = service.substring(service.indexOf("override fun onPlaybackStateChanged(\n        @Player.State playbackState: Int,"))
        assertTrue(state.substringBefore("\n    }\n").contains("updateOfflineStallWatchdog()"))
        val pwr = service.substring(service.indexOf("override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {"))
        assertTrue(pwr.substringBefore("\n    }\n").contains("updateOfflineStallWatchdog()"))
        val watchdog = service.substring(service.indexOf("private fun updateOfflineStallWatchdog() {"))
            .substringBefore("\n    }\n")
        assertTrue(watchdog.contains("if (offlineStallJob?.isActive == true) return"))
        assertTrue(watchdog.contains("delay(PlaybackOfflineSession.STALL_TIMEOUT_MS)"))
        assertTrue(watchdog.contains("val evidence = PlaybackOfflineSession.stallHasEvidence(systemHasNetwork)"))
        assertFalse("no polling loop", watchdog.contains("while (") || watchdog.contains("while(true"))
    }

    @Test
    fun `radio, pagination and the URL preload stay off the network during the session`() {
        val radio = main("playback/RadioContinuationEngine.kt")
        assertTrue(radio.contains("if (dataStore.get(OfflineModeKey, false) || PlaybackOfflineSession.isActive) {"))
        assertTrue(service.contains("!isRepeatTransition &&\n            !PlaybackOfflineSession.isActive &&"))
        assertTrue(service.contains("if (PlaybackOfflineSession.isActive) return@launch"))
    }

    @Test
    fun `the screens read the session through the one offline state`() {
        val ui = main("utils/AutoOfflineMode.kt")
        assertTrue(ui.contains("PlaybackOfflineSession.active.collectAsState()"))
        assertTrue(ui.contains("playbackOffline = playbackOffline"))
    }

    /** A refusal that lands while the user has paused moves on but never resumes the pause. */
    @Test
    fun `the offline skip never resumes a user pause`() {
        val skip = service.substring(service.indexOf("private fun skipToOfflinePlayableSong(index: Int) {")).substringBefore("\n    }\n")
        assertTrue(skip.contains("val resume = player.playWhenReady"))
        assertTrue(skip.contains("if (resume && castConnectionHandler?.isCasting?.value != true) {"))
    }

    /** One ERR_BR line per branch, so the shared log says which recovery ran. Ids only (rule 4). */
    @Test
    fun `every onPlayerError branch logs its ERR_BR line`() {
        val branches = listOf(
            "soft_video", "video_format_retry", "video_unresolvable", "video_to_audio", "retry_limit",
            "no_stream_video", "no_stream_skip", "renderer", "range_416", "corruption_giveup",
            "corruption", "page_reload", "expired_403", "offline_network", "offline_skip", "offline_wait",
            "network_giveup", "network_retry", "generic_io", "auto_skip", "stop",
        )
        for (branch in branches) {
            assertTrue("ERR_BR $branch", onPlayerError.contains("logErrorBranch(\"$branch\", mediaId, error)"))
        }
        assertTrue(rejectionHandler.contains("logErrorBranch(\"offline_rejection_\${kind.name.lowercase()}\", mediaId, error)"))
        assertTrue(rejectionHandler.contains("logErrorBranch(\"offline_rejection_stale_\${kind.name.lowercase()}\", mediaId, error)"))
        val helper = service.substring(service.indexOf("private fun logErrorBranch(")).substringBefore("\n    }\n")
        assertTrue(helper.contains("\"ERR_BR=%s id=%s code=%d connected=%b session=%b validated=%b\""))
        assertTrue(helper.contains("LogRedaction.forLog(mediaId)"))
        assertFalse(helper.contains("error.message"))
    }

    /** The bounded network retry keeps the bytes (a dead link says nothing about audio already on disk). */
    @Test
    fun `the network branches never purge the song bytes`() {
        val start = onPlayerError.indexOf("!isNetworkConnected.value -> {")
        val end = onPlayerError.indexOf("handleGenericIOError(mediaId)")
        assertTrue(start in 1 until end)
        assertFalse(onPlayerError.substring(start, end).contains("performAggressiveCacheClear"))
    }

    /** (3) YTPlayerUtils: every client failure is tallied per call, and only the "all clients failed" dead end reads it. */
    @Test
    fun `the resolver carries a lack of internet out of the all-clients-failed dead end`() {
        val ytp = main("utils/YTPlayerUtils.kt")
        assertTrue(ytp.contains("val clientFailures = ClientFailureTally()"))
        assertTrue("per call, never a shared object field", !ytp.contains("private val clientFailures"))
        for (client in listOf("VIDEO_CLIENT.clientName", "MAIN_CLIENT.clientName", "gateClient.clientName", "client.clientName")) {
            assertTrue(client, ytp.contains("logClientFailure($client, videoId, it); clientFailures.record(it)"))
        }
        val deadEnd = ytp.substring(ytp.indexOf("if (streamPlayerResponse == null) {")).substringBefore("\n        }\n")
        assertTrue(deadEnd.contains("val noInternet = clientFailures.allConnectivity()"))
        assertTrue(deadEnd.contains("cause = noInternet,"))
        assertTrue(ytp.contains("PlaybackErrorClassifier.isConnectivityCause(error)"))
        // A single client call that got ANY response proves the internet works: never reported as "no internet".
        assertEquals(4, Regex(Regex.escape("clientFailures.record(it) }.onSuccess { clientFailures.answered() }")).findAll(ytp).count())
        assertTrue(ytp.contains("return if (!anyAnswered && failed > 0 && connectivity.get() == failed)"))
    }

    /** (1) Never a stall skip while a resolution is in flight (a fresh resolve after a retry, the probe). */
    @Test
    fun `the stall watchdog leaves a resolution in flight alone`() {
        val watchdog = service.substring(service.indexOf("private fun updateOfflineStallWatchdog() {")).substringBefore("\n    }\n")
        val inFlight = watchdog.indexOf("if (audioStreamResolveInFlight.get() > 0) return@launch")
        assertTrue(inFlight > 0)
        assertTrue(inFlight < watchdog.indexOf("PlaybackOfflineSession.stallHasEvidence("))
    }

    /** (d) + (e) */
    @Test
    fun `a new item gets its own stale retry and an unblocked network is a network change`() {
        val transition = service.substring(service.indexOf("override fun onMediaItemTransition(")).substringBefore("\n    }\n")
        assertTrue(transition.contains("if (trackChanged) staleRejectionRetryId = null"))
        val state = main("utils/NetworkState.kt")
        assertTrue(state.contains("override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {"))
        assertTrue(state.contains("if (!blocked) _epoch.update { it + 1 }"))
    }
}
