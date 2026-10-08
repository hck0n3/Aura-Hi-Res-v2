package iad1tya.echo.music.reco

/**
 * Fila 354 — la cola inteligente sigue con el MISMO estilo concreto y el mismo idioma que lo que escuchabas,
 * en cualquier género (ver [MusicStyle] para la queja del dueño y la causa).
 *
 * ## Las reglas
 *  · El OBJETIVO ([Target]) es el estilo de la canción de la que sale la cola (su título/álbum, lo aprendido de
 *    su artista o su género de iTunes si es concreto) o, al terminar un álbum/lista, los estilos de la
 *    colección (el nombre de la lista manda: "Cumbias cristianas" = cumbia). Más el idioma, si está claro.
 *  · Cada candidata recibe un [Verdict]:
 *     - MATCH: su propio texto dice el mismo estilo; o la trajo la búsqueda de ese estilo; o es del mismo
 *       artista/colección; o su artista es conocido de ese estilo.
 *     - OFF: su texto, su artista conocido o su género de iTunes dicen OTRO estilo (o, sin estilo concreto,
 *       otra familia); o está claramente en otro idioma.
 *     - UNKNOWN: no se sabe.
 *  · OFF no suena nunca. Con al menos [MIN_MATCHES] MATCH, tampoco suenan los UNKNOWN: así es como una salsa sin
 *    la palabra "salsa" deja de colarse detrás de una cumbia. Si hay pocas MATCH, quien llama pide más a YouTube
 *    buscando el estilo ([searchQuery]) antes de admitir los UNKNOWN.
 *  · Sin objetivo (estilo e idioma desconocidos) no se toca nada: la cola funciona exactamente como antes.
 */
object StyleContinuity {

    const val MIN_MATCHES = 3

    data class Target(
        /** Allowed concrete styles; null = unknown style (only the language is judged). */
        val styles: Set<String>?,
        /** TitleLanguage code, or null when unclear. */
        val language: String?,
        /** Christian context: searches ask for the Christian version of the style. */
        val christian: Boolean,
    ) {
        val families: Set<String> = styles.orEmpty().mapNotNull { MusicStyle.familyOf(it) }.toSet()
        val active: Boolean get() = !styles.isNullOrEmpty() || language != null
    }

    data class Candidate(
        /** [MusicStyle.fromText] of its own title + album. */
        val textStyle: String?,
        /** [ArtistStyleMemory.styleOf] its primary artist. */
        val learnedStyle: String?,
        /** [MusicStyle.fromGenre] of its primary artist's iTunes genre. */
        val genre: MusicStyle.GenreInfo,
        /** [TitleLanguage.detect] of its own title + album. */
        val language: String?,
        /** Same artist as the anchor song / one of the finished collection's artists. */
        val ownArtist: Boolean,
        /** Brought by the search for the target style itself. */
        val fromSearch: Boolean,
    )

    enum class Verdict { MATCH, UNKNOWN, OFF }

    fun verdict(target: Target, c: Candidate): Verdict {
        if (!target.active) return Verdict.MATCH
        val styles = target.styles?.takeIf { it.isNotEmpty() }
        // The track's own words are the strongest evidence there is — they decide even over the artist.
        if (styles != null && c.textStyle != null) {
            return if (c.textStyle in styles) Verdict.MATCH else Verdict.OFF
        }
        if (target.language != null && c.language != null && c.language != target.language) return Verdict.OFF
        if (styles == null) {
            // Language-only target: a known same language is a match, unknown stays unknown.
            return if (c.language != null) Verdict.MATCH else Verdict.UNKNOWN
        }
        if (c.ownArtist || c.fromSearch) return Verdict.MATCH
        val known = c.learnedStyle ?: c.genre.style
        if (known != null) return if (known in styles) Verdict.MATCH else Verdict.OFF
        val family = c.genre.family
        if (family != null && family !in target.families) return Verdict.OFF
        return Verdict.UNKNOWN
    }

    /**
     * What plays, in the caller's order: MATCH only when there are at least [minMatches]; otherwise MATCH then
     * UNKNOWN. OFF never. Empty when nothing fits — the caller then tries its next source.
     */
    fun <T> select(items: List<T>, verdicts: List<Verdict>, minMatches: Int = MIN_MATCHES): List<T> {
        require(items.size == verdicts.size)
        val matches = items.filterIndexed { i, _ -> verdicts[i] == Verdict.MATCH }
        if (matches.size >= minMatches) return matches
        return matches + items.filterIndexed { i, _ -> verdicts[i] == Verdict.UNKNOWN }
    }

    /**
     * The style of one track for building a target: its own words, else its artist's learned style, else a
     * concrete iTunes genre.
     */
    fun trackStyle(ownText: String?, learnedStyle: String?, genre: String?): String? =
        MusicStyle.fromText(ownText) ?: learnedStyle ?: MusicStyle.fromGenre(genre).style

    /**
     * The styles a finished collection keeps. The collection's NAME first ("Cumbias cristianas"); else every
     * style holding at least [COLLECTION_STYLE_FLOOR] of the tracks whose style is known, with at least
     * [COLLECTION_MIN_KNOWN] known — so a mixed playlist keeps ALL its styles, not only the largest.
     */
    fun collectionStyles(trackStyles: List<String?>, nameStyle: String?): Set<String>? {
        if (nameStyle != null) return setOf(nameStyle)
        val known = trackStyles.filterNotNull()
        if (known.size < COLLECTION_MIN_KNOWN) return null
        return known.groupingBy { it }.eachCount()
            .filter { (_, n) -> n.toDouble() / known.size >= COLLECTION_STYLE_FLOOR }
            .keys
            .takeIf { it.isNotEmpty() }
    }

    const val COLLECTION_MIN_KNOWN = 3
    const val COLLECTION_STYLE_FLOOR = 0.15

    /**
     * What to ask YouTube for more songs of [style] in this context: "cumbia cristiana", "rock en español",
     * "worship", "corridos"… Null when there is no style.
     */
    fun searchQuery(style: String?, language: String?, christian: Boolean): String? {
        val s = MusicStyle.style(style) ?: return null
        if (s.id == MusicStyle.WORSHIP) {
            return when (language) {
                TitleLanguage.EN -> "worship songs"
                TitleLanguage.PT -> "louvor e adoração"
                else -> s.search
            }
        }
        val parts = mutableListOf(s.search)
        if (christian) {
            parts += when (language) {
                TitleLanguage.EN -> "christian"
                TitleLanguage.PT -> "gospel"
                else -> if (s.feminine) "cristiana" else "cristiano"
            }
        }
        if (language == TitleLanguage.ES && !s.latinNative) parts += "en español"
        return parts.joinToString(" ")
    }
}
