package iad1tya.echo.music.playback

import java.io.File
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

    /**
     * An offline refusal is routed BEFORE the generic I/O fallthrough (handleGenericIOError → performAggressiveCacheClear),
     * and its branch never purges, never retries the same song.
     */
    @Test
    fun `an offline refusal goes to the offline skip and never reaches the purge`() {
        val branchStart = onPlayerError.indexOf("PlaybackErrorClassifier.isOfflineRejection(error) -> {")
        val generic = onPlayerError.indexOf("handleGenericIOError(mediaId)")
        assertTrue(branchStart > 0)
        assertTrue("the offline branch must come before the generic I/O handler", branchStart < generic)
        val branch = onPlayerError.substring(branchStart, onPlayerError.indexOf("!isNetworkConnected.value -> {", branchStart))
        assertTrue(branch.contains("continueWithOfflineQueue("))
        assertFalse(branch.contains("performAggressiveCacheClear"))
        assertFalse(branch.contains("incrementRetryCount"))
        assertFalse(branch.contains("handleGenericIOError"))

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

    /** Order: the evidence-backed session branches run before the bounded retry and before the "unavailable song" verdict. */
    @Test
    fun `network failures with evidence continue with the offline queue`() {
        val noStreamSession = onPlayerError.indexOf("!PlaybackErrorClassifier.isContentLevelNoStream(error) &&")
        val noStreamVerdict = onPlayerError.indexOf("PlaybackErrorClassifier.isNoStreamError(error) && isNetworkConnected.value -> {")
        assertTrue(noStreamSession in 1 until noStreamVerdict)

        val networkSession = onPlayerError.indexOf("PlaybackErrorClassifier.isConnectivityFailure(error) && noteOfflineNetworkFailure() -> {")
        val boundedRetry = onPlayerError.indexOf("PlaybackErrorClassifier.isNetworkRelatedError(error) -> {")
        val offline = onPlayerError.indexOf("!isNetworkConnected.value -> {")
        assertTrue(networkSession in 1 until offline)
        assertTrue(offline < boundedRetry)
        assertTrue(onPlayerError.substring(networkSession, offline).contains("continueWithOfflineQueue(stopIfNothing = false)"))

        val rejection = onPlayerError.substring(onPlayerError.indexOf("PlaybackErrorClassifier.isOfflineRejection(error) -> {"), networkSession)
        assertTrue(rejection.contains("if (kind == OfflineRejectionException.Kind.NO_NETWORK) noteOfflineNoNetwork()"))
        assertTrue(rejection.contains("continueWithOfflineQueue(stopIfNothing = kind == OfflineRejectionException.Kind.MANUAL)"))
    }

    @Test
    fun `entering and leaving are event-driven and say so once`() {
        assertTrue(service.contains("if (!isConnected && !hasLiveInternetConnection()) noteOfflineNoNetwork()"))
        assertTrue(service.contains("if (validated && PlaybackOfflineSession.onValidated()) onOfflineSessionExited(\"validated\")"))
        assertTrue(service.contains("Timber.tag(TAG).i(\"OFFLINE_SESSION enter reason=%s\", reason.log)"))
        assertTrue(service.contains("Timber.tag(TAG).i(\"OFFLINE_SESSION exit reason=%s\", how)"))
        assertTrue(service.contains("R.string.offline_session_started") && service.contains("R.string.offline_session_ended"))
        // Leaving re-prepares a waiting queue with the reconnection semantics: only a NETWORK pause is resumed.
        assertTrue(service.contains("if (how != \"resolved\" && waitingForNetworkConnection.value) triggerRetry()"))
        assertTrue(service.contains("if (PlaybackOfflineSession.onNetworkAvailable()) onOfflineSessionExited(\"network_back\")"))
        for (values in listOf("values", "values-es")) {
            val strings = File(root, "app/src/main/res/$values/strings.xml").readText()
            assertTrue("$values", strings.contains("name=\"offline_session_started\"") && strings.contains("name=\"offline_session_ended\""))
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
        assertTrue(watchdog.contains("PlaybackOfflineSession.stallHasEvidence("))
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
            "offline_no_stream", "no_stream_video", "no_stream_skip", "renderer", "range_416", "corruption_giveup",
            "corruption", "page_reload", "expired_403", "offline_network", "offline_skip", "offline_wait",
            "network_giveup", "network_retry", "generic_io", "auto_skip", "stop",
        )
        for (branch in branches) {
            assertTrue("ERR_BR $branch", onPlayerError.contains("logErrorBranch(\"$branch\", mediaId, error)"))
        }
        assertTrue(onPlayerError.contains("logErrorBranch(\"offline_rejection_\${kind?.name?.lowercase()}\", mediaId, error)"))
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
}
