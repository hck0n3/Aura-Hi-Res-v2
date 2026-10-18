package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeliberatePausePolicyTest {

    @Test
    fun `only a user request that the service did not cause is deliberate`() {
        assertTrue(DeliberatePausePolicy.isDeliberate(1, false, false, false))
        assertFalse("our focus-loss pause", DeliberatePausePolicy.isDeliberate(1, true, false, false))
        assertFalse("our error stop", DeliberatePausePolicy.isDeliberate(1, false, true, false))
        assertFalse("mute pause", DeliberatePausePolicy.isDeliberate(1, false, false, true))
        assertFalse("another reason (noisy, focus loss)", DeliberatePausePolicy.isDeliberate(4, false, false, false))
    }

    @Test
    fun `a connecting device never overrides a deliberate pause through the setting`() {
        assertTrue(DeliberatePausePolicy.mayResumeOnRouteConnect(true, false, deliberatePause = false))
        assertFalse(DeliberatePausePolicy.mayResumeOnRouteConnect(true, false, deliberatePause = true))
        assertFalse(DeliberatePausePolicy.mayResumeOnRouteConnect(false, false, deliberatePause = false))
        // Android Auto re-connect after a noisy pause is already bounded to 45 s and was never a deliberate pause.
        assertTrue(DeliberatePausePolicy.mayResumeOnRouteConnect(false, true, deliberatePause = false))
    }

    @Test
    fun `a foreign PLAY right after a route connects is rejected only after a deliberate pause`() {
        fun reject(deliberate: Boolean = true, playing: Boolean = false, own: Boolean = false, since: Long = 2_000L) =
            DeliberatePausePolicy.shouldRejectExternalPlay(deliberate, playing, own, since)
        assertTrue(reject())
        assertFalse("user never paused", reject(deliberate = false))
        assertFalse("already playing: that press is a pause", reject(playing = true))
        assertFalse("our own notification", reject(own = true))
        assertFalse("later press on the headset works", reject(since = DeliberatePausePolicy.EXTERNAL_PLAY_SHIELD_MS + 1))
        assertFalse("no route ever connected", reject(since = -1L))
    }

    @Test
    fun `the service and the session callback actually use the policy`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root")
        fun src(name: String) =
            File(root, "app/src/main/kotlin/com/music/echo/playback/$name").also { assertTrue("$name se movió", it.isFile) }.readText()
        val service = src("MusicService.kt")
        val callback = src("MediaLibrarySessionCallback.kt")
        assertTrue(service.contains("DeliberatePausePolicy.isDeliberate("))
        assertTrue(service.contains("DeliberatePausePolicy.mayResumeOnRouteConnect("))
        assertTrue(callback.contains("DeliberatePausePolicy.shouldRejectExternalPlay("))
        assertEquals("route time must be stamped when a playback route is added", 1,
            Regex("lastRouteConnectedAtMs = System.currentTimeMillis\\(\\)").findAll(service).count())
    }
}
