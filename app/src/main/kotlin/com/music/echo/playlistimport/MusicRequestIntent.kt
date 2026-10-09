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
    ) {
        /** Something in the request can be CHECKED on each song (style, faith, language or an exclusion). */
        val checkable: Boolean
            get() = styles.isNotEmpty() || christian || language != null ||
                excludedStyles.isNotEmpty() || excludedTerms.isNotEmpty()
    }

    private val NEGATIONS = setOf("sin", "excepto", "menos", "without", "except", "salvo")
    private val NEGATION_PAIRS = setOf("nada de", "no quiero", "que no sea", "que no sean", "no me pongas")
    private val STOP = setOf(
        "y", "pero", "con", "para", "en", "o", "que", "porque", "mas", "solo", "and", "but", "with", "for",
    )
    private val LIKE_TRIGGERS = listOf(
        "que suene como", "que suenen como", "parecido a", "parecida a", "parecidos a", "parecidas a",
        "similar a", "similares a", "al estilo de", "estilo de", "como las de", "como los de", "tipo",
        "sounds like", "similar to", "like",
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
        "retro", "vieja escuela", "de siempre", "del recuerdo",
    )
    private val NEW_WORDS = listOf(
        "nuevas", "nuevos", "recientes", "estrenos", "lo ultimo", "lo mas nuevo", "actuales", "de ahora",
        "new", "latest",
    )
    private val BEST_WORDS = listOf(
        "lo mejor", "las mejores", "los mejores", "mejores", "exitos", "top", "hits", "mas escuchadas",
        "mas populares", "populares", "best",
    )
    private val YEAR = Regex("^(19[5-9]\\d|20[0-4]\\d)$")
    private val NUMBER = Regex("^\\d{1,3}$")

    fun parse(prompt: String): Intent {
        val tokens = prompt.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
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
            val isNeg = pairLen != null || folded[i] in NEGATIONS
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
                // Another negation starts its own item ("sin reggaeton excepto Bad Bunny").
                if (w in NEGATIONS || NEGATION_PAIRS.any { phraseAt(j, it) }) break
                if (w in STOP && item.isNotEmpty()) break
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
            if (NUMBER.matches(w) && count == null) {
                val n = w.toInt()
                val next = folded.getOrNull(k + 1)
                val next2 = folded.getOrNull(k + 2)
                val prev = folded.getOrNull(k - 1)
                val countLike = n in 1..200 && (
                    next in COUNT_NOUNS || next2 in COUNT_NOUNS || prev == "top" ||
                        (next == "mejores" || next == "mejor")
                    )
                if (countLike) {
                    count = n
                    drop[k] = true
                }
            }
        }

        val keptTokens = tokens.filterIndexed { k, _ -> !drop[k] }
        val clean = keptTokens.joinToString(" ").trim()
        val cleanFolded = " ${fold(clean)} "
        val fullFolded = " ${fold(prompt)} "

        val styles = MusicStyle.stylesIn(clean) - excludedStyles
        val christian = CHRISTIAN_WORDS.any { cleanFolded.contains(" $it ") } ||
            GenreLane.laneOf(clean) == GenreLane.CHRISTIAN
        val language = LANGUAGE_WORDS.firstOrNull { (w, _) -> cleanFolded.contains(" $w ") }?.second
            ?: when {
                cleanFolded.contains(" ingles ") || cleanFolded.contains(" english ") -> TitleLanguage.EN
                cleanFolded.contains(" espanol ") || cleanFolded.contains(" spanish ") -> TitleLanguage.ES
                cleanFolded.contains(" portugues ") -> TitleLanguage.PT
                else -> null
            }
        val era = when {
            OLD_WORDS.any { cleanFolded.contains(" $it ") } -> Era.OLD
            NEW_WORDS.any { cleanFolded.contains(" $it ") } -> Era.NEW
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
        )
    }

    /**
     * The words to search for one requested [style] in this intent: the style as the queue searches it
     * ([iad1tya.echo.music.reco.StyleContinuity.searchQuery] — "merengue cristiano", "rock en español") plus the
     * year, or "éxitos"/"clásicos"/"nuevo" when that is what was asked.
     */
    fun styleQuery(intent: Intent, style: String): String? {
        val base = iad1tya.echo.music.reco.StyleContinuity.searchQuery(style, intent.language, intent.christian)
            ?: return null
        val suffix = when {
            intent.year != null -> intent.year.toString()
            intent.era == Era.OLD -> "clasicos"
            intent.era == Era.NEW -> "nuevo"
            intent.best -> "exitos"
            else -> null
        }
        return listOfNotNull(base, suffix).joinToString(" ")
    }

    /** True when a playlist [title] proves it is [style] (and Christian, when that was asked). Pure. */
    fun playlistMatches(title: String, style: String, christian: Boolean): Boolean {
        if (style !in MusicStyle.stylesIn(title)) return false
        if (!christian) return true
        val t = " ${fold(title)} "
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
