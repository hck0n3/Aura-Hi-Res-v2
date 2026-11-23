package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review C1 of the device-control fixes: a queue that ENDED keeps play-when-ready = true. Remote mode switches on (an ENDED player is
 * not «playing»), the mirror seeks inside that queue or a late radio append moves it — and it sounded here with no play-when-ready
 * transition, so neither the guard nor «pause the other» saw it: both devices played («Aura se puso a sonar sola»).
 */
class RemoteEndedQueueGuardTest {
    private val ended = 4
    private val idle = 1
    private val buffering = 2
    private val ready = 3

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    @Test
    fun `an ENDED or IDLE player with play-when-ready set is not sounding, a buffering or ready one is`() {
        assertFalse(RemotePlayback.sounding(playWhenReady = true, playbackState = ended))
        assertFalse(RemotePlayback.sounding(playWhenReady = true, playbackState = idle))
        assertTrue(RemotePlayback.sounding(playWhenReady = true, playbackState = buffering))
        assertTrue(RemotePlayback.sounding(playWhenReady = true, playbackState = ready))
        assertFalse(RemotePlayback.sounding(playWhenReady = false, playbackState = ready))
    }

    @Test
    fun `the ENDED queue moved into BUFFERING while a remote is shown is blocked, unless this device was told to play`() {
        val noGrant = 1_700_000_000_000L
        // ENDED + play-when-ready: not sounding yet, nothing to block…
        assertFalse(RemotePlayback.blocksSounding(remoteActive = true, msSinceGrant = noGrant, playWhenReady = true, playbackState = ended))
        // …the mirror's seek (or a radio append) takes it to BUFFERING with no play-when-ready change: blocked.
        assertTrue(RemotePlayback.blocksSounding(true, noGrant, true, buffering))
        assertTrue(RemotePlayback.blocksSounding(true, noGrant, true, ready))
        assertFalse("take-over in flight", RemotePlayback.blocksSounding(true, 500L, true, buffering))
        assertFalse("no remote shown", RemotePlayback.blocksSounding(false, noGrant, true, buffering))
    }

    @Test
    fun `a fresh grant is seen as granted, an old one is not`() {
        RemotePlayback.grantLocalStart(nowMs = 10_000L)
        assertTrue(RemotePlayback.localStartGranted(nowMs = 12_000L))
        assertFalse(RemotePlayback.localStartGranted(nowMs = 10_000L + RemotePlayback.COMMAND_GRACE_MS + 1))
    }

    @Test
    fun `showing a remote stands the local player down before the mirror is loaded`() {
        val engine = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
        val eval = engine.substringAfter("RemotePlayback.update(remote, serverNow())").substringBefore("private suspend fun mirrorRemote(")
        val standDown = eval.indexOf("if (playerOrNull() != null && !RemotePlayback.localStartGranted()) service.standLocalDownForRemote()")
        assertTrue(standDown >= 0)
        assertTrue(standDown < eval.indexOf("mirrorRemote(remote)"))

        val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val body = service.substringAfter("internal fun standLocalDownForRemote() {").substringBefore("internal var fadingPlayer")
        assertTrue(body.contains("resumeAfterSeed = false"))
        assertTrue(body.contains("advanceIntoRadioRequested = false"))
        assertTrue(body.contains("radioResumeWatchdogJob?.cancel()"))
        assertTrue(body.contains("if (player.playWhenReady) {") && body.contains("player.pause()"))
    }

    @Test
    fun `the service also blocks a start it only sees as a state change`() {
        val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val state = service.substringAfter("override fun onPlaybackStateChanged(").substringBefore("updateOfflineStallWatchdog()")
        assertTrue(state.contains("RemotePlayback.blocksSoundingNow(player.playWhenReady, playbackState)"))
        assertTrue(state.contains("player.pause()"))
    }

    @Test
    fun `an external PLAY on an ENDED queue goes to the remote and is logged as a play`() {
        val cb = read("app/src/main/kotlin/com/music/echo/playback/MediaLibrarySessionCallback.kt")
        assertTrue(cb.contains("!iad1tya.echo.music.devicesync.RemotePlayback.sounding(service.player.playWhenReady, service.player.playbackState)"))
        assertFalse("the old play-when-ready test is gone", cb.contains("            !service.player.playWhenReady\n        ) {"))
        assertTrue(cb.contains("if (iad1tya.echo.music.devicesync.RemotePlayback.sounding(service.player.playWhenReady, service.player.playbackState)) \"pause\" else \"play\""))
    }
}
