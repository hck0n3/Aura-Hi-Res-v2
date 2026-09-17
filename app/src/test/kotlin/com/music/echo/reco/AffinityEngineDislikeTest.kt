package iad1tya.echo.music.reco

import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.Event
import iad1tya.echo.music.db.entities.EventWithSong
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.dislike.DislikeStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Punto 10 del dueño (2026-09-17): "verificar el algoritmo de predicción".
 *
 * Lo que se verifica aquí es el hallazgo de esa revisión: "No me gusta" era **solo un filtro de
 * salida**, así que un artista marcado conservaba toda su afinidad y seguía puntuando como favorito
 * por la ruta de los nombres — que es la de la radio infinita, los estantes de Inicio y el aleatorio
 * inteligente — y se colaba entero cuando el candidato remoto llegaba sin id de artista.
 */
class AffinityEngineDislikeTest {

    private fun song(id: String, title: String, artistId: String, artistName: String, duration: Int = 200) =
        Song(
            song = SongEntity(id = id, title = title, duration = duration),
            artists = listOf(ArtistEntity(id = artistId, name = artistName)),
        )

    private fun play(s: Song, minutesAgo: Long, completion: Double = 1.0) =
        EventWithSong(
            event = Event(
                songId = s.song.id,
                timestamp = LocalDateTime.now().minusMinutes(minutesAgo),
                playTime = (s.song.duration * 1000L * completion).toLong(),
            ),
            song = s,
        )

    private val loved = song("s1", "Uno", "a1", "Artista Querido")
    private val hated = song("s2", "Dos", "a2", "Artista Marcado")

    private fun profile(disliked: DislikeStore.Disliked) = runBlocking {
        AffinityEngine.buildProfile(
            events = listOf(
                play(loved, 10), play(loved, 60), play(loved, 120),
                play(hated, 15), play(hated, 70), play(hated, 130),
            ),
            disliked = disliked,
        )
    }

    @Test
    fun `without dislikes both artists score positive`() {
        val p = profile(DislikeStore.Disliked())
        assertTrue(p.scoreNames(listOf("Artista Querido"), "Uno") > 0.0)
        assertTrue(p.scoreNames(listOf("Artista Marcado"), "Dos") > 0.0)
    }

    @Test
    fun `a disliked artist sinks by NAME even with no id`() {
        // Este es el agujero: la radio filtra por id (`it.id != null && it.id in disliked.artists`),
        // así que un candidato de YouTube sin id de artista pasaba el filtro — y antes puntuaba alto.
        val p = profile(DislikeStore.Disliked(artists = setOf("a2")))
        assertEquals(TasteProfile.AVOID, p.scoreNames(listOf("Artista Marcado"), "Dos"), 0.0)
    }

    @Test
    fun `disliking one artist does not touch the other`() {
        val p = profile(DislikeStore.Disliked(artists = setOf("a2")))
        assertTrue(p.scoreNames(listOf("Artista Querido"), "Uno") > 0.0)
    }

    @Test
    fun `a disliked artist is not a known artist for the exploration quota`() {
        val p = profile(DislikeStore.Disliked(artists = setOf("a2")))
        assertFalse(p.isKnownArtist("Artista Marcado"))
        assertTrue(p.isKnownArtist("Artista Querido"))
    }

    @Test
    fun `plays of a disliked song stop feeding its artist`() {
        // La canción está marcada, el artista no: sus escuchas dejan de sumar, pero el artista sigue
        // existiendo para el modelo si tiene otras canciones. Aquí solo tiene esa, así que cae a cero.
        val p = profile(DislikeStore.Disliked(songs = setOf("s2")))
        assertEquals(0.0, p.scoreNames(listOf("Artista Marcado"), "Dos"), 1e-9)
        assertTrue(p.scoreNames(listOf("Artista Querido"), "Uno") > 0.0)
    }

    @Test
    fun `a disliked local song still returns AVOID`() {
        // La ruta local no se toca: seguía funcionando y tiene que seguir funcionando.
        val p = profile(DislikeStore.Disliked(songs = setOf("s2")))
        assertEquals(TasteProfile.AVOID, p.score(hated), 0.0)
    }

    @Test
    fun `an empty dislike store changes nothing`() {
        val p = profile(DislikeStore.Disliked())
        assertTrue(p.isKnownArtist("Artista Marcado"))
        assertTrue(p.score(hated) > 0.0)
    }
}
