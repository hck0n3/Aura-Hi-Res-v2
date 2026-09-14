package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class StreamLoadErrorPolicyTest {
    private class HttpStatus(val code: Int, cause: Throwable? = null) : IOException("HTTP $code", cause)

    private val codeOf: (Throwable) -> Int? = { (it as? HttpStatus)?.code }

    @Test
    fun forbiddenAndGoneAreDeadUrls() {
        assertEquals(403, StreamLoadErrorPolicy.deadResponseCodeIn(HttpStatus(403), codeOf))
        assertEquals(410, StreamLoadErrorPolicy.deadResponseCodeIn(HttpStatus(410), codeOf))
    }

    @Test
    fun deadStatusIsFoundInsideWrappers() {
        val wrapped = IllegalStateException("source error", IOException("loader", HttpStatus(403)))
        assertEquals(403, StreamLoadErrorPolicy.deadResponseCodeIn(wrapped, codeOf))
    }

    @Test
    fun otherErrorsKeepDefaultRetries() {
        assertNull(StreamLoadErrorPolicy.deadResponseCodeIn(HttpStatus(416), codeOf))
        assertNull(StreamLoadErrorPolicy.deadResponseCodeIn(HttpStatus(503), codeOf))
        assertNull(StreamLoadErrorPolicy.deadResponseCodeIn(IOException("timeout"), codeOf))
    }
}
