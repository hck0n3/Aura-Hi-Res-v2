package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22: «la música que sonaba en el dispositivo 2 se pasó sola al 1, y Aura se abrió sola». While another device is shown and
 * controlled from here, this player only holds its mirror: an automatic start (Bluetooth resume, focus or network coming back, an error
 * skip) must not make it sound — which then paused the other device — and an external PLAY goes to the device that really plays.
 */
class RemoteLocalStartGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    @Test
    fun `with a remote shown a local start needs a fresh grant`() {
        assertTrue("no grant ever", RemotePlayback.blocksLocalStart(remoteActive = true, msSinceGrant = 1_700_000_000_000L))
        assertFalse("an order or take-over a moment ago", RemotePlayback.blocksLocalStart(true, msSinceGrant = 800L))
        assertFalse(RemotePlayback.blocksLocalStart(true, msSinceGrant = RemotePlayback.COMMAND_GRACE_MS))
        assertTrue(RemotePlayback.blocksLocalStart(true, msSinceGrant = RemotePlayback.COMMAND_GRACE_MS + 1))
        assertTrue("a clock that went backwards is no grant", RemotePlayback.blocksLocalStart(true, msSinceGrant = -1L))
    }

    @Test
    fun `without a remote nothing is blocked`() {
        assertFalse(RemotePlayback.blocksLocalStart(remoteActive = false, msSinceGrant = 1_700_000_000_000L))
    }

    @Test
    fun `external play plays here without a remote, goes to the remote with one, and the reconnect echo goes nowhere`() {
        val shield = 10_000L
        assertEquals(RemotePlayback.ExternalPlay.LOCAL, RemotePlayback.externalPlay(false, false, 1_000L, shield))
        assertEquals(RemotePlayback.ExternalPlay.REMOTE, RemotePlayback.externalPlay(true, false, 60_000L, shield))
        assertEquals("own notification, even right after a route", RemotePlayback.ExternalPlay.REMOTE, RemotePlayback.externalPlay(true, true, 1_000L, shield))
        assertEquals(RemotePlayback.ExternalPlay.IGNORE, RemotePlayback.externalPlay(true, false, 1_000L, shield))
        assertEquals(RemotePlayback.ExternalPlay.IGNORE, RemotePlayback.externalPlay(true, false, shield, shield))
    }

    @Test
    fun `the service keeps the mirror silent like the cast guard does`() {
        val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val pwr = service.substringAfter("override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {")
            .substringBefore("scheduleSoftVideoRestoreOnPause(playWhenReady)")
        val guard = pwr.indexOf("if (playWhenReady && !remoteRedirectBypass && !inListenRoom && iad1tya.echo.music.devicesync.RemotePlayback.blocksLocalStartNow()) {")
        assertTrue(guard > pwr.indexOf("castConnectionHandler?.isCasting?.value == true"))
        assertTrue("blocked before the mirror is prepared", guard < pwr.indexOf("player.prepare()"))
        assertTrue(pwr.substring(guard).substringBefore("return").contains("player.pause()"))
        assertTrue("the widget's play goes to the remote", service.contains("} else if (iad1tya.echo.music.devicesync.RemotePlayback.play()) {"))
    }

    @Test
    fun `the session callback sends external play to the remote`() {
        val cb = read("app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt")
        assertTrue(cb.contains("RemotePlayback.externalPlay("))
        assertTrue(cb.contains("shieldMs = DeliberatePausePolicy.EXTERNAL_PLAY_SHIELD_MS"))
        assertTrue(cb.contains("if (action == iad1tya.echo.music.devicesync.RemotePlayback.ExternalPlay.REMOTE) iad1tya.echo.music.devicesync.RemotePlayback.play()"))
        assertTrue("the 422 echo check still runs first", cb.indexOf("DeliberatePausePolicy.shouldRejectExternalPlay(") < cb.indexOf("RemotePlayback.externalPlay("))
    }

    @Test
    fun `every way this device is told to play here grants it`() {
        val e = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
        assertTrue("orders from the other device", e.substringAfter("private suspend fun runCommand(").substringBefore("when (cmd) {").contains("RemotePlayback.grantLocalStart()"))
        assertTrue("take-over / continuar aquí", e.substringAfter("private suspend fun play(state: NowState) {").substringBefore("service.playQueue(").contains("RemotePlayback.grantLocalStart()"))
        val failed = e.substringAfter("suspend fun sendQueueToRemote(").substringBefore("return sent")
        assertTrue("a queue that could not be sent plays here", failed.contains("RemotePlayback.update(null, serverNow())") && failed.contains("RemotePlayback.grantLocalStart()"))
        val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        assertTrue("a song picked on Android Auto", service.substringAfter("internal fun onControllerSelectedItem() {").substringBefore("\n    }").contains("RemotePlayback.grantLocalStart()"))
    }
}
