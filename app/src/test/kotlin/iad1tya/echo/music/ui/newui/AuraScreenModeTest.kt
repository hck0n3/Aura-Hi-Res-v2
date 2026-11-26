package iad1tya.echo.music.ui.newui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 896 (plan, phase 6.2): «Pantalla» of the TV player — portada → letra → vídeo. */
class AuraScreenModeTest {

    @Test
    fun `the view on screen comes from the player's own two states, video first`() {
        assertEquals(AuraScreenMode.COVER, auraScreenModeOf(videoMode = false, lyricsOpen = false))
        assertEquals(AuraScreenMode.LYRICS, auraScreenModeOf(videoMode = false, lyricsOpen = true))
        assertEquals(AuraScreenMode.VIDEO, auraScreenModeOf(videoMode = true, lyricsOpen = false))
        assertEquals(AuraScreenMode.VIDEO, auraScreenModeOf(videoMode = true, lyricsOpen = true))
    }

    @Test
    fun `the cycle is cover, lyrics, video, cover`() {
        assertEquals(AuraScreenMode.LYRICS, nextAuraScreenMode(AuraScreenMode.COVER, videoOffered = true))
        assertEquals(AuraScreenMode.VIDEO, nextAuraScreenMode(AuraScreenMode.LYRICS, videoOffered = true))
        assertEquals(AuraScreenMode.COVER, nextAuraScreenMode(AuraScreenMode.VIDEO, videoOffered = true))
    }

    @Test
    fun `Pantalla never offers video for a song without video`() {
        for (mode in AuraScreenMode.entries) {
            assertNotEquals("from $mode", AuraScreenMode.VIDEO, nextAuraScreenMode(mode, videoOffered = false))
        }
        assertEquals(AuraScreenMode.COVER, nextAuraScreenMode(AuraScreenMode.LYRICS, videoOffered = false))
        // A song has a video only on the «Vídeo» chip's own terms.
        assertFalse(auraScreenOffersVideo(hasCompatibleVideo = false, podcastVideoUrl = null))
        assertFalse(auraScreenOffersVideo(hasCompatibleVideo = false, podcastVideoUrl = ""))
        assertTrue(auraScreenOffersVideo(hasCompatibleVideo = true, podcastVideoUrl = null))
        assertTrue(auraScreenOffersVideo(hasCompatibleVideo = false, podcastVideoUrl = "https://example.invalid/v.mp4"))
    }

    @Test
    fun `a video whose song audio is refused offers no way out, like the Cancion chip`() {
        assertFalse(auraScreenCycleOffered(AuraScreenMode.VIDEO, canLeaveVideo = false))
        assertTrue(auraScreenCycleOffered(AuraScreenMode.VIDEO, canLeaveVideo = true))
        assertTrue(auraScreenCycleOffered(AuraScreenMode.COVER, canLeaveVideo = false))
        assertTrue(auraScreenCycleOffered(AuraScreenMode.LYRICS, canLeaveVideo = false))
    }

    @Test
    fun `the button reuses the player's states and only shows in the TV shell`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val player = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt").readText()
        assertTrue(player.contains("val tvShell = iad1tya.echo.music.ui.utils.rememberIsTvShell()"))
        assertTrue(player.contains("val screenMode = auraScreenModeOf(videoMode, showInlineLyrics)"))
        assertTrue(player.contains("val screenVideoOffered = auraScreenOffersVideo(meta.hasCompatibleVideo, meta.podcastVideoUrl)"))
        assertTrue(player.contains("val showScreenCycle = tvShell && auraScreenCycleOffered("))
        val click = player.substringAfter("ActionLog.tap(ActionId.PLR_SCREEN_CYCLE)").substringBefore(".padding(horizontal = 10.dp")
        assertTrue(click.contains("val next = nextAuraScreenMode(screenMode, screenVideoOffered)"))
        assertTrue("the same lyrics state", click.contains("showInlineLyrics = next == AuraScreenMode.LYRICS"))
        assertTrue("the same video toggle", click.contains("if ((next == AuraScreenMode.VIDEO) != videoMode) playerConnection.toggleVideoMode()"))
        // D-pad: the ring sits above the clickable it observes; initial focus on play/pause stays as it was.
        val button = player.substringAfter("if (showScreenCycle) {").substringBefore("ActionLog.tap(ActionId.PLR_SCREEN_CYCLE)")
        assertTrue(button.indexOf(".tvFocusable(isTvOrCar, RoundedCornerShape(8.dp))") in 0 until button.indexOf(".clickable {"))
        assertTrue(player.contains(".focusRequester(playFocusRequester)"))
        // The TV player is never the split pane: the capsule (and with it the split) is off in the TV shell.
        val activity = File(root, "app/src/main/kotlin/com/music/echo/MainActivity.kt").readText()
        assertTrue(activity.contains("val auraWideBottom = newUiShell && isWideLayout && !tvShell"))
        assertTrue(activity.contains("val splitActive = auraWideBottom && widePlayerSplit"))
    }
}
