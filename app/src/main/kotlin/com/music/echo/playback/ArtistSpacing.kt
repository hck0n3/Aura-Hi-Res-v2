package iad1tya.echo.music.playback

/**
 * Greedy artist spacing for the smart queue, free of Android/media3 types so it is unit-testable on the JVM
 * ([RadioQueueShaping.spacedByArtist] is the media3 face of it).
 *
 * The incoming order is the backbone (relatedness / taste): the next item is taken as is, unless its primary
 * artist is one of the last [WINDOW] artists already placed — then the best-ranked item by a different artist
 * goes first (fallback: the head, so nothing is ever dropped and a one-artist batch keeps its order).
 *
 * [leading] is what came BEFORE this batch in the queue (oldest first, lowercase primary artists). Without it a
 * new batch could open with the artist of the song that is playing right now — YouTube puts the seed's own
 * artist first in a radio, and the spacing only looked inside the batch, so the seam between two batches was
 * the one place a same-artist pair could still happen. Empty [leading] is byte-identical to the old behaviour.
 */
object ArtistSpacing {
    const val WINDOW = 2

    /** [artistOf] returns the lowercase primary artist (null = unknown, always eligible). */
    fun <T> space(items: List<T>, artistOf: (T) -> String?, leading: List<String> = emptyList()): List<T> {
        val seed = leading.mapNotNull { it.trim().lowercase().takeIf { s -> s.isNotEmpty() } }.takeLast(WINDOW)
        if (items.size < 3 && seed.isEmpty()) return items
        if (items.size < 2) return items
        val remaining = ArrayList(items)
        val out = ArrayList<T>(items.size)
        val recent = ArrayDeque<String>()
        seed.forEach { recent.addLast(it) }
        while (remaining.isNotEmpty()) {
            var idx = remaining.indexOfFirst { item ->
                val a = artistOf(item)
                a == null || a !in recent
            }
            if (idx < 0) idx = 0
            val pick = remaining.removeAt(idx)
            out.add(pick)
            artistOf(pick)?.let {
                recent.addLast(it)
                if (recent.size > WINDOW) recent.removeFirst()
            }
        }
        return out
    }
}
