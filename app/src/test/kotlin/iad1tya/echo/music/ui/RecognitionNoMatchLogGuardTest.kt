package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AGENTS.md rule 4: WARN reaches the shared app.log and LogRedaction does not strip titles or artists, so the
 * recognition "no confident match" line keeps only the count of searched songs.
 */
class RecognitionNoMatchLogGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    @Test
    fun `the no-match warning carries no recognised title or artist`() {
        val file = File(root, "app/src/main/kotlin/com/music/echo/ui/screens/recognition/RecognitionScreen.kt")
        assertTrue("RecognitionScreen.kt moved: update this test", file.isFile)
        val source = file.readText()
        val start = source.indexOf("Timber.w(\"Recognition: no confident YouTube Music match")
        assertTrue("the no-match warning is gone", start >= 0)
        val call = source.substring(start, source.indexOf('\n', start))
        assertTrue(call.contains("songs.size)"))
        assertFalse(call.contains("result.title"))
        assertFalse(call.contains("result.artist"))
        assertFalse(call.contains("%s"))
    }
}
