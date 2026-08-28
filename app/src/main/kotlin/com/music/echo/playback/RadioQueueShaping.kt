package iad1tya.echo.music.playback

import androidx.media3.common.MediaItem
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.reco.TasteProfile

/**
 * HALLAZGO-021 split, phase A — the two stateless post-sort passes that shape the radio queue order,
 * extracted verbatim from MusicService.orderedByTaste (which stays in the service: it reads the taste
 * profile, dislike store, listening history and genre cache). Nothing here touches service state, the
 * database or the network — both passes are in-memory, order- and length-preserving.
 */
object RadioQueueShaping {

    /** Greedy artist-spacing: keep the incoming (taste/relatedness) order as the base, but when the next item
     *  repeats a primary artist placed in the last 2 slots, skip ahead to the best-ranked item by a different
     *  artist (fallback: take the head). Preserves the backbone, kills same-artist streaks. */
    fun spacedByArtist(items: List<MediaItem>): List<MediaItem> {
        if (items.size < 3) return items
        val remaining = ArrayList(items)
        val out = ArrayList<MediaItem>(items.size)
        val recent = ArrayDeque<String>()
        while (remaining.isNotEmpty()) {
            var idx = remaining.indexOfFirst { mi ->
                val a = mi.metadata?.artists?.firstOrNull()?.name?.lowercase()
                a == null || a !in recent
            }
            if (idx < 0) idx = 0
            val pick = remaining.removeAt(idx)
            out.add(pick)
            pick.metadata?.artists?.firstOrNull()?.name?.lowercase()?.let {
                recent.addLast(it); if (recent.size > 2) recent.removeFirst()
            }
        }
        return out
    }

    /**
     * Phase B #4 — exploration quota. Reserve roughly every 15th slot for a "fresh" candidate: one whose primary
     * artist is NOT already in the taste profile ([iad1tya.echo.music.reco.TasteProfile.isKnownArtist]), so radio
     * doesn't tunnel into pure exploitation — but far less often than the old 1-in-5 / 1-in-10 cadences that made
     * context-faithful radio feel random. Never drops or duplicates anything —
     * output length == input length, and each partition keeps its incoming (taste/relatedness) order. Null profile
     * (no taste yet), lists under 8, or no fresh/known split → returns the list unchanged (today's behaviour).
     * In-memory only, no network, no extra cost.
     *
     * [blocked] are ids that may NOT claim a reserved exploration slot: a CTX_SINK id, or a candidate the
     * genre steer pushed back for a genre we KNOW and know to be off-context
     * ([iad1tya.echo.music.reco.ContextProfile.blocksExploration]). Without that rule the quota undid the
     * steer — the reserved slots went to precisely the songs it had just demoted (the owner's "una
     * del género, otra que no tiene nada que ver").
     *
     * An UNKNOWN genre is deliberately NOT a reason to block, and that boundary is load-bearing: "not in
     * your taste profile" and "we have no idea what genre this is" describe the SAME candidate almost
     * every time, so blocking on absence would empty this fresh partition on a cold or partial GenreCache
     * and turn the discovery reserve off exactly when discovery is happening (registry #39/#41).
     *
     * A blocked candidate is only moved OUT of the fresh partition: it keeps its sorted position, is
     * never dropped, and if every fresh candidate is blocked the interleave no-ops (order unchanged).
     * Empty [blocked] (the steer is off) → byte-identical to before.
     */
    fun withExplorationQuota(
        items: List<MediaItem>,
        p: TasteProfile?,
        blocked: Set<String> = emptySet(),
    ): List<MediaItem> {
        if (p == null || items.size < 8) return items
        val known = ArrayList<MediaItem>(items.size)
        val fresh = ArrayList<MediaItem>()
        for (mi in items) {
            val artist = mi.metadata?.artists?.firstOrNull()?.name
            val isFresh = artist != null && !p.isKnownArtist(artist) &&
                (blocked.isEmpty() || mi.mediaId !in blocked)
            if (isFresh) fresh.add(mi) else known.add(mi)
        }
        // Nothing to interleave (all known or all fresh) → preserve the existing order exactly.
        if (fresh.isEmpty() || known.isEmpty()) return items
        val out = ArrayList<MediaItem>(items.size)
        val ki = known.iterator()
        val fi = fresh.iterator()
        var pos = 0
        while (ki.hasNext() || fi.hasNext()) {
            val takeFresh = pos % 15 == 14 && fi.hasNext()
            out.add(if (takeFresh) fi.next() else if (ki.hasNext()) ki.next() else fi.next())
            pos++
        }
        return out
    }
}
