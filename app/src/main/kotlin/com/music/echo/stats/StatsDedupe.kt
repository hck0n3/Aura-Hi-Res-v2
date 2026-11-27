package iad1tya.echo.music.stats

import iad1tya.echo.music.cloud.BestSourcePolicy
import iad1tya.echo.music.playback.NoRepeatKey

/**
 * Registry row 909 — one song, artist or album counts ONCE in Estadísticas, whichever of the three sources (phone, cloud,
 * online; rows 891-894) it was heard from. Pure (tested on the JVM).
 *
 * The SAME song is [BestSourcePolicy.sameSong] (row 894: normalised title, an artist in common, durations within 2 s) on
 * ANOTHER source: two copies on the same source (the explicit and the clean version online, two files on the phone) stay
 * two songs, exactly as the artist page keeps them. The same artist is the same normalised name ([NoRepeatKey]: the local
 * `LOCAL_ARTIST_…` row and the YouTube `UC…` row are different ids for one act). The same album is the same normalised
 * title by the same normalised first artist; an album with no known artist is never merged by title alone.
 */
object StatsDedupe {
    data class SongKey(
        val id: String,
        val title: String,
        val artists: List<String>,
        val durationSeconds: Int,
    )

    /**
     * id -> the id of the group it belongs to (the FIRST of its copies in [songs], so pass them most listened first).
     * Every id is in the map; a song with a single copy maps to itself.
     *
     * [BestSourcePolicy.sameSong] needs equal normalised titles, so a song is only compared with the groups that share
     * its title, each title normalised once: linear in the number of songs, not quadratic (thousands on «Todo»).
     */
    fun songGroups(songs: List<SongKey>): Map<String, String> {
        val clustersByTitle = HashMap<String, MutableList<MutableList<BestSourcePolicy.Candidate>>>()
        val groupOf = LinkedHashMap<String, String>()
        for (song in songs) {
            if (song.id in groupOf) continue
            val candidate = BestSourcePolicy.Candidate(song.id, song.title, song.artists, song.durationSeconds, lossless = false)
            val clusters = clustersByTitle.getOrPut(NoRepeatKey.normalizeTitle(song.title)) { ArrayList() }
            val home = clusters.firstOrNull { cluster ->
                BestSourcePolicy.sameSong(cluster.first(), candidate) && cluster.none { it.source == candidate.source }
            }
            if (home != null) {
                home += candidate
                groupOf[song.id] = home.first().id
            } else {
                clusters += mutableListOf(candidate)
                groupOf[song.id] = song.id
            }
        }
        return groupOf
    }

    /** The key two artist rows share when they are one act; the id when the name says nothing. */
    fun artistKey(id: String, name: String?): String = NoRepeatKey.normalizeArtist(name).ifEmpty { "id:$id" }

    /** The key two album rows share when they are one album; the id when either the title or the artist is unknown. */
    fun albumKey(id: String, title: String?, firstArtist: String?): String {
        val t = NoRepeatKey.normalizeTitle(title.orEmpty())
        val a = NoRepeatKey.normalizeArtist(firstArtist)
        return if (t.isEmpty() || a.isEmpty()) "id:$id" else "$t|$a"
    }

    /**
     * Folds the rows that share a key into one, keeping the order by [weight] (heaviest first, ties in their original
     * order). The surviving row is the first one [preferred] accepts, else the first of the group; [merge] adds the
     * others' counts into it.
     */
    fun <T> mergeGroups(
        items: List<T>,
        keyOf: (T) -> String,
        weight: (T) -> Long,
        preferred: (T) -> Boolean = { false },
        merge: (into: T, other: T) -> T,
    ): List<T> {
        val groups = LinkedHashMap<String, MutableList<T>>()
        for (item in items) groups.getOrPut(keyOf(item)) { mutableListOf() } += item
        return groups.values
            .map { group ->
                val keep = group.firstOrNull(preferred) ?: group.first()
                group.filter { it !== keep }.fold(keep, merge)
            }
            .sortedByDescending(weight)
    }
}
