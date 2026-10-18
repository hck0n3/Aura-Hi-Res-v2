package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun pick(focus: MusicRequestFocus, songs: List<MediaMetadata>, played: Set<String> = emptySet()) =
        MusicRequestFocusFilter.select(focus, songs, isFamiliar, played)

    @Test
    fun `Todo no toca nada`() {
        val songs = listOf(song("1", "fam"), song("2", "new"))
        val out = pick(MusicRequestFocus.All, songs)
        assertEquals(songs, out.songs)
        assertFalse(out.relaxed)
    }

    @Test
    fun `Conocido deja solo lo conocido cuando hay suficiente`() {
        val songs = (1..5).map { song("f$it", "fam") } + song("n1", "new")
        val out = pick(MusicRequestFocus.Familiar, songs)
        assertTrue(out.songs.all { it.artists.first().name == "fam" })
        assertEquals(5, out.songs.size)
        assertFalse(out.relaxed)
    }

    @Test
    fun `Conocido con pocas devuelve SOLO lo conocido y lo marca corto, sin mezclar desconocidos`() {
        val songs = listOf(song("n1", "new"), song("f1", "fam"), song("n2", "new"))
        val out = pick(MusicRequestFocus.Familiar, songs)
        assertEquals(listOf("f1"), out.songs.map { it.id })
        assertTrue(out.relaxed)
    }

    @Test
    fun `Conocido sin ninguna conocida no deja la peticion vacia y lo marca`() {
        val songs = listOf(song("n1", "new"), song("n2", "new"))
        val out = pick(MusicRequestFocus.Familiar, songs)
        assertEquals(songs, out.songs)
        assertTrue(out.relaxed)
    }

    @Test
    fun `Conocido ordena lo mas afin primero y respeta el orden ante empates`() {
        val a = song("a", "fam")
        val b = song("b", "fam")
        val c = song("c", "fam")
        val weight = mapOf("a" to 1.0, "b" to 3.0, "c" to 1.0)
        val out = MusicRequestFocusFilter.select(
            MusicRequestFocus.Familiar, listOf(a, b, c), isFamiliar, emptySet(),
        ) { weight.getValue(it.id) }
        assertEquals(listOf("b", "a", "c"), out.songs.map { it.id })
    }

    @Test
    fun `Descubrir quita lo ya reproducido y lo de artistas conocidos`() {
        val songs = (1..5).map { song("n$it", "new$it") } + song("p1", "newP") + song("f1", "fam")
        val out = pick(MusicRequestFocus.Discover, songs, setOf("p1"))
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5"), out.songs.map { it.id })
        assertFalse(out.relaxed)
    }

    @Test
    fun `Descubrir con pocas devuelve solo lo nuevo y lo marca corto`() {
        val songs = listOf(song("f1", "fam"), song("n1", "new"), song("f2", "fam"))
        val out = pick(MusicRequestFocus.Discover, songs)
        assertEquals(listOf("n1"), out.songs.map { it.id })
        assertTrue(out.relaxed)
    }

    @Test
    fun `Descubrir con todo conocido devuelve igualmente algo y lo marca`() {
        val songs = listOf(song("f1", "fam"), song("f2", "fam"))
        val out = pick(MusicRequestFocus.Discover, songs, setOf("f1", "f2"))
        assertEquals(2, out.songs.size)
        assertTrue(out.relaxed)
    }

    @Test
    fun `se piden mas candidatos solo con filtro, y Conocido pide mas`() {
        assertEquals(8, MusicRequestFocusFilter.candidatesFor(MusicRequestFocus.All, 8))
        assertEquals(24, MusicRequestFocusFilter.candidatesFor(MusicRequestFocus.Familiar, 8))
        assertEquals(16, MusicRequestFocusFilter.candidatesFor(MusicRequestFocus.Discover, 8))
    }
}
