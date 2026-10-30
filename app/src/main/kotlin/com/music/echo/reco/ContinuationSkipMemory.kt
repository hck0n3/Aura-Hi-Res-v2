package iad1tya.echo.music.reco

import android.content.Context

/**
 * Row 525 (owner 2026-10-29: «después de la salsa me puso rap… y ahorita otra vez puso Tercer Cielo»). The smart queue learned
 * from quick skips only for the rest of THAT queue ([ExactStyleState.skippedArtists]): the next queue started from zero, so an
 * artist the user had skipped twice yesterday came back today. This keeps the same signal for 30 days.
 *
 * Only the songs the smart queue ADDED count (a song the user chose himself — a list, a search — is never held against its
 * artist), and only a quick skip does: an artist is rejected for the CONTINUATION after [SKIPS_TO_REJECT] of them inside
 * [WINDOW_MS]. A song of that artist heard to its end forgives it. The artist is never removed from anything the user plays on
 * purpose, and the context's own artists are never rejected by this. Artist keys only (lower-cased names), in the app's private
 * preferences; nothing here reaches the log except a count.
 */
object ContinuationSkipMemory {
    private const val PREFS = "continuation_skip_memory"
    const val SKIPS_TO_REJECT = 2
    const val WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_ARTISTS = 400

    /** key → "count,lastSkipMs". Pure: the artists those [entries] reject at [now]. */
    fun rejectedFrom(entries: Map<String, String>, now: Long): Set<String> =
        entries.mapNotNull { (key, value) ->
            val (count, last) = parse(value) ?: return@mapNotNull null
            if (count >= SKIPS_TO_REJECT && now - last < WINDOW_MS) key else null
        }.toSet()

    /** Pure: [entries] after one more quick skip of [key] at [now] (a skip after the window starts the count again). */
    fun withSkip(entries: Map<String, String>, key: String, now: Long): Map<String, String> {
        val previous = entries[key]?.let(::parse)
        val count = if (previous != null && now - previous.second < WINDOW_MS) previous.first + 1 else 1
        val next = HashMap(entries)
        next[key] = "$count,$now"
        if (next.size > MAX_ARTISTS) {
            // Drop the oldest entries first.
            next.entries.sortedBy { parse(it.value)?.second ?: 0L }.take(next.size - MAX_ARTISTS).forEach { next.remove(it.key) }
        }
        return next
    }

    fun rejectedArtists(context: Context, now: Long = System.currentTimeMillis()): Set<String> =
        rejectedFrom(read(context), now)

    fun recordSkip(context: Context, artistKey: String?, now: Long = System.currentTimeMillis()) {
        if (artistKey.isNullOrBlank()) return
        write(context, withSkip(read(context), artistKey, now))
    }

    fun forgive(context: Context, artistKey: String?) {
        if (artistKey.isNullOrBlank()) return
        val entries = read(context)
        if (artistKey in entries) write(context, entries - artistKey)
    }

    private fun parse(value: String): Pair<Int, Long>? {
        val i = value.indexOf(',')
        if (i <= 0) return null
        val count = value.substring(0, i).toIntOrNull() ?: return null
        val last = value.substring(i + 1).toLongOrNull() ?: return null
        return count to last
    }

    private fun read(context: Context): Map<String, String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    private fun write(context: Context, entries: Map<String, String>) {
        val editor = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        entries.forEach { (k, v) -> editor.putString(k, v) }
        editor.apply()
    }
}
