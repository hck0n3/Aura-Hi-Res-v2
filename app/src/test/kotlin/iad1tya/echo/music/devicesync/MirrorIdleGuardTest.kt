package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player that only SHOWS another device's playback (the device-sync mirror) loads that queue without preparing it: nothing is
 * downloaded on every song change of the other device (AGENTS rule 7), and with no READY state Bluetooth resume or an error skip
 * cannot start it here by itself (owner: «la música que sonaba en el dispositivo 2 se pasó sola al 1 y Aura se abrió sola»).
 * The first real request to play it here prepares it, so taking playback over still sounds.
 */
class MirrorIdleGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    private val service = read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
    private val engine = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")

    @Test
    fun `the mirror asks for a queue that is not prepared`() {
        assertTrue(engine.contains("playWhenReady = false,\n                isRestore = true,\n                prepareRestore = false,"))
    }

    @Test
    fun `an unprepared restore stops the player before loading and never prepares it`() {
        val load = service.substringAfter("if (isRestore && !prepareRestore) player.stop()")
        val branch = load.substringAfter("if (isRestore && !prepareRestore) {").substringBefore("} else if (isRestore) {")
        assertTrue("the stop must come before setMediaItems", load.indexOf("player.setMediaItems(") in 0 until load.indexOf("if (isRestore && !prepareRestore) {"))
        assertTrue(branch.contains("player.playWhenReady = false"))
        assertTrue(branch.contains("mirrorUnprepared = true"))
        assertFalse(branch.contains("prepare()"))
    }

    @Test
    fun `a call queued before the player exists keeps the choice`() {
        assertTrue(service.contains("playQueue(queue, playWhenReady, isRestore, prepareRestore)"))
    }

    @Test
    fun `the first request to play here prepares the mirror, and leaving IDLE ends it`() {
        val pwr = service.substringAfter("override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {")
            .substringBefore("scheduleSoftVideoRestoreOnPause(playWhenReady)")
        assertTrue(pwr.contains("if (playWhenReady && mirrorUnprepared && player.playbackState == Player.STATE_IDLE) player.prepare()"))
        assertTrue(service.contains("if (playbackState != Player.STATE_IDLE) mirrorUnprepared = false"))
    }

    @Test
    fun `seeking the mirror within the queue it already holds also leaves it IDLE`() {
        val mirror = engine.substringAfter("private suspend fun mirrorRemote(").substringBefore("service.playQueue(")
        assertTrue(mirror.contains("if (p.playbackState != Player.STATE_IDLE) p.stop()"))
        assertTrue(mirror.contains("service.mirrorUnprepared = true"))
        assertTrue("never stops a player that was asked to play", mirror.contains("if (p.playWhenReady) return"))
    }
}
