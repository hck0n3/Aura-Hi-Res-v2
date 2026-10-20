package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track lists of álbum / playlist: columns only on a wide pane, genre only when the on-device cache knows it.
 * If these drift the owner sees a made-up genre, or the folded phone gets a four-column row it has no room for.
 */
class AuraTrackColumnsTest {

    @Test
    fun foldedPhoneStaysStackedAndUnfoldedGetsColumns() {
        assertFalse(auraTrackColumnsFit(360.dp))
        assertFalse(auraTrackColumnsFit(599.dp))
        assertTrue(auraTrackColumnsFit(600.dp))
        assertTrue(auraTrackColumnsFit(673.dp))
    }

    @Test
    fun genreComesFromTheFirstArtistOnlyAndIgnoresCase() {
        val genres = mapOf("bad bunny" to "Latin", "drake" to "Hip-Hop/Rap")
        assertEquals("Latin", auraArtistGenre(listOf("Bad Bunny", "Drake"), genres))
        assertEquals("Latin", auraArtistGenre(listOf("  BAD BUNNY "), genres))
        // The first artist is unknown: do NOT borrow the featured artist's genre.
        assertNull(auraArtistGenre(listOf("Unknown", "Drake"), genres))
    }

    @Test
    fun unknownOrBlankGenreStaysEmpty() {
        assertNull(auraArtistGenre(emptyList(), mapOf("x" to "Pop")))
        assertNull(auraArtistGenre(listOf(""), mapOf("" to "Pop")))
        assertNull(auraArtistGenre(listOf("x"), mapOf("x" to "  ")))
        assertNull(auraArtistGenre(listOf("x"), emptyMap()))
    }

    @Test
    fun genreColumnOnlyWhenSomeSongHasOne() {
        val known = AuraTrackGenres(mapOf("a" to "Pop"))
        val songs = listOf(listOf("a"), listOf("b"))
        assertTrue(known.anyKnown(songs) { it })
        assertFalse(known.anyKnown(listOf(listOf("b")) ) { it })
        assertFalse(AuraTrackGenres(emptyMap()).anyKnown(songs) { it })
    }

    // ── Same x-positions on every row (owner 2026-10-20, «Oxigeno»: liked rows had their columns ~25 px to the left) ──

    private fun newui(name: String): String {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        return File(root, "app/src/main/kotlin/com/music/echo/ui/newui/$name").also { assertTrue("$name se movió", it.isFile) }.readText()
    }

    @Test
    fun trailingEndOfAColumnRowHasAConstantWidth() {
        // Both slots are fixed and the action slot fits the 48 dp ⋯ button and the checkbox alike.
        assertEquals(72.dp, AuraTrackStateSlotWidth)
        assertEquals(48.dp, AuraTrackActionSlotWidth)
        val slots = newui("AuraTrackColumns.kt").substringAfter("fun AuraTrackTrailingSlots(")
        assertTrue("el estado va en un hueco de ancho fijo", slots.contains("Modifier.width(AuraTrackStateSlotWidth)"))
        assertTrue("la acción va en un hueco de ancho fijo", slots.contains("Modifier.width(AuraTrackActionSlotWidth)"))
        // A longer cluster must overflow to the left, never widen the slot (which would shift the columns).
        assertTrue(slots.contains("unbounded = true"))
    }

    @Test
    fun bothColumnRowsRouteTheirTrailingClusterThroughTheFixedSlots() {
        val content = newui("AuraContent.kt")
        assertEquals(
            "AuraSongRow y AuraAlbumTrackRow usan AuraTrackTrailingSlots",
            2,
            Regex("AuraTrackTrailingSlots\\(state = state, action = action\\)").findAll(content).count(),
        )
        // The duration lives in the column, not in the variable cluster, whenever the column layout is on.
        assertEquals(2, Regex("!durationLabel\\.isNullOrBlank\\(\\) && !columnsLayout").findAll(content).count())
    }
}
