package iad1tya.echo.music.reco

import iad1tya.echo.music.reco.StyleContinuity.Verdict
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 459 — dueño (2026-10-20): *«estaba escuchando rap cristiano en español y continúa con reggaetón y luego con
 * hip hop en inglés, nada que ver con la cola; solo mantuvo que era cristiano»*. These pin the lane logic with the
 * owner's own case: what a Spanish Christian-rap context accepts, what it never plays, and that a context which
 * really mixes languages stays mixed.
 */
class ContextLaneTest {

    // ── The owner's context: Spanish Christian rap ────────────────────────────────────────────────────────────
    private val rapEs = StyleContinuity.Target(setOf("rap"), TitleLanguage.ES, christian = true)
    private val rapEsLane = ContextLane.Lane(TitleLanguage.ES, mixed = false, christian = true, families = emptySet())

    /** A radio candidate, judged exactly like the gate does: style verdict first, then the lane's evidence. */
    private fun candidate(
        title: String,
        learned: String? = null,
        genre: String? = null,
        artistLanguage: String? = null,
        spanishHint: Boolean = false,
        christian: Boolean = false,
        own: Boolean = false,
        target: StyleContinuity.Target = rapEs,
    ): ContextLane.Candidate {
        val evidence = ContextLane.candidateLanguage(ContextLane.languageOfText(title), artistLanguage, spanishHint)
        val textStyle = MusicStyle.fromText(title)
        val info = MusicStyle.fromGenre(genre)
        val verdict = StyleContinuity.verdict(
            target,
            StyleContinuity.Candidate(
                textStyle = textStyle,
                learnedStyle = learned,
                genre = info,
                language = evidence.code.takeIf { evidence.strong },
                ownArtist = own,
                fromSearch = false,
            ),
        )
        val family = MusicStyle.familyOf(textStyle ?: learned ?: info.style) ?: info.family
        return ContextLane.Candidate(verdict, evidence, family, christian)
    }

    private fun tierOf(c: ContextLane.Candidate, lane: ContextLane.Lane = rapEsLane) = ContextLane.tier(lane, c)

    @Test
    fun `spanish christian rap is the best step`() {
        val c = candidate("Mi Testimonio", learned = "rap", christian = true)
        assertEquals(Verdict.MATCH, c.verdict)
        assertEquals(0, tierOf(c))
    }

    @Test
    fun `spanish christian reggaeton is out - rap is not reggaeton`() {
        val c = candidate("Dios Es Real", learned = "reggaeton", christian = true)
        assertEquals(Verdict.OFF, c.verdict)
        assertNull(tierOf(c))
    }

    @Test
    fun `english christian hip hop is out whatever its style says`() {
        // Its own title says English.
        val byTitle = candidate("Gods Plan", genre = "Hip-Hop/Rap", christian = true)
        assertNull(tierOf(byTitle))
        // The title says nothing («Jordan»), but the app learned that the artist sings in English.
        val byArtist = candidate("Jordan", genre = "Hip-Hop/Rap", artistLanguage = TitleLanguage.EN, christian = true)
        assertNull(tierOf(byArtist))
        // Even a title that names the style ("Rap God") cannot vouch for itself when it is plainly English.
        val named = candidate("Rap God", christian = true)
        assertNull(tierOf(named))
    }

    @Test
    fun `an unproven language never beats a proven one`() {
        val spanish = candidate("Mi Testimonio", learned = "rap", christian = true)
        val unknown = candidate("Jordan", genre = "Hip-Hop/Rap")
        assertEquals(0, tierOf(spanish))
        assertEquals(1, tierOf(unknown)) // eligible (#39/#41) but only as a step further down
        val kept = ContextLane.choose(
            listOf("unknown", "spanish-1", "spanish-2"),
            listOf(tierOf(unknown), tierOf(spanish), tierOf(spanish)),
            listOf(false, true, true),
            enough = 2,
        )
        assertEquals(listOf("spanish-1", "spanish-2"), kept)
    }

    @Test
    fun `a spanish hint proves the language for ranking only`() {
        val hinted = candidate("Jordan", genre = "Hip-Hop/Rap", spanishHint = true)
        assertEquals(0, tierOf(hinted))
        // The hint of a Spanish artist never excludes it from an ENGLISH context either.
        val english = ContextLane.Lane(TitleLanguage.EN, mixed = false, christian = false, families = emptySet())
        val enTarget = StyleContinuity.Target(setOf("rap"), TitleLanguage.EN, christian = false)
        val hintedForEnglish = candidate("Jordan", genre = "Hip-Hop/Rap", spanishHint = true, target = enTarget)
        assertEquals(1, tierOf(hintedForEnglish, english))
    }

    // ── A context that mixes languages stays mixed ─────────────────────────────────────────────────────────────
    @Test
    fun `a mixed context judges no language`() {
        val evidence = listOf("es", "en", "es", "en").map { ContextLane.LangEvidence(it, strong = true) }
        val language = ContextLane.contextLanguage(evidence, minDecided = 2)
        assertNull(language.code)
        assertTrue(language.mixed)
        val mixedTarget = StyleContinuity.Target(setOf("rap"), language.code, christian = true)
        val mixedLane = ContextLane.Lane(language.code, language.mixed, christian = true, families = emptySet())
        val english = candidate("Gods Plan", genre = "Hip-Hop/Rap", christian = true, target = mixedTarget)
        val spanish = candidate("Mi Testimonio", learned = "rap", christian = true, target = mixedTarget)
        assertEquals(0, tierOf(english, mixedLane))
        assertEquals(0, tierOf(spanish, mixedLane))
    }

    @Test
    fun `the context language needs a clear majority of decided tracks`() {
        fun ev(vararg codes: String?) = codes.map { ContextLane.LangEvidence(it, strong = it != null) }
        assertEquals("es", ContextLane.contextLanguage(ev("es"), 1).code) // one anchor song is enough
        assertNull(ContextLane.contextLanguage(ev("es"), 2).code) // a collection needs two decided
        assertEquals("es", ContextLane.contextLanguage(ev("es", "es", "es", "es", "en", null, null), 2).code) // 80 %
        assertTrue(ContextLane.contextLanguage(ev("es", "es", "es", "en"), 2).mixed) // 75 %: mixed, not Spanish
        assertNull(ContextLane.contextLanguage(ev(null, null), 1).code)
        // A list written in another script never becomes THE language (a K-pop list mixes scripts and English titles).
        assertNull(ContextLane.contextLanguage(ev("x", "x", "x"), 1).code)
    }

    @Test
    fun `hints alone give a weak spanish only when they cover the context`() {
        val hint = ContextLane.LangEvidence(TitleLanguage.ES, strong = false)
        val anchor = ContextLane.contextLanguage(listOf(hint), minDecided = 1)
        assertEquals("es", anchor.code)
        assertTrue(anchor.weak)
        // One hint in a context of three says too little.
        assertNull(ContextLane.contextLanguage(listOf(hint, ContextLane.LangEvidence.NONE, ContextLane.LangEvidence.NONE), 1).code)
        // Strong evidence outranks hints: an English title wins over a Spanish hint.
        val english = ContextLane.LangEvidence(TitleLanguage.EN, strong = true)
        assertEquals("en", ContextLane.contextLanguage(listOf(english, hint), minDecided = 1).code)
    }

    // ── The language of a text ─────────────────────────────────────────────────────────────────────────────────
    @Test
    fun `titles that the base vocabulary leaves undecided are read with more evidence`() {
        assertEquals("es", ContextLane.languageOfText("Mi Testimonio"))
        assertEquals("es", ContextLane.languageOfText("Libertad"))
        assertEquals("es", ContextLane.languageOfText("Sueños"))
        assertEquals("es", ContextLane.languageOfText("¿Quién Como Tú?"))
        assertEquals("es", ContextLane.languageOfText("Canción de Cuna"))
        assertEquals("pt", ContextLane.languageOfText("Coração Valente"))
        assertEquals("en", ContextLane.languageOfText("Gods Plan"))
        assertEquals("en", ContextLane.languageOfText("Rise Up"))
        assertEquals(ContextLane.OTHER_SCRIPT, ContextLane.languageOfText("愛してる"))
        assertEquals(ContextLane.OTHER_SCRIPT, ContextLane.languageOfText("Привет мир"))
    }

    @Test
    fun `a name or a mixed title decides nothing`() {
        assertNull(ContextLane.languageOfText("Jordan"))
        assertNull(ContextLane.languageOfText("Despacito (feat. Justin Bieber) [Remix]"))
        assertNull(ContextLane.languageOfText("Amor"))
        assertNull(ContextLane.languageOfText("Libre Free")) // one word of each: undecided
        assertNull(ContextLane.languageOfText(""))
        assertNull(ContextLane.languageOfText(null))
        // The base vocabulary keeps speaking first.
        assertEquals("en", ContextLane.languageOfText("Goodness of God"))
        assertEquals("es", ContextLane.languageOfText("Eres Todo Para Mí"))
    }

    // ── Genre family and short contexts ────────────────────────────────────────────────────────────────────────
    @Test
    fun `a short collection still has a style when the only one known covers it`() {
        assertEquals(setOf("rap"), ContextLane.fewKnownStyles(listOf("rap", null)))
        assertNull(ContextLane.fewKnownStyles(listOf("rap", null, null))) // known on a third only
        assertNull(ContextLane.fewKnownStyles(listOf("rap", "reggaeton"))) // two styles: not one
        assertNull(ContextLane.fewKnownStyles(listOf("rap", "rap", "rap"))) // three known: the regular rule decides
        assertNull(ContextLane.fewKnownStyles(listOf(null, null)))
    }

    @Test
    fun `the family hint exists only while the concrete style is unknown`() {
        assertEquals(setOf("urbano"), ContextLane.familyHint(null, listOf("urbano", null, "urbano")))
        assertEquals(emptySet<String>(), ContextLane.familyHint(setOf("rap"), listOf("urbano", "urbano")))
        assertEquals(emptySet<String>(), ContextLane.familyHint(null, listOf("urbano", null, null))) // a third known
        // A mixed list keeps every family above the floor, and drops a stray one.
        assertEquals(setOf("urbano", "rock"), ContextLane.familyHint(null, List(6) { "urbano" } + List(2) { "rock" }))
        assertEquals(setOf("urbano"), ContextLane.familyHint(null, List(7) { "urbano" } + "rock"))
    }

    @Test
    fun `with the concrete style unknown the genre family is a gate`() {
        val target = StyleContinuity.Target(null, TitleLanguage.ES, christian = true)
        val lane = ContextLane.Lane(TitleLanguage.ES, mixed = false, christian = true, families = setOf("urbano"))
        val urban = candidate("Mi Testimonio", learned = "reggaeton", christian = true, target = target)
        val rock = candidate("Mi Testimonio", learned = "rock", christian = true, target = target)
        val unknown = candidate("Jordan", christian = true, target = target)
        assertEquals(0, tierOf(urban, lane)) // rap and reggaeton share the family: the honest limit without a style
        assertNull(tierOf(rock, lane))
        assertEquals(3, tierOf(unknown, lane)) // nothing known: eligible (#39/#41), a step further down
    }

    // ── The widening ladder ────────────────────────────────────────────────────────────────────────────────────
    @Test
    fun `an unknown style is admitted in small steps, same language and faith first`() {
        val lane = rapEsLane
        // Style unknown (no learned style, no genre): only the language and the faith can place it.
        val sameLanguageChristian = candidate("Mi Testimonio", christian = true)
        val sameLanguage = candidate("Mi Testimonio")
        val unprovenChristian = candidate("Jordan", christian = true)
        val unprovenSecular = candidate("Jordan")
        assertEquals(2, tierOf(sameLanguageChristian, lane))
        // Row 503: in a Christian lane faith is a GATE — a candidate not shown to be Christian is never admitted.
        assertNull(tierOf(sameLanguage, lane))
        assertEquals(3, tierOf(unprovenChristian, lane))
        assertNull(tierOf(unprovenSecular, lane))

        val items = listOf("secular", "unproven-christian", "spanish-christian", "spanish-secular")
        val tiers = listOf(tierOf(unprovenSecular), tierOf(unprovenChristian), tierOf(sameLanguageChristian), tierOf(sameLanguage))
        val faith = listOf(false, true, true, false)
        assertEquals(listOf("spanish-christian"), ContextLane.choose(items, tiers, faith, enough = 1))
        assertEquals(listOf("spanish-christian", "unproven-christian"), ContextLane.choose(items, tiers, faith, enough = 2))
        // However much is wanted, the secular ones never come in.
        assertEquals(2, ContextLane.choose(items, tiers, faith, enough = 4).size)
    }

    // ── Row 503 (owner 2026-10-29): faith is a gate, and the relaxation keeps a fence ────────────────────────────────
    @Test
    fun `a secular song with the right style and language is not admitted in a christian lane`() {
        // The leak: «rap cristiano» continued with Spanish rap that was not Christian — style and language matched, so it
        // took step 0/1 and the faith only ordered the step.
        val secularRap = candidate("Mi Testimonio", learned = "rap")
        assertNull(tierOf(secularRap))
        val christianRap = candidate("Mi Testimonio", learned = "rap", christian = true)
        assertEquals(0, tierOf(christianRap))
    }

    @Test
    fun `a non christian lane still admits what it cannot place - only a christian lane gates faith`() {
        val lane = ContextLane.Lane(TitleLanguage.ES, mixed = false, christian = false, families = emptySet())
        val target = StyleContinuity.Target(setOf("rap"), TitleLanguage.ES, christian = false)
        val unproven = candidate("Jordan", target = target)
        assertEquals(3, tierOf(unproven, lane)) // language unproven, nothing excludes it
    }

    @Test
    fun `luis miguel never continues into cumbia - the neighbourhood of balada and bolero is pop and iberian`() {
        val neighbours = MusicStyle.neighbourFamilies(setOf("bolero"))
        assertTrue(MusicStyle.FAMILY_IBERIAN in neighbours)
        assertTrue(MusicStyle.FAMILY_POP in neighbours)
        assertFalse(MusicStyle.FAMILY_TROPICAL in neighbours)
        assertEquals(neighbours, MusicStyle.neighbourFamilies(setOf("balada")))
        // The lane the first relaxation level keeps: a cumbia (known only by its family) is out, a ballad stays.
        val lane = ContextLane.Lane(TitleLanguage.ES, mixed = false, christian = false, families = neighbours)
        val target = StyleContinuity.Target(null, TitleLanguage.ES, christian = false)
        val cumbia = candidate("Mi Testimonio", learned = "cumbia", target = target)
        val ballad = candidate("Mi Testimonio", learned = "balada", target = target)
        assertNull(tierOf(cumbia, lane))
        assertEquals(0, tierOf(ballad, lane))
        assertTrue(MusicStyle.neighbourFamilies(null).isEmpty())
    }

    @Test
    fun `a christian context with a language always has a christian search to fall back on`() {
        assertEquals("música cristiana", StyleContinuity.christianQuery(TitleLanguage.ES))
        assertEquals("christian music", StyleContinuity.christianQuery(TitleLanguage.EN))
        assertNull(StyleContinuity.christianQuery(null)) // never guess a language
    }

    @Test
    fun `an excluded candidate never comes back however much is wanted`() {
        val reggaeton = candidate("Dios Es Real", learned = "reggaeton", christian = true)
        val english = candidate("Gods Plan", genre = "Hip-Hop/Rap", christian = true)
        val kept = ContextLane.choose(
            listOf("reggaeton", "english"),
            listOf(tierOf(reggaeton), tierOf(english)),
            listOf(true, true),
            enough = Int.MAX_VALUE,
        )
        assertTrue(kept.isEmpty())
    }

    @Test
    fun `provably christian candidates lead their step`() {
        val kept = ContextLane.choose(
            listOf("a", "b", "c"),
            listOf(0, 0, 0),
            listOf(false, true, false),
            enough = 3,
        )
        assertEquals(listOf("b", "a", "c"), kept) // the caller's order is kept inside each group
    }

    @Test
    fun `without a language or a family the lane changes nothing`() {
        val lane = ContextLane.Lane(null, mixed = false, christian = false, families = emptySet())
        fun c(verdict: Verdict) = ContextLane.Candidate(verdict, ContextLane.LangEvidence.NONE, null, false)
        assertEquals(0, ContextLane.tier(lane, c(Verdict.MATCH)))
        assertEquals(2, ContextLane.tier(lane, c(Verdict.UNKNOWN)))
        assertNull(ContextLane.tier(lane, c(Verdict.OFF)))
        assertFalse(lane.extra)
    }

    // ── Wiring: the gate really uses the lane, and the engine keeps the language until the last step ───────────
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the exact style gate ranks by the lane, learns languages and fills from the context's own artists`() {
        val gate = main("playback/ExactStyleGate.kt")
        assertTrue(gate.contains("ContextLane.tier(lane, "))
        assertTrue(gate.contains("ContextLane.choose("))
        assertTrue(gate.contains("ArtistLanguageMemory.learn(service, languageObservations)"))
        assertTrue(gate.contains("ContextLane.contextLanguage("))
        assertTrue("el relleno sale de los artistas del contexto", gate.contains("laneFillPage(seedArtists)"))
        assertTrue("acotado: presupuesto de páginas de artista por cola", gate.contains("LANE_FILL_REQUEST_BUDGET"))
        // Only STRONG evidence may call a candidate «another language».
        assertTrue(gate.contains("language = evidence.code.takeIf { evidence.strong }"))
    }

    @Test
    fun `the engine never switches the gate off - level 3 drops the family fence only`() {
        val engine = main("playback/RadioContinuationEngine.kt")
        // Row 503: it used to read `relaxLevel < 3`, i.e. at the last level ANYTHING played (the cumbia after Luis Miguel).
        assertFalse(engine.contains("keepGenreLaneHint && relaxLevel < 3"))
        assertTrue(engine.contains("languageOnly = relaxLevel >= 2"))
        assertTrue(engine.contains("dropFamilies = relaxLevel >= 3"))
        assertTrue(engine.contains("relaxLevel = 3"))
    }

    @Test
    fun `the gate proves faith by the context's own artists and by a christian search, and keeps searching when relaxed`() {
        val gate = main("playback/ExactStyleGate.kt")
        assertTrue(gate.contains("lane.christian && (ownArtist || faithSource)"))
        assertTrue(gate.contains("faithSource: Boolean = fromSearch && gateTarget.christian"))
        assertTrue(gate.contains("(!languageOnly || lane.christian)"))
        assertTrue(gate.contains("StyleContinuity.christianQuery(gateTarget.language)"))
        // A Christian context is never left to the raw list because it names no language or style.
        assertTrue(gate.contains("!gateTarget.active && lane.families.isEmpty() && !lane.christian"))
        assertTrue(gate.contains("target.language == null && !target.christian"))
        assertTrue(gate.contains("target.neighbourFamilies"))
    }

    @Test
    fun `the gate log carries counts and family ids only`() {
        val gate = main("playback/ExactStyleGate.kt")
        val log = gate.substringAfter("\"STYLE_GATE %s: styles=%s lang=%s").substringBefore("kept\n}")
        assertTrue(log.contains("steps=%s never=%d only=%b"))
        assertFalse("no se registra ningún título/artista", log.contains("title") || log.contains(".name"))
    }

    // ── Learned artist language and Last.fm lane hints ─────────────────────────────────────────────────────────
    @Test
    fun `an artist language needs two observations and a clear majority`() {
        var m: ArtistLanguageMemory.Memory = emptyMap()
        m = ArtistLanguageMemory.merged(m, listOf("Redimi2" to "es"))
        assertNull(ArtistLanguageMemory.languageOf(m, "redimi2"))
        m = ArtistLanguageMemory.merged(m, listOf("redimi2 " to "es"))
        assertEquals("es", ArtistLanguageMemory.languageOf(m, "Redimi2"))
        m = ArtistLanguageMemory.merged(m, listOf("Redimi2" to "en", "Redimi2" to "en"))
        assertNull(ArtistLanguageMemory.languageOf(m, "Redimi2")) // bilingual: nothing is judged by it
    }

    @Test
    fun `last fm tags give a family and a spanish hint, never more`() {
        val tags = listOf("christian hip hop" to 100, "reggaeton" to 70, "latin" to 40, "seen live" to 5)
        assertEquals(MusicStyle.FAMILY_URBAN, ArtistTagStyles.familyFromTags(tags))
        assertEquals("es", ArtistTagStyles.languageFromTags(tags))
        assertNull(ArtistTagStyles.languageFromTags(listOf("rock" to 100, "latin" to 5)))
        assertNull(ArtistTagStyles.familyFromTags(listOf("christian hip hop" to 50, "rock" to 50)))
        val lane = ArtistTagStyles.TagLane("urbano", "es")
        assertEquals(lane, ArtistTagStyles.decodeLane(ArtistTagStyles.encodeLane(lane)))
        assertEquals(ArtistTagStyles.TagLane(null, "es"), ArtistTagStyles.decodeLane("|es"))
    }
}
