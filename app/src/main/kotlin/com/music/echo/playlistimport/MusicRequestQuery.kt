package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.reco.MusicStyle
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
        """(?iu)^\s*(?:por\s+favor\s+)?(?:me\s+)?(?:puedes?\s+|podrias\s+)?""" +
            """(?:ponme|pon|poner|reproduce|reproducir|quiero|querria|queria|me\s+gustaria|dame|""" +
            """busca|buscame|necesito|pongamos|echame|play|put\s+on|i\s+want|genera|generame|crea|creame|hazme|""" +
            """armame|arma|pasame|suena|escuchar|quisiera|recomiendame|sugiereme|show\s+me|give\s+me|play\s+me)\s+""" +
            """(?:(?:escuchar|oir|ver|tener)\s+)?""" +
            // Dueño (2026-10-19): «dame 20 canciones de bachata», «ponme una lista de salsa», «quiero unos temas de…»:
            // los sustantivos de relleno también sobran, en cualquier orden y combinación.
            """(?:(?:algo|alguna|unas?|unos?|un\s+poco)\s+(?:de\s+)?|(?:una?\s+)?(?:lista|playlist|mix)\s+(?:de\s+)?|""" +
            """(?:las\s+|los\s+)?(?:canciones|temas|exitos)\s+(?:de\s+)?|musica\s+(?:de\s+)?)*""",
    )

    /**
     * Palabras de relleno de una frase hablada: no nombran nada y, sueltas, eran lo que la categoría o la lista tenían
     * que "demostrar" («algo triste para llorar» → «algo» y «llorar» como palabras obligatorias del título).
     */
    private val FILLER_WORDS = setOf(
        "algo", "alguna", "algunas", "algun", "alguno", "algunos", "unas", "unos", "bien", "muy", "mucho", "mas", "tipo",
        "favor", "porfa", "gracias", "please", "thanks", "ahora", "hoy", "ratito", "rato", "cosas", "cosa", "que", "quiero",
        "pon", "ponme", "dame", "poner", "tengo", "ganas", "escuchar", "oir", "pero", "como", "estilo", "genero", "music",
        "ese", "esa", "esto", "esta", "este", "esos", "esas", "mis", "mi", "tu", "su", "me", "te", "se", "lo", "al",
        // Verbos de actividad de una frase hablada («para hacer ejercicio», «mientras voy a trabajar»): no nombran nada.
        "hacer", "haciendo", "hago", "estar", "estoy", "estando", "voy", "vas", "ir", "irme", "salir", "tener", "ver",
        "mientras", "cuando", "durante", "donde", "hacia", "sobre", "entre", "desde", "hasta", "cada", "todo", "todos", "toda",
        "todas", "siempre", "nunca", "tiempo", "tiempos", "ayer", "manana", "luego", "despues", "antes", "mejorar", "poner",
    )

    /** Conjunciones y preposiciones que no pueden ir al principio ni al final de una consulta. */
    private val DANGLING = setOf("pero", "y", "e", "o", "ni", "con", "para", "de", "del", "que", "por", "sin", "al", "en", "a")

    /** La petición sin la muletilla inicial («ponme», «quiero algo de»…), ya normalizada. Para quien necesita el resto. */
    fun withoutLeadIn(prompt: String): String =
        LEAD_IN.replace(fold(prompt), "").trim().ifBlank { fold(prompt) }

    /** Las de [FILLER_WORDS] que se pueden quitar de la CONSULTA sin dañar un título ("mi gente", "lo que sea"). */
    private val QUERY_FILLER = setOf(
        "algo", "alguna", "algunas", "algun", "alguno", "algunos", "unas", "unos", "favor", "porfa", "gracias", "please",
        "thanks", "ahora", "hoy", "ratito", "rato", "ganas", "tengo", "quiero", "pon", "ponme", "dame", "poner", "escuchar", "oir",
    )

    /**
     * Adjetivos y épocas sueltas ("nueva", "viejitas", "populares", "latino"): matizan lo que se pide pero ningún título
     * de lista los lleva de forma fiable, así que NO se exigen como palabra obligatoria (la época y los clásicos los lee
     * [MusicRequestIntent]; el idioma y el país no se pueden demostrar con un título).
     */
    private val SOFT_WORDS = setOf(
        "nueva", "nuevas", "nuevo", "nuevos", "viejita", "viejitas", "viejito", "viejitos", "vieja", "viejas", "viejo", "viejos",
        "antigua", "antiguas", "antiguo", "antiguos", "reciente", "recientes", "actual", "actuales", "estreno", "estrenos",
        "novedades", "ultimo", "ultimos", "ultima", "ultimas", "popular", "populares", "famosa", "famosas", "famoso", "famosos",
        "conocida", "conocidas", "conocido", "conocidos", "buena", "buenas", "bueno", "buenos", "genial", "geniales",
        "bonita", "bonitas", "bonito", "bonitos", "linda", "lindas", "lindo", "lindos",
        "movida", "movidas", "movido", "movidos", "suave", "suaves", "lenta", "lentas", "lento", "lentos", "rapida", "rapidas",
        "bailable", "bailables", "pegajosa", "pegajosas", "pegadiza", "pegadizas", "mejor", "mejores", "clasica", "clasicas",
        "latino", "latina", "latinos", "latinas", "latin", "oldies", "retro", "actual", "nuevos", "favoritas", "favoritos",
        "moderna", "modernas", "moderno", "modernos", "original", "originales",
    )

    /**
     * Frases de época o de «lo mejor» («de todos los tiempos», «of all time»): las lee [MusicRequestIntent] y no son una
     * palabra que el título de una lista deba llevar.
     */
    private val ERA_PHRASES = listOf(
        "de todos los tiempos", "of all time", "de la historia", "de siempre", "del momento", "en la actualidad", "hoy en dia",
        "de hoy", "de ahora", "de antes", "del recuerdo", "vieja escuela", "old school", "lo mas nuevo", "lo ultimo",
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
     * Otros idiomas que se pueden pedir. No se comprueban canción por canción (la app solo distingue español, inglés y
     * portugués), pero su pista no puede quedar como «palabra obligatoria» del título de una lista.
     */
    private val OTHER_LANGUAGE_HINTS = listOf(
        "en portugues", "em portugues", "in portuguese", "portugues", "portuguese", "en frances", "in french", "frances",
        "french", "en italiano", "in italian", "italiano", "italian", "en aleman", "in german", "aleman", "german",
        "en coreano", "en japones", "en chino", "en arabe", "en catalan", "en gallego", "en latin",
    )

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
        // Ronda 9 (dueño, auditoría del algoritmo): "música romántica"/"canciones tristes" no
        // activaban preferPlaylists aunque MusicRequestMoods.FAMILIES sí reconoce esas dos familias
        // ("Romance", "Sad") — la petición caía directo a búsqueda de texto libre sin curar, sin que
        // ningún filtro la volviera a comprobar después. Mismas palabras que disparan esas familias.
        "triste", "bajon", "melancol", "desamor", "sad",
        "romantic", "romantica", "romantico", "amor", "cita", "love",
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
        // Ronda 9 (dueño): "death metal" — subgéneros de dos palabras que antes solo matcheaban por
        // la palabra genérica ("metal"), dejando la otra mitad ("death") como ruido suelto en
        // [residualBeyondCategory] y contaminando la detección de "esto es específico".
        "death metal", "black metal", "thrash metal", "power metal", "heavy metal", "doom metal",
        "nu metal", "metalcore", "hardcore", "emo",
        // Ronda 9 (dueño, auditoría del algoritmo): "música cristiana"/"quiero alabanza"/"worship" no
        // disparaban preferPlaylists — MusicRequestMoods.GENRE_FAMILIES/CHRISTIAN_TRIGGERS sí las
        // reconoce ("gospel" ya estaba aquí, pero no sus sinónimos), así que la petición se saltaba
        // moodCategoryPlaylists por completo y caía a búsqueda sin curar.
        "cristiana", "cristiano", "alabanza", "worship",
        // Fila 365: plurals and the English word, or "alabanzas cristianas" left "cristianas" as a stray qualifier.
        "cristianas", "cristianos", "alabanzas", "christian",
        // Ronda 12 (dueño, 2026-10-05: "pedí afro gospel y me tiró cualquier cosa menos lo que pedí").
        // "afro" no era un género conocido: quedaba como resto suelto, se fijaba como si fuera el
        // título de una canción y la categoría genérica de gospel llenaba todo lo demás. Mismas
        // palabras que la familia afro de MusicRequestMoods.GENRE_FAMILIES.
        "afro", "afrobeat", "afrobeats", "afropop", "afro house", "amapiano",
        "africa", "african", "africana", "africano",
        // Dueño (2026-10-19): «oldies» es una época, no el título de una canción; «urbano», «tropical» y «regional
        // mexicano» son FAMILIAS (se aceptan todos sus estilos), no el nombre de una canción.
        "oldies", "urbano", "urbana", "urbanos", "urbanas", "urban", "tropical", "tropicales", "regional mexicano",
        "regional mexicana", "mexicana", "mexicano",
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
        // Ronda 11: "pon la canción Flipando de Redimi2" — "cancion"/"tema" no nombran nada.
        "cancion", "tema", "temas", "song",
        // Palabras de recopilación (mismo espíritu que MusicRequestMatch.COMPILATION_WORDS): "éxitos
        // de los 2000" no debe leer "exitos" como si fuera el género que pidió.
        "exitos", "hits", "mejor", "best", "greatest", "clasicos", "classics", "mix", "top",
    )

    /**
     * Lo que NO puede ser una palabra obligatoria del título de una lista ni lo que se busque como canción: relleno hablado,
     * adjetivos y épocas sueltas. A diferencia de [CONNECTOR_WORDS] NO se quita del resto que se compara con los títulos de
     * canciones («Siempre Te Esperaré», «Como La Flor» conservan todas sus palabras): solo se descuenta al decidir si el
     * resto tiene SUSTANCIA ([hasSubstance]) y al elegir las palabras que califican al género ([residualWords]).
     */
    private val IGNORED_WORDS = FILLER_WORDS + SOFT_WORDS + setOf("music", "for", "in", "on", "with", "by")

    /** Row 493: GENRE_HINTS words that mean a COUNTRY once a concrete style is named next to them. */
    private val DEMONYM_HINTS = setOf("mexicana", "mexicano")

    fun build(prompt: String): Parsed {
        val raw = prompt.trim()
        if (raw.isBlank()) return Parsed(query = "", preferPlaylists = false)

        val folded = fold(raw)
        val withoutLeadIn = LEAD_IN.replace(fold(raw), "").trim().ifBlank { folded }
        // La consulta sin muletillas habladas que quedan en medio («algo triste para llorar», «rock pon»).
        val spokenTokens = withoutLeadIn.split(Regex("\\s+")).filter { it.isNotBlank() && it !in QUERY_FILLER }
            // Solo «pero» y «ni» sobran al principio; «con», «sin», «a», «para», «y»… abren títulos de verdad («Con Calma»,
            // «Sin Pijama»). Al final, en cambio, ninguna preposición cierra un título.
            .dropWhile { it == "pero" || it == "ni" }.dropLastWhile { it in DANGLING }
        // «algo parecido a X pero sin Y» deja «pero»: una consulta sin nada que buscar es "" (quien llama usa otra fuente),
        // no la palabra suelta, que traía canciones que se llaman «Pero».
        val spoken = if (spokenTokens.all { it in CONNECTOR_WORDS || it in DANGLING || it in IGNORED_WORDS }) "" else spokenTokens.joinToString(" ")

        val decade = detectDecade(withoutLeadIn)
        val language = when {
            ENGLISH_HINTS.any { withoutLeadIn.contains(it) } -> "en"
            SPANISH_HINTS.any { withoutLeadIn.contains(it) } -> "es"
            else -> null
        }
        val moment = MOMENT_HINTS.any { withoutLeadIn.contains(it) } || MusicRequestMoods.hasMoment(withoutLeadIn)
        val trending = TREND_HINTS.any { withoutLeadIn.contains(it) }
        // Ronda 7: un género suelto también prefiere listas — ver GENRE_HINTS arriba.
        // Dueño (2026-10-19): por PALABRA completa (antes por subcadena: «popular» era pop, «emocional» era emo) y con
        // todo el vocabulario de géneros de [MusicStyle] — un género que solo estaba allí (phonk, champeta, kizomba…) no
        // activaba las listas y se tomaba por el título de una canción.
        val genreHint = GENRE_HINTS.any { hasWord(withoutLeadIn, it) } || MusicStyle.namesAStyle(withoutLeadIn)

        // Ronda 6 (dueño): "reggae de los 90" perdía "reggae" por completo — la petición se
        // reemplazaba entera por la plantilla fija "exitos de los 90". Lo que queda de la frase tras
        // quitar la década y las pistas de idioma es el género/tema que sí pidió; si no queda nada
        // (petición de década pura, "música de los 80"), el comportamiento es exactamente el de antes.
        val genreRaw = if (decade != null) residualGenre(withoutLeadIn, decade) else null

        // Fila 365 (dueño 2026-10-09: "música de los 80 cristiana en inglés" → "no encontré nada"). Asked in
        // English, the search got a half-Spanish query ("cristiana 80s hits english") and the English Christian
        // lists ("80s Christian Hits") never came up: the theme words go in English too.
        val genre = if (language == "en") genreRaw?.let { toEnglishTerms(it) } else genreRaw
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
            language == "en" -> "${toEnglishTerms(stripLanguageHint(spoken))} english".trim()
            language == "es" -> "${stripLanguageHint(spoken)} en espanol".trim()
            // Sin década ni idioma: su propia petición, sin muletillas. Nunca peor que mandar la
            // frase entera.
            else -> spoken
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

    /**
     * Ronda 9 (dueño): "pedí death metal más el nombre de una canción y de un artista, y reprodujo
     * lo que quiso, no lo que pedí específicamente — quiero que entienda cuando soy específico".
     * Lo que sobra de la petición tras quitar género/momento/tendencia/década/idioma/muletillas. Si
     * no queda nada, la petición es puramente genérica (un género o momento solos) y el
     * comportamiento de categoría/lista de [MusicRequestMoods] sigue intacto. Si queda algo
     * sustancial, es la canción/artista concretos que mencionó junto al género — [AiPlaylistGenerator]
     * usa esto para buscar y anteponer ESO antes de dejar que la categoría genérica llene la cola.
     */
    fun residualBeyondCategory(prompt: String): String {
        val raw = prompt.trim()
        if (raw.isBlank()) return ""
        val folded = fold(raw)
        var stripped = LEAD_IN.replace(folded, "").trim().ifBlank { folded }
        // Las frases de dos palabras van PRIMERO (más largas → más específicas): quitar "metal"
        // suelto antes de llegar a "death metal" dejaría "death" como ruido residual suelto.
        val hints = (GENRE_HINTS + MOMENT_HINTS + TREND_HINTS).sortedByDescending { it.length }
        fun strip(list: List<String>) {
            list.forEach { hint ->
                stripped = stripped.replace(Regex("""(?<![\p{L}\p{N}])${Regex.escape(hint)}(?:s|es)?(?![\p{L}\p{N}])"""), " ")
            }
        }
        strip(hints.filter { it.any { c -> c == ' ' || c == '-' || c == '&' } })
        ERA_PHRASES.forEach { phrase ->
            stripped = stripped.replace(Regex("""(?<![\p{L}\p{N}])${Regex.escape(phrase)}(?![\p{L}\p{N}])"""), " ")
        }
        MusicRequestMoods.momentPhrases.forEach { phrase ->
            stripped = stripped.replace(Regex("""(?<![\p{L}\p{N}])${Regex.escape(phrase)}(?![\p{L}\p{N}])"""), " ")
        }
        // Dueño (2026-10-19): TODO el vocabulario de géneros de la app, no solo los de [GENRE_HINTS] — «phonk» o
        // «champeta» quedaban como resto y se buscaban como si fueran el título de una canción. Solo se quitan los nombres
        // del estilo; las palabras de un subgénero («sonidera», «doom») se quedan para que la lista las demuestre. Va
        // antes que las palabras sueltas de la lista vieja: «city pop» es un estilo, no «pop» más «city».
        // Row 493: «mexicana» is the FAMILY «regional mexicano» only when no concrete style is named; with one («cumbia
        // mexicana») it is the COUNTRY and has to stay, or «cumbia mexicana» was answered as plain «cumbia».
        val concreteStyleNamed = MusicStyle.stylesIn(stripped).isNotEmpty()
        stripped = MusicStyle.withoutStyles(stripped)
        strip(hints.filter { hint ->
            hint.none { c -> c == ' ' || c == '-' || c == '&' } && !(concreteStyleNamed && hint in DEMONYM_HINTS)
        })
        stripped = stripLanguageHint(stripped)
        OTHER_LANGUAGE_HINTS.sortedByDescending { it.length }.forEach { hint ->
            stripped = stripped.replace(Regex("""\b${Regex.escape(hint)}\b"""), " ")
        }
        DECADE_WORDS.keys.forEach { word ->
            stripped = stripped.replace(Regex("""\b${Regex.escape(word)}\b"""), " ")
        }
        stripped = DECADE_DIGITS.replace(stripped, " ")
        return stripped.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in CONNECTOR_WORDS }
            .joinToString(" ")
            .trim()
    }

    /**
     * true cuando [residual] (de [residualBeyondCategory]) tiene algo que pueda ser el nombre de una canción o de un
     * artista: alguna palabra que no sea relleno, adjetivo suelto ni palabra de ánimo o actividad. «algo llorar» (de «algo
     * triste para llorar») no la tiene: es una petición de ambiente, y fijar la primera canción que se llame «Algo» o
     * «Llorar» sería la improvisación contraria a lo que pidió.
     */
    fun hasSubstance(residual: String): Boolean = residualWords(residual).isNotEmpty()

    /**
     * true cuando [text] está hecho SOLO de géneros, ánimos o momentos, épocas, idiomas y muletillas: no nombra a ninguna
     * persona ni canción («bossa nova», «música de los 80», «reguetón cristiano en inglés»). Lo usa
     * [iad1tya.echo.music.api.AiPlaylistConstraints] para no tomar un género por un artista.
     */
    fun isThemeOnly(text: String): Boolean =
        residualBeyondCategory(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            .all {
                it in IGNORED_WORDS || MusicRequestMoods.isMomentWord(it) ||
                    // Row 493: a country («colombiana») or a sub-style («villera») is not a person either.
                    MusicRequestRegion.isRegionWord(it) || MusicStyle.isSubStyleWord(it)
            }

    /**
     * Ronda 12 (dueño: "afro gospel", "hip hop de eminem", "rock argentino"). Las palabras de contenido
     * de un resto de [residualBeyondCategory] — ya normalizado — que no sea un artista ni una canción
     * confirmada: entonces es lo que CALIFICA al género ("argentino", "afro"…) y la categoría o la lista
     * elegidas tienen que nombrarlo también, en vez de ignorarlo.
     */
    fun residualWords(residual: String): List<String> =
        residual.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in CONNECTOR_WORDS && it !in IGNORED_WORDS && !MusicRequestMoods.isMomentWord(it) }
            .distinct()

    /** Fila 365 — the Spanish theme words a request in English carries, as the English search knows them. */
    private val TO_ENGLISH = mapOf(
        "cristiana" to "christian", "cristiano" to "christian", "cristianas" to "christian",
        "cristianos" to "christian", "alabanza" to "worship", "alabanzas" to "worship", "adoracion" to "worship",
        "romantica" to "love songs", "romanticas" to "love songs", "romantico" to "love songs",
        "romanticos" to "love songs", "baladas" to "ballads", "balada" to "ballads",
        // Dueño (2026-10-19): «algo triste para llorar en inglés» se buscaba medio en español («algo triste para llorar
        // english») y no traía las listas inglesas. Los ánimos y momentos van en inglés cuando la petición es en inglés.
        "triste" to "sad", "tristes" to "sad", "llorar" to "sad", "lloro" to "sad", "desamor" to "heartbreak",
        "estudiar" to "study", "concentrarme" to "focus", "dormir" to "sleep", "relajar" to "relax", "relajarme" to "relax",
        "correr" to "running", "gimnasio" to "workout", "entrenar" to "workout", "fiesta" to "party", "bailar" to "dance",
        "cocinar" to "cooking", "cenar" to "dinner", "playa" to "beach", "verano" to "summer", "navidad" to "christmas",
        "manejar" to "driving", "viaje" to "road trip", "meditar" to "meditation", "alegre" to "happy", "feliz" to "happy",
    )

    /** Conectores del español que no significan nada en una consulta en inglés. */
    private val SPANISH_CONNECTORS = setOf("para", "de", "del", "la", "el", "los", "las", "un", "una", "con", "por", "en", "y", "al")

    internal fun toEnglishTerms(text: String): String =
        text.split(Regex("\\s+")).filter { it.isNotBlank() && it !in SPANISH_CONNECTORS }
            .map { TO_ENGLISH[it] ?: it }
            .distinct()
            .joinToString(" ")

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

    private fun hasWord(text: String, word: String): Boolean =
        Regex("""(?<![\p{L}\p{N}])${Regex.escape(word)}(?:s|es)?(?![\p{L}\p{N}])""").containsMatchIn(text)

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
