package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 744 (P25B): source guards for the Listen Together fixes that live in Android-only code (the bridge drives a media3
 * player, the dialog is Compose). Each literal below is grepped from the source it pins.
 */
class ListenTogetherReliabilityGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val bridge by lazy { text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt") }

    @Test
    fun theSpeedTrimEndsWhenItsGapIsClosed() {
        assertTrue(bridge.contains("val closeMs = SyncCorrection.trimDurationMs(errorMs, action.speed)"))
        assertTrue(bridge.contains("delay(closeMs)"))
    }

    private val client by lazy { text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherClient.kt") }

    @Test
    fun aHalfOpenSocketIsDetectedByThePingLoopAndReconnected() {
        val readLoop = client.substringAfter("for (frame in live.incoming) {").substringBefore("// Metrolist's server")
        assertTrue("every frame refreshes the liveness clock", readLoop.contains("lastInboundAt = elapsedRealtime()"))
        assertTrue(client.contains("unanswered = ConnectionPolicy.unansweredAfter(unanswered, heardSincePing = lastInboundAt >= sentAt)"))
        assertTrue(client.contains("if (ConnectionPolicy.socketLooksDead(unanswered)) {"))
        assertTrue("a dead socket ends the read loop, which schedules the reconnect", client.contains("reader.cancel()"))
    }

    @Test
    fun insideARoomReconnectingIsBoundedByTimeNotByAttempts() {
        assertTrue("a token read back from disk gets the attempt budget", client.contains("val inRoom = sessionToken != null && tokenConfirmed"))
        assertTrue(client.contains("MessageTypes.RECONNECTED -> if (sessionToken != null) tokenConfirmed = true"))
        assertTrue(client.contains("if (ConnectionPolicy.shouldGiveUp(inRoom, reconnectAttempts, MAX_RECONNECT_ATTEMPTS, dropped, now)) {"))
        assertTrue("a completed handshake starts the next drop's window afresh", client.contains("droppedAt = null"))
    }

    @Test
    fun aResumeToARoomThatIsGoneEndsInsteadOfRetryingAndForgetsThePersistedToken() {
        assertTrue("a 404 upgrade is the room's final answer", client.contains("if (endResumeIfRoomIsGone(e)) return@launch"))
        assertTrue(client.contains("_events.emit(ListenTogetherEvent.ResumeRefused(\"The room no longer exists\"))"))
        val session = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherSession.kt")
        assertTrue(session.contains("is ListenTogetherEvent.ResumeRefused -> endRefusedResume(event.reason)"))
        assertTrue("giving up forgets the persisted token too", session.contains("if (!event.willRetry) tokenStore?.clear()"))
        val create = session.substringAfter("fun createRoom(username: String) =").substringBefore("val sent = client.send(")
        assertTrue("a new room never carries the old token", create.contains("if (!_state.value.inRoom) client.forgetSessionToken()"))
        val join = session.substringAfter("fun joinRoom(").substringBefore("val sent = client.send(")
        assertTrue(join.contains("if (!_state.value.inRoom) client.forgetSessionToken()"))
    }

    @Test
    fun noRoomMessageOvertakesTheResumeOnANewSocket() {
        assertTrue(client.contains("if (!roomMessagesOpen && msgType != MessageTypes.CLIENT_CAPABILITIES && msgType != MessageTypes.RECONNECT) return false"))
        val open = client.substringAfter("val live = client.webSocketSession(url)").substringBefore("_events.emit(")
        assertTrue("closed before the socket is published", open.indexOf("roomMessagesOpen = false") in 0 until open.indexOf("session = live"))
        assertTrue(
            "opened only after the reconnect is sent, before Connected flushes queued actions",
            open.indexOf("roomMessagesOpen = true") > open.indexOf("send(MessageTypes.RECONNECT, ReconnectPayload(sessionToken = token))"),
        )
    }

    @Test
    fun theHostRepublishesAfterEveryReconnect() {
        assertTrue(bridge.contains(".map { (it.inRoom && it.isHost) to it.reconnectRevision }"))
        val session = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherSession.kt")
        assertTrue("leaving forgets the token the client would replay", session.contains("client.forgetSessionToken()"))
    }

    @Test
    fun aGuestPauseIsJudgedByTheRoomsCallsOnTheMainThreadOnly() {
        assertTrue(bridge.contains("val roomsOwnCall = applyingOnMain > 0"))
        assertTrue(bridge.contains("if (!roomsOwnCall) pausedLocally = !playWhenReady && state.inRoom && !state.isHost"))
        assertTrue("every room call that moves the player is counted", bridge.contains("): Boolean = remoteOnMain {"))
        assertTrue(bridge.contains("remoteOnMain { advanceInPlace("))
        val posted = bridge.substringAfter("android.os.Handler(android.os.Looper.getMainLooper()).post {").substringBefore("override fun onIsPlayingChanged")
        assertTrue("the room's own pause of a guest is counted too", posted.contains("applyingOnMain++"))
    }

    @Test
    fun theHostPublishesItsQueueInPlaybackOrder() {
        assertTrue(bridge.contains("next = { i -> timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle) },"))
        assertTrue(bridge.contains("previous = { i -> timeline.getPreviousWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle) },"))
        assertTrue("toggling shuffle republishes the queue", bridge.contains("override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {"))
    }

    @Test
    fun everyDevicePlaysAtOneXInsideARoomAndGetsItsSpeedBackAfter() {
        assertTrue(bridge.contains("const val ROOM_TEMPO = 1f"))
        assertTrue("the guest's base speed is the room's", bridge.contains("val userTempo = ROOM_TEMPO"))
        assertTrue(bridge.contains("scope.launch { lockTempoInRoom() }"))
        assertTrue("a player attached inside a room is locked too", bridge.contains("if (session.state.value.inRoom) lockRoomTempo(player)"))
        assertTrue(bridge.contains("if (inRoom) lockRoomTempo(listeningTo) else unlockRoomTempo(listeningTo)"))
        val menu = text("app/src/main/kotlin/com/music/echo/ui/menu/PlayerMenu.kt")
        assertTrue("the dialog says why the speed control is gone", menu.contains("R.string.listen_together_tempo_locked"))
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            assertTrue(dir, text("app/src/main/res/$dir/echo_strings.xml").contains("name=\"listen_together_tempo_locked\""))
        }
    }
}
