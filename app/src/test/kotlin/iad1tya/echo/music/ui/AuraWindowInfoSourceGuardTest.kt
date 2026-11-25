package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 869 (plan, phase 1 item 1): the window is measured in ONE place (ui/newui/AuraWindowInfo*.kt), provided once
 * at the root, and nothing else reads the raw window size to decide a layout. A new `Configuration.screenWidthDp` or
 * `LocalWindowInfo.current` read elsewhere is a sixth measurement — exactly what this layer removed. There is no Android SDK
 * in the unit job, so the rule is pinned on the source text.
 */
class AuraWindowInfoSourceGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val mainDir get() = File(root, "app/src/main/kotlin")

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    /** Files allowed to read the raw window, and why. Everything else asks `auraWindowInfo()`. */
    private val allowed = mapOf(
        // The layer itself.
        "AuraWindowInfoLocal.kt" to "measures the window once for everyone",
        // The share-as-image of the lyrics sizes a BITMAP from the window's pixels: not a layout decision.
        "Lyrics.kt" to "pixel size of the shared lyrics image",
    )

    private val rawRead = Regex("""\.screen(Width|Height)Dp\b|LocalWindowInfo\.current""")

    private fun codeOf(line: String): String {
        val t = line.trimStart()
        if (t.startsWith("*") || t.startsWith("/*") || t.startsWith("//")) return ""
        return line.substringBefore("//")
    }

    @Test
    fun `nothing outside the window layer reads the raw window size`() {
        val offenders = mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in allowed }
            .flatMap { f ->
                f.readLines().withIndex()
                    .filter { (_, line) -> rawRead.containsMatchIn(codeOf(line)) }
                    .map { (i, _) -> "${f.name}:${i + 1}" }
            }.toList()
        assertEquals("lecturas crudas de la ventana fuera de AuraWindowInfo: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `the allow-list is not stale`() {
        val lyrics = main("ui/component/Lyrics.kt")
        assertTrue("si Lyrics.kt ya no lee la ventana, sácalo de la lista", lyrics.contains("LocalWindowInfo.current"))
    }

    @Test
    fun `the window is provided once at the root, around the terms and licence gates`() {
        val main = main("MainActivity.kt")
        val content = main.substringAfter("setContent {").substringBefore("iad1tya.echo.music.legal.TermsGate {")
        assertTrue(content.contains("iad1tya.echo.music.ui.newui.ProvideAuraWindowInfo {"))
        assertEquals("one provider, not one per screen", 1, Regex("ProvideAuraWindowInfo \\{").findAll(main).count())
        assertTrue("the rail's landscape test reads the layer", main.contains("val isLandscape = auraWindow.isLandscape"))
    }

    @Test
    fun `the existing wide helpers answer from the layer with their old rules`() {
        val tv = main("ui/utils/TvUi.kt")
        assertTrue(tv.contains("val width = iad1tya.echo.music.ui.newui.auraPaneWidthDp().dp"))
        assertTrue(tv.contains("return width >= WIDE_LAYOUT_BREAKPOINT_DP.dp"))
        assertTrue(tv.contains("val wide = isWideLayoutWindow(window.widthDp, window.heightDp)"))
        val recipe = main("ui/newui/AuraWindowRecipe.kt")
        // Row 874 (wave 2): the floating-window recipe now reads the same window size as the wide shell above.
        assertTrue(recipe.contains("val width = window.widthDp"))
        assertTrue(recipe.contains("val height = window.heightDp"))
        assertTrue(main("ui/newui/AuraTrackColumns.kt").contains("auraTrackColumnsFit(auraPaneWidthDp().dp)"))
    }

    @Test
    fun `posture and hinges come from the flow the adaptive library already collects, with no new dependency`() {
        val local = main("ui/newui/AuraWindowInfoLocal.kt")
        assertTrue(local.contains("currentWindowAdaptiveInfoV2().windowPosture"))
        assertFalse("no polling of the window", local.contains("while (") || local.contains("withFrameNanos"))
        assertFalse(
            "androidx.window is only a transitive dependency of the adaptive library; declaring it is an owner decision",
            File(root, "app/build.gradle.kts").readText().contains("androidx.window"),
        )
    }
}
