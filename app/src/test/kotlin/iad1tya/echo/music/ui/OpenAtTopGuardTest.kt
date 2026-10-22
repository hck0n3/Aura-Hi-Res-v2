package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19: «cuando entre a listas y álbumes que inicien mostrando la portada, no que inicien desde abajo».
 *
 * The page opened scrolled past its cover because a LazyListState follows the KEY of its first visible item: while a
 * playlist loaded, its footer spacer was the only item; when the header and the songs were inserted above it the state
 * followed the footer to the bottom. There is no Compose runtime in the unit job, so the wiring is pinned as source text.
 */
class OpenAtTopGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the local playlist footer is only emitted once the playlist is there`() {
        val src = main("ui/newui/AuraLocalPlaylistScreen.kt")
        val footer = src.substringBefore("item(key = \"aura_lp_tail\"").takeLast(80)
        assertTrue("el pie solo con la lista cargada: $footer", footer.contains("if (current != null) {"))
        assertFalse(
            "ningún pie incondicional como único elemento mientras carga",
            Regex("""\n            item\(key = "aura_lp_tail"""").containsMatchIn(src),
        )
    }

    @Test
    fun `every collection page opens at the top and only a restored position is left alone`() {
        for (screen in listOf(
            "ui/newui/AuraAlbumScreen.kt" to "AuraOpenAtTop(listState)",
            "ui/newui/AuraLocalPlaylistScreen.kt" to "AuraOpenAtTop(lazyListState)",
            "ui/newui/AuraOnlinePlaylistScreen.kt" to "AuraOpenAtTop(lazyListState)",
            "ui/newui/AuraCollectionScreens.kt" to "AuraOpenAtTop(listState)",
        )) {
            assertTrue("${screen.first} abre en la portada", main(screen.first).contains(screen.second))
        }
        val helper = main("ui/newui/AuraOpenAtTop.kt")
        assertTrue("una posición restaurada (índice > 0) es del usuario", helper.contains("if (!openedAtTop) return"))
        assertTrue("solo mientras el usuario no haya tocado la lista", helper.contains("!userScrolled && index != 0"))
        assertTrue("el scrollToItem propio no cuenta como gesto del usuario", helper.contains("isScrollInProgress && !snapping"))
    }

    @Test
    fun `the Descargado screen goes through the shared scaffold that opens at the top`() {
        assertTrue(main("ui/newui/AuraDownloadsScreen.kt").contains("AuraSongCollectionScaffold("))
    }
}
