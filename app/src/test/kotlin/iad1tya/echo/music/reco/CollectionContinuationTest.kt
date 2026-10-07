package iad1tya.echo.music.reco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Row 344 (owner 2026-10-07): "siguen las primeras tres canciones de acuerdo, después mete otra cosa que nada
 * que ver y después cambia a otro idioma y otro género… cuando terminó el álbum".
 */
class CollectionContinuationTest {

    private val album = listOf("a1", "a2", "a3", "a4", "a5", "a6", "a7")

    @Test
    fun `only a collection gets the collection rules`() {
        assertTrue(CollectionContinuation.isCollection(anchored = false, poolSize = 7))
        assertFalse(CollectionContinuation.isCollection(anchored = true, poolSize = 7))
        assertFalse(CollectionContinuation.isCollection(anchored = false, poolSize = 1))
    }

    @Test
    fun `each round seeds from tracks of the collection not used yet`() {
        val used = HashSet<String>()
        val first = CollectionContinuation.rotateSeeds(album, used, 5).also { used += it }
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5"), first)
        val second = CollectionContinuation.rotateSeeds(album, used, 5).also { used += it }
        assertEquals(listOf("a6", "a7", "a1", "a2", "a3"), second)
        // Duplicates in the preference list never take two slots.
        assertEquals(listOf("a1", "a2"), CollectionContinuation.rotateSeeds(listOf("a1", "a1", "a2"), emptySet(), 5))
    }

    @Test
    fun `a fallback never seeds from a radio pick that drifted out of the collection`() {
        val r = Random(1)
        // The playing song is a radio pick, not an album track: the seed comes from the album.
        val seed = CollectionContinuation.fallbackSeed("drifted", album, setOf("a1", "a2"), anchored = false, random = r)
        assertTrue(seed in album && seed != "a1" && seed != "a2")
        // An album track playing right now is a fine seed.
        assertEquals("a4", CollectionContinuation.fallbackSeed("a4", album, emptySet(), anchored = false, random = r))
        // Everything used: still an album track, never the drifted one.
        assertTrue(CollectionContinuation.fallbackSeed("drifted", album, album.toSet(), false, r) in album)
        // A single-song radio keeps its own behaviour.
        assertEquals("x", CollectionContinuation.fallbackSeed("x", listOf("x"), emptySet(), anchored = true, random = r))
    }

    @Test
    fun `titles in spanish english and portuguese are told apart`() {
        assertEquals(TitleLanguage.ES, TitleLanguage.detect("Eres Todo Para Mí"))
        assertEquals(TitleLanguage.ES, TitleLanguage.detect("Cuán Grande Es Él"))
        assertEquals(TitleLanguage.ES, TitleLanguage.detect("Quiero Estar Contigo (En Vivo)"))
        assertEquals(TitleLanguage.EN, TitleLanguage.detect("Way Maker (Live)"))
        assertEquals(TitleLanguage.EN, TitleLanguage.detect("Goodness of God"))
        assertEquals(TitleLanguage.EN, TitleLanguage.detect("I Will Always Love You"))
        assertEquals(TitleLanguage.PT, TitleLanguage.detect("Deus do Impossível"))
        assertEquals(TitleLanguage.PT, TitleLanguage.detect("Você Não Está Sozinho"))
    }

    @Test
    fun `a doubtful title is never judged`() {
        assertNull(TitleLanguage.detect("Amor"))
        assertNull(TitleLanguage.detect("Hosanna"))
        assertNull(TitleLanguage.detect("1999"))
        assertNull(TitleLanguage.detect("Despacito (feat. Justin Bieber) [Remix]"))
        assertNull(TitleLanguage.detect(""))
    }

    @Test
    fun `a collection has a language only when it clearly has one`() {
        val spanish = listOf(
            "Eres Todo Para Mí", "Cuán Grande Es Él", "Mi Dios Es Real", "Tu Fidelidad", "Hosanna", "Amor",
        )
        assertEquals(TitleLanguage.ES, CollectionContinuation.dominantLanguage(spanish))
        // Bilingual: no dominant language → nothing is filtered by language.
        val mixed = listOf("Eres Todo Para Mí", "Mi Dios Es Real", "Way Maker", "Goodness of God", "Tu Fidelidad", "I Will Follow You")
        assertNull(CollectionContinuation.dominantLanguage(mixed))
        // Too few decided titles: no decision.
        assertNull(CollectionContinuation.dominantLanguage(listOf("Mi Dios Es Real", "Amor", "Hosanna")))
    }

    @Test
    fun `only a clearly other-language candidate is off`() {
        assertTrue(CollectionContinuation.offLanguage(TitleLanguage.ES, "Goodness of God"))
        assertFalse(CollectionContinuation.offLanguage(TitleLanguage.ES, "Mi Dios Es Real"))
        assertFalse(CollectionContinuation.offLanguage(TitleLanguage.ES, "Hosanna"))
        assertFalse(CollectionContinuation.offLanguage(null, "Goodness of God"))
    }
}

/** Row 345b (owner 2026-10-07): "cumbia cristiana… cuando la lista termina no continúa con el mismo género". */
class CollectionStyleTest {
    private val tropical = GenreLane.STYLE_TROPICAL
    private val worship = GenreLane.STYLE_WORSHIP

    @Test
    fun `the collection's own name decides its style`() {
        val titleStyle = GenreLane.styleOfTrack(emptyMap(), null, "Cumbias Cristianas 2026")
        assertEquals(tropical, titleStyle)
        // Even when iTunes only knows its artists as "Christian" (= worship), a cumbia list stays cumbia.
        val allowed = CollectionContinuation.allowedStyles(listOf(worship, worship, worship, worship), titleStyle)
        assertEquals(setOf(tropical), allowed)
        assertTrue(CollectionContinuation.offStyle(allowed, worship))
        assertFalse(CollectionContinuation.offStyle(allowed, tropical))
    }

    @Test
    fun `a mixed collection keeps every style it really has`() {
        val styles = listOf(tropical, tropical, tropical, worship, worship, null, null)
        assertEquals(setOf(tropical, worship), CollectionContinuation.allowedStyles(styles, null))
        // A single stray track (under the floor) is not a style of the collection.
        val mostlyTropical = List(9) { tropical } + GenreLane.STYLE_URBAN
        assertEquals(setOf(tropical), CollectionContinuation.allowedStyles(mostlyTropical, null))
    }

    @Test
    fun `too little known means nothing is filtered by style`() {
        assertNull(CollectionContinuation.allowedStyles(listOf(tropical, null, null, null), null))
        assertFalse(CollectionContinuation.offStyle(null, worship))
    }

    @Test
    fun `an unknown candidate style is never off`() {
        assertFalse(CollectionContinuation.offStyle(setOf(tropical), null))
    }

    @Test
    fun `song titles carry their style`() {
        assertEquals(tropical, GenreLane.styleOfTrack(emptyMap(), null, "Cumbia del Espíritu"))
        assertEquals(worship, GenreLane.styleOfTrack(emptyMap(), null, "Alabanza de Júbilo"))
        assertNull(GenreLane.styleOfTrack(emptyMap(), null, "Mi Dios Es Real"))
    }
}
