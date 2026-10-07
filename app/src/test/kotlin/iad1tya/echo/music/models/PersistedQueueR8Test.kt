package iad1tya.echo.music.models

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 345: the saved queue is Java-serialized, which stores CLASS NAMES. Every class inside it must keep
 * its name under R8, or each app update renames it and the queue is thrown away on the next start.
 */
class PersistedQueueR8Test {
    private val rules: String by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        File(root, "app/proguard-rules.pro").readText()
    }

    @Test
    fun everyPersistedClassKeepsItsName() {
        listOf(
            "-keep class iad1tya.echo.music.models.PersistQueue",
            "-keep class iad1tya.echo.music.models.PersistPlayerState",
            "-keep class iad1tya.echo.music.models.MediaMetadata {",
            "-keep class iad1tya.echo.music.models.MediaMetadata\$*",
            "-keep class iad1tya.echo.music.models.QueueData",
            "-keep class iad1tya.echo.music.models.QueueType",
        ).forEach { assertTrue("missing R8 rule: $it", rules.contains(it)) }
    }

    @Test
    fun persistedClassesStayInTheKeptPackage() {
        // If MediaMetadata or the persisted queue classes move, the keep rules above silently stop applying.
        listOf(PersistQueue::class, PersistPlayerState::class, MediaMetadata::class).forEach {
            assertTrue(it.java.name.startsWith("iad1tya.echo.music.models."))
        }
    }
}
