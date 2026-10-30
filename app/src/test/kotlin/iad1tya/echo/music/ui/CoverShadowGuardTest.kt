package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 521 — the player's cover is lifted off the ground by a soft layered shadow (owner 2026-10-29). */
class CoverShadowGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    @Test
    fun `the new player's cover wears the layered drop shadow, with no blur or render effect`() {
        val s = File(root, "app/src/main/kotlin/com/music/echo/ui/player/Thumbnail.kt").readText()
        assertTrue(s.contains("private fun Modifier.coverDropShadow("))
        assertTrue("con el estilo de tarjeta y con el de Apple Music", Regex("coverDropShadow\\(dimensions\\.cornerRadius\\)").findAll(s).count() == 2)
        val fn = s.substring(s.indexOf("private fun Modifier.coverDropShadow("), s.indexOf("private fun Modifier.coverDropShadow(") + 900)
        assertTrue("batería/calor: solo trazos, nada de desenfoque", !fn.contains(".blur(") && !fn.contains("RenderEffect"))
    }
}
