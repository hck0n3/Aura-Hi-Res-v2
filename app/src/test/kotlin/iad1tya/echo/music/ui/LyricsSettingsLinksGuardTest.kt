package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P22D (row 735): lyrics settings live in four places (Apariencia ▸ Letra, Contenido ▸ Letra, Romanización,
 * Traducción con IA). Nothing is moved (AGENTS.md rule 5: hiding is losing); each place shows the same
 * «Más ajustes de letras» block linking the others, and the settings search returns all four.
 */
class LyricsSettingsLinksGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()
    private fun res(path: String) = File(root, "app/src/main/res/$path").readText()

    // place -> (screen file, route, label string)
    private val places = mapOf(
        "APPEARANCE" to Triple("AppearanceSettings.kt", "settings/appearance", "lyrics_link_appearance"),
        "SOURCES" to Triple("ContentSettings.kt", "settings/content", "lyrics_link_sources"),
        "ROMANIZATION" to Triple("RomanizationSettings.kt", "settings/content/romanization", "lyrics_link_romanization"),
        "AI" to Triple("AiSettings.kt", "settings/ai", "lyrics_link_ai"),
    )

    @Test
    fun `each lyrics screen shows the block, marked with its own place`() {
        places.forEach { (place, p) ->
            val screen = src("ui/screens/settings/${p.first}")
            assertEquals(p.first, 1, screen.split("LyricsSettingsLinks(navController, LyricsSettingsPlace.$place)").size - 1)
        }
    }

    @Test
    fun `every place routes to a registered destination`() {
        val links = src("ui/screens/settings/LyricsSettingsLinks.kt")
        val nav = src("ui/screens/NavigationBuilder.kt")
        places.forEach { (place, p) ->
            assertTrue(place, links.contains("$place(\"${p.second}\")"))
            assertTrue("${p.second} registered", nav.contains("composable(\"${p.second}\")"))
            assertTrue("label of $place shown", links.contains("Triple(LyricsSettingsPlace.$place, "))
            assertTrue("label of $place shown", links.contains("stringResource(R.string.${p.third})"))
        }
        assertTrue("this screen's row is inert, the others navigate", links.contains("onClick = if (isHere) null else ({ navController.navigate(place.route) }),"))
    }

    @Test
    fun `the search returns all four places, each at the screen where its label is shown`() {
        val index = src("ui/screens/settings/SearchableSettings.kt")
        places.values.forEach { (_, route, label) ->
            assertTrue(label, index.contains("Triple(stringResource(R.string.$label), \"Más ajustes de letras\", \"$route\")"))
            assertTrue("header on $route", index.contains("Triple(stringResource(R.string.lyrics_more_settings), ") &&
                index.lines().any { it.contains("R.string.lyrics_more_settings") && it.contains("\"$route\")") })
        }
    }

    @Test
    fun `strings exist in English and Spanish and the Spanish ones match the query letras`() {
        val en = res("values/lyrics_links_strings.xml")
        val es = res("values-es/lyrics_links_strings.xml")
        (places.values.map { it.third } + listOf("lyrics_more_settings", "lyrics_link_here")).forEach { name ->
            assertTrue("en $name", en.contains("name=\"$name\""))
            assertTrue("es $name", es.contains("name=\"$name\""))
        }
        places.values.forEach { (_, _, label) ->
            val line = es.lines().first { it.contains("name=\"$label\"") }
            assertTrue(line, line.lowercase().contains("letras"))
        }
    }

    @Test
    fun `nothing was moved off the lyrics screens`() {
        assertTrue(src("ui/screens/settings/AppearanceSettings.kt").contains("title = stringResource(R.string.lyrics),"))
        val content = src("ui/screens/settings/ContentSettings.kt")
        assertTrue(content.contains("title = stringResource(R.string.lyrics),"))
        assertTrue(content.contains("onClick = { navController.navigate(\"settings/content/romanization\") }"))
    }
}
