package iad1tya.echo.music.playlistimport

import java.text.Normalizer

/**
 * 🔴 QUE NO IMPROVISE (dueño, 2026-09-17: *"necesito que sea lo más asertivo posible la función de
 * pedir música, que no improvise"*).
 *
 * Buscar listas para una petición de época o momento es lo que mejor responde — es lo que hace la
 * propia app de YouTube Music — pero coger **la primera que salga** es justo la puerta por la que
 * entra la improvisación: el buscador devuelve también listas de gente ("mi mix", "para el carro")
 * que contienen cualquier cosa. Una lista solo vale si **demuestra** que corresponde a lo que él
 * pidió, y lo único que tenemos para comprobarlo antes de reproducir nada es su título.
 *
 * De ahí esta puntuación, que es una regla y no una corazonada:
 *  · **si pidió una década, el título tiene que nombrarla** (cifras o letra). Sin eso, se rechaza —
 *    no se "intenta igual";
 *  · el idioma que pidió suma;
 *  · las palabras de su petición que aparecen en el título suman;
 *  · ser una recopilación ("éxitos", "hits", "lo mejor") suma, porque es la forma que tiene una lista
 *    de una época.
 *
 * Y si ninguna candidata pasa, no se usa ninguna: se baja al peldaño siguiente de la escalera. Mejor
 * canciones sueltas que sí se buscaron que una lista que nadie ha comprobado.
 */
object MusicRequestMatch {

    /** Un título rechazado. Nunca se usa esa lista, aunque sea la única que haya. */
    const val REJECT = -1

    private val COMPILATION_WORDS =
        listOf("exitos", "hits", "lo mejor", "best", "greatest", "clasicos", "classics", "mix", "top")

    private val ENGLISH_MARKERS = listOf("english", "ingles", "anglo", "american", "uk", "british")
    private val SPANISH_MARKERS =
        listOf("espanol", "espana", "latino", "latina", "castellano", "hispano", "iberoamericano")

    /** Palabras que no dicen nada del contenido y no deben puntuar como coincidencia. */
    private val STOP_WORDS = setOf(
        "de", "del", "la", "el", "los", "las", "un", "una", "y", "en", "para", "por", "con", "the",
        "of", "and", "a", "to", "musica", "music", "canciones", "songs", "playlist", "lista",
    )

    /** Las formas en que una década se nombra de verdad en un título. */
    fun decadeTokens(decade: String): List<String> {
        val two = if (decade.length == 4) decade.takeLast(2) else decade
        val four = when {
            decade.length == 4 -> decade
            two.toIntOrNull() != null && two.toInt() >= 20 -> "19$two"
            else -> "20$two"
        }
        val word = when (two) {
            "60" -> listOf("sesenta", "sixties")
            "70" -> listOf("setenta", "seventies")
            "80" -> listOf("ochenta", "eighties")
            "90" -> listOf("noventa", "nineties")
            else -> emptyList()
        }
        // Las cuatro cifras CON ese ("1980s") van aparte: la comprobación es por palabra completa, y
        // "1980" no casa dentro de "1980s" — por ahí se escapaba un título perfectamente válido.
        return listOf("${two}s", "${two}'s", two, four, "${four}s", "${four}'s") + word
    }

    private fun contentWords(query: String): List<String> =
        query.split(' ', '-', ',')
            .map { fold(it) }
            .filter { it.length > 2 && it !in STOP_WORDS && it.toIntOrNull() == null }
            .distinct()

    /**
     * Puntúa el título de una lista contra la petición. [REJECT] = no vale, pase lo que pase.
     *
     * @param groups Ronda 9 (dueño): "bachata cristiana" — con la regla de "al menos una palabra en
     *   común" de más abajo, una lista titulada solo "Bachata" pasaba igual, ignorando "cristiana" por
     *   completo. Cuando la petición matcheó VARIAS familias reconocidas a la vez (género + tema, ver
     *   [MusicRequestMoods.conceptGroupsFor]), el título tiene que demostrar TODAS, no una cualquiera —
     *   la misma exigencia que ya se le aplica a la categoría oficial. Con menos de dos grupos (el caso
     *   normal, una sola familia o ninguna) el comportamiento es exactamente el de antes.
     */
    fun score(title: String, parsed: MusicRequestQuery.Parsed, groups: List<List<String>> = emptyList()): Int {
        val t = fold(title)
        if (t.isBlank()) return REJECT
        var score = 0

        val decade = parsed.decade
        if (decade != null) {
            val tokens = decadeTokens(decade)
            // La comprobación es por palabra completa: "80" dentro de "1980s" vale, pero el "80" de
            // "808 State" no debería colar una lista de techno en una petición de los ochenta.
            val named = tokens.any { token -> containsToken(t, token) }
            if (!named) return REJECT
            score += 3
        }

        // Ronda 9 (dueño): "que respete lo que pido siempre" — el idioma explícito deja de ser un
        // simple empuje de puntaje y pasa a ser una condición dura, igual que la década: si pidió
        // inglés y el título no lo demuestra, se rechaza — no se acepta con menos puntos.
        when (parsed.language) {
            "en" -> if (ENGLISH_MARKERS.any { containsToken(t, it) }) score += 2 else return REJECT
            "es" -> if (SPANISH_MARKERS.any { containsToken(t, it) }) score += 2 else return REJECT
        }

        if (groups.size >= 2) {
            if (!groups.all { group -> group.any { containsToken(t, it) } }) return REJECT
            score += groups.size
        } else {
            val words = contentWords(parsed.query)
            val wordHits = words.count { containsToken(t, it) }
            // Sin década que comprobar, lo que se exige es que el título comparta al menos una palabra
            // de CONTENIDO con la petición. Sin esa regla, "Mi mix" pasaba por el simple hecho de
            // llamarse "mix" — una lista de cualquier cosa colándose en una petición de música para
            // estudiar.
            if (decade == null && wordHits == 0) return REJECT
            score += wordHits.coerceAtMost(3)
        }

        if (COMPILATION_WORDS.any { containsToken(t, it) }) score += 1
        return score
    }

    /**
     * El índice de la mejor candidata, o null si ninguna merece reproducirse. Empate → la primera,
     * que es la que el buscador considera más relevante.
     */
    fun bestIndex(
        titles: List<String>,
        parsed: MusicRequestQuery.Parsed,
        groups: List<List<String>> = emptyList(),
    ): Int? {
        var bestIdx: Int? = null
        var best = Int.MIN_VALUE
        titles.forEachIndexed { index, title ->
            val s = score(title, parsed, groups)
            if (s != REJECT && s > best) {
                best = s
                bestIdx = index
            }
        }
        return bestIdx
    }

    /**
     * Todas las candidatas que PASAN, de mejor a peor. Existe porque ahora se usan las dos mejores en
     * vez de una sola: dos curadores distintos dan más variedad, y las dos han pasado la misma prueba.
     * Las rechazadas no aparecen, pase lo que pase.
     */
    fun rankedIndices(
        titles: List<String>,
        parsed: MusicRequestQuery.Parsed,
        groups: List<List<String>> = emptyList(),
    ): List<Int> =
        titles.indices
            .map { it to score(titles[it], parsed, groups) }
            .filter { it.second != REJECT }
            .sortedByDescending { it.second }
            .map { it.first }

    /** Coincidencia por palabra completa sobre un texto ya normalizado. */
    private fun containsToken(folded: String, token: String): Boolean {
        if (token.isBlank()) return false
        val escaped = Regex.escape(token)
        return Regex("""(?<![\p{L}\p{N}])$escaped(?![\p{L}\p{N}])""").containsMatchIn(folded)
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
