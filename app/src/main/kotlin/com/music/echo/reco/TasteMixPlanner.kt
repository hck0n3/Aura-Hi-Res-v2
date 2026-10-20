package iad1tya.echo.music.reco

import iad1tya.echo.music.playback.NoRepeatKey
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * The brain of the Inicio «Aleatorio» tile («Mezcla de tu gusto»). Pure Kotlin, no Android types, so the rules are
 * unit-tested on the JVM ([TasteMixPlannerTest]).
 *
 * # Why it exists (owner 2026-10-20)
 * *«lo vuelvo a tocar y repite el contenido… no sé cómo estará funcionando bien el gusto mío ahí… no me gusta que salga
 * nada repetido»*. The tile used to pick ONE random song out of the ~20 «Para ti» picks (80 %) or the YouTube shelves
 * (20 %) and start that song's YouTube radio: a tiny, almost fixed pool, uniform luck instead of taste, and no memory at
 * all, so two taps fished in the same pond and two radios of similar seeds overlapped heavily.
 *
 * # What one tap builds now
 *  1. **Pool** = the user's own library (+ their most played songs), minus «No me gusta», minus hidden videos.
 *  2. **Memory**: nothing heard (or served) by a previous tap is eligible again — by id AND by [NoRepeatKey], so the
 *     «Official Video» of a song already heard counts as heard. [memory] maps song id → when it was last heard/served.
 *  3. **Taste**: ~80 % of the slots are drawn by weighted sampling WITHOUT replacement; the weight grows with the
 *     [TasteProfile] affinity (followed artists, play history, genre, likes), with the song being liked and with how
 *     much of it was listened to. A weight is a probability, not a rank, so two taps with the same taste still differ.
 *  4. **Exploration**: the remaining ~20 % are songs of the library the user never really listened to (and did not
 *     like), drawn uniformly, so the mix is not a closed loop on the same ten artists.
 *  5. **Order**: exploration lands on every fifth slot (the opener is always a taste pick), then an artist-spacing pass
 *     keeps the same artist at least [ARTIST_GAP] songs apart while the pool allows it.
 *  6. **Exhaustion**: when fewer than a full queue of un-heard songs is left, [Plan.cycleReset] is true: every un-heard
 *     song is taken and the rest is filled with the LEAST recently heard ones, and the caller wipes the memory so the
 *     next lap starts clean. Never an empty queue, never a silent repeat of what was heard five minutes ago.
 */
object TasteMixPlanner {

    const val DEFAULT_SIZE = 50
    const val EXPLORATION_SHARE = 0.2
    const val ARTIST_GAP = 3

    /** A song listened to for less than this is «never really heard» — a candidate for the exploration share. */
    internal const val UNFAMILIAR_MS = 30_000L

    private const val EXPLORE_EVERY = 5
    private const val AFFINITY_SLOPE = 1.5
    private const val LIKED_BOOST = 1.6
    private const val PLAYTIME_BOOST = 0.35

    /** One song of the pool, reduced to what the plan needs. */
    data class Candidate(
        val id: String,
        val title: String,
        /** Primary artist's display name (identity + spacing). */
        val artist: String?,
        /** [TasteProfile.score]; at or below `TasteProfile.AVOID / 2` the song is dropped. */
        val affinity: Double,
        val liked: Boolean,
        /** `SongEntity.totalPlayTime`, in milliseconds. */
        val playTimeMs: Long,
    )

    /**
     * @property picks the queue, in play order.
     * @property freshPool how many distinct songs were still un-heard before this plan (0 after an exhaustion).
     * @property cycleReset the un-heard pool could not fill a queue: the caller must wipe the memory.
     * @property exploration how many of [picks] are exploration slots (for tests and diagnostics).
     */
    data class Plan(
        val picks: List<Candidate>,
        val freshPool: Int,
        val cycleReset: Boolean,
        val exploration: Int,
    )

    fun plan(
        candidates: List<Candidate>,
        memory: Map<String, Long>,
        size: Int = DEFAULT_SIZE,
        explorationShare: Double = EXPLORATION_SHARE,
        random: Random = Random.Default,
    ): Plan {
        if (size <= 0) return Plan(emptyList(), 0, false, 0)

        // 1) Usable songs, one per id.
        val usable = candidates
            .filter { it.id.isNotBlank() && it.affinity > TasteProfile.AVOID / 2 }
            .distinctBy { it.id }

        // 2) One representative per SONG (not per version): the liked / most listened / best fitting version wins.
        val keyOf = HashMap<String, String>(usable.size)
        usable.forEach { keyOf[it.id] = NoRepeatKey.of(it.title, it.artist) }
        // What was heard is a SONG: any version of it blocks every other version.
        val keyMemory = HashMap<String, Long>()
        usable.forEach { c ->
            val at = memory[c.id] ?: return@forEach
            keyMemory.merge(keyOf.getValue(c.id), at) { a, b -> maxOf(a, b) }
        }
        val representatives = usable
            .groupBy { keyOf.getValue(it.id) }
            .values
            .map { versions ->
                versions.sortedWith(
                    compareByDescending<Candidate> { it.liked }
                        .thenByDescending { it.playTimeMs }
                        .thenByDescending { it.affinity }
                        .thenBy { it.id },
                ).first()
            }

        fun heardAt(c: Candidate): Long? = memory[c.id] ?: keyMemory[keyOf.getValue(c.id)]

        val fresh = representatives.filter { heardAt(it) == null }
        val target = minOf(size, representatives.size)
        if (target == 0) return Plan(emptyList(), 0, false, 0)

        // 3) Choose WHICH songs.
        val cycleReset = fresh.size < target
        val chosenTaste: List<Candidate>
        val chosenExplore: List<Candidate>
        if (cycleReset) {
            // Exhausted: every un-heard song, topped up with the LEAST recently heard ones.
            val oldestFirst = representatives
                .filter { heardAt(it) != null }
                .sortedWith(compareBy<Candidate> { heardAt(it) ?: Long.MAX_VALUE }.thenBy { it.id })
                .take(target - fresh.size)
            val all = fresh + oldestFirst
            // Exploration slots are about WHAT is drawn; here everything is drawn, so only the ORDER (below) matters.
            val explorers = all.filter { it.isUnfamiliar() }
            val exploreN = minOf(exploreSlots(target, explorationShare), explorers.size)
            chosenExplore = explorers.shuffled(random).take(exploreN)
            chosenTaste = orderByWeight(all - chosenExplore.toSet(), random)
        } else {
            var exploreN = exploreSlots(target, explorationShare)
            val taste = weightedSample(fresh, target - exploreN, random)
            val tasteIds = taste.mapTo(HashSet()) { it.id }
            val explorers = fresh.filter { it.id !in tasteIds && it.isUnfamiliar() }
            val explore = explorers.shuffled(random).take(exploreN)
            // Not enough unfamiliar songs: the missing slots go back to the taste share, never to a smaller queue.
            exploreN = explore.size
            val topUp = if (taste.size + exploreN < target) {
                val used = tasteIds + explore.map { it.id }
                weightedSample(fresh.filter { it.id !in used }, target - taste.size - exploreN, random)
            } else {
                emptyList()
            }
            chosenTaste = taste + topUp
            chosenExplore = explore
        }

        // 4) Order: exploration every fifth slot, opener always taste, then artist spacing.
        val merged = ArrayList<Candidate>(target)
        val tasteIt = chosenTaste.iterator()
        val exploreIt = chosenExplore.iterator()
        while (tasteIt.hasNext() || exploreIt.hasNext()) {
            val exploreSlot = (merged.size + 1) % EXPLORE_EVERY == 0
            val next = when {
                exploreSlot && exploreIt.hasNext() -> exploreIt.next()
                tasteIt.hasNext() -> tasteIt.next()
                else -> exploreIt.next()
            }
            merged += next
        }
        val spaced = spaceArtists(merged)
        return Plan(
            picks = spaced,
            freshPool = if (cycleReset) 0 else fresh.size,
            cycleReset = cycleReset,
            exploration = chosenExplore.size,
        )
    }

    private fun exploreSlots(target: Int, share: Double): Int =
        if (target < EXPLORE_EVERY) 0 else (target * share.coerceIn(0.0, 0.5)).toInt()

    private fun Candidate.isUnfamiliar(): Boolean = !liked && playTimeMs < UNFAMILIAR_MS

    /** Relative chance of a song being drawn. Always > 0, so even an unknown song has some chance. */
    internal fun weight(c: Candidate): Double {
        val affinity = c.affinity.coerceIn(-1.0, 2.0)
        val minutes = (c.playTimeMs.coerceAtLeast(0L) / 60_000.0)
        val listened = 1.0 + ln(1.0 + minutes) * PLAYTIME_BOOST
        return exp(AFFINITY_SLOPE * affinity) * listened * (if (c.liked) LIKED_BOOST else 1.0)
    }

    /**
     * Efraimidis–Spirakis weighted sampling without replacement: each song gets `ln(u) / w` (u uniform in (0, 1]); the
     * [n] largest keys are the draw, already in «most likely first» order.
     */
    internal fun weightedSample(pool: List<Candidate>, n: Int, random: Random): List<Candidate> {
        if (n <= 0 || pool.isEmpty()) return emptyList()
        return pool
            .map { c -> c to ln(1.0 - random.nextDouble()) / weight(c) }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }

    private fun orderByWeight(pool: List<Candidate>, random: Random): List<Candidate> =
        weightedSample(pool, pool.size, random)

    /**
     * Greedy pass: at every slot take the first remaining song whose artist is not among the last [gap] placed; if none,
     * relax the gap until one fits. Songs without an artist are unconstrained. Order otherwise preserved.
     */
    internal fun spaceArtists(ordered: List<Candidate>, gap: Int = ARTIST_GAP): List<Candidate> {
        val remaining = ArrayList(ordered)
        val out = ArrayList<Candidate>(ordered.size)
        fun artistKey(c: Candidate) = NoRepeatKey.normalizeArtist(c.artist)
        while (remaining.isNotEmpty()) {
            var g = gap
            var index = -1
            while (index < 0) {
                val recent = out.takeLast(g).map { artistKey(it) }.filter { it.isNotEmpty() }.toSet()
                index = remaining.indexOfFirst { artistKey(it).let { k -> k.isEmpty() || k !in recent } }
                if (index < 0) {
                    if (g == 0) index = 0 else g--
                }
            }
            out += remaining.removeAt(index)
        }
        return out
    }
}

/**
 * What the tile has already served in this process: the part of the no-repeat memory that the playback service cannot
 * know, namely songs that were PUT in a mix but not (yet) heard. Together with the persistent «ya reproducidas»
 * memory of [iad1tya.echo.music.playback.ShuffleContexts.TASTE_MIX] (what was really heard, survives restarts) it is
 * what makes a second tap fresh. Bounded, in memory only, nothing is ever logged from here.
 */
object TasteMixSession {
    private const val MAX_IDS = 800
    private val served = LinkedHashMap<String, Long>()

    @Synchronized
    fun snapshot(): Map<String, Long> = HashMap(served)

    @Synchronized
    fun remember(ids: Collection<String>, now: Long) {
        ids.forEach { id ->
            served.remove(id) // re-insert at the end: eviction is oldest-first
            served[id] = now
        }
        while (served.size > MAX_IDS) served.remove(served.keys.first())
    }

    @Synchronized
    fun clear() = served.clear()
}
