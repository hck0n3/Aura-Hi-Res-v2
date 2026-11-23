package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review C2 of the device-control fixes: «Reproducir a continuación» while another device is controlled from here took the empty-player
 * branch on the IDLE mirror (new queue + prepare + play), was paused by the remote-mode guard, reached no device and was lost; «Añadir a
 * la cola» prepared the mirror. The other device has no command to queue a song: the edit is refused with a notice (ES + EN) and the
 * mirror is never touched.
 */
class RemoteQueueEditGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    private val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")

    private fun body(signature: String) = service.substringAfter(signature).substringBefore("\n    }\n")

    @Test
    fun `play next refuses before the empty-player branch can build, prepare and play a queue`() {
        val playNext = body("fun playNext(items: List<MediaItem>) {")
        val refuse = playNext.indexOf("if (refuseQueueEditInRemoteMode()) return")
        assertTrue(refuse >= 0)
        assertTrue(refuse < playNext.indexOf("adoptDirectQueue(items = items, title = null)"))
        assertTrue(refuse < playNext.indexOf("player.prepare()"))
    }

    @Test
    fun `add to queue refuses before it adds or prepares anything`() {
        val add = body("fun addToQueue(items: List<MediaItem>) {")
        val refuse = add.indexOf("if (refuseQueueEditInRemoteMode()) return")
        assertTrue(refuse >= 0)
        assertTrue(refuse < add.indexOf("player.addMediaItems(items)"))
        assertTrue(refuse < add.indexOf("player.prepare()"))
    }

    @Test
    fun `the automix pool keeps its song when the edit is refused, and automatic fills stay silent`() {
        for (sig in listOf("fun playNextAutomix(", "fun addToQueueAutomix(")) {
            val f = body(sig)
            assertTrue(sig, f.indexOf("if (refuseQueueEditInRemoteMode()) return") in 0 until f.indexOf("removeAt(position)"))
        }
        assertTrue(body("fun addNextUnheardAutomix() {").contains("if (refuseQueueEditInRemoteMode(notify = false)) return"))
        val fill = body("fun extendQueueForContext(contextId: String, items: List<MediaItem>) {")
        assertTrue(fill.indexOf("if (refuseQueueEditInRemoteMode(notify = false)) return") in 0 until fill.indexOf("addToQueue(items)"))
    }

    @Test
    fun `the refusal only applies while another device is controlled, and says so`() {
        val refuse = body("internal fun refuseQueueEditInRemoteMode(notify: Boolean = true): Boolean {")
        assertTrue(refuse.contains("val target = iad1tya.echo.music.devicesync.RemotePlayback.target.value ?: return false"))
        assertTrue(refuse.contains("R.string.device_sync_queue_edit_not_sent"))
        assertTrue(refuse.contains("if (notify) {"))
    }

    @Test
    fun `the notice exists in English and Spanish`() {
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            val xml = read("app/src/main/res/$dir/device_sync_queue_strings.xml")
            assertTrue(dir, xml.contains("name=\"device_sync_queue_edit_not_sent\"") && xml.contains("%1\$s"))
        }
    }
}
