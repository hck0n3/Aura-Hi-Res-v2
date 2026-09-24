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

    /**
     * Ronda 7 (dueño): "si pongo reggaetón me pone canciones que llevan el nombre de reggaetón en el
     * título — es una búsqueda estúpida". Un género suelto ("reggaeton", "bossa nova") no matcheaba
     * ninguna [FAMILIES] (esas son de MOMENTO/ACTIVIDAD, no de género) ni activaba
     * [MusicRequestQuery.Parsed.preferPlaylists], así que nunca llegaba a
     * [AiPlaylistGenerator.moodCategoryPlaylists] — caía directo a una búsqueda de texto libre sin
     * ninguna verificación de que el resultado fuera realmente de ese género (a diferencia de las
     * listas, que sí pasan por [MusicRequestMatch]). Mismo mecanismo que [FAMILIES], mismo formato
     * (disparadores → nombres de categoría a buscar), solo que para géneros musicales en vez de
     * momentos — la categoría real de YouTube Music ("Reggaetón", "Bossa Nova"…) sigue siendo la
     * prueba, no una búsqueda cruda.
     */
    private val GENRE_FAMILIES: List<Pair<List<String>, List<String>>> = listOf(
        listOf("reggaeton", "reggaetón", "perreo", "dembow") to listOf("reggaeton", "urbano", "urban"),
        listOf("salsa") to listOf("salsa"),
        listOf("bachata") to listOf("bachata"),
        listOf("merengue") to listOf("merengue"),
        listOf("rock") to listOf("rock"),
        listOf("pop") to listOf("pop"),
        listOf("bossa nova", "bossanova", "bosanova") to listOf("bossa nova", "brasil", "brazilian", "brazil"),
        listOf("jazz") to listOf("jazz"),
        listOf("cumbia") to listOf("cumbia"),
        listOf("vallenato") to listOf("vallenato"),
        listOf("banda", "sinaloense") to listOf("banda", "regional mexicano", "regional mexican"),
        listOf("mariachi") to listOf("mariachi", "regional mexicano", "regional mexican"),
        listOf("ranchera", "rancheras") to listOf("ranchera", "regional mexicano", "regional mexican"),
        listOf("corridos", "corrido") to listOf("corridos", "regional mexicano", "regional mexican"),
        listOf("trap") to listOf("trap"),
        listOf("rap", "hip hop", "hiphop") to listOf("rap", "hip hop", "hip-hop"),
        listOf("electronica", "electrónica", "edm", "house", "techno") to listOf("electronica", "edm", "dance"),
        listOf("metal") to listOf("metal"),
        listOf("punk") to listOf("punk"),
        listOf("k-pop", "kpop") to listOf("k-pop", "korean pop", "kpop"),
        listOf("r&b", "rnb") to listOf("r&b", "rnb"),
        listOf("flamenco") to listOf("flamenco"),
        listOf("country") to listOf("country"),
        listOf("blues") to listOf("blues"),
        listOf("indie") to listOf("indie"),
        listOf("funk") to listOf("funk"),
        listOf("soul") to listOf("soul"),
        listOf("reggae") to listOf("reggae"),
        listOf("disco") to listOf("disco"),
        listOf("tango") to listOf("tango"),
        listOf("bolero", "boleros") to listOf("bolero"),
        listOf("gospel", "cristiana", "cristiano", "alabanza", "worship") to
            listOf("cristiana", "cristiano", "gospel", "worship"),
    )

    /**
     * Ronda 9 (dueño): "pedí reggae cristiano y me salió un artista que no es cristiano... si al
     * final va la palabra cristiano, tiene que respetar eso sí o sí — mientras no se mencione, puede
     * poner lo que considere mejor". A diferencia del género (donde una interpretación floja es
     * aceptable), el tema religioso es una condición DURA cuando se pide: a diferencia de una lista
     * (verificable por título vía [conceptGroupsFor]/[MusicRequestMatch]), una canción suelta sin
     * verificar no pasaba por ningún filtro — [AiPlaylistGenerator] usa esto para descartarla si ni
     * su título ni su artista demuestran el tema, en vez de aceptar cualquier resultado del buscador.
     */
    private val CHRISTIAN_TRIGGERS = listOf("gospel", "cristiana", "cristiano", "alabanza", "worship")
    private val CHRISTIAN_CONCEPTS =
        listOf("cristiana", "cristiano", "gospel", "worship", "alabanza", "adoracion", "jesus", "dios")

    /** true si [prompt] pidió explícitamente música cristiana/gospel — ver [CHRISTIAN_TRIGGERS]. */
    fun requiresChristianContent(prompt: String): Boolean =
        CHRISTIAN_TRIGGERS.any { fold(prompt).contains(it) }

    /** true si [text] (título, nombre de artista…) muestra una señal cristiana/gospel reconocible. */
    fun looksChristian(text: String): Boolean {
        val t = fold(text)
        return CHRISTIAN_CONCEPTS.any { containsToken(t, it) }
    }

    /** Los nombres que puede tener la categoría que él busca, o vacío si no cae en ninguna familia. */
    fun conceptsFor(prompt: String, parsed: MusicRequestQuery.Parsed): List<String> =
        conceptGroupsFor(prompt, parsed).flatten().distinct()

    /**
     * Ronda 9 (dueño): "bachata cristiana" — [pickCategory] cogía la categoría "Bachata" a secas,
     * ignorando "cristiana" por completo, porque [conceptsFor] mezclaba los conceptos de TODAS las
     * familias que matchean en una sola bolsa plana, y bastaba con satisfacer UNA para "ganar". Esto
     * separa cada familia que matchea en su propio grupo para que la categoría elegida tenga que
     * demostrar TODAS las familias pedidas a la vez, no una cualquiera de ellas.
     */
    internal fun conceptGroupsFor(prompt: String, parsed: MusicRequestQuery.Parsed): List<List<String>> {
        val folded = fold(prompt)
        val groups = ArrayList<List<String>>()
        FAMILIES.forEach { (triggers, concepts) ->
            if (triggers.any { folded.contains(it) }) groups += concepts
        }
        GENRE_FAMILIES.forEach { (triggers, concepts) ->
            if (triggers.any { folded.contains(it) }) groups += concepts
        }
        if (parsed.decade != null) groups += MusicRequestMatch.decadeTokens(parsed.decade)
        return groups
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
        val groups = conceptGroupsFor(prompt, parsed)
        if (groups.isEmpty()) return null
        categoryTitles.forEachIndexed { index, title ->
            val t = fold(title)
            // Ronda 9: si matcheó VARIAS familias a la vez (género + tema), la categoría tiene que
            // demostrar todas — una que solo cumpla una es la misma improvisación que ya se prohibió.
            if (groups.all { group -> group.any { containsToken(t, it) } }) return index
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
