package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 772. Two gesture detectors started with pointerInput(Unit) kept running with stale state:
 *  - the in-window dialog scrim (and its panel blocker) kept eating taps during the 180 ms fade-out, so the first tap
 *    on the screen below after closing a dialog was lost;
 *  - the cover's double tap read the guest role of the composition where it started, so a user who became a Listen
 *    Together guest later could still pause or seek the room from the cover.
 * Neither shows in a compile or a JVM logic test, so the shape of each fix is pinned as source text.
 */
class ScrimAndGuestTapGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the fading dialog scrim and panel stop consuming taps once the dialog is closing`() {
        val dialog = main("ui/newui/AuraInWindowDialog.kt")
        val scrim = Regex(
            """if \(visible\) \{\s*Modifier\.pointerInput\(Unit\) \{\s*detectTapGestures \{\s*focusManager\.clearFocus\(\)\s*if \(currentDismissOnOutsideTap\) currentOnDismiss\(\)""",
        )
        assertEquals("el fondo solo escucha mientras el diálogo es visible", 1, scrim.findAll(dialog).count())
        assertTrue(
            "el bloqueador del panel tampoco come toques durante la salida",
            dialog.contains(".then(if (visible) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier)"),
        )
        assertFalse(
            "ningún detector sin condición queda en el host",
            Regex("""\n\s*\.pointerInput\(Unit\) \{""").containsMatchIn(dialog),
        )
        // Stale-closure fix of 2026-09-04 (AI playlist generation) still in place.
        assertTrue(dialog.contains("val currentOnDismiss by rememberUpdatedState(onDismiss)"))
        assertTrue(dialog.contains("val currentDismissOnOutsideTap by rememberUpdatedState(dismissOnOutsideTap)"))
    }

    @Test
    fun `the cover double tap reads the current guest role and seek callback`() {
        val thumb = main("ui/player/Thumbnail.kt")
        assertTrue(thumb.contains("val currentIsListenTogetherGuest by rememberUpdatedState(isListenTogetherGuest)"))
        assertTrue(thumb.contains("val currentOnSeek by rememberUpdatedState(onSeek)"))
        val doubleTap = thumb.substringAfter("onDoubleTap = { offset ->").substringBefore("contentAlignment = Alignment.Center")
        assertTrue("el invitado no pausa ni salta", doubleTap.contains("if (currentIsListenTogetherGuest) return@detectTapGestures"))
        assertFalse("sin lectura del valor capturado al empezar", doubleTap.contains("if (isListenTogetherGuest)"))
        assertEquals(2, Regex("""currentOnSeek\(context\.getString\(R\.string\.seek_""").findAll(doubleTap).count())
        assertFalse(Regex("""(?<!current)onSeek\(context""").containsMatchIn(doubleTap))
        // Registry row 239: progressive seek stays always on.
        assertTrue(thumb.contains("val incrementalSeekSkipEnabled = true"))
    }

    @Test
    fun `the full screen lyrics cover does not toggle the room for a guest`() {
        for (file in listOf("ui/newui/AuraPlayer.kt", "ui/newui/AuraLyricsScreen.kt")) {
            val src = main(file)
            assertTrue(
                "$file: la portada de la letra a pantalla completa respeta al invitado",
                Regex("""\.pointerInput\(isListenTogetherGuest\) \{\s*detectTapGestures \{ if \(!isListenTogetherGuest\) playerConnection\.togglePlayPause\(\) \}""")
                    .containsMatchIn(src),
            )
            assertFalse(
                "$file: ningún toque suelto alterna la reproducción sin mirar el rol",
                Regex("""\.pointerInput\(Unit\) \{\s*detectTapGestures \{ playerConnection\.togglePlayPause\(\) \}""").containsMatchIn(src),
            )
        }
    }

    @Test
    fun `the full screen lyrics transport row mutes for a guest instead of pausing or skipping`() {
        val src = main("ui/newui/AuraLyricsScreen.kt")
        val call = src.substringAfter("if (showTransport) {").substringBefore("Spacer(")
        assertTrue("la fila de transporte existe", call.contains("AuraLyricsTransport("))
        assertTrue(call.contains("isPlaying = if (isListenTogetherGuest) !isMuted else isPlaying,"))
        assertTrue(call.contains("canSkipPrevious = canSkipPrevious && !isListenTogetherGuest,"))
        assertTrue(call.contains("canSkipNext = canSkipNext && !isListenTogetherGuest,"))
        assertTrue(
            "el botón grande del invitado silencia, como en el reproductor",
            call.contains("if (isListenTogetherGuest) playerConnection.toggleMute() else playerConnection.togglePlayPause()"),
        )
        assertFalse(
            "ningún play/pausa sin mirar el rol",
            call.contains("onTogglePlayPause = { playerConnection.togglePlayPause() }"),
        )
        assertTrue(src.contains("val isMuted by playerConnection.isMuted.collectAsState()"))
        assertTrue(src.contains("contentDescription = playPauseDescription,"))
    }
}
