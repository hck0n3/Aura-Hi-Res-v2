package iad1tya.echo.music.utils

import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterUnavailable
import com.music.innertube.pages.SearchSummary
import com.music.innertube.pages.SearchSummaryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Row 329: search results, suggestions and artist pages hide unavailable songs and videos too. */
class UnavailableSearchFilterTest {

    @Test
    fun greyedAndKnownUnavailableSongsAreDropped() {
        val items: List<YTItem> = listOf(song("ok"), song("grey", unavailable = true), song("known"), artist("UC1"))
        assertEquals(listOf("ok", "UC1"), items.filterUnavailable(setOf("known")).map { it.id })
    }

    @Test
    fun anArtistOrAlbumIsNeverHiddenEvenIfItsIdIsListed() {
        val items: List<YTItem> = listOf(artist("UC1"))
        assertEquals(listOf("UC1"), items.filterUnavailable(setOf("UC1")).map { it.id })
    }

    @Test
    fun summaryDropsOnlyTheSectionLeftEmpty() {
        val page = SearchSummaryPage(
            listOf(
                SearchSummary("Canciones", listOf(song("a"), song("b", unavailable = true))),
                SearchSummary("Videos", listOf(song("v", unavailable = true))),
                SearchSummary("Artistas", listOf(artist("UC1"))),
            ),
        )
        val out = page.filterUnavailable()
        assertEquals(listOf("Canciones", "Artistas"), out.summaries.map { it.title })
        assertEquals(listOf("a"), out.summaries.first().items.map { it.id })
    }

    @Test
    fun untouchedSectionKeepsItsInstance() {
        val section = SearchSummary("Artistas", listOf(artist("UC1")))
        assertSame(section, SearchSummaryPage(listOf(section)).filterUnavailable().summaries.single())
    }

    private fun artist(id: String) = ArtistItem(
        id = id,
        title = id,
        thumbnail = "",
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    private fun song(id: String, unavailable: Boolean = false) = SongItem(
        id = id,
        title = id,
        artists = emptyList(),
        thumbnail = "",
        unavailable = unavailable,
    )
}
