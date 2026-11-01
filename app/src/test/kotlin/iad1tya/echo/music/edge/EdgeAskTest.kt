package iad1tya.echo.music.edge

import iad1tya.echo.music.api.TrackQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeAskTest {
    private val tracks = (1..3).map { TrackQuery("Song $it", "Artist") }

    @Test
    fun theKeyIsAHashThatNeverContainsTheText() {
        val k = EdgeAsk.key("Rock cristiano de los 90", 20)
        assertEquals(64, k.length)
        assertTrue(k.all { it in "0123456789abcdef" })
        assertFalse(k.contains("rock", ignoreCase = true))
    }

    @Test
    fun spacingCaseAndPunctuationShareOneAnswerButTheCountDoesNot() {
        assertEquals(EdgeAsk.key("Rock  cristiano, 90s!", 20), EdgeAsk.key("rock cristiano 90s", 20))
        assertNotEquals(EdgeAsk.key("rock cristiano", 20), EdgeAsk.key("rock cristiano", 30))
        assertNotEquals(EdgeAsk.key("rock cristiano", 20), EdgeAsk.key("pop cristiano", 20))
    }

    @Test
    fun theBodyRoundTripsThroughTheParser() {
        val body = EdgeAsk.body(EdgeAsk.key("x", 3), tracks)
        val sent = kotlinx.serialization.json.Json.parseToJsonElement(body).toString()
        assertTrue(sent.contains("\"k\""))
        assertEquals(tracks, EdgeAsk.parse("""{"tracks":[{"title":"Song 1","artist":"Artist"},{"title":"Song 2","artist":"Artist"},{"title":"Song 3","artist":"Artist"}]}"""))
    }

    @Test
    fun junkAndTinyAnswersAreAMiss() {
        assertNull(EdgeAsk.parse("not json"))
        assertNull(EdgeAsk.parse("""{"miss":true}"""))
        assertNull(EdgeAsk.parse("""{"tracks":[{"title":"a","artist":"b"}]}"""))
        assertNull(EdgeAsk.parse("""{"tracks":[{"title":"","artist":"b"},{"title":"a"},{"title":"c","artist":"d"}]}"""))
    }
}
