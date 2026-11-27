package iad1tya.echo.music.stats

import iad1tya.echo.music.cloud.CloudSongIds
import iad1tya.echo.music.stats.ListeningStats.ArtistInfo
import iad1tya.echo.music.stats.ListeningStats.Play
import iad1tya.echo.music.stats.ListeningStats.SongInfo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 909 — the statistics computed on the phone: time only when played, periods at the user's local midnight,
 * one song across the three sources, streaks, hours, days, new artists and genres.
 */
class ListeningStatsTest {
    private val min = 60_000L
    private val phone = "content://media/external/audio/media/7"
    private val cloud = CloudSongIds.of("aura-onedrive", "Music/a.flac")
    private val online = "dQw4w9WgXcQ"

    private fun song(
        id: String,
        title: String = "Song",
        artist: String = "Artist",
        artistId: String = "UCartist",
        album: String? = "Album",
        albumId: String? = "MPREb_album",
        seconds: Int = 200,
    ) = SongInfo(id, title, listOf(ArtistInfo(artistId, artist)), albumId, album, seconds, null)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, mi: Int = 0) = LocalDateTime.of(y, m, d, h, mi)

    private val year2026 = ListeningStats.year(2026)

    @Test
    fun `time counts only what was played, a synced listen is a play without time`() {
        val songs = mapOf("a" to song("a"))
        val s = ListeningStats.summarize(
            year2026,
            listOf(Play("a", at(2026, 2, 1), 3 * min), Play("a", at(2026, 2, 2), 0L), Play("a", at(2026, 2, 3), -5L)),
            songs,
        )
        assertEquals(3 * min, s.playTimeMs)
        assertEquals(3, s.plays)
        assertEquals(3, s.listeningDays)
        assertEquals(3, s.topSongs.single().plays)
    }

    @Test
    fun `periods start at local midnight and the stored timestamp round-trips`() {
        val y = ListeningStats.year(2026)
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), y.start)
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), y.endExclusive)
        assertTrue(LocalDateTime.of(2026, 12, 31, 23, 59, 59) in y)
        assertTrue(LocalDateTime.of(2027, 1, 1, 0, 0) !in y)
        val m = ListeningStats.month(YearMonth.of(2024, 2))
        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0), m.endExclusive)
        val w = ListeningStats.week(LocalDate.of(2026, 11, 27), DayOfWeek.MONDAY)
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), w.start)
        assertEquals(LocalDateTime.of(2026, 11, 30, 0, 0), w.endExclusive)
        val sunday = ListeningStats.week(LocalDate.of(2026, 11, 27), DayOfWeek.SUNDAY)
        assertEquals(LocalDateTime.of(2026, 11, 22, 0, 0), sunday.start)

        // The stored value is the local wall-clock read as UTC (Converters): 23:30 local stays 23:30 on its own day.
        val late = LocalDateTime.of(2026, 12, 31, 23, 30, 15, 250_000_000)
        val stored = late.toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(stored, ListeningStats.storedMillis(late))
        assertEquals(late, ListeningStats.fromStored(stored))
        assertEquals(y.startStored, LocalDateTime.of(2026, 1, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

        // A listen at 23:59 on Dec 31 is this year's; one at 00:00 on Jan 1 is next year's.
        val s = ListeningStats.summarize(
            y,
            listOf(Play("a", at(2026, 12, 31, 23, 59), min), Play("a", at(2027, 1, 1, 0, 0), min), Play("a", at(2025, 12, 31, 23, 59), min)),
            mapOf("a" to song("a")),
        )
        assertEquals(1, s.plays)
    }

    @Test
    fun `one song heard from the phone, the cloud and online counts once`() {
        val songs = mapOf(
            online to song(online, title = "Song (Official Video)", artistId = "UCartist"),
            phone to song(phone, artistId = "LOCAL_ARTIST_1", albumId = "LA1", album = "Album"),
            cloud to song(cloud, artistId = "LOCAL_ARTIST_1", albumId = "LA1", seconds = 201),
            "other" to song("other", title = "Another one"),
        )
        val s = ListeningStats.summarize(
            year2026,
            listOf(
                Play(online, at(2026, 3, 1), 1 * min),
                Play(phone, at(2026, 3, 2), 4 * min),
                Play(cloud, at(2026, 3, 3), 2 * min),
                Play("other", at(2026, 3, 4), 5 * min),
            ),
            songs,
        )
        assertEquals(2, s.songs)
        assertEquals(listOf(7 * min, 5 * min), s.topSongs.map { it.playTimeMs })
        assertEquals("the most listened copy leads", phone, s.topSongs.first().id)
        assertEquals(3, s.topSongs.first().plays)
        assertEquals("the local artist row and the YouTube one are one act", 1, s.artists)
        assertEquals("UCartist", s.topArtists.single().id)
        assertEquals(12 * min, s.topArtists.single().playTimeMs)
        assertEquals("the same album title by the same artist is one album", 1, s.albums)
    }

    @Test
    fun `tops by time and by plays differ`() {
        val songs = mapOf("long" to song("long", title = "Long"), "short" to song("short", title = "Short", artist = "B", artistId = "UCb"))
        val s = ListeningStats.summarize(
            year2026,
            listOf(Play("long", at(2026, 1, 5), 20 * min)) + (1..3).map { Play("short", at(2026, 1, 5 + it), min) },
            songs,
        )
        assertEquals("long", s.topSongs.first().id)
        assertEquals("short", s.topSongsByPlays.first().id)
        assertEquals("UCb", s.topArtistsByPlays.first().id)
    }

    @Test
    fun `streaks, the top day, hours and days of the week`() {
        val songs = mapOf("a" to song("a"))
        val plays = listOf(
            Play("a", at(2026, 4, 1, 23), 10 * min), // Wednesday
            Play("a", at(2026, 4, 2, 8), 5 * min),
            Play("a", at(2026, 4, 3, 8), 5 * min),
            Play("a", at(2026, 4, 10, 22), 30 * min), // Friday, the top day
            Play("a", at(2026, 4, 11, 22), 1 * min),
        )
        val s = ListeningStats.summarize(year2026, plays, songs, today = LocalDate.of(2026, 4, 12))
        assertEquals(3, s.longestStreak!!.days)
        assertEquals(LocalDate.of(2026, 4, 1), s.longestStreak!!.first)
        assertEquals(LocalDate.of(2026, 4, 3), s.longestStreak!!.last)
        assertEquals("today has nothing yet: the streak up to yesterday still counts", 2, s.currentStreak)
        assertEquals(LocalDate.of(2026, 4, 10), s.topDay!!.day)
        assertEquals(10 * min, s.playTimeByHour[23])
        assertEquals(10 * min, s.playTimeByHour[8])
        assertEquals(31 * min, s.playTimeByHour[22])
        assertEquals(24, s.playTimeByHour.size)
        assertEquals(10 * min, s.playTimeByDayOfWeek[DayOfWeek.WEDNESDAY.value - 1])
        assertEquals("April 3 and 10 are both Fridays", 35 * min, s.playTimeByDayOfWeek[DayOfWeek.FRIDAY.value - 1])
        assertEquals(7, s.playTimeByDayOfWeek.size)

        val broken = ListeningStats.summarize(year2026, plays, songs, today = LocalDate.of(2026, 4, 14))
        assertEquals("two days without music break the current streak", 0, broken.currentStreak)
        assertEquals(0, ListeningStats.summarize(year2026, plays, songs).currentStreak)
    }

    @Test
    fun `a streak across a month boundary and nothing played at all`() {
        val songs = mapOf("a" to song("a"))
        val s = ListeningStats.summarize(
            year2026,
            listOf(Play("a", at(2026, 1, 31), min), Play("a", at(2026, 2, 1), min), Play("a", at(2026, 2, 2), min)),
            songs,
        )
        assertEquals(3, s.longestStreak!!.days)
        val empty = ListeningStats.summarize(year2026, emptyList(), songs, today = LocalDate.of(2026, 5, 1))
        assertEquals(0, empty.plays)
        assertNull(empty.longestStreak)
        assertNull(empty.topDay)
        assertEquals(0, empty.currentStreak)
        assertTrue(empty.topSongs.isEmpty())
    }

    @Test
    fun `new artists are those first played inside the range, and genres come from what the phone knows`() {
        val songs = mapOf(
            "old" to song("old", artist = "Old Band", artistId = "UCold"),
            "new" to song("new", title = "Fresh", artist = "New Band", artistId = "UCnew"),
        )
        val firsts = ListeningStats.artistFirstPlays(
            listOf(
                Triple("UCold", "Old Band", at(2024, 6, 1)),
                Triple("LOCAL_ARTIST_9", "New band", at(2026, 2, 1)),
                Triple("UCnew", "New Band", at(2026, 3, 1)),
            ),
        )
        assertEquals("the earliest row of an act wins", at(2026, 2, 1), firsts[StatsDedupe.artistKey("UCnew", "New Band")])
        val s = ListeningStats.summarize(
            year2026,
            listOf(Play("old", at(2026, 3, 1), 5 * min), Play("new", at(2026, 3, 2), 2 * min)),
            songs,
            genreByArtistName = mapOf("old band" to "Rock"),
            artistFirstPlayed = firsts,
        )
        assertEquals(listOf("UCnew"), s.newArtists.map { it.id })
        assertEquals(listOf("Rock"), s.topGenres.map { it.name })
        assertEquals(5 * min, s.topGenres.single().playTimeMs)
    }

    @Test
    fun `rows read from Room become plays and songs`() {
        val (plays, songs) = statsInput(
            listOf(StatsPlayRow("a", ListeningStats.storedMillis(at(2026, 1, 2, 7)), 90_000L)),
            listOf(StatsSongRow("a", "Song", 200, "MPREb", "Album", null)),
            listOf(StatsSongArtistRow("a", "UC2", "Second", 1), StatsSongArtistRow("a", "UC1", "First", 0)),
        )
        assertEquals(at(2026, 1, 2, 7), plays.single().at)
        assertEquals(listOf("First", "Second"), songs.getValue("a").artists.map { it.name })
        assertEquals("Album", songs.getValue("a").albumTitle)
    }

    @Test
    fun `the time reads in hours and minutes and the charts never divide by zero`() {
        assertEquals("0m", StatsFormat.hoursMinutes(0))
        assertEquals("45m", StatsFormat.hoursMinutes(45 * min))
        assertEquals("3h", StatsFormat.hoursMinutes(180 * min))
        assertEquals("3h 20m", StatsFormat.hoursMinutes(200 * min))
        assertEquals(listOf(0f, 0f), StatsFormat.fractions(listOf(0L, 0L)))
        assertEquals(listOf(0.5f, 1f), StatsFormat.fractions(listOf(5L, 10L)))
    }
}
