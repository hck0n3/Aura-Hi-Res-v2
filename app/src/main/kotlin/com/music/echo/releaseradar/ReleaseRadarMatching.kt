package iad1tya.echo.music.releaseradar

import java.time.LocalDate
import kotlin.math.abs

data class ReleaseCandidate(
    val title: String,
    val artist: String,
    val date: LocalDate,
    val source: String,      // "yt" | "spotify"
    val artworkUri: String?,
    val playId: String,      // videoId/browseId for playback
    val followed: Boolean = false, // artist the user FOLLOWS (priority) vs just listens to
    val type: String = ReleaseRadarMatching.TYPE_UNKNOWN, // stored in release_radar.type, see releaseType
)

object ReleaseRadarMatching {
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun dedupeKey(c: ReleaseCandidate): String = dedupeKey(c.artist, c.title)

    /** The same key before a candidate exists (the worker looks a release up in the table by it). */
    fun dedupeKey(artist: String, title: String): String = norm(artist) + "|" + norm(title)

    /** Keep one per key; prefer source "yt" (directly playable). */
    fun dedupe(items: List<ReleaseCandidate>): List<ReleaseCandidate> =
        items.groupBy { dedupeKey(it) }
            .values
            .map { group -> group.firstOrNull { it.source == "yt" } ?: group.first() }

    /**
     * Spotify's HARD cap of one track per artist: keep only the most recent release per artist
     * (a followed release wins ties so the priority artist survives the cap).
     */
    fun onePerArtist(items: List<ReleaseCandidate>): List<ReleaseCandidate> =
        items.groupBy { norm(it.artist) }
            .values
            .map { group ->
                group.maxWithOrNull(
                    compareBy<ReleaseCandidate> { it.date }.thenBy { it.followed },
                )!!
            }

    /** Order followed-artist releases first, then by release date descending, and cap the list. */
    fun rankForRadar(items: List<ReleaseCandidate>, cap: Int): List<ReleaseCandidate> =
        items.sortedWith(
            compareByDescending<ReleaseCandidate> { it.followed }.thenByDescending { it.date },
        ).take(cap)

    /** Values of `release_radar.type`. Rows written before 2026-11-22 hold [TYPE_UNKNOWN] ("Release"). */
    const val TYPE_SINGLE = "single"
    const val TYPE_EP = "ep"
    const val TYPE_ALBUM = "album"
    const val TYPE_COMPILATION = "compilation"
    const val TYPE_UNKNOWN = "Release"

    private val EP_IN_TITLE = Regex("""(?i)(^|[^\w])EP([^\w]|$)""")
    private val SINGLE_IN_TITLE = Regex("""(?i)(^|[^\w])Single([^\w]|$)""")

    /**
     * Owner (2026-11-22): Novedades must say whether a release is a Single, an EP or an album. Spotify's
     * [albumType] wins; Spotify files EPs as "single", so a single with 4-6 tracks or "EP" in its title is an
     * EP. Without a type (YouTube-only path) only the title can tell; otherwise [TYPE_UNKNOWN].
     */
    fun releaseType(albumType: String?, totalTracks: Int, title: String): String {
        val epTitle = EP_IN_TITLE.containsMatchIn(title)
        return when (albumType?.lowercase()) {
            "ep" -> TYPE_EP
            "single" -> if (epTitle || totalTracks in 4..6) TYPE_EP else TYPE_SINGLE
            "album" -> if (epTitle) TYPE_EP else TYPE_ALBUM
            "compilation" -> TYPE_COMPILATION
            else -> when {
                epTitle -> TYPE_EP
                SINGLE_IN_TITLE.containsMatchIn(title) -> TYPE_SINGLE
                else -> TYPE_UNKNOWN
            }
        }
    }

    fun isWithinWindow(date: LocalDate, ref: LocalDate, days: Long): Boolean {
        val diff = abs(date.toEpochDay() - ref.toEpochDay())
        return diff <= days
    }
}
