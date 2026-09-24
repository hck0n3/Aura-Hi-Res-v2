package iad1tya.echo.music.playlistimport

import java.text.Normalizer

/**
 * 🔴 LO QUE PIDE, TRADUCIDO A LO QUE YOUTUBE MUSIC ENTIENDE (dueño, 2026-09-17: *"le pedí música de
 * los 80s y nunca funcionó […] necesito que pueda entender el lenguaje natural también y que me
 * responda"*).
 *
 * El buscador de YouTube Music no es un asistente: es un buscador. "ponme música de los 80s en
 * inglés" escrito tal cual devuelve poco y malo, porque busca esa FRASE. Lo que sí entiende de
 * maravilla es "80s hits" o "éxitos de los 80" — y para una petición de ÉPOCA o de MOMENTO lo que
 * mejor responde no son canciones sueltas sino una **lista**, que es exactamente lo que YouTube Music
 * te enseña cuando le pides eso mismo en su app.
 *
 * Así que esto hace dos cosas, las dos puras y probadas:
 *  1. limpia la petición (quita "ponme", "quiero", "dame"…) y la convierte en una consulta con la
 *     forma que el buscador premia, entendiendo **década** e **idioma**;
 *  2. dice si conviene buscar LISTAS antes que canciones ([preferPlaylists]).
 *
 * No inventa nada que él no haya dicho: si no reconoce ni década ni momento, la consulta es su propia
 * petición sin las muletillas — nunca peor que antes.
 */
object MusicRequestQuery {

    data class Parsed(
        /** La consulta que se le manda al buscador. */
        val query: String,
        /** true cuando una LISTA responde mejor que canciones sueltas (época, momento, actividad). */
        val preferPlaylists: Boolean,
        /** La década detectada ("80", "90", "2000"), o null. Solo para el log y las pruebas. */
        val decade: String? = null,
        /** "en" (inglés), "es" (español) o null si no lo dijo. */
        val language: String? = null,
        /**
         * Ronda 6 (dueño): pidió "lo que suena ahora"/tendencias. La fuente que corresponde no es
         * buscar ni pedirle a un LLM que adivine qué está de moda — es la sección TRENDING/TOP real
         * de YouTube Music ([AiPlaylistGenerator.trendingSongs]), la misma filosofía de "la categoría
         * ES la prueba" que ya usa [MusicRequestMoods] para época/momento.
         */
        val trending: Boolean = false,
    )

    /** Muletillas de petición: no aportan nada a una búsqueda y sí ensucian la frase. */
    private val LEAD_IN = Regex(
        """(?iu)^\s*(?:por\s+favor\s+)?(?:me\s+)?(?:puedes?\s+)?""" +
            """(?:ponme|pon|poner|reproduce|reproducir|quiero|querria|queria|me\s+gustaria|dame|""" +
            """busca|buscame|necesito|pongamos|echame|play|put\s+on|i\s+want)\s+""" +
            """(?:algo\s+de\s+|un\s+poco\s+de\s+|una\s+lista\s+de\s+|musica\s+de\s+|musica\s+)?""",
    )

    /**
     * "los 80", "80s", "los 80's", "años 90", "the 2000s" → la década.
     *
     * Las dos cifras solas NO bastan, y ese es el detalle que evita un ridículo: "música de 50 Cent"
     * no es una petición de los años 50. Hace falta el artículo delante ("de los 80") o la ese detrás
     * ("80s"), que es como se nombra una década de verdad.
     */
    private val DECADE_DIGITS = Regex("""(?iu)\b(los|las|the|an[oó]s?)?\s*((?:19|20)?\d0)('?s)?\b""")

    private val DECADE_WORDS = mapOf(
        "sesenta" to "60", "sesentas" to "60", "sixties" to "60",
        "setenta" to "70", "setentas" to "70", "seventies" to "70",
        "ochenta" to "80", "ochentas" to "80", "eighties" to "80",
        "noventa" to "90", "noventas" to "90", "nineties" to "90",
    )

    private val ENGLISH_HINTS = listOf("en ingles", "in english", "ingles", "english", "anglo")
    private val SPANISH_HINTS = listOf("en espanol", "in spanish", "espanol", "spanish", "castellano")

    /**
     * Momentos y actividades. No se traducen a inglés: el buscador de YouTube Music responde bien a
     * "música para estudiar" en español, y forzar el inglés le daría resultados de otro mundo a quien
     * escribe en español. Lo único que aportan aquí es que **una lista responde mejor** que canciones.
     */
    private val MOMENT_HINTS = listOf(
        "para estudiar", "para concentrarme", "para dormir", "para relajarme", "para correr",
        "para el gimnasio", "para entrenar", "para trabajar", "para la fiesta", "para manejar",
        "para conducir", "para cocinar", "para leer", "de fondo", "para bailar",
        "to study", "to sleep", "to run", "for the gym", "to focus", "workout",
    )

    /** "lo que suena ahora" / tendencias — ver [Parsed.trending]. */
    private val TREND_HINTS = listOf(
        "lo que suena ahora", "lo mas escuchado", "lo mas sonado", "tendencias", "tendencia",
        "lo mas popular ahora", "top actual", "top del momento", "lo nuevo y popular",
        "trending", "what's popular", "whats popular", "popular right now", "popular now",
    )

    /**
     * Ronda 7 (dueño): un género suelto ("reggaeton", "bossa nova") no activaba [Parsed.preferPlaylists],
     * así que nunca pasaba por las categorías oficiales de YouTube Music ([MusicRequestMoods]) — caía
     * directo a una búsqueda de texto libre sin ninguna verificación de género. Mismas palabras que
     * disparan [MusicRequestMoods]'s tabla de géneros (mantenidas en sync a mano, mismo espíritu que
     * [MOMENT_HINTS] duplica los disparadores de [MusicRequestMoods.FAMILIES] hoy).
     */
    private val GENRE_HINTS = listOf(
        "reggaeton", "reggaetón", "perreo", "dembow", "salsa", "bachata", "merengue", "rock", "pop",
        "bossa nova", "bossanova", "bosanova", "jazz", "cumbia", "vallenato", "banda", "mariachi",
        "ranchera", "corridos", "corrido", "trap", "hip hop", "hiphop", "electronica", "electrónica",
        "edm", "house", "techno", "metal", "punk", "k-pop", "kpop", "r&b", "rnb", "flamenco",
        "country", "blues", "indie", "funk", "soul", "reggae", "disco", "tango", "bolero", "gospel",
    )

    /**
     * Palabras que sobran del residuo tras quitar década/idioma: no dicen nada de género/tema. Mismo
     * espíritu que MusicRequestMatch.STOP_WORDS (palabras de relleno que no cuentan como contenido),
     * pero corta aparte porque este filtro corre ANTES de tener un [Parsed] con el que llamar a esa
     * clase.
     */
    private val CONNECTOR_WORDS = setOf(
        "de", "del", "en", "los", "las", "el", "la", "lo", "the", "of", "and", "y", "a", "to", "an",
        "un", "una", "para", "por", "con",
        "años", "anos", "año", "ano",
        "musica", "canciones", "songs", "playlist", "lista",
        // Palabras de recopilación (mismo espíritu que MusicRequestMatch.COMPILATION_WORDS): "éxitos
        // de los 2000" no debe leer "exitos" como si fuera el género que pidió.
        "exitos", "hits", "mejor", "best", "greatest", "clasicos", "classics", "mix", "top",
    )

    fun build(prompt: String): Parsed {
        val raw = prompt.trim()
        if (raw.isBlank()) return Parsed(query = "", preferPlaylists = false)

        val folded = fold(raw)
        val withoutLeadIn = LEAD_IN.replace(fold(raw), "").trim().ifBlank { folded }

        val decade = detectDecade(withoutLeadIn)
        val language = when {
            ENGLISH_HINTS.any { withoutLeadIn.contains(it) } -> "en"
            SPANISH_HINTS.any { withoutLeadIn.contains(it) } -> "es"
            else -> null
        }
        val moment = MOMENT_HINTS.any { withoutLeadIn.contains(it) }
        val trending = TREND_HINTS.any { withoutLeadIn.contains(it) }
        // Ronda 7: un género suelto también prefiere listas — ver GENRE_HINTS arriba.
        val genreHint = GENRE_HINTS.any { withoutLeadIn.contains(it) }

        // Ronda 6 (dueño): "reggae de los 90" perdía "reggae" por completo — la petición se
        // reemplazaba entera por la plantilla fija "exitos de los 90". Lo que queda de la frase tras
        // quitar la década y las pistas de idioma es el género/tema que sí pidió; si no queda nada
        // (petición de década pura, "música de los 80"), el comportamiento es exactamente el de antes.
        val genre = if (decade != null) residualGenre(withoutLeadIn, decade) else null

        val query = when {
            // Década + género: se conserva lo que pidió, con el sufijo que el buscador premia.
            decade != null && !genre.isNullOrBlank() && language == "en" ->
                "$genre ${decadeLabel(decade)}s hits english"
            decade != null && !genre.isNullOrBlank() && language == "es" ->
                "$genre exitos de los ${decadeLabel(decade)} en espanol"
            decade != null && !genre.isNullOrBlank() ->
                "$genre exitos de los ${decadeLabel(decade)}"
            // Década sola: la forma que el buscador premia, en el idioma que él pidió. Con idioma
            // inglés "80s hits" trae los éxitos anglosajones; sin idioma, "éxitos de los 80" ya trae
            // la mezcla que espera quien escribe en español.
            decade != null && language == "en" -> "${decadeLabel(decade)}s hits english"
            decade != null && language == "es" -> "exitos de los ${decadeLabel(decade)} en espanol"
            decade != null -> "exitos de los ${decadeLabel(decade)}"
            // Ronda 9 (dueño): "trap cristiano en inglés" mandaba la frase TAL CUAL, con "en ingles"
            // como texto literal — el buscador no lo lee como un filtro de idioma, así que no sesgaba
            // el resultado en absoluto (ya se resolvía para década sola / década+género; sin década
            // se quedaba sin arreglar). Mismo tratamiento: se quita la pista cruda y se añade el
            // sufijo que el buscador sí entiende.
            language == "en" -> "${stripLanguageHint(withoutLeadIn)} english".trim()
            language == "es" -> "${stripLanguageHint(withoutLeadIn)} en espanol".trim()
            // Sin década ni idioma: su propia petición, sin muletillas. Nunca peor que mandar la
            // frase entera.
            else -> withoutLeadIn
        }

        return Parsed(
            query = query.trim().take(80),
            preferPlaylists = decade != null || moment || trending || genreHint,
            decade = decade,
            language = language,
            trending = trending,
        )
    }

    /**
     * Lo que sobra de [text] tras quitar la mención de [decade] (cifras o palabra) y las pistas de
     * idioma — el género o tema que pidió junto con la década, o cadena vacía si no pidió nada más.
     */
    private fun residualGenre(text: String, decade: String): String {
        var stripped = text
        // Probar TODAS las palabras que nombran esta década (no solo la primera del mapa): "seventies
        // rock" no contiene "setenta", así que quedarse con una sola entrada dejaba "seventies" sin
        // quitar y el residuo entero se tomaba por género. Por PALABRA COMPLETA, no subcadena: "ochenta"
        // es un prefijo de "ochentas" y un replace ingenuo dejaba una "s" suelta como residuo falso.
        DECADE_WORDS.entries.filter { it.value == decade }.forEach { (word, _) ->
            stripped = stripped.replace(Regex("""\b${Regex.escape(word)}\b"""), " ")
        }
        stripped = DECADE_DIGITS.replace(stripped) { m ->
            if (m.groupValues[2] == decade) " " else m.value
        }
        stripped = stripLanguageHint(stripped)
        return stripped.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in CONNECTOR_WORDS }
            .joinToString(" ")
            .trim()
    }

    /** Quita la pista de idioma cruda ("en ingles", "in spanish"…) — no aporta nada a una búsqueda literal. */
    private fun stripLanguageHint(text: String): String {
        var stripped = text
        (ENGLISH_HINTS + SPANISH_HINTS).forEach { hint ->
            stripped = stripped.replace(Regex("""\b${Regex.escape(hint)}\b"""), " ")
        }
        return stripped.replace(Regex("\\s+"), " ").trim()
    }

    /** "80" → "80"; "1980"/"2000" → "80"/"2000" tal y como se buscan de verdad. */
    private fun decadeLabel(decade: String): String = when {
        decade.length == 4 && decade.startsWith("19") -> decade.removePrefix("19")
        else -> decade
    }

    private fun detectDecade(folded: String): String? {
        DECADE_WORDS.entries.firstOrNull { folded.contains(it.key) }?.let { return it.value }
        for (m in DECADE_DIGITS.findAll(folded)) {
            val article = m.groupValues[1].isNotBlank()
            val value = m.groupValues[2]
            val plural = m.groupValues[3].isNotBlank()
            val named = article || plural || value.length == 4
            if (!named) continue
            // Un número suelto que no es una década (el "10" de "top 10") no cuenta ni nombrado.
            val ok = when {
                value.length == 2 -> value != "00" && value != "10"
                value.length == 4 -> value.startsWith("19") || value == "2000" || value == "2010"
                else -> false
            }
            if (ok) return value
        }
        return null
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
