package iad1tya.echo.music.reco

/**
 * THE GENRE PATTERN of a finished collection, and the order that follows it.
 *
 * Owner (2026-10-04): *"cuando la lista termina — estoy escuchando cumbia, por ejemplo — viene una canción
 * que nada que ver y luego viene cumbia. Quiero que el algoritmo sea exacto: si la playlist tiene varios
 * géneros (cumbia, reggaetón, pop…) la cola que sigue repite ese mismo patrón; si la playlist es de un
 * solo género, la cola sigue con ese género. No quiero que continúe con la última canción que escuchó:
 * quiero que la cola, antes de empezar, estudie la playlist para saber con qué seguir."*
 *
 * [ContextProfile] already knew WHAT the collection was (its genre shares), but only as a bounded sort
 * NUDGE on YouTube's relatedness order — so the head of each batch was still whatever YouTube related
 * most to the seed, and an unknown-genre pick at relatedness rank 0 could open the continuation. This
 * object turns the shares into the batch ORDER itself:
 *
 *  - [sequence] — a deterministic lane sequence whose every prefix tracks the shares (smooth weighted
 *    round-robin): {cumbia 0.5, reggaetón 0.3, pop 0.2} → C R C P C R C … ; a one-genre list → C C C …
 *  - [arrange] — fills that sequence from the batch, best-ranked first within each lane, so slot 0 is
 *    always a song of the collection's own dominant genre (or, failing that, by one of its own artists),
 *    never a song we know nothing about.
 *
 * REGISTRY #39/#41/#116 — a cache-derived genre must never become a FILTER: nothing here removes
 * anything. Unknown-genre candidates keep their relative order and play right after the patterned part
 * of the batch; known off-context ones (already sunk by the caller) stay last. Output length == input
 * length, always.
 *
 * Pure (no Android, no player, no network) and unit-tested in ContextPatternTest.
 */
object ContextPattern {

    /**
     * A genre below this share is noise for the PATTERN (one mislabeled track in a cumbia list must not
     * schedule a pop song every few slots). Its candidates are still kept — they go to the unpatterned
     * tail with the unknowns — and when NO lane reaches it (a very fragmented list) every lane counts.
     */
    const val MIN_PATTERN_SHARE = 0.10

    /** How many recent picks the artist-spacing looks back over (same window as RadioQueueShaping). */
    private const val ARTIST_WINDOW = 2

    /** The lanes that shape the pattern, renormalized to sum 1.0, largest first (ties by name). */
    fun patternShares(shares: Map<String, Double>): List<Pair<String, Double>> {
        val positive = shares.filter { it.value > 0.0 }
        if (positive.isEmpty()) return emptyList()
        val strong = positive.filter { it.value >= MIN_PATTERN_SHARE }.ifEmpty { positive }
        val total = strong.values.sum()
        return strong.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { it.key to it.value / total }
    }

    /**
     * [length] lanes following [shares] (see [patternShares]) by smooth weighted round-robin: at every
     * step each lane earns its share, the lane with the most credit plays and pays 1. Every prefix stays
     * within one slot of the exact proportion, and lanes interleave instead of coming in blocks.
     */
    fun sequence(shares: Map<String, Double>, length: Int): List<String> {
        val lanes = patternShares(shares)
        if (length <= 0 || lanes.isEmpty()) return emptyList()
        val credit = DoubleArray(lanes.size)
        val out = ArrayList<String>(length)
        repeat(length) {
            var best = 0
            for (i in lanes.indices) {
                credit[i] += lanes[i].second
                if (credit[i] > credit[best] + 1e-9) best = i
            }
            credit[best] -= 1.0
            out.add(lanes[best].first)
        }
        return out
    }

    /**
     * Re-order [ranked] (already best-first: relatedness + taste + steer, off-context sunk to the end)
     * so the batch follows the collection's genre pattern.
     *
     * Three tiers, in this order:
     *  1. SCHEDULED — candidates whose lane is a pattern lane, plus candidates by the collection's OWN
     *     artists ([isContextArtist], a cache-free signal) whose lane we don't know or that iTunes files
     *     elsewhere. Emitted slot by slot following [sequence]; an empty lane yields its slot to the
     *     context artists first, then to the next lane that still has songs.
     *  2. UNPATTERNED — everything else (unknown genre, a noise lane), in [ranked] order.
     *  3. the caller's sunk off-context candidates are part of tier 2 and, being last in [ranked],
     *     stay last.
     * Within every tier the same primary artist is kept out of the last [ARTIST_WINDOW] slots when an
     * alternative exists. With no usable shares the input order is returned unchanged.
     */
    fun <T> arrange(
        ranked: List<T>,
        shares: Map<String, Double>,
        laneOf: (T) -> String?,
        isContextArtist: (T) -> Boolean,
        artistOf: (T) -> String?,
    ): List<T> {
        val lanes = patternShares(shares)
        if (ranked.size < 2 || lanes.isEmpty()) return ranked
        val laneIds = lanes.mapTo(HashSet()) { it.first }

        val buckets = LinkedHashMap<String, MutableList<T>>()
        lanes.forEach { buckets[it.first] = ArrayList() }
        val contextFill = ArrayList<T>()
        val unpatterned = ArrayList<T>()
        for (item in ranked) {
            val lane = laneOf(item)
            when {
                lane != null && lane in laneIds -> buckets.getValue(lane).add(item)
                isContextArtist(item) -> contextFill.add(item)
                else -> unpatterned.add(item)
            }
        }
        val scheduledCount = buckets.values.sumOf { it.size } + contextFill.size
        if (scheduledCount == 0) return ranked

        val recent = ArrayDeque<String>()
        val out = ArrayList<T>(ranked.size)
        fun take(from: MutableList<T>): T {
            var idx = from.indexOfFirst { val a = artistOf(it); a == null || a !in recent }
            if (idx < 0) idx = 0
            val pick = from.removeAt(idx)
            artistOf(pick)?.let {
                recent.addLast(it)
                if (recent.size > ARTIST_WINDOW) recent.removeFirst()
            }
            return pick
        }

        // The sequence is generated lazily (same smooth round-robin as [sequence]) so it can run exactly
        // as long as the scheduled tier lasts.
        val credit = DoubleArray(lanes.size)
        while (out.size < scheduledCount) {
            var best = 0
            for (i in lanes.indices) {
                credit[i] += lanes[i].second
                if (credit[i] > credit[best] + 1e-9) best = i
            }
            credit[best] -= 1.0
            val wanted = buckets.getValue(lanes[best].first)
            val source = when {
                wanted.isNotEmpty() -> wanted
                contextFill.isNotEmpty() -> contextFill
                else -> lanes.firstNotNullOfOrNull { (lane, _) -> buckets.getValue(lane).takeIf { it.isNotEmpty() } }
            } ?: break
            out.add(take(source))
        }
        while (unpatterned.isNotEmpty()) out.add(take(unpatterned))
        return out
    }

    /**
     * The lane each candidate INHERITS from the pattern seed whose radio page brought it (owner log
     * 2026-10-05 11:55: a continuation seeded at the true end of the list — no time to look genres up —
     * scored 52 candidates with 42 of UNKNOWN genre, so only 8 could be placed in the pattern and the
     * other 42 played in YouTube's raw order). A song YouTube relates to a cumbia track of the list is,
     * far more often than not, cumbia: that is a better guess than "nothing", and it costs no lookup.
     *
     * Walks [pages] in the SAME round-robin order the caller merges them in (position 0 of every page,
     * then position 1…), so a candidate several seeds share inherits from the seed that put it in the
     * batch. A page whose seed has no lane ([seedLanes] entry null or missing) gives nothing.
     *
     * ORDER ONLY — the caller must use it exclusively as [arrange]'s fallback for a candidate whose own
     * genre is unknown: a known genre always wins, and nothing is ever dropped on an inherited lane
     * (registry #39/#41/#116 — a guess may place a song, never exclude one).
     */
    fun inheritedLanes(seedLanes: List<String?>, pages: List<List<String>>): Map<String, String> {
        val out = HashMap<String, String>()
        // Claimed by whichever page reached it first, exactly like the merge — a lane-less page that got
        // there first keeps the candidate lane-less instead of letting a later page relabel it.
        val claimed = HashSet<String>()
        val maxSize = pages.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxSize) {
            pages.forEachIndexed { p, page ->
                if (i >= page.size) return@forEachIndexed
                val id = page[i]
                if (!claimed.add(id)) return@forEachIndexed
                seedLanes.getOrNull(p)?.let { out[id] = it }
            }
        }
        return out
    }

    /**
     * How many of [n] radio SEEDS each lane gets, in seed order: the same smooth round-robin, so a
     * one-genre list seeds only that genre and a mixed list seeds its genres in proportion (the
     * dominant one first). Empty when the shares are unusable.
     */
    fun seedLanes(shares: Map<String, Double>, n: Int): List<String> = sequence(shares, n)

    /**
     * Which artists to STUDY (look up in iTunes) when a collection starts: its primary artists, most
     * frequent first (ties keep their first appearance), at most [limit]. The genre profile is a share of
     * TRACKS, so the artists with the most tracks decide it — a 5000-song list must spend its bounded
     * lookups on them, not on whoever happens to come first. Names keep their original spelling.
     */
    fun studyArtists(primaryArtists: List<String>, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val counts = LinkedHashMap<String, Int>()
        val spelling = HashMap<String, String>()
        primaryArtists.forEach { raw ->
            val name = raw.trim()
            if (name.isEmpty()) return@forEach
            val key = name.lowercase()
            counts[key] = (counts[key] ?: 0) + 1
            spelling.putIfAbsent(key, name)
        }
        return counts.entries
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<Map.Entry<String, Int>>> { it.value.value }.thenBy { it.index })
            .take(limit)
            .map { spelling.getValue(it.value.key) }
    }
}
