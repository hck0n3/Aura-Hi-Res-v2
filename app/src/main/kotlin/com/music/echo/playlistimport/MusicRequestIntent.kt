package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.reco.GenreLane
import iad1tya.echo.music.reco.MusicStyle
import iad1tya.echo.music.reco.TitleLanguage
import java.text.Normalizer

/**
 * Fila 361 — LO QUE SE PIDE, ENTENDIDO: el intérprete de lenguaje natural de "Pedir música".
 *
 * 🔴 Dueño (2026-10-09): *"pedir música no está entendiendo bien el lenguaje natural; aparte te pedí que la
 * mejoraras"* — con sus casos: *"le pedí reggaeton cristiano y puso reggaeton cristiano de artistas que no son
 * conocidos, generados por IA; luego le pedí lo mejor del merengue cristiano 2026 y me sale con canciones de
 * Jesús Adrián Romero"*.
 *
 * Antes, la búsqueda solo reconocía unas cuarenta palabras por SUBCADENA ("trap" activaba también "rap",
 * "popular" era pop, "pasado" era música triste), no sabía qué es "sin reggaeton", leía "las 20 mejores" como
 * los años 20 y no conocía cantidades, años concretos ni "parecido a X". Esto lee la petición entera con el
 * MISMO vocabulario que la cola inteligente ([MusicStyle], 56 estilos, palabra completa) y devuelve qué se
 * pidió de verdad:
 *
 *  · [Intent.styles] — los estilos concretos ("merengue", "reggaeton", "cumbia"…), varios si se nombran varios;
 *  · [Intent.christian] — temática cristiana (cristiano/a, alabanza, adoración, gospel, worship, iglesia…);
 *  · [Intent.language] — idioma pedido ("en inglés", "en español", "en portugués");
 *  · [Intent.year], [Intent.era] y [Intent.best] — "2026", "viejitas/clásicos", "nuevas/estrenos", "lo mejor";
 *  · [Intent.count] — "pon 50 canciones de salsa";
 *  · [Intent.excludedStyles] / [Intent.excludedTerms] — "sin reggaeton", "nada de trap", "excepto Bad Bunny";
 *  · [Intent.likeArtist] — "parecido a Redimi2", "al estilo de Juan Luis Guerra";
 *  · [Intent.cleanPrompt] — la petición SIN negaciones, cantidades, año ni "parecido a…", que es lo que se le
 *    da al buscador y al resto de la escalera (antes "rock sin reggaeton" se buscaba tal cual y sonaba reggaeton).
 *
 * Puro, sin red. Probado en `MusicRequestIntentTest`.
 */
object MusicRequestIntent {

    enum class Era { OLD, NEW }

    data class Intent(
        val styles: Set<String>,
        val excludedStyles: Set<String>,
        val excludedTerms: List<String>,
        val christian: Boolean,
        val language: String?,
        val year: Int?,
        val era: Era?,
        val best: Boolean,
        val count: Int?,
        val likeArtist: String?,
        val cleanPrompt: String,
        /** Row 435: the years asked for ("del 2004 a 2010", "años 70 y 80", "2026"), enforced on each song. */
        val eraRange: MusicRequestEra.Range? = null,
    ) {
        /** Something in the request can be CHECKED on each song (style, faith, language or an exclusion). */
        val checkable: Boolean
            get() = styles.isNotEmpty() || christian || language != null ||
                excludedStyles.isNotEmpty() || excludedTerms.isNotEmpty() || eraRange != null
    }

    private val NEGATIONS = setOf("sin", "excepto", "menos", "without", "except", "salvo")
    private val FRAMING = setOf("musica", "canciones", "cancion", "temas", "tema", "songs", "music")
    private val NEGATIONS_EXTRA = setOf("evita", "evitar", "quita", "quitando", "ignora")
    private val NEGATION_PAIRS = setOf(
        "nada de", "no quiero", "que no sea", "que no sean", "no me pongas",
        // Dueño (2026-10-19): el lenguaje natural dice lo mismo de muchas maneras.
        "que no tenga", "que no tengan", "que no lleve", "que no lleven", "no incluyas", "no pongas", "no metas",
        "sin nada de", "no quiero nada de", "nada que sea",
    )
    private val STOP = setOf(
        "y", "pero", "con", "para", "en", "o", "que", "porque", "mas", "solo", "and", "but", "with", "for",
    )
    private val LIKE_TRIGGERS = listOf(
        "que suene como", "que suenen como", "parecido a", "parecida a", "parecidos a", "parecidas a",
        "similar a", "similares a", "al estilo de", "estilo de", "como las de", "como los de", "tipo",
        "sounds like", "similar to", "like",
        // Dueño (2026-10-19): «en la onda de X», «para fans de X», «inspirado en X».
        "en la onda de", "de la onda de", "inspirado en", "inspirada en", "para fans de", "fans de", "algo como",
        "igual que", "como si fuera", "estilo", "onda",
    )
    private val COUNT_NOUNS = setOf(
        "canciones", "cancion", "temas", "tema", "songs", "song", "tracks", "exitos", "rolas", "cantos",
        "alabanzas", "coros", "himnos", "videos",
    )
    private val CHRISTIAN_WORDS = setOf(
        "cristiano", "cristiana", "cristianos", "cristianas", "christian", "gospel", "alabanza", "alabanzas",
        "adoracion", "worship", "evangelico", "evangelica", "iglesia", "congregacional", "louvor", "adoracao",
        "catolico", "catolica", "coros", "coritos",
    )
    private val LANGUAGE_WORDS = listOf(
        "en espanol" to TitleLanguage.ES, "en castellano" to TitleLanguage.ES, "in spanish" to TitleLanguage.ES,
        "en ingles" to TitleLanguage.EN, "in english" to TitleLanguage.EN, "anglo" to TitleLanguage.EN,
        "en portugues" to TitleLanguage.PT, "em portugues" to TitleLanguage.PT, "brasilena" to TitleLanguage.PT,
        "brasileno" to TitleLanguage.PT, "brasileira" to TitleLanguage.PT,
    )
    private val OLD_WORDS = listOf(
        "viejitas", "viejitos", "antiguas", "antiguos", "clasicas", "clasicos", "de antes", "old school",
        "retro", "vieja escuela", "de siempre", "del recuerdo", "oldies", "golden oldies", "de colecciones",
    )
    private val NEW_WORDS = listOf(
        "nuevas", "nuevos", "recientes", "estrenos", "lo ultimo", "lo mas nuevo", "actuales", "de ahora",
        "latest", "novedades", "nuevos lanzamientos", "new releases", "new music", "new songs", "new hits",
        "del momento", "lo recien salido",
    )
    private val REQUEST_VERBS = setOf(
        "pon", "ponme", "dame", "quiero", "play", "genera", "generame", "crea", "creame", "hazme", "armame", "arma", "pasame",
        "necesito", "busca", "buscame", "give", "show",
    )
    private val NUMBER_WORDS = mapOf(
        "diez" to 10, "quince" to 15, "veinte" to 20, "veinticinco" to 25, "treinta" to 30, "cuarenta" to 40,
        "cincuenta" to 50, "cien" to 100, "ten" to 10, "twenty" to 20, "thirty" to 30, "fifty" to 50,
    )
    private val BEST_WORDS = listOf(
        "lo mejor", "las mejores", "los mejores", "mejores", "exitos", "top", "hits", "mas escuchadas",
        "mas populares", "populares", "best", "de todos los tiempos", "of all time", "de la historia",
    )
    private val YEAR = Regex("^(19[5-9]\\d|20[0-4]\\d)$")
    private val NUMBER = Regex("^\\d{1,3}$")

    /** true when the words at [from] (skipping an article) open a style: «el reguetón», «trap latino». */
    private fun startsStyleAt(folded: List<String>, from: Int): Boolean {
        var i = from
        if (folded.getOrNull(i) in setOf("el", "la", "los", "las", "un", "una")) i++
        val one = folded.getOrNull(i) ?: return false
        val two = folded.getOrNull(i + 1)?.let { "$one $it" }
        return MusicStyle.stylesIn(one).isNotEmpty() || (two != null && MusicStyle.stylesIn(two).isNotEmpty())
    }

    fun parse(prompt: String, currentYear: Int = java.time.Year.now().value): Intent {
        // "2004-2010" and "70's" would otherwise be swallowed whole by the whitespace split (row 435).
        val tokens = MusicRequestEra.normalize(prompt).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val folded = tokens.map { fold(it) }
        val drop = BooleanArray(tokens.size)
        val excludedStyles = LinkedHashSet<String>()
        val excludedTerms = ArrayList<String>()

        fun phraseAt(i: Int, phrase: String): Boolean {
            val words = phrase.split(' ')
            if (i + words.size > folded.size) return false
            return words.indices.all { folded[i + it] == words[it] }
        }

        // ── Negations: "sin reggaeton", "nada de trap ni dembow", "excepto Bad Bunny" ──
        var i = 0
        while (i < folded.size) {
            val pairLen = NEGATION_PAIRS.firstOrNull { phraseAt(i, it) }?.split(' ')?.size
            val isNeg = pairLen != null || folded[i] in NEGATIONS || folded[i] in NEGATIONS_EXTRA
            if (!isNeg) {
                i++
                continue
            }
            val start = i
            var j = i + (pairLen ?: 1)
            val item = ArrayList<String>()
            fun flush() {
                if (item.isEmpty()) return
                val text = item.joinToString(" ")
                val styles = MusicStyle.stylesIn(text)
                if (styles.isNotEmpty()) excludedStyles += styles else excludedTerms += text
                item.clear()
            }
            while (j < folded.size && item.size < 4) {
                val w = folded[j]
                if (w.isEmpty()) break
                if (w == "ni" || w == "nor" || w == "or") {
                    flush()
                    j++
                    continue
                }
                // «evita el trap y el reguetón»: una «y» entre dos estilos sigue la lista de lo que NO se quiere.
                if ((w == "y" || w == "e" || w == "and") && item.isNotEmpty() && startsStyleAt(folded, j + 1)) {
                    flush()
                    j++
                    continue
                }
                // Another negation starts its own item ("sin reggaeton excepto Bad Bunny").
                if (w in NEGATIONS || w in NEGATIONS_EXTRA || NEGATION_PAIRS.any { phraseAt(j, it) }) break
                if (w in STOP && item.isNotEmpty()) break
                // «evita el trap música urbana»: la palabra que enmarca la petición cierra lo que NO se quiere.
                if (w in FRAMING && item.isNotEmpty()) break
                if (w == "de" || w == "el" || w == "la" || w == "los" || w == "las") {
                    if (item.isEmpty()) {
                        j++
                        continue
                    }
                }
                item += w
                j++
                if (tokens[j - 1].endsWith(",") || tokens[j - 1].endsWith(".")) break
            }
            flush()
            for (k in start until j) drop[k] = true
            i = j
        }

        // ── "parecido a X", "al estilo de X" ──
        var likeArtist: String? = null
        i = 0
        while (i < folded.size && likeArtist == null) {
            val trigger = LIKE_TRIGGERS.firstOrNull { !drop[i] && phraseAt(i, it) }
            if (trigger == null) {
                i++
                continue
            }
            val tLen = trigger.split(' ').size
            var j = i + tLen
            val words = ArrayList<String>()
            while (j < tokens.size && words.size < 4 && !drop[j]) {
                if (folded[j] in STOP && words.isNotEmpty()) break
                words += tokens[j].trim(',', '.', ';', '!', '?')
                j++
                if (tokens[j - 1].endsWith(",")) break
            }
            // "tipo"/"like" before a style word is not an artist ("tipo cumbia" = cumbia).
            if (words.isNotEmpty() && MusicStyle.stylesIn(words.joinToString(" ")).isEmpty()) {
                likeArtist = words.joinToString(" ")
                for (k in i until j) drop[k] = true
            }
            i = j
        }

        // ── Count and year ──
        var count: Int? = null
        var year: Int? = null
        // Row 435: the years asked for, as a RANGE (the loop below only ever understood one year).
        val eraHit = MusicRequestEra.find(folded, drop, currentYear)
        if (eraHit != null) {
            for (k in eraHit.dropIndexes) drop[k] = true
            if (eraHit.range.singleYear) year = eraHit.range.from
        }
        for (k in folded.indices) {
            if (drop[k]) continue
            val w = folded[k]
            if (YEAR.matches(w)) {
                // "los 2000" / "2000s" stay decades (MusicRequestQuery); any other 4-digit year is a year.
                val prev = folded.getOrNull(k - 1)
                val decadeLike = w.endsWith("0") && (prev in setOf("los", "las", "the", "anos", "ano") ||
                    tokens[k].lowercase().endsWith("s"))
                if (!decadeLike) {
                    year = w.toInt()
                    drop[k] = true
                }
                continue
            }
            val number = if (NUMBER.matches(w)) w.toInt() else NUMBER_WORDS[w]
            if (number != null && count == null) {
                val n = number
                val next = folded.getOrNull(k + 1)
                val next2 = folded.getOrNull(k + 2)
                val prev = folded.getOrNull(k - 1)
                // «pon 30», «dame 20 de salsa», «30 de cumbia»: un número tras un verbo de petición, o seguido de «de»
                // (salvo «los 80 de…», que es una década), también es una cantidad. Dueño (2026-10-19): «rock en español
                // 2000-2010 pon 30» dejaba «pon 30» pegado a la búsqueda.
                val afterVerb = prev in REQUEST_VERBS
                val beforeDe = next == "de" && prev !in setOf("los", "las", "anos", "ano", "the", "de") && n >= 5
                val countLike = n in 1..200 && (
                    next in COUNT_NOUNS || next2 in COUNT_NOUNS || prev == "top" ||
                        (next == "mejores" || next == "mejor") || afterVerb || beforeDe
                    )
                if (countLike) {
                    count = n
                    drop[k] = true
                    if (afterVerb) drop[k - 1] = true
                }
            }
        }

        val keptTokens = tokens.filterIndexed { k, _ -> !drop[k] }
        val clean = keptTokens.joinToString(" ").trim()
        val cleanFolded = " ${fold(clean)} "
        val fullFolded = " ${fold(prompt)} "

        val concrete = MusicStyle.stylesIn(clean)
        // «música urbana sin reguetón ni trap» pide la familia entera menos lo excluido; «salsa tropical» sigue siendo salsa.
        val styles = (concrete.ifEmpty { MusicStyle.umbrellaStylesIn(clean) }) - excludedStyles
        val christian = CHRISTIAN_WORDS.any { cleanFolded.contains(" $it ") } ||
            GenreLane.laneOf(clean) == GenreLane.CHRISTIAN
        val language = LANGUAGE_WORDS.firstOrNull { (w, _) -> cleanFolded.contains(" $w ") }?.second
            ?: when {
                cleanFolded.contains(" ingles ") || cleanFolded.contains(" english ") -> TitleLanguage.EN
                cleanFolded.contains(" espanol ") || cleanFolded.contains(" spanish ") -> TitleLanguage.ES
                cleanFolded.contains(" portugues ") -> TitleLanguage.PT
                else -> null
            }
        // The era words are read AFTER the style names are taken out: «new jack swing» and «new wave» are styles, not «new».
        val eraFolded = " ${MusicStyle.withoutStyles(clean)} "
        val era = when {
            OLD_WORDS.any { eraFolded.contains(" $it ") } -> Era.OLD
            NEW_WORDS.any { eraFolded.contains(" $it ") } -> Era.NEW
            else -> null
        }
        val best = BEST_WORDS.any { fullFolded.contains(" $it ") }

        return Intent(
            styles = styles,
            excludedStyles = excludedStyles,
            excludedTerms = excludedTerms,
            christian = christian,
            language = language,
            year = year,
            era = era,
            best = best,
            count = count,
            likeArtist = likeArtist,
            cleanPrompt = clean.ifBlank { prompt.trim() },
            eraRange = eraHit?.range,
        )
    }

    /**
     * The words to search for one requested [style] in this intent: the style as the queue searches it
     * ([iad1tya.echo.music.reco.StyleContinuity.searchQuery] — "merengue cristiano", "rock en español") plus the
     * year, or "éxitos"/"clásicos"/"nuevo" when that is what was asked.
     */
    fun styleQuery(intent: Intent, style: String, qualifiers: List<String> = emptyList()): String? {
        // Row 462: «cumbia villera cristiana argentina» used to ask for plain «cumbia cristiana», the same as «cumbia
        // colombiana cristiana» — the sub-style and the country go into the query too (one search word per country).
        val words = qualifiers.map { MusicRequestRegion.searchWord(it) }.distinct()
        val base = iad1tya.echo.music.reco.StyleContinuity.searchQuery(style, intent.language, intent.christian, words)
            ?: return null
        val suffix = when {
            intent.eraRange != null -> intent.eraRange.searchWord()
            intent.year != null -> intent.year.toString()
            intent.era == Era.OLD -> "clasicos"
            intent.era == Era.NEW -> "nuevo"
            intent.best -> "exitos"
            else -> null
        }
        return listOfNotNull(base, suffix).joinToString(" ")
    }

    /** True when a playlist [title] proves it is [style] (and Christian, when that was asked). Pure. */
    fun playlistMatches(
        title: String,
        style: String,
        christian: Boolean,
        /** Row 462: the sub-style / country the request named; the title must prove EVERY group (any word of a group). */
        qualifierGroups: List<List<String>> = emptyList(),
    ): Boolean {
        if (style !in MusicStyle.stylesIn(title)) return false
        val t = " ${fold(title)} "
        if (qualifierGroups.any { group -> group.none { word -> t.contains(" $word ") } }) return false
        if (!christian) return true
        return CHRISTIAN_WORDS.any { t.contains(" $it ") } || GenreLane.laneOf(title) == GenreLane.CHRISTIAN
    }

    /** True when [text] (a song's title/album/artists) contains one of the excluded words. Pure. */
    fun mentionsExcluded(intent: Intent, text: String): Boolean {
        if (intent.excludedTerms.isEmpty()) return false
        val t = " ${fold(text)} "
        return intent.excludedTerms.any { term -> t.contains(" $term ") }
    }

    internal fun fold(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
