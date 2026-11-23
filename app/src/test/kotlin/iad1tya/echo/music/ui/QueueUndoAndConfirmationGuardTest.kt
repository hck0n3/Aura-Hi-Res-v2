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
}
