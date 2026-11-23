package iad1tya.echo.music.reco

import iad1tya.echo.music.constants.DefaultOpenRouterModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry 765: with no model chosen, the daily AI playlist worker sent NO model (`?: ""`) while every screen sent
 * "google/gemini-2.5-flash-lite", so a user key got two different models. One constant now, and no default may drift.
 */
class AiModelDefaultTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private val mainDir = File(root, "app/src/main/kotlin/com/music/echo")

    @Test
    fun `the default model is the one the screens always used`() {
        assertEquals("google/gemini-2.5-flash-lite", DefaultOpenRouterModel)
    }

    @Test
    fun `the worker falls back to the shared default, never to an empty model`() {
        val worker = File(mainDir, "reco/AutoRecoPlaylistWorker.kt").readText()
        assertTrue(worker.contains("model = prefs[OpenRouterModelKey] ?: DefaultOpenRouterModel,"))
        assertFalse(worker.contains("prefs[OpenRouterModelKey] ?: \"\""))
        for (path in listOf("ui/component/AiPlaylistDialog.kt", "ui/menu/LyricsMenu.kt")) {
            assertTrue(path, File(mainDir, path).readText().contains("rememberPreference(OpenRouterModelKey, DefaultOpenRouterModel)"))
        }
    }

    @Test
    fun `no screen seeds the model preference with another default`() {
        val seed = Regex("rememberPreference\\(OpenRouterModelKey, ([^)]+)\\)")
        var seen = 0
        mainDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            for (m in seed.findAll(file.readText())) {
                seen++
                val arg = m.groupValues[1].trim()
                assertTrue("${file.name}: $arg", arg == "DefaultOpenRouterModel" || arg == "\"$DefaultOpenRouterModel\"")
            }
        }
        assertTrue(seen >= 3)
    }
}
