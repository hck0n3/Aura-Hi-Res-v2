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
        /**
         * Row 434: a SUB-style ("corridos tumbados" inside "corridos"). A request for the sub-style must not be
         * answered with plain parent songs (owner 2026-10-18: *«pido corridos tumbados y me pone corridos pero
         * normales»*), yet a song that is only known as the parent (an artist iTunes files under "Corridos") is not
         * proof against it either — [iad1tya.echo.music.reco.StyleContinuity] treats that as UNKNOWN, not OFF.
         */
        val parent: String? = null,
        /**
         * Row 434: styles a listener may also hear under this one ("bossa" ↔ "jazz", "mpb"): an artist KNOWN only as one
         * of them (iTunes files João Gilberto's neighbours under "Jazz") is UNKNOWN for a request of this style — a
         * filler when nothing surer exists — instead of OFF, which silently emptied or replaced the request.
         */
        val related: List<String> = emptyList(),
        /**
         * Request-only SYNONYMS ("alternativo" for rock, "tecno" for techno, "musica clasica" for classical): a person
         * asking for music says them, but they are too loose to judge a TRACK by (a title that says "Soul" is not
         * proof of anything), so [fromText] — the smart queue and the Last.fm tags — never reads them. Only
         * [stylesIn] (a request) does.
         */
        val requestWords: List<String> = emptyList(),
        /**
         * Request-only SUB-STYLE names that stand alone ("tecnocumbia", "grime", "milonga"): the request is
         * recognised as the parent style (so the per-song check and the searches work), but the name itself is NOT
         * stripped from the request, so it still has to be named by the list or category that answers it — the same
         * way "sonidera" in "cumbia sonidera" does.
         */
        val variants: List<String> = emptyList(),
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
    const val FAMILY_WORLD = "mundo"

    /** Alabanza/adoración: also what a style word means in "Merengue de Adoración", so a concrete style wins. */
    const val WORSHIP = "worship"

    /** Generic styles that give way to a concrete one named in the same text ("Pop Latino Cumbia" = cumbia). */
    private val YIELDING = setOf(WORSHIP, "pop")

    private val BASE: List<Style> = listOf(
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
        Style("plena", FAMILY_TROPICAL, listOf("plena", "plenas", "bomba y plena", "bomba puertorriquena"), "plena",
            latinNative = true, feminine = true),
        // ── Urbano ──────────────────────────────────────────────────────────────────────────────────
        Style("reggaeton", FAMILY_URBAN, listOf("reggaeton", "regueton", "reggaetton", "reguetton", "perreo",
            "reggaeton cristiano", "reggaeton viejo"), "reggaeton", latinNative = true),
        Style("trap", FAMILY_URBAN, listOf("trap", "trap latino", "trap cristiano"), "trap"),
        Style("rap", FAMILY_URBAN, listOf("rap", "hip hop", "hiphop", "rap cristiano", "drill", "boom bap"), "rap"),
        Style("dembow", FAMILY_URBAN, listOf("dembow", "dembo"), "dembow", latinNative = true),
        // ── Regional mexicano ───────────────────────────────────────────────────────────────────────
        Style("corridos", FAMILY_REGIONAL, listOf("corrido", "corridos", "belico", "belicos", "corridos belicos"),
            "corridos", latinNative = true),
        // Row 434: its own style (it used to be an alias of "corridos", so the search asked YouTube for plain corridos).
        Style("tumbados", FAMILY_REGIONAL, listOf("corridos tumbados", "corrido tumbado", "tumbado", "tumbados",
            "corridos tumbado", "trap corridos"), "corridos tumbados", latinNative = true, parent = "corridos",
            related = listOf("sierreno")),
        Style("grupera", FAMILY_REGIONAL, listOf("grupera", "gruperas", "grupero", "gruperos", "musica grupera"),
            "grupera", latinNative = true, feminine = true),
        Style("tejano", FAMILY_REGIONAL, listOf("tejano", "tejana", "tex mex", "texmex"), "tejano", latinNative = true),
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
        Style("emo", FAMILY_ROCK, listOf("emo", "screamo", "midwest emo", "emo pop"), "emo"),
        Style("postpunk", FAMILY_ROCK, listOf("post punk", "postpunk", "new wave", "darkwave", "dark wave", "gothic rock",
            "goth rock", "coldwave"), "post punk"),
        Style("shoegaze", FAMILY_ROCK, listOf("shoegaze", "dream pop", "noise pop"), "shoegaze"),
        Style("psych", FAMILY_ROCK, listOf("psychedelic rock", "rock psicodelico", "psicodelico", "psicodelia",
            "psychedelia", "stoner rock"), "psychedelic rock"),
        Style("prog", FAMILY_ROCK, listOf("progressive rock", "prog rock", "rock progresivo", "art rock", "krautrock",
            "math rock"), "progressive rock"),
        Style("indie", FAMILY_ROCK, listOf("indie", "indie rock", "indie pop", "indie folk"), "indie"),
        // ── Pop ─────────────────────────────────────────────────────────────────────────────────────
        Style("pop", FAMILY_POP, listOf("pop", "pop latino", "latin pop", "dance pop", "synthpop", "synth pop"), "pop"),
        Style("balada", FAMILY_POP, listOf("balada", "baladas", "balada romantica", "baladas romanticas", "ballad",
            "ballads"), "baladas romanticas", latinNative = true, feminine = true),
        Style("kpop", FAMILY_POP, listOf("kpop", "k pop"), "kpop"),
        Style("jpop", FAMILY_POP, listOf("jpop", "j pop", "anime opening"), "jpop"),
        Style("hyperpop", FAMILY_POP, listOf("hyperpop", "hyper pop", "digicore"), "hyperpop"),
        Style("citypop", FAMILY_POP, listOf("city pop", "citypop", "shibuya kei"), "city pop"),
        Style("infantil", FAMILY_POP, listOf("infantil", "infantiles", "musica infantil", "canciones infantiles",
            "nursery rhymes", "kids songs"), "canciones infantiles", latinNative = true, feminine = true),
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
        Style("ambient", FAMILY_CHILL, listOf("ambient", "dark ambient", "chillout", "chill out", "downtempo", "new age"),
            "ambient music"),
        Style("triphop", FAMILY_CHILL, listOf("trip hop", "triphop"), "trip hop"),
        Style("phonk", FAMILY_ELECTRONIC, listOf("phonk", "drift phonk", "brazilian phonk", "memphis phonk"), "phonk"),
        Style("synthwave", FAMILY_ELECTRONIC, listOf("synthwave", "retrowave", "outrun", "vaporwave"), "synthwave"),
        Style("garage", FAMILY_ELECTRONIC, listOf("uk garage", "speed garage", "garage house", "2 step"), "uk garage"),
        Style("hardstyle", FAMILY_ELECTRONIC, listOf("hardstyle", "gabber", "happy hardcore", "hard dance"), "hardstyle"),
        // ── R&B / soul / funk ───────────────────────────────────────────────────────────────────────
        Style("rnb", FAMILY_RNB, listOf("rnb", "r b", "neo soul", "soul music", "contemporary r b"), "r&b"),
        Style("funk", FAMILY_RNB, listOf("funk", "disco funk"), "funk"),
        // ── Jazz / blues / country / reggae ─────────────────────────────────────────────────────────
        Style("jazz", FAMILY_JAZZ_BLUES, listOf("jazz", "bebop", "smooth jazz", "jazz latino", "latin jazz"), "jazz"),
        Style("blues", FAMILY_JAZZ_BLUES, listOf("blues", "delta blues", "blues rock"), "blues"),
        Style("swing", FAMILY_JAZZ_BLUES, listOf("swing", "big band", "jazz swing", "dixieland"), "swing jazz"),
        Style("motown", FAMILY_RNB, listOf("motown", "northern soul", "soul clasico"), "motown soul"),
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
        Style("opera", FAMILY_CLASSICAL, listOf("opera", "operas"), "opera"),
        Style("soundtrack", FAMILY_CLASSICAL, listOf("banda sonora", "bandas sonoras", "soundtrack", "ost", "film score"),
            "soundtrack", feminine = true),
        // ── Ibérica / rioplatense / bolero ──────────────────────────────────────────────────────────
        Style("flamenco", FAMILY_IBERIAN, listOf("flamenco", "bulerias", "sevillanas", "rumba flamenca"), "flamenco", latinNative = true),
        Style("tango", FAMILY_IBERIAN, listOf("tango", "tangos"), "tango", latinNative = true),
        Style("bolero", FAMILY_IBERIAN, listOf("bolero", "boleros"), "boleros", latinNative = true),
        Style("trova", FAMILY_IBERIAN, listOf("trova", "nueva trova", "trova cubana", "trova yucateca"), "trova",
            latinNative = true, feminine = true),
        Style("folklore", FAMILY_IBERIAN, listOf("folklore", "chacarera", "zamba", "huayno", "cueca", "joropo",
            "musica llanera", "llanera"), "folklore latinoamericano", latinNative = true),
        // ── Brasil ──────────────────────────────────────────────────────────────────────────────────
        Style("bossa", FAMILY_BRAZIL, listOf("bossa nova", "bossanova", "bossa"), "bossa nova",
            related = listOf("jazz", "mpb", "samba")),
        Style("mpb", FAMILY_BRAZIL, listOf("mpb", "musica popular brasileira"), "mpb", related = listOf("bossa", "samba")),
        Style("samba", FAMILY_BRAZIL, listOf("samba", "pagode"), "samba"),
        Style("sertanejo", FAMILY_BRAZIL, listOf("sertanejo", "sertaneja", "sertanejo universitario"), "sertanejo"),
        Style("forro", FAMILY_BRAZIL, listOf("forro", "piseiro", "arrocha"), "forró"),
        Style("funkbr", FAMILY_BRAZIL, listOf("funk carioca", "funk brasileiro", "baile funk", "funk mandelao"), "funk brasileiro"),
        // ── África ──────────────────────────────────────────────────────────────────────────────────
        Style("afrobeats", FAMILY_AFRO, listOf("afrobeats", "afrobeat", "afropop", "afro pop", "amapiano", "afro gospel"), "afrobeats"),
    )
    /**
     * Pedir música (dueño, 2026-10-19): *«cuando pido algunos géneros no me pone lo que pido; necesito que identifiques
     * todos los géneros que existen»*. Géneros que existen y la app aún no entendía, en dos formas:
     *  · [NEW_STYLES] — géneros que no tenían estilo propio (champeta, kizomba, fado, bollywood, lounge…). Sus
     *    nombres sin ambigüedad cuentan también como palabras de una canción; los sueltos y dudosos, solo en una
     *    petición ([Style.requestWords]).
     *  · [REQUEST_EXTRAS] — sinónimos y subgéneros de estilos que ya existían (alternativo → rock, tecnocumbia →
     *    cumbia, "música clásica" → clásica…). Solo los lee una petición: la cola inteligente y las etiquetas de
     *    Last.fm siguen viendo exactamente el vocabulario de antes.
     */
    private val NEW_STYLES: List<Style> = listOf(
        // ── Tropical / regional ─────────────────────────────────────────────────────────────────────
        Style("champeta", FAMILY_TROPICAL, listOf("champeta", "champeta criolla"), "champeta", latinNative = true, feminine = true),
        Style("cuarteto", FAMILY_TROPICAL, listOf("cuarteto cordobes", "cuarteto cordoves"), "cuarteto cordobés",
            latinNative = true, requestWords = listOf("cuarteto")),
        // «Polka» y «Calypso» como palabra suelta también son títulos («Polka Dots and Moonbeams», «Calypso» de John Denver):
        // solo las entiende una petición, no la cola. Lo mismo con «Lounge» («Lounge Act»), «Mento» y «Celtic».
        Style("polka", FAMILY_REGIONAL, listOf("polka nortena", "polka alemana", "polka music"), "polka",
            latinNative = true, feminine = true, requestWords = listOf("polka", "polkas")),
        Style("calypso", FAMILY_REGGAE, listOf("soca", "soca music", "steelpan", "steel drum", "steel drums"),
            "soca calypso", related = listOf("reggae", "dancehall"), requestWords = listOf("calypso", "mento")),
        // Row 462 (owner 2026-10-21: «identifica todos los géneros que existen en YouTube Music»): gaps found by probing
        // the catalogue names a person says — Colombian carranga, tropipop, Guatemalan marimba, pasodoble, stage musicals.
        Style("carranga", FAMILY_TROPICAL, listOf("carranga", "musica carranguera"), "carranga", latinNative = true, feminine = true),
        Style("tropipop", FAMILY_POP, listOf("tropipop", "tropi pop"), "tropipop", latinNative = true),
        Style("marimba", FAMILY_IBERIAN, listOf("marimba guatemalteca", "musica de marimba", "marimba de concierto"), "marimba",
            latinNative = true, feminine = true, requestWords = listOf("marimba")),
        Style("pasodoble", FAMILY_IBERIAN, listOf("pasodoble", "pasodobles"), "pasodoble", latinNative = true),
        Style("musical", FAMILY_CLASSICAL, listOf("show tunes", "cast recording", "musicales de broadway", "broadway musical"),
            "broadway musicals", requestWords = listOf("broadway", "musicales", "musical de teatro", "musicals", "teatro musical")),
        // ── África ──────────────────────────────────────────────────────────────────────────────────
        Style("kizomba", FAMILY_AFRO, listOf("kizomba", "semba", "tarraxinha"), "kizomba", related = listOf("kuduro", "kompa")),
        Style("kuduro", FAMILY_AFRO, listOf("kuduro"), "kuduro", related = listOf("kizomba", "afrobeats")),
        Style("highlife", FAMILY_AFRO, listOf("highlife", "juju music", "fuji music"), "highlife", requestWords = listOf("high life")),
        Style("soukous", FAMILY_AFRO, listOf("soukous", "ndombolo", "rumba congolesa", "congolese rumba", "lingala",
            "coupe decale", "makossa", "mbalax"), "soukous"),
        Style("gqom", FAMILY_AFRO, listOf("gqom", "kwaito"), "gqom", related = listOf("afrobeats", "house")),
        // ── Mundo ───────────────────────────────────────────────────────────────────────────────────
        Style("arabe", FAMILY_WORLD, listOf("musica arabe", "arabic music", "arabic pop", "arab pop", "pop arabe", "rai music",
            "chaabi", "gnawa", "khaleeji", "dabke"), "arabic pop", requestWords = listOf("rai")),
        Style("india", FAMILY_WORLD, listOf("bollywood", "bhangra", "hindi songs", "musica hindi", "indian pop",
            "musica india", "musica de la india", "filmi", "ghazal", "qawwali"), "bollywood", requestWords = listOf("punjabi")),
        Style("klezmer", FAMILY_WORLD, listOf("klezmer"), "klezmer"),
        Style("celtic", FAMILY_WORLD, listOf("musica celta", "irish folk", "irish music", "gaelic"), "celtic music",
            requestWords = listOf("celtic")),
        Style("fado", FAMILY_IBERIAN, listOf("fado"), "fado"),
        Style("cpop", FAMILY_POP, listOf("mandopop", "cantopop", "c pop", "cpop", "chinese pop", "musica china"), "mandopop"),
        // Same family as iTunes "Folk" ([fromGenre] files it under IBERIAN), so an artist labelled "Folk" is not judged OFF.
        Style("folk", FAMILY_IBERIAN, listOf("folk music", "musica folk", "freak folk", "neofolk"), "folk",
            requestWords = listOf("folk", "folk rock", "singer songwriter", "cantautor", "cantautora", "cantautores")),
        // ── Electrónica / baile / R&B ───────────────────────────────────────────────────────────────
        Style("eurodance", FAMILY_ELECTRONIC, listOf("eurodance", "eurobeat", "euro dance", "europop", "euro pop", "italo dance"),
            "eurodance", related = listOf("edm")),
        Style("disco", FAMILY_RNB, listOf("italo disco", "nu disco", "disco music", "eurodisco"), "disco",
            requestWords = listOf("disco", "musica disco", "studio 54"), related = listOf("funk", "eurodance")),
        Style("doowop", FAMILY_RNB, listOf("doo wop", "doowop"), "doo wop"),
        Style("lounge", FAMILY_CHILL, listOf("easy listening", "musica lounge", "musica de ascensor"), "lounge music",
            requestWords = listOf("lounge")),
    )

    private val REQUEST_EXTRAS: Map<String, Pair<List<String>, List<String>>> = mapOf(
        // id to (requestWords, variants)
        "cumbia" to (listOf("cumbias") to listOf("tecnocumbia", "villera")),
        "salsa" to (emptyList<String>() to listOf("choke", "salsa gorda")),
        "son" to (listOf("boogaloo", "charanga", "guaracha", "rumba cubana", "guajira", "descarga", "pachanga", "danzon",
            "changui") to emptyList()),
        "kompa" to (listOf("compas", "konpa direk", "zouk love", "cadence") to emptyList()),
        "plena" to (listOf("bomba") to emptyList()),
        "merengue" to (emptyList<String>() to listOf("mangulina")),
        "reggaeton" to (emptyList<String>() to listOf("neoperreo", "rkt", "cachengue")),
        "rap" to (emptyList<String>() to listOf("grime", "gangsta", "freestyle")),
        "banda" to (emptyList<String>() to listOf("tamborazo")),
        "huapango" to (listOf("son jarocho", "jarocho", "son huasteco", "huasteco") to emptyList()),
        "rock" to (listOf("rockero", "rockera", "rockeros", "rockeras", "alternativo", "alternativa", "alternative") to
            listOf("britpop", "madchester", "rockabilly", "psychobilly")),
        "metal" to (listOf("heavy") to listOf("djent", "sludge", "grindcore", "mathcore", "nwobhm", "thrash", "doom", "folk metal",
            "viking metal", "gothic metal", "industrial metal")),
        "punk" to (listOf("hardcore", "anarcopunk") to listOf("oi", "crust")),
        "emo" to (listOf("post hardcore", "posthardcore") to emptyList()),
        "postpunk" to (listOf("gotico", "gotica", "goth", "gothic", "new romantic", "industrial", "ebm") to emptyList()),
        "prog" to (listOf("post rock", "postrock", "progresivo") to emptyList()),
        "pop" to (listOf("pop music", "musica pop", "pop comercial", "top 40", "top40") to
            listOf("electropop", "electro pop", "bedroom pop", "art pop", "chamber pop", "bubblegum")),
        "kpop" to (listOf("korean pop", "musica coreana", "musica de corea", "k ballad", "korean ballad", "k rnb", "k hip hop") to
            emptyList()),
        "jpop" to (listOf("musica japonesa", "musica de japon", "japanese pop", "j rock", "jrock", "visual kei", "anime",
            "anime songs", "vocaloid", "kayokyoku") to listOf("enka")),
        "infantil" to (listOf("para ninos", "para bebes", "musica para ninos", "canciones para ninos") to emptyList()),
        "house" to (listOf("house", "electro house") to emptyList()),
        "techno" to (listOf("tecno") to emptyList()),
        "dnb" to (listOf("drum bass") to emptyList()),
        "edm" to (listOf("electronico", "electronicos", "musica electronica", "breakbeat", "big beat", "idm", "glitch",
            "bass music") to listOf("moombahton", "future bass")),
        "ambient" to (listOf("ambiental", "musica ambiental", "drone") to emptyList()),
        "synthwave" to (listOf("chillwave") to emptyList()),
        "hardstyle" to (listOf("hardcore techno", "frenchcore", "uptempo hardcore") to emptyList()),
        "rnb" to (listOf("soul", "rhythm and blues", "new jack swing", "quiet storm") to emptyList()),
        "funk" to (listOf("g funk", "p funk", "funk rock", "funk soul") to emptyList()),
        "jazz" to (emptyList<String>() to listOf("hard bop", "manouche")),
        "swing" to (listOf("ragtime", "charleston", "lindy hop") to emptyList()),
        "country" to (listOf("country", "musica country", "honky tonk", "western", "outlaw", "americana", "nashville",
            "texas country", "red dirt") to emptyList()),
        "reggae" to (listOf("rasta", "rastafari") to listOf("dub")),
        "dancehall" to (listOf("ragga", "raggamuffin", "reggae dancehall") to emptyList()),
        "gospel" to (listOf("spirituals", "negro spiritual") to emptyList()),
        "classical" to (listOf("musica clasica", "classical music", "barroco", "baroque", "gregoriano", "canto gregoriano",
            "gregorian chant", "romanticismo", "musica de camara", "chamber music", "musica sinfonica", "orquesta sinfonica",
            "piano clasico") to emptyList()),
        "opera" to (listOf("zarzuela", "arias", "musica lirica") to emptyList()),
        "soundtrack" to (listOf("bso", "musica de peliculas", "musica de series", "musica de cine", "musica de videojuegos",
            "video game music", "videogame music", "game music", "film music", "movie soundtrack") to emptyList()),
        "flamenco" to (listOf("flamenca", "cante jondo", "palos flamencos") to listOf("copla")),
        "tango" to (emptyList<String>() to listOf("milonga")),
        "folklore" to (listOf("folclor", "folclore", "folclorica", "musica folclorica", "musica andina", "andina", "andino",
            "quena", "charango", "musica tradicional", "vals criollo", "vals peruano") to listOf("chamame", "candombe", "murga", "bambuco", "pasillo",
            "currulao", "mapale", "bullerengue", "marinera", "festejo", "tinku", "morenada", "tonada", "gaita zuliana")),
        "mpb" to (listOf("tropicalia") to emptyList()),
        "samba" to (listOf("axe", "choro", "chorinho", "batucada", "samba enredo", "samba rock") to emptyList()),
        "sertanejo" to (listOf("sertaneja", "modao", "musica sertaneja") to emptyList()),
        "forro" to (listOf("xote", "baiao", "brega") to emptyList()),
        "funkbr" to (listOf("funk ostentacao", "brega funk", "funk paulista", "favela funk", "proibidao") to emptyList()),
        "afrobeats" to (listOf("afro", "naija", "musica africana", "african music", "musica de africa") to emptyList()),
    )

    val ALL: List<Style> = BASE.map { s ->
        REQUEST_EXTRAS[s.id]?.let { (req, vars) -> s.copy(requestWords = s.requestWords + req, variants = s.variants + vars) } ?: s
    } + NEW_STYLES


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
        "tumbados" to "Corridos tumbados", "grupera" to "Grupera", "tejano" to "Tejano", "plena" to "Plena",
        "emo" to "Emo", "postpunk" to "Post-punk / New wave", "shoegaze" to "Shoegaze / Dream pop",
        "psych" to "Rock psicodélico", "prog" to "Rock progresivo", "hyperpop" to "Hyperpop", "citypop" to "City pop",
        "infantil" to "Infantil", "ambient" to "Ambient / Chillout", "triphop" to "Trip hop", "phonk" to "Phonk",
        "synthwave" to "Synthwave", "garage" to "UK garage", "hardstyle" to "Hardstyle", "swing" to "Swing / Big band",
        "motown" to "Motown / Soul", "opera" to "Ópera", "soundtrack" to "Bandas sonoras", "trova" to "Trova",
        "mpb" to "MPB",
        "champeta" to "Champeta", "cuarteto" to "Cuarteto", "polka" to "Polka", "calypso" to "Soca / Calypso",
        "kizomba" to "Kizomba", "kuduro" to "Kuduro", "highlife" to "Highlife / Juju", "soukous" to "Soukous / Rumba congoleña",
        "gqom" to "Gqom / Kwaito", "arabe" to "Árabe / Raï", "india" to "India / Bollywood", "celtic" to "Celta",
        "fado" to "Fado", "klezmer" to "Klezmer", "cpop" to "C-pop / Mandopop", "folk" to "Folk", "eurodance" to "Eurodance", "disco" to "Disco",
        "doowop" to "Doo-wop", "lounge" to "Lounge",
        "carranga" to "Carranga", "tropipop" to "Tropipop", "marimba" to "Marimba", "pasodoble" to "Pasodoble",
        "musical" to "Musicales",
    )

    /** Row 434: the sub-styles [id] contains ("corridos" → "tumbados"). Empty for a style that has none. */
    fun childrenOf(id: String): Set<String> = ALL.filter { it.parent == id }.map { it.id }.toSet()

    fun displayName(id: String): String = NAMES[id] ?: id.replaceFirstChar { it.uppercase() }

    // Every (phrase, style) pair, LONGEST phrase first: "pop punk" must be read before "pop" and "punk",
    // and "cumbia nortena" before "norteno". A matched phrase is consumed so its words do not match again.
    private val phrases: List<Pair<String, String>> = ALL
        .flatMap { s -> s.words.map { it to s.id } }
        .sortedByDescending { it.first.length }

    /** What a REQUEST understands: the track-level words plus the request-only synonyms and sub-style names. */
    private val requestPhrases: List<Pair<String, String>> = ALL
        .flatMap { s -> (s.words + s.requestWords + s.variants).map { it to s.id } }
        .distinctBy { it.first }
        .sortedByDescending { it.first.length }

    /**
     * The phrases of a request that name the style ITSELF, as opposed to a sub-style of it: a phrase that contains
     * another phrase of the same style as whole words ("cumbia sonidera" ⊃ "cumbia", "deep house" ⊃ "house") is a
     * sub-style and is not in this list, and neither are the stand-alone [Style.variants] ("tecnocumbia"). They are what
     * [withoutStyles] removes from a request, so the sub-style's own words stay behind to be proven by the list that
     * answers it.
     */
    private val basePhrases: List<Pair<String, String>> by lazy {
        ALL.flatMap { s ->
            val all = (s.words + s.requestWords).distinct()
            all.filter { p -> p !in SUBSTYLE_NAMES && all.none { q -> q != p && wholeWordsIn(q, p) } }.map { it to s.id }
        }.distinctBy { it.first }.sortedByDescending { it.first.length }
    }

    /**
     * Palabras que la cola ya entiende como el estilo [Style.id] pero que son el nombre de un SUBGÉNERO («sonidera» dentro de
     * cumbia, «mambo» dentro de son, «bélicos» dentro de corridos): quien las pide quiere ESO, no el estilo en general, así
     * que no se quitan de la petición y la lista o la categoría tienen que nombrarlas.
     */
    private val SUBSTYLE_NAMES = setOf(
        "sonidera", "sonidero", "chicha", "belico", "belicos", "perico ripiao", "timba", "guaguanco", "chachacha", "cha cha cha",
        "mambo", "tambora", "sinaloense", "redova", "bulerias", "sevillanas", "rumba flamenca", "punta garifuna", "punta catracha",
        "montuno", "son montuno", "salsa baul",
    )

    private fun wholeWordsIn(small: String, big: String): Boolean = " $big ".contains(" $small ")

    /**
     * Single words that are also well-known artist or song names ("Jungle", "Swing", "OST"): when a request is NOTHING
     * but one of them, it is not stripped as a genre, so the artist/song lookup still gets its chance.
     */
    private val FRAMING = setOf(
        "musica", "canciones", "cancion", "temas", "tema", "de", "del", "la", "el", "los", "las", "un", "una", "music", "songs",
        "of", "the", "pon", "ponme", "quiero", "dame", "play", "solo", "puro", "pura",
    )

    private val AMBIGUOUS_SINGLE = setOf("jungle", "swing", "ost", "riddim", "outrun", "rai", "mento", "oi", "choke", "mambo", "aria")

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

    /**
     * Fila 361 — EVERY style a free text names (a request: "salsa y merengue cristiano"), concrete ones only
     * unless the text names nothing else ("alabanza" alone = worship). Same phrase matching as [fromText].
     */
    fun stylesIn(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        var padded = " ${fold(text)} "
        val found = LinkedHashSet<String>()
        for ((phrase, id) in requestPhrases) {
            val needle = " $phrase "
            if (padded.contains(needle)) {
                found.add(id)
                padded = padded.replace(needle, " | ")
            }
        }
        val concrete = found.filter { it !in YIELDING }.toSet()
        return concrete.ifEmpty { found }
    }

    /**
     * Every (phrase, style) a request names, longest phrase first, each phrase consumed once — [stylesIn] with the
     * phrases kept, so a caller can tell WHICH words named the style. The same generic-yields-to-concrete rule as
     * [stylesIn] applies ("Pop Latino Cumbia" = cumbia only).
     */
    fun phrasesIn(text: String?): List<Pair<String, String>> {
        if (text.isNullOrBlank()) return emptyList()
        var padded = " ${fold(text)} "
        val found = ArrayList<Pair<String, String>>()
        for ((phrase, id) in requestPhrases) {
            val needle = " $phrase "
            if (padded.contains(needle)) {
                found += phrase to id
                padded = padded.replace(needle, " | ")
            }
        }
        val concrete = found.filter { it.second !in YIELDING }
        return concrete.ifEmpty { found }
    }

    /**
     * [text] with the names of the styles themselves taken out (replaced by a space) — see [basePhrases]. Sub-style words
     * ("sonidera", "doom") and every other word stay. A request that is exactly one [AMBIGUOUS_SINGLE] word is returned
     * untouched. The text comes back folded (lowercase, no accents, single spaces).
     */
    fun withoutStyles(text: String?): String {
        val folded = fold(text.orEmpty())
        // «música de Jungle» es la banda, no el género: sin las palabras de encuadre, una sola palabra ambigua se respeta.
        val core = folded.split(' ').filter { it.isNotEmpty() && it !in FRAMING }
        if (core.size == 1 && core[0] in AMBIGUOUS_SINGLE) return folded
        var padded = " $folded "
        for ((phrase, _) in basePhrases) {
            val needle = " $phrase "
            while (padded.contains(needle)) padded = padded.replace(needle, " ")
        }
        return padded.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Palabras que nombran una FAMILIA entera, no un estilo ("música urbana", "tropical", "regional mexicano"): quien las
     * pide acepta cualquier estilo de la familia. Folded.
     */
    private val UMBRELLAS: Map<String, String> = mapOf(
        "urbano" to FAMILY_URBAN, "urbana" to FAMILY_URBAN, "urbanos" to FAMILY_URBAN, "urbanas" to FAMILY_URBAN,
        "urban" to FAMILY_URBAN, "tropical" to FAMILY_TROPICAL, "tropicales" to FAMILY_TROPICAL,
        "regional mexicano" to FAMILY_REGIONAL, "regional mexicana" to FAMILY_REGIONAL, "mexicana" to FAMILY_REGIONAL,
        "mexicano" to FAMILY_REGIONAL, "mexicanas" to FAMILY_REGIONAL, "mexicanos" to FAMILY_REGIONAL,
    )

    /**
     * Los estilos de las familias que [text] nombra cuando NO nombra ningún estilo concreto ("música urbana" → reguetón,
     * trap, rap, dembow; "tropical" → cumbia, salsa, merengue…). Vacío si hay un estilo concreto: «salsa tropical» es salsa.
     */
    fun umbrellaStylesIn(text: String?): Set<String> {
        if (text.isNullOrBlank() || stylesIn(text).isNotEmpty()) return emptySet()
        val padded = " ${fold(text)} "
        val families = UMBRELLAS.filter { (word, _) -> padded.contains(" $word ") }.values.toSet()
        return ALL.filter { it.family in families && it.words.isNotEmpty() }.map { it.id }.toSet()
    }

    /** True when [text] names at least one style a request understands ([stylesIn]). */
    fun namesAStyle(text: String?): Boolean = stylesIn(text).isNotEmpty()

    /**
     * The words that make up the NAME of [id] ("death metal" → metal, "tecnocumbia" → cumbia) for proving a list title:
     * every phrase of the style, request-only ones included. Folded.
     */
    fun namesOf(id: String): List<String> {
        val s = style(id) ?: return emptyList()
        return (s.words + s.requestWords + s.variants).distinct()
    }

    /** Tails that are not the name of a sub-style ("rock en español" → "español", "salsa romántica" → "romántica"). */
    private val NOT_SUBSTYLE_TAILS = setOf(
        "espanol", "cristiano", "cristiana", "music", "musica", "latino", "latina", "viejo", "vieja", "romantica", "romantico",
    )

    /**
     * Row 462: the words that only name a SUB-STYLE of a style ("villera" in «cumbia villera», "sonidera", "tecnocumbia",
     * "doom"): what is left of a multi-word phrase once the style's own name is taken out, plus the stand-alone sub-style
     * names. A request that is only those words is describing a style, not naming a song — pinning a track that happens to be
     * called «Villera» was the improvisation the owner forbade.
     */
    private val subStyleWords: Set<String> by lazy {
        val out = HashSet<String>()
        fun add(phrase: String) {
            phrase.split(' ').filter { it.length >= 4 && it !in NOT_SUBSTYLE_TAILS }.forEach { out += it }
        }
        for (s in ALL) {
            val own = (s.words + s.requestWords).distinct()
            s.variants.forEach { add(it) }
            own.filter { it in SUBSTYLE_NAMES }.forEach { add(it) }
            for (p in own) {
                val base = own.filter { q -> q != p && wholeWordsIn(q, p) }.maxByOrNull { it.length } ?: continue
                add(" $p ".replace(" $base ", " ").trim())
            }
        }
        out
    }

    /** true when the folded [word] is the name of a sub-style ([subStyleWords]). */
    fun isSubStyleWord(word: String): Boolean = fold(word) in subStyleWords

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
