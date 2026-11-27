package iad1tya.echo.music.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * Registry row 909 — the numbers of Estadísticas and of «Tu año en Aura» (row 910), computed on the phone from the
 * `event` table and nothing else. Pure (tested on the JVM): no database, no network, no clock read.
 *
 * TIME. An event is written once a listen ends (`MusicService.onPlaybackStatsReady`), only when it lasted at least the
 * history threshold, with media3's time in the PLAYING state: pauses, buffering and a skip before the threshold add
 * nothing. A listen another device heard arrives through the sync with `playTime = 0` (row 558): it is a play and a
 * listening day, but adds no time here. Negative values never subtract.
 *
 * WHEN. `event.timestamp` is the local wall-clock time of the phone, stored read as UTC (`Converters`), so [fromStored]
 * gives back the user's own hour and day, and every period below starts at the user's local midnight.
 *
 * ONCE. A song, artist or album heard from the phone, the cloud and online counts once ([StatsDedupe]).
 */
object ListeningStats {
    data class Play(val songId: String, val at: LocalDateTime, val playTimeMs: Long)

    data class ArtistInfo(val id: String, val name: String)

    data class SongInfo(
        val id: String,
        val title: String,
        val artists: List<ArtistInfo>,
        val albumId: String?,
        val albumTitle: String?,
        val durationSeconds: Int,
        val thumbnailUrl: String?,
    )

    /** Local wall-clock range, start included, end excluded. */
    data class Range(val start: LocalDateTime, val endExclusive: LocalDateTime) {
        operator fun contains(t: LocalDateTime): Boolean = !t.isBefore(start) && t.isBefore(endExclusive)
        val startStored: Long get() = storedMillis(start)
        val endStored: Long get() = storedMillis(endExclusive)
    }

    fun year(year: Int): Range = Range(LocalDate.of(year, 1, 1).atStartOfDay(), LocalDate.of(year + 1, 1, 1).atStartOfDay())

    fun month(month: YearMonth): Range = Range(month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay())

    fun week(day: LocalDate, firstDayOfWeek: DayOfWeek): Range {
        val start = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return Range(start.atStartOfDay(), start.plusWeeks(1).atStartOfDay())
    }

    val ALL_TIME: Range = Range(LocalDate.of(1970, 1, 1).atStartOfDay(), LocalDate.of(9999, 1, 1).atStartOfDay())

    /** The value `event.timestamp` holds for [t] (what `Converters.dateToTimestamp` writes). */
    fun storedMillis(t: LocalDateTime): Long = t.toInstant(ZoneOffset.UTC).toEpochMilli()

    /** The local wall-clock time an `event.timestamp` stands for (what `Converters.fromTimestamp` reads). */
    fun fromStored(ms: Long): LocalDateTime =
        LocalDateTime.ofEpochSecond(Math.floorDiv(ms, 1000L), (Math.floorMod(ms, 1000L) * 1_000_000L).toInt(), ZoneOffset.UTC)

    /** One row of a top list. [id] is the copy (song, artist, album) that represents the group. */
    data class Entry(
        val key: String,
        val id: String,
        val name: String,
        val subtitle: String?,
        val thumbnailUrl: String?,
        val playTimeMs: Long,
        val plays: Int,
    )

    data class DayTotal(val day: LocalDate, val playTimeMs: Long)

    data class Streak(val days: Int, val first: LocalDate, val last: LocalDate)

    data class Summary(
        val range: Range,
        val playTimeMs: Long,
        val plays: Int,
        val songs: Int,
        val artists: Int,
        val albums: Int,
        val topSongs: List<Entry>,
        val topSongsByPlays: List<Entry>,
        val topArtists: List<Entry>,
        val topArtistsByPlays: List<Entry>,
        val topAlbums: List<Entry>,
        val topAlbumsByPlays: List<Entry>,
        /** Only artists whose genre the phone already knows (`GenreCache`, filled by the taste engine): nothing is fetched. */
        val topGenres: List<Entry>,
        /** 24 values, index = hour of the day (local). */
        val playTimeByHour: List<Long>,
        /** 7 values, index 0 = Monday … 6 = Sunday. */
        val playTimeByDayOfWeek: List<Long>,
        val listeningDays: Int,
        val topDay: DayTotal?,
        val longestStreak: Streak?,
        /**
         * Consecutive listening days up to today (or up to yesterday while today has none yet); 0 without `today`. Counted
         * over `listeningDaysEver` when given (the real current streak, whatever the range), else over the range's days.
         */
        val currentStreak: Int,
        /** Artists first heard in this range (their first play ever falls inside it), most listened first. */
        val newArtists: List<Entry>,
    ) {
        val minutes: Long get() = playTimeMs / 60_000L
    }

    private class Acc(val key: String, var id: String, var name: String, var subtitle: String?, var thumbnailUrl: String?) {
        var timeMs = 0L
        var count = 0
        fun entry() = Entry(key, id, name, subtitle, thumbnailUrl, timeMs, count)
    }

    private fun top(accs: Collection<Acc>, limit: Int, byPlays: Boolean): List<Entry> {
        val order = if (byPlays) {
            compareByDescending<Acc> { it.count }.thenByDescending { it.timeMs }
        } else {
            compareByDescending<Acc> { it.timeMs }.thenByDescending { it.count }
        }
        return accs.sortedWith(order.thenBy { it.name.lowercase() }).take(limit).map { it.entry() }
    }

    private fun isChannelId(id: String) = id.startsWith("UC")

    /**
     * Everything the screens show for [range]. Plays outside [range] are ignored, so a caller may pass more.
     *
     * @param genreByArtistName lowercased artist name -> genre (`GenreCache.snapshot`).
     * @param artistFirstPlayed [StatsDedupe.artistKey] -> the first time that artist was ever played.
     * @param today the user's local date, for [Summary.currentStreak].
     * @param listeningDaysEver every local day with a listen, ever, so [Summary.currentStreak] is not cut by [range].
     */
    fun summarize(
        range: Range,
        plays: List<Play>,
        songs: Map<String, SongInfo>,
        genreByArtistName: Map<String, String> = emptyMap(),
        artistFirstPlayed: Map<String, LocalDateTime> = emptyMap(),
        today: LocalDate? = null,
        listeningDaysEver: Set<LocalDate>? = null,
        limit: Int = 5,
    ): Summary {
        val inRange = plays.filter { it.at in range }
        fun ms(p: Play) = p.playTimeMs.coerceAtLeast(0L)

        // Song groups: the most listened copy of a song leads its group.
        val msBySongId = HashMap<String, Long>()
        for (p in inRange) msBySongId[p.songId] = (msBySongId[p.songId] ?: 0L) + ms(p)
        val orderedIds = msBySongId.keys.sortedWith(compareByDescending<String> { msBySongId[it] ?: 0L }.thenBy { it })
        val groupOf = StatsDedupe.songGroups(
            orderedIds.map { id ->
                val s = songs[id]
                StatsDedupe.SongKey(id, s?.title.orEmpty(), s?.artists?.map { it.name }.orEmpty(), s?.durationSeconds ?: -1)
            },
        )

        val songAcc = LinkedHashMap<String, Acc>()
        val artistAcc = LinkedHashMap<String, Acc>()
        val albumAcc = LinkedHashMap<String, Acc>()
        val genreAcc = LinkedHashMap<String, Acc>()
        val byHour = LongArray(24)
        val byDow = LongArray(7)
        val byDay = java.util.TreeMap<LocalDate, Long>()
        var total = 0L

        for (p in inRange) {
            val t = ms(p)
            total += t
            byHour[p.at.hour] += t
            byDow[p.at.dayOfWeek.value - 1] += t
            val day = p.at.toLocalDate()
            byDay[day] = (byDay[day] ?: 0L) + t

            val groupId = groupOf[p.songId] ?: p.songId
            val lead = songs[groupId] ?: songs[p.songId]
            val song = songs[p.songId]
            songAcc.getOrPut(groupId) {
                Acc(groupId, groupId, lead?.title ?: groupId, lead?.artists?.firstOrNull()?.name, lead?.thumbnailUrl)
            }.apply { timeMs += t; count++ }

            if (song == null) continue
            for (artist in song.artists.distinctBy { StatsDedupe.artistKey(it.id, it.name) }) {
                val key = StatsDedupe.artistKey(artist.id, artist.name)
                val acc = artistAcc.getOrPut(key) { Acc(key, artist.id, artist.name, null, null) }
                if (!isChannelId(acc.id) && isChannelId(artist.id)) {
                    acc.id = artist.id
                    acc.name = artist.name
                }
                acc.timeMs += t
                acc.count++
            }

            val firstArtist = song.artists.firstOrNull()?.name
            val albumTitle = song.albumTitle
            if (!albumTitle.isNullOrBlank()) {
                val albumKey = StatsDedupe.albumKey(song.albumId ?: "", albumTitle, firstArtist)
                if (song.albumId != null || !albumKey.startsWith("id:")) {
                    albumAcc.getOrPut(albumKey) {
                        Acc(albumKey, song.albumId ?: albumKey, albumTitle, firstArtist, song.thumbnailUrl)
                    }.apply { timeMs += t; count++ }
                }
            }

            val genre = song.artists.firstNotNullOfOrNull { genreByArtistName[it.name.lowercase()]?.takeIf(String::isNotBlank) }
            if (genre != null) {
                genreAcc.getOrPut(genre.lowercase()) { Acc(genre.lowercase(), genre, genre, null, null) }.apply { timeMs += t; count++ }
            }
        }

        val days = byDay.keys.toList()
        var longest: Streak? = null
        var runStart: LocalDate? = null
        var prev: LocalDate? = null
        for (d in days) {
            if (prev == null || d != prev.plusDays(1)) runStart = d
            val length = (d.toEpochDay() - runStart!!.toEpochDay() + 1).toInt()
            if (longest == null || length > longest.days) longest = Streak(length, runStart, d)
            prev = d
        }

        var current = 0
        val daySet: Set<LocalDate> = listeningDaysEver ?: byDay.keys
        if (today != null && daySet.isNotEmpty()) {
            var cursor: LocalDate = if (today in daySet) today else today.minusDays(1)
            while (cursor in daySet) {
                current++
                cursor = cursor.minusDays(1)
            }
        }

        val topDay = byDay.entries
            .maxWithOrNull(compareBy<Map.Entry<LocalDate, Long>> { it.value }.thenByDescending { it.key })
            ?.let { DayTotal(it.key, it.value) }

        val newArtists = artistAcc.values
            .filter { acc -> artistFirstPlayed[acc.key]?.let { it in range } == true }
            .sortedWith(compareByDescending<Acc> { it.timeMs }.thenBy { it.name.lowercase() })
            .map { it.entry() }

        return Summary(
            range = range,
            playTimeMs = total,
            plays = inRange.size,
            songs = songAcc.size,
            artists = artistAcc.size,
            albums = albumAcc.size,
            topSongs = top(songAcc.values, limit, byPlays = false),
            topSongsByPlays = top(songAcc.values, limit, byPlays = true),
            topArtists = top(artistAcc.values, limit, byPlays = false),
            topArtistsByPlays = top(artistAcc.values, limit, byPlays = true),
            topAlbums = top(albumAcc.values, limit, byPlays = false),
            topAlbumsByPlays = top(albumAcc.values, limit, byPlays = true),
            topGenres = top(genreAcc.values, limit, byPlays = false),
            playTimeByHour = byHour.toList(),
            playTimeByDayOfWeek = byDow.toList(),
            listeningDays = byDay.size,
            topDay = topDay?.takeIf { it.playTimeMs > 0L },
            longestStreak = longest,
            currentStreak = current,
            newArtists = newArtists,
        )
    }

    /** Folds the per-artist-row first plays into one per act ([StatsDedupe.artistKey]): the earliest wins. */
    fun artistFirstPlays(rows: List<Triple<String, String, LocalDateTime>>): Map<String, LocalDateTime> {
        val out = HashMap<String, LocalDateTime>()
        for ((id, name, first) in rows) {
            val key = StatsDedupe.artistKey(id, name)
            val known = out[key]
            if (known == null || first.isBefore(known)) out[key] = first
        }
        return out
    }
}
