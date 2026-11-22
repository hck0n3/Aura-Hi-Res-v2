package iad1tya.echo.music.playlistimport

/**
 * Row 649 — the ONE artist a «Pedir música» request named («Luis Miguel»), by the queue's context id, so the smart queue
 * that continues it stays on that artist (the same name + channel checks as the request itself:
 * [AiPlaylistGenerator.soloPrimaryMatch] and [AiPlaylistGenerator.soloIdMatch]) instead of drifting to similar artists.
 * In memory, the last few requests only (same shape as [iad1tya.echo.music.reco.RequestStyleTargets]).
 */
object RequestArtistTargets {
    data class Lock(val name: String, val id: String?)

    private const val MAX = 16
    private val map = object : LinkedHashMap<String, Lock>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lock>?): Boolean = size > MAX
    }

    @Synchronized
    fun put(contextId: String, lock: Lock) {
        map[contextId] = lock
    }

    @Synchronized
    fun get(contextId: String?): Lock? = contextId?.let { map[it] }

    /** True when a song credited to [credits] (name to channel id, in order) is [lock]'s artist; no lock = anything. */
    fun admits(lock: Lock?, credits: List<Pair<String, String?>>): Boolean {
        if (lock == null) return true
        return AiPlaylistGenerator.soloPrimaryMatch(credits.map { it.first }, lock.name) &&
            AiPlaylistGenerator.soloIdMatch(credits, lock.name, lock.id)
    }
}
