package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Conocido / Descubrir» de «Pedir música». Lo que no puede pasar: que un filtro deje la petición VACÍA, que «Todo»
 * cambie algo, o que Descubrir devuelva canciones que ya reprodujo cuando hay otras.
 */
class MusicRequestFocusTest {

    private fun song(id: String, artist: String) = MediaMetadata(
        id = id,
        title = "t$id",
        artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
        duration = 180,
    )

    private val known = setOf("fam")
    private val isFamiliar: (MediaMetadata) -> Boolean = { m -> m.artists.any { it.name in known } }

    @Test
    fun `Todo no toca nada`() {
        val songs = listOf(song("1", "fam"), song("2", "new"))
        assertEquals(songs, MusicRequestFocusFilter.apply(MusicRequestFocus.All, songs, isFamiliar, emptySet()))
    }

    @Test
    fun `Conocido deja solo lo conocido cuando hay suficiente`() {
        val songs = (1..5).map { song("f$it", "fam") } + song("n1", "new")
        val out = MusicRequestFocusFilter.apply(MusicRequestFocus.Familiar, songs, isFamiliar, emptySet())
        assertTrue(out.all { it.artists.first().name == "fam" })
        assertEquals(5, out.size)
    }

    @Test
    fun `Conocido con pocas pone lo conocido primero y no pierde nada`() {
        val songs = listOf(song("n1", "new"), song("f1", "fam"), song("n2", "new"))
        val out = MusicRequestFocusFilter.apply(MusicRequestFocus.Familiar, songs, isFamiliar, emptySet())
        assertEquals(listOf("f1", "n1", "n2"), out.map { it.id })
    }

    @Test
    fun `Descubrir quita lo ya reproducido y lo de artistas conocidos`() {
        val songs = (1..5).map { song("n$it", "new$it") } + song("p1", "newP") + song("f1", "fam")
        val out = MusicRequestFocusFilter.apply(MusicRequestFocus.Discover, songs, isFamiliar, setOf("p1"))
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5"), out.map { it.id })
    }

    @Test
    fun `Descubrir con pocas pone lo nuevo primero y nunca deja la lista vacia`() {
        val songs = listOf(song("f1", "fam"), song("n1", "new"), song("f2", "fam"))
        val out = MusicRequestFocusFilter.apply(MusicRequestFocus.Discover, songs, isFamiliar, emptySet())
        assertEquals(listOf("n1", "f1", "f2"), out.map { it.id })
    }

    @Test
    fun `Descubrir con todo conocido devuelve igualmente algo`() {
        val songs = listOf(song("f1", "fam"), song("f2", "fam"))
        val out = MusicRequestFocusFilter.apply(MusicRequestFocus.Discover, songs, isFamiliar, setOf("f1", "f2"))
        assertEquals(2, out.size)
    }

    @Test
    fun `se piden mas candidatos solo con filtro`() {
        assertEquals(8, MusicRequestFocusFilter.candidatesFor(MusicRequestFocus.All, 8))
        assertEquals(16, MusicRequestFocusFilter.candidatesFor(MusicRequestFocus.Discover, 8))
    }
}
