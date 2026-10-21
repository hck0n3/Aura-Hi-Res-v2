package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 498 (owner 2026-10-21): Pedir música, Añadir a lista de reproducción and the Buscar history use the glass
 * slabs of the ⋯ menu, and nothing they offered went away.
 */
class GlassSlabWindowsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `Pedir musica is built from glass slabs and keeps every function`() {
        val s = main("ui/newui/AuraMusicRequest.kt")
        assertTrue(s.contains("MenuHeader("))
        assertTrue("el campo va en una losa", s.contains("AuraGlassSlab {\n        Row("))
        assertTrue("selectores segmentados", s.split("AuraGlassSegmented(").size - 1 >= 2)
        assertTrue("historial como losa de filas", s.contains("recentPrompts.forEachIndexed"))
        // Every function of the sheet is still there.
        listOf(
            "MusicRequestHistory.clear()", "MusicRequestHistory.remove(text)", "voice.launch", "send(prompt)", "onNeedAlternate", "onSaveAlternate", "R.string.music_request_save",
            "R.string.music_request_show_all", "musicRequestSuggestions(currentArtist)", "onCancel",
        ).forEach { assertTrue(it, s.contains(it)) }
        assertFalse("el degradado de la tarjeta se fue", s.contains("Brush.verticalGradient(listOf(AuraPalette.Teal.copy(alpha = 0.10f)"))
    }

    @Test
    fun `Anadir a lista keeps its marks, sort, search and duplicates, and only restyles with the new interface`() {
        val s = main("ui/menu/AddToPlaylistDialog.kt")
        assertTrue(s.contains("AuraGlassSlab("))
        assertTrue(s.contains("auraGlassSegment(first = index == 0, last = index == filteredPlaylists.lastIndex)"))
        listOf(
            "R.string.playlist_already_has_song", "SortHeader(", "playlistDuplicates(", "R.string.skip_duplicates",
            "R.string.add_anyway", "YouTube.addToPlaylist(", "CreatePlaylistDialog(", "listItemShape(index = index + 1",
        ).forEach { assertTrue(it, s.contains(it)) }
        assertTrue("la interfaz clásica no cambia", s.contains("if (premium) RectangleShape else listItemShape"))
    }

    @Test
    fun `the Buscar history is one slab and keeps delete, copy to field and clear all`() {
        val s = main("ui/newui/AuraSearchSuggestions.kt")
        assertTrue(s.contains("AuraGlassRow("))
        assertTrue(s.contains("auraGlassSegment(first = first, last = last"))
        assertTrue(s.contains("R.string.search_history_remove") && s.contains("R.string.search_use_suggestion"))
        assertTrue(s.contains("AuraSearchHistoryHeader(onClearAll"))
        assertTrue(s.contains("last = index == viewState.history.lastIndex"))
    }

    @Test
    fun `the slab primitives are the menu's material`() {
        val s = main("ui/newui/AuraGlassSlab.kt")
        assertTrue(s.contains("internal val AuraGlassCorner = 20.dp"))
        assertTrue(s.contains("AuraPalette.SurfaceFill") && s.contains("AuraPalette.SurfaceLine") && s.contains("AuraPalette.Divider"))
        // Same row height and text as Material3MenuGroup.
        assertTrue(s.contains("AuraGlassRowMinHeight = 54.dp") && s.contains("AuraType.MenuLabel"))
        val menu = main("ui/component/Menu.kt")
        assertTrue(menu.contains("MenuGroupCorner = 20.dp") && menu.contains("MenuRowMinHeight = 54.dp"))
    }
}
