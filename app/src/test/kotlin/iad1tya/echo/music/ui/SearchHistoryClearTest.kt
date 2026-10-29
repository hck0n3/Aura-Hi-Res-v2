package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 431 and 433 (owner 2026-10-18): the search history can be cleared from the player's quick search AND from the
 * general search (it only had a ✕ per row), and Novedades judges "yours" against the whole library, not only the artists
 * flagged "followed".
 */
class SearchHistoryClearTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `both search panels offer Borrar todo, asking first`() {
        val suggestions = main("ui/newui/AuraSearchSuggestions.kt")
        assertTrue(suggestions.contains("AuraSearchHistoryHeader(onClearAll = { database.query { clearSearchHistory() } })"))
        assertTrue(suggestions.contains("R.string.clear_search_history_confirm"))
        val quick = main("ui/newui/AuraPlayerQuickSearch.kt")
        assertTrue(quick.contains("AuraSearchHistoryHeader(onClearAll = onClearHistory)"))
        assertTrue(quick.contains("onClearHistory = { database.query { clearSearchHistory() } }"))
    }

    // Row 517 replaces row 431's «whole library taste» shelves: Novedades is now ONLY the followed artists' releases.
    @Test
    fun `Novedades reads only the followed artists' radar and upcoming releases`() {
        val vm = main("viewmodels/NovedadesViewModel.kt")
        assertTrue(vm.contains("val radarReleases"))
        assertTrue(vm.contains("scanUpcoming()"))
        assertTrue("sin llamadas de YouTube para estantes que ya no existen", !vm.contains("newReleaseAlbums") && !vm.contains("getChartsPage"))
    }
}
