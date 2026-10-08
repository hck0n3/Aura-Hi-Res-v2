package iad1tya.echo.music.reco

import java.text.Normalizer

/**
 * Fila 354 — el ESTILO CONCRETO de una canción (cumbia, salsa, merengue, corridos, trap, metal, bachata,
 * house…), no la familia ancha.
 *
 * 🔴 Dueño (2026-10-08): *"estaba escuchando una cumbia cristiana y cuando terminó la cola inteligente
 * continuó con salsa y merengue… no es coherente con lo que estaba escuchando. Y no solo me refiero al
 * ámbito cristiano, solo lo utilizo de ejemplo"* — y después: *"necesito que repares todo eso en general de
 * los géneros, no solo los que he mencionado como ejemplos"*.
 *
 * Causa: [GenreLane] solo conoce FAMILIAS ("tropical" = cumbia + salsa + merengue + bachata + vallenato), así
 * que para la cola una salsa encajaba con una cumbia. Aquí cada estilo es suyo y pertenece a una familia
 * (la familia solo se usa cuando no se sabe el estilo). Puro, sin red, sin disco.
 *
 * Dos fuentes, en este orden de confianza:
 *  1. [fromText] — palabras del TÍTULO/ÁLBUM de la canción (nunca el artista). Es lo más fiable: un título que
 *     dice "Cumbia…" es una cumbia. Si nombra dos estilos distintos ("Cumbia vs Salsa") no decide.
 *  2. [fromGenre] — el género que iTunes da al artista. Solo cuenta cuando es CONCRETO ("Bachata", "Reggae",
 *     "Hip-Hop/Rap"); las etiquetas paraguas ("Latin", "Pop", "Christian & Gospel", "Salsa y Tropical")
 *     no dicen el estilo — como mucho la familia — porque iTunes las pone a artistas de estilos distintos.
 */
object MusicStyle {

    data class Style(
        val id: String,
        val family: String,
        /** Words/phrases, already folded (lowercase, no accents, single spaces). */
        val words: List<String>,
        /** What YouTube is asked for to find more songs of this style. */
        val search: String,
        /** The style is itself Latin (its name already says "in Spanish"): no language suffix in searches. */
        val latinNative: Boolean = false,
        /** Spanish noun gender for "cristiana/cristiano" in searches. */
        val feminine: Boolean = false,
    )

    const val FAMILY_TROPICAL = "tropical"
    const val FAMILY_URBAN = "urbano"
    const val FAMILY_REGIONAL = "regional"
    const val FAMILY_ROCK = "rock"
    const val FAMILY_POP = "pop"
    const val FAMILY_ELECTRONIC = "electronica"
    const val FAMILY_RNB = "rnb"
    const val FAMILY_JAZZ_BLUES = "jazz-blues"
    const val FAMILY_COUNTRY = "country"
    const val FAMILY_REGGAE = "reggae"
    const val FAMILY_WORSHIP = "worship"
    const val FAMILY_CLASSICAL = "clasica"
    const val FAMILY_IBERIAN = "iberica"
    const val FAMILY_BRAZIL = "brasil"
    const val FAMILY_AFRO = "afro"
    const val FAMILY_CHILL = "chill"

    /** Alabanza/adoración: also what a style word means in "Merengue de Adoración", so a concrete style wins. */
    const val WORSHIP = "worship"

    /** Generic styles that give way to a concrete one named in the same text ("Pop Latino Cumbia" = cumbia). */
    private val YIELDING = setOf(WORSHIP, "pop")

    val ALL: List<Style> = listOf(
        // ── Tropical ────────────────────────────────────────────────────────────────────────────────
        Style("cumbia", FAMILY_TROPICAL, listOf("cumbia", "cumbias", "cumbion", "cumbiero", "cumbiera", "cumbieros",
            "sonidera", "sonidero", "cumbia sonidera", "cumbia nortena", "cumbia villera", "cumbia andina", "chicha"),
            "cumbia", latinNative = true, feminine = true),
        Style("salsa", FAMILY_TROPICAL, listOf("salsa", "salsas", "salsero", "salsera", "salseros", "salsa brava",
            "salsa romantica", "salsa baul", "salsa dura"), "salsa", latinNative = true, feminine = true),
        Style("merengue", FAMILY_TROPICAL, listOf("merengue", "merengues", "merenguero", "merenguera", "merengueros",
            "merengue tipico", "perico ripiao"), "merengue", latinNative = true),
        Style("bachata", FAMILY_TROPICAL, listOf("bachata", "bachatas", "bachatero", "bachatera", "bachateros"),
            "bachata", latinNative = true, feminine = true),
        Style("vallenato", FAMILY_TROPICAL, listOf("vallenato", "vallenatos", "vallenata"), "vallenato", latinNative = true),
        Style("son", FAMILY_TROPICAL, listOf("son cubano", "son montuno", "timba", "guaguanco", "chachacha", "cha cha cha",
            "mambo"), "son cubano", latinNative = true),
        Style("punta", FAMILY_TROPICAL, listOf("punta garifuna", "punta catracha"), "punta", latinNative = true, feminine = true),
        Style("kompa", FAMILY_TROPICAL, listOf("kompa", "konpa", "zouk"), "kompa"),
        // ── Urbano ──────────────────────────────────────────────────────────────────────────────────
        Style("reggaeton", FAMILY_URBAN, listOf("reggaeton", "regueton", "reggaetton", "reguetton", "perreo",
            "reggaeton cristiano", "reggaeton viejo"), "reggaeton", latinNative = true),
        Style("trap", FAMILY_URBAN, listOf("trap", "trap latino", "trap cristiano"), "trap"),
        Style("rap", FAMILY_URBAN, listOf("rap", "hip hop", "hiphop", "rap cristiano", "drill", "boom bap"), "rap"),
        Style("dembow", FAMILY_URBAN, listOf("dembow", "dembo"), "dembow", latinNative = true),
        // ── Regional mexicano ───────────────────────────────────────────────────────────────────────
        Style("corridos", FAMILY_REGIONAL, listOf("corrido", "corridos", "corridos tumbados", "corrido tumbado", "tumbado",
            "tumbados", "belico", "belicos", "corridos belicos"), "corridos", latinNative = true),
        Style("banda", FAMILY_REGIONAL, listOf("banda", "banda sinaloense", "sinaloense", "tambora"),
            "banda sinaloense", latinNative = true, feminine = true),
        Style("norteno", FAMILY_REGIONAL, listOf("norteno", "nortena", "nortenos", "nortenas", "conjunto norteno", "redova"),
            "norteño", latinNative = true),
        Style("ranchera", FAMILY_REGIONAL, listOf("ranchera", "rancheras", "ranchero", "mariachi", "mariachis"),
            "rancheras mariachi", latinNative = true, feminine = true),
        Style("sierreno", FAMILY_REGIONAL, listOf("sierreno", "sierrenos", "sierrena"), "sierreño", latinNative = true),
        Style("huapango", FAMILY_REGIONAL, listOf("huapango", "huapangos"), "huapango", latinNative = true),
        Style("duranguense", FAMILY_REGIONAL, listOf("duranguense", "pasito duranguense"), "duranguense", latinNative = true),
        // ── Rock ────────────────────────────────────────────────────────────────────────────────────
        Style("rock", FAMILY_ROCK, listOf("rock", "rock en espanol", "rock and roll", "rock n roll", "hard rock",
            "rock alternativo", "pop rock", "soft rock", "classic rock", "rock cristiano"), "rock"),
        Style("metal", FAMILY_ROCK, listOf("metal", "heavy metal", "metalcore", "death metal", "thrash metal",
            "black metal", "nu metal", "power metal", "deathcore"), "metal"),
        Style("punk", FAMILY_ROCK, listOf("punk", "pop punk", "punk rock", "hardcore punk", "ska punk"), "punk"),
        Style("grunge", FAMILY_ROCK, listOf("grunge"), "grunge"),
        Style("indie", FAMILY_ROCK, listOf("indie", "indie rock", "indie pop", "indie folk"), "indie"),
        // ── Pop ─────────────────────────────────────────────────────────────────────────────────────
        Style("pop", FAMILY_POP, listOf("pop", "pop latino", "latin pop", "dance pop", "synthpop", "synth pop"), "pop"),
        Style("balada", FAMILY_POP, listOf("balada", "baladas", "balada romantica", "baladas romanticas", "ballad",
            "ballads"), "baladas romanticas", latinNative = true, feminine = true),
        Style("kpop", FAMILY_POP, listOf("kpop", "k pop"), "kpop"),
        Style("jpop", FAMILY_POP, listOf("jpop", "j pop", "anime opening"), "jpop"),
        // ── Electrónica ─────────────────────────────────────────────────────────────────────────────
        Style("house", FAMILY_ELECTRONIC, listOf("deep house", "tech house", "house music", "progressive house",
            "afro house", "tropical house"), "deep house"),
        Style("techno", FAMILY_ELECTRONIC, listOf("techno", "hard techno", "minimal techno"), "techno"),
        Style("trance", FAMILY_ELECTRONIC, listOf("trance", "psytrance", "psy trance", "uplifting trance"), "trance"),
        Style("dubstep", FAMILY_ELECTRONIC, listOf("dubstep", "brostep", "riddim"), "dubstep"),
        Style("dnb", FAMILY_ELECTRONIC, listOf("drum and bass", "drum n bass", "dnb", "jungle"), "drum and bass"),
        Style("edm", FAMILY_ELECTRONIC, listOf("edm", "electronica", "electronic", "electro", "big room", "dance music",
            "guaracha aleteo", "aleteo"), "edm"),
        Style("lofi", FAMILY_CHILL, listOf("lofi", "lo fi", "chillhop", "chill hop"), "lofi"),
        // ── R&B / soul / funk ───────────────────────────────────────────────────────────────────────
        Style("rnb", FAMILY_RNB, listOf("rnb", "r b", "neo soul", "soul music", "contemporary r b"), "r&b"),
        Style("funk", FAMILY_RNB, listOf("funk", "disco funk"), "funk"),
        // ── Jazz / blues / country / reggae ─────────────────────────────────────────────────────────
        Style("jazz", FAMILY_JAZZ_BLUES, listOf("jazz", "bebop", "smooth jazz", "jazz latino", "latin jazz"), "jazz"),
        Style("blues", FAMILY_JAZZ_BLUES, listOf("blues", "delta blues", "blues rock"), "blues"),
        Style("country", FAMILY_COUNTRY, listOf("country music", "bluegrass", "country pop", "outlaw country"), "country music"),
        Style("reggae", FAMILY_REGGAE, listOf("reggae", "roots reggae", "reggae cristiano", "lovers rock"), "reggae"),
        Style("dancehall", FAMILY_REGGAE, listOf("dancehall"), "dancehall"),
        Style("ska", FAMILY_REGGAE, listOf("ska", "rocksteady"), "ska"),
        // ── Cristiana / alabanza ────────────────────────────────────────────────────────────────────
        Style(WORSHIP, FAMILY_WORSHIP, listOf("worship", "adoracion", "alabanza", "alabanzas", "himno", "himnos",
            "praise", "musica de adoracion", "alabanzas de adoracion", "coritos", "louvor", "adoracao"),
            "alabanza y adoracion", latinNative = true, feminine = true),
        Style("gospel", FAMILY_WORSHIP, listOf("gospel", "black gospel", "southern gospel"), "gospel"),
        // ── Clásica ─────────────────────────────────────────────────────────────────────────────────
        Style("classical", FAMILY_CLASSICAL, listOf("classical", "sinfonia", "symphony", "concerto", "sonata",
            "nocturne", "requiem", "orchestral", "orquestal"), "classical music"),
        // ── Ibérica / rioplatense / bolero ──────────────────────────────────────────────────────────
        Style("flamenco", FAMILY_IBERIAN, listOf("flamenco", "bulerias", "sevillanas", "rumba flamenca"), "flamenco", latinNative = true),
        Style("tango", FAMILY_IBERIAN, listOf("tango", "tangos"), "tango", latinNative = true),
        Style("bolero", FAMILY_IBERIAN, listOf("bolero", "boleros"), "boleros", latinNative = true),
        Style("folklore", FAMILY_IBERIAN, listOf("folklore", "chacarera", "zamba", "huayno", "cueca", "joropo",
            "musica llanera", "llanera"), "folklore latinoamericano", latinNative = true),
        // ── Brasil ──────────────────────────────────────────────────────────────────────────────────
        Style("bossa", FAMILY_BRAZIL, listOf("bossa nova", "bossa"), "bossa nova"),
        Style("samba", FAMILY_BRAZIL, listOf("samba", "pagode"), "samba"),
        Style("sertanejo", FAMILY_BRAZIL, listOf("sertanejo", "sertaneja", "sertanejo universitario"), "sertanejo"),
        Style("forro", FAMILY_BRAZIL, listOf("forro", "piseiro", "arrocha"), "forró"),
        Style("funkbr", FAMILY_BRAZIL, listOf("funk carioca", "funk brasileiro", "baile funk", "funk mandelao"), "funk brasileiro"),
        // ── África ──────────────────────────────────────────────────────────────────────────────────
        Style("afrobeats", FAMILY_AFRO, listOf("afrobeats", "afrobeat", "afropop", "afro pop", "amapiano", "afro gospel"), "afrobeats"),
    )

    private val byId: Map<String, Style> = ALL.associateBy { it.id }

    /** Fila 356 — the name the queue shows for a style (genre names; the app's audience reads Spanish). */
    private val NAMES = mapOf(
        "cumbia" to "Cumbia", "salsa" to "Salsa", "merengue" to "Merengue", "bachata" to "Bachata",
        "vallenato" to "Vallenato", "son" to "Son cubano", "punta" to "Punta", "kompa" to "Kompa",
        "reggaeton" to "Reguetón", "trap" to "Trap", "rap" to "Rap / Hip hop", "dembow" to "Dembow",
        "corridos" to "Corridos", "banda" to "Banda", "norteno" to "Norteño", "ranchera" to "Ranchera / Mariachi",
        "sierreno" to "Sierreño", "huapango" to "Huapango", "duranguense" to "Duranguense",
        "rock" to "Rock", "metal" to "Metal", "punk" to "Punk", "grunge" to "Grunge", "indie" to "Indie",
        "pop" to "Pop", "balada" to "Balada", "kpop" to "K-pop", "jpop" to "J-pop",
        "house" to "House", "techno" to "Techno", "trance" to "Trance", "dubstep" to "Dubstep",
        "dnb" to "Drum and bass", "edm" to "Electrónica", "lofi" to "Lo-fi",
        "rnb" to "R&B / Soul", "funk" to "Funk", "jazz" to "Jazz", "blues" to "Blues", "country" to "Country",
        "reggae" to "Reggae", "dancehall" to "Dancehall", "ska" to "Ska",
        WORSHIP to "Alabanza y adoración", "gospel" to "Gospel", "classical" to "Clásica",
        "flamenco" to "Flamenco", "tango" to "Tango", "bolero" to "Bolero", "folklore" to "Folclore",
        "bossa" to "Bossa nova", "samba" to "Samba / Pagode", "sertanejo" to "Sertanejo", "forro" to "Forró",
        "funkbr" to "Funk brasileño", "afrobeats" to "Afrobeats",
    )

    fun displayName(id: String): String = NAMES[id] ?: id.replaceFirstChar { it.uppercase() }

    // Every (phrase, style) pair, LONGEST phrase first: "pop punk" must be read before "pop" and "punk",
    // and "cumbia nortena" before "norteno". A matched phrase is consumed so its words do not match again.
    private val phrases: List<Pair<String, String>> = ALL
        .flatMap { s -> s.words.map { it to s.id } }
        .sortedByDescending { it.first.length }

    fun style(id: String?): Style? = id?.let { byId[it] }

    fun familyOf(id: String?): String? = style(id)?.family

    /**
     * The style named by a track's own TITLE and ALBUM (never pass the artist: "Banda MS", "La Sonora
     * Dinamita"). Null when no style word appears, or two different concrete styles do.
     */
    fun fromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        var padded = " ${fold(text)} "
        if (padded.isBlank()) return null
        val found = LinkedHashSet<String>()
        for ((phrase, id) in phrases) {
            val needle = " $phrase "
            if (padded.contains(needle)) {
                found.add(id)
                padded = padded.replace(needle, " | ")
            }
        }
        if (found.isEmpty()) return null
        val concrete = found.filter { it !in YIELDING }
        return when {
            concrete.size == 1 -> concrete.first()
            concrete.size > 1 -> null
            found.size == 1 -> found.first()
            else -> null // "pop" + "worship": nothing concrete, two generic words
        }
    }

    /** What an iTunes primary genre says: a concrete style, or only its family, or nothing. */
    data class GenreInfo(val style: String?, val family: String?)

    private val NOTHING = GenreInfo(null, null)

    fun fromGenre(genre: String?): GenreInfo {
        if (genre.isNullOrBlank()) return NOTHING
        val g = " ${fold(genre)} "
        fun has(vararg words: String) = words.any { g.contains(" $it ") }
        fun style(id: String) = GenreInfo(id, familyOf(id))
        return when {
            // Umbrella labels iTunes gives to artists of many styles: they say nothing concrete.
            has("christian", "gospel", "cristiana", "cristiano", "inspirational") -> NOTHING
            has("salsa") && has("tropical") -> GenreInfo(null, FAMILY_TROPICAL)
            has("cumbia") -> style("cumbia")
            has("merengue") -> style("merengue")
            has("bachata") -> style("bachata")
            has("vallenato") -> style("vallenato")
            has("salsa") -> style("salsa")
            has("tropical") -> GenreInfo(null, FAMILY_TROPICAL)
            has("reggaeton", "regueton") -> style("reggaeton")
            has("trap") -> style("trap")
            has("dembow") -> style("dembow")
            has("hip hop", "rap") -> style("rap")
            has("urbano", "urban") -> GenreInfo(null, FAMILY_URBAN)
            has("norteno") -> style("norteno")
            has("banda") -> style("banda")
            has("ranchera", "mariachi") -> style("ranchera")
            has("corrido", "corridos") -> style("corridos")
            has("duranguense") -> style("duranguense")
            has("regional", "mexicana", "mexicano", "mexican", "grupero") -> GenreInfo(null, FAMILY_REGIONAL)
            has("metal") -> style("metal")
            has("punk") -> style("punk")
            has("grunge") -> style("grunge")
            has("rock") -> style("rock")
            has("alternative", "alternativo", "alternativa", "indie") -> GenreInfo(null, FAMILY_ROCK)
            has("k pop", "kpop") -> style("kpop")
            has("j pop", "jpop", "anime") -> style("jpop")
            has("house") -> style("house")
            has("techno") -> style("techno")
            has("trance") -> style("trance")
            has("dubstep") -> style("dubstep")
            has("drum") && has("bass") -> style("dnb")
            has("electronic", "electronica", "dance", "edm") -> GenreInfo(null, FAMILY_ELECTRONIC)
            has("r b", "rnb", "soul") -> style("rnb")
            has("funk") -> style("funk")
            has("jazz") -> style("jazz")
            has("blues") -> style("blues")
            has("country", "bluegrass", "americana") -> GenreInfo("country", FAMILY_COUNTRY)
            has("reggae") -> style("reggae")
            has("dancehall") -> style("dancehall")
            has("ska") -> style("ska")
            has("classical", "clasica", "opera") -> style("classical")
            has("flamenco") -> style("flamenco")
            has("tango") -> style("tango")
            has("bolero") -> style("bolero")
            has("folk", "folklore") -> GenreInfo(null, FAMILY_IBERIAN)
            has("bossa") -> style("bossa")
            has("samba", "pagode") -> style("samba")
            has("sertanejo") -> style("sertanejo")
            has("forro") -> style("forro")
            has("mpb") -> GenreInfo(null, FAMILY_BRAZIL)
            has("afrobeats", "afrobeat", "afropop", "afro pop", "amapiano") -> style("afrobeats")
            has("lofi", "lo fi") -> style("lofi")
            // "Pop", "Pop Latino", "Latin", "World", "Singer/Songwriter", "Soundtrack"…: too broad to judge.
            else -> NOTHING
        }
    }

    internal fun fold(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
