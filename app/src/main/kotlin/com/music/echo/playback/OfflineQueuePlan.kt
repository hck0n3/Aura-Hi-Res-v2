package iad1tya.echo.music.playback

import kotlin.random.Random

/**
 * Owner (2.0.69-beta2): «el modo sin conexión no reemplaza la cola por la cola que está disponible sin conexión». When the
 * player's offline session starts, what comes after the current song is REPLACED by what plays with zero network: first the
 * rest of the queue that does ([OfflinePlayability], the one test), in its order; then, up to [BATCH], the offline library —
 * same album as the current song first, then same artist, then the rest. At the end of that queue more offline songs follow
 * (never the radio: it is network), and with nothing new left the library loops instead of stopping. When the session ends
 * the original upcoming songs that did not play come back, only if the queue is still the one built here.
 *
 * The pure decisions, so they are testable on the JVM. Every playability answer comes in as a lambda, asked lazily (a
 * listen-cache scan is not free), and memoised by the caller.
 */
internal object OfflineQueuePlan {
    /** Songs per batch: the switch fills the queue up to this, and each top-up at the end adds this many. Chosen, not measured. */
    const val BATCH = 20

    class Candidate(val id: String, val albumId: String?, val artistIds: Set<String>)

    /** Same album as [albumId] first, then any of [artistIds], then the rest — shuffled inside each group, each song once. */
    fun contextOrder(candidates: List<Candidate>, albumId: String?, artistIds: Set<String>, random: Random): List<Candidate> {
        val unique = candidates.distinctBy { it.id }
        val (album, others) = unique.partition { albumId != null && it.albumId == albumId }
        val (artist, rest) = others.partition { candidate -> candidate.artistIds.any { it in artistIds } }
        return album.shuffled(random) + artist.shuffled(random) + rest.shuffled(random)
    }

    /**
     * Up to [count] ids from [ordered] that play offline. Never one in [ahead] (the current song and what is queued after it).
     *  1. Songs not heard this session ([heard]) and not anywhere in the queue ([lastIndexInQueue]), in [ordered] order.
     *  2. Not enough: the library loops — songs heard or queued before, the ones that played LONGEST ago first (lowest index in
     *     the queue; a song not in the queue counts as oldest).
     *  3. Only with [lastResort] (the end of the queue) and still nothing: a song in [ahead] repeats rather than the music stopping.
     */
    fun fill(
        ordered: List<Candidate>,
        count: Int,
        ahead: Set<String>,
        lastIndexInQueue: Map<String, Int>,
        heard: (String) -> Boolean,
        playable: (String) -> Boolean,
        lastResort: Boolean,
    ): List<String> {
        if (count <= 0) return emptyList()
        val picked = LinkedHashSet<String>()
        for (candidate in ordered) {
            if (picked.size >= count) break
            val id = candidate.id
            if (id in picked || id in ahead || id in lastIndexInQueue || heard(id)) continue
            if (playable(id)) picked.add(id)
        }
        if (picked.size < count) {
            ordered
                .filter { it.id !in picked && it.id !in ahead }
                .sortedBy { lastIndexInQueue[it.id] ?: -1 }
                .forEach { candidate ->
                    if (picked.size < count && candidate.id !in picked && playable(candidate.id)) picked.add(candidate.id)
                }
        }
        if (picked.isEmpty() && lastResort) {
            ordered.firstOrNull { it.id in ahead && playable(it.id) }?.let { picked.add(it.id) }
        }
        return picked.toList()
    }

    /**
     * The ids that played (or were passed) during the session, read from where the player is now. The queue is the kept
     * [prefixCount] items (the current song at switch time, [anchorIndex], among them) followed by [owned], the songs the switch
     * and its top-ups put there; [beforeCurrent] are the indices that come before the current one in PLAY order (shuffle too).
     * Null when the player is back among the kept items, off the anchor: the user moved, nothing is restored.
     */
    fun playedDuringSession(
        owned: List<String>,
        prefixCount: Int,
        anchorIndex: Int,
        anchorId: String,
        anchorPlayed: Boolean,
        currentIndex: Int,
        beforeCurrent: Set<Int>,
    ): Set<String>? {
        val onAnchor = currentIndex == anchorIndex
        if (!onAnchor && currentIndex < prefixCount) return null
        val played = HashSet<String>()
        if (onAnchor || anchorPlayed) played.add(anchorId)
        for (i in owned.indices) {
            val index = prefixCount + i
            if (index == currentIndex || index in beforeCurrent) played.add(owned[i])
        }
        return played
    }

    /** Indices of [original] (the upcoming songs replaced at switch time, in order) that come back: the ones not [played]. */
    fun toRestore(original: List<String>, played: Set<String>): List<Int> =
        original.indices.filter { original[it] !in played }
}
