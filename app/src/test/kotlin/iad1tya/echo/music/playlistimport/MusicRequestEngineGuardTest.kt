package iad1tya.echo.music.playlistimport

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-10-19): *«cuando pido algunos géneros no me pone lo que pido… que lo sepa interpretar de la mejor manera y
 * que improvise»*. Lo que se puede probar sin red está en `MusicRequestVocabularyTest`; esto fija las piezas de la escalera
 * de `AiPlaylistGenerator` (que habla con YouTube y no corre en la JVM) que dependen de esa lectura.
 */
class MusicRequestEngineGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val generator get() = main("playlistimport/AiPlaylistGenerator.kt")

    @Test
    fun `the solo-artist guard asks the whole genre vocabulary, not a hand list`() {
        val src = main("api/AiPlaylistConstraints.kt")
        assertTrue(src.contains("MusicRequestQuery.isThemeOnly(s)"))
        // «música de bossa nova» ya no puede volver a ser un artista: el guardián mira el mismo vocabulario que la búsqueda.
        assertTrue(main("playlistimport/MusicRequestQuery.kt").contains("fun isThemeOnly(text: String): Boolean ="))
        assertTrue(generator.contains("residual.length >= 3 && MusicRequestQuery.hasSubstance(residual)"))
    }

    @Test
    fun `the lists and categories read genres as whole words from the one vocabulary`() {
        val moods = main("playlistimport/MusicRequestMoods.kt")
        assertTrue("grupos por estilo de MusicStyle", moods.contains("MusicStyle.phrasesIn(folded)"))
        assertFalse("sin disparadores por subcadena", moods.contains("triggers.any { t -> folded.contains(t) }"))
        assertTrue("el momento es blando junto a un género o una década", moods.contains("if (genres.isEmpty() && parsed.decade == null)"))
        val query = main("playlistimport/MusicRequestQuery.kt")
        assertTrue(query.contains("MusicStyle.namesAStyle(withoutLeadIn)"))
        assertTrue(query.contains("MusicStyle.withoutStyles(stripped)"))
    }

    @Test
    fun `an unknown word is searched as a theme, a like-only request searches no loose text`() {
        val src = generator
        assertTrue("tema desconocido", src.contains("var themeRequest = false"))
        assertTrue(src.contains("themeRequest = true"))
        assertTrue("las listas también corren para un tema", src.contains("intent.styles.isNotEmpty() || themeRequest) && effectiveSolo == null"))
        assertTrue("solo «parecido a X»", src.contains("val onlyLike = parsed.query.isBlank() && intent.likeArtist != null"))
        assertTrue("sin texto no se buscan canciones sueltas", src.contains("if (query.isNotBlank() && pool.size < POOL_TARGET"))
        assertTrue("un artista con palabra de género en el nombre", src.contains("MusicRequestArtist.confirm(MusicRequestQuery.withoutLeadIn(prompt), candidateArtists)"))
    }

    @Test
    fun `the log carries counts only - never a word of the request`() {
        // AGENTS.md regla 4: el registro lo comparte el usuario.
        assertTrue(generator.contains("\"MUSIC_REQUEST specific=%b artist=%b theme=%b qualifiers=%d versionsDropped=%d pool=%d\""))
        assertFalse(main("playlistimport/MusicRequestQuery.kt").contains("Timber"))
        assertFalse(main("playlistimport/MusicRequestMoods.kt").contains("Timber"))
        assertFalse(main("reco/MusicStyle.kt").contains("Timber"))
    }
}
