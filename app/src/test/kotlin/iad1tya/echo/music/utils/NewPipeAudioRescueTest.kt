package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 514 — owner 2026-10-29: «en modo audio no me reproduce nada y entra en un bucle… lo cambio a vídeo y sí
 * reproduce». The audio path only asked InnerTube /player (ANDROID_VR, IOS) and gave up when both refused; video mode takes
 * its URL from NewPipe's extractor and played the same ids. These pins keep the extractor as the last rung of the audio path.
 */
class NewPipeAudioRescueTest {
    private val streams = listOf(
        18 to "u18", 22 to "u22", 136 to "u136",
        140 to "u140", 139 to "u139", 249 to "u249", 251 to "u251", 250 to "u250",
    )

    @Test
    fun `picks the Opus family first and never a muxed or video-only itag`() {
        assertEquals(251, pickNewPipeAudio(streams, smallest = false)?.first)
        assertEquals(140, pickNewPipeAudio(streams.filter { it.first in setOf(18, 22, 140, 139) }, false)?.first)
        assertNull(pickNewPipeAudio(listOf(18 to "u", 22 to "u", 137 to "u"), false))
        assertNull(pickNewPipeAudio(emptyList(), false))
    }

    @Test
    fun `the smallest request takes the lightest rendition`() {
        assertEquals(249, pickNewPipeAudio(streams, smallest = true)?.first)
    }

    @Test
    fun `a blank url is not a stream`() {
        assertEquals(140, pickNewPipeAudio(listOf(251 to "", 140 to "u140"), false)?.first)
    }

    @Test
    fun `mime types match the itag family`() {
        assertTrue(mimeTypeForAudioItag(251).startsWith("audio/webm"))
        assertTrue(mimeTypeForAudioItag(140).startsWith("audio/mp4"))
        assertTrue(mimeTypeForAudioItag(139).contains("mp4a.40.5"))
    }

    @Test
    fun `the resolver hands a failed song to the extractor once before giving up`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val src = File(root, "app/src/main/kotlin/com/music/echo/utils/YTPlayerUtils.kt").readText()
        assertTrue(src.contains("if (inner.isFailure) newPipeAudioRescue(videoId, inner, preferSmallestAudio, timing) ?: inner else inner"))
        assertTrue(src.contains("as? StreamResolutionException ?: return null"))
        assertTrue(src.contains("NEWPIPE_RESCUE_TIMEOUT_MS"))
        // Privacy (AGENTS.md rule 4): the rescue logs the id and the itag, never the URL.
        val body = src.substring(src.indexOf("private suspend fun newPipeAudioRescue("), src.indexOf("private const val NEWPIPE_RESCUE_TIMEOUT_MS"))
        assertTrue(!body.contains("\$url") && !body.contains("%s\", url"))
    }
}
