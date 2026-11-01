package iad1tya.echo.music.reco

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 524 — owner 2026-10-29: after a Christian-rap list the smart queue went to another artist («Tercer Cielo») and
 * «la cola nunca tiene que cambiar de género». The gate could not be reproduced from the log, which only had counts: this pins
 * the new evidence label and its summary line, so the next shared log says WHICH proof let each kept song in.
 */
class StyleBasisTest {
    private val rap = StyleContinuity.Target(setOf("rap"), TitleLanguage.ES, christian = true)

    private fun cand(
        text: String? = null,
        learned: String? = null,
        genre: String? = null,
        own: Boolean = false,
        search: Boolean = false,
        rejected: Boolean = false,
    ) = StyleContinuity.Candidate(
        textStyle = MusicStyle.fromText(text),
        learnedStyle = learned,
        genre = MusicStyle.fromGenre(genre),
        language = null,
        ownArtist = own,
        fromSearch = search,
        rejected = rejected,
    )

    @Test
    fun `the label follows the order the verdict reads its evidence in`() {
        assertEquals("rejected", StyleContinuity.basis(rap, cand(own = true, rejected = true)))
        assertEquals("text", StyleContinuity.basis(rap, cand(text = "Rap cristiano", own = true)))
        assertEquals("own", StyleContinuity.basis(rap, cand(own = true, genre = "Hip-Hop/Rap")))
        assertEquals("search", StyleContinuity.basis(rap, cand(search = true, genre = "Hip-Hop/Rap")))
        assertEquals("learned", StyleContinuity.basis(rap, cand(learned = "rap", genre = "Pop")))
        assertEquals("tag", StyleContinuity.basis(rap, cand(genre = "Hip-Hop/Rap")))
        assertEquals("family", StyleContinuity.basis(rap, cand()))
        assertEquals("inactive", StyleContinuity.basis(StyleContinuity.Target(null, null, christian = false), cand()))
    }

    @Test
    fun `the gate logs what let the kept songs in, as counts only`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val s = File(root, "app/src/main/kotlin/com/music/echo/playback/ExactStyleGate.kt").readText()
        assertTrue(s.contains("STYLE_GATE_BASIS %s: kept by %s"))
        assertTrue(s.contains("basisById[mi.mediaId] = StyleContinuity.basis("))
        assertFalse("sin títulos ni artistas en el registro", Regex("""STYLE_GATE_BASIS[^\n]*(title|artist\b)""").containsMatchIn(s))
    }
}
