package iad1tya.echo.music.ui.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX audit P22A (registry row 732): «Letras no encontradas» offers its next step, «Buscar letra» — the lyrics menu's
 * EXISTING search dialog, opened directly with the same menu every host opens (offset dialog included, so nothing
 * under it is a dead button).
 */
class LyricsNotFoundSearchGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `the not-found state opens the lyrics menu straight on its search dialog`() {
        val view = main("ui/player/InlineLyricsView.kt")
        val notFound = view.substringAfter("lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {").substringBefore("else -> {")
        assertTrue(notFound.contains("stringResource(R.string.lyrics_not_found)"))
        assertTrue(notFound.contains("Text(text = stringResource(R.string.search_lyrics))"))
        assertTrue(notFound.contains("startWithSearch = true,"))
        assertTrue("same menu as the hosts: the offset dialog is wired", notFound.contains("ShowOffsetDialog(songProvider = { song?.song })"))
        // The song row is observed only while the menu is open, never by the lyrics view itself.
        val show = notFound.indexOf("menuState.show {")
        assertTrue(show > 0 && notFound.indexOf("playerConnection.currentSong.collectAsState(initial = null)") > show)
    }

    @Test
    fun `the menu can start on the search and closes itself when that search is cancelled`() {
        val menu = main("ui/menu/LyricsMenu.kt")
        assertTrue(menu.contains("startWithSearch: Boolean = false,"))
        assertTrue(menu.contains("mutableStateOf(startWithSearch)"))
        assertEquals("dialog dismiss + Cancel", 2, Regex("if \\(startWithSearch\\) onDismiss\\(\\)").findAll(menu).count())
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            assertTrue("$dir lacks search_lyrics", File(root, "app/src/main/res/$dir/strings.xml").readText().contains("<string name=\"search_lyrics\">"))
        }
    }
}
