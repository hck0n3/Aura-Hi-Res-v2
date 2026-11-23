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
        assertTrue(dialog.contains("database.addSongToPlaylist(playlist, songIds!!)\n                                confirmAddedToPlaylist(context, playlist.playlist.name)"))
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
}
