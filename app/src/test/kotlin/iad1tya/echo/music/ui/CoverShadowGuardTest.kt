package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 521 / 532 — the player's cover is lifted off the ground by the owner's chosen look, option F (2026-10-30): a light
 * rim, a short soft shadow and a glow in the cover's own colour. Smooth platform shadows; no stacked layers, no blur.
 */
class CoverShadowGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private val source: String
        get() = File(root, "app/src/main/kotlin/com/music/echo/ui/player/Thumbnail.kt").readText()

    private fun dp(s: String, name: String) = Regex("val $name = (\\d+(?:\\.\\d+)?)\\.dp").find(s)!!.groupValues[1].toFloat()

    @Test
    fun `both cover styles of the new player wear the same lift, with no blur or render effect`() {
        val s = source
        assertTrue(s.contains("private fun Modifier.coverLift("))
        assertEquals("tarjeta y estilo Apple Music comparten un único punto de uso", 1, Regex("Modifier\\.coverLift\\(coverShape, coverGlow\\)").findAll(s).count())
        assertTrue("la sombra apilada de rectángulos (a saltos, fea) no vuelve", !s.contains("coverDropShadow"))
        val fn = s.substring(s.indexOf("private fun Modifier.coverLift("), s.indexOf("private fun Modifier.coverLift(") + 900)
        assertTrue("batería/calor: sombras de plataforma, nada de desenfoque", !fn.contains(".blur(") && !fn.contains("RenderEffect"))
    }

    @Test
    fun `the glow takes the colour the ambient wash already extracted, not a new decode`() {
        val s = source
        assertTrue(s.contains("AuraBloomCache.accentSeed(item.mediaId)"))
        assertTrue("sin color todavía: solo filo y sombra corta", s.contains("if (glow != null)"))
    }

    @Test
    fun `the shadow stays short and the rim stays a hairline (owner 2026-10-30)`() {
        val s = source
        assertTrue("sombra corta ≤ 8 dp", dp(s, "COVER_LIFT_ELEVATION") <= 8f)
        assertTrue("resplandor ≤ 28 dp", dp(s, "COVER_GLOW_ELEVATION") <= 28f)
        assertTrue("filo de 1 dp", s.contains(".border(1.dp, COVER_RIM_BRUSH, shape)"))
    }
}
