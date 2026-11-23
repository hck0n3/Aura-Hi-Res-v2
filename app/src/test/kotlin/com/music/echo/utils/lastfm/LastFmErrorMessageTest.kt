package iad1tya.echo.music.utils.lastfm

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 760: a Last.fm write rejected with an error body that does not parse used to throw
 * LastFmException("$method failed: $responseText"). ScrobbleManager logs that exception at ERROR and
 * AppLogger persists ERROR (with the trace) to app.log, the file the user shares — so the raw body
 * went into it. The message now carries the method and the body length only.
 */
class LastFmErrorMessageTest {

    private val fakeToken = "sk_0123456789abcdefFAKETOKEN"
    private val fakeUrl = "https://ws.audioscrobbler.com/2.0/?method=track.scrobble&sk=SESSIONSECRET"
    private val body = "<html>\"error\" upstream proxy failure token=$fakeToken see $fakeUrl</html>"

    @Test
    fun `the message of an unparseable error body never carries the body`() {
        val message = lastFmUnparseableErrorMessage("track.scrobble", body)
        assertFalse(message, message.contains(fakeToken))
        assertFalse(message, message.contains("SESSIONSECRET"))
        assertFalse(message, message.contains("audioscrobbler"))
        assertFalse(message, message.contains("<html>"))
    }

    @Test
    fun `the message keeps the method and the body length for diagnosis`() {
        val message = lastFmUnparseableErrorMessage("track.scrobble", body)
        assertTrue(message, message.contains("track.scrobble"))
        assertTrue(message, message.contains("${body.length} chars"))
    }

    @Test
    fun `the exception built from it carries no body either`() {
        val e = LastFM.LastFmException(-1, lastFmUnparseableErrorMessage("track.updateNowPlaying", body))
        assertFalse(e.toString(), e.toString().contains(fakeToken))
        assertFalse(e.message, e.message.contains(fakeToken))
    }

    @Test
    fun `LastFM never interpolates the response body into an exception or a log`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val f = File(root, "app/src/main/kotlin/com/music/echo/utils/lastfm/LastFM.kt")
        assertTrue("LastFM.kt moved: update this test", f.isFile)
        val source = f.readText()
        assertFalse("raw body interpolated", source.contains("\$responseText"))
        assertFalse("raw body interpolated", source.contains("\${responseText"))
        assertTrue(
            "the unparseable-body path must use the body-free message",
            source.contains("LastFmException(-1, lastFmUnparseableErrorMessage(method, responseText))"),
        )
    }
}
