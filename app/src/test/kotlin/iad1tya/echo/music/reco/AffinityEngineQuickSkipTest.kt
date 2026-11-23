package iad1tya.echo.music.reco

import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.Event
import iad1tya.echo.music.db.entities.EventWithSong
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.dislike.DislikeStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 720 — a quick skip the history never recorded now reaches the taste profile, weighed like the play it
 * would have been (same decay, same completion → quality mapping), never adding taste and capped per artist.
 */
class AffinityEngineQuickSkipTest {
    private val now = System.currentTimeMillis()
    private val day = 86_400_000L

    private fun song(id: String, title: String, artistId: String, artistName: String) = Song(
        song = SongEntity(id = id, title = title, duration = 200),
        artists = listOf(ArtistEntity(id = artistId, name = artistName)),
    )

    private fun play(s: Song, minutesAgo: Long) = EventWithSong(
        event = Event(songId = s.song.id, timestamp = LocalDateTime.now().minusMinutes(minutesAgo), playTime = 200_000L),
        song = s,
    )

    private val loved = song("s1", "Uno", "a1", "Artista Querido")
    private val skipped = song("s2", "Dos", "a2", "Artista Saltado")

    private fun skip(s: Song, atMs: Long = now, listenedMs: Long = 5_000, withId: Boolean = true) = QuickSkipLog.Entry(
        songId = s.song.id,
        artistId = if (withId) s.artists.first().id else null,
        artistName = s.artists.first().name,
        atMs = atMs,
        listenedMs = listenedMs,
        durationMs = s.song.duration * 1000L,
    )

    // Both artists heard exactly alike (one shared list, so every profile sees the same timestamps): any difference
    // comes from the skips.
    private val events = listOf(play(loved, 10), play(skipped, 10), play(loved, 60), play(skipped, 60))

    private fun profile(
        skips: List<QuickSkipLog.Entry>,
        disliked: DislikeStore.Disliked = DislikeStore.Disliked(),
    ) = runBlocking {
        AffinityEngine.buildProfile(events = events, disliked = disliked, now = now, quickSkips = skips)
    }

    private fun TasteProfile.named(s: Song) = scoreNames(s.artists.map { it.name }, s.song.title)

    private fun hourOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).hour

    @Test
    fun `without skips both artists score the same and the default changes nothing`() {
        val p = profile(emptyList())
        assertEquals(p.named(loved), p.named(skipped), 1e-9)
        val byDefault = runBlocking { AffinityEngine.buildProfile(events = events, disliked = DislikeStore.Disliked(), now = now) }
        assertEquals(byDefault.named(skipped), p.named(skipped), 0.0)
    }

    @Test
    fun `quick skips pull their artist below one heard just as much, by name and by id`() {
        val p = profile(listOf(skip(skipped), skip(skipped)))
        assertTrue(p.named(skipped) < p.named(loved))
        assertTrue(p.score(skipped) < p.score(loved))
        assertTrue("less, never AVOID", p.named(skipped) > TasteProfile.AVOID)
    }

    @Test
    fun `an entry without artist id still reaches remote candidates by name`() {
        val p = profile(listOf(skip(skipped, withId = false)))
        assertTrue(p.named(skipped) < p.named(loved))
    }

    @Test
    fun `a skip weighs like the play it would have been and never adds`() {
        val fresh = skip(skipped, listenedMs = 5_000)
        val expected = (5_000.0 / 200_000 - 0.30) / (1 - 0.30) * 1.25
        assertEquals(expected, AffinityEngine.quickSkipWeight(fresh, now, hourOf(fresh.atMs)), 1e-6)
        val heard = skip(skipped, listenedMs = 200_000)
        assertEquals(0.0, AffinityEngine.quickSkipWeight(heard, now, hourOf(heard.atMs)), 0.0)
        val unknownLength = fresh.copy(durationMs = 0)
        assertEquals(-0.30 / (1 - 0.30) * 1.25, AffinityEngine.quickSkipWeight(unknownLength, now, hourOf(now)), 1e-6)
    }

    @Test
    fun `an old skip fades with the same half-life as a play`() {
        val fresh = skip(skipped)
        val old = skip(skipped, atMs = now - 30 * day)
        val ratio = AffinityEngine.quickSkipWeight(old, now, hourOf(now)) / AffinityEngine.quickSkipWeight(fresh, now, hourOf(now))
        assertEquals(0.5, ratio, 1e-3)
    }

    @Test
    fun `the penalty per artist is capped, so a burst of skips cannot bury it`() {
        val one = profile(listOf(skip(skipped)))
        val many = profile(List(200) { skip(skipped) })
        val more = profile(List(400) { skip(skipped) })
        assertTrue(many.named(skipped) < one.named(skipped))
        assertEquals(many.named(skipped), more.named(skipped), 1e-9)
        assertEquals(many.score(skipped), more.score(skipped), 1e-9)
    }

    @Test
    fun `a skip of a song marked No me gusta feeds nothing, as its plays do (row 285)`() {
        val p = profile(List(5) { skip(skipped) }, DislikeStore.Disliked(songs = setOf("s2")))
        assertEquals(0.0, p.named(skipped), 1e-9)
        assertEquals(TasteProfile.AVOID, p.score(skipped), 0.0)
        assertTrue(p.named(loved) > 0.0)
    }

    @Test
    fun `a disliked artist stays AVOID with skips on record`() {
        val p = profile(listOf(skip(skipped)), DislikeStore.Disliked(artists = setOf("a2")))
        assertEquals(TasteProfile.AVOID, p.named(skipped), 0.0)
    }
}
