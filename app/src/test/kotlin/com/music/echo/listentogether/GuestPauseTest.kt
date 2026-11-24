package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 816 (C1): which pauses of a guest's own player keep it out of the room's play and pause. */
class GuestPauseTest {
    @Test
    fun aPauseThatIsNotTheServiceGivingUpHoldsTheGuest() {
        // The user's own pause, and the service's audio-situation pauses (focus, noisy, mute, sleep timer), all report no failure.
        assertTrue(guestPausedLocally(playWhenReady = false, inRoom = true, isHost = false, pausedByPlaybackFailure = false))
    }

    @Test
    fun aPauseThatIsTheServiceGivingUpOnPlaybackLeavesTheGuestInTheRoom() {
        assertFalse(guestPausedLocally(playWhenReady = false, inRoom = true, isHost = false, pausedByPlaybackFailure = true))
    }

    @Test
    fun playingTheHostOrNoRoomNeverHoldsAnyone() {
        assertFalse(guestPausedLocally(playWhenReady = true, inRoom = true, isHost = false, pausedByPlaybackFailure = false))
        assertFalse(guestPausedLocally(playWhenReady = false, inRoom = true, isHost = true, pausedByPlaybackFailure = false))
        assertFalse(guestPausedLocally(playWhenReady = false, inRoom = false, isHost = false, pausedByPlaybackFailure = false))
    }

    @Test
    fun theLogLineCarriesOnlyBooleansAndTheReasonNumber() {
        assertEquals(
            "LT_PAUSED_LOCALLY=true reason=1 deliberate=true failure=false",
            pausedLocallyLogLine(pausedLocally = true, reason = 1, deliberate = true, playbackFailure = false),
        )
        assertEquals(
            "LT_PAUSED_LOCALLY=false reason=-1 deliberate=false failure=false",
            pausedLocallyLogLine(pausedLocally = false, reason = PAUSED_LOCALLY_REASON_NONE, deliberate = false, playbackFailure = false),
        )
    }

    @Test
    fun onlyAGuestPlayingInAPausedRoomWithATrackIsPausedAgain() {
        assertTrue(roomRefusesGuestResume(inRoom = true, isHost = false, roomPlaying = false, hasRoomTrack = true))
        assertFalse(roomRefusesGuestResume(inRoom = true, isHost = false, roomPlaying = true, hasRoomTrack = true))
        assertFalse("an empty room has nothing to follow", roomRefusesGuestResume(inRoom = true, isHost = false, roomPlaying = false, hasRoomTrack = false))
        assertFalse(roomRefusesGuestResume(inRoom = true, isHost = true, roomPlaying = false, hasRoomTrack = true))
        assertFalse(roomRefusesGuestResume(inRoom = false, isHost = false, roomPlaying = false, hasRoomTrack = true))
    }

    // ── wiring: the bridge and the service are Android-only, so their calls are pinned from the source ──────────────────────────

    @Test
    fun theRoomsRepauseOfAGuestSaysWhy() {
        val bridge = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt")
        val posted = bridge.substringAfter("android.os.Handler(android.os.Looper.getMainLooper()).post {").substringBefore("override fun onIsPlayingChanged")
        assertTrue(posted.contains("if (!roomRefusesGuestResume(now.inRoom, now.isHost, now.isPlaying, now.currentTrack != null)) return@post"))
        assertTrue("after the pause", posted.indexOf("iad1tya.echo.music.R.string.listen_together_room_paused_host_only") > posted.indexOf("player.pause()"))
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            assertTrue(dir, text("app/src/main/res/$dir/echo_strings.xml").contains("<string name=\"listen_together_room_paused_host_only\">"))
        }
        assertTrue(
            text("app/src/main/res/values-es/echo_strings.xml")
                .contains("<string name=\"listen_together_room_paused_host_only\">La sala está en pausa: solo el anfitrión puede reanudar</string>"),
        )
    }

    @Test
    fun theSharedLogSaysWhatTheGuestLoadedWhatTheHostSentAndWhichMessagesArrived() {
        val bridge = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt")
        assertTrue(bridge.contains("\"Guest loading %s at %d of %d playWhenReady=%b\", info.id.asLogToken(), startIndex, ordered.size, playWhenReady)"))
        assertTrue(bridge.contains("\"Host publishing PLAY after track change (changeHostPlaying=%s roomWasPlaying=%b)\", changeHostPlaying, roomWasPlaying)"))
        assertTrue(bridge.contains("\"Host track change: no PLAY, the host did not start it within %d ms\""))
        val client = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherClient.kt")
        val read = client.substringAfter("private suspend fun readFrames(").substringBefore("private suspend fun handleMessage(")
        assertTrue("the type only, never the payload", read.contains("if (type != MessageTypes.PONG) Timber.tag(TAG).i(\"Received %s\", type.take(MAX_LOGGED_TYPE_CHARS))"))
        assertFalse(read.contains("payloadBytes)\n            Timber"))
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun theBridgeJudgesEveryOwnPauseThroughTheRuleAndLogsEachChange() {
        val bridge = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt")
        assertTrue(bridge.contains("guestPausedLocally(playWhenReady, state.inRoom, state.isHost, service?.pausedByPlaybackFailure == true),"))
        assertTrue("one funnel for every change", bridge.contains("if (pausedLocally == value) return"))
        assertTrue(bridge.contains("pausedLocallyLogLine(value, reason, service?.deliberatePause == true, service?.pausedByPlaybackFailure == true),"))
        assertEquals("nothing else writes it", 1, Regex("""pausedLocally = value""").findAll(bridge).count())
        assertFalse("the old every-pause-sticks rule is gone", bridge.contains("pausedLocally = !playWhenReady"))
    }

    @Test
    fun theServiceMarksOnlyItsGiveUpPausesAndClearsTheMarkOnPlay() {
        val service = text("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        // Set BEFORE the pause, so every listener sees it whatever order media3 calls them in.
        val stop = service.substringAfter("internal fun stopOnError() {").substringBefore("private fun updateNotification()")
        assertTrue(stop.indexOf("pausedByPlaybackFailure = true") in 0 until stop.indexOf("player.pause()"))
        val chain = service.substringAfter("Timber.tag(TAG).i(\"START_CAUSE=error_skip_chain_pause\")").substringBefore("consecutivePlaybackErr = 0")
        assertTrue(chain.indexOf("pausedByPlaybackFailure = true") in 0 until chain.indexOf("player.pause()"))
        val fgs = service.substringAfter("override fun onForegroundServiceStartNotAllowedException() {").substringBefore("secondaryPlayer?.pause()")
        assertTrue(fgs.indexOf("pausedByPlaybackFailure = true") in 0 until fgs.indexOf("player.pause()"))
        assertTrue("any start clears it", service.contains("            deliberatePause = false\n            pausedByPlaybackFailure = false\n"))
        // The sleep timer is the user's choice and keeps holding the guest.
        val sleep = service.substringAfter("private fun fireSleepTimer() {").substringBefore("Timber.tag(TAG).i(\"SLEEP_TIMER fired")
        assertFalse(sleep.contains("pausedByPlaybackFailure"))
    }
}
