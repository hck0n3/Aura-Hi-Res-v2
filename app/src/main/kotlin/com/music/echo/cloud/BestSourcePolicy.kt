package iad1tya.echo.music.cloud

import iad1tya.echo.music.playback.NoRepeatKey
import kotlin.math.abs

/**
 * Registry row 894 — one library for the three sources: when the same song is on the phone, in the cloud and/or online,
 * the best copy plays first. Pure (tested on the JVM).
 *
 * SAME SONG: the same normalised title and at least one normalised artist in common (the no-repeat key of the smart
 * queue, [NoRepeatKey]: packaging such as «Remastered» or «(Official Video)» ignored, live/acoustic/remix kept apart) and
 * durations at most [DURATION_TOLERANCE_SECONDS] apart, both known.
 *
 * BEST (plan, phase 5: «local sin pérdida > nube > en línea»): a lossless copy on the phone, then a lossless copy in the
 * cloud, then a compressed copy on the phone, then a compressed one in the cloud, then online. Between two copies of the
 * same quality the phone wins: it needs no network.
 */
object BestSourcePolicy {
    enum class Source { PHONE, CLOUD, ONLINE }

    data class Candidate(
        val id: String,
        val title: String,
        val artists: List<String>,
        val durationSeconds: Int,
        val lossless: Boolean,
    ) {
        val source: Source get() = sourceOf(id)
    }

    const val DURATION_TOLERANCE_SECONDS = 2

    fun sourceOf(id: String): Source = when {
        id.isCloudSongId() -> Source.CLOUD
        PHONE_SCHEMES.any { id.startsWith(it) } -> Source.PHONE
        else -> Source.ONLINE
    }

    fun rank(c: Candidate): Int = when (c.source) {
        Source.PHONE -> if (c.lossless) 0 else 2
        Source.CLOUD -> if (c.lossless) 1 else 3
        Source.ONLINE -> 4
    }

    fun sameSong(a: Candidate, b: Candidate): Boolean {
        if (a.durationSeconds <= 0 || b.durationSeconds <= 0) return false
        if (abs(a.durationSeconds - b.durationSeconds) > DURATION_TOLERANCE_SECONDS) return false
        val title = NoRepeatKey.normalizeTitle(a.title)
        if (title.isEmpty() || title != NoRepeatKey.normalizeTitle(b.title)) return false
        val artists = a.artists.map(NoRepeatKey::normalizeArtist).filter(String::isNotEmpty).toSet()
        return b.artists.any { NoRepeatKey.normalizeArtist(it) in artists }
    }

    /**
     * id -> the id that plays in its place, only for a copy that is NOT the best of its song (a best copy, or a song
     * with a single copy, is absent). Ties keep the first one listed.
     */
    fun bestTwins(candidates: List<Candidate>): Map<String, String> {
        val byTitle = candidates.groupBy { NoRepeatKey.normalizeTitle(it.title) }
        val twins = HashMap<String, String>()
        byTitle.values.filter { it.size > 1 }.forEach { group ->
            val unassigned = group.toMutableList()
            while (unassigned.isNotEmpty()) {
                val first = unassigned.removeAt(0)
                val same = listOf(first) + unassigned.filter { sameSong(first, it) }
                unassigned.removeAll(same.toSet())
                if (same.size < 2) continue
                val best = same.minWith(compareBy(::rank))
                same.filter { it.id != best.id }.forEach { twins[it.id] = best.id }
            }
        }
        return twins
    }

    private val PHONE_SCHEMES = listOf("content://", "file://", "android.resource://")
}
