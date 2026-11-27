package iad1tya.echo.music.reco

import iad1tya.echo.music.reco.TasteSummary.Count
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 906: «Tu gusto» — the local summary, the AI payload (only the allowed fields) and the consent gate. */
class TasteSummaryTest {
    private val genres = mapOf("alpha" to "Salsa", "beta" to "Salsa", "gamma" to "Rock")

    private fun sample() = TasteSummary.stats(
        topArtists = listOf(Count("Alpha", 30), Count("beta", 10), Count("Gamma", 25), Count("ALPHA", 5), Count("  ", 9), Count("Zero", 0)),
        genreOf = { genres[it.lowercase()] },
        songYears = listOf(1995, 1998, 1991, 2004, 2007, null, 1850, 2015),
    )

    // ---- local summary ----

    @Test
    fun `stats merge the same artist, drop blanks and zero plays, and rank by plays`() {
        val stats = sample()
        assertEquals(listOf(Count("Alpha", 35), Count("Gamma", 25), Count("beta", 10)), stats.artists)
        assertEquals(listOf(Count("Salsa", 45), Count("Rock", 25)), stats.genres)
        assertEquals(listOf(1990 to 3, 2000 to 2, 2010 to 1), stats.decades.map { it.decade to it.count })
    }

    @Test
    fun `stats keep at most the documented number of each`() {
        val many = (1..30).map { Count("Artist $it", 100 - it) }
        val stats = TasteSummary.stats(many, { "Genre ${it.takeLast(2)}" }, (1900..2099 step 10).toList())
        assertEquals(TasteSummary.MAX_ARTISTS, stats.artists.size)
        assertEquals(TasteSummary.MAX_GENRES, stats.genres.size)
        assertEquals(TasteSummary.MAX_DECADES, stats.decades.size)
    }

    @Test
    fun `the local summary always works with no network and needs only the stats`() {
        val local = TasteSummary.local(sample())
        assertEquals(listOf("Salsa", "Rock"), local.genres)
        assertEquals(listOf("Alpha", "Gamma", "beta"), local.artists)
        assertEquals(1990, local.decade)
        assertEquals(
            listOf(TasteSummary.ChipKind.GENRE, TasteSummary.ChipKind.GENRE, TasteSummary.ChipKind.DECADE, TasteSummary.ChipKind.DECADE,
                TasteSummary.ChipKind.ARTIST, TasteSummary.ChipKind.ARTIST),
            local.chips.map { it.kind },
        )
        val empty = TasteSummary.stats(emptyList(), { null }, emptyList())
        assertTrue(empty.isEmpty)
        val nothing = TasteSummary.local(empty)
        assertTrue(nothing.genres.isEmpty() && nothing.artists.isEmpty() && nothing.chips.isEmpty())
        assertNull(nothing.decade)
    }

    // ---- payload ----

    @Test
    fun `the payload carries only the allowed fields`() {
        val body = Json.parseToJsonElement(TasteSummary.payload(sample(), "es-419")).jsonObject
        assertEquals(TasteSummary.PAYLOAD_KEYS, body.keys)
        (body["artists"] as JsonArray).forEach { assertEquals(TasteSummary.ARTIST_KEYS, (it as JsonObject).keys) }
        (body["genres"] as JsonArray).forEach { assertEquals(TasteSummary.GENRE_KEYS, (it as JsonObject).keys) }
        (body["decades"] as JsonArray).forEach { assertEquals(TasteSummary.DECADE_KEYS, (it as JsonObject).keys) }
        assertEquals("\"es\"", body["lang"].toString())
        assertEquals(TasteSummary.PAYLOAD_KEYS, setOf("artists", "genres", "decades", "lang"))
        assertEquals(TasteSummary.ARTIST_KEYS, setOf("name", "plays"))
    }

    @Test
    fun `the payload has no timestamps, ids, titles or account data, only names and counts`() {
        val body = TasteSummary.payload(sample(), "en")
        listOf("time", "date", "\"id\"", "Id\"", "title", "email", "account", "cookie", "UC").forEach {
            assertFalse("$it in the payload: $body", body.contains(it, ignoreCase = false))
        }
        assertTrue(body.contains("\"name\":\"Alpha\""))
        assertTrue(body.contains("\"plays\":35"))
        assertTrue(body.contains("\"decade\":1990"))
        assertFalse("the system prompt carries no user data", TasteSummary.systemPrompt("en").contains("Alpha"))
    }

    @Test
    fun `the language is a bare ISO code`() {
        assertEquals("es", TasteSummary.language("es"))
        assertEquals("es", TasteSummary.language("es-419"))
        assertEquals("en", TasteSummary.language(""))
        assertEquals("pt", TasteSummary.language("PT"))
    }

    // ---- the AI answer ----

    @Test
    fun `only the expected JSON answer reaches the screen, trimmed and capped`() {
        val ok = TasteSummary.parseAnswer("Sure! {\"summary\": \"  You love salsa.  \", \"chips\": [\"Salsa\", \"salsa\", \"\", 5, \"Años 90\", \"${"x".repeat(40)}\"]} done")
        assertEquals("You love salsa.", ok?.summary)
        assertEquals(listOf("Salsa", "Años 90"), ok?.chips)
        assertNull(TasteSummary.parseAnswer("no json here"))
        assertNull(TasteSummary.parseAnswer("{\"chips\": [\"a\"]}"))
        assertNull(TasteSummary.parseAnswer("{\"summary\": 3}"))
        assertNull(TasteSummary.parseAnswer("{\"summary\": \"   \"}"))
        val long = TasteSummary.parseAnswer("{\"summary\": \"${"a".repeat(2000)}\"}")
        assertEquals(TasteSummary.MAX_SUMMARY_CHARS, long?.summary?.length)
        val manyChips = TasteSummary.parseAnswer("{\"summary\": \"s\", \"chips\": [${(1..10).joinToString(",") { "\"c$it\"" }}]}")
        assertEquals(TasteSummary.MAX_CHIPS, manyChips?.chips?.size)
    }

    // ---- consent gate ----

    @Test
    fun `nothing is sent before the consent is accepted`() = runBlocking {
        var sent = 0
        val send: suspend (String, String) -> String? = { _, _ -> sent++; "{\"summary\": \"s\"}" }
        assertEquals(TasteAiGate.Outcome.NotAccepted, TasteAiGate.ask(null, sample(), "es", send))
        assertEquals(TasteAiGate.Outcome.NotAccepted, TasteAiGate.ask(0, sample(), "es", send))
        assertEquals("an older consent does not count", TasteAiGate.Outcome.NotAccepted, TasteAiGate.ask(TasteAiGate.CONSENT_VERSION - 1, sample(), "es", send))
        assertEquals(TasteAiGate.Outcome.NotAccepted, TasteAiGate.ask(TasteAiGate.CONSENT_VERSION + 1, sample(), "es", send))
        assertEquals(0, sent)
    }

    @Test
    fun `with consent it sends exactly the payload once, and nothing when there is nothing to describe`() = runBlocking {
        val bodies = ArrayList<String>()
        val send: suspend (String, String) -> String? = { system, user -> bodies += user; assertTrue(system.isNotBlank()); "{\"summary\": \"You love salsa.\", \"chips\": [\"Salsa\"]}" }
        val stats = sample()
        val outcome = TasteAiGate.ask(TasteAiGate.CONSENT_VERSION, stats, "es", send)
        assertEquals(TasteAiGate.Outcome.Done(TasteSummary.AiTaste("You love salsa.", listOf("Salsa"))), outcome)
        assertEquals(listOf(TasteSummary.payload(stats, "es")), bodies)
        val empty = TasteSummary.stats(emptyList(), { null }, listOf(1990))
        assertEquals(TasteAiGate.Outcome.NothingToSend, TasteAiGate.ask(TasteAiGate.CONSENT_VERSION, empty, "es", send))
        assertEquals(1, bodies.size)
    }

    @Test
    fun `a failed or unusable answer is a failure, never a crash`() = runBlocking {
        val stats = sample()
        assertEquals(TasteAiGate.Outcome.Failed, TasteAiGate.ask(TasteAiGate.CONSENT_VERSION, stats, "es") { _, _ -> null })
        assertEquals(TasteAiGate.Outcome.Failed, TasteAiGate.ask(TasteAiGate.CONSENT_VERSION, stats, "es") { _, _ -> "busy" })
        assertEquals(TasteAiGate.Outcome.Failed, TasteAiGate.ask(TasteAiGate.CONSENT_VERSION, stats, "es") { _, _ -> error("network") })
    }
}
