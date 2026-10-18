package iad1tya.echo.music.playlistimport

/**
 * Fila 435 — LOS AÑOS QUE SE PIDEN, COMO RESTRICCIÓN DE VERDAD.
 *
 * 🔴 Dueño (2026-10-18): *«le pido reggaetón cristiano del 2004 a 2010: empieza bien y luego me pone música de años más
 * recientes… tiene que adecuarse a lo que yo pido»*.
 *
 * Qué pasaba (leído en el código, no supuesto): el año solo entraba como PALABRA en la búsqueda. "del 2004 a 2010" se
 * leía como UN año (el último, 2010: el 2004 se perdía y "del a" quedaba en la petición); "años 70 y 80" solo conservaba
 * el primero ("70") y dejaba "80" como si fuera un género; y nada comprobaba el año de cada canción, así que el
 * búsqueda, los vídeos, la radio de relleno y la cola inteligente que continúa la petición traían cualquier año.
 *
 * Este objeto es la parte PURA: entender el rango pedido ([find]) y decidir qué canciones se quedan según el año que se
 * sepa de ellas ([select]). De dónde sale el año de una canción (el del álbum en YouTube Music) vive aparte, en
 * [MusicRequestEraYears]. Probado en `MusicRequestEraTest`.
 */
object MusicRequestEra {

    /** Años INCLUSIVOS. */
    data class Range(val from: Int, val to: Int) {
        init {
            require(from <= to) { "from=$from > to=$to" }
        }

        operator fun contains(year: Int): Boolean = year in from..to

        val singleYear: Boolean get() = from == to

        /** Una sola década exacta ("los 80", "2000s"). */
        val singleDecade: Boolean get() = from % 10 == 0 && to == from + 9

        /** La palabra que se le añade a la búsqueda: "1980s", "2010", o la primera década de un rango. */
        fun searchWord(): String = when {
            singleYear -> from.toString()
            else -> "${from / 10 * 10}s"
        }
    }

    /**
     * [range] es lo pedido; [dropIndexes] son los tokens de la petición que dicen el año (se quitan de lo que se le da
     * al buscador y al resto de la escalera). Una sola década NO se quita: [MusicRequestQuery] ya la entiende ("de los
     * 80") y exige que las listas la nombren.
     */
    data class Hit(val range: Range, val dropIndexes: Set<Int>)

    private val YEAR = Regex("^(19[5-9]\\d|20[0-4]\\d)$")
    private val DECADE_S = Regex("^(\\d)0s$|^(19[5-9]0|20[0-4]0)s$")
    private val DECADE_TWO = Regex("^(\\d)0$")
    private val DECADE_FOUR = Regex("^(19[5-9]0|20[0-4]0)$")

    // Palabras que anuncian una década ("años 70", "de los 70", "la década del 80").
    private val DECADE_CUES = setOf("anos", "ano", "los", "las", "decada", "decadas", "the")

    // Unen dos años o dos décadas en un rango.
    private val CONNECTORS = setOf("y", "e", "a", "al", "hasta", "to", "and", "o", "through")

    // Rellenos que pueden ir entre el conector y el segundo elemento ("al 2010", "y los 80").
    private val FILLERS = setOf("el", "la", "los", "las", "de", "del", "anos", "ano", "decada", "the")

    // Palabras que se van con el año cuando es un rango o una cota abierta ("del 2004 a 2010", "desde 2015").
    private val CUES = setOf("de", "del", "desde", "entre", "from", "between", "en", "el", "los", "las", "anos", "ano", "decada", "decadas", "the", "of", "since")
    private val AFTER_CUES = setOf("despues", "after", "since", "desde")
    private val BEFORE_CUES = setOf("antes", "before", "until", "hasta")

    // "las 20 mejores", "los 10 temas": a quantity, not the years 20 / 10.
    private val QUANTITY_NEXT = setOf(
        "canciones", "cancion", "temas", "tema", "songs", "song", "tracks", "exitos", "rolas", "mejores", "mejor", "videos",
        "alabanzas", "coros", "himnos", "cantos",
    )

    private data class Element(val from: Int, val to: Int, val index: Int, val isYear: Boolean)

    private fun twoDigitDecade(digit: Int): Int = if (digit * 10 >= 50) 1900 + digit * 10 else 2000 + digit * 10

    private fun elementAt(folded: List<String>, blocked: BooleanArray, i: Int, chained: Boolean): Element? {
        if (i !in folded.indices || blocked[i]) return null
        val t = folded[i]
        val prev = folded.getOrNull(i - 1)
        YEAR.matchEntire(t)?.let { m ->
            val y = m.value.toInt()
            // "los 2000" / "años 2010" is a decade; any other 4-digit year is a year.
            val decadeLike = y % 10 == 0 && (prev in DECADE_CUES)
            return if (decadeLike) Element(y, y + 9, i, isYear = false) else Element(y, y, i, isYear = true)
        }
        DECADE_S.matchEntire(t)?.let { m ->
            val start = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: twoDigitDecade(m.groupValues[1].toInt())
            return Element(start, start + 9, i, isYear = false)
        }
        if (DECADE_FOUR.matches(t) && (prev in DECADE_CUES)) {
            val y = t.toInt()
            return Element(y, y + 9, i, isYear = false)
        }
        // "70" / "80": a decade only with a cue before it ("años 70", "de los 80") or chained to a decade ("70 y 80").
        DECADE_TWO.matchEntire(t)?.let { m ->
            if (folded.getOrNull(i + 1) in QUANTITY_NEXT) return null
            if (chained || prev in DECADE_CUES) {
                val start = twoDigitDecade(m.groupValues[1].toInt())
                return Element(start, start + 9, i, isYear = false)
            }
        }
        return null
    }

    /**
     * El rango de años que pide la petición ([folded]: sus palabras sin acentos ni mayúsculas, ya normalizadas por
     * [normalize]) o null. [blocked] son los tokens que otra parte ya se llevó (negaciones, "parecido a…").
     *
     *  · "del 2004 a 2010", "entre 2004 y 2010", "2004 hasta 2010"  → 2004–2010
     *  · "años 70 y 80", "de los 70 y los 80", "70s a 90s"        → 1970–1989 / 1970–1999
     *  · "los 2000", "2000s", "años 80"                            → la década (no se quita de la petición)
     *  · "2026"                                                    → ese año
     *  · "desde 2015", "después de 2015"                           → 2015–[currentYear]
     *  · "antes de 2000", "hasta 2010" (un solo año)               → 1950–1999 / 1950–2010
     */
    fun find(folded: List<String>, blocked: BooleanArray, currentYear: Int): Hit? {
        var i = 0
        while (i < folded.size) {
            val first = elementAt(folded, blocked, i, chained = false)
            if (first == null) {
                i++
                continue
            }
            val elements = arrayListOf(first)
            val consumed = linkedSetOf(first.index)
            var j = first.index
            while (true) {
                val conn = j + 1
                if (conn >= folded.size || blocked[conn] || folded[conn] !in CONNECTORS) break
                var k = conn + 1
                val fillers = ArrayList<Int>()
                while (k < folded.size && !blocked[k] && folded[k] in FILLERS && elementAt(folded, blocked, k, true) == null) {
                    fillers += k
                    k++
                }
                val next = elementAt(folded, blocked, k, chained = !elements.last().isYear) ?: break
                // A year chains with a year, a decade with a decade: "80 y 2004" is not one range.
                if (next.isYear != elements.last().isYear) break
                consumed += conn
                consumed += fillers
                consumed += next.index
                elements += next
                j = next.index
            }
            // The cue words right before the first element belong to it.
            val cueIndexes = ArrayList<Int>()
            var c = first.index - 1
            while (c >= 0 && !blocked[c] && folded[c] in CUES + AFTER_CUES + BEFORE_CUES) {
                cueIndexes += c
                c--
            }
            val lowest = elements.minOf { it.from }
            val highest = elements.maxOf { it.to }
            val cueWords = cueIndexes.map { folded[it] }.toSet()

            if (elements.size >= 2) {
                return Hit(Range(lowest, highest), consumed + cueIndexes)
            }
            // One element.
            return when {
                first.isYear -> {
                    val y = first.from
                    when {
                        cueWords.any { it in AFTER_CUES } && y <= currentYear ->
                            Hit(Range(y, currentYear), setOf(first.index) + cueIndexes)
                        // "hasta 2010" / "antes de 2000": an upper bound (inclusive for "hasta", exclusive for "antes").
                        cueWords.any { it in BEFORE_CUES } -> {
                            val upper = if ("antes" in cueWords || "before" in cueWords) y - 1 else y
                            Hit(Range(minOf(1950, upper), upper), setOf(first.index) + cueIndexes)
                        }
                        // A plain year: only the year token leaves the request (what it always did).
                        else -> Hit(Range(y, y), setOf(first.index))
                    }
                }
                // A single decade: kept in the request as it always was.
                else -> Hit(Range(first.from, first.to), emptySet())
            }
        }
        return null
    }

    /**
     * Normaliza la petición cruda antes de partirla en palabras: "2004-2010" → "2004 a 2010", "70's" → "70s",
     * "70-80" → "70s a 80s". Si no, el guion se come el rango dentro de una sola "palabra".
     */
    fun normalize(prompt: String): String =
        prompt
            .replace(Regex("(\\d{4})\\s*[-–—]\\s*(\\d{4})"), "$1 a $2")
            .replace(Regex("\\b(\\d0)'?s\\b"), "$1s")
            .replace(Regex("\\b(\\d0)s?\\s*[-–—/]\\s*(\\d0)s?\\b"), "$1s a $2s")

    // ── qué canciones se quedan ──────────────────────────────────────────────────────────────────────────────────────

    enum class Verdict { IN, OUT, UNKNOWN }

    fun verdict(year: Int?, range: Range): Verdict = when {
        year == null -> Verdict.UNKNOWN
        year in range -> Verdict.IN
        else -> Verdict.OUT
    }

    data class Selection<T>(val kept: List<T>, val inRange: Int, val unknown: Int, val out: Int)

    /** Con tantas canciones seguras, las de año desconocido sobran. */
    const val ENOUGH_VERIFIED = 8

    /**
     * Las de año seguro dentro del rango primero (en su orden); las de año DESCONOCIDO solo rellenan cuando las seguras
     * son menos de [enoughVerified] — y van después, nunca mezcladas por delante; las de año seguro FUERA, nunca. Lo
     * [isProtected] (la canción que nombró) no se juzga. Así una petición de años no se llena en silencio con lo que no
     * se pidió, y tampoco se queda vacía por un año que no se conoce.
     */
    fun <T> select(
        items: List<T>,
        yearOf: (T) -> Int?,
        range: Range,
        isProtected: (T) -> Boolean = { false },
        enoughVerified: Int = ENOUGH_VERIFIED,
    ): Selection<T> {
        val sure = ArrayList<T>()
        val unknown = ArrayList<T>()
        var out = 0
        for (item in items) {
            if (isProtected(item)) {
                sure += item
                continue
            }
            when (verdict(yearOf(item), range)) {
                Verdict.IN -> sure += item
                Verdict.UNKNOWN -> unknown += item
                Verdict.OUT -> out++
            }
        }
        val kept = if (sure.size >= enoughVerified) sure else sure + unknown
        return Selection(kept, inRange = sure.size, unknown = unknown.size, out = out)
    }

    private val COMPILATION_WORDS = setOf(
        "hits", "hit", "exitos", "exito", "greatest", "best", "mejor", "mejores", "recopilatorio", "recopilacion",
        "collection", "coleccion", "anthology", "antologia", "essential", "essentials", "platinum", "gold", "grandes",
        "sencillos", "singles", "mix", "vol", "volumen", "volume", "now", "top", "playlist", "radio",
    )

    /**
     * El año de un álbum solo cuenta si el álbum no es una RECOPILACIÓN: "Éxitos 2015" publicado en 2015 con canciones
     * de 2005 diría 2015 y sacaría de un rango 2004–2010 canciones que sí son de ahí. Esa canción queda de año
     * desconocido (relleno), no fuera.
     */
    fun trustworthyAlbumYear(albumTitle: String?, year: Int?): Int? {
        if (year == null) return null
        val words = MusicRequestIntent.fold(albumTitle.orEmpty()).split(' ')
        return if (words.any { it in COMPILATION_WORDS }) null else year
    }
}
