package iad1tya.echo.music.playback.audio

import iad1tya.echo.music.constants.GlueCompressorDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 712 — «Compresor suave (pegamento)» is OFF by default, and the switch (SoundSettings), the service
 * hint, the mastering collector and the offload gate (MusicService) read the SAME default. Two literals
 * drifting apart is the row 325 bug class: the switch shows one state while the DSP runs the other, and
 * nothing errors. Pinned by source, like Float32ProcessingDefaultParityTest, because none of the read
 * sites is reachable from a JVM test.
 */
class GlueCompressorDefaultParityTest {

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
        assertFalse(GlueCompressorDefault)
    }

    @Test
    fun `every read of the key in main sources uses the shared default`() {
        val mainRoot = File(repoRoot, "app/src/main/kotlin")
        val reads = mutableListOf<String>()
        mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "PreferenceKeys.kt" }.forEach { f ->
            val text = f.readText()
            // A key access `[...GlueCompressorEnabledKey]` or a rememberPreference(…Key, …) call.
            Regex("""GlueCompressorEnabledKey\s*(\]|,)""").findAll(text).forEach { m ->
                // Row 799: the one-shot write (`…Key] = false`) is not a read; `no other write …` pins it below.
                if (Regex("""^\]\s*=[^=]""").containsMatchIn(text.substring(m.range.last))) return@forEach
                val window = text.substring(m.range.first, minOf(text.length, m.range.last + 220))
                reads += "${f.name}: ${window.lineSequence().take(3).joinToString(" ").trim()}"
                assertTrue(
                    "${f.name} lee GlueCompressorEnabledKey sin GlueCompressorDefault: $window",
                    window.contains("GlueCompressorDefault"),
                )
            }
        }
        assertTrue(
            "se esperaban las lecturas de MusicService (2) y SoundSettings (1), encontradas: $reads",
            reads.count { it.startsWith("MusicService.kt") } == 2 && reads.count { it.startsWith("SoundSettings.kt") } == 1,
        )
    }

    @Test
    fun `the service hint starts at the shared default`() {
        val service = source("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val decl = Regex("""private var glueCompressorHint: Boolean = ([\w.]+)""").find(service)
            ?: error("glueCompressorHint no encontrado en MusicService.kt")
        assertTrue(decl.groupValues[1].endsWith("GlueCompressorDefault"))
    }

    @Test
    fun `the only write of the key is the one-shot OFF migration`() {
        // Row 712 kept every saved choice (no migration). Row 799 (owner decision H16, 2026-11-24) replaced that
        // order: whoever had it saved ON hears it once more, so it is written OFF exactly once, behind its own
        // flag, and the switch is the user's again afterwards. Any other write would override his choice.
        val write = Regex("""\[[\w.]*GlueCompressorEnabledKey\]\s*=[^=]""")
        val writes = mutableListOf<String>()
        File(repoRoot, "app/src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            write.findAll(f.readText()).forEach { writes += "${f.name}: ${it.value}" }
        }
        assertEquals("se esperaba una sola escritura (App.kt), encontradas: $writes", 1, writes.size)
        assertTrue(writes.single().startsWith("App.kt:"))

        val app = source("app/src/main/kotlin/com/music/echo/App.kt")
        val migration = app.substringAfter("private suspend fun migrateGlueCompressorOff20261124(")
            .substringBefore("\n    }\n")
        assertTrue("the flag gates it", migration.contains("if (settings[iad1tya.echo.music.constants.GlueCompressorOff20261124AppliedKey] == true) return"))
        assertTrue(migration.contains("it[iad1tya.echo.music.constants.GlueCompressorEnabledKey] = false"))
        assertTrue("value and flag in one edit", migration.contains("it[iad1tya.echo.music.constants.GlueCompressorOff20261124AppliedKey] = true"))
        assertTrue("logged once, at INFO", migration.contains("Timber.i(\"GLUE_COMPRESSOR_OFF_ONCE was_on=%b\", wasOn)"))
        assertTrue("it runs at startup", app.contains("        migrateGlueCompressorOff20261124(settings)\n"))
        val keys = source("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertTrue(keys.contains("val GlueCompressorOff20261124AppliedKey = booleanPreferencesKey(\"glue_compressor_off_20261124_applied\")"))
    }

    @Test
    fun `the preference key name is stable`() {
        // Renaming the stored key silently resets everyone's choice to the default.
        val keys = source("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertEquals(
            1,
            Regex("""val GlueCompressorEnabledKey = booleanPreferencesKey\("mastering_glue_compressor_enabled"\)""")
                .findAll(keys).count(),
        )
    }
}
