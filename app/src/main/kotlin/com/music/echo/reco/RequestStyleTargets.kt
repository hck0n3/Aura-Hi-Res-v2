package iad1tya.echo.music.reco

/**
 * Fila 361 — what a "Pedir música" request asked for (styles, language, faith), by the queue's context id, so
 * the smart queue that continues it keeps EXACTLY that ([iad1tya.echo.music.playback.exactStyleFilter]) instead
 * of guessing it from the first 40 characters of the request used as the queue's title. Small and in memory:
 * the last few requests only.
 */
object RequestStyleTargets {
    private const val MAX = 16
    private val map = object : LinkedHashMap<String, StyleContinuity.Target>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StyleContinuity.Target>?): Boolean =
            size > MAX
    }

    @Synchronized
    fun put(contextId: String, target: StyleContinuity.Target) {
        map[contextId] = target
    }

    @Synchronized
    fun get(contextId: String?): StyleContinuity.Target? = contextId?.let { map[it] }
}
