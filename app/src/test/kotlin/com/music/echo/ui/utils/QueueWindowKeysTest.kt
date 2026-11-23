package iad1tya.echo.music.ui.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 767: queue rows get collision-free keys instead of `uid.hashCode()`. */
class QueueWindowKeysTest {
    /** Two different uids with the SAME hashCode: exactly what used to produce a duplicate LazyColumn key. */
    private class CollidingUid(val name: String) {
        override fun hashCode(): Int = 42
        override fun equals(other: Any?): Boolean = other is CollidingUid && other.name == name
    }

    @Test
    fun `different uids with the same hashCode get different keys`() {
        val a = CollidingUid("a")
        val b = CollidingUid("b")
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(QueueWindowKeys.keyOf(a), QueueWindowKeys.keyOf(b))
    }

    @Test
    fun `equal uids share one key across timeline refreshes`() {
        val first = Pair(Any(), "window")
        val refreshed = first.copy()
        assertEquals(QueueWindowKeys.keyOf(first), QueueWindowKeys.keyOf(refreshed))
    }

    @Test
    fun `a forgotten uid never gets back a key that is in use`() {
        val kept = CollidingUid("kept")
        val dropped = CollidingUid("dropped")
        val keptKey = QueueWindowKeys.keyOf(kept)
        val droppedKey = QueueWindowKeys.keyOf(dropped)
        QueueWindowKeys.retainOnly(listOf(kept))
        assertEquals(keptKey, QueueWindowKeys.keyOf(kept))
        val again = QueueWindowKeys.keyOf(dropped)
        assertNotEquals(droppedKey, again)
        assertNotEquals(keptKey, again)
    }

    @Test
    fun `no queue list keys its rows by uid hashCode`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val aura = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraQueue.kt").readText()
        val classic = File(root, "app/src/main/kotlin/com/music/echo/ui/player/Queue.kt").readText()
        for (source in listOf(aura, classic)) {
            assertFalse(source.contains("uid.hashCode()"))
            assertTrue(source.contains("iad1tya.echo.music.ui.utils.QueueWindowKeys.retainOnly(queueWindows.map { it.uid })"))
        }
        assertTrue(aura.contains("\"aura_queue_song_\${iad1tya.echo.music.ui.utils.QueueWindowKeys.keyOf(window.uid)}\""))
        assertTrue(classic.contains("key = { _, item -> iad1tya.echo.music.ui.utils.QueueWindowKeys.keyOf(item.uid) },"))
        assertTrue(classic.contains("key = iad1tya.echo.music.ui.utils.QueueWindowKeys.keyOf(window.uid),"))
    }
}
