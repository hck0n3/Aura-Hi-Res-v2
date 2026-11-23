package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 733 (UX audit P22B): the queue's own wording, the confirmations of queue/playlist actions from the menus,
 * and «Deshacer» after swiping the player away. Pinned as source text because none of it fails to compile when lost.
 */
class QueueUndoAndConfirmationGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun res(path: String) =
        File(root, "app/src/main/res/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun stringValue(xml: String, name: String): String =
        Regex("""<string name="$name">([^<]*)</string>""").find(xml)?.groupValues?.get(1)
            ?: error("missing string $name")

    @Test
    fun `removing a song from the queue says queue, not playlist`() {
        assertTrue(stringValue(res("values/strings.xml"), "removed_song_from_playlist").endsWith("from the queue"))
        assertTrue(stringValue(res("values-es/strings.xml"), "removed_song_from_playlist").endsWith("de la cola"))
        assertTrue(stringValue(res("values-es-rUS/strings.xml"), "removed_song_from_playlist").endsWith("de la cola"))
        // The key is used ONLY by the two queue screens' swipe-to-remove; a playlist screen must keep its own string.
        assertTrue(main("ui/newui/AuraQueue.kt").contains("R.string.removed_song_from_playlist"))
        assertTrue(main("ui/player/Queue.kt").contains("R.string.removed_song_from_playlist"))
        assertFalse(main("ui/newui/AuraLocalPlaylistScreen.kt").contains("R.string.removed_song_from_playlist"))
        assertFalse(main("ui/screens/playlist/LocalPlaylistScreen.kt").contains("R.string.removed_song_from_playlist"))
    }

    @Test
    fun `a queue edit is confirmed only when it really happens on this device`() {
        assertTrue(iad1tya.echo.music.ui.menu.MenuActionConfirmation.queueEditConfirms(listenTogetherGuest = false, controllingAnotherDevice = false))
        // Guest: PlayerConnection refuses with its own toast. Remote control: the service refuses with its notice (rows 682/686).
        assertFalse(iad1tya.echo.music.ui.menu.MenuActionConfirmation.queueEditConfirms(listenTogetherGuest = true, controllingAnotherDevice = false))
        assertFalse(iad1tya.echo.music.ui.menu.MenuActionConfirmation.queueEditConfirms(listenTogetherGuest = false, controllingAnotherDevice = true))
    }

    @Test
    fun `play next and add to queue confirm from every song and selection menu`() {
        val expected = mapOf(
            "ui/menu/SongMenu.kt" to 1,
            "ui/menu/YouTubeSongMenu.kt" to 1,
            "ui/menu/SelectionSongsMenu.kt" to 2,
            "ui/menu/YouTubeSelectionSongMenu.kt" to 1,
        )
        for ((path, n) in expected) {
            val src = main(path)
            assertTrue(path, Regex("""confirmQueueEdit\(context, playerConnection, R\.string\.play_next\)""").findAll(src).count() == n)
            assertTrue(path, Regex("""confirmQueueEdit\(context, playerConnection, R\.string\.add_to_queue\)""").findAll(src).count() == n)
        }
        val helper = main("ui/menu/MenuActionConfirmation.kt")
        assertTrue(helper.contains("Toast.makeText(context, label, Toast.LENGTH_SHORT).show()"))
        // The remote check is the SAME predicate the service refuses with (refuseQueueEditInRemoteMode reads RemotePlayback.target).
        assertTrue(helper.contains("RemotePlayback.active"))
        assertTrue(main("devicesync/RemotePlayback.kt").contains("val active: Boolean get() = target.value != null"))
        // The guest check must not be shouldBlockPlaybackChanges?.invoke(): that one toasts as a side effect (double toast).
        assertFalse(helper.contains("shouldBlockPlaybackChanges?.invoke()"))
    }

    @Test
    fun `adding to a list confirms with the list's name, only when something was added`() {
        val dialog = main("ui/menu/AddToPlaylistDialog.kt")
        // Several callers hand back an empty list when they have nothing to add (offline, unresolved song): no «Añadido».
        assertTrue(dialog.contains("database.addSongToPlaylist(playlist, songIds!!)\n                                if (songIds!!.isNotEmpty()) confirmAddedToPlaylist(context, playlist.playlist.name)"))
        assertTrue(dialog.contains("if (addedIds.isNotEmpty()) confirmAddedToPlaylist(context, playlist.playlist.name)"))
        assertTrue(Regex("""confirmAddedToPlaylist\(context, playlist\.playlist\.name\)""").findAll(dialog).count() == 3)
        val online = main("ui/menu/AddToPlaylistDialogOnline.kt")
        assertTrue(online.contains("if (added.get() > 0) confirmAddedToPlaylist(context, playlist.playlist.name)"))
        // The add runs on Dispatchers.IO: the toast is posted to the main looper, never shown from the IO thread.
        assertTrue(main("ui/menu/MenuActionConfirmation.kt").contains("Handler(Looper.getMainLooper()).post {"))
        assertTrue(stringValue(res("values/strings.xml"), "added_to_playlist_toast").contains("%s"))
        assertTrue(stringValue(res("values-es/strings.xml"), "added_to_playlist_toast") == "Añadido a «%s»")
        assertTrue(stringValue(res("values-es-rUS/strings.xml"), "added_to_playlist_toast") == "Añadido a «%s»")
    }

    // ── «Cola cerrada · Deshacer» ────────────────────────────────────────────────────────────────────────────────────
    private val rule = iad1tya.echo.music.playback.DismissedQueueUndoRule

    @Test
    fun `the undo is offered only for a real local queue`() {
        assertTrue(rule.canOffer(itemCount = 12, controllingAnotherDevice = false, listenTogetherGuest = false))
        assertFalse("nothing to bring back", rule.canOffer(itemCount = 0, controllingAnotherDevice = false, listenTogetherGuest = false))
        assertFalse("remote mirror stays as it was", rule.canOffer(itemCount = 12, controllingAnotherDevice = true, listenTogetherGuest = false))
        assertFalse("guest stays as it was", rule.canOffer(itemCount = 12, controllingAnotherDevice = false, listenTogetherGuest = true))
    }

    @Test
    fun `the undo restores only into an empty player, inside the window, outside remote and guest mode`() {
        val expires = 10_000L
        assertTrue(rule.canRestore(nowElapsed = 9_000L, expiresAtElapsed = expires, playerItemCount = 0, controllingAnotherDevice = false, listenTogetherGuest = false))
        // A tap landing right at the close still counts…
        assertTrue(rule.canRestore(nowElapsed = expires + rule.TAP_GRACE_MS, expiresAtElapsed = expires, playerItemCount = 0, controllingAnotherDevice = false, listenTogetherGuest = false))
        // …but not one after it.
        assertFalse(rule.canRestore(nowElapsed = expires + rule.TAP_GRACE_MS + 1, expiresAtElapsed = expires, playerItemCount = 0, controllingAnotherDevice = false, listenTogetherGuest = false))
        assertFalse("he already started something else: never overwrite it", rule.canRestore(nowElapsed = 9_000L, expiresAtElapsed = expires, playerItemCount = 3, controllingAnotherDevice = false, listenTogetherGuest = false))
        assertFalse(rule.canRestore(nowElapsed = 9_000L, expiresAtElapsed = expires, playerItemCount = 0, controllingAnotherDevice = true, listenTogetherGuest = false))
        assertFalse(rule.canRestore(nowElapsed = 9_000L, expiresAtElapsed = expires, playerItemCount = 0, controllingAnotherDevice = false, listenTogetherGuest = true))
    }

    @Test
    fun `the snackbar shows what is left of the window and never a negative time`() {
        assertTrue(rule.remainingMs(nowElapsed = 1_000L, expiresAtElapsed = 7_000L) == 6_000L)
        assertTrue(rule.remainingMs(nowElapsed = 8_000L, expiresAtElapsed = 7_000L) == 0L)
        assertTrue(rule.UNDO_WINDOW_MS == 6_000L)
    }

    @Test
    fun `swiping the player away snapshots the queue first and the undo restores it paused through the boot-restore call`() {
        val dismiss = main("ui/newui/AuraPlayer.kt").substringAfter("DESTRUCTIVE GESTURE, PRESERVED VERBATIM")
            .substringAfter("onDismiss = {").substringBefore("\n        },")
        val capture = dismiss.indexOf("iad1tya.echo.music.playback.DismissedQueueUndo.capture(playerConnection.service)")
        assertTrue(capture >= 0 && capture < dismiss.indexOf("playerConnection.player.stop()"))
        assertTrue(capture < dismiss.indexOf("playerConnection.player.clearMediaItems()"))
        // The gesture itself is unchanged (row 718 included).
        assertTrue(dismiss.contains("playerConnection.service.queueGeneration++"))
        assertTrue(dismiss.contains("playerConnection.service.clearAutomix()"))

        val undo = main("playback/DismissedQueueUndo.kt")
        assertTrue(undo.contains("playWhenReady = false,\n            isRestore = true,"))
        assertTrue(undo.contains("startIndex = snap.index,") && undo.contains("position = snap.positionMs,"))
        assertTrue(undo.contains("contextId = snap.contextId,") && undo.contains("title = snap.title,"))
        assertTrue(undo.contains("player.repeatMode = snap.repeatMode") && undo.contains("player.shuffleModeEnabled = snap.shuffleModeEnabled"))
        assertTrue(undo.contains("RemotePlayback.active") && undo.contains("shouldBlockPlaybackChanges()"))
        // Rule 7: one delayed message frees the snapshot — no polling.
        assertTrue(undo.contains("mainHandler.postDelayed({ drop(token) }"))
        assertFalse(undo.contains("while (") || undo.contains("repeat("))

        val shell = main("MainActivity.kt")
        assertTrue(shell.contains("iad1tya.echo.music.playback.DismissedQueueUndo.offer.collectLatest { offer ->"))
        assertTrue(shell.contains("iad1tya.echo.music.playback.DismissedQueueUndo.restore(connection.service, offer.token)"))
        assertTrue(shell.contains("iad1tya.echo.music.playback.DismissedQueueUndo.drop(offer.token)"))
        assertTrue(shell.contains("actionLabel = queueClosedUndoLabel,"))
        assertTrue(stringValue(res("values/strings.xml"), "queue_closed_undo") == "Queue closed")
        assertTrue(stringValue(res("values-es/strings.xml"), "queue_closed_undo") == "Cola cerrada")
        assertTrue(stringValue(res("values-es-rUS/strings.xml"), "queue_closed_undo") == "Cola cerrada")
    }
}
