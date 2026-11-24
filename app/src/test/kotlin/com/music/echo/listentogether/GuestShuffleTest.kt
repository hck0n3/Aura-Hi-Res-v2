package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 848 (owner decision B2, 2026-11-24): inside a Listen Together room only the host decides the order. The policy, and a source
 * guard for every door through which a guest could turn shuffle on. Each literal below is grepped from the source it pins.
 */
class GuestShuffleTest {
    @Test
    fun aGuestMayNotShuffle() {
        assertFalse(GuestShuffle.mayShuffle(inRoom = true, isHost = false))
    }

    @Test
    fun theHostKeepsFullControl() {
        assertTrue(GuestShuffle.mayShuffle(inRoom = true, isHost = true))
    }

    @Test
    fun outsideARoomShuffleIsFree() {
        assertTrue(GuestShuffle.mayShuffle(inRoom = false, isHost = false))
        assertTrue(GuestShuffle.mayShuffle(inRoom = false, isHost = true))
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun thePlayersShuffleButtonIsGreyedForAGuestAndExplainsOnTap() {
        val player = text("app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt")
        val button = player.substringAfter("ActionLog.tap(ActionId.PLR_SHUFFLE)").substringBefore("icon = AuraIcons.SkipPrevious")
        assertTrue(button.contains("if (isListenTogetherGuest) {"))
        assertTrue(button.contains("iad1tya.echo.music.listentogether.explainGuestShuffleLocked(context)"))
        assertTrue("greyed, not hidden", button.contains("isListenTogetherGuest -> AuraPalette.OnGroundFaint.copy(alpha = AuraPalette.OnGroundFaint.alpha * 0.35f)"))
        assertFalse("a disabled button cannot say why", button.contains("enabled = !isListenTogetherGuest"))
    }

    @Test
    fun theQueueSheetsShuffleButtonIsGreyedForAGuestAndExplainsOnTap() {
        val queue = text("app/src/main/kotlin/com/music/echo/ui/newui/AuraQueue.kt")
        val button = queue.substringAfter("ActionLog.tap(ActionId.QUEUE_SHUFFLE)").substringBefore("painterId = R.drawable.repeat_one")
        assertTrue(button.contains("if (isListenTogetherGuest) {"))
        assertTrue(button.contains("iad1tya.echo.music.listentogether.explainGuestShuffleLocked(context)"))
        assertTrue(button.contains("isListenTogetherGuest -> AuraPalette.OnGroundDisabled.copy(alpha = AuraPalette.OnGroundDisabled.alpha * 0.35f)"))
        assertFalse(button.contains("enabled = !isListenTogetherGuest"))
    }

    @Test
    fun thePlayerMenusShuffleRowStaysOutOfAGuestsReach() {
        val menu = text("app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayerMenu.kt")
        val beforeRow = menu.substringBefore("ActionLog.tap(ActionId.PMENU_SHUFFLE)").takeLast(600)
        assertTrue(beforeRow.contains("if (!isListenTogetherGuest) {"))
    }

    @Test
    fun theLiveQueueShuffleIsRefusedForAGuest() {
        val connection = text("app/src/main/kotlin/com/music/echo/playback/PlayerConnection.kt")
        val shuffle = connection.substringAfter("fun shuffleLiveQueue(resetMemory: Boolean) {").substringBefore("service.shuffleLiveQueue(resetMemory)")
        assertTrue(shuffle.contains("if (shouldBlockPlaybackChanges?.invoke() == true) {"))
    }

    @Test
    fun theNotificationTheCarAndExternalControllersAreRefusedForAGuest() {
        val callback = text("app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt")
        assertTrue(callback.contains("if (playerCommand == androidx.media3.common.Player.COMMAND_SET_SHUFFLE_MODE && refusesGuestShuffle()) {"))
        assertTrue(callback.contains("MediaSessionConstants.ACTION_TOGGLE_SHUFFLE -> if (!refusesGuestShuffle()) {"))
        val refuse = callback.substringAfter("private fun refusesGuestShuffle(): Boolean {").substringBefore("override fun onCustomCommand(")
        assertTrue(refuse.contains("if (iad1tya.echo.music.listentogether.GuestShuffle.mayShuffle(manager.isInRoom, manager.isHost)) return false"))
        assertTrue(refuse.contains("iad1tya.echo.music.listentogether.explainGuestShuffleLocked(context)"))
    }

    @Test
    fun theExplanationExistsInEnglishAndSpanish() {
        assertTrue(text("app/src/main/res/values/echo_strings.xml").contains("<string name=\"listen_together_guest_shuffle_locked\">In a room only the host decides the order</string>"))
        val es = "<string name=\"listen_together_guest_shuffle_locked\">En una sala solo el anfitrión decide el orden</string>"
        assertTrue(text("app/src/main/res/values-es/echo_strings.xml").contains(es))
        assertTrue(text("app/src/main/res/values-es-rUS/echo_strings.xml").contains(es))
    }
}
