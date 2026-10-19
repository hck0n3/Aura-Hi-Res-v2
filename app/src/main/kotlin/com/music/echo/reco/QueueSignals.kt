package iad1tya.echo.music.reco

import java.text.Normalizer

/**
 * Queue-scoped memory of artists the listener skipped within seconds, for the smart queue's ordering.
 *
 * Why: the long-term taste model ([AffinityEngine]) already counts a skip as a small negative, but one skip moves a
 * weight that is normalised against a favourite artist, so inside the SAME queue an artist skipped twice in a row
 * came straight back in the next batch. And the exact-style gate's own quick-skip memory only covers songs it
 * let through (`gatedIds`), so a queue the gate does not judge forgot every skip. Streaming services treat the early
 * skip as the strongest cheap implicit signal (Spotify published a 130-million-session skip dataset for it; Deezer
 * says Flow learns from played / skipped / favourited tracks).
 *
 * Rules (deliberately modest — it only ever reorders, it never removes or adds a song):
 *  · [SKIPS_TO_DEMOTE] quick skips of the same primary artist in one queue demote that artist's candidates by
 *    [DEMOTION] ranks (one skip is noise: the user may be hunting for a track);
 *  · hearing a song of that artist to the end ([forgive]) clears it;
 *  · a new queue starts clean ([clear]); bounded at [CAP] artists.
 */
class SessionSkipMemory {
    private val counts = HashMap<String, Int>()

    fun record(artist: String?) {
        val k = key(artist) ?: return
        synchronized(counts) {
            if (counts.size >= CAP && k !in counts) return
            counts.merge(k, 1, Int::plus)
        }
    }

    fun forgive(artist: String?) {
        val k = key(artist) ?: return
        synchronized(counts) { counts.remove(k) }
    }

    fun clear() = synchronized(counts) { counts.clear() }

    /** Artists currently demoted (for the counts-only log). */
    fun demotedCount(): Int = synchronized(counts) { counts.values.count { it >= SKIPS_TO_DEMOTE } }

    /** [DEMOTION] when any of [artists] was skipped quickly [SKIPS_TO_DEMOTE]+ times in this queue, else 0. */
    fun penalty(artists: List<String>): Double = synchronized(counts) {
        if (counts.isEmpty()) return 0.0
        if (artists.any { (counts[key(it)] ?: 0) >= SKIPS_TO_DEMOTE }) DEMOTION else 0.0
    }

    companion object {
        const val SKIPS_TO_DEMOTE = 2
        const val DEMOTION = 8.0
        const val CAP = 200

        fun key(artist: String?): String? = artist?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Titles that are a processed re-upload of a song rather than the song: karaoke and backing tracks, instrumentals,
 * sped-up / slowed(+reverb) / nightcore / 8D / bass-boosted edits. YouTube's radio mixes them in with the real
 * thing. The smart queue demotes them by [DEMOTION] ranks (never drops them) UNLESS what is playing right now
 * is one itself — then the listener is in that mood and they rank as any other song.
 *
 * Deliberately NOT here: live, acoustic, remix, cover, remaster — real recordings a listener often wants.
 */
object VersionNoise {
    const val DEMOTION = 12.0

    private val combiningMarks = Regex("""\p{M}+""")

    private val markers = listOf(
        "karaoke", "instrumental", "backing track", "pista instrumental", "pista para cantar", "sin voz",
        "sped up", "speed up", "speedup", "slowed", "nightcore", "8d audio", "8d", "bass boosted",
    ).map { Regex("""(?<![\p{L}\p{N}])${Regex.escape(it)}(?![\p{L}\p{N}])""") }

    fun isDerivative(vararg texts: String?): Boolean {
        for (raw in texts) {
            if (raw.isNullOrBlank()) continue
            val t = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD).replace(combiningMarks, "")
            if (markers.any { it.containsMatchIn(t) }) return true
        }
        return false
    }
}
