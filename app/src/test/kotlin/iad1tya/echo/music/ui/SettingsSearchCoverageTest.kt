package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every row a user can READ on a settings screen must be reachable from the settings search.
 *
 * Owner order (2026-10-31): "que la búsqueda de ajustes tenga todo indexado". A title or description
 * passed to `title = { Text(stringResource(R.string.x)) }` / `description = { … }` in a settings screen
 * has to appear in `SearchableSettings.kt`, or be listed in [NOT_A_ROW] with the reason (screen titles are
 * the top-level index rows, dialogs and logout confirmations are not settings).
 */
class SettingsSearchCoverageTest {
    private val dir =
        File(
            generateSequence(File("").absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "settings.gradle.kts").isFile }
                ?: error("could not locate the repository root from ${File("").absolutePath}"),
            "app/src/main/kotlin/com/music/echo/ui/screens/settings",
        )

    private val row =
        Regex("""(?:title|description)\s*=\s*\{\s*Text\(\s*(?:text\s*=\s*)?stringResource\(R\.string\.(\w+)\)""")

    @Test
    fun everySettingsRowIsIndexed() {
        val index = File(dir, "SearchableSettings.kt").readText()
        val indexed = Regex("""R\.string\.(\w+)""").findAll(index).map { it.groupValues[1] }.toSet()
        val missing = mutableListOf<String>()
        dir.walkTopDown().filter { it.extension == "kt" && it.name != "SearchableSettings.kt" }.forEach { f ->
            row.findAll(f.readText()).map { it.groupValues[1] }.distinct().forEach { name ->
                if (name !in indexed && name !in NOT_A_ROW) missing += "${f.name}: $name"
            }
        }
        assertTrue("Settings rows missing from SearchableSettings.kt (add them or justify in NOT_A_ROW): $missing", missing.isEmpty())
    }

    private companion object {
        // Screen titles (top-level index rows already echo them), dialog titles and confirmations.
        val NOT_A_ROW = setOf(
            "about", "account", "more_content", "yt_sync", "logout_dialog_title", "ai_lyrics_translation",
            "appearance", "content", "logs", "privacy", "storage", "logs_desc", "login",
            "update_settings_title", "liquid_glass_player_not_available",
        )
    }
}
