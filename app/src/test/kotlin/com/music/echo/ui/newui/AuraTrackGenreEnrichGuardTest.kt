package iad1tya.echo.music.ui.newui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The genre column of the album / playlist track lists fills the on-device artist cache ONCE per screen instance,
 * only on a wide pane, honouring the network preference, and never logs artist names. What the eye sees can only
 * be judged on the phone; this pins WHERE the work happens.
 */
class AuraTrackGenreEnrichGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun newui(name: String) =
        File(root, "app/src/main/kotlin/com/music/echo/ui/newui/$name").also { assertTrue("$name se movió", it.isFile) }.readText()

    @Test
    fun `one bounded wifi-preference enrich call, wide pane only, once per screen instance`() {
        val src = newui("AuraTrackColumns.kt")
        assertEquals("una sola llamada a enrich", 1, Regex("GenreCache\\.enrich\\(").findAll(src).count())
        assertTrue(src.contains("GenreCache.enrich(context, study, onlyWifi = true)"))
        val effect = src.substringAfter("LaunchedEffect(columns, hasArtists)").substringBefore("return remember(byArtist)")
        assertTrue("solo en panel ancho", effect.contains("!columns"))
        assertTrue("una vez por instancia", effect.contains("enrichStarted"))
        assertTrue("el snapshot se relee", effect.contains("refresh++"))
        assertFalse("sin logs con nombres", src.contains("AppLogger") || src.contains("Log.") || src.contains("Timber"))
    }

    @Test
    fun `the four track-list screens ask for it once and per row nothing happens`() {
        listOf(
            "AuraAlbumScreen.kt", "AuraOnlinePlaylistScreen.kt", "AuraLocalPlaylistScreen.kt", "AuraCollectionScreens.kt",
        ).forEach { file ->
            val src = newui(file)
            assertEquals("$file: una llamada a rememberAuraTrackGenres", 1, Regex("rememberAuraTrackGenres\\(").findAll(src).count())
            assertFalse("$file: nada de GenreCache por fila", src.contains("GenreCache.") || src.contains("reco.GenreCache"))
        }
        assertFalse(newui("AuraContent.kt").contains("GenreCache."))
    }
}
