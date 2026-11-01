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
        /**
         * Row 435: the years the request asked for ("del 2004 a 2010", "años 70 y 80"), or null. A candidate whose
         * year is KNOWN and outside it is OFF — the queue that continues a "Pedir música" request used to drift to
         * any year once the generated list ended (owner 2026-10-18).
         */
        val era: iad1tya.echo.music.playlistimport.MusicRequestEra.Range? = null,
    ) {
        val families: Set<String> = styles.orEmpty().mapNotNull { MusicStyle.familyOf(it) }.toSet()
        /** Row 503: [families] plus the neighbourhood the queue may widen into ([MusicStyle.neighbourFamilies]). */
        val neighbourFamilies: Set<String> get() = MusicStyle.neighbourFamilies(styles)
        val active: Boolean get() = !styles.isNullOrEmpty() || language != null || era != null
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
        /** Brought by the search for the target style itself (or a playlist of that style). */
        val fromSearch: Boolean,
        /** Fila 356 — the user skipped this artist (or this style) quickly in this queue. */
        val rejected: Boolean = false,
        /** Row 435: the release year of the candidate's album when it is known and trustworthy, else null. */
        val year: Int? = null,
    )

    enum class Verdict { MATCH, UNKNOWN, OFF }

    /**
     * Row 524: WHICH evidence decided [verdict] for [c] — a label for the diagnostic log only (counts, never a name). Mirrors the
     * order of [verdict]: the track's own words, then the context's own artist, then a search for the style, then what is known
     * of the artist (learned in this queue, or the genre tags), then only the family. `known` is the genre tag alone: the
     * weakest proof of a style, and the one worth looking at when a queue «changes genre».
     */
    fun basis(target: Target, c: Candidate): String = when {
        !target.active -> "inactive"
        c.rejected -> "rejected"
        c.textStyle != null && !target.styles.isNullOrEmpty() -> "text"
        c.ownArtist -> "own"
        c.fromSearch -> "search"
        c.learnedStyle != null -> "learned"
        c.genre.style != null -> "tag"
        else -> "family"
    }

    /** True when [style] is the parent of, or a style related to, one of the requested [styles] (row 434). */
    private fun isParentOf(style: String, styles: Set<String>): Boolean =
        styles.any { requested -> MusicStyle.style(requested)?.let { it.parent == style || style in it.related } == true }

    fun verdict(target: Target, c: Candidate): Verdict {
        if (!target.active) return Verdict.MATCH
        if (c.rejected) return Verdict.OFF
        // Row 435: a KNOWN year outside the requested years is decisive; an unknown one says nothing.
        if (target.era != null && c.year != null && c.year !in target.era.from..target.era.to) return Verdict.OFF
        val styles = target.styles?.takeIf { it.isNotEmpty() }
        // An era-only request ("música del 2004 al 2010"): nothing else to judge.
        if (styles == null && target.language == null) return Verdict.MATCH
        // The track's own words are the strongest evidence there is — they decide even over the artist.
        // Row 434: ...except that a PARENT style ("corridos" for a "corridos tumbados" request) is not proof either way.
        if (styles != null && c.textStyle != null) {
            return when {
                c.textStyle in styles -> Verdict.MATCH
                isParentOf(c.textStyle, styles) -> Verdict.UNKNOWN
                else -> Verdict.OFF
            }
        }
        if (target.language != null && c.language != null && c.language != target.language) return Verdict.OFF
        if (styles == null) {
            // Language-only target: a known same language is a match, unknown stays unknown.
            return if (c.language != null) Verdict.MATCH else Verdict.UNKNOWN
        }
        if (c.ownArtist) return Verdict.MATCH
        val known = c.learnedStyle ?: c.genre.style
        // Row 440 (owner 2026-10-18, smart queue): a song that arrives from a search or a community playlist of the style
        // was trusted whole — a "drum and bass" playlist added its 100 songs, intruders included. What we KNOW about its
        // artist now counts first when it plainly says another style; a parent/related style or no knowledge stays a match.
        if (c.fromSearch) {
            return if (known != null && known !in styles && !isParentOf(known, styles)) Verdict.OFF else Verdict.MATCH
        }
        if (known != null) {
            return when {
                known in styles -> Verdict.MATCH
                isParentOf(known, styles) -> Verdict.UNKNOWN
                else -> Verdict.OFF
            }
        }
        val family = c.genre.family
        if (family != null && family !in target.families) return Verdict.OFF
        return Verdict.UNKNOWN
    }

    /**
     * What plays, in the caller's order: MATCH only when there are at least [minMatches]; otherwise MATCH then
     * UNKNOWN. OFF never. Empty when nothing fits — the caller then tries its next source.
     */
    fun <T> select(
        items: List<T>,
        verdicts: List<Verdict>,
        minMatches: Int = MIN_MATCHES,
        /**
         * The number of MATCH that is enough to leave the UNKNOWN out (never more than [minMatches]). Row 430 (owner
         * 2026-10-18: *«escuchaba rock clásico de los 70 y 80… me puso otra canción, siguió con el género, la volvió a
         * cambiar»*): a batch with 1–2 matches took its UNKNOWN along, and they land at the END of the batch, so the
         * queue read "rock, rock, ajena, rock, rock, ajena". Two sure matches are a batch; the unknowns only fill when
         * there are fewer than that.
         */
        enoughToDropUnknown: Int = minMatches,
    ): List<T> {
        require(items.size == verdicts.size)
        val matches = items.filterIndexed { i, _ -> verdicts[i] == Verdict.MATCH }
        if (matches.size >= minOf(minMatches, enoughToDropUnknown)) return matches
        return matches + items.filterIndexed { i, _ -> verdicts[i] == Verdict.UNKNOWN }
    }

    /**
     * A run of at most [count] consecutive items of [items] starting at [startSeed] (wrapping). Row 440: a community
     * playlist was shuffled and added whole; its author's ORDER is a flow worth keeping, and a random start still gives
     * variety between rounds.
     */
    fun <T> contiguousRun(items: List<T>, count: Int, startSeed: Int): List<T> {
        if (count <= 0) return emptyList()
        if (items.size <= count) return items
        val start = Math.floorMod(startSeed, items.size)
        return List(count) { items[(start + it) % items.size] }
    }

    /** The most songs one filler playlist adds to a queue in a round. */
    const val FILL_RUN = 24

    /** What the live gate passes as `enoughToDropUnknown`. */
    const val ENOUGH_TO_DROP_UNKNOWN = 2

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

    /**
     * Fila 356 — what the user chose in the queue ("Siguiendo: …"): another exact style, the detected styles'
     * whole families ("estilos parecidos"), or any style (only the language is kept).
     */
    sealed interface Override {
        data class Exact(val style: String) : Override
        data object Similar : Override
        data object AnyStyle : Override
    }

    /** [target] as the user's [override] reshapes it. */
    fun applyOverride(target: Target, override: Override?): Target = when (override) {
        null -> target
        is Override.Exact -> target.copy(styles = setOf(override.style))
        Override.Similar -> {
            val families = target.families
            target.copy(
                styles = if (families.isEmpty()) target.styles
                else MusicStyle.ALL.filter { it.family in families }.map { it.id }.toSet(),
            )
        }
        Override.AnyStyle -> target.copy(styles = null)
    }

    /** What the queue shows: the style(s) followed, as detected and as applied. */
    data class Follow(
        val detected: List<String>,
        val applied: List<String>,
        val christian: Boolean,
        val language: String?,
        val override: Override?,
    )

    const val COLLECTION_MIN_KNOWN = 3
    const val COLLECTION_STYLE_FLOOR = 0.15

    /**
     * Row 503: what to ask YouTube when a CHRISTIAN context knows no concrete style but knows its language, so the
     * queue always has a source of provably Christian songs ("música cristiana"). Null without a language: asking for
     * Spanish songs to someone listening to English ones would be a guess.
     */
    fun christianQuery(language: String?): String? = when (language) {
        TitleLanguage.ES -> "música cristiana"
        TitleLanguage.EN -> "christian music"
        TitleLanguage.PT -> "música gospel"
        else -> null
    }

    /**
     * What to ask YouTube for more songs of [style] in this context: "cumbia cristiana", "rock en español",
     * "worship", "corridos"… Null when there is no style.
     */
    fun searchQuery(
        style: String?,
        language: String?,
        christian: Boolean,
        /** Row 493: sub-style and country words the request named ("villera", "colombiana"), placed right after the style. */
        qualifiers: List<String> = emptyList(),
    ): String? {
        val s = MusicStyle.style(style) ?: return null
        if (s.id == MusicStyle.WORSHIP) {
            return when (language) {
                TitleLanguage.EN -> "worship songs"
                TitleLanguage.PT -> "louvor e adoração"
                else -> s.search
            }
        }
        val parts = mutableListOf(s.search)
        parts += qualifiers.filter { it.isNotBlank() }
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
