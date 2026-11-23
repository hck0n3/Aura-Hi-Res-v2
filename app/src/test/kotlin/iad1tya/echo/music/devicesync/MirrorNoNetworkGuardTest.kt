package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review C4 of the device-control fixes (AGENTS rule 7): the unprepared mirror still hit the network — playQueue preloaded its upcoming
 * songs and studied its artists, and every mirror seek ran the song-change side effects (preload, SponsorBlock, video prefetch, style
 * learning, no-repeat memory, radio pre-seed). None of it runs while the player only shows another device.
 */
class MirrorNoNetworkGuardTest {
    private val service: String = run {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
    }

    @Test
    fun `only showing a mirror means unprepared, IDLE, remote on and no take-over in flight`() {
        val def = service.substringAfter("private val onlyShowingMirror: Boolean").substringBefore("/**")
        for (part in listOf("mirrorUnprepared", "player.playbackState == Player.STATE_IDLE", "RemotePlayback.active", "!iad1tya.echo.music.devicesync.RemotePlayback.localStartGranted()")) {
            assertTrue(part, def.contains(part))
        }
    }

    @Test
    fun `a mirror song change runs none of the side effects`() {
        val transition = service.substringAfter("override fun onMediaItemTransition(").substringBefore("applyAutoAdvanceSideEffects()")
        val gate = transition.indexOf("if (onlyShowingMirror) {")
        assertTrue(gate >= 0)
        assertTrue("before the style learning", gate < transition.indexOf("exactStyleOnTransition(mediaItem, reason)"))
        assertTrue(transition.substring(gate).substringBefore("// Fila 356").contains("return"))
    }

    @Test
    fun `the mirror is flagged before its items land and preloads or studies nothing`() {
        val load = service.substringAfter("if (isRestore && !prepareRestore) player.stop()")
        assertTrue(load.indexOf("mirrorUnprepared = isRestore && !prepareRestore") in 0 until load.indexOf("player.setMediaItems("))
        assertTrue(service.contains("if (keepGenreLaneHint && radioSeedPool.size > 1 && !(isRestore && !prepareRestore)) {"))
        val preload = service.substringAfter("private fun preloadUpcomingItems() {").substringBefore("val currentIndex")
        assertTrue(preload.contains("if (onlyShowingMirror) return"))
    }
}
