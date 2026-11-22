package iad1tya.echo.music.viewmodels

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for docs/REGRESSION_REGISTRY.md rows 14, 16/292 and 20 (ArtistItemsViewModel.kt, a shared file).
 * Row 16 already came back once (row 292): these tests make that impossible to repeat silently.
 */
class ArtistItemsRegistryGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val source: String by lazy {
        val f = File(root, "app/src/main/kotlin/com/music/echo/viewmodels/ArtistItemsViewModel.kt")
        assertTrue("ArtistItemsViewModel.kt se movió: actualiza este test", f.isFile)
        f.readText()
    }

    @Test
    fun `row 14 - the completion cache is keyed by artist AND section, so Albums and Singles never collide`() {
        assertTrue(source.contains("val cacheKey = \"\${artistId ?: \"\"}:\$browseId\""))
    }

    @Test
    fun `row 14 - the Singles and EP section is recognised and completed too`() {
        assertTrue(source.contains("Regex(\"(?i)single|sencillo|\\\\bep\\\\b\").containsMatchIn(pageTitle ?: \"\")"))
    }

    @Test
    fun `rows 16 and 292 - the Albums screen completes against the WHOLE iTunes catalog`() {
        val missingAll = source.substringAfter("val missingAll = itunes").substringBefore("val missing = missingAll")
        // The only allowed EP/Single filter is the one that applies to the Singles screen itself.
        assertTrue(missingAll.contains(".filter { !isSinglesSection || isEpOrSingle(it) }"))
        assertFalse(
            "la pantalla de Álbumes no debe excluir EPs/sencillos (regresión 866b4c8)",
            missingAll.contains("!isEpOrSingle(it)") || missingAll.contains("isEpOrSingle(it) == isSinglesSection"),
        )
        assertTrue("el tope de lanzamientos buscados sigue en 80", source.contains("missingReleaseCap: Int = 80"))
    }

    @Test
    fun `row 20 - a truncated upload is not a complete album`() {
        val ok = AlbumQuality(songCount = 12, basicOk = true)
        assertTrue(isComplete(ok, floor = 12))
        assertTrue("60 % del iTunes es suficiente", isComplete(AlbumQuality(songCount = 8, basicOk = true), floor = 13))
        assertFalse("menos del 60 % es un álbum cortado", isComplete(AlbumQuality(songCount = 3, basicOk = true), floor = 13))
        assertFalse("mediana demasiado corta", isComplete(AlbumQuality(songCount = 12, basicOk = false), floor = 12))
        assertFalse("sin sondeo no hay veredicto", isComplete(null, floor = 12))
    }

    @Test
    fun `row 20 - one short track among normal ones marks the album as truncated`() {
        assertTrue(hasTruncatedTrack(listOf(200, 210, 45, 190), median = 200))
        assertFalse("un álbum de temas cortos de verdad no se rechaza", hasTruncatedTrack(listOf(50, 55, 58), median = 55))
    }

    @Test
    fun `row 20 - the merge drops a stale playlist copy once the real album is shown`() {
        val merge = source.substringAfter("private fun mergeDiscography(").substringBefore("fun loadMore()")
        assertTrue(merge.contains("reconKey(item.title) !in albumNorms"))
    }
}
