package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.api.AiPlaylistConstraints
import iad1tya.echo.music.reco.MusicStyle
import iad1tya.echo.music.reco.StyleContinuity
import iad1tya.echo.music.reco.StyleContinuity.Verdict
import iad1tya.echo.music.reco.TitleLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-10-19): *«sigo teniendo problemas con la función de música: cuando pido algunos géneros no me pone lo que
 * pido; necesito que identifiques todos los géneros que existen, el lenguaje natural, años y todo combinado a la vez,
 * que lo sepa interpretar de la mejor manera… y que improvise»*.
 *
 * Cada defecto de aquí se REPRODUJO antes de tocar nada (el "antes" va en el comentario de cada prueba) y los grupos
 * están separados por qué falla, para que un fallo en CI diga por su nombre dónde está.
 */
class MusicRequestVocabularyTest {

    // ── Helpers: lo mismo que hace `AiPlaylistGenerator.searchFallbackPlaylist` con la petición ─────────────────────

    private fun intent(prompt: String) = MusicRequestIntent.parse(prompt, currentYear = 2026)

    private fun styles(prompt: String) = intent(prompt).styles

    /** Las palabras obligatorias del título de una lista, igual que el generador: lo que sobra tras género/momento/década. */
    private fun groupsOf(prompt: String): Pair<MusicRequestQuery.Parsed, List<List<String>>> {
        val i = intent(prompt)
        val clean = i.cleanPrompt
        val parsed = MusicRequestQuery.build(clean)
        val residual = MusicRequestQuery.residualBeyondCategory(clean)
        val qualifiers =
            if (parsed.preferPlaylists || i.styles.isNotEmpty()) MusicRequestQuery.residualWords(residual) else emptyList()
        return parsed to MusicRequestMoods.conceptGroupsFor(clean, parsed, qualifiers)
    }

    private fun listAccepted(prompt: String, title: String): Boolean {
        val (parsed, groups) = groupsOf(prompt)
        return MusicRequestMatch.score(title, parsed, groups) != MusicRequestMatch.REJECT
    }

    private fun categoryPicked(prompt: String, catalogue: List<String>): Int? {
        val i = intent(prompt)
        val clean = i.cleanPrompt
        val parsed = MusicRequestQuery.build(clean)
        val residual = MusicRequestQuery.residualBeyondCategory(clean)
        val qualifiers =
            if (parsed.preferPlaylists || i.styles.isNotEmpty()) MusicRequestQuery.residualWords(residual) else emptyList()
        return MusicRequestMoods.pickCategory(catalogue, clean, parsed, qualifiers)
    }

    private fun assertStyles(table: List<Pair<String, String>>) {
        for ((phrase, id) in table) assertEquals("«$phrase»", setOf(id), styles(phrase))
    }

    // ── 1. El vocabulario: cada género que existe se reconoce ───────────────────────────────────────────────────────

    @Test
    fun `latin and caribbean genres are all understood`() = assertStyles(
        listOf(
            "champeta" to "champeta", "cuarteto cordobés" to "cuarteto", "tecnocumbia" to "cumbia", "cumbia villera" to "cumbia",
            "salsa choke" to "salsa", "boogaloo" to "son", "guaracha" to "son", "mambo" to "son", "timba" to "son",
            "neoperreo" to "reggaeton", "rkt" to "reggaeton", "dembow" to "dembow", "plena" to "plena", "bomba" to "plena",
            "zouk love" to "kompa", "mangulina" to "merengue", "calypso" to "calypso", "soca" to "calypso",
            "polka" to "polka", "son jarocho" to "huapango", "tamborazo" to "banda", "tex mex" to "tejano",
            "chamamé" to "folklore", "candombe" to "folklore", "murga" to "folklore", "bambuco" to "folklore",
            "milonga" to "tango", "vals criollo" to "folklore", "copla" to "flamenco", "sevillanas" to "flamenco",
            "bossa nova" to "bossa", "tropicália" to "mpb", "choro" to "samba", "axé" to "samba", "baile funk" to "funkbr",
            "brega funk" to "funkbr", "sertaneja" to "sertanejo", "forró" to "forro", "xote" to "forro",
        ),
    )

    @Test
    fun `rock punk and metal subgenres are all understood`() = assertStyles(
        listOf(
            "britpop" to "rock", "rockabilly" to "rock", "surf rock" to "rock", "glam rock" to "rock", "rockero" to "rock",
            "alternativo" to "rock", "psychobilly" to "rock", "madchester" to "rock",
            "doom metal" to "metal", "symphonic metal" to "metal", "glam metal" to "metal", "djent" to "metal",
            "grindcore" to "metal", "thrash" to "metal", "heavy" to "metal", "folk metal" to "metal",
            "hardcore" to "punk", "oi" to "punk", "ska punk" to "punk", "crust" to "punk", "anarcopunk" to "punk",
            "post hardcore" to "emo", "screamo" to "emo", "gothic" to "postpunk", "darkwave" to "postpunk",
            "industrial" to "postpunk", "new wave" to "postpunk", "post rock" to "prog", "krautrock" to "prog",
            "stoner rock" to "psych", "dream pop" to "shoegaze", "grunge" to "grunge", "indie folk" to "indie",
        ),
    )

    @Test
    fun `urban pop electronic and soul genres are all understood`() = assertStyles(
        listOf(
            "grime" to "rap", "gangsta rap" to "rap", "cloud rap" to "rap", "drill" to "rap", "boom bap" to "rap",
            "latin trap" to "trap", "new jack swing" to "rnb", "quiet storm" to "rnb", "neo soul" to "rnb",
            "soul" to "rnb", "g funk" to "funk", "disco" to "disco", "italo disco" to "disco", "eurodance" to "eurodance",
            "europop" to "eurodance", "electropop" to "pop", "bedroom pop" to "pop", "art pop" to "pop",
            "enka" to "jpop", "vocaloid" to "jpop", "anime" to "jpop", "j-rock" to "jpop", "mandopop" to "cpop",
            "cantopop" to "cpop", "k-pop" to "kpop", "korean ballad" to "kpop", "city pop" to "citypop",
            "future bass" to "edm", "moombahton" to "edm", "breakbeat" to "edm", "idm" to "edm", "tecno" to "techno",
            "electro house" to "house", "house" to "house", "deep house" to "house", "psytrance" to "trance",
            "hardcore techno" to "hardstyle", "frenchcore" to "hardstyle", "chillwave" to "synthwave",
            "lo-fi" to "lofi", "ambiental" to "ambient", "new age" to "ambient", "lounge" to "lounge",
            "easy listening" to "lounge", "trip hop" to "triphop", "phonk" to "phonk", "uk garage" to "garage",
        ),
    )

    @Test
    fun `jazz country folk classical and world genres are all understood`() = assertStyles(
        listOf(
            "acid jazz" to "jazz", "hard bop" to "jazz", "manouche" to "jazz", "ragtime" to "swing", "lindy hop" to "swing",
            "doo wop" to "doowop", "honky tonk" to "country", "bluegrass" to "country", "americana" to "country",
            "outlaw" to "country", "folk" to "folk", "singer songwriter" to "folk", "cantautor" to "folk",
            "música clásica" to "classical", "barroco" to "classical", "canto gregoriano" to "classical",
            "zarzuela" to "opera", "bso" to "soundtrack", "música de videojuegos" to "soundtrack",
            "dub" to "reggae", "rastafari" to "reggae", "ragga" to "dancehall", "rocksteady" to "ska",
            "kizomba" to "kizomba", "kuduro" to "kuduro", "highlife" to "highlife", "soukous" to "soukous",
            "rumba congolesa" to "soukous", "gqom" to "gqom", "kwaito" to "gqom", "amapiano" to "afrobeats",
            "naija" to "afrobeats", "bollywood" to "india", "bhangra" to "india", "qawwali" to "india",
            "rai" to "arabe", "chaabi" to "arabe", "fado" to "fado", "celtic" to "celtic", "música celta" to "celtic",
            "klezmer" to "klezmer", "música andina" to "folklore", "huayno" to "folklore",
        ),
    )

    @Test
    fun `a genre spelled the common way is understood - reguetón with and without double g`() = assertStyles(
        listOf("reguetón" to "reggaeton", "regueton" to "reggaeton", "reggaetón" to "reggaeton", "reggaeton" to "reggaeton"),
    )

    @Test
    fun `the vocabulary is consistent - every style named, searchable, no phrase shared and every reference real`() {
        val seen = HashMap<String, String>()
        for (s in MusicStyle.ALL) {
            assertTrue("«${s.id}» necesita nombre", MusicStyle.displayName(s.id).let { it.isNotBlank() && it != s.id })
            assertTrue("«${s.id}» necesita consulta de búsqueda", s.search.isNotBlank())
            val names = s.words + s.requestWords + s.variants
            assertTrue("«${s.id}» necesita al menos una palabra", names.isNotEmpty())
            for (w in names) {
                assertEquals("«$w» (${s.id}) debe estar en minúsculas, sin acentos ni signos", w, MusicStyle.fold(w))
                val other = seen.put(w, s.id)
                assertTrue("«$w» está en «$other» y en «${s.id}»", other == null || other == s.id)
            }
            s.parent?.let { assertNotNull("padre de ${s.id}", MusicStyle.style(it)) }
            s.related.forEach { assertNotNull("vecino ${s.id}→$it", MusicStyle.style(it)) }
        }
        assertTrue("más de 90 estilos", MusicStyle.ALL.size >= 90)
    }

    // ── 2. Lo que antes se tomaba por el nombre de una canción o de un artista ──────────────────────────────────────

    @Test
    fun `a genre the app knows is never left over as a song title`() {
        // ANTES: «phonk», «shoegaze», «champeta»… dejaban el resto igual a la palabra, y el generador buscaba una CANCIÓN
        // con ese título y la fijaba en el primer puesto con su radio.
        for (g in listOf("phonk", "shoegaze", "champeta", "kizomba", "city pop", "hyperpop", "synthwave", "grupera", "tejano",
            "trip hop", "uk garage", "plena", "corridos tumbados", "música infantil", "bandas sonoras", "lofi", "gqom", "fado")) {
            assertEquals("«$g» no es un resto", "", MusicRequestQuery.residualBeyondCategory(g))
            assertTrue("«$g» prefiere listas", MusicRequestQuery.build(g).preferPlaylists)
        }
    }

    @Test
    fun `a sub-style keeps its distinguishing word, to be proven by the list`() {
        // «cumbia sonidera», «symphonic metal», «tecnocumbia»: el estilo se reconoce y la palabra de subgénero se queda.
        assertEquals("sonidera", MusicRequestQuery.residualBeyondCategory("cumbia sonidera"))
        assertEquals("symphonic", MusicRequestQuery.residualBeyondCategory("symphonic metal"))
        assertEquals("tecnocumbia", MusicRequestQuery.residualBeyondCategory("tecnocumbia"))
        assertEquals("milonga", MusicRequestQuery.residualBeyondCategory("milonga"))
    }

    @Test
    fun `family words are not song titles either`() {
        // ANTES: «música urbana sin reguetón» dejaba «urbana» de resto y se fijaba una canción que se llama «Urbana».
        assertFalse(MusicRequestQuery.hasSubstance(MusicRequestQuery.residualBeyondCategory("música urbana latina")))
        assertFalse(MusicRequestQuery.hasSubstance(MusicRequestQuery.residualBeyondCategory("música tropical para bailar")))
        assertTrue(MusicRequestQuery.build("música urbana").preferPlaylists)
    }

    @Test
    fun `words of mood and activity are not song titles - algo triste para llorar`() {
        // ANTES: «algo triste para llorar en inglés» → resto «algo llorar» → la categoría o la lista tenía que NOMBRAR
        // «algo» y «llorar»: ninguna lo hacía y la petición caía a la búsqueda de canciones sueltas.
        val p = "algo triste para llorar en inglés"
        assertFalse(MusicRequestQuery.hasSubstance(MusicRequestQuery.residualBeyondCategory(p)))
        val parsed = MusicRequestQuery.build(p)
        assertEquals("en", parsed.language)
        assertEquals("sad english", parsed.query) // la consulta en inglés, sin «algo» ni «para»
        assertTrue(parsed.preferPlaylists)
        assertTrue(listAccepted(p, "Sad Songs To Cry To"))
        assertFalse(listAccepted(p, "Mi mix de ayer"))
    }

    @Test
    fun `a song title keeps every one of its words when it is searched`() {
        // Las palabras de relleno y los adjetivos NO se quitan del resto que se compara con los títulos de canciones: solo
        // se descuentan al decidir si el resto tiene sustancia y al elegir qué palabras califican al género.
        for (title in listOf("pon Siempre Te Esperaré", "pon Como La Flor", "pon Mi Gente", "pon Happy Pharrell Williams",
            "pon Todo Cambia", "pon Hoy Quiero Soñar", "pon Despacito")) {
            val residual = MusicRequestQuery.residualBeyondCategory(title)
            assertTrue("«$title» (resto «$residual»)", MusicRequestQuery.hasSubstance(residual))
        }
        assertTrue(MusicRequestQuery.residualBeyondCategory("pon Siempre Te Esperaré").contains("siempre"))
        assertTrue(MusicRequestQuery.residualBeyondCategory("pon Como La Flor").contains("como"))
        assertTrue(MusicRequestQuery.residualBeyondCategory("pon Happy Pharrell Williams").contains("happy"))
    }

    @Test
    fun `filler words and adjectives are not required in a list title`() {
        for (p in listOf("quiero algo de bachata muy bonita", "dame unas salsas buenas", "pon bachata moderna", "ponme rock nuevo")) {
            val residual = MusicRequestQuery.residualBeyondCategory(intent(p).cleanPrompt)
            assertFalse("«$p» (resto «$residual»)", MusicRequestQuery.hasSubstance(residual))
            assertTrue("«$p»", MusicRequestQuery.residualWords(residual).isEmpty())
        }
        // «para hacer ejercicio»: ANTES «hacer» quedaba como palabra obligatoria.
        assertTrue(MusicRequestQuery.residualWords(MusicRequestQuery.residualBeyondCategory("música electrónica para hacer ejercicio")).isEmpty())
        // «de todos los tiempos»: ANTES «todos» y «tiempos» quedaban como palabras obligatorias.
        assertTrue(MusicRequestQuery.residualWords(MusicRequestQuery.residualBeyondCategory("el mejor rock en español de todos los tiempos")).isEmpty())
    }

    @Test
    fun `names of artists are still artists - the bigger vocabulary does not swallow them`() {
        for (artist in listOf("Bad Bunny", "Rosalía", "Soda Stereo", "Café Tacvba", "La Sonora Dinamita", "Los Rakas", "Camilo",
            "Gorillaz", "The Weeknd", "Marc Anthony", "Banda MS", "Rock Hudson", "Pop Smoke", "Ska-P", "50 Cent")) {
            val residual = MusicRequestQuery.residualBeyondCategory(artist)
            assertTrue("«$artist» debe conservar algo que buscar (quedó «$residual»)", residual.isNotBlank())
        }
        // Y una sola palabra ambigua («Jungle», la banda) no se come como género.
        assertEquals("jungle", MusicRequestQuery.residualBeyondCategory("música de Jungle"))
        assertEquals("jungle", MusicRequestQuery.residualBeyondCategory("Jungle"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("música de salsa"))
        assertEquals("Jungle", AiPlaylistConstraints.extractSoloArtist("música de Jungle"))
    }

    // ── 3. «música de <género>» no es un artista ────────────────────────────────────────────────────────────────────

    @Test
    fun `music of a genre is never locked to an artist`() {
        // ANTES: la lista de géneros del guardián tenía ~40 palabras. «música de bossa nova» → artista «bossa nova»: el
        // filtro de «solo este artista» no dejaba pasar nada. Probado por cada una de estas.
        for (g in listOf("bossa nova", "reguetón", "reggaetón", "gospel", "adoración", "k-pop", "country", "ópera", "r&b",
            "sertanejo", "hyperpop", "champeta", "dubstep", "phonk", "lofi", "navidad", "cumbia sonidera", "música clásica",
            "hip hop", "trap latino", "metal sinfónico", "jazz suave", "música para estudiar", "los 80", "salsa romántica")) {
            assertNull("«música de $g»", AiPlaylistConstraints.extractSoloArtist("música de $g"))
            assertNull("«canciones de $g»", AiPlaylistConstraints.extractSoloArtist("canciones de $g"))
        }
        assertEquals("Bad Bunny", AiPlaylistConstraints.extractSoloArtist("música de Bad Bunny"))
        assertEquals("Café Tacvba", AiPlaylistConstraints.extractSoloArtist("canciones de Café Tacvba"))
        assertEquals("De La Ghetto", AiPlaylistConstraints.extractSoloArtist("puro De La Ghetto"))
    }

    // ── 4. Probar una lista o una categoría: palabra completa, no subcadena ─────────────────────────────────────────

    @Test
    fun `a bare reggaeton request accepts a reggaeton list`() {
        // ANTES: los disparadores eran SUBCADENA, así que «reggaeton» activaba también «reggae» y la lista tenía que
        // contener las dos palabras enteras: «Reggaeton Hits 2024» → RECHAZADA. Toda petición de reggaetón perdía las listas.
        assertTrue(listAccepted("reggaeton", "Reggaeton Hits 2024"))
        assertTrue(listAccepted("reguetón", "Reggaeton Mix"))
        assertTrue(listAccepted("reguetón viejo", "Reggaeton Old School"))
        assertFalse(listAccepted("reggaeton", "Reggae Classics"))
        assertFalse(listAccepted("reggaeton", "Mi mix"))
    }

    @Test
    fun `a trap request accepts a trap list and not a rap one`() {
        // ANTES: «trap» activaba también «rap» (subcadena): «Trap Latino 2024» → RECHAZADA, y la categoría nunca salía.
        assertTrue(listAccepted("trap", "Trap Latino 2024"))
        assertTrue(listAccepted("trap latino", "Latin Trap Mix"))
        assertFalse(listAccepted("trap", "Rap"))
        assertTrue(listAccepted("rap", "Rap Hits"))
        assertFalse(listAccepted("rap", "Trap Hits"))
    }

    @Test
    fun `hyperpop and kpop lists are accepted by their own name`() {
        // ANTES: «hyperpop» exigía también la palabra «pop» entera; «Kpop 2024» no tenía ninguna palabra de «k-pop».
        assertTrue(listAccepted("hyperpop", "Hyperpop Essentials"))
        assertFalse(listAccepted("hyperpop", "Pop Hits"))
        assertTrue(listAccepted("k-pop", "K-Pop Hits"))
        assertTrue(listAccepted("k-pop", "Kpop 2024"))
        assertTrue(listAccepted("kpop", "Korean Pop Mix"))
    }

    @Test
    fun `no genre word triggers another inside a bigger word`() {
        // «emocional» no es emo, «bandas sonoras» no es banda, «popular» no es pop, «rapido» no es rap.
        assertTrue(groupsOf("música emocional").second.none { g -> "emo" in g })
        assertEquals(setOf("soundtrack"), styles("bandas sonoras"))
        assertTrue(listAccepted("bandas sonoras", "Bandas Sonoras Épicas"))
        assertFalse(listAccepted("bandas sonoras", "Banda MS"))
        assertTrue(styles("las canciones más populares").isEmpty())
        assertTrue(styles("algo rápido para correr").isEmpty())
    }

    @Test
    fun `a genre only the app vocabulary knows still proves a list`() {
        // Géneros sin entrada en la tabla vieja de categorías: ahora la lista que los nombra pasa.
        assertTrue(listAccepted("phonk", "Drift Phonk Mix"))
        assertTrue(listAccepted("champeta", "Champeta Criolla Hits"))
        assertTrue(listAccepted("kizomba", "Kizomba Love"))
        assertTrue(listAccepted("shoegaze", "Shoegaze & Dream Pop"))
        assertTrue(listAccepted("city pop", "City Pop Classics"))
        assertTrue(listAccepted("house", "House Music Essentials"))
        assertFalse(listAccepted("phonk", "Pop Hits"))
    }

    @Test
    fun `an official category is found for a genre by the names the catalogue really uses`() {
        val catalogue = listOf("Reggaetón", "Salsa", "Trap", "Hip hop", "Electrónica", "K-pop", "Bossa Nova", "Rock", "Metal")
        assertEquals(catalogue.indexOf("Reggaetón"), categoryPicked("reguetón", catalogue))
        assertEquals(catalogue.indexOf("Trap"), categoryPicked("trap", catalogue))
        assertEquals(catalogue.indexOf("Hip hop"), categoryPicked("rap", catalogue))
        assertEquals(catalogue.indexOf("K-pop"), categoryPicked("kpop", catalogue))
        assertEquals(catalogue.indexOf("Electrónica"), categoryPicked("house", catalogue))
        assertEquals(catalogue.indexOf("Metal"), categoryPicked("doom metal", catalogue))
        assertNull(categoryPicked("champeta", catalogue))
    }

    // ── 5. Varios estilos, varios momentos: «cualquiera de ellos», no «todos a la vez en un título» ─────────────────

    @Test
    fun `several styles in one request are any-of for a list title`() {
        // ANTES: «salsa y merengue» exigía una lista que dijera las dos palabras. Ahora basta con una de las dos y cada
        // canción se comprueba aparte contra el conjunto.
        assertEquals(setOf("salsa", "merengue"), styles("salsa y merengue para bailar"))
        assertTrue(listAccepted("salsa y merengue para bailar", "Salsa Hits"))
        assertTrue(listAccepted("salsa y merengue para bailar", "Merengue Clásico"))
        assertFalse(listAccepted("salsa y merengue para bailar", "Rock Hits"))
        assertEquals(setOf("dancehall", "afrobeats"), styles("dancehall y afrobeats para la playa"))
        assertTrue(listAccepted("dancehall y afrobeats para la playa", "Afrobeats Summer"))
    }

    @Test
    fun `a mood next to a genre or a decade is ambience, not a hard requirement`() {
        // ANTES: «salsa romántica de los 90 para cocinar» exigía romance + ambiente + salsa + 90s en un solo título.
        val p = "salsa romántica de los 90 para cocinar"
        assertTrue(listAccepted(p, "Salsa de los 90"))
        assertTrue(listAccepted(p, "Salsa Hits 90s"))
        assertFalse(listAccepted(p, "Baladas de los 90")) // lo comprobable (salsa) sigue mandando
        assertFalse(listAccepted(p, "Salsa de los 80")) // y la década también
    }

    @Test
    fun `several moods alone are any-of`() {
        val p = "música relajante con sonidos de la naturaleza para dormir"
        assertTrue(listAccepted(p, "Sonidos de la naturaleza para dormir"))
        assertTrue(listAccepted(p, "Música para dormir"))
        assertFalse(listAccepted(p, "Fiesta Latina"))
        assertEquals(
            0,
            MusicRequestMoods.pickCategory(listOf("Relajación", "Fiesta"), p, MusicRequestQuery.build(p)),
        )
    }

    @Test
    fun `moods and occasions the catalogue knows`() {
        val cat = listOf("Concentración", "Entrenamiento", "Fiesta", "Relajación", "Romance", "Viaje", "Triste", "Verano", "Navidad", "Cena")
        fun pick(p: String) = MusicRequestMoods.pickCategory(cat, p, MusicRequestQuery.build(p))
        assertEquals(cat.indexOf("Triste"), pick("algo para llorar"))
        assertEquals(cat.indexOf("Verano"), pick("música para la playa"))
        assertEquals(cat.indexOf("Navidad"), pick("villancicos"))
        assertEquals(cat.indexOf("Cena"), pick("música para cenar"))
        assertEquals(cat.indexOf("Romance"), pick("canciones para una serenata"))
        assertEquals(cat.indexOf("Entrenamiento"), pick("música para hacer cardio"))
        // Una palabra de ánimo no se come el título de una canción: «Raining Blood» sigue siendo un resto.
        assertEquals("raining blood slayer", MusicRequestQuery.residualBeyondCategory("death metal raining blood slayer"))
    }

    // ── 6. Lenguaje natural combinado ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the owner's example - salsa romántica de los 90 sin Marc Anthony para cocinar`() {
        val i = intent("ponme salsa romántica de los 90 sin Marc Anthony para cocinar")
        assertEquals(setOf("salsa"), i.styles)
        assertEquals(listOf("marc anthony"), i.excludedTerms)
        assertEquals(1990, i.eraRange!!.from)
        assertEquals(1999, i.eraRange!!.to)
        assertFalse(i.cleanPrompt.lowercase().contains("marc"))
        assertTrue(MusicRequestIntent.mentionsExcluded(i, "Vivir Mi Vida Marc Anthony"))
        val parsed = MusicRequestQuery.build(i.cleanPrompt)
        assertEquals("90", parsed.decade)
        assertTrue(parsed.query.contains("salsa") && parsed.query.contains("90"))
        assertFalse(parsed.query.contains("marc"))
    }

    @Test
    fun `rock en español 2000-2010 pon 30`() {
        val i = intent("rock en español 2000-2010 pon 30")
        assertEquals(setOf("rock"), i.styles)
        assertEquals(TitleLanguage.ES, i.language)
        assertEquals(30, i.count) // ANTES: «pon 30» quedaba pegado a la búsqueda y count = null
        assertEquals(2000, i.eraRange!!.from)
        assertEquals(2010, i.eraRange!!.to)
        assertEquals("rock en español", i.cleanPrompt)
        assertEquals("rock en espanol", MusicRequestQuery.build(i.cleanPrompt).query)
    }

    @Test
    fun `counts in the many ways people say them`() {
        assertEquals(20, intent("dame 20 canciones de bachata").count)
        assertEquals(30, intent("pon 30 de cumbia").count)
        assertEquals(25, intent("dame 25 canciones de bachata moderna en inglés").count)
        assertEquals(15, intent("quince canciones de salsa").count)
        assertEquals(10, intent("el top 10 de rock").count)
        assertEquals(20, intent("las 20 mejores de salsa").count)
        // Lo que NO es una cantidad.
        assertNull(intent("música de 50 Cent").count)
        assertNull(intent("música de los 80").count)
        assertNull(intent("pon los 90").count)
        assertEquals("canciones de bachata", intent("dame 20 canciones de bachata").cleanPrompt)
    }

    @Test
    fun `a request said in many words keeps only what can be searched`() {
        val a = MusicRequestQuery.build("quiero escuchar algo de música clásica para concentrarme")
        assertEquals("clasica para concentrarme", a.query)
        assertTrue(a.preferPlaylists)
        assertEquals("rock", MusicRequestQuery.build("ponme algo de rock").query)
        assertEquals("bachata", MusicRequestQuery.build("generame una lista de bachata").query)
        assertEquals("salsa", MusicRequestQuery.build("hazme un mix de salsa").query)
    }

    @Test
    fun `a request with nothing left to search has an empty query - like-only requests`() {
        // ANTES: «algo parecido a Bad Bunny pero sin reguetón» se quedaba con «pero» y buscaba canciones llamadas «Pero».
        val i = intent("algo parecido a Bad Bunny pero sin reguetón")
        assertEquals("Bad Bunny", i.likeArtist)
        assertEquals(setOf("reggaeton"), i.excludedStyles)
        assertEquals("", MusicRequestQuery.build(i.cleanPrompt).query)
    }

    @Test
    fun `negations in the many ways people say them`() {
        assertEquals(setOf("reggaeton"), intent("rock que no tenga reguetón").excludedStyles)
        assertEquals(setOf("reggaeton", "trap"), intent("evita el trap y el reguetón música urbana").excludedStyles)
        assertEquals(setOf("salsa", "merengue"), intent("música cristiana pero sin salsa ni merengue").excludedStyles)
        assertEquals(setOf("corridos"), intent("banda sinaloense sin corridos").excludedStyles)
        assertEquals(listOf("mana"), intent("rock en español de los 80 y 90 que no sea Maná").excludedTerms)
        assertEquals(listOf("daddy yankee"), intent("quiero reguetón viejo del 2005 al 2012 sin Daddy Yankee").excludedTerms)
    }

    @Test
    fun `like-artist in the many ways people say it`() {
        assertEquals("Rosalía", intent("algo en la onda de Rosalía").likeArtist)
        assertEquals("Soda Stereo", intent("para fans de Soda Stereo").likeArtist)
        assertEquals("Queen", intent("inspirado en Queen").likeArtist)
        assertEquals("Hades 66", intent("rap en español de los 90 estilo Hades 66").likeArtist)
        assertEquals("Fania", intent("salsa dura de los 70 estilo Fania").likeArtist)
        assertNull(intent("algo estilo cumbia").likeArtist) // «estilo cumbia» es un estilo, no un artista
    }

    @Test
    fun `years decades and languages combined with the genre`() {
        val a = intent("rock alternativo de los 2000 en español")
        assertEquals(setOf("rock"), a.styles)
        assertEquals(TitleLanguage.ES, a.language)
        val b = intent("quiero reguetón viejo del 2005 al 2012 sin Daddy Yankee")
        assertEquals(2005, b.eraRange!!.from)
        assertEquals(2012, b.eraRange!!.to)
        val c = intent("rock en español de los 80 y 90")
        assertEquals(1980, c.eraRange!!.from)
        assertEquals(1999, c.eraRange!!.to)
        val d = intent("pop en portugués para correr")
        assertEquals(setOf("pop"), d.styles)
        assertEquals(TitleLanguage.PT, d.language)
        assertTrue(MusicRequestQuery.residualWords(MusicRequestQuery.residualBeyondCategory(d.cleanPrompt)).isEmpty()) // «portugués» no es palabra obligatoria
        val e = intent("ska punk de los 90 en inglés")
        assertEquals(setOf("punk"), e.styles)
        assertEquals(TitleLanguage.EN, e.language)
        assertEquals("ska punk 90s hits english", MusicRequestQuery.build(e.cleanPrompt).query)
    }

    @Test
    fun `new jack swing and new wave are styles, not the new era`() {
        // ANTES: «new jack swing» daba swing + época NUEVA; «new wave» daba época NUEVA.
        assertNull(intent("new jack swing").era)
        assertEquals(setOf("rnb"), intent("new jack swing").styles)
        assertNull(intent("new wave").era)
        assertEquals(MusicRequestIntent.Era.NEW, intent("bachata nueva, estrenos").era)
        assertEquals(MusicRequestIntent.Era.OLD, intent("oldies").era)
    }

    @Test
    fun `a family request accepts every style of the family minus what is excluded`() {
        // «música urbana sin reguetón ni trap» → lo que queda de la familia; «tropical» → sus estilos; «salsa tropical» → salsa.
        val urban = intent("música urbana sin reguetón ni trap")
        assertEquals(setOf("rap", "dembow"), urban.styles)
        assertTrue(intent("música tropical para bailar").styles.containsAll(listOf("cumbia", "salsa", "merengue", "bachata")))
        assertEquals(setOf("salsa"), intent("salsa tropical").styles)
        assertTrue(intent("música regional mexicana").styles.containsAll(listOf("banda", "norteno", "ranchera", "corridos")))
        assertTrue(listAccepted("música tropical para bailar", "Tropical Hits"))
        assertTrue(listAccepted("música urbana", "Urbano Latino Mix"))
    }

    @Test
    fun `a christian request keeps the faith group even when the genre phrase swallows the word`() {
        // «afro gospel»: el estilo (afrobeats) se come la frase, pero lo cristiano se sigue exigiendo.
        val groups = groupsOf("afro gospel").second
        assertEquals(2, groups.size)
        assertTrue(groups.any { g -> "ccm" in g })
        // «gospel» ya trae sus palabras cristianas: no se duplica el grupo.
        assertEquals(1, groupsOf("gospel").second.count { g -> "ccm" in g || "cristiana" in g })
        assertTrue(listAccepted("bachata cristiana", "Bachata Cristiana 2024"))
        assertFalse(listAccepted("bachata cristiana", "Bachata Hits"))
    }

    // ── 7. Improvisar con sentido: el vecino cercano, nunca otro estilo ─────────────────────────────────────────────

    private fun candidate(learned: String? = null, text: String? = null, fromSearch: Boolean = false) =
        StyleContinuity.Candidate(
            textStyle = text,
            learnedStyle = learned,
            genre = MusicStyle.GenreInfo(null, null),
            language = null,
            ownArtist = false,
            fromSearch = fromSearch,
        )

    @Test
    fun `a close neighbour fills in for a rare style, an unrelated style never does`() {
        val kizomba = StyleContinuity.Target(setOf("kizomba"), null, christian = false)
        assertEquals(Verdict.MATCH, StyleContinuity.verdict(kizomba, candidate(learned = "kizomba")))
        assertEquals(Verdict.UNKNOWN, StyleContinuity.verdict(kizomba, candidate(learned = "kuduro")))
        assertEquals(Verdict.UNKNOWN, StyleContinuity.verdict(kizomba, candidate(learned = "kompa")))
        assertEquals(Verdict.OFF, StyleContinuity.verdict(kizomba, candidate(learned = "metal")))
        val calypso = StyleContinuity.Target(setOf("calypso"), null, christian = false)
        assertEquals(Verdict.UNKNOWN, StyleContinuity.verdict(calypso, candidate(learned = "reggae")))
        assertEquals(Verdict.OFF, StyleContinuity.verdict(calypso, candidate(learned = "salsa")))
    }

    @Test
    fun `a family request judges each song against the whole family`() {
        val tropical = StyleContinuity.Target(intent("música tropical").styles, null, christian = false)
        assertEquals(Verdict.MATCH, StyleContinuity.verdict(tropical, candidate(learned = "salsa")))
        assertEquals(Verdict.MATCH, StyleContinuity.verdict(tropical, candidate(learned = "cumbia")))
        assertEquals(Verdict.OFF, StyleContinuity.verdict(tropical, candidate(learned = "rock")))
    }

    // ── 8. La cola inteligente y las etiquetas de Last.fm siguen leyendo el vocabulario de antes ────────────────────

    @Test
    fun `a song title is read with the track vocabulary only - ambiguous single words stay out`() {
        // Palabras sueltas que son también títulos: solo las entiende una PETICIÓN, no la cola ni las etiquetas.
        for (title in listOf("Lounge Act", "Polka Dots and Moonbeams", "Calypso", "Celtic", "High Life", "Soul", "Folk", "Country",
            "House", "Disco", "Rai", "Mento", "Oi", "Heavy", "Hardcore", "Industrial", "Dub", "Anime", "Tecno", "Alternative")) {
            assertNull("«$title» como título de canción", MusicStyle.fromText(title))
        }
        // …y los nombres sin ambigüedad sí.
        assertEquals("kizomba", MusicStyle.fromText("Kizomba Love"))
        assertEquals("champeta", MusicStyle.fromText("Champeta Criolla"))
        assertEquals("fado", MusicStyle.fromText("Fado Português"))
        // Lo que ya existía no cambia.
        assertEquals("cumbia", MusicStyle.fromText("Cumbia Sonidera Mix"))
        assertEquals("tumbados", MusicStyle.fromText("Corridos Tumbados"))
        assertEquals("metal", MusicStyle.fromText("Heavy Metal Anthems"))
        assertEquals("reggaeton", MusicStyle.fromText("Reguetón Viejo"))
    }

    // ── 9. La tabla grande: frase → qué se entendió ─────────────────────────────────────────────────────────────────

    private data class Case(
        val phrase: String,
        val styles: Set<String>,
        val excluded: Set<String> = emptySet(),
        val christian: Boolean = false,
        val language: String? = null,
        val years: Pair<Int, Int>? = null,
        val count: Int? = null,
        val like: String? = null,
        val cleanContains: List<String> = emptyList(),
        val cleanLacks: List<String> = emptyList(),
    )

    private fun check(cases: List<Case>) {
        for (c in cases) {
            val i = intent(c.phrase)
            val tag = "«${c.phrase}»"
            assertEquals("$tag estilos", c.styles, i.styles)
            assertEquals("$tag excluidos", c.excluded, i.excludedStyles)
            assertEquals("$tag cristiano", c.christian, i.christian)
            assertEquals("$tag idioma", c.language, i.language)
            assertEquals("$tag años", c.years, i.eraRange?.let { it.from to it.to })
            assertEquals("$tag cantidad", c.count, i.count)
            assertEquals("$tag parecido a", c.like, i.likeArtist)
            c.cleanContains.forEach { assertTrue("$tag limpia debe tener «$it» (es «${i.cleanPrompt}»)", i.cleanPrompt.lowercase().contains(it)) }
            c.cleanLacks.forEach { assertFalse("$tag limpia no debe tener «$it» (es «${i.cleanPrompt}»)", i.cleanPrompt.lowercase().contains(it)) }
        }
    }

    @Test
    fun `table - genre with years`() = check(
        listOf(
            Case("reggaetón cristiano del 2004 a 2010", setOf("reggaeton"), christian = true, years = 2004 to 2010),
            Case("años 70 y 80", emptySet(), years = 1970 to 1989),
            Case("salsa de los 90", setOf("salsa"), years = 1990 to 1999),
            Case("rock de los 80 y 90 en español", setOf("rock"), language = TitleLanguage.ES, years = 1980 to 1999),
            Case("cumbia cristiana de los 2000 pon 40", setOf("cumbia"), christian = true, years = 2000 to 2009, count = 40),
            Case("música electrónica de los 2010 para hacer ejercicio", setOf("edm"), years = 2010 to 2019),
            Case("jazz de los 50", setOf("jazz"), years = 1950 to 1959),
            Case("hip hop de los 90", setOf("rap"), years = 1990 to 1999),
            Case("reggae roots de los 70", setOf("reggae"), years = 1970 to 1979),
            Case("lo mejor del merengue cristiano 2026", setOf("merengue"), christian = true, years = 2026 to 2026),
        ),
    )

    @Test
    fun `table - genre with language and country`() = check(
        listOf(
            Case("blues en inglés", setOf("blues"), language = TitleLanguage.EN),
            Case("pop en portugués", setOf("pop"), language = TitleLanguage.PT),
            Case("rap en español", setOf("rap"), language = TitleLanguage.ES),
            Case("alabanzas en inglés", setOf("worship"), christian = true, language = TitleLanguage.EN),
            Case("rock argentino", setOf("rock")),
            Case("salsa colombiana", setOf("salsa")),
            Case("pop coreano", setOf("pop")),
            Case("música de Brasil en portugués", emptySet(), language = TitleLanguage.PT),
        ),
    )

    @Test
    fun `table - everything at once`() = check(
        listOf(
            Case(
                "ponme salsa romántica de los 90 sin Marc Anthony para cocinar", setOf("salsa"), years = 1990 to 1999,
                cleanContains = listOf("salsa", "90"), cleanLacks = listOf("marc"),
            ),
            Case(
                "rock en español 2000-2010 pon 30", setOf("rock"), language = TitleLanguage.ES, years = 2000 to 2010, count = 30,
            ),
            Case(
                "dame 25 canciones de bachata moderna en inglés", setOf("bachata"), language = TitleLanguage.EN, count = 25,
            ),
            Case(
                "quiero reguetón viejo del 2005 al 2012 sin Daddy Yankee", setOf("reggaeton"), years = 2005 to 2012,
            ),
            Case(
                "algo parecido a Bad Bunny pero sin reguetón", emptySet(), excluded = setOf("reggaeton"), like = "Bad Bunny",
            ),
            Case(
                "música cristiana pero sin salsa ni merengue", emptySet(), excluded = setOf("salsa", "merengue"), christian = true,
            ),
            Case(
                "corridos tumbados y trap latino nuevos", setOf("tumbados", "trap"),
            ),
            Case(
                "rap en español de los 90 estilo Hades 66", setOf("rap"), language = TitleLanguage.ES, years = 1990 to 1999,
                like = "Hades 66",
            ),
            Case(
                "las 20 mejores canciones de metal sinfónico", setOf("metal"), count = 20,
            ),
        ),
    )
}
