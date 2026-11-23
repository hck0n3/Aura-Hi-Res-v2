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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 721 — «Menos de esto» reaches the artist and the Home shelves, as a bounded demotion (never a filter). */
class SoftDislikeRankTest {
    private fun song(id: String, artistId: String, artistName: String) = Song(
        song = SongEntity(id = id, title = "T$id", duration = 200),
        artists = listOf(ArtistEntity(id = artistId, name = artistName)),
    )

    private fun play(s: Song, minutesAgo: Long) = EventWithSong(
        event = Event(songId = s.song.id, timestamp = LocalDateTime.now().minusMinutes(minutesAgo), playTime = 200_000L),
        song = s,
    )

    private val fav = song("s1", "a1", "Favorito")
    private val other = song("s2", "a2", "Otro")

    private fun profile(disliked: DislikeStore.Disliked) = runBlocking {
        AffinityEngine.buildProfile(events = listOf(play(fav, 10), play(fav, 20), play(other, 30)), disliked = disliked)
    }

    @Test
    fun `the artist key is the id, or the lower-cased name when YouTube gave none`() {
        assertEquals("UC1", SoftDislikeRank.artistKey("UC1", "Name"))
        assertEquals("name x", SoftDislikeRank.artistKey(null, "  Name X "))
        assertEquals("name x", SoftDislikeRank.artistKey(" ", "Name X"))
        assertNull(SoftDislikeRank.artistKey(null, " "))
        assertNull(SoftDislikeRank.artistKey(null, null))
    }

    @Test
    fun `a marked song or artist matches by id or by name`() {
        val d = DislikeStore.Disliked(softSongs = setOf("s9"), softArtists = setOf("a2", "sin id"))
        assertTrue(SoftDislikeRank.matches(d, "s9", emptyList(), emptyList()))
        assertTrue(SoftDislikeRank.matches(d, "x", listOf("a2"), listOf("Otro")))
        assertTrue(SoftDislikeRank.matches(d, null, listOf(null), listOf("Sin Id")))
        assertFalse(SoftDislikeRank.matches(d, "x", listOf("a1"), listOf("Favorito")))
        assertFalse(SoftDislikeRank.matches(DislikeStore.Disliked(), "s9", listOf("a2"), listOf("Otro")))
    }

    @Test
    fun `the Home penalty is bounded and nothing marked costs nothing`() {
        val p = profile(DislikeStore.Disliked(softArtists = setOf("a1")))
        assertEquals(SoftDislikeRank.HOME_PENALTY, p.softPenalty("s1", listOf("a1"), listOf("Favorito")), 0.0)
        assertEquals(0.0, p.softPenalty("s2", listOf("a2"), listOf("Otro")), 0.0)
        assertEquals(0.0, profile(DislikeStore.Disliked()).softPenalty("s1", listOf("a1"), listOf("Favorito")), 0.0)
    }

    @Test
    fun `a marked favourite drops but stays above an unknown song - less, never gone`() {
        val p = profile(DislikeStore.Disliked(softArtists = setOf("a1")))
        val favourite = p.score(fav) - p.softPenalty(fav.id, fav.artists.map { it.id }, fav.artists.map { it.name })
        val unknown = 0.0
        assertTrue(favourite < p.score(fav))
        assertTrue(favourite > unknown)
        assertTrue(favourite > TasteProfile.AVOID)
    }

    @Test
    fun `soft marks do not touch the taste weights themselves`() {
        val plain = profile(DislikeStore.Disliked())
        val marked = profile(DislikeStore.Disliked(softSongs = setOf("s1"), softArtists = setOf("a1")))
        assertEquals(plain.score(fav), marked.score(fav), 1e-9)
        assertEquals(plain.scoreNames(listOf("Favorito"), "Ts1"), marked.scoreNames(listOf("Favorito"), "Ts1"), 1e-9)
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the menu marks the song, its primary artist and the smart queue's skip memory`() {
        val menu = main("ui/menu/YouTubeSongMenu.kt")
        val item = menu.substringAfter("title = { Text(text = \"Menos de esto\") }").substringBefore("onDismiss()")
        assertTrue(item.contains("description = { Text(text = \"Se recomendará menos parecido a esto\") }"))
        assertTrue(item.contains("store.softDislikeSong(song.id)"))
        assertTrue(item.contains("SoftDislikeRank.artistKey(primary?.id, primary?.name)"))
        assertTrue(item.contains("?.let { store.softDislikeArtist(it) }"))
        assertTrue(item.contains("ContinuationSkipMemory.recordSkip(context, skipKey)"))
        assertTrue("the prefs write stays off the main thread", item.contains("withContext(Dispatchers.IO)"))
    }

    @Test
    fun `Home ranks with the soft sets, songs and albums alike`() {
        val home = main("viewmodels/HomeViewModel.kt")
        val ranked = home.substringAfter("private fun List<Song>.rankedByTaste()").substringBefore("Diversity re-rank")
        assertTrue(ranked.contains("p.score(it) - p.softPenalty(it.id, it.artists.map { a -> a.id }, it.artists.map { a -> a.name })"))
        val yt = home.substringAfter("private fun tasteScoreYt(").substringBefore("private fun List<com.music.innertube.models.YTItem>.rankedByTasteYt()")
        assertTrue(yt.contains("p.softPenalty(item.id, item.artists.map { it.id }, item.artists.map { it.name })"))
        assertTrue(yt.contains("p.softPenalty(null, item.artists?.map { it.id } ?: emptyList(), item.artists?.map { it.name } ?: emptyList())"))
        assertFalse("never a filter (rows 39/116)", ranked.contains("softSongs") || ranked.contains("softArtists"))
    }
}
