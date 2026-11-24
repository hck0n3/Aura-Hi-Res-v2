package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 849 (owner decision B2, 2026-11-24): joining a room as a guest switches shuffle off, leaving gives the user's own back, and
 * any other door that turns it on while a guest (a synced device, the car's «Aleatorio», a list's shuffle start) is undone by the
 * service without saving it. Each literal below is grepped from the source it pins.
 */
class GuestShuffleRestoreTest {
    @Test
    fun joiningAsAGuestSwitchesShuffleOffAndLeavingGivesItBack() {
        val hold = GuestShuffleHold()
        assertEquals(false, hold.onRole(guest = true, shuffleOn = true))
        assertEquals(true, hold.onRole(guest = false, shuffleOn = false))
    }

    @Test
    fun aGuestWhoHadShuffleOffIsLeftAloneBothWays() {
        val hold = GuestShuffleHold()
        assertNull(hold.onRole(guest = true, shuffleOn = false))
        assertNull(hold.onRole(guest = false, shuffleOn = false))
    }

    @Test
    fun theHostAndNoRoomNeverTouchShuffle() {
        val hold = GuestShuffleHold()
        assertNull(hold.onRole(guest = false, shuffleOn = true))
        assertNull(hold.onRole(guest = false, shuffleOn = false))
    }

    @Test
    fun aNewPlayerWhileStillAGuestIsSwitchedOffAndTheFirstChoiceIsKept() {
        val hold = GuestShuffleHold()
        assertEquals(false, hold.onRole(guest = true, shuffleOn = true))
        // The service rebuilt its player (or restarted) and it came back shuffled: off again, the user's choice unchanged.
        assertEquals(false, hold.onRole(guest = true, shuffleOn = true))
        assertNull(hold.onRole(guest = true, shuffleOn = false))
        assertEquals(true, hold.onRole(guest = false, shuffleOn = false))
    }

    @Test
    fun becomingTheHostGivesTheChoiceBackOnceAndANewRoomStartsClean() {
        val hold = GuestShuffleHold()
        hold.onRole(guest = true, shuffleOn = true)
        assertEquals(true, hold.onRole(guest = false, shuffleOn = false))
        assertNull("given back once", hold.onRole(guest = false, shuffleOn = false))
        assertNull(hold.onRole(guest = true, shuffleOn = false))
        assertNull("this room began with shuffle off", hold.onRole(guest = false, shuffleOn = false))
    }

    @Test
    fun aChoiceAlreadyBackInPlaceIsNotAppliedAgain() {
        val hold = GuestShuffleHold()
        hold.onRole(guest = true, shuffleOn = true)
        assertNull(hold.onRole(guest = false, shuffleOn = true))
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun theBridgeAppliesTheHoldOnEveryChangeOfRoleAndToEveryNewPlayer() {
        val bridge = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt")
        assertTrue(bridge.contains("scope.launch { holdShuffleForGuests() }"))
        val hold = bridge.substringAfter("private suspend fun holdShuffleForGuests() {").substringBefore("private fun lockRoomTempo(")
        assertTrue("event-driven, rule 7", hold.contains(".map { it.inRoom && !it.isHost }"))
        assertTrue(hold.contains(".distinctUntilChanged()"))
        assertTrue(hold.contains(".collect { guest -> onMain { applyGuestShuffle(guest) } }"))
        assertTrue(hold.contains("val wanted = shuffleHold.onRole(guest, shuffleOn) ?: return"))
        assertTrue(hold.contains("service.setShuffleForRoom(wanted)"))
        val attach = bridge.substringAfter("private fun attachListener(player: ExoPlayer?) {").substringBefore("private val playerListener")
        assertTrue(attach.contains("applyGuestShuffle(session.state.value.let { it.inRoom && !it.isHost })"))
    }

    @Test
    fun theServiceHoldsAndRestoresWithoutSavingItAsTheUsersChoice() {
        val service = text("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val set = service.substringAfter("fun setShuffleForRoom(enabled: Boolean) {").substringBefore("\n    }\n")
        assertTrue(set.contains("suppressShuffleModePersist = true"))
        assertTrue("giving it back is not a user activation", set.contains("suppressShuffleActivationReset = true"))
        assertTrue(set.contains("player.shuffleModeEnabled = enabled"))
    }

    @Test
    fun anyOtherDoorThatTurnsShuffleOnForAGuestIsUndoneBeforeItStartsALapOrIsSaved() {
        val service = text("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val callback = service.substringAfter("override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {")
        val guard = callback.indexOf("if (shuffleModeEnabled && ::listenTogetherManager.isInitialized && listenTogetherManager.shouldBlockPlaybackChanges()) {")
        assertTrue(guard >= 0)
        assertTrue("before the lap starts", guard < callback.indexOf("scope.launch { beginShuffleSession(isUserActivation = isUserActivation) }"))
        assertTrue("before it is saved", guard < callback.indexOf("settings[ShuffleModeKey] = shuffleModeEnabled"))
        assertTrue(callback.contains("scope.launch { if (listenTogetherManager.shouldBlockPlaybackChanges()) setShuffleForRoom(false) }"))
    }
}
