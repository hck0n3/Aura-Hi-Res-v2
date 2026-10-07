package iad1tya.echo.music.playback.audio

import iad1tya.echo.music.constants.Float32ProcessingDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plan A3 — «Procesado en 32 bits» is OFF by default, and the switch (SoundSettings) and the audio sink's
 * gate (MusicService) read the SAME default. Two literals drifting apart is the row 325 bug class: the
 * switch shows one state while the sink runs the other, and nothing errors. Pinned by source, like
 * HighTierGlassDefaultTest, because neither read site is reachable from a JVM test.
 */
class Float32ProcessingDefaultParityTest {

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(path: String): String {
        val f = File(repoRoot, path)
        assertTrue("$path se movió — actualiza este test para seguirlo", f.isFile)
        return f.readText()
    }

    @Test
    fun `the default is OFF`() {
        assertFalse(Float32ProcessingDefault)
    }

    @Test
    fun `every read of the key in main sources uses the shared default`() {
        val mainRoot = File(repoRoot, "app/src/main/kotlin")
        val reads = mutableListOf<String>()
        mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "PreferenceKeys.kt" }.forEach { f ->
            val text = f.readText()
            // A key access `[...Float32ProcessingEnabledKey]` or a rememberPreference(…Key, …) call.
            Regex("""Float32ProcessingEnabledKey\s*(\]|,)""").findAll(text).forEach { m ->
                val window = text.substring(m.range.first, minOf(text.length, m.range.last + 220))
                reads += "${f.name}: ${window.lineSequence().take(3).joinToString(" ").trim()}"
                assertTrue(
                    "${f.name} lee Float32ProcessingEnabledKey sin Float32ProcessingDefault: $window",
                    window.contains("Float32ProcessingDefault"),
                )
            }
        }
        assertTrue(
            "se esperaban las dos lecturas (MusicService + SoundSettings), encontradas: $reads",
            reads.any { it.startsWith("MusicService.kt") } && reads.any { it.startsWith("SoundSettings.kt") },
        )
    }

    @Test
    fun `the service hint starts at the shared default`() {
        val service = source("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val decl = Regex("""private var float32ProcessingHint: Boolean = ([\w.]+)""").find(service)
            ?: error("float32ProcessingHint no encontrado en MusicService.kt")
        assertTrue(decl.groupValues[1].endsWith("Float32ProcessingDefault"))
    }

    @Test
    fun `the preference key name is stable`() {
        // Renaming the stored key silently resets everyone's choice to the default.
        val keys = source("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertEquals(
            1,
            Regex("""val Float32ProcessingEnabledKey = booleanPreferencesKey\("mastering_float32_processing"\)""")
                .findAll(keys).count(),
        )
    }
}
