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

    /** How long the library read once is reused (row 717: a re-entry or a top-up never reads it again). Chosen, not measured. */
    const val LIBRARY_CACHE_MS = 10 * 60_000L

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

    // ---- the way back (row 717) ----------------------------------------------------------------------------

    /** What each queue item is to the switch: left in place (history, the current song), kept from the queue, or library fill. */
    enum class Role { PREFIX, KEPT, FILL }

    class Entry(val id: String, val role: Role)

    /** One player edit, applied in order: media3's removeMediaItem / moveMediaItem (index after the move) / addMediaItem. */
    sealed interface Op {
        data class Remove(val index: Int) : Op
        data class Move(val from: Int, val to: Int) : Op
        data class Insert(val index: Int, val originalIndex: Int) : Op
    }

    class Restore(val ops: List<Op>, val entries: List<Entry>, val restored: Int, val removed: Int)

    private class Slot(val id: String, val role: Role, var placed: Boolean = false)

    /**
     * How the queue goes back when the network returns. [entries] is the queue exactly as the switch left it (the caller only
     * plans when the live queue still matches), [heard] what played or was passed during the session (never a song the offline
     * skip jumped away from because it could not play), [original] the upcoming songs the switch replaced, in order.
     *  1. The library fill nobody heard goes, always — with nothing to restore too (C2); with repeat-all every fill but the
     *     current song goes, or the loop would keep it.
     *  2. The original songs not heard come back after the current one, in their order. One still in the queue is never added
     *     twice: kept in place, moved forward from behind the current song (skipped by the error path), or left where it is
     *     when it already plays later (a wrapped repeat-all, the user went back). In linear play what sits before the current
     *     song counts as passed; shuffle reads only [heard], and order is left to the shuffle.
     */
    fun restorePlan(
        entries: List<Entry>,
        currentIndex: Int,
        heard: Set<String>,
        original: List<String>,
        shuffle: Boolean,
        repeatAll: Boolean,
    ): Restore {
        val list = entries.map { Slot(it.id, it.role) }.toMutableList()
        var c = currentIndex
        val currentId = list[c].id
        val gone = HashSet(heard)
        gone.add(currentId)
        if (!shuffle) for (i in 0 until c) if (list[i].role != Role.PREFIX) gone.add(list[i].id)
        val ops = ArrayList<Op>()
        var removed = 0
        for (i in list.indices.reversed()) {
            val slot = list[i]
            if (i != c && slot.role == Role.FILL && (repeatAll || slot.id !in gone)) {
                ops += Op.Remove(i)
                list.removeAt(i)
                removed++
                if (i < c) c--
            }
        }
        val wanted = original.indices.filter { original[it] !in gone }
        // Free copies per id (not the current song, never fill): a song with none is inserted without any scan.
        val available = HashMap<String, Int>()
        for (i in list.indices) if (i != c && list[i].role != Role.FILL) available[list[i].id] = (available[list[i].id] ?: 0) + 1
        fun free(i: Int, id: String) = !list[i].placed && list[i].id == id && list[i].role != Role.FILL && i != c
        fun take(slot: Slot) {
            slot.placed = true
            available[slot.id] = (available[slot.id] ?: 1) - 1
        }
        if (shuffle) {
            for (k in wanted) {
                val j = if ((available[original[k]] ?: 0) > 0) list.indices.firstOrNull { free(it, original[k]) } else null
                if (j != null) {
                    take(list[j])
                } else {
                    ops += Op.Insert(list.size, k)
                    list.add(Slot(original[k], Role.KEPT, placed = true))
                }
            }
            return Restore(ops, list.map { Entry(it.id, it.role) }, wanted.size, removed)
        }
        val ownedStart = list.indices.firstOrNull { list[it].role != Role.PREFIX } ?: list.size
        val lastHeardAhead = (c + 1 until list.size).lastOrNull { list[it].role != Role.PREFIX && list[it].id in gone } ?: -1
        var cursor = maxOf(c + 1, ownedStart, lastHeardAhead + 1)
        for (k in wanted) {
            val id = original[k]
            if ((available[id] ?: 0) <= 0) {
                ops += Op.Insert(cursor, k)
                list.add(cursor, Slot(id, Role.KEPT, placed = true))
                cursor++
                continue
            }
            if (cursor < list.size && free(cursor, id)) {
                take(list[cursor])
                cursor++
                continue
            }
            val laterInPlace = (c + 1 until minOf(cursor, list.size)).firstOrNull { free(it, id) }
            if (laterInPlace != null) {
                take(list[laterInPlace])
                continue
            }
            val ahead = (cursor + 1 until list.size).firstOrNull { free(it, id) }
            if (ahead != null) {
                ops += Op.Move(ahead, cursor)
                val slot = list.removeAt(ahead)
                list.add(cursor, slot)
                take(slot)
                cursor++
                continue
            }
            val behind = (0 until c).lastOrNull { free(it, id) }
            if (behind != null) {
                ops += Op.Move(behind, cursor - 1)
                val slot = list.removeAt(behind)
                list.add(cursor - 1, slot)
                take(slot)
                c--
                continue
            }
            ops += Op.Insert(cursor, k)
            list.add(cursor, Slot(id, Role.KEPT, placed = true))
            cursor++
        }
        return Restore(ops, list.map { Entry(it.id, it.role) }, wanted.size, removed)
    }

    /**
     * C4: when the session only timed out, the queue stays offline and ONE network song is resolved in the background as the
     * probe — never played, so a failure cuts nothing. It is the first original song that needs the network and was not
     * heard; null when there is none.
     */
    fun probeTarget(original: List<String>, needsNetwork: Set<Int>, heard: Set<String>, currentId: String?): Int? =
        original.indices.firstOrNull { it in needsNetwork && original[it] !in heard && original[it] != currentId }
}
