package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.reco.MusicStyle
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
        listOf("estudiar", "concentrar", "concentracion", "trabajar", "leer", "focus", "study", "programar", "codear") to
            listOf("concentracion", "focus", "estudio", "study", "productividad"),
        listOf("dormir", "sueño", "sueno", "relajar", "relajarme", "relajante", "relajado", "calma", "tranquilo", "sleep", "relax",
            "ruido blanco", "white noise") to
            listOf("dormir", "sueño", "sueno", "relax", "relajacion", "calma", "chill", "sleep"),
        listOf("gimnasio", "entrenar", "entrenamiento", "correr", "gym", "workout", "ejercicio", "run", "cardio") to
            listOf("entrenamiento", "workout", "fitness", "energia", "gym", "deporte"),
        listOf("fiesta", "bailar", "baile", "party", "dance", "perreo", "antro", "discoteca", "boliche", "parrillada") to
            listOf("fiesta", "party", "baile", "dance", "bailar"),
        listOf("manejar", "conducir", "carretera", "viaje", "viajar", "driving", "road") to
            listOf("viaje", "carretera", "road", "conducir"),
        // Llorar / desamor: «algo triste para llorar» (dueño, 2026-10-19) — «llorar» no era de ninguna familia y acababa
        // como palabra suelta que la categoría o la lista tenían que nombrar.
        listOf(
            "triste", "bajon", "melancol", "desamor", "sad", "llorar", "lloro", "llorando", "corazon roto", "despecho",
        ) to listOf("triste", "sad", "melancolia", "desamor", "sentimental", "despecho"),
        listOf("romantic", "amor", "cita", "love", "enamorar", "enamorado", "enamorada", "enamorarme", "conquistar", "sensual", "intimo",
            "pasion", "serenata", "serenatas") to
            listOf("romance", "romantic", "amor", "love"),
        listOf("cocinar", "cocinando", "cenar", "cena", "cenando", "desayuno", "almuerzo", "brunch", "restaurante",
            "de fondo", "ambiente", "background", "limpiar", "limpieza", "quehaceres") to
            listOf("ambiente", "chill", "relax", "background", "cena", "dinner"),
        listOf("playa", "verano", "vacaciones", "piscina", "alberca") to
            listOf("verano", "summer", "playa", "beach", "vacaciones", "fiesta", "party"),
        listOf("lluvia", "lluvioso", "llover", "tormenta", "otono", "invierno", "cafeteria") to
            listOf("lluvia", "rain", "chill", "relax", "ambiente", "cafe", "coffee", "otono", "autumn"),
        listOf("meditar", "meditacion", "yoga", "mindfulness", "masaje", "respirar", "respiracion", "sonidos de la naturaleza",
            "sonidos de naturaleza", "nature sounds", "sonidos del mar") to
            listOf("meditacion", "meditation", "yoga", "relax", "relajacion", "calma", "chill", "bienestar", "wellness"),
        listOf("bebe", "bebes", "arrullar", "nana", "nanas") to
            listOf("bebe", "nanas", "lullaby", "infantil", "kids", "ninos", "dormir", "sueno", "sleep", "relax"),
        listOf("navidad", "navideno", "navidena", "navidenos", "villancicos", "villancico", "christmas", "nochebuena") to
            listOf("navidad", "christmas", "navideno", "holiday", "villancicos", "fiestas"),
        listOf("halloween", "brujas") to listOf("halloween", "spooky", "terror"),
        listOf("boda", "casamiento", "matrimonio", "quinceanera", "quinceaneras", "quinceanos", "cumpleanos", "graduacion") to
            listOf("boda", "wedding", "fiesta", "party", "cumpleanos", "birthday", "celebracion"),
        listOf("feliz", "alegre", "contento", "buena vibra", "buenas vibras", "positivo", "alegria", "sentirse bien", "feel good",
            "motivacion", "motivar", "motivacional", "animarme", "energia", "energetico") to
            listOf("sentirse bien", "feel good", "happy", "alegre", "feliz", "positivo", "energia", "energy", "motivacion", "motivation"),
        listOf("gaming") to listOf("gaming", "focus", "concentracion", "electronica", "edm"),
        listOf("madrugada", "nocturno", "trasnochar", "medianoche") to
            listOf("noche", "night", "madrugada", "nocturno", "chill", "relax"),
    )

    /**
     * Para que una palabra de momento NO se confunda con el nombre de una canción o artista. Prefijo con al menos
     * [STEM_MIN_PREFIX] letras ("concentrar" → «concentrarme»); las más cortas, solo la palabra entera — con prefijo
     * "rain" se llevaba «Raining Blood» y «amor» a medio catálogo.
     */
    private const val STEM_MIN_PREFIX = 6

    private fun stemMatches(token: String, stem: String): Boolean =
        token == stem || (stem.length >= STEM_MIN_PREFIX && token.startsWith(stem))

    private fun tokensOf(folded: String): List<String> =
        folded.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun triggerHits(folded: String, tokens: List<String>, trigger: String): Boolean =
        if (trigger.contains(' ')) {
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(trigger)}(?![\\p{L}\\p{N}])").containsMatchIn(folded)
        } else {
            tokens.any { stemMatches(it, trigger) }
        }

    /** true cuando [text] nombra un momento, actividad o ánimo ("para llorar", "cenar", "playa"…). */
    fun hasMoment(text: String): Boolean {
        val folded = fold(text)
        val tokens = tokensOf(folded)
        return FAMILIES.any { (triggers, _) -> triggers.any { triggerHits(folded, tokens, it) } }
    }

    /** Las frases de varias palabras del vocabulario de momentos («ruido blanco», «de fondo»): también salen del resto. */
    val momentPhrases: List<String> by lazy {
        FAMILIES.flatMap { it.first }.filter { it.contains(' ') }.map { fold(it) }.distinct().sortedByDescending { it.length }
    }

    /** true cuando la palabra suelta [word] (ya normalizada) es parte del vocabulario de momentos. */
    fun isMomentWord(word: String): Boolean =
        FAMILIES.any { (triggers, _) -> triggers.any { !it.contains(' ') && stemMatches(word, it) } }

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
        // Ronda 9 (dueño): subgéneros de metal — antes solo "metal" a secas, así que "death metal"
        // caía en la categoría genérica de Metal en vez de una más específica si existe.
        listOf("death metal") to listOf("death metal", "metal"),
        listOf("black metal") to listOf("black metal", "metal"),
        listOf("thrash metal") to listOf("thrash metal", "metal"),
        listOf("power metal") to listOf("power metal", "metal"),
        listOf("heavy metal") to listOf("heavy metal", "metal"),
        listOf("doom metal") to listOf("doom metal", "metal"),
        listOf("nu metal") to listOf("nu metal", "metal"),
        listOf("metalcore") to listOf("metalcore", "metal"),
        listOf("hardcore") to listOf("hardcore"),
        listOf("emo") to listOf("emo"),
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
        // Familias enteras (dueño, 2026-10-19: «música urbana», «tropical», «regional mexicano»): cualquiera de sus nombres.
        listOf("urbano", "urbana", "urbanos", "urbanas", "urban") to
            listOf("urbano", "urbana", "urban", "reggaeton", "trap", "dembow", "hip hop", "rap", "perreo"),
        listOf("tropical", "tropicales") to
            listOf("tropical", "tropicales", "salsa", "cumbia", "merengue", "bachata", "vallenato"),
        listOf("regional mexicano", "regional mexicana", "mexicana", "mexicano") to
            listOf("regional mexicano", "regional mexican", "mexicana", "mexicano", "banda", "norteno", "corridos", "ranchera", "mariachi"),
        listOf("bolero", "boleros") to listOf("bolero"),
        // Fila 365: "christian"/"praise"/"ccm" — "80s Christian Hits" did not prove it was Christian.
        listOf("gospel", "cristiana", "cristiano", "alabanza", "worship", "christian") to
            listOf(
                "cristiana", "cristiano", "cristianas", "cristianos", "gospel", "worship", "christian", "ccm",
                "praise", "alabanza", "alabanzas",
            ),
        // Ronda 12 (dueño: "afro gospel"). Con esta familia, "afro gospel" son DOS familias pedidas a la
        // vez y la categoría/lista tiene que demostrar las dos — "Gospel" a secas ya no cuela.
        listOf("afro", "amapiano", "africa", "naija") to
            listOf(
                "afro", "afrobeats", "afrobeat", "afropop", "amapiano", "africa", "african", "africana",
                "africano", "naija", "nigeria", "nigerian", "ghana",
            ),
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
    private val CHRISTIAN_TRIGGERS = listOf("gospel", "cristiana", "cristiano", "alabanza", "worship", "christian")
    private val CHRISTIAN_CONCEPTS = listOf(
        "cristiana", "cristiano", "cristianas", "cristianos", "gospel", "worship", "alabanza", "alabanzas",
        "adoracion", "jesus", "dios", "christian", "ccm", "louvor",
    )

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
     * Los conceptos con los que una categoría o una lista puede demostrar el estilo [id]: los de la tabla de arriba (si
     * el estilo está en ella) más todos sus nombres de [MusicStyle] — "House Music" demuestra «house» aunque la tabla
     * solo hable de «electrónica».
     */
    private val CONCEPTS_BY_STYLE: Map<String, List<String>> by lazy {
        val map = HashMap<String, List<String>>()
        GENRE_FAMILIES.forEach { (triggers, concepts) ->
            triggers.forEach { t -> MusicStyle.stylesIn(t).forEach { id -> map.putIfAbsent(id, concepts) } }
        }
        map
    }

    private fun conceptsOfStyle(id: String): List<String> =
        ((CONCEPTS_BY_STYLE[id] ?: emptyList()) + MusicStyle.namesOf(id)).distinct()

    /**
     * Los grupos de conceptos de GÉNERO (no momento/actividad) que [folded] dispara.
     *
     * Dueño (2026-10-19): *«cuando pido algunos géneros no me pone lo que pido»*. Dos causas, reproducidas con
     * `MusicRequestVocabularyTest`:
     *  · los disparadores eran SUBCADENA: «reggaeton» activaba también «reggae» y «trap» también «rap», así que la
     *    categoría o la lista tenía que nombrar las dos palabras y NINGUNA lo hacía («Reggaeton Hits» no contiene
     *    «reggae» como palabra) — toda petición de reggaetón o trap descartaba todas las listas;
     *  · solo conocía ~40 géneros: cualquier otro (phonk, champeta, kizomba…) no tenía grupo y se tomaba por el título
     *    de una canción. Ahora el reconocimiento es el de [MusicStyle] (palabra completa, la frase más larga primero).
     */
    private fun genreGroupsFor(folded: String): List<List<String>> {
        val groups = LinkedHashSet<List<String>>()
        // 1) Los estilos que la petición nombra (el vocabulario completo de la app).
        // Varios estilos pedidos a la vez («salsa y merengue», «dancehall y afrobeats») son «cualquiera de ellos»: una lista
        // que los nombre a TODOS no existe, y exigirlo descartaba todas. Cada canción se comprueba aparte contra el conjunto.
        val styleIds = MusicStyle.phrasesIn(folded).map { it.second }.distinct()
        if (styleIds.isNotEmpty()) groups += styleIds.flatMap { conceptsOfStyle(it) }.distinct()
        // 2) Lo que la tabla conoce y MusicStyle no ("africa", "cristiana"…), sobre lo que queda sin esos estilos.
        var rest = " ${MusicStyle.withoutStyles(folded)} "
        for ((triggers, concepts) in GENRE_TRIGGERS_LONGEST_FIRST) {
            val hit = triggers.firstOrNull { t -> wholeWord(rest, t) } ?: continue
            groups += concepts
            rest = rest.replace(Regex("(?<![\\p{L}\\p{N}])${Regex.escape(hit)}(?:s|es)?(?![\\p{L}\\p{N}])"), " ")
        }
        // 3) Lo cristiano se pide con su propia palabra aunque la frase ya sea un estilo ("afro gospel").
        if (CHRISTIAN_TRIGGERS.any { wholeWord(" $folded ", it) } && groups.none { it.containsAll(CHRISTIAN_GROUP) }) {
            groups += CHRISTIAN_GROUP
        }
        return groups.toList()
    }

    private fun wholeWord(paddedOrText: String, word: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?:s|es)?(?![\\p{L}\\p{N}])").containsMatchIn(paddedOrText)

    private val CHRISTIAN_GROUP: List<String> by lazy {
        GENRE_FAMILIES.first { (_, concepts) -> "ccm" in concepts }.second
    }

    /** La tabla de arriba, con el disparador MÁS LARGO primero para que «death metal» se lea antes que «metal». */
    private val GENRE_TRIGGERS_LONGEST_FIRST: List<Pair<List<String>, List<String>>> by lazy {
        GENRE_FAMILIES.sortedByDescending { (triggers, _) -> triggers.maxOf { it.length } }
    }

    /**
     * Los momentos/actividades/ánimos que [folded] nombra, como grupos de conceptos. Son BLANDOS (ver
     * [conceptGroupsFor]): el género y la década se comprueban, el momento se interpreta.
     */
    private fun momentGroupsFor(folded: String): List<List<String>> {
        val tokens = tokensOf(folded)
        return FAMILIES.mapNotNull { (triggers, concepts) ->
            concepts.takeIf { triggers.any { t -> triggerHits(folded, tokens, t) } }
        }
    }

    /**
     * Ronda 9 (dueño): "bachata cristiana" — [pickCategory] cogía la categoría "Bachata" a secas,
     * ignorando "cristiana" por completo, porque [conceptsFor] mezclaba los conceptos de TODAS las
     * familias que matchean en una sola bolsa plana, y bastaba con satisfacer UNA para "ganar". Esto
     * separa cada familia que matchea en su propio grupo para que la categoría elegida tenga que
     * demostrar TODAS las familias pedidas a la vez, no una cualquiera de ellas.
     */
    /**
     * @param qualifiers Ronda 12 — palabras de la petición que no son ninguna familia conocida pero la
     *   CALIFICAN ("argentino" en "rock argentino"; ver [MusicRequestQuery.residualWords]). Cada una es
     *   un grupo propio, así que la categoría o la lista tiene que nombrarla también.
     */
    internal fun conceptGroupsFor(
        prompt: String,
        parsed: MusicRequestQuery.Parsed,
        qualifiers: List<String> = emptyList(),
    ): List<List<String>> {
        val folded = fold(prompt)
        val groups = ArrayList<List<String>>()
        val genres = genreGroupsFor(folded)
        // Lo comprobable manda sobre lo interpretable: «salsa romántica de los 90 para cocinar» pide salsa y los 90
        // (se demuestran por el título); «romántica» y «cocinar» son ambiente, y exigirlos TAMBIÉN hacía que ninguna
        // lista o categoría pasara jamás. El momento solo se exige cuando es lo ÚNICO que se pidió.
        // Varios momentos («dormir» + «sonidos de la naturaleza») se suman: el título prueba cualquiera de ellos.
        if (genres.isEmpty() && parsed.decade == null) {
            val moments = momentGroupsFor(folded)
            if (moments.isNotEmpty()) groups += moments.flatten().distinct()
        }
        groups += genres
        if (parsed.decade != null) groups += MusicRequestMatch.decadeTokens(parsed.decade)
        // Row 462: a country is proved by ANY of its names ("colombiana" ⇒ also "colombia", "colombiano", "vallenato"…).
        qualifiers.map { fold(it) }.filter { it.isNotBlank() }.distinct().forEach { groups += MusicRequestRegion.group(it) }
        return groups.distinct()
    }

    /**
     * El índice de la categoría que corresponde, o null si ninguna lo hace de forma clara.
     *
     * Una década manda sobre el MOMENTO: "música de los 80 para el gimnasio" es, ante todo, de los
     * 80 — la década es comprobable y el momento es interpretación. Pero NO manda sobre el GÉNERO
     * (ronda 9, dueño: "reggae de los 90" traía "Éxitos de los 90" genéricos, ignorando "reggae" por
     * completo). Antes de este arreglo la rama de década descartaba conceptGroupsFor entero — géneros
     * incluidos — así que cualquier categoría con la década alcanzaba, sin importar el género pedido.
     * Ahora, si además hay un género reconocido, la categoría tiene que demostrar los dos a la vez,
     * igual que ya exige la rama de abajo para género+tema.
     */
    fun pickCategory(
        categoryTitles: List<String>,
        prompt: String,
        parsed: MusicRequestQuery.Parsed,
        // Ronda 12 — ver [conceptGroupsFor]: "afro gospel" no puede caer en la categoría "Gospel" a secas.
        qualifiers: List<String> = emptyList(),
    ): Int? {
        if (categoryTitles.isEmpty()) return null
        val qualifierGroups = qualifiers.map { fold(it) }.filter { it.isNotBlank() }.distinct().map { MusicRequestRegion.group(it) }
        if (parsed.decade != null) {
            val requiredGroups = listOf(MusicRequestMatch.decadeTokens(parsed.decade)) + genreGroupsFor(fold(prompt)) +
                qualifierGroups
            return bestCategory(categoryTitles, requiredGroups)
        }
        val groups = conceptGroupsFor(prompt, parsed, qualifiers)
        if (groups.isEmpty()) return null
        // Ronda 9: si matcheó VARIAS familias a la vez (género + tema), la categoría tiene que
        // demostrar todas — una que solo cumpla una es la misma improvisación que ya se prohibió.
        return bestCategory(categoryTitles, groups)
    }

    /**
     * La categoría que cumple TODOS los [groups], y entre las que los cumplen la que lo hace con los conceptos más
     * principales (los de cada grupo van de más a menos propios: «verano» antes que «fiesta»), después la primera del
     * catálogo. Dueño (2026-10-19): «música para la playa» caía en «Fiesta» solo por salir antes en el catálogo.
     * Si pidió una década (y, si la hay, un género) y el catálogo no tiene una categoría que demuestre todo a la vez: null —
     * NO se cae al momento, que daría una lista de otra cosa; sin categoría, la escalera sigue por la búsqueda.
     */
    private fun bestCategory(categoryTitles: List<String>, groups: List<List<String>>): Int? {
        var best: Int? = null
        var bestRank = Int.MAX_VALUE
        categoryTitles.forEachIndexed { index, title ->
            val t = fold(title)
            var rank = 0
            for (group in groups) {
                val at = group.indexOfFirst { containsToken(t, it) }
                if (at < 0) return@forEachIndexed
                rank += at
            }
            if (rank < bestRank) {
                best = index
                bestRank = rank
            }
        }
        return best
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
