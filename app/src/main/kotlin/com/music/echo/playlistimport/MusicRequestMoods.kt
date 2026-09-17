package iad1tya.echo.music.playlistimport

import java.text.Normalizer

/**
 * 🔴 EL CATÁLOGO PROPIO DE YOUTUBE MUSIC (dueño, 2026-09-17: *"haz lo mejor de esa recomendación"*).
 *
 * YouTube Music mantiene su propia taxonomía de **estados de ánimo y géneros** — "Años 80",
 * "Concentración", "Fiesta", "Entrenamiento"… — y cada categoría entrega **listas editoriales suyas**.
 * Es la fuente más curada que existe para lo que él pide, y cambia la naturaleza de la garantía:
 * con búsqueda de texto libre hay que **verificar** por título que la lista corresponde
 * ([MusicRequestMatch]); con una categoría, **la categoría ES la prueba**. Si su petición cae en
 * "Años 80", lo que sale es de los 80 por construcción.
 *
 * Lo que hace falta resolver aquí, y es lo único que no es obvio: sus palabras **no coinciden** con
 * los nombres de las categorías. "música para estudiar" no contiene "concentración", y sin embargo es
 * esa. De ahí la tabla de conceptos: un puñado de sinónimos por familia, en español y en inglés,
 * porque el catálogo llega en el idioma de su cuenta.
 *
 * Deliberadamente CORTA y conservadora: si su petición no cae claramente en una familia, no se elige
 * categoría y la escalera sigue por donde iba. Inventar una correspondencia floja sería exactamente la
 * improvisación que él prohibió — mejor no usar el catálogo que usarlo mal.
 */
object MusicRequestMoods {

    /**
     * Familias de momento/actividad: lo que él puede escribir → lo que la categoría puede llamarse.
     *
     * Las claves se buscan como SUBCADENA de su petición (ya normalizada), así que "para estudiar" y
     * "quiero estudiar" caen las dos. Los valores se comparan contra el título de la categoría.
     */
    private val FAMILIES: List<Pair<List<String>, List<String>>> = listOf(
        listOf("estudiar", "concentrar", "concentracion", "trabajar", "leer", "focus", "study") to
            listOf("concentracion", "focus", "estudio", "study", "productividad"),
        listOf("dormir", "sueño", "sueno", "relajar", "relajarme", "calma", "tranquilo", "sleep", "relax") to
            listOf("dormir", "sueño", "sueno", "relax", "relajacion", "calma", "chill", "sleep"),
        listOf("gimnasio", "entrenar", "entrenamiento", "correr", "gym", "workout", "ejercicio", "run") to
            listOf("entrenamiento", "workout", "fitness", "energia", "gym", "deporte"),
        listOf("fiesta", "bailar", "baile", "party", "dance", "perreo", "antro") to
            listOf("fiesta", "party", "baile", "dance", "bailar"),
        listOf("manejar", "conducir", "carretera", "viaje", "driving", "road") to
            listOf("viaje", "carretera", "road", "conducir"),
        listOf("triste", "bajon", "melancol", "desamor", "sad") to
            listOf("triste", "sad", "melancolia", "desamor", "sentimental"),
        listOf("romantic", "amor", "cita", "love") to
            listOf("romance", "romantic", "amor", "love"),
        listOf("cocinar", "de fondo", "ambiente", "background") to
            listOf("ambiente", "chill", "relax", "background"),
    )

    /** Los nombres que puede tener la categoría que él busca, o vacío si no cae en ninguna familia. */
    fun conceptsFor(prompt: String, parsed: MusicRequestQuery.Parsed): List<String> {
        val folded = fold(prompt)
        val out = ArrayList<String>()
        FAMILIES.forEach { (triggers, concepts) ->
            if (triggers.any { folded.contains(it) }) out += concepts
        }
        if (parsed.decade != null) out += MusicRequestMatch.decadeTokens(parsed.decade)
        return out.distinct()
    }

    /**
     * El índice de la categoría que corresponde, o null si ninguna lo hace de forma clara.
     *
     * Una década manda sobre el momento: "música de los 80 para el gimnasio" es, ante todo, de los 80 —
     * la década es comprobable y el momento es interpretación.
     */
    fun pickCategory(
        categoryTitles: List<String>,
        prompt: String,
        parsed: MusicRequestQuery.Parsed,
    ): Int? {
        if (categoryTitles.isEmpty()) return null
        if (parsed.decade != null) {
            val tokens = MusicRequestMatch.decadeTokens(parsed.decade)
            categoryTitles.forEachIndexed { index, title ->
                val t = fold(title)
                if (tokens.any { containsToken(t, it) }) return index
            }
            // Pidió una década y el catálogo no la tiene como categoría: NO se cae al momento, que
            // daría una lista de otra cosa. Sin categoría, la escalera sigue por la búsqueda.
            return null
        }
        val concepts = conceptsFor(prompt, parsed)
        if (concepts.isEmpty()) return null
        categoryTitles.forEachIndexed { index, title ->
            val t = fold(title)
            if (concepts.any { containsToken(t, it) }) return index
        }
        return null
    }

    private fun containsToken(folded: String, token: String): Boolean {
        if (token.isBlank()) return false
        val escaped = Regex.escape(token)
        return Regex("""(?<![\p{L}\p{N}])$escaped""").containsMatchIn(folded)
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
