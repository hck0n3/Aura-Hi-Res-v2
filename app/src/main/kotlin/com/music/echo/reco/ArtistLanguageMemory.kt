package iad1tya.echo.music.reco

import android.content.Context

/**
 * Row 459 — in which LANGUAGE each artist sings, LEARNED from the titles and album names the app sees pass
 * (the lists the user plays, every batch of the smart queue, what the style search brings), exactly like
 * [ArtistStyleMemory] does for the style.
 *
 * Why: most song titles do not say their language («Testimonio», «Jordan», «Lo Que Viene»), but the same artist
 * says it in OTHER titles. Once an artist is known to sing in Spanish, every later song of theirs counts as
 * Spanish even when its title is a single name — and a context seeded by that artist knows its language even
 * when the song that started it was a one-word title. That is how a Spanish Christian-rap context stops
 * continuing with English hip hop: the English side is recognised by ITS artists, not only by its titles.
 *
 * Decides only with clear evidence ([MIN_EVIDENCE] observations, one language holding [MIN_SHARE] of them): a
 * bilingual artist stays language-less and nothing is rejected because of them. Local, bounded to
 * [MAX_ARTISTS] artists, no network, nothing of it reaches the log (AGENTS rule 4). The pure maths are shared
 * with [ArtistStyleMemory] ([ArtistStyleMemory.merged] / `encode` / `decode`), the file is its own.
 */
object ArtistLanguageMemory {
    private const val PREFS = "artist_language_memory"
    const val MIN_EVIDENCE = 2
    const val MIN_SHARE = 0.7
    const val MAX_ARTISTS = 4000

    /** artist key (lowercased, trimmed) → language code → count. */
    typealias Memory = Map<String, Map<String, Int>>

    /** The artist's language when the evidence is clear, else null. Pure. */
    fun languageOf(memory: Memory, artist: String?): String? {
        val counts = memory[ArtistStyleMemory.key(artist) ?: return null] ?: return null
        val total = counts.values.sum()
        if (total < MIN_EVIDENCE) return null
        val (language, n) = counts.maxByOrNull { it.value } ?: return null
        return if (n.toDouble() / total >= MIN_SHARE) language else null
    }

    /** [memory] plus [observations] (artist → language). Pure; new artists beyond [maxArtists] are not added. */
    fun merged(memory: Memory, observations: List<Pair<String, String>>, maxArtists: Int = MAX_ARTISTS): Memory =
        ArtistStyleMemory.merged(memory, observations, maxArtists)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** One disk read; call off the main thread, once per batch. */
    fun snapshot(context: Context): Memory =
        prefs(context).all.mapNotNull { (k, v) -> (v as? String)?.let { k to ArtistStyleMemory.decode(it) } }.toMap()

    /** Adds [observations] (artist → language) and persists only the artists that changed. Off the main thread. */
    @Synchronized
    fun learn(context: Context, observations: List<Pair<String, String>>) {
        if (observations.isEmpty()) return
        val before = snapshot(context)
        val after = merged(before, observations)
        val changed = observations.mapNotNull { ArtistStyleMemory.key(it.first) }.toSet()
        val edit = prefs(context).edit()
        var writes = 0
        for (k in changed) {
            val counts = after[k] ?: continue
            if (counts != before[k]) {
                edit.putString(k, ArtistStyleMemory.encode(counts))
                writes++
            }
        }
        if (writes > 0) edit.apply()
    }
}
