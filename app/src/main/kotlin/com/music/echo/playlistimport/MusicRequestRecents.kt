package iad1tya.echo.music.playlistimport

/**
 * Ronda 2, punto 1 del dueño: pedir el mismo prompt dos veces en la misma sesión (p. ej. "reggae" ahora
 * y "reggae" otra vez en 20 minutos) devolvía la MISMA lista — [MusicRequestRanking.pick] es
 * determinístico a propósito (mismo `pool` + mismo `tasteOf` = mismo orden), y el `pool` de
 * [AiPlaylistGenerator.searchFallbackPlaylist] viene de los mismos resultados de búsqueda de YouTube
 * para el mismo `query`. La solución no es romper ese determinismo (sigue siendo correcto DENTRO de una
 * sola llamada) sino recordar, ENTRE llamadas, qué ya se sirvió — para que el pool que llega a `pick`
 * ya no incluya lo repetido.
 *
 * Memoria de sesión (se pierde al reiniciar la app, como [iad1tya.echo.music.playback.VideoCompatibility]
 * y por la misma razón: es un acelerador de variedad, no un dato que deba sobrevivir un reinicio) con
 * TTL corto — pasado ese tiempo un id vuelve a estar disponible, porque "pedir reggae" mañana SÍ debe
 * poder repetir lo de hoy.
 */
object MusicRequestRecents {
    private const val TTL_MS = 45 * 60 * 1000L
    private const val MAX_TRACKED = 300

    private data class Entry(val id: String, val atMs: Long)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    private fun prune(nowMs: Long) {
        while (entries.isNotEmpty() && (nowMs - entries.first().atMs > TTL_MS || entries.size > MAX_TRACKED)) {
            entries.removeFirst()
        }
    }

    @Synchronized
    fun markServed(ids: Collection<String>, nowMs: Long = System.currentTimeMillis()) {
        if (ids.isEmpty()) return
        val already = entries.mapTo(HashSet()) { it.id }
        for (id in ids) {
            if (already.add(id)) entries.addLast(Entry(id, nowMs))
        }
        prune(nowMs)
    }

    @Synchronized
    fun isRecent(id: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        prune(nowMs)
        return entries.any { it.id == id }
    }
}
