package iad1tya.echo.music.reco

import android.content.Context

/**
 * Fila 354 — qué estilo toca cada artista, APRENDIDO de los títulos y álbumes que la app ve pasar (las listas
 * que pones, cada tanda de la cola y lo que trae la búsqueda de un estilo).
 *
 * Por qué: iTunes no distingue la cumbia de la salsa ("Salsa y Tropical", "Latin", "Christian & Gospel"
 * cubren a los dos), y muchos títulos no dicen su estilo. Pero el mismo artista sí lo dice en OTROS títulos o
 * en el nombre de sus álbumes ("Cumbias de adoración Vol. 2"): con eso, la siguiente vez que aparece una
 * canción suya sin pista, ya se sabe.
 *
 * Decide solo con evidencia clara: al menos [MIN_EVIDENCE] observaciones y un estilo con [MIN_SHARE] de ellas
 * (un artista que toca cumbia y salsa por igual queda sin estilo, y entonces nada lo descarta por él).
 * Local, acotado a [MAX_ARTISTS] artistas, sin red. Nada de esto va al registro (AGENTS regla 4).
 */
object ArtistStyleMemory {
    private const val PREFS = "artist_style_memory"
    const val MIN_EVIDENCE = 2
    const val MIN_SHARE = 0.7
    const val MAX_ARTISTS = 4000

    /** artist key (lowercased, trimmed) → style id → count. */
    typealias Memory = Map<String, Map<String, Int>>

    fun key(artist: String?): String? = artist?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /** The artist's style when the evidence is clear, else null. Pure. */
    fun styleOf(memory: Memory, artist: String?): String? {
        val counts = memory[key(artist) ?: return null] ?: return null
        val total = counts.values.sum()
        if (total < MIN_EVIDENCE) return null
        val (style, n) = counts.maxByOrNull { it.value } ?: return null
        return if (n.toDouble() / total >= MIN_SHARE) style else null
    }

    /** [memory] plus [observations] (artist → style). Pure; new artists beyond [maxArtists] are not added. */
    fun merged(memory: Memory, observations: List<Pair<String, String>>, maxArtists: Int = MAX_ARTISTS): Memory {
        if (observations.isEmpty()) return memory
        val out = HashMap<String, MutableMap<String, Int>>(memory.size + observations.size)
        memory.forEach { (k, v) -> out[k] = v.toMutableMap() }
        for ((artist, style) in observations) {
            val k = key(artist) ?: continue
            val counts = out[k] ?: if (out.size < maxArtists) HashMap<String, Int>().also { out[k] = it } else continue
            counts[style] = (counts[style] ?: 0) + 1
        }
        return out
    }

    // "cumbia:3,salsa:1"
    internal fun encode(counts: Map<String, Int>): String = counts.entries.joinToString(",") { "${it.key}:${it.value}" }

    internal fun decode(value: String): Map<String, Int> = value.split(',').mapNotNull { part ->
        val i = part.lastIndexOf(':')
        if (i <= 0) return@mapNotNull null
        val n = part.substring(i + 1).toIntOrNull() ?: return@mapNotNull null
        part.substring(0, i) to n
    }.toMap()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** One disk read; call off the main thread, once per batch. */
    fun snapshot(context: Context): Memory =
        prefs(context).all.mapNotNull { (k, v) -> (v as? String)?.let { k to decode(it) } }.toMap()

    /** Adds [observations] (artist → style) and persists only the artists that changed. Off the main thread. */
    @Synchronized
    fun learn(context: Context, observations: List<Pair<String, String>>) {
        if (observations.isEmpty()) return
        val before = snapshot(context)
        val after = merged(before, observations)
        val changed = observations.mapNotNull { key(it.first) }.toSet()
        val edit = prefs(context).edit()
        var writes = 0
        for (k in changed) {
            val counts = after[k] ?: continue
            if (counts != before[k]) {
                edit.putString(k, encode(counts))
                writes++
            }
        }
        if (writes > 0) edit.apply()
    }
}
