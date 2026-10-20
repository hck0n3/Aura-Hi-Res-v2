package iad1tya.echo.music.ui.newui

import androidx.compose.ui.unit.dp
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
}
