package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ronda 7 (dueño): "¿se están autocompletando con discos que suben los usuarios?". iTunes' artistTerm
 * search matches by NAME TEXT only, así que un homónimo/tributo con el mismo nombre exacto podía
 * colarse en la discografía. [iTunesDiscography.filterHomonyms] filtra por artistId de mayoría — estos
 * tests fijan ese contrato sin red.
 */
class ItunesHomonymFilterTest {

    private fun hit(title: String, artistId: String?, trackCount: Int = 10) =
        ItunesAlbumHit(title = title, trackCount = trackCount, artistId = artistId)

    @Test
    fun `a minority homonym is dropped`() {
        val hits = listOf(
            hit("Album A", "123"),
            hit("Album B", "123"),
            hit("Album C", "123"),
            hit("Tribute Record", "999"), // homonym / tribute act, minority artistId
        )
        val result = iTunesDiscography.filterHomonyms(hits)
        assertEquals(listOf("Album A", "Album B", "Album C"), result.map { it.title })
    }

    @Test
    fun `hits with a null artistId are always kept`() {
        val hits = listOf(
            hit("Album A", "123"),
            hit("Album B", "123"),
            hit("Old Response Entry", null), // can't be disproven — kept
        )
        val result = iTunesDiscography.filterHomonyms(hits)
        assertEquals(listOf("Album A", "Album B", "Old Response Entry"), result.map { it.title })
    }

    @Test
    fun `no clear majority drops nothing`() {
        // Every hit has a distinct artistId: insufficient evidence to call any of them the homonym.
        val hits = listOf(hit("Album A", "1"), hit("Album B", "2"), hit("Album C", "3"))
        val result = iTunesDiscography.filterHomonyms(hits)
        assertEquals(hits, result)
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals(emptyList<ItunesAlbumHit>(), iTunesDiscography.filterHomonyms(emptyList()))
    }

    @Test
    fun `all hits sharing one artistId keeps everything`() {
        val hits = listOf(hit("Album A", "123"), hit("Album B", "123"))
        assertEquals(hits, iTunesDiscography.filterHomonyms(hits))
    }
}
