package iad1tya.echo.music.playlistimport

/**
 * 🔴 REUNIR MUCHAS Y ELEGIR, EN VEZ DE COGER LAS DIEZ PRIMERAS (dueño, 2026-09-17).
 *
 * Antes las canciones se aceptaban por orden hasta llenar el cupo: la primera lista que pasara el
 * filtro decidía el resultado entero. Ahora se junta un montón de candidatas — una sola página de
 * lista ya trae cincuenta y pico, así que **no cuesta ni una llamada más** — y se ELIGE.
 *
 * ## Cómo se elige, y por qué así
 * La receta es la de la radio, que lleva meses funcionando: **el orden de origen manda** (la curación
 * de la lista editorial, o la relevancia del buscador) y el gusto solo **empuja** unos puestos. Al
 * revés — ordenar por gusto — se destruye justo lo que hace buena a una lista de los 80: que alguien
 * la ordenó. De ahí que el empujón esté topado: un artista favorito adelanta unos puestos, nunca
 * reescribe la lista.
 *
 * Tres reglas más, en orden de importancia:
 *  · lo marcado con "No me gusta" se cae ([avoidScore]), pero **nunca hasta dejarlo vacío**: si todo
 *    lo que hay está marcado, se devuelve lo que había — mejor eso que "no encontré nada";
 *  · no se permiten dos canciones seguidas del mismo artista, que es lo que convierte una lista de
 *    diez en "tres de este y tres de aquel";
 *  · sin perfil de gusto (instalación nueva) el empujón es cero y el resultado es **exactamente** el
 *    orden de origen: la personalización no puede empeorar el caso en que no hay nada que saber.
 */
object MusicRequestRanking {

    /** Cuánto pesa el gusto frente al orden de origen. Mismo espíritu que el `pull` de la radio. */
    const val TASTE_WEIGHT = 5.5

    /** Tope del empujón, en puestos. Un favorito adelanta; no reescribe la lista. */
    const val PULL_CAP = 8.0

    /**
     * @param candidates el montón, YA en su orden de origen (curación o relevancia).
     * @param artistOf el artista principal, para la separación. null o vacío = desconocido, y dos
     *   desconocidos seguidos sí se permiten (no se puede afirmar que sean el mismo).
     * @param tasteOf afinidad del usuario, 0.0 cuando no hay perfil; [avoidScore] = marcado.
     */
    fun <T> pick(
        candidates: List<T>,
        target: Int,
        artistOf: (T) -> String?,
        tasteOf: (T) -> Double,
        avoidScore: Double,
    ): List<T> {
        if (candidates.isEmpty() || target <= 0) return emptyList()
        val kept = candidates.filter { tasteOf(it) > avoidScore / 2 }
        // Todo marcado → se devuelve lo que había. Nunca un resultado vacío por filtrar de más.
        val pool = kept.ifEmpty { candidates }

        val ordered = pool
            .mapIndexed { index, item ->
                val pull = (tasteOf(item) * TASTE_WEIGHT).coerceIn(-PULL_CAP, PULL_CAP)
                item to (index.toDouble() - pull)
            }
            .sortedBy { it.second }
            .map { it.first }

        return spacedByArtist(ordered, artistOf).take(target)
    }

    /**
     * Separación por artista: se mantiene el orden que entra, pero si la siguiente repite el artista
     * de la anterior se adelanta la mejor colocada que no lo haga. Lo que no se puede separar queda al
     * final, en su orden — nunca se pierde nada.
     */
    fun <T> spacedByArtist(items: List<T>, artistOf: (T) -> String?): List<T> {
        if (items.size < 3) return items
        val remaining = ArrayList(items)
        val out = ArrayList<T>(items.size)
        var last: String? = null
        while (remaining.isNotEmpty()) {
            var idx = remaining.indexOfFirst {
                val a = artistOf(it)?.trim()?.lowercase()
                a.isNullOrBlank() || a != last
            }
            if (idx < 0) idx = 0
            val pick = remaining.removeAt(idx)
            out += pick
            last = artistOf(pick)?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        }
        return out
    }
}
