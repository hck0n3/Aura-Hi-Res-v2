package iad1tya.echo.music.reco

import java.text.Normalizer
import kotlin.random.Random

/**
 * Cómo sigue la cola cuando termina un álbum o una playlist — sin desviarse de género ni de idioma.
 *
 * 🔴 Dueño (2026-10-07, fila 344): *"la cola inteligente no se mantiene: estoy escuchando un estilo, siguen
 * tal vez las primeras tres canciones de acuerdo, después mete otra cosa que nada que ver y después cambia a
 * otro idioma y otro género… me pasó cuando terminó un álbum; verifica si no pasa en las listas también"*.
 *
 * ## Las tres causas (verificadas en MusicService)
 *  1. Si iTunes no conoce el género del álbum (artista de nicho), el perfil queda inactivo y la continuación
 *     se convertía en la RADIO DE LA ÚLTIMA CANCIÓN, que luego se paginaba sin filtro: las primeras eran del
 *     mismo artista (YouTube las pone delante) y después la radio derivaba sola.
 *  2. Al agotarse una ronda, los respaldos sembraban desde la canción que SONABA — que para entonces ya era
 *     una elección de la radio — y cada ronda derivaba de la deriva anterior.
 *  3. Nada comprobaba el idioma: "cristiano" es un solo carril para español e inglés.
 *
 * ## Las reglas
 *  · Una colección se continúa SIEMPRE desde sus propias canciones, ronda a ronda, rotando las semillas
 *    ([rotateSeeds]); nunca se pagina la radio de una sola canción.
 *  · Los respaldos solo siembran desde la canción actual si pertenece a la colección ([fallbackSeed]).
 *  · Idioma por el texto de la propia canción ([TitleLanguage]): si la colección es claramente de un idioma,
 *    un candidato claramente de OTRO idioma cuenta como fuera de contexto. Un título dudoso nunca se toca.
 */
object CollectionContinuation {

    /** A collection (album/playlist), not a single-song radio — the only case these rules apply to. */
    fun isCollection(anchored: Boolean, poolSize: Int): Boolean = !anchored && poolSize > 1

    /**
     * Up to [max] seeds from [candidates] (already in preference order), unused ones first, so each round of
     * the same collection opens new YouTube mixes; when every candidate was used, the cycle starts again.
     */
    fun rotateSeeds(candidates: List<String>, used: Set<String>, max: Int): List<String> {
        val distinct = candidates.distinct()
        val fresh = distinct.filter { it !in used }
        return (fresh + distinct.filter { it in used }).take(max)
    }

    /**
     * The seed for a fallback radio of a finished collection: the playing song only while it is one of the
     * collection's own; otherwise an unused collection track (random among the unused, for variety), or any
     * collection track. Outside a collection this is just [current], as before.
     */
    fun fallbackSeed(
        current: String?,
        collection: List<String>,
        used: Set<String>,
        anchored: Boolean,
        random: Random,
    ): String? {
        if (!isCollection(anchored, collection.size)) return current
        if (current != null && current in collection) return current
        val unused = collection.filter { it !in used }
        return unused.randomOrNull(random) ?: collection.randomOrNull(random) ?: current
    }

    /**
     * The collection's language, or null when it has none clearly: at least [MIN_DECIDED] titles decided
     * and one language holding at least [DOMINANT_SHARE] of them.
     */
    fun dominantLanguage(titles: List<String>): String? {
        val decided = titles.mapNotNull { TitleLanguage.detect(it) }
        if (decided.size < MIN_DECIDED) return null
        val (lang, count) = decided.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return if (count.toDouble() / decided.size >= DOMINANT_SHARE) lang else null
    }

    /** True only when the candidate is CLEARLY in another language than the collection's [dominant]. */
    fun offLanguage(dominant: String?, candidateText: String): Boolean {
        if (dominant == null) return false
        val lang = TitleLanguage.detect(candidateText) ?: return false
        return lang != dominant
    }

    const val MIN_DECIDED = 4
    const val DOMINANT_SHARE = 0.8
}

/**
 * Idioma de una canción por las palabras de su título (y álbum): español, inglés o portugués, o null si no
 * está claro. Solo palabras función y vocabulario muy frecuente de canciones; las etiquetas de versión
 * ("Live", "Remix", "feat.", "En Vivo"…) no cuentan. Decide solo con ventaja clara: al menos dos palabras
 * propias del idioma ganador, o una y ninguna del resto. Pura, sin red.
 */
object TitleLanguage {
    const val ES = "es"
    const val EN = "en"
    const val PT = "pt"

    private val ES_WORDS = setOf(
        "el", "los", "las", "del", "que", "y", "un", "una", "mi", "mis", "tu", "tus", "te", "me", "se", "yo",
        "por", "con", "sin", "es", "eres", "soy", "estoy", "como", "mas", "al", "lo", "le", "su", "sus",
        "todo", "nada", "corazon", "dios", "senor", "jesus", "cristo", "gloria", "quiero", "cuando", "donde",
        "porque", "siempre", "nunca", "hoy", "aqui", "solo", "amor", "vida", "noche", "cielo", "fe", "alma",
        "gracias", "padre", "santo", "espiritu", "alabanza", "adoracion", "contigo", "conmigo", "nosotros",
        "quien", "ella", "esta", "estas", "este", "ese", "esa", "hay", "voy", "ven", "dame", "eso", "muy",
        "tambien", "ahora", "otra", "otro", "bien", "mejor", "nuestro", "nuestra", "rey", "mundo", "pueblo",
    )
    private val EN_WORDS = setOf(
        "the", "of", "and", "my", "your", "you", "i", "is", "it", "to", "be", "love", "heart", "god",
        "lord", "jesus", "christ", "with", "without", "for", "are", "am", "all", "we", "our", "this", "that",
        "what", "when", "where", "never", "always", "now", "here", "only", "praise", "worship", "holy",
        "grace", "king", "night", "life", "soul", "way", "come", "let", "me", "on", "in", "be", "will",
        "can", "do", "don't", "dont", "i'm", "im", "you're", "it's", "know", "feel", "baby", "just", "like",
        "into", "from", "who", "how", "there", "they", "him", "his", "her", "she", "he", "up", "down", "out",
        "over", "every", "everything", "nothing", "forever", "again", "yes", "oh",
    )
    private val PT_WORDS = setOf(
        "o", "os", "as", "do", "da", "dos", "das", "nao", "voce", "meu", "minha", "teu", "tua", "coracao",
        "deus", "senhor", "com", "sem", "em", "eu", "ele", "ela", "nos", "graca", "louvor", "vou", "pra",
        "isso", "esse", "essa", "tudo", "nada", "sempre", "nunca", "hoje", "aqui", "agora", "ceu", "fe",
        "pai", "santo", "espirito", "adoracao", "obrigado", "muito", "tambem", "outra", "outro",
        // Shared with Spanish on purpose: listed in both, they count for neither.
        "que", "como", "por", "te", "se", "me", "vida", "amor", "mais", "quando", "porque", "solo",
    )

    /** Version tags and credits that say nothing about the song's own language. */
    private val NOISE = setOf(
        "live", "remix", "remastered", "remaster", "version", "edit", "mix", "feat", "ft", "featuring",
        "official", "video", "audio", "lyrics", "lyric", "acoustic", "acustico", "en", "vivo", "ao", "vivo",
        "instrumental", "radio", "extended", "original", "deluxe", "single", "ep",
    )

    fun detect(text: String): String? {
        val words = normalize(text)
            .split(Regex("[^a-z']+"))
            .filter { it.isNotBlank() && it !in NOISE }
        if (words.isEmpty()) return null
        // Count only the words that belong to ONE language — "amor", "nada", "jesus" say nothing alone.
        var es = 0
        var en = 0
        var pt = 0
        for (w in words) {
            val inEs = w in ES_WORDS
            val inEn = w in EN_WORDS
            val inPt = w in PT_WORDS
            val hits = (if (inEs) 1 else 0) + (if (inEn) 1 else 0) + (if (inPt) 1 else 0)
            if (hits != 1) continue
            when {
                inEs -> es++
                inEn -> en++
                else -> pt++
            }
        }
        val ranked = listOf(ES to es, EN to en, PT to pt).sortedByDescending { it.second }
        val (best, top) = ranked[0]
        val second = ranked[1].second
        return when {
            top >= 2 && top >= second * 2 -> best
            top == 1 && second == 0 -> best
            else -> null
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            // Credits and version tags in brackets: "(feat. X)", "[Live]" — the artist names inside are noise.
            .replace(Regex("\\((?:feat|ft|with|con)[^)]*\\)"), " ")
            .replace(Regex("\\[(?:feat|ft|with|con)[^]]*]"), " ")
}
