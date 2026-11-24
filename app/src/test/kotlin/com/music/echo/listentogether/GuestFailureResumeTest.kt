package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 821: the room re-plays a guest its service paused on a failure only a few times, spaced out, and then says so once. */
class GuestFailureResumeTest {
    @Test
    fun eachAutomaticReplayWaitsItsBackoffAfterTheFailurePause() {
        assertEquals(listOf(15_000L, 30_000L, 60_000L), GUEST_FAILURE_RESUME_BACKOFF_MS)
        assertEquals(GuestFailureResumeStep.Wait, guestFailureResumeStep(0, 14_999L, foregroundRefused = false, gaveUp = false))
        assertEquals(GuestFailureResumeStep.Resume(1), guestFailureResumeStep(0, 15_000L, foregroundRefused = false, gaveUp = false))
        assertEquals(GuestFailureResumeStep.Wait, guestFailureResumeStep(1, 29_999L, foregroundRefused = false, gaveUp = false))
        assertEquals(GuestFailureResumeStep.Resume(2), guestFailureResumeStep(1, 30_000L, foregroundRefused = false, gaveUp = false))
        assertEquals(GuestFailureResumeStep.Wait, guestFailureResumeStep(2, 59_999L, foregroundRefused = false, gaveUp = false))
        assertEquals(GuestFailureResumeStep.Resume(3), guestFailureResumeStep(2, 60_000L, foregroundRefused = false, gaveUp = false))
    }

    @Test
    fun afterTheLastReplayTheGuestGivesUpOnceAndThenHolds() {
        assertEquals(
            GuestFailureResumeStep.GiveUp(3, GUEST_RETRY_REASON_PLAYBACK_ERROR),
            guestFailureResumeStep(3, 600_000L, foregroundRefused = false, gaveUp = false),
        )
        assertEquals(GuestFailureResumeStep.Hold, guestFailureResumeStep(3, 600_000L, foregroundRefused = false, gaveUp = true))
    }

    @Test
    fun aRefusedForegroundStartIsNeverReplayedAutomatically() {
        assertEquals(
            GuestFailureResumeStep.GiveUp(0, GUEST_RETRY_REASON_FOREGROUND_REFUSED),
            guestFailureResumeStep(0, 600_000L, foregroundRefused = true, gaveUp = false),
        )
        assertEquals(
            GuestFailureResumeStep.GiveUp(1, GUEST_RETRY_REASON_FOREGROUND_REFUSED),
            guestFailureResumeStep(1, 0L, foregroundRefused = true, gaveUp = false),
        )
        assertEquals(GuestFailureResumeStep.Hold, guestFailureResumeStep(0, 600_000L, foregroundRefused = true, gaveUp = true))
    }

    @Test
    fun theGuardCountsFromEachFailurePauseAndStopsAfterThree() {
        val guard = GuestFailureResumeGuard()
        guard.onFailurePause(0L)
        assertEquals(GuestFailureResumeStep.Wait, guard.decide(10_000L, foregroundRefused = false))
        assertEquals(GuestFailureResumeStep.Resume(1), guard.decide(15_000L, foregroundRefused = false))
        guard.onFailurePause(20_000L)
        assertEquals(GuestFailureResumeStep.Wait, guard.decide(45_000L, foregroundRefused = false))
        assertEquals(GuestFailureResumeStep.Resume(2), guard.decide(50_000L, foregroundRefused = false))
        guard.onFailurePause(51_000L)
        assertEquals(GuestFailureResumeStep.Wait, guard.decide(100_000L, foregroundRefused = false))
        assertEquals(GuestFailureResumeStep.Resume(3), guard.decide(111_000L, foregroundRefused = false))
        guard.onFailurePause(112_000L)
        assertEquals(GuestFailureResumeStep.GiveUp(3, GUEST_RETRY_REASON_PLAYBACK_ERROR), guard.decide(200_000L, foregroundRefused = false))
        // One notice and one log line, then nothing: every later heartbeat holds.
        assertEquals(GuestFailureResumeStep.Hold, guard.decide(215_000L, foregroundRefused = false))
        assertEquals(GuestFailureResumeStep.Hold, guard.decide(10_000_000L, foregroundRefused = false))
    }

    @Test
    fun aNewRoomCommandOrTheUserGivesTheReplaysBack() {
        val guard = GuestFailureResumeGuard()
        guard.onFailurePause(0L)
        assertTrue(guard.decide(0L, foregroundRefused = true) is GuestFailureResumeStep.GiveUp)
        guard.reset()
        guard.onFailurePause(1_000L)
        assertEquals(GuestFailureResumeStep.Resume(1), guard.decide(16_000L, foregroundRefused = false))
    }

    @Test
    fun aFailurePauseTheGuardDidNotSeeIsTimedFromTheFirstQuestion() {
        val guard = GuestFailureResumeGuard()
        assertEquals(GuestFailureResumeStep.Wait, guard.decide(500_000L, foregroundRefused = false))
        assertEquals(GuestFailureResumeStep.Resume(1), guard.decide(515_000L, foregroundRefused = false))
    }

    @Test
    fun theLogLineCarriesOnlyNumbers() {
        assertEquals("LT_GUEST_RETRY_EXHAUSTED attempts=3 reason=1", guestRetryExhaustedLogLine(3, GUEST_RETRY_REASON_PLAYBACK_ERROR))
        assertEquals("LT_GUEST_RETRY_EXHAUSTED attempts=0 reason=2", guestRetryExhaustedLogLine(0, GUEST_RETRY_REASON_FOREGROUND_REFUSED))
    }

    // ── wiring: the bridge and the service are Android-only, so their calls are pinned from the source ──────────────────────────

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val bridge by lazy { text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt") }

    @Test
    fun everyAutomaticPathAsksTheGuardBeforePlayingAFailedGuestAgain() {
        val apply = bridge.substringAfter("private suspend fun applyTransport(").substringBefore("private suspend fun playTrack(")
        val gate = "if (!fromRoomCommand && isPlaying && !player.playWhenReady && service.pausedByPlaybackFailure && !failureResumeMayPlay(service)) {"
        assertTrue(apply.contains("fromRoomCommand: Boolean = false,"))
        assertTrue(apply.contains(gate))
        assertTrue("before the reloads of the room's track", apply.indexOf(gate) in 0 until apply.indexOf("val step = trackGuard.decide("))
        assertTrue("before anything moves the player", apply.indexOf(gate) < apply.indexOf("player.seekTo(action.positionMs)"))
        assertTrue(apply.indexOf(gate) < apply.indexOf("player.play()"))
        // Only the room's own command skips it (applied at once, or let go by a synchronized start's hold, once per hold); the
        // heartbeat, realign and recheck all use the default.
        assertEquals(2, Regex("""fromRoomCommand = true""").findAll(bridge).count())
        val watch = bridge.substringAfter("private suspend fun watchRoomForGuests()").substringBefore("private suspend fun guestPositionHeartbeat()")
        assertTrue(watch.contains("applyTransport(service, playing && !pausedLocally, position, track, anchorServerTime, fromRoomCommand = true)"))
        val follow = bridge.substringAfter("private suspend fun followRoomAfterHold(").substringBefore("    /**")
        assertTrue(
            follow.contains(
                "applyTransport(service, state.isPlaying && !pausedLocally, state.position, state.currentTrack, state.lastActionServerTime, fromRoomCommand = true)",
            ),
        )
    }

    @Test
    fun theGuardIsToldOfEachFailurePauseAndResetLikeTheTrackGuard() {
        val listener = bridge.substringAfter("override fun onPlayWhenReadyChanged(").substringBefore("override fun onIsPlayingChanged")
        assertTrue(listener.contains("failureResume.onFailurePause(android.os.SystemClock.elapsedRealtime())"))
        assertTrue("the user's resume", listener.contains("failureResume.reset()\n            trackGuard.reset()"))
        val watch = bridge.substringAfter("private suspend fun watchRoomForGuests()").substringBefore("private suspend fun guestPositionHeartbeat()")
        assertTrue("every new room command", watch.indexOf("failureResume.reset()\n                        roomReceipt = RoomTimelineReceipt(") > 0)
        assertTrue("leaving the room", watch.contains("failureResume.reset()\n                    trackGuard.reset()"))
    }

    @Test
    fun givingUpIsSaidOnceInTheLogAndInANotice() {
        val mayPlay = bridge.substringAfter("private fun failureResumeMayPlay(service: MusicService)").substringBefore("private suspend fun applyTransport(")
        assertTrue(mayPlay.contains("failureResume.decide(android.os.SystemClock.elapsedRealtime(), service.pausedByForegroundRefusal)"))
        assertTrue(mayPlay.contains("is GuestFailureResumeStep.Resume -> true"))
        assertTrue(mayPlay.contains("Timber.tag(TAG).i(guestRetryExhaustedLogLine(step.attempts, step.reason))"))
        assertTrue(mayPlay.contains("iad1tya.echo.music.R.string.listen_together_guest_retry_exhausted"))
        for (forbidden in listOf("title", "artist", "asLogToken", ".id")) assertFalse("rule 4: $forbidden", mayPlay.contains(forbidden))
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            assertTrue(dir, text("app/src/main/res/$dir/echo_strings.xml").contains("<string name=\"listen_together_guest_retry_exhausted\">"))
        }
        assertTrue(
            text("app/src/main/res/values-es/echo_strings.xml").contains(
                "<string name=\"listen_together_guest_retry_exhausted\">No se pudo reproducir esta canción en este aparato; toca play para reintentar</string>",
            ),
        )
    }

    @Test
    fun theServiceMarksOnlyTheRefusedForegroundStartAndClearsItOnPlay() {
        val service = text("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val fgs = service.substringAfter("override fun onForegroundServiceStartNotAllowedException() {").substringBefore("secondaryPlayer?.pause()")
        assertTrue("set before the pause", fgs.indexOf("pausedByForegroundRefusal = true") in 0 until fgs.indexOf("player.pause()"))
        assertEquals("nowhere else", 1, Regex("""pausedByForegroundRefusal = true""").findAll(service).count())
        assertTrue(
            "any start clears it",
            service.contains("            pausedByPlaybackFailure = false\n            pausedByForegroundRefusal = false\n"),
        )
    }
}
