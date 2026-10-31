package iad1tya.echo.music.ui.screens.artist

import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Row 612: the same release listed twice under different ids appears once. */
class ArtistDiscographyTest {
    private fun album(id: String, title: String, year: Int?, artist: String = "Luis Miguel", explicit: Boolean = false) =
        AlbumItem(
            browseId = id, playlistId = "OLAK_$id", title = title, artists = listOf(Artist(artist, "UC1")),
            year = year, thumbnail = "", explicit = explicit,
        )

    @Test
    fun cleanAndExplicitEditionsOfOneReleaseCollapseKeepingTheFirstInPlace() {
        val out = ArtistDiscography.dedupe(
            listOf(album("A", "Romances", 1997), album("B", "Aries", 1993), album("C", "ROMANCES", 1997, explicit = true)),
        )
        assertEquals(listOf("A", "B"), out.map { it.id })
    }

    @Test
    fun accentsPunctuationAndSpacingDoNotMakeANewRelease() {
        val out = ArtistDiscography.dedupe(listOf(album("A", "Mi Música", 2000), album("B", "mi  musica!", 2000)))
        assertEquals(1, out.size)
    }

    @Test
    fun aDeluxeEditionAnotherYearOrAnotherArtistIsADifferentRelease() {
        val out = ArtistDiscography.dedupe(
            listOf(
                album("A", "Romances", 1997),
                album("B", "Romances (Deluxe)", 1997),
                album("C", "Romances", 2017),
                album("D", "Romances", 1997, artist = "Otro"),
            ),
        )
        assertEquals(listOf("A", "B", "C", "D"), out.map { it.id })
    }

    @Test
    fun theSameIdIsStillRemoved() {
        assertEquals(1, ArtistDiscography.dedupe(listOf(album("A", "X", 1), album("A", "X", 1))).size)
    }

    private fun song(id: String, title: String, vararg artists: String) =
        SongItem(id = id, title = title, artists = artists.map { Artist(it, "UC_$it") }, thumbnail = "")

    @Test
    fun missingSongsAreOnlyTheArtistsOwnAndNotAlreadyListed() {
        val listed = listOf(song("1", "La Incondicional", "Luis Miguel"))
        val found = listOf(
            song("1", "La Incondicional", "Luis Miguel"),            // same id
            song("9", "la incondicional!", "Luis Miguel"),           // same title and artist, other id
            song("2", "Sol, Arena y Mar", "Luis Miguel"),            // new, his
            song("3", "Cover de Hasta Que Te Conocí", "Otro"),       // someone else's
            song("4", "Por Debajo de la Mesa", "Luis Miguel", "Ana"), // his, with a guest
        )
        val out = ArtistDiscography.missingSongs(listed, found, "Luis Miguel")
        assertEquals(listOf("2", "4"), out.map { it.id })
    }

    @Test
    fun missingSongsAreBounded() {
        val found = (1..100).map { song("s$it", "Song $it", "X") }
        assertEquals(60, ArtistDiscography.missingSongs(emptyList(), found, "X").size)
    }
}
