package iad1tya.echo.music.reco

import iad1tya.echo.music.reco.StyleContinuity.Verdict.MATCH
import iad1tya.echo.music.reco.StyleContinuity.Verdict.OFF
import iad1tya.echo.music.reco.StyleContinuity.Verdict.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fila 354 (dueño 2026-10-08): "estaba escuchando una cumbia cristiana y cuando terminó la cola continuó con
 * salsa y merengue… no solo el ámbito cristiano… repara todo eso en general de los géneros".
 */
class MusicStyleTest {

    @Test
    fun `tropical styles are no longer one lane`() {
        assertEquals("cumbia", MusicStyle.fromText("Cumbia del Espíritu (En Vivo)"))
        assertEquals("salsa", MusicStyle.fromText("Salsa Cristiana Mix"))
        assertEquals("merengue", MusicStyle.fromText("Merengue de Adoración"))
        assertEquals("bachata", MusicStyle.fromText("Bachata para Jesús"))
        assertEquals("vallenato", MusicStyle.fromText("Vallenatos de fe"))
        assertEquals(MusicStyle.FAMILY_TROPICAL, MusicStyle.familyOf("cumbia"))
        assertEquals(MusicStyle.FAMILY_TROPICAL, MusicStyle.familyOf("salsa"))
    }

    @Test
    fun `every genre family has concrete styles, not only the examples`() {
        assertEquals("corridos", MusicStyle.fromText("Corridos Tumbados 2024"))
        assertEquals("banda", MusicStyle.fromText("Éxitos de Banda Sinaloense"))
        assertEquals("norteno", MusicStyle.fromText("Puro Norteño"))
        assertEquals("reggaeton", MusicStyle.fromText("Reguetón Viejo"))
        assertEquals("trap", MusicStyle.fromText("Trap Latino"))
        assertEquals("rap", MusicStyle.fromText("Hip-Hop Classics"))
        assertEquals("metal", MusicStyle.fromText("Heavy Metal Anthems"))
        assertEquals("punk", MusicStyle.fromText("Pop Punk Hits"))
        assertEquals("rock", MusicStyle.fromText("Rock en Español"))
        assertEquals("kpop", MusicStyle.fromText("K-Pop Hits"))
        assertEquals("techno", MusicStyle.fromText("Techno Set"))
        assertEquals("reggae", MusicStyle.fromText("Reggae Roots"))
        assertEquals("sertanejo", MusicStyle.fromText("Sertanejo Universitário"))
        assertEquals("afrobeats", MusicStyle.fromText("Amapiano Mix"))
        assertEquals(MusicStyle.WORSHIP, MusicStyle.fromText("Alabanzas de Adoración"))
        assertTrue(MusicStyle.ALL.map { it.family }.toSet().size >= 12)
        assertTrue(MusicStyle.ALL.size >= 50)
    }

    @Test
    fun `a concrete style wins over a generic word, two concrete styles decide nothing`() {
        assertEquals("merengue", MusicStyle.fromText("Merengue de alabanza"))
        assertEquals("cumbia", MusicStyle.fromText("Pop Latino Cumbia"))
        assertNull(MusicStyle.fromText("Cumbia vs Salsa"))
        assertNull(MusicStyle.fromText("Te Busco"))
        assertNull(MusicStyle.fromText(null))
    }

    @Test
    fun `words are whole words, never pieces of other words`() {
        assertNull(MusicStyle.fromText("Salsabil")) // not "salsa"
        assertNull(MusicStyle.fromText("House of the Lord")) // a worship song, not house music
        assertNull(MusicStyle.fromText("It Is Well With My Soul"))
        assertNull(MusicStyle.fromText("Clásicos de siempre")) // "classics", not classical music
    }

    @Test
    fun `iTunes umbrella labels say no concrete style`() {
        assertNull(MusicStyle.fromGenre("Salsa y Tropical").style)
        assertEquals(MusicStyle.FAMILY_TROPICAL, MusicStyle.fromGenre("Salsa y Tropical").family)
        assertNull(MusicStyle.fromGenre("Christian & Gospel").style)
        assertNull(MusicStyle.fromGenre("Christian & Gospel").family)
        assertNull(MusicStyle.fromGenre("Latin").style)
        assertNull(MusicStyle.fromGenre("Pop").style)
        assertNull(MusicStyle.fromGenre("Pop Latino").family)
        assertEquals("bachata", MusicStyle.fromGenre("Bachata").style)
        assertEquals("rap", MusicStyle.fromGenre("Hip-Hop/Rap").style)
        assertEquals("reggae", MusicStyle.fromGenre("Reggae").style)
        assertEquals(MusicStyle.FAMILY_REGIONAL, MusicStyle.fromGenre("Música Mexicana").family)
    }
}

class StyleContinuityTest {

    private fun cand(
        text: String? = null,
        learned: String? = null,
        genre: String? = null,
        lang: String? = null,
        own: Boolean = false,
        search: Boolean = false,
    ) = StyleContinuity.Candidate(
        textStyle = MusicStyle.fromText(text),
        learnedStyle = learned,
        genre = MusicStyle.fromGenre(genre),
        language = TitleLanguage.detect(text.orEmpty()),
        ownArtist = own,
        fromSearch = search,
    )

    private val cumbia = StyleContinuity.Target(setOf("cumbia"), TitleLanguage.ES, christian = true)

    @Test
    fun `the owner's case - after a cumbia, salsa and merengue are out`() {
        assertEquals(OFF, StyleContinuity.verdict(cumbia, cand("Salsa para mi Dios")))
        assertEquals(OFF, StyleContinuity.verdict(cumbia, cand("Merengue de la fe")))
        assertEquals(OFF, StyleContinuity.verdict(cumbia, cand("Tu amor", learned = "salsa")))
        assertEquals(OFF, StyleContinuity.verdict(cumbia, cand("Tu amor", genre = "Bachata")))
        assertEquals(MATCH, StyleContinuity.verdict(cumbia, cand("Cumbia del cielo")))
        assertEquals(MATCH, StyleContinuity.verdict(cumbia, cand("Tu amor", learned = "cumbia")))
    }

    @Test
    fun `own words beat the artist, the search and the collection`() {
        assertEquals(OFF, StyleContinuity.verdict(cumbia, cand("Salsa del barrio", own = true)))
        assertEquals(OFF, StyleContinuity.verdict(cumbia, cand("Salsa del barrio", search = true)))
        assertEquals(MATCH, StyleContinuity.verdict(cumbia, cand("Tu amor", own = true)))
        assertEquals(MATCH, StyleContinuity.verdict(cumbia, cand("Tu amor", search = true)))
    }

    @Test
    fun `umbrella genres never condemn, another family does`() {
        assertEquals(UNKNOWN, StyleContinuity.verdict(cumbia, cand("Tu amor", genre = "Christian & Gospel")))
        assertEquals(UNKNOWN, StyleContinuity.verdict(cumbia, cand("Tu amor", genre = "Salsa y Tropical")))
        assertEquals(UNKNOWN, StyleContinuity.verdict(cumbia, cand("Tu amor", genre = "Latin")))
        assertEquals(OFF, StyleContinuity.verdict(cumbia, cand("Tu amor", genre = "Alternative")))
    }

    @Test
    fun `another language is out, an unclear one is not judged`() {
        assertEquals(OFF, StyleContinuity.verdict(cumbia, cand("You are my everything", search = true)))
        assertEquals(MATCH, StyleContinuity.verdict(cumbia, cand("Hosanna", search = true)))
        val spanishOnly = StyleContinuity.Target(null, TitleLanguage.ES, christian = false)
        assertEquals(OFF, StyleContinuity.verdict(spanishOnly, cand("I will always love you")))
        assertEquals(MATCH, StyleContinuity.verdict(spanishOnly, cand("Te quiero con todo mi corazón")))
        assertEquals(UNKNOWN, StyleContinuity.verdict(spanishOnly, cand("Hosanna")))
    }

    @Test
    fun `nothing known about the target changes nothing`() {
        val none = StyleContinuity.Target(null, null, christian = false)
        assertEquals(MATCH, StyleContinuity.verdict(none, cand("Salsa")))
    }

    @Test
    fun `enough matches - the unknowns stay out too, OFF never plays`() {
        val items = listOf("m1", "u1", "o1", "m2", "u2", "m3")
        val v = listOf(MATCH, UNKNOWN, OFF, MATCH, UNKNOWN, MATCH)
        assertEquals(listOf("m1", "m2", "m3"), StyleContinuity.select(items, v, minMatches = 3))
        assertEquals(listOf("m1", "m2", "m3", "u1", "u2"), StyleContinuity.select(items, v, minMatches = 4))
        assertEquals(emptyList<String>(), StyleContinuity.select(listOf("o"), listOf(OFF), minMatches = 3))
    }

    @Test
    fun `a collection keeps its name's style, else every real style it has`() {
        assertEquals(setOf("cumbia"), StyleContinuity.collectionStyles(listOf("salsa", "salsa"), "cumbia"))
        assertEquals(
            setOf("cumbia", "salsa"),
            StyleContinuity.collectionStyles(listOf("cumbia", "cumbia", "salsa", "cumbia", null, "salsa"), null),
        )
        assertNull(StyleContinuity.collectionStyles(listOf("cumbia", null, null), null))
    }

    @Test
    fun `the style search asks for the context's own version of the style`() {
        assertEquals("cumbia cristiana", StyleContinuity.searchQuery("cumbia", TitleLanguage.ES, christian = true))
        assertEquals("merengue cristiano", StyleContinuity.searchQuery("merengue", TitleLanguage.ES, christian = true))
        assertEquals("rock en español", StyleContinuity.searchQuery("rock", TitleLanguage.ES, christian = false))
        assertEquals("corridos", StyleContinuity.searchQuery("corridos", TitleLanguage.ES, christian = false))
        assertEquals("rap christian", StyleContinuity.searchQuery("rap", TitleLanguage.EN, christian = true))
        assertEquals("worship songs", StyleContinuity.searchQuery(MusicStyle.WORSHIP, TitleLanguage.EN, christian = true))
        assertNull(StyleContinuity.searchQuery(null, TitleLanguage.ES, christian = true))
    }
}

class ArtistStyleMemoryTest {

    @Test
    fun `an artist's style needs clear, repeated evidence`() {
        var m: ArtistStyleMemory.Memory = emptyMap()
        m = ArtistStyleMemory.merged(m, listOf("Grupo X" to "cumbia"))
        assertNull(ArtistStyleMemory.styleOf(m, "grupo x")) // one observation is not enough
        m = ArtistStyleMemory.merged(m, listOf("grupo x " to "cumbia"))
        assertEquals("cumbia", ArtistStyleMemory.styleOf(m, "Grupo X"))
        m = ArtistStyleMemory.merged(m, listOf("Grupo X" to "salsa", "Grupo X" to "salsa"))
        assertNull(ArtistStyleMemory.styleOf(m, "Grupo X")) // half and half: no style, nothing judged by it
    }

    @Test
    fun `the memory is bounded and survives its own encoding`() {
        val m = ArtistStyleMemory.merged(emptyMap(), listOf("a" to "salsa", "b" to "rock"), maxArtists = 1)
        assertEquals(1, m.size)
        val counts = mapOf("cumbia" to 3, "salsa" to 1)
        assertEquals(counts, ArtistStyleMemory.decode(ArtistStyleMemory.encode(counts)))
    }
}

/** Fila 356 — the four improvements the owner approved ("sí, haz los puntos que dices"). */
class ExactStyleImprovementsTest {

    @Test
    fun `Last_fm tags name the artist's style when they clearly agree`() {
        assertEquals("cumbia", ArtistTagStyles.styleFromTags(listOf("cumbia" to 100, "christian" to 80, "latin" to 60)))
        assertEquals("salsa", ArtistTagStyles.styleFromTags(listOf("salsa" to 100, "salsa romantica" to 40, "cumbia" to 10)))
        assertEquals("rock", ArtistTagStyles.styleFromTags(listOf("rock en español" to 100, "seen live" to 50)))
        assertNull(ArtistTagStyles.styleFromTags(listOf("cumbia" to 100, "salsa" to 90))) // no clear leader
        assertNull(ArtistTagStyles.styleFromTags(listOf("christian" to 100, "latin" to 80))) // say no style
        assertNull(ArtistTagStyles.styleFromTags(listOf("cumbia" to 3))) // one stray, weak tag
        assertNull(ArtistTagStyles.styleFromTags(emptyList()))
    }

    @Test
    fun `a quickly skipped artist is out for the rest of the queue`() {
        val target = StyleContinuity.Target(setOf("cumbia"), TitleLanguage.ES, christian = true)
        val skipped = StyleContinuity.Candidate(
            textStyle = "cumbia", learnedStyle = null, genre = MusicStyle.fromGenre(null), language = null,
            ownArtist = false, fromSearch = true, rejected = true,
        )
        assertEquals(StyleContinuity.Verdict.OFF, StyleContinuity.verdict(target, skipped))
    }

    @Test
    fun `the user's correction reshapes the target`() {
        val detected = StyleContinuity.Target(setOf("cumbia"), TitleLanguage.ES, christian = true)
        assertEquals(setOf("merengue"), StyleContinuity.applyOverride(detected, StyleContinuity.Override.Exact("merengue")).styles)
        val similar = StyleContinuity.applyOverride(detected, StyleContinuity.Override.Similar).styles.orEmpty()
        assertTrue(similar.containsAll(listOf("cumbia", "salsa", "merengue", "bachata")))
        assertTrue("rock" !in similar)
        val any = StyleContinuity.applyOverride(detected, StyleContinuity.Override.AnyStyle)
        assertNull(any.styles)
        assertEquals(TitleLanguage.ES, any.language) // the language is still kept
        assertEquals(detected, StyleContinuity.applyOverride(detected, null))
    }

    @Test
    fun `every style has a name for the queue`() {
        MusicStyle.ALL.forEach { s ->
            val name = MusicStyle.displayName(s.id)
            assertTrue(s.id, name.isNotBlank() && name != s.id)
        }
    }
}
