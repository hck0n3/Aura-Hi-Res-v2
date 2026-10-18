package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 426-429 (owner 2026-10-18, unfolded foldable): the playlist page wears the album page's design, the
 * Inicio carousel starts at the left with more covers, «Añadir a lista» has real covers and whole names, and a followed
 * playlist ends in a related shelf. Source-text guards: there is no Android SDK here, so these pin the wiring a refactor
 * could silently undo.
 */
class FoldableListsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the local playlist header uses the wide album top on a wide pane and keeps the centred column on a narrow one`() {
        val src = main("ui/newui/AuraLocalPlaylistScreen.kt")
        val header = src.substringAfter("private fun AuraLocalPlaylistHeader(").substringBefore("The classic [LocalPlaylistMenu]")
        assertTrue(header.contains("BoxWithConstraints"))
        assertTrue(header.contains("maxWidth >= AURA_WIDE_HERO_MIN_WIDTH"))
        assertTrue(header.contains("AuraWideTop("))
        assertTrue("el mosaico y el ✎ viajan al marco ancho", header.contains("coverContent = { cover(AURA_WIDE_TOP_COVER) }"))
        assertTrue("la columna centrada de siempre sigue", header.contains("info(false)") && header.contains("info(true)"))
        assertTrue(main("ui/newui/AuraWideHero.kt").contains("coverContent: (@Composable BoxScope.() -> Unit)? = null"))
    }

    @Test
    fun `the wide Inicio carousel rests its first card at the left edge and shows more, smaller cards`() {
        val src = main("ui/newui/AuraHomeScreen.kt")
        val wide = src.substringAfter("private fun AuraWideCarousel(").substringBefore("private fun AuraHeroCarouselCard(")
        assertTrue(wide.contains("SnapPosition.Start"))
        assertFalse("centrado dejaba media fila vacía", wide.contains("SnapPosition.Center"))
        assertTrue(src.contains("AURA_WIDE_CAROUSEL_ITEM_WIDTH = 200.dp"))
    }

    @Test
    fun `Anadir a lista rows show the whole name and a bigger cover on a wide window`() {
        val sizing = main("ui/component/ListItemSizing.kt")
        assertTrue(sizing.contains("thumbnail = 72.dp"))
        assertTrue(sizing.contains("titleLines = 3"))
        val items = main("ui/component/Items.kt")
        assertTrue(items.contains("maxLines = sizing.titleLines"))
        assertTrue(items.contains("size = LocalListItemSizing.current.thumbnail"))
        for (dialog in listOf("ui/menu/AddToPlaylistDialog.kt", "ui/menu/AddToPlaylistDialogOnline.kt")) {
            assertTrue("$dialog usa la fila amplia", main(dialog).contains("RoomyPlaylistListItem("))
        }
        // every other list keeps the classic 48 dp / 64 dp / one-line row
        assertTrue(sizing.contains("minHeight = ListItemHeight") && sizing.contains("fixedHeight = true"))
    }

    @Test
    fun `a followed playlist ends in the related shelf and an owned one keeps its suggested songs`() {
        val src = main("ui/newui/AuraLocalPlaylistScreen.kt")
        val shelf = src.substringAfter("aura_lp_related").take(400)
        assertTrue(src.contains("if (!editable && !isSearching && !inSelectMode && relatedItems.isNotEmpty())"))
        assertTrue(shelf.contains("AuraRelatedShelf("))
        assertTrue("las propias conservan las canciones sugeridas", src.contains("SuggestedSongsSection("))
        assertTrue("sin red no se pide", src.contains("if (followedBrowseId != null && !offlineNow) viewModel.loadRelated(followedBrowseId)"))
        assertTrue(main("viewmodels/LocalPlaylistViewModel.kt").contains("fun loadRelated(browseId: String)"))
    }
}
