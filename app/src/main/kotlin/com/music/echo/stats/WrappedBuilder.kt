package iad1tya.echo.music.stats

import java.time.LocalDate
import java.time.YearMonth

/**
 * Registry row 910 — «Tu año en Aura» / «Tu mes»: the sequence of full-screen cards, built on the phone from a
 * [ListeningStats.Summary]. Pure (tested on the JVM): no AI, no network; the only thing that can leave the phone is the
 * summary image the user chooses to share.
 */
object WrappedBuilder {
    /**
     * Below either of these the period «has too little data» and the screen says so instead of showing empty cards.
     * Chosen here (not measured): ten listens and half an hour are the least that makes a top 5 and a «top day» mean
     * something. [NO VERIFICADO con el dueño] — tune here if he prefers another floor.
     */
    const val MIN_PLAYS = 10
    const val MIN_MINUTES = 30L

    /** How many days into January the Home card still offers the year that just ended (and «Tu mes» the past month). */
    const val EARLY_DAYS = 7

    enum class Kind { YEAR, MONTH }

    /** The «personalidad» line, from the user's own numbers. Rules in [personality]. */
    enum class Personality { NIGHT_OWL, EARLY_BIRD, LOYAL_FAN, EXPLORER, MARATHON, ALL_ROUNDER }

    sealed interface Card {
        data object Intro : Card
        data class Minutes(val minutes: Long, val plays: Int, val listeningDays: Int, val songs: Int, val artists: Int) : Card
        data class TopArtists(val entries: List<ListeningStats.Entry>) : Card
        data class TopSongs(val entries: List<ListeningStats.Entry>) : Card
        data class TopAlbums(val entries: List<ListeningStats.Entry>) : Card
        data class TopGenres(val entries: List<ListeningStats.Entry>) : Card
        data class TopDay(val day: ListeningStats.DayTotal) : Card
        data class LongestStreak(val streak: ListeningStats.Streak) : Card
        data class NewArtists(val count: Int, val first: ListeningStats.Entry) : Card
        data class PersonalityLine(val personality: Personality) : Card
        data class Final(
            val minutes: Long,
            val topArtist: String?,
            val topSong: String?,
            val topGenre: String?,
            val personality: Personality,
        ) : Card
    }

    sealed interface Story {
        data class TooLittle(val plays: Int, val minutes: Long) : Story
        data class Ready(val cards: List<Card>) : Story
    }

    fun hasEnough(summary: ListeningStats.Summary): Boolean = summary.plays >= MIN_PLAYS && summary.minutes >= MIN_MINUTES

    fun build(summary: ListeningStats.Summary): Story {
        if (!hasEnough(summary)) return Story.TooLittle(summary.plays, summary.minutes)
        val personality = personality(summary)
        val cards = buildList {
            add(Card.Intro)
            add(Card.Minutes(summary.minutes, summary.plays, summary.listeningDays, summary.songs, summary.artists))
            if (summary.topArtists.isNotEmpty()) add(Card.TopArtists(summary.topArtists))
            if (summary.topSongs.isNotEmpty()) add(Card.TopSongs(summary.topSongs))
            if (summary.topAlbums.isNotEmpty()) add(Card.TopAlbums(summary.topAlbums))
            if (summary.topGenres.isNotEmpty()) add(Card.TopGenres(summary.topGenres))
            summary.topDay?.let { add(Card.TopDay(it)) }
            summary.longestStreak?.takeIf { it.days >= 2 }?.let { add(Card.LongestStreak(it)) }
            summary.newArtists.firstOrNull()?.let { add(Card.NewArtists(summary.newArtists.size, it)) }
            add(Card.PersonalityLine(personality))
            add(
                Card.Final(
                    minutes = summary.minutes,
                    topArtist = summary.topArtists.firstOrNull()?.name,
                    topSong = summary.topSongs.firstOrNull()?.name,
                    topGenre = summary.topGenres.firstOrNull()?.name,
                    personality = personality,
                ),
            )
        }
        return Story.Ready(cards)
    }

    /**
     * First rule that holds, in this order (shares are of the listening TIME of the period):
     *  - NIGHT_OWL: at least 35 % between 22:00 and 04:59;
     *  - EARLY_BIRD: at least 25 % between 05:00 and 08:59;
     *  - LOYAL_FAN: the top artist alone is at least 30 %;
     *  - EXPLORER: at least 10 artists and at least 40 % of them heard for the first time in the period;
     *  - MARATHON: two hours or more on an average listening day;
     *  - ALL_ROUNDER otherwise.
     * The thresholds are this file's choice, not measured: [NO VERIFICADO con el dueño].
     */
    fun personality(summary: ListeningStats.Summary): Personality {
        val total = summary.playTimeMs
        if (total <= 0L) return Personality.ALL_ROUNDER
        val byHour = summary.playTimeByHour
        fun share(hours: Iterable<Int>) = hours.sumOf { byHour.getOrElse(it) { 0L } }.toDouble() / total
        val night = share((22..23) + (0..4))
        val morning = share(5..8)
        val topArtistShare = (summary.topArtists.firstOrNull()?.playTimeMs ?: 0L).toDouble() / total
        val newShare = if (summary.artists > 0) summary.newArtists.size.toDouble() / summary.artists else 0.0
        val perDayMinutes = if (summary.listeningDays > 0) summary.minutes.toDouble() / summary.listeningDays else 0.0
        return when {
            night >= 0.35 -> Personality.NIGHT_OWL
            morning >= 0.25 -> Personality.EARLY_BIRD
            topArtistShare >= 0.30 -> Personality.LOYAL_FAN
            summary.artists >= 10 && newShare >= 0.40 -> Personality.EXPLORER
            perDayMinutes >= 120.0 -> Personality.MARATHON
            else -> Personality.ALL_ROUNDER
        }
    }

    /** The year «Tu año en Aura» shows today: the one ending in December, and still that one in early January. */
    fun wrappedYear(today: LocalDate): Int =
        if (today.monthValue == 1 && today.dayOfMonth <= EARLY_DAYS) today.year - 1 else today.year

    /** The month «Tu mes» shows today: the current one, or the one just ended during the first days of a month. */
    fun wrappedMonth(today: LocalDate): YearMonth {
        val month = YearMonth.from(today)
        return if (today.dayOfMonth <= EARLY_DAYS) month.minusMonths(1) else month
    }

    /** The year the Home card offers today (December, and the first days of January), or null; never a dismissed one. */
    fun homeCardYear(today: LocalDate, dismissedYear: Int?): Int? {
        val year = when {
            today.monthValue == 12 -> today.year
            today.monthValue == 1 && today.dayOfMonth <= EARLY_DAYS -> today.year - 1
            else -> return null
        }
        return year.takeIf { it != dismissedYear }
    }

    // ── The route argument: «2026» for a year, «2026-11» for a month ──────────────────────────────────────────────────

    // Plain digits whatever the phone's language (a locale-aware format could write Arabic or Devanagari digits).
    fun yearArg(year: Int): String = year.toString().padStart(4, '0')

    fun monthArg(month: YearMonth): String = yearArg(month.year) + "-" + month.monthValue.toString().padStart(2, '0')

    data class Period(val kind: Kind, val year: Int, val month: YearMonth?) {
        val range: ListeningStats.Range
            get() = if (month != null) ListeningStats.month(month) else ListeningStats.year(year)
    }

    fun parse(arg: String?): Period? {
        val a = arg?.trim().orEmpty()
        Regex("""^(\d{4})$""").matchEntire(a)?.let { return Period(Kind.YEAR, it.groupValues[1].toInt(), null) }
        Regex("""^(\d{4})-(\d{2})$""").matchEntire(a)?.let {
            val m = it.groupValues[2].toInt()
            if (m !in 1..12) return null
            val ym = YearMonth.of(it.groupValues[1].toInt(), m)
            return Period(Kind.MONTH, ym.year, ym)
        }
        return null
    }
}
