package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20 (beta4): «le doy play y va bien, pero para pausar toco y toco y no hace nada hasta varias veces».
 *
 * The play/pause path itself is idempotent (it toggles the player's live `playWhenReady`). What was lost was the
 * TAP: the transport buttons sat under `shieldTapsFromAncestorDrags`, which consumes every event after the button's
 * `clickable` saw it, and `clickable` cancels when a later pass finds its event consumed — so any MOVE between down
 * and up (a real thumb always makes one) dropped the tap. These pins keep the transport buttons on the
 * button-owned gesture, with a single firing path (a double fire of a toggle cancels itself out).
 */
class PlayPauseFirstTapGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root")
    }

    private fun src(path: String): String {
        val f = File(repoRoot, path)
        assertTrue("$path se movió — actualiza este test", f.isFile)
        return f.readText()
    }

    private val ui = "app/src/main/kotlin/com/music/echo/ui/newui"

    @Test
    fun `first tap mode fires through the button's own gesture and never mounts a second clickable`() {
        val prim = src("$ui/AuraPrimitives.kt")
        val helper = prim.substring(prim.indexOf("private fun Modifier.auraFirstTap("))
        val body = helper.substring(0, helper.indexOf("\n\n").takeIf { it > 0 } ?: helper.length)
        assertTrue(body.contains("Modifier.unstealableClick(action)") || helper.take(2500).contains(".unstealableClick(action)"))
        assertFalse("un segundo camino de disparo anularía el conmutador", body.contains(".clickable("))
        // TV / car / keyboard activation that the clickable used to provide
        assertTrue(helper.take(2500).contains("Key.DirectionCenter"))
        assertTrue(helper.take(2500).contains(".focusable()"))
    }

    @Test
    fun `the full player transport row uses first tap on every button`() {
        val player = src("$ui/AuraPlayer.kt")
        val row = player.indexOf("TIDAL-STYLE TRANSPORT")
        assertTrue(row > 0)
        val block = player.substring(row, row + 9000)
        // shuffle, previous, play, next, repeat
        assertEquals(5, Regex("firstTap = true,").findAll(block).count())
    }

    @Test
    fun `the mini pill play pause and skip buttons use first tap`() {
        val shell = src("$ui/AuraShell.kt")
        val pill = shell.indexOf("widePillMode?.miniCompact != true")
        assertTrue(pill > 0)
        val block = shell.substring(pill, pill + 6000)
        assertTrue("prev, play, next", Regex("firstTap = true,").findAll(block).count() >= 3)
    }

    @Test
    fun `a tap and the player's answer to it leave a trace in the shared log`() {
        val conn = src("app/src/main/kotlin/com/music/echo/playback/PlayerConnection.kt")
        assertTrue(conn.contains("\"PLAY_TOGGLE playWhenReady=%b state=%d casting=%b\""))
        val svc = src("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        assertTrue(svc.contains("\"PLAY_WHEN_READY -> %b reason=%d ownFocusPause=%b ownStopPause=%b\""))
    }
}
