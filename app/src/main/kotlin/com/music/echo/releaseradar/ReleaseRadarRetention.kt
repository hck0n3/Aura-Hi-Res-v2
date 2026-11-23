package iad1tya.echo.music.releaseradar

import iad1tya.echo.music.db.entities.ReleaseRadarItem
import java.time.LocalDate

/**
 * Cumulative Release Radar (owner 2026-11-23, registry row 674): a release stays in Novedades until the user has
 * SEEN it there AND played at least one of its songs, for at most [MAX_AGE_DAYS]; with nothing left the screen says
 * «Estás al día». Pure rules (JVM-tested); the "played" half is the `DatabaseDao.retiredReleaseIds` query.
 */
object ReleaseRadarRetention {
    /** Oldest a release may be and still be shown. It is also the worker's fetch window, so a release pruned
     *  for age can never be fetched again as new. */
    const val MAX_AGE_DAYS = 60L

    /** `release_radar.source` of the year-only YouTube fallback (its releaseDate is 1 January of the year). */
    const val SOURCE_YT = "yt"

    /** The date a row's age counts from: the exact release date, or — year-only YouTube rows, whose date is a
     *  placeholder — the day the radar first stored it (insert-IGNORE keeps the first fetchedAt). */
    fun ageAnchor(row: ReleaseRadarItem): LocalDate =
        if (row.source == SOURCE_YT) row.fetchedAt.toLocalDate() else row.releaseDate.toLocalDate()

    fun isExpired(row: ReleaseRadarItem, today: LocalDate): Boolean =
        ageAnchor(row).isBefore(today.minusDays(MAX_AGE_DAYS))

    /**
     * What is drawn: not expired, and not retired (seen + played) — except a release whose card was already shown
     * during this visit ([shownThisVisit], by playId), so a card never vanishes under the user's finger while he is
     * looking at it or listening to it; it leaves on the next visit.
     */
    fun visible(
        rows: List<ReleaseRadarItem>,
        retiredIds: Set<String>,
        shownThisVisit: Set<String>,
        today: LocalDate,
    ): List<ReleaseRadarItem> = rows.filter { row ->
        !isExpired(row, today) && (row.id !in retiredIds || row.playId in shownThisVisit)
    }

    /**
     * «Estás al día»: nothing left to show although the radar has had releases — rows still in the table (retired or
     * expired), or [hadReleases] once even those were pruned. A radar that never stored anything keeps the
     * «Aún no hay novedades» hint.
     */
    fun caughtUp(rows: List<ReleaseRadarItem>, visible: List<ReleaseRadarItem>, hadReleases: Boolean): Boolean =
        visible.isEmpty() && (rows.isNotEmpty() || hadReleases)

    /** Exact-date rows older than this are deleted (outside the fetch window). */
    fun pruneThreshold(today: LocalDate): LocalDate = today.minusDays(MAX_AGE_DAYS)

    /** Year-only YouTube rows are deleted only once their year is over: until then the fallback fetches them again,
     *  and the kept row (seen flag included) is what stops a retired release from coming back as new. */
    fun ytPruneThreshold(today: LocalDate): LocalDate = LocalDate.of(today.year, 1, 1)
}
