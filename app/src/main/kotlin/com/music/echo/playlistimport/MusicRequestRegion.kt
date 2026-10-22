package iad1tya.echo.music.playlistimport

import java.text.Normalizer

/**
 * Fila 493 — EL PAÍS DE LO QUE SE PIDE («cumbia colombiana», «cumbia villera argentina», «rock de Chile»).
 *
 * 🔴 Dueño (2026-10-21): pidió «cumbia villera cristiana argentina», «cumbia colombiana cristiana» y «cumbia mexicana
 * cristiana» y las tres veces recibió LA MISMA lista, y no era cristiana. Causa leída en el código: el peldaño de
 * listas por estilo buscaba solo «cumbia cristiana» y daba por buena cualquier lista que dijera eso; el país («argentina»,
 * «colombiana») y el subgénero («villera») solo existían como palabras sueltas del resto de la petición, que ese peldaño
 * no leía. Además «mexicana» se tomaba por la familia «regional mexicano» y se borraba de la petición.
 *
 * Un país no se puede comprobar canción por canción (YouTube Music no da el país del artista), pero sí en el TÍTULO de la
 * lista que responde: «Cumbia Colombiana Cristiana». Aquí se sabe qué palabras nombran cada país (nombre, gentilicio en
 * masculino, femenino y plural) y qué géneros lo DELATAN por sí solos (un título «Cumbia Villera» ya dice Argentina aunque
 * no escriba el país). Puro, sin red.
 */
object MusicRequestRegion {

    /**
     * Un país o región. [words] son todos los modos de nombrarlo (ya normalizados); [implied] son géneros que lo delatan
     * solos; [search] es lo que se le añade a la consulta del buscador.
     */
    data class Region(
        val id: String,
        val search: String,
        val words: List<String>,
        val implied: List<String> = emptyList(),
    )

    private fun demonym(stem: String) = listOf("${stem}o", "${stem}a", "${stem}os", "${stem}as")

    val ALL: List<Region> = listOf(
        // ── América Latina ───────────────────────────────────────────────────────────────────────
        Region("argentina", "argentina", listOf("argentina") + demonym("argentin") + listOf("rioplatense", "bonaerense"),
            implied = listOf("villera", "villero", "santafesina", "cuarteto", "rkt", "chacarera", "zamba", "cordobes")),
        Region("colombia", "colombiana", listOf("colombia") + demonym("colombian"),
            implied = listOf("vallenato", "champeta", "carranga", "bullerengue", "currulao", "mapale", "bambuco", "cumbiamba",
                "llanera", "paisa", "costeno")),
        Region("mexico", "mexicana", listOf("mexico", "mexican") + demonym("mexican"),
            implied = listOf("sonidera", "sonidero", "norteno", "nortena", "banda", "corridos", "corrido", "tumbados",
                "ranchera", "rancheras", "mariachi", "grupera", "sierreno", "huapango", "duranguense", "tamborazo", "jarocho",
                "regional mexicano", "tejano")),
        Region("peru", "peruana", listOf("peru") + demonym("peruan"),
            implied = listOf("chicha", "huayno", "marinera", "festejo", "tecnocumbia", "selvatica", "amazonica")),
        Region("chile", "chilena", listOf("chile") + demonym("chilen"), implied = listOf("cueca", "tonada")),
        Region("venezuela", "venezolana", listOf("venezuela") + demonym("venezolan"),
            implied = listOf("joropo", "gaita zuliana", "gaita")),
        Region("ecuador", "ecuatoriana", listOf("ecuador") + demonym("ecuatorian"), implied = listOf("pasillo", "sanjuanito")),
        Region("bolivia", "boliviana", listOf("bolivia") + demonym("bolivian"), implied = listOf("morenada", "tinku", "saya")),
        Region("uruguay", "uruguaya", listOf("uruguay") + demonym("uruguay"), implied = listOf("candombe", "murga")),
        Region("paraguay", "paraguaya", listOf("paraguay") + demonym("paraguay"), implied = listOf("guarania")),
        Region("cuba", "cubana", listOf("cuba") + demonym("cuban"),
            implied = listOf("timba", "guaracha", "guaguanco", "danzon", "trova", "son cubano", "son montuno")),
        Region("dominicana", "dominicana", listOf("republica dominicana", "dominicana", "dominicano", "dominicanos", "dominicanas", "dominican"),
            implied = listOf("dembow", "perico ripiao", "mangulina")),
        Region("puertorico", "puertorriquena", listOf("puerto rico", "puertorriqueno", "puertorriquena", "puertorriquenos",
            "puertorriquenas", "boricua", "boricuas"), implied = listOf("plena", "bomba")),
        Region("panama", "panamena", listOf("panama") + demonym("panamen")),
        Region("costarica", "costarricense", listOf("costa rica", "costarricense", "costarricenses", "tico", "tica", "ticos", "ticas")),
        Region("guatemala", "guatemalteca", listOf("guatemala") + demonym("guatemalte"), implied = listOf("marimba")),
        Region("honduras", "hondurena", listOf("honduras") + demonym("honduren"), implied = listOf("punta catracha", "catracho", "catracha")),
        Region("elsalvador", "salvadorena", listOf("el salvador", "salvadoreno", "salvadorena", "salvadorenos", "salvadorenas")),
        Region("nicaragua", "nicaraguense", listOf("nicaragua", "nicaraguense", "nicaraguenses")),
        Region("brasil", "brasil", listOf("brasil", "brazil")),
        // ── Resto del mundo ──────────────────────────────────────────────────────────────────────
        Region("espana", "espana", listOf("espana", "espanola", "espanolas", "spain"), implied = listOf("flamenco", "sevillanas")),
        Region("portugal", "portugal", listOf("portugal"), implied = listOf("fado")),
        Region("usa", "usa", listOf("estados unidos", "usa", "eeuu", "ee uu", "united states")),
        Region("uk", "uk", listOf("reino unido", "uk", "british", "britanico", "britanica", "britanicos", "britanicas", "inglaterra")),
        Region("canada", "canada", listOf("canada", "canadiense", "canadienses")),
        Region("francia", "francia", listOf("francia", "france", "francesa", "frances", "francesas", "franceses")),
        Region("italia", "italia", listOf("italia", "italy", "italiana", "italiano", "italianas", "italianos")),
        Region("alemania", "alemania", listOf("alemania", "germany", "alemana", "aleman", "alemanas", "alemanes")),
        Region("japon", "japon", listOf("japon", "japan", "japonesa", "japones", "japonesas", "japoneses"), implied = listOf("enka")),
        Region("corea", "corea", listOf("corea", "korea", "coreana", "coreano", "coreanas", "coreanos"), implied = listOf("kpop")),
        Region("china", "china", listOf("china", "chinese", "mandopop", "cantopop")),
        Region("india", "india", listOf("india", "hindu", "bollywood", "bhangra"), implied = listOf("bollywood", "bhangra")),
        Region("jamaica", "jamaica", listOf("jamaica", "jamaiquina", "jamaiquino", "jamaiquinas", "jamaiquinos", "jamaican"),
            implied = listOf("dancehall", "ska", "rocksteady")),
        Region("haiti", "haiti", listOf("haiti", "haitiana", "haitiano", "haitianas", "haitianos"), implied = listOf("kompa", "konpa")),
        Region("nigeria", "nigeria", listOf("nigeria", "nigeriana", "nigeriano", "nigerian", "naija"), implied = listOf("afrobeats", "afrobeat")),
        Region("sudafrica", "sudafrica", listOf("sudafrica", "sudafricana", "sudafricano", "south africa"), implied = listOf("amapiano", "gqom", "kwaito")),
        Region("angola", "angola", listOf("angola", "angoleña", "angoleno"), implied = listOf("kizomba", "kuduro", "semba")),
    )

    /** Every single word and every phrase that names a region, mapped to it. */
    private val byWord: Map<String, Region> by lazy {
        val map = LinkedHashMap<String, Region>()
        for (r in ALL) for (w in r.words) map.putIfAbsent(fold(w), r)
        map
    }

    /** The words of multi-word names ("puerto", "rico") the splitter in [MusicRequestQuery.residualWords] hands back one by one. */
    private val partsOfPhrases: Set<String> by lazy {
        ALL.flatMap { r -> r.words.filter { ' ' in it }.flatMap { it.split(' ') } }
            .filter { it.length >= 4 && it !in setOf("estados", "unidos", "south", "reino", "united", "costa") }
            .toSet()
    }

    /** true when the folded [word] names a country or region on its own ("colombiana", "mexico") — not a song or an artist. */
    fun isRegionWord(word: String): Boolean {
        val w = fold(word)
        return w in byWord || w in partsOfPhrases
    }

    /** The region a folded [word] or phrase names, or null. */
    fun regionOf(word: String): Region? = byWord[fold(word)]

    /** Every region [text] names (whole words / phrases, longest phrase first, each consumed once). */
    fun regionsIn(text: String?): List<Region> {
        if (text.isNullOrBlank()) return emptyList()
        var padded = " ${fold(text)} "
        val found = LinkedHashSet<Region>()
        for ((phrase, region) in byWord.entries.sortedByDescending { it.key.length }.map { it.key to it.value }) {
            val needle = " $phrase "
            if (padded.contains(needle)) {
                found += region
                padded = padded.replace(needle, " | ")
            }
        }
        return found.toList()
    }

    /**
     * Every way a list title can PROVE the qualifier [word]: for a country, its name and demonyms plus the genres that give
     * it away on their own ("Cumbia Villera" ⇒ Argentina); for any other word, just that word. Folded.
     */
    fun group(word: String): List<String> {
        val w = fold(word)
        val region = byWord[w] ?: return listOf(w)
        return (region.words.map { fold(it) } + region.implied.map { fold(it) }).distinct()
    }

    /** The search word that names [word]'s region in a query ("colombiano" → "colombiana"), or [word] itself. */
    fun searchWord(word: String): String = regionOf(word)?.search ?: fold(word)

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
