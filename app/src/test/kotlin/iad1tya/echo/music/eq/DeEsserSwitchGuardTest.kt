package iad1tya.echo.music.eq

import iad1tya.echo.music.constants.DeEsserDefault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 793 — the de-esser gets its own switch (equalizer ▸ Efectos), default ON so an update changes nothing.
 *
 * Pinned by source, like GlueCompressorDefaultParityTest: none of the read sites (service, screen, native
 * chain) is reachable from a JVM test. Two defaults drifting apart is the row 325 bug class — the switch
 * shows one state while the DSP runs the other — and a native branch that ignores the flag is a placebo.
 */
class DeEsserSwitchGuardTest {

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(path: String): String {
        val f = File(repoRoot, path)
        assertTrue("$path moved — update this test to follow it", f.isFile)
        return f.readText()
    }

    private val bridge by lazy { source("app/src/main/cpp/superpowered/SuperpoweredBridge.cpp") }
    private val processor by lazy {
        source("app/src/main/kotlin/com/music/echo/eq/audio/CustomEqualizerAudioProcessor.kt")
    }

    @Test
    fun `the default is ON`() {
        assertTrue(DeEsserDefault)
    }

    @Test
    fun `every read of the key in main sources uses the shared default`() {
        val mainRoot = File(repoRoot, "app/src/main/kotlin")
        val reads = mutableListOf<String>()
        mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "PreferenceKeys.kt" }.forEach { f ->
            val text = f.readText()
            Regex("""DeEsserEnabledKey\s*(\]|,)""").findAll(text).forEach { m ->
                val window = text.substring(m.range.first, minOf(text.length, m.range.last + 220))
                reads += f.name
                assertTrue(
                    "${f.name} reads DeEsserEnabledKey without DeEsserDefault: $window",
                    window.contains("DeEsserDefault"),
                )
            }
        }
        assertEquals(
            "expected one read in MusicService (collector) and one in AxionEqScreen (switch), found: $reads",
            listOf("AxionEqScreen.kt", "MusicService.kt"),
            reads.sorted(),
        )
    }

    @Test
    fun `the service hint and the processor field start at the shared default`() {
        val service = source("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val hint = Regex("""private var deEsserEnabledHint: Boolean = ([\w.]+)""").find(service)
            ?: error("deEsserEnabledHint not found in MusicService.kt")
        assertTrue(hint.groupValues[1].endsWith("DeEsserDefault"))

        val field = Regex("""private var deEsserEnabled = ([\w.]+)""").find(processor)
            ?: error("deEsserEnabled not found in CustomEqualizerAudioProcessor.kt")
        assertTrue(field.groupValues[1].endsWith("DeEsserDefault"))
    }

    @Test
    fun `the preference key name is stable`() {
        // Renaming the stored key silently resets everyone's choice to the default.
        val keys = source("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertEquals(
            1,
            Regex("""val DeEsserEnabledKey = booleanPreferencesKey\("eq_de_esser_enabled"\)""").findAll(keys).count(),
        )
    }

    @Test
    fun `the processor publishes the de-esser only through pushToneStages, with the master EQ gate`() {
        // Row 240: pushToneStages is the ONLY caller of the native tone setters.
        val calls = Regex("""(?<!fun )setDeEsserEnabled\(ptr""").findAll(processor).count()
        assertEquals("setDeEsserEnabled must have exactly one call site (pushToneStages)", 1, calls)
        val push = processor.substringAfter("private fun pushToneStages(ptr: Long) {").substringBefore("\n    }\n")
        assertTrue(push.contains("deEsserEnabled = deEsserEnabled,"))
        assertTrue(push.contains("setDeEsserEnabled(ptr, plan.deEsser)"))
    }

    @Test
    fun `the native snapshot defaults the de-esser ON`() {
        assertTrue(bridge.contains("bool deEsserEnabled = true;"))
        assertTrue(bridge.contains("bool activeDeEsserEnabled = true;"))
        assertTrue(bridge.contains("activeDeEsserEnabled = s.deEsserEnabled;"))
    }

    @Test
    fun `the native de-esser branch checks its switch`() {
        assertTrue(
            "the de-esser branch must run only when activeDeEsserEnabled",
            bridge.contains("if (processor->activeDeEsserEnabled && processor->deEsser && processor->deEsserDetector) {"),
        )
        // Switched off, its envelope and gain start again from flat.
        val offBranch = bridge.substringAfter("if (processor->activeDeEsserEnabled && processor->deEsser")
            .substringAfter("} else {").substringBefore("}")
        assertTrue(offBranch.contains("processor->currentDeEsserDb = 0.0f;"))
        assertTrue(offBranch.contains("processor->deEsserEnv = 0.0f;"))
    }

    @Test
    fun `the JNI setter exists with the exact mangled name`() {
        assertTrue(
            bridge.contains(
                "Java_iad1tya_echo_music_eq_audio_CustomEqualizerAudioProcessor_setDeEsserEnabled(" +
                    "JNIEnv *env, jobject thiz, jlong ptr, jboolean enabled)",
            ),
        )
        assertTrue(bridge.contains("processor->staging.deEsserEnabled = (enabled == JNI_TRUE);"))
        assertTrue(processor.contains("private external fun setDeEsserEnabled(ptr: Long, enabled: Boolean)"))
    }
}
