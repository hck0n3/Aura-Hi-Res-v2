package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 436-438 (owner 2026-10-18): the result card of «Pedir música» is a two-page carousel (this list / another
 * option), every row carries its cover, and the second option is searched only when the page is reached.
 */
class MusicRequestCardGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the card is a pager of two options and asks for the second one only when it is reached`() {
        val ui = main("ui/newui/AuraMusicRequest.kt")
        val card = ui.substringAfter("private fun AuraMusicRequestResultCard(").substringBefore("private fun AuraMusicRequestOptionHeader(")
        assertTrue(card.contains("rememberPagerState(pageCount = { 2 })"))
        assertTrue(card.contains("HorizontalPager("))
        assertTrue("la segunda opción solo se pide al llegar a ella", card.contains("if (pagerState.currentPage == 1) onNeedAlternate()"))
        assertTrue(ui.contains("onNeedAlternate = viewModel::loadAlternate"))
        assertTrue(ui.contains("contextForAlternate = viewModel::contextForAlternate"))
    }

    @Test
    fun `every row shows its cover, and the header stacks the first three`() {
        val ui = main("ui/newui/AuraMusicRequest.kt")
        val row = ui.substringAfter("private fun AuraRequestSongRow(").substringBefore("private fun Modifier.auraRequestOptionSurface()")
        assertTrue(row.contains("AuraCover("))
        assertTrue(row.contains("thumbnailUrl = song.thumbnailUrl"))
        assertTrue(ui.substringAfter("private fun AuraRequestCoverStack(").substringBefore("private fun AuraRequestSongRow(").contains("songs.take(3)"))
        // The plain index + one-line text row is gone.
        assertFalse(ui.contains("text = \"\${index + 1}\""))
    }

    @Test
    fun `the alternative is another list, one search per request, without the first list's songs, in the same target`() {
        val vm = main("viewmodels/MusicRequestViewModel.kt")
        val load = vm.substringAfter("fun loadAlternate()").substringBefore("fun contextForAlternate()")
        assertTrue("una sola vez por petición", load.contains("current.alternate != AlternateOption.NotAsked"))
        assertTrue("sin las canciones de la primera", load.contains(".filter { it.id !in firstIds }"))
        assertTrue("con el mismo filtro Conocido/Descubrir", load.contains("MusicRequestFocusFilter.select(args.focus"))
        assertTrue(vm.substringAfter("fun contextForAlternate()").substringBefore("fun saveAlternateAsPlaylist()").contains("RequestStyleTargets.put(altId"))
        assertTrue(vm.contains("sealed interface AlternateOption"))
    }
}
