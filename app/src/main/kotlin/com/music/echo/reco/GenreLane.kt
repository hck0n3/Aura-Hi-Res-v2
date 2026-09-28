package iad1tya.echo.music.reco

/**
 * "Style lane" inference so autoplay can keep the same line as what you're listening to (Christian stays
 * Christian, Latin stays Latin, Rock stays Rock — instead of drifting). Two signals, in order:
 *
 *  1. [laneOf] — a keyword classifier over a track's title + artist + album text. It only makes a POSITIVE
 *     call for lanes with unambiguous markers (currently "christian"); it exists because YouTube doesn't tag
 *     lyrical content and it also catches Christian tracks whose artist isn't in the genre cache yet.
 *  2. [laneOfTrack] — the artist's REAL primary genre from [GenreCache] (iTunes: "Latin", "Rock",
 *     "Hip-Hop/Rap", "Pop"...), normalized to a lane id. This is what makes the lane follow the actual genre
 *     being played rather than a single hardcoded style.
 *
 * Anything we can't call is UNKNOWN (null) — the caller then enforces nothing, and even with a known lane it
 * only enforces when it has enough same-lane candidates, so playback never dead-ends. Combined with
 * "No me gusta", the more you correct it the cleaner it stays.
 */
object GenreLane {
    const val CHRISTIAN = "christian"

    // Unambiguous Christian markers (Spanish + English). Kept to words that rarely appear in secular
    // music so we don't mislabel. Artist names are intentionally avoided (too brittle / ambiguous).
    private val CHRISTIAN_MARKERS = listOf(
        "cristian", "cristo", " jesus", " jesús", "jesucristo", "evangelio", "evangélic", "evangelic",
        "gospel", "adoracion", "adoración", "worship", "alabanza", "aleluya", "hallelujah",
        "espiritu santo", "espíritu santo", "santo espiritu", "iglesia", "salmo", "redentor",
        "musica cristiana", "música cristiana", "christian", "reggaeton cristiano", "rap cristiano",
        "tu presencia", "dios es", "señor jesus", "el shaddai", "jehova", "jehová", "yahweh",
        // Ronda 11 (dueño: "El culto está bueno" de Geovanni Rios, merengue cristiano). Frases de iglesia
        // que casi nunca aparecen en música secular.
        "el culto", "coritos", "avivamiento", "pentecostal", "gloria a dios", "gloria e' dios", "gloria e’ dios",
    )

    /**
     * Ronda 11 (dueño, 2026-09-27): *"si el artista que estoy escuchando es cristiano, que también se
     * mantenga el género más su religión; y si no tiene religión, que se mantenga con música de acuerdo
     * a lo que se escucha"*.
     *
     * Hasta aquí la fe y el estilo eran UN solo carril: "cristiano" se comía el estilo, así que un
     * merengue cristiano y una alabanza quedaban en el mismo carril y la cola saltaba de uno a otra
     * (Geovanni Rios → Marcos Witt). El estilo es ahora una dimensión aparte, en familias amplias —
     * iTunes nombra el mismo estilo de varias formas ("Tropical", "Salsa y Tropical", "Merengue").
     */
    const val STYLE_TROPICAL = "tropical"
    const val STYLE_URBAN = "urbano"
    const val STYLE_ROCK = "rock"
    const val STYLE_POP = "pop"
    const val STYLE_REGIONAL = "regional"

    /** Estilo "alabanza/adoración": el de un artista que iTunes solo etiqueta como cristiano. */
    const val STYLE_WORSHIP = "worship"

    /** Palabras de estilo en el TÍTULO/ÁLBUM (nunca en el artista), por palabra completa. */
    private val STYLE_WORDS: List<Pair<String, List<String>>> = listOf(
        STYLE_TROPICAL to listOf("merengue", "bachata", "salsa", "cumbia", "tropical", "vallenato", "tipico"),
        STYLE_URBAN to listOf("reggaeton", "urbano", "urbana", "trap", "rap", "hip hop", "dembow", "perreo", "drill"),
        STYLE_ROCK to listOf("rock", "metal"),
        STYLE_REGIONAL to listOf("corrido", "corridos", "ranchera", "norteno", "banda", "mariachi"),
        STYLE_WORSHIP to listOf("worship", "adoracion", "alabanza", "himno", "himnos"),
    )

    // Separators used by YouTube/media metadata to pack several artists into one string. We only want the
    // PRIMARY artist for the genre lookup, since GenreCache is keyed by a single artist name.
    private val ARTIST_SPLITTERS = listOf(",", ";", " & ", " feat.", " feat ", " ft.", " ft ", " x ", " con ", " vs.", " vs ", " and ", " y ")

    private fun normalize(text: String?): String =
        (text ?: "").lowercase()

    /** Returns a lane id (e.g. [CHRISTIAN]) when the TEXT clearly belongs to one, else null. */
    fun laneOf(vararg parts: String?): String? {
        val t = normalize(parts.joinToString(" "))
        if (t.isBlank()) return null
        if (CHRISTIAN_MARKERS.any { t.contains(it) }) return CHRISTIAN
        return null
    }

    /**
     * The lane of a whole track: the keyword lane first (it catches Christian songs whose artist has no
     * cached genre yet), otherwise the primary artist's real genre from [genres] (a [GenreCache] snapshot),
     * normalized. Null when we simply don't know — the caller must treat that as "no lane, no enforcement".
     *
     * [genres] is passed IN (never read from disk here) so callers can take ONE snapshot per run instead of
     * one SharedPreferences read per candidate.
     */
    fun laneOfTrack(
        genres: Map<String, String>,
        artistName: String?,
        title: String?,
        album: String? = null,
    ): String? {
        val cached = if (genres.isEmpty()) null else lookupGenre(genres, artistName)?.let { normalizeGenre(it) }
        // When the cache KNOWS this artist and knows them NOT to be Christian, the keyword pass must not read
        // the ARTIST field: "Christian Nodal" and "Cristian Castro" are Latin artists whose NAME contains a
        // marker, and a false CHRISTIAN lane is the WORST case — it is the strict lane, so it would drop every
        // Latin candidate. Their TITLES are still scanned, so an actual worship song by them is still caught.
        if (cached != null && cached != CHRISTIAN) {
            return laneOf(title, album) ?: cached
        }
        // Unknown artist: keywords first — that is exactly what they are for (a Christian track whose artist
        // has no cached genre yet).
        laneOf(title, artistName, album)?.let { return it }
        return cached
    }

    /**
     * True when [CHRISTIAN] is backed by the KEYWORD classifier (the track's own text) rather than by a cached
     * genre. Only that origin justifies the caller's strict "must match" filter: keywords need no cache, so an
     * unknown candidate really is, in practice, secular. A CHRISTIAN lane that came from [GenreCache] (e.g. an
     * artist iTunes labels "Christian & Gospel") must NOT be strict — the cache only knows library artists, so
     * strictness there would drop every new radio artist and collapse autoplay onto the library.
     *
     * Callers must combine this with the lane itself: a keyword hit on the ARTIST field alone does not make the
     * lane Christian (see [laneOfTrack] — "Christian Nodal" is Latin).
     */
    fun isKeywordChristian(title: String?, artistName: String?, album: String? = null): Boolean =
        laneOf(title, artistName, album) == CHRISTIAN

    /**
     * Look the artist up in the cache: the full name first (how GenreCache is keyed when we enrich from the
     * library), then the primary artist when the metadata packs several into one string ("A, B", "A feat. B").
     */
    private fun lookupGenre(genres: Map<String, String>, artistName: String?): String? {
        val full = artistName?.trim()?.lowercase().orEmpty()
        if (full.isBlank()) return null
        genres[full]?.takeIf { it.isNotBlank() }?.let { return it }
        val primary = primaryArtist(full)
        if (primary != full && primary.isNotBlank()) {
            genres[primary]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    /** First artist of an already-lowercased artist string. */
    private fun primaryArtist(lowered: String): String {
        var cut = lowered.length
        ARTIST_SPLITTERS.forEach { sep ->
            val i = lowered.indexOf(sep)
            if (i in 1 until cut) cut = i
        }
        return lowered.take(cut).trim()
    }

    /**
     * Normalize an iTunes primary genre into a lane id. Lowercased/trimmed, and the Christian/Gospel genre
     * collapses onto [CHRISTIAN] so the keyword path and the genre path agree on one lane (otherwise a
     * Christian song matched by keyword and one matched by cached genre would sit in two different lanes and
     * fail to keep each other company).
     *
     * Internal (not private) so [ContextProfile] and its tests share the EXACT same genre vocabulary —
     * a context profiled as "salsa y tropical" must match candidates normalized the same way.
     */
    internal fun normalizeGenre(genre: String): String? {
        val g = genre.trim().lowercase()
        if (g.isBlank()) return null
        if (g.contains("christian") || g.contains("gospel")) return CHRISTIAN
        return g
    }

    /** Familia de estilo de un género de iTunes. "Christian & Gospel" sin más → [STYLE_WORSHIP]. */
    internal fun styleFamily(genre: String): String? {
        val g = fold(genre)
        if (g.isBlank()) return null
        return when {
            listOf("salsa", "tropical", "merengue", "bachata", "cumbia", "vallenato").any { g.contains(it) } -> STYLE_TROPICAL
            listOf("urban", "reggaeton", "hip-hop", "hip hop", "rap", "trap").any { g.contains(it) } -> STYLE_URBAN
            listOf("rock", "metal", "alternativ", "punk").any { g.contains(it) } -> STYLE_ROCK
            listOf("regional", "mexican", "ranchera", "banda", "norten").any { g.contains(it) } -> STYLE_REGIONAL
            g.contains("christian") || g.contains("gospel") || g.contains("cristian") -> STYLE_WORSHIP
            g.contains("pop") -> STYLE_POP
            else -> g
        }
    }

    /**
     * El estilo de una pista: las palabras de estilo de su propio título/álbum primero ("Merengue de
     * Adoración" dice más que la etiqueta del artista), después la familia del género en caché del
     * artista. Null = no se sabe, y un estilo desconocido nunca descarta nada.
     */
    fun styleOfTrack(genres: Map<String, String>, artistName: String?, title: String?, album: String? = null): String? {
        val text = fold(listOfNotNull(title, album).joinToString(" "))
        if (text.isNotBlank()) {
            STYLE_WORDS.firstOrNull { (_, words) -> words.any { hasWord(text, it) } }?.let { return it.first }
        }
        if (genres.isEmpty()) return null
        return lookupGenre(genres, artistName)?.let { styleFamily(it) }
    }

    /**
     * Lo que una ANCLA (la canción desde la que sigue la cola) exige a cada candidata. [lane] y
     * [strict] son exactamente el carril de siempre (filas #300/#307); [christianStyle] es lo nuevo de
     * la ronda 11: el estilo concreto de un ancla CRISTIANA, o null cuando el ancla no es cristiana o
     * su estilo no se conoce (entonces todo sigue igual que antes).
     */
    data class Anchor(val lane: String?, val strict: Boolean, val christianStyle: String?)

    fun anchorOf(genres: Map<String, String>, artistName: String?, title: String?, album: String? = null): Anchor {
        val lane = laneOfTrack(genres, artistName, title, album)
        val strict = lane == CHRISTIAN && isKeywordChristian(title, artistName, album)
        // "Alabanza" no es un estilo que haya que proteger contra otros estilos cristianos: es también lo
        // que iTunes dice de cualquier artista cristiano sin más etiqueta, o sea "no sé su estilo".
        val style = if (lane == CHRISTIAN) {
            styleOfTrack(genres, artistName, title, album)?.takeIf { it != STYLE_WORSHIP }
        } else {
            null
        }
        return Anchor(lane, strict, style)
    }

    /**
     * ¿Mantiene [anchor] esta candidata? El carril se decide igual que siempre; si además el ancla es
     * cristiana con un estilo conocido, la candidata tiene que ser de ese estilo — o de estilo
     * desconocido, que nunca se descarta (#39/#41). Un artista que iTunes solo etiqueta como cristiano
     * cuenta como [STYLE_WORSHIP]: es lo que separa un merengue cristiano de una alabanza.
     */
    fun keeps(anchor: Anchor, genres: Map<String, String>, artistName: String?, title: String?, album: String? = null): Boolean {
        val lane = laneOfTrack(genres, artistName, title, album)
        val laneOk = if (anchor.strict) lane == anchor.lane else lane == null || lane == anchor.lane
        if (!laneOk) return false
        val want = anchor.christianStyle ?: return true
        val style = styleOfTrack(genres, artistName, title, album) ?: return true
        return style == want
    }

    private fun hasWord(folded: String, word: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])").containsMatchIn(folded)

    private fun fold(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
