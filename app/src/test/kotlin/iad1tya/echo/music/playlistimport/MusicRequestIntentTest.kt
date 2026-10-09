package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.reco.TitleLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fila 361 (dueño 2026-10-09): "pedir música no está entendiendo bien el lenguaje natural" — "reggaeton
 * cristiano" → artistas generados por IA; "lo mejor del merengue cristiano 2026" → Jesús Adrián Romero.
 */
class MusicRequestIntentTest {

    @Test
    fun `the owner's two requests are understood`() {
        val a = MusicRequestIntent.parse("reggaeton cristiano")
        assertEquals(setOf("reggaeton"), a.styles)
        assertTrue(a.christian)

        val b = MusicRequestIntent.parse("lo mejor del merengue cristiano 2026")
        assertEquals(setOf("merengue"), b.styles)
        assertTrue(b.christian)
        assertEquals(2026, b.year)
        assertTrue(b.best)
        assertFalse(b.cleanPrompt.contains("2026"))
        assertEquals("merengue cristiano 2026", MusicRequestIntent.styleQuery(b, "merengue"))
    }

    @Test
    fun `whole words only - trap is not rap, popular is not pop`() {
        assertEquals(setOf("trap"), MusicRequestIntent.parse("ponme trap latino").styles)
        assertEquals(emptySet<String>(), MusicRequestIntent.parse("las canciones más populares").styles)
        assertEquals(setOf("salsa", "merengue"), MusicRequestIntent.parse("salsa y merengue para bailar").styles)
    }

    @Test
    fun `negations are excluded and leave the search text`() {
        val i = MusicRequestIntent.parse("música urbana sin reggaeton ni trap")
        assertTrue("reggaeton" in i.excludedStyles)
        assertTrue("trap" in i.excludedStyles)
        assertFalse(i.cleanPrompt.lowercase().contains("reggaeton"))
        val j = MusicRequestIntent.parse("rock en español excepto soda stereo")
        assertEquals(listOf("soda stereo"), j.excludedTerms)
        assertEquals(setOf("rock"), j.styles)
    }

    @Test
    fun `counts are counts, not decades`() {
        val i = MusicRequestIntent.parse("las 20 mejores de salsa")
        assertEquals(20, i.count)
        assertFalse(i.cleanPrompt.contains("20"))
        assertEquals(50, MusicRequestIntent.parse("pon 50 canciones de cumbia").count)
        assertNull(MusicRequestIntent.parse("música de 50 cent").count)
        // A decade stays a decade (handled by MusicRequestQuery on the clean text).
        val d = MusicRequestIntent.parse("rock de los 80")
        assertNull(d.year)
        assertTrue(d.cleanPrompt.contains("80"))
    }

    @Test
    fun `language, era and like-artist`() {
        assertEquals(TitleLanguage.EN, MusicRequestIntent.parse("alabanzas en inglés").language)
        assertEquals(MusicRequestIntent.Era.OLD, MusicRequestIntent.parse("salsa viejita, clásicos").era)
        assertEquals(MusicRequestIntent.Era.NEW, MusicRequestIntent.parse("bachata nueva, estrenos").era)
        val like = MusicRequestIntent.parse("algo parecido a Redimi2")
        assertEquals("Redimi2", like.likeArtist)
        assertFalse(like.cleanPrompt.contains("Redimi2"))
        // "tipo cumbia" names a style, not an artist.
        assertNull(MusicRequestIntent.parse("algo tipo cumbia").likeArtist)
    }

    @Test
    fun `a playlist title must prove the style, and the faith when asked`() {
        assertTrue(MusicRequestIntent.playlistMatches("Merengue Cristiano 2026 🔥", "merengue", christian = true))
        assertFalse(MusicRequestIntent.playlistMatches("Merengue Clásico", "merengue", christian = true))
        assertFalse(MusicRequestIntent.playlistMatches("Alabanzas de Jesús Adrián Romero", "merengue", christian = true))
        assertTrue(MusicRequestIntent.playlistMatches("Reggaeton Cristiano Mix", "reggaeton", christian = true))
    }
}

class MusicRequestStyleGateTest {

    private fun cand(id: String, title: String, artist: String) =
        MusicRequestStyleGate.Cand(id, title, null, listOf(artist))

    @Test
    fun `merengue request - a worship artist is out, playlist-proven merengue stays`() {
        val intent = MusicRequestIntent.parse("lo mejor del merengue cristiano 2026")
        val k = MusicRequestStyleGate.Knowledge(
            genres = emptyMap(),
            tags = mapOf("jesús adrián romero" to "worship"),
            memory = emptyMap(),
            listeners = mapOf("jesús adrián romero" to 300_000L, "grupo merengue" to 5_000L),
        )
        val o = MusicRequestStyleGate.judge(
            intent,
            listOf(
                cand("1", "Te Sigo", "Jesús Adrián Romero"),
                cand("2", "Fiesta en el cielo", "Grupo Merengue"),
                cand("3", "Merengue de alabanza", "Otro"),
            ),
            verified = setOf("2"),
            protectedIds = emptySet(),
            k = k,
            minMatches = 2,
        )
        assertEquals(listOf("2", "3"), o.keptIds)
        assertEquals(1, o.off)
    }

    @Test
    fun `anonymous artists (no Last_fm listeners) are out, unknown lookups are not judged`() {
        val intent = MusicRequestIntent.parse("reggaeton cristiano")
        val k = MusicRequestStyleGate.Knowledge(
            genres = emptyMap(),
            tags = emptyMap(),
            memory = emptyMap(),
            listeners = mapOf("ai beats channel" to 0L, "redimi2" to 80_000L),
        )
        val o = MusicRequestStyleGate.judge(
            intent,
            listOf(
                cand("1", "Reggaeton Cristiano 2026", "AI Beats Channel"),
                cand("2", "Flipando", "Redimi2"),
                cand("3", "Dios es bueno", "Artista sin consultar"),
            ),
            verified = setOf("2"),
            protectedIds = emptySet(),
            k = k,
            minMatches = 1,
        )
        assertEquals(1, o.unpopular)
        assertTrue("1" !in o.keptIds)
        assertTrue("2" in o.keptIds)
    }

    @Test
    fun `a niche style never ends with nothing - the check relaxes step by step`() {
        // Fila 364 (dueño): "pedí trap cristiano y dice que no encontró nada". Small artists Last.fm never
        // catalogued (-1) and titles that name no style: strict leaves nothing, so it relaxes.
        val intent = MusicRequestIntent.parse("trap cristiano")
        val k = MusicRequestStyleGate.Knowledge(
            genres = emptyMap(),
            tags = mapOf("artista salsa" to "salsa"),
            memory = emptyMap(),
            listeners = mapOf("a1" to -1L, "a2" to -1L, "a3" to -1L, "tiny" to 12L, "artista salsa" to -1L),
        )
        val cands = listOf(
            cand("1", "Fe", "A1"),
            cand("2", "Gracia", "A2"),
            cand("3", "Victoria", "A3"),
            cand("4", "Luz", "Tiny"),
            cand("5", "Otra", "Artista Salsa"),
        )
        val strict = MusicRequestStyleGate.judge(intent, cands, emptySet(), emptySet(), k, minMatches = 8)
        assertTrue(strict.keptIds.isEmpty())
        val relaxed = MusicRequestStyleGate.judgeWithFallback(intent, cands, emptySet(), emptySet(), k, target = 3)
        assertEquals(listOf("1", "2", "3"), relaxed.keptIds) // tiny KNOWN artist and another KNOWN style stay out
        assertTrue(relaxed.relaxed > 0)
    }

    @Test
    fun `excluded styles and names are out, what the user named is never judged`() {
        val intent = MusicRequestIntent.parse("música urbana sin reggaeton excepto bad bunny")
        val o = MusicRequestStyleGate.judge(
            intent,
            listOf(
                cand("1", "Reggaeton Mix", "X"),
                cand("2", "Tití me preguntó", "Bad Bunny"),
                cand("3", "Trap Song", "Y"),
                cand("4", "Reggaeton Pinned", "Z"),
            ),
            verified = emptySet(),
            protectedIds = setOf("4"),
            k = MusicRequestStyleGate.Knowledge.EMPTY,
            minMatches = 1,
        )
        assertEquals(listOf("3", "4"), o.keptIds.sorted())
        assertEquals(2, o.excluded)
    }
}
