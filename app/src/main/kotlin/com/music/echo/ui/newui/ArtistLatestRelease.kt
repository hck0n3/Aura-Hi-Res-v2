package iad1tya.echo.music.ui.newui

/**
 * Which release the artist page pins as «Último lanzamiento».
 *
 * Owner report (2026-10-04): *"cuando miro el último lanzamiento tiene una canción que nada que ver con
 * el último lanzamiento real del artista"* — the artist had just released a new album and the pinned
 * card showed an unrelated song. Two defects in the old one-liner (`maxBy { year }.thenBy { title }`):
 *  1. It looked at EVERY album card on the page, including «Aparece en» — releases by OTHER artists the
 *     page only lists because this one guests on them (the screen even appends that shelf itself from
 *     iTunes/feat. searches). A collaboration from this year could win.
 *  2. A year tie — the normal case, an album and its singles all come out the same year — was broken by
 *     the TITLE, alphabetically: the newest release only won if its name happened to sort last.
 *
 * Now: only the artist's OWN release shelves compete; the newest YEAR narrows the field; inside that year
 * the exact iTunes/Apple Music release date decides when it is known, and otherwise YouTube Music's own
 * shelf order (each discography shelf lists its newest release first) decides.
 *
 * Pure (no Android, no network) so the rule is pinned by unit tests.
 */
object ArtistLatestRelease {

    /** Shelf ranks ([appleArtistSectionRank]) that hold the artist's OWN releases, best first. */
    private val OWN_RELEASE_RANKS = listOf(4, 7, 8, 9)

    /** [appleArtistSectionRank]'s "unrecognised title" bucket — only used when no known shelf exists. */
    private const val UNKNOWN_SHELF_RANK = 50

    data class Candidate(
        val title: String,
        val year: Int?,
        /** [appleArtistSectionRank] of the shelf it came from. */
        val shelfRank: Int,
        /** Position inside that shelf (0 = first card). */
        val shelfPosition: Int,
        /** Shelf title is a guest/«Aparece en» shelf — never the artist's own release. */
        val guestShelf: Boolean,
        /** ISO-8601 release date from iTunes, when this title was matched there. */
        val releaseDate: String? = null,
    )

    /**
     * Index into [candidates] of the latest release, or null when nothing qualifies.
     *
     * [itunesNewest] is the newest release date iTunes knows for this artist (matched or not). When it
     * is newer than every date we could match, iTunes knows a newer release whose YouTube title we failed
     * to match — so a dated older release must not win over an undated card at the top of its shelf.
     */
    fun pick(candidates: List<Candidate>, itunesNewest: String? = null): Int? {
        val indexed = candidates.withIndex().filter { !it.value.guestShelf }
        if (indexed.isEmpty()) return null
        val own = indexed.filter { it.value.shelfRank in OWN_RELEASE_RANKS }
        val pool = own.ifEmpty { indexed.filter { it.value.shelfRank == UNKNOWN_SHELF_RANK } }
        if (pool.isEmpty()) return null

        val maxYear = pool.mapNotNull { it.value.year }.maxOrNull()
        val contenders = if (maxYear == null) pool else pool.filter { it.value.year == maxYear }

        val bestDated = contenders.filter { it.value.releaseDate != null }
            .maxByOrNull { it.value.releaseDate!! }
        val shelfOrder = compareBy<IndexedValue<Candidate>>(
            { it.value.shelfPosition },
            { OWN_RELEASE_RANKS.indexOf(it.value.shelfRank).let { r -> if (r < 0) Int.MAX_VALUE else r } },
        )
        val bestByShelf = contenders.minWithOrNull(shelfOrder)

        if (bestDated == null) return bestByShelf?.index
        val iTunesKnowsNewer = itunesNewest != null && itunesNewest > bestDated.value.releaseDate!!
        if (iTunesKnowsNewer) {
            // The newer release is on iTunes but its YouTube title did not match ours — trust YouTube's own
            // "newest first" shelf order for an UNDATED top card over the older dated one.
            val undatedTop = contenders.filter { it.value.releaseDate == null }.minWithOrNull(shelfOrder)
            if (undatedTop != null && undatedTop.value.shelfPosition == 0) return undatedTop.index
        }
        return bestDated.index
    }

    /** iTunes release dates for one artist, split by release type so "X - Single" never dates album "X". */
    data class DateIndex(
        /** reconKey → earliest known date of the album/full-length release with that title. */
        val albums: Map<String, String>,
        /** reconKey → earliest known date of the single/EP with that title. */
        val singles: Map<String, String>,
        /** Newest release date of any kind iTunes knows for the artist. */
        val newest: String?,
    ) {
        /** [fromSinglesShelf]: the YouTube card sits on the «Singles y EP» shelf. */
        fun dateOf(key: String, fromSinglesShelf: Boolean): String? =
            if (fromSinglesShelf) singles[key] ?: albums[key] else albums[key] ?: singles[key]

        companion object {
            /**
             * [hits] are (reconKey, isEpOrSingle, iso date) triples straight from iTunes, across stores.
             * The EARLIEST date per key wins (a store that listed the release late must not make it look
             * newer — same rule as the discography screen's buildReleaseDates).
             */
            fun build(hits: List<Triple<String, Boolean, String?>>): DateIndex {
                val albums = HashMap<String, String>()
                val singles = HashMap<String, String>()
                for ((key, isSingle, date) in hits) {
                    if (key.isBlank() || date.isNullOrBlank()) continue
                    val target = if (isSingle) singles else albums
                    val prev = target[key]
                    if (prev == null || date < prev) target[key] = date
                }
                // From the per-release EARLIEST dates, not the raw hits: a late regional re-listing of an old
                // album must not pose as "a newer release iTunes knows about".
                val newest = (albums.values + singles.values).maxOrNull()
                return DateIndex(albums, singles, newest)
            }
        }
    }
}
