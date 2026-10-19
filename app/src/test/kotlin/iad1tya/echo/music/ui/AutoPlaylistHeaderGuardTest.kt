package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19: «las portadas de las canciones que me gustan, mp3 exportados, mi top, descargados, en caché sean como
 * las de las listas, plegado y desplegado». One shared header (AuraCollectionHeader) serves all five screens, so one place
 * wears the own lists' hero.
 */
class AutoPlaylistHeaderGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the collection header wears the album hero on a phone and the wide top on a wide pane`() {
        val src = main("ui/newui/AuraCollectionScreens.kt")
        val header = src.substringAfter("internal fun AuraCollectionHeader(").substringBefore("// ── Muebles compartidos")
        assertTrue(header.contains("maxWidth >= AURA_WIDE_HERO_MIN_WIDTH"))
        assertTrue("plegado abierto: portada a la izquierda", header.contains("AuraWideTop(") && header.contains("coverContent = { cover(AURA_WIDE_TOP_COVER) }"))
        assertTrue("móvil: la portada a pantalla completa", header.contains("AuraAlbumHero(") && header.contains("listState = listState"))
        assertTrue("mosaico de cuatro, nunca una celda vacía", header.contains("List(4) { covers[it % covers.size] }"))
        assertFalse("el cuadrado de 260 dp de antes", header.contains("else -> 260.dp"))
    }

    @Test
    fun `the hero is full bleed and every action of the old header survives`() {
        val src = main("ui/newui/AuraCollectionScreens.kt")
        assertTrue("sin reserva superior bajo la portada", src.contains("} else if (songs?.isNotEmpty() == true) {"))
        assertTrue(src.contains("listState = listState,"))
        val header = src.substringAfter("internal fun AuraCollectionHeader(").substringBefore("// ── Muebles compartidos")
        for (kept in listOf("rememberShuffleMemoryPrompt(", "EnhancedShuffleChip(", "AuraIcons.Search", "AuraIcons.More", "AuraExpandableText(", "R.string.shuffle", "R.string.play")) {
            assertTrue("se perdió $kept", header.contains(kept))
        }
    }

    @Test
    fun `the five automatic lists all go through the shared scaffold`() {
        val collections = main("ui/newui/AuraCollectionScreens.kt")
        assertTrue(collections.contains("AuraSongCollectionScaffold("))
        assertTrue(main("ui/newui/AuraDownloadsScreen.kt").contains("AuraSongCollectionScaffold("))
        assertTrue("Mi top y En caché", collections.contains("fun AuraCachePlaylistScreen(") && collections.contains("fun AuraTopPlaylistScreen("))
    }
}
