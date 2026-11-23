package iad1tya.echo.music.utils

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 761: `RESOLVE_FAIL` (WARNING) and the proxy failure line (ERROR) are persisted to the shared
 * app.log. They wrote `error.message?.take(180)`, and a ktor ClientRequestException message is
 * `POST <url> ... Text: "<body>"` — the /player URL with its query and the start of the response body.
 * The summary carries the exception simple name and, for a ktor ResponseException, the HTTP status only.
 */
class ResolveFailureSummaryTest {

    private val leakyMessage =
        "Client request(POST https://music.youtube.com/youtubei/v1/player?prettyPrint=false&sig=SIGVALUE" +
            "&pot=POTVALUE) invalid: 403 Forbidden. Text: \"{\"error\":{\"code\":403,\"message\":\"BODYSECRET\"}}\""

    private class PlayerRequestException(message: String) : IllegalStateException(message)

    @Test
    fun `nothing from the message survives`() {
        listOf(IOException(leakyMessage), PlayerRequestException(leakyMessage)).forEach { error ->
            val summary = resolveFailureSummary(error)
            listOf("sig=", "SIGVALUE", "pot=", "POTVALUE", "youtubei", "https://", "{", "BODYSECRET", "Text:")
                .forEach { assertFalse("<$it> leaked in: $summary", summary.contains(it)) }
        }
    }

    @Test
    fun `the summary is the exception simple name`() {
        assertEquals("IOException", resolveFailureSummary(IOException(leakyMessage)))
        assertEquals("PlayerRequestException", resolveFailureSummary(PlayerRequestException(leakyMessage)))
    }

    @Test
    fun `an HTTP status is appended when there is one`() {
        assertEquals("ClientRequestException http=403", resolveFailureSummary("ClientRequestException", 403))
        assertEquals("IOException", resolveFailureSummary("IOException", null))
    }

    @Test
    fun `the persisted resolve lines use the summary, never the message`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val f = File(root, "app/src/main/kotlin/com/music/echo/utils/YTPlayerUtils.kt")
        assertTrue("YTPlayerUtils.kt moved: update this test", f.isFile)
        val source = f.readText()
        assertTrue(source.contains("\"client=\$clientName videoId=\$videoId \${resolveFailureSummary(error)}\""))
        assertTrue(source.contains("Proxy connection failed for host=\${uri?.host}: \${ioe?.let { resolveFailureSummary(it) }}"))
        assertFalse(source.contains("error.message?.take(180)"))
        assertFalse(source.contains("ioe?.message"))
    }
}
