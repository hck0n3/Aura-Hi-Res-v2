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

    /**
     * Row 756: a Spotify token that cannot be refreshed skips the YouTube fallback only on the FIRST pass in a row
     * (the transient case). When it fails again on the next pass (expired or revoked `sp_dc`, a changed token
     * endpoint) the fallback runs, or no new release would reach Novedades until every Spotify row aged out (60 days).
     * Rows it stores are replaced by Spotify's once the token works again ([upgradeYtRows]).
     */
    fun skipFallbackOnTokenFailure(failedLastPass: Boolean): Boolean = !failedLastPass

    // ── Review of row 674: bounded YouTube lookups, and Spotify rows replacing fallback rows ──────────────────

    /** A release the table does not hold is searched on YouTube on every pass only while it is this young (the old
     *  7-day window, so a no-match is retried as before); an older one is searched once (battery, rule 7). */
    const val LOOKUP_RETRY_DAYS = 7L

    fun needsLookup(releaseDate: LocalDate, today: LocalDate, alreadyLookedUp: Boolean): Boolean =
        !alreadyLookedUp || !releaseDate.isBefore(today.minusDays(LOOKUP_RETRY_DAYS))

    /** Stored lookups are "epochDay:dedupeKey"; entries older than [MAX_AGE_DAYS] (or unreadable) are dropped. */
    fun readLookups(stored: Set<String>, today: LocalDate): Map<String, Long> {
        val oldest = today.minusDays(MAX_AGE_DAYS).toEpochDay()
        return stored.mapNotNull { entry ->
            val day = entry.substringBefore(':', "").toLongOrNull() ?: return@mapNotNull null
            if (day < oldest) null else entry.substringAfter(':') to day
        }.toMap()
    }

    fun writeLookups(lookups: Map<String, Long>): Set<String> = lookups.map { (key, day) -> "$day:$key" }.toSet()

    /** What a pass must change besides inserting new rows: fallback rows a Spotify row replaces. */
    data class YtUpgrade(val deleteIds: List<String>, val upserts: List<ReleaseRadarItem>)

    /**
     * A Spotify row (exact date, real type — row 653) replaces a year-only YouTube-fallback row of the same release:
     * same id, or the same playable album ([ReleaseRadarItem.playId]) under another title spelling. The replacement
     * keeps `seen` (seen in either row) and the earliest `fetchedAt`; the other fallback rows are deleted, so the
     * release is never listed twice. Insert-IGNORE alone could not do this: it keeps the fallback row.
     */
    fun upgradeYtRows(known: Collection<ReleaseRadarItem>, items: List<ReleaseRadarItem>): YtUpgrade {
        val ytRows = known.filter { it.source == SOURCE_YT }
        if (ytRows.isEmpty()) return YtUpgrade(emptyList(), emptyList())
        val byId = known.associateBy { it.id }
        val consumed = mutableSetOf<String>()
        val deletes = mutableListOf<String>()
        val upserts = mutableListOf<ReleaseRadarItem>()
        for (item in items) {
            if (item.source == SOURCE_YT) continue
            val replaced = ytRows.filter { yt ->
                yt.id !in consumed && (yt.id == item.id || (item.playId.isNotBlank() && yt.playId == item.playId))
            }
            if (replaced.isEmpty()) continue
            val merged = replaced + listOfNotNull(byId[item.id]?.takeIf { it.source != SOURCE_YT })
            upserts += item.copy(seen = merged.any { it.seen }, fetchedAt = merged.minOf { it.fetchedAt })
            replaced.forEach { consumed += it.id }
            deletes += replaced.map { it.id }.filter { it != item.id }
        }
        return YtUpgrade(deletes, upserts)
    }

    /** A fallback row for an album the table already holds under another id (a Spotify row's title spelling) would
     *  list the release twice: it is not inserted. */
    fun withoutDuplicateAlbums(known: Collection<ReleaseRadarItem>, items: List<ReleaseRadarItem>): List<ReleaseRadarItem> {
        val heldAlbums = known.mapNotNull { row -> row.playId.takeIf { it.isNotBlank() }?.let { it to row.id } }.toMap()
        return items.filterNot { item ->
            item.source == SOURCE_YT && heldAlbums[item.playId].let { it != null && it != item.id }
        }
    }
}
