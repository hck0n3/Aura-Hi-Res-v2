package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: «que la app pueda mostrar todo el contenido que es para mayores de edad, que no haya nada que
 * me oculte» (and 2026-10-19: «nunca quiero que restrinjas a los usuarios el contenido explícito»).
 *
 * "Ocultar contenido explícito" is gone. These tests pin the three things that make it impossible to come back
 * by accident: the retired preference, the inert filter helpers, and every remaining inline explicit filter
 * sitting behind that always-false preference.
 */
class ExplicitContentNeverHiddenGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun source(path: String) =
        File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun mainSources(): List<File> =
        listOf("app/src/main", "innertube/src/main").flatMap { dir ->
            File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    @Test
    fun `the hide-explicit preference is retired - it no longer shares the name users stored`() {
        val keys = source("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        val line = keys.lines().single { it.startsWith("val HideExplicitKey") }
        assertFalse("un valor guardado como hideExplicit=true volvería a ocultar", line.contains("\"hideExplicit\""))
    }

    @Test
    fun `no screen offers or writes the hide-explicit setting`() {
        for (f in mainSources()) {
            val text = f.readText()
            assertFalse("${f.name}: el interruptor volvió", text.contains("onHideExplicitChange"))
            assertFalse("${f.name}: la cadena del interruptor volvió", text.contains("R.string.hide_explicit"))
            assertFalse("${f.name}: nada puede escribir la clave", Regex("""HideExplicitKey\]\s*=[^=]""").containsMatchIn(text))
            assertFalse("${f.name}: nada puede escribir la clave", Regex("""edit\s*\{[^}]*HideExplicitKey""").containsMatchIn(text))
        }
    }

    @Test
    fun `the explicit filter helpers never remove anything`() {
        val yt = source("innertube/src/main/kotlin/com/music/innertube/models/YTItem.kt")
        assertTrue(yt.contains("fun <T : YTItem> List<T>.filterExplicit(enabled: Boolean = true): List<T> = this"))
        val ext = source("app/src/main/kotlin/com/music/echo/extensions/ListExt.kt")
        assertTrue(ext.contains("fun List<Song>.filterExplicit(enabled: Boolean = true): List<Song> = this"))
        assertTrue(ext.contains("fun List<Album>.filterExplicitAlbums(enabled: Boolean = true): List<Album> = this"))
        val queue = source("app/src/main/kotlin/com/music/echo/playback/queues/Queue.kt")
        assertTrue(queue.contains("fun List<MediaItem>.filterExplicit(enabled: Boolean = true): List<MediaItem> = this"))
        assertTrue(queue.contains("fun filterExplicit(enabled: Boolean = true): Status = this"))
    }

    @Test
    fun `every explicit-dropping filter is behind the retired preference`() {
        val dropsExplicit = Regex(
            """!\s*(it|a|v|s|song|item|album)\.(song\.|album\.)?explicit\b""" +
                """|\b\w+\.explicit\)\s*(continue|return)|if\s*\(\s*\w+\.explicit\s*\)\s*return@""",
        )
        for (f in mainSources()) {
            val lines = f.readLines()
            lines.forEachIndexed { i, line ->
                if (dropsExplicit.containsMatchIn(line) && !line.contains("Icon")) {
                    val context = lines.subList(maxOf(0, i - 5), i + 1).joinToString("\n")
                    assertTrue(
                        "${f.name}:${i + 1} descarta contenido explícito sin pasar por la preferencia retirada",
                        context.contains("ideExplicit"),
                    )
                }
            }
        }
    }

    @Test
    fun `no request tells YouTube to run in restricted or locked safety mode`() {
        val inner = File(root, "innertube/src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }
        for (f in inner) {
            val text = f.readText()
            assertFalse("${f.name}: lockedSafetyMode = true", Regex("""lockedSafetyMode\s*=\s*true""").containsMatchIn(text))
            assertFalse("${f.name}: restrictedMode", text.contains("restrictedMode", ignoreCase = true))
            assertFalse("${f.name}: enableSafetyMode", text.contains("enableSafetyMode", ignoreCase = true))
        }
        val context = source("innertube/src/main/kotlin/com/music/innertube/models/Context.kt")
        assertTrue(context.contains("val lockedSafetyMode: Boolean = false"))
        val clientBody = source("innertube/src/main/kotlin/com/music/innertube/models/body/PlayerBody.kt")
        assertTrue(clientBody.contains("val contentCheckOk: Boolean = true"))
        assertTrue(clientBody.contains("val racyCheckOk: Boolean = true"))
    }
}
