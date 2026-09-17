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

        val query = when {
            // Década: la forma que el buscador premia, en el idioma que él pidió. Con idioma inglés
            // "80s hits" trae los éxitos anglosajones; sin idioma, "éxitos de los 80" ya trae la
            // mezcla que espera quien escribe en español.
            decade != null && language == "en" -> "${decadeLabel(decade)}s hits english"
            decade != null && language == "es" -> "exitos de los ${decadeLabel(decade)} en espanol"
            decade != null -> "exitos de los ${decadeLabel(decade)}"
            // Sin década: su propia petición, sin muletillas. Nunca peor que mandar la frase entera.
            else -> withoutLeadIn
        }

        return Parsed(
            query = query.trim().take(80),
            preferPlaylists = decade != null || moment,
            decade = decade,
            language = language,
        )
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
