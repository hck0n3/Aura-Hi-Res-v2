package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: (1) «la parte de cuando entro al historial no tiene los mismos colores de la app»;
 * (2) «cuando le doy compartir … siempre sea a través de link de song.link».
 */
class HistoryAndShareGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `history wears the cover ground and accents and no longer paints raw Material surfaces under its headers`() {
        val src = main("ui/screens/HistoryScreen.kt")
        assertTrue(src.contains("AuraCoverThemeScope(enabled = auraDark)"))
        assertTrue(src.contains("auraScreenBackground(bloom"))
        assertTrue(src.contains("AuraChip("))
        // Inside the scope surface/background are transparent: the sticky bands must use the explicit ground.
        assertFalse(src.contains(".background(MaterialTheme.colorScheme.surface)"))
        assertFalse(src.contains(".background(MaterialTheme.colorScheme.background)"))
        // The skin is read OUTSIDE the scope (inside it the dark-ground test would always be true).
        assertTrue(src.indexOf("rememberAuraPanelSkin()") < src.indexOf("AuraCoverThemeScope(enabled"))
    }

    @Test
    fun `every share builds its text through ShareLinks, never from a raw YouTube Music URL`() {
        val dir = File(root, "app/src/main/kotlin/com/music/echo/ui")
        val offenders = dir.walkTopDown().filter { it.extension == "kt" }.filter { f ->
            val t = f.readText()
            Regex("""EXTRA_TEXT,\s*[^\n]*(\.shareLink|ShareLinks\.(song|playlist|channel|album)\()""").containsMatchIn(t) ||
                t.contains("\"https://music.youtube.com/playlist?list=\$pid\"")
        }.map { it.name }.toList()
        assertTrue("shares fuera de ShareLinks.*ShareText: $offenders", offenders.isEmpty())
        // The receiving side unwraps what Aura shares.
        assertTrue(main("MainActivity.kt").contains("songLinkWrappedYouTubeUrl("))
    }
}
