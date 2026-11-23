package iad1tya.echo.music.reco

import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.Event
import iad1tya.echo.music.db.entities.EventWithSong
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.dislike.DislikeStore
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 721 — a song's «No me gusta» also lowers its primary artist a little (bounded, never AVOID). The plays of
 * the disliked song still feed nothing (row 285, `AffinityEngineDislikeTest`); this is a separate, explicit input.
 */
class AffinityEngineDislikedSongArtistTest {
    private val now = System.currentTimeMillis()

    private fun song(id: String, artistId: String, artistName: String) = Song(
        song = SongEntity(id = id, title = "T$id", duration = 200),
        artists = listOf(ArtistEntity(id = artistId, name = artistName)),
    )

    private fun play(s: Song, minutesAgo: Long) = EventWithSong(
        event = Event(songId = s.song.id, timestamp = LocalDateTime.now().minusMinutes(minutesAgo), playTime = 200_000L),
        song = s,
    )

    private val a1 = song("s1", "a1", "Uno")
    private val a1Other = song("s1b", "a1", "Uno")
    private val a2 = song("s2", "a2", "Dos")
    private val a2Other = song("s2b", "a2", "Dos")

    // Both artists heard alike through a song that is NOT disliked.
    private val events = listOf(play(a1Other, 10), play(a2Other, 10), play(a1Other, 60), play(a2Other, 60))

    private fun profile(disliked: DislikeStore.Disliked, dislikedSongs: List<Song>) = runBlocking {
        AffinityEngine.buildProfile(events = events, disliked = disliked, now = now, dislikedSongs = dislikedSongs)
    }

    @Test
    fun `without the stored rows nothing changes`() {
        val p = profile(DislikeStore.Disliked(songs = setOf("s2")), emptyList())
        assertEquals(p.scoreNames(listOf("Uno"), null), p.scoreNames(listOf("Dos"), null), 1e-9)
    }

    @Test
    fun `a disliked song lowers its artist, by id and by name, but never to AVOID`() {
        val p = profile(DislikeStore.Disliked(songs = setOf("s2")), listOf(a2))
        assertTrue(p.scoreNames(listOf("Dos"), null) < p.scoreNames(listOf("Uno"), null))
        assertTrue(p.score(a2Other) < p.score(a1Other))
        assertTrue(p.score(a2Other) > TasteProfile.AVOID)
        assertTrue(p.scoreNames(listOf("Dos"), null) > TasteProfile.AVOID)
        assertEquals("the disliked song itself stays AVOID", TasteProfile.AVOID, p.score(a2), 0.0)
    }

    @Test
    fun `the weight is one fresh play skipped at second zero`() {
        assertEquals(0.30 / 0.70, AffinityEngine.DISLIKED_SONG_ARTIST_WEIGHT, 1e-9)
    }

    @Test
    fun `the penalty per artist is capped`() {
        val many = (0 until 20).map { song("x$it", "a2", "Dos") }
        val more = (0 until 40).map { song("y$it", "a2", "Dos") }
        val p20 = profile(DislikeStore.Disliked(songs = many.map { it.song.id }.toSet()), many)
        val p40 = profile(DislikeStore.Disliked(songs = more.map { it.song.id }.toSet()), more)
        assertEquals(p20.scoreNames(listOf("Dos"), null), p40.scoreNames(listOf("Dos"), null), 1e-9)
        assertEquals(p20.score(a2Other), p40.score(a2Other), 1e-9)
    }

    @Test
    fun `a row whose song is no longer disliked, or a marked artist, takes nothing more`() {
        val stale = profile(DislikeStore.Disliked(), listOf(a2))
        assertEquals(stale.scoreNames(listOf("Uno"), null), stale.scoreNames(listOf("Dos"), null), 1e-9)
        val hard = profile(DislikeStore.Disliked(songs = setOf("s2"), artists = setOf("a2")), listOf(a2))
        assertEquals(TasteProfile.AVOID, hard.scoreNames(listOf("Dos"), null), 0.0)
    }

    @Test
    fun `both profile builders pass the disliked songs' rows`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()
        val read = "disliked.songs.toList().chunked(900).flatMap { database.getSongsByIds(it) }"
        val taste = main("playback/MusicService.kt").substringAfter("internal suspend fun tasteProfile()").substringBefore("cachedTaste = it")
        assertTrue(taste.contains(read))
        assertTrue(taste.contains("dislikedSongs = dislikedSongs)"))
        val refresh = main("viewmodels/HomeViewModel.kt").substringAfter("private suspend fun refreshTasteProfile()")
            .substringBefore("private fun List<Song>.rankedByTaste()")
        assertTrue(refresh.contains(read))
        assertTrue(refresh.contains("dislikedSongs = dislikedSongs)"))
    }
}
