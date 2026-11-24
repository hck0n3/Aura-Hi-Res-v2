package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 812 (owner H17, 2026-11-24): crossfade curve 4 is labelled after what CrossfadeMath really does (both songs
 * together, one lowering while the other rises). Only the label changed: the stored values and the key stay as they were,
 * so nobody's saved curve moves on update.
 */
class CrossfadeCurveLabelGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `curve 4 carries the clearer name and the old misleading one is gone`() {
        val settings = main("ui/screens/settings/PlayerSettings.kt")
        assertTrue(settings.contains("4 -> \"Cruce natural (una baja mientras la otra sube)\""))
        assertFalse(settings.contains("Ascenso (entra rápido, sale lento)"))
    }

    @Test
    fun `stored values and key are unchanged`() {
        val settings = main("ui/screens/settings/PlayerSettings.kt")
        assertTrue(settings.contains("values = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8),"))
        assertTrue(settings.contains("onCrossfadeCurveChange(it)"))
        val keys = main("constants/PreferenceKeys.kt")
        assertTrue(keys.contains("val CrossfadeCurveKey = intPreferencesKey(\"crossfadeCurve\")"))
    }
}
