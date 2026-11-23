package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry 763: skipping a failed song keeps a paused queue paused; it never starts music by itself. */
class ErrorSkipPolicyTest {

    @Test
    fun `the skip resumes only what was already meant to play`() {
        assertTrue(ErrorSkipPolicy.shouldResumeAfterErrorSkip(wasPlayWhenReady = true, casting = false))
        assertFalse("a paused or restored queue stays paused", ErrorSkipPolicy.shouldResumeAfterErrorSkip(wasPlayWhenReady = false, casting = false))
        assertFalse("casting: the receiver owns playback", ErrorSkipPolicy.shouldResumeAfterErrorSkip(wasPlayWhenReady = true, casting = true))
        assertFalse(ErrorSkipPolicy.shouldResumeAfterErrorSkip(wasPlayWhenReady = false, casting = true))
    }

    private val service: String by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
    }

    @Test
    fun `skipOnError no longer calls play unconditionally`() {
        val body = service.substringAfter("internal fun skipOnError(wasPlayWhenReady: Boolean = player.playWhenReady) {")
            .substringBefore("\n    }\n")
        assertTrue("skipOnError moved or lost its intent parameter", body.length < service.length)
        assertEquals("exactly one play() in skipOnError", 1, Regex("player\\.play\\(\\)").findAll(body).count())
        val gate = body.indexOf("if (ErrorSkipPolicy.shouldResumeAfterErrorSkip(wasPlayWhenReady, castConnectionHandler?.isCasting?.value == true)) {")
        assertTrue("play() must sit behind the policy", gate >= 0 && gate < body.indexOf("player.play()"))
        assertTrue(body.contains("Timber.tag(TAG).i(\"START_CAUSE=error_skip\")"))
    }

    @Test
    fun `onPlayerError reads the intent before handling and passes it on`() {
        val onError = service.substringAfter("    override fun onPlayerError(error: PlaybackException) {\n        super.onPlayerError(error)")
            .substringBefore("\n    }\n")
        val read = onError.indexOf("val wasPlayWhenReady = player.playWhenReady")
        assertTrue("intent read missing", read >= 0)
        assertTrue("intent must be read before any branch handles the error", read < onError.indexOf("if (softVideoId != null)"))
        assertTrue(onError.contains("skipOnError(wasPlayWhenReady)"))
    }
}
