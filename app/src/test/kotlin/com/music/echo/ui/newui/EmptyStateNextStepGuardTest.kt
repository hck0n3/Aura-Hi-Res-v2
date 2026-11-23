package iad1tya.echo.music.ui.newui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX audit P22A (registry row 732): the main screens' empty states offer ONE next step, and every one of those steps is an
 * action the app already had (the bottom bar's «Buscar», the «+» menu's «Crear lista», the «Todo» results chip).
 */
class EmptyStateNextStepGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun newui(name: String) = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/$name").readText()

    private fun res(dir: String, file: String) = File(root, "app/src/main/res/$dir/$file").readText()

    @Test
    fun `AuraEmpty draws its action only when both label and action are given`() {
        val content = newui("AuraContent.kt")
        val empty = content.substringAfter("fun AuraEmpty(").substringBefore("// ── Swipe actions")
        assertTrue(empty.contains("actionLabel: String? = null,"))
        assertTrue(empty.contains("onAction: (() -> Unit)? = null,"))
        assertTrue(empty.contains("if (actionLabel != null && onAction != null) {"))
    }

    @Test
    fun `library albums and playlists point to Buscar and Crear lista`() {
        val tabs = newui("AuraLibraryTabs.kt")
        val albums = tabs.substringAfter("key = \"aura_albums_empty\"").substringBefore("items(albums")
        assertTrue(albums.contains("actionLabel = stringResource(R.string.empty_action_search_music),"))
        assertTrue(albums.contains("onAction = { navController.navigateToTab(Screens.Search.route) },"))
        val playlists = tabs.substringAfter("key = \"aura_playlists_empty\"").substringBefore("items(filteredPlaylists")
        assertTrue("no create button while searching", playlists.contains("if (playlistSearchQuery.isBlank() && onCreatePlaylist != null) {"))
        assertTrue(playlists.contains("stringResource(R.string.create_playlist)"))
        assertTrue(playlists.contains("onAction = onCreatePlaylist,"))
        // The callback opens the SAME dialog the floating «+» menu opens.
        val screen = newui("AuraLibraryScreen.kt")
        assertTrue(screen.contains("onCreatePlaylist = { showCreatePlaylistDialog = true },"))
        assertTrue(screen.contains("if (showCreatePlaylistDialog) {"))
        // navigateToTab is the bottom bar's own tab switch.
        assertTrue(File(root, "app/src/main/kotlin/com/music/echo/MainActivity.kt").readText().contains("internal fun NavController.navigateToTab(route: String) {"))
    }

    @Test
    fun `a filtered search with no results offers all results, the unfiltered one stays text`() {
        val search = newui("AuraSearchResultScreen.kt")
        assertEquals(
            "grid + list filtered empties",
            2,
            Regex("onAction = \\{ viewModel\\.filter\\.value = null \\},").findAll(search).count(),
        )
        assertTrue(search.contains("item(key = \"aura_results_empty\", contentType = \"aura_results_empty\") {\n                                    AuraEmpty(text = stringResource(R.string.no_results_found))"))
        assertTrue("the «Todo» chip is the null filter", search.contains("null to R.string.filter_all,"))
    }

    @Test
    fun `Descargadas and Me gusta empty point to Buscar`() {
        val downloads = newui("AuraDownloadsScreen.kt")
        assertTrue(downloads.contains("emptyActionLabel = if (playlistType == PlaylistType.DOWNLOAD || playlistType == PlaylistType.LIKE) {"))
        assertTrue(downloads.contains("onEmptyAction = { navController.navigateToTab(Screens.Search.route) },"))
        val scaffold = newui("AuraCollectionScreens.kt").substringAfter("key = \"aura_coll_empty\"").substringBefore("aura_coll_header")
        assertTrue(scaffold.contains("actionLabel = emptyActionLabel,"))
        assertTrue(scaffold.contains("onAction = onEmptyAction,"))
    }

    @Test
    fun `the labels exist in English and Spanish`() {
        for (name in listOf("empty_action_search_music", "empty_action_show_all_results")) {
            assertTrue(res("values", "empty_state_strings.xml").contains("<string name=\"$name\">"))
            assertTrue(res("values-es", "empty_state_strings.xml").contains("<string name=\"$name\">"))
        }
        assertTrue(res("values-es", "empty_state_strings.xml").contains("<string name=\"empty_action_search_music\">Buscar música</string>"))
    }
}
