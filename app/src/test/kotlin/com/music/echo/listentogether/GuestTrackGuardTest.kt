package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 796: a guest never keeps playing a song that is not the room's current track — bounded reloads, then hold, and a guest that
 * finished the room's song first waits for the host's change_track once.
 */
class GuestTrackGuardTest {
    private val duration = 200_000L

    private fun GuestTrackGuard.offTrack(
        nowMs: Long,
        roomPosition: Long = 60_000L,
        cameFromRoomTrack: Boolean = false,
        playing: Boolean = true,
        room: String = "A",
        loaded: String? = "B",
    ) = decide(room, loaded, cameFromRoomTrack, roomPosition, duration, playing, nowMs)

    @Test
    fun onTheRoomsTrackNothingHappens() {
        val guard = GuestTrackGuard()
        assertEquals(GuestTrackStep.OnRoomTrack, guard.offTrack(nowMs = 0L, loaded = "A"))
    }

    @Test
    fun aGuestOnAnotherSongReloadsTheRoomsTrackAtOnce() {
        assertEquals(GuestTrackStep.Reload(1), GuestTrackGuard().offTrack(nowMs = 0L))
    }

    @Test
    fun aReloadThatFailsAgainIsRetriedSpacedAndThenTheGuestHolds() {
        val guard = GuestTrackGuard()
        assertEquals(GuestTrackStep.Reload(1), guard.offTrack(nowMs = 0L))
        // The reload lands, then the stream fails and the service skips on: landing does NOT give the attempts back.
        assertEquals(GuestTrackStep.OnRoomTrack, guard.offTrack(nowMs = 500L, loaded = "A"))
        assertEquals(GuestTrackStep.Wait(REALIGN_MIN_SPACING_MS - 1_000L), guard.offTrack(nowMs = 1_000L))
        assertEquals(GuestTrackStep.Reload(2), guard.offTrack(nowMs = REALIGN_MIN_SPACING_MS))
        assertEquals(GuestTrackStep.Reload(3), guard.offTrack(nowMs = 2 * REALIGN_MIN_SPACING_MS))
        // No loop: after the last attempt the guest stops playing the song the room did not choose.
        assertEquals(GuestTrackStep.Hold, guard.offTrack(nowMs = 10 * REALIGN_MIN_SPACING_MS))
        assertEquals(GuestTrackStep.Hold, guard.offTrack(nowMs = 20 * REALIGN_MIN_SPACING_MS))
    }

    @Test
    fun aNewRoomTrackOrTheUserResumingGivesTheReloadsBack() {
        val guard = GuestTrackGuard()
        repeat(MAX_GUEST_TRACK_RELOADS) { guard.offTrack(nowMs = it * REALIGN_MIN_SPACING_MS) }
        assertEquals(GuestTrackStep.Hold, guard.offTrack(nowMs = 100_000L))
        assertEquals(GuestTrackStep.Reload(1), guard.offTrack(nowMs = 100_001L, room = "C"))
        repeat(MAX_GUEST_TRACK_RELOADS) { guard.offTrack(nowMs = 200_000L + it * REALIGN_MIN_SPACING_MS, room = "C") }
        guard.reset()
        assertEquals(GuestTrackStep.Reload(1), guard.offTrack(nowMs = 300_000L, room = "C"))
    }

    @Test
    fun aGuestThatFinishedTheRoomsSongFirstWaitsForTheHostOnceThenHolds() {
        val guard = GuestTrackGuard()
        val step = guard.offTrack(nowMs = 0L, roomPosition = duration - 1_500L, cameFromRoomTrack = true)
        assertEquals(GuestTrackStep.Wait(1_500L + GUEST_TRACK_HANDOFF_MS), step)
        // The host never moved on: the guest does not keep playing its own next song.
        assertEquals(GuestTrackStep.Hold, guard.offTrack(nowMs = 3_500L, roomPosition = duration + GUEST_TRACK_HANDOFF_MS, cameFromRoomTrack = true))
    }

    @Test
    fun theHandoffWaitIsBoundedEvenWhenTheRoomPositionCannotAdvance() {
        val guard = GuestTrackGuard()
        assertTrue(guard.offTrack(nowMs = 0L, roomPosition = duration - 1_000L, cameFromRoomTrack = true) is GuestTrackStep.Wait)
        assertEquals(GuestTrackStep.Hold, guard.offTrack(nowMs = 3_000L, roomPosition = duration - 1_000L, cameFromRoomTrack = true))
    }

    @Test
    fun aPausedRoomAtTheEndOfItsSongHoldsTheGuest() {
        assertEquals(GuestTrackStep.Hold, GuestTrackGuard().offTrack(nowMs = 0L, roomPosition = duration - 500L, cameFromRoomTrack = true, playing = false))
    }

    @Test
    fun midSongTheSongBeforeTheRoomsTrackIsNoHandoff() {
        // An error skip off the room's track mid-song: previous item is the room's track, but the room is nowhere near its end.
        assertEquals(GuestTrackStep.Reload(1), GuestTrackGuard().offTrack(nowMs = 0L, roomPosition = 30_000L, cameFromRoomTrack = true))
    }

    // ── wiring (the bridge drives a media3 player, so its calls are pinned from the source) ──────────────────────────────────

    private val bridge by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        File(root, "app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt").readText()
    }

    @Test
    fun theBridgeDecidesEveryMismatchThroughTheGuard() {
        assertFalse("the one-shot reload is gone", bridge.contains("mismatchReload"))
        assertTrue(bridge.contains("val step = trackGuard.decide("))
        assertTrue(bridge.contains("is GuestTrackStep.Reload -> playTrack(service, roomTrack, keepPosition = corrected, playWhenReady = isPlaying)"))
        assertTrue(bridge.contains("is GuestTrackStep.Wait -> scheduleTrackRecheck(step.inMs)"))
        assertTrue(bridge.contains("GuestTrackStep.Hold -> if (player.playWhenReady) player.pause()"))
        assertTrue("a stale state never reloads a track the room left", bridge.contains("if (session.state.value.currentTrack?.id != roomTrack.id) return@remoteOnMain false"))
    }

    @Test
    fun aGuestLeavingTheRoomsTrackByItselfIsLookedAtAtOnce() {
        val transition = bridge.substringAfter("override fun onMediaItemTransition(").substringBefore("override fun onTimelineChanged(")
        assertTrue("only the guest's own moves, not the room's loads", transition.contains("applyingOnMain == 0"))
        assertTrue(transition.contains("scheduleTrackRecheck(0L)"))
        assertTrue(bridge.contains("scope.launch { recheckGuestTrack() }"))
        assertTrue("the user's resume gives the reloads back", bridge.contains("trackGuard.reset()\n            Timber.tag(TAG).i(\"Guest resumed"))
    }
}
