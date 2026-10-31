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
    fun `the shadow stays short and the rim stays a thin line (owner 2026-10-30, row 560)`() {
        val s = source
        assertTrue("sombra corta ≤ 8 dp", dp(s, "COVER_LIFT_ELEVATION") <= 8f)
        assertTrue("resplandor ≤ 28 dp", dp(s, "COVER_GLOW_ELEVATION") <= 28f)
        // Row 560 (owner 2026-10-31): the rim was too faint to notice; it is now 1.5 dp and brighter, with a sheen over the artwork. The
        // shadow constants above are untouched on purpose («el sombreado está bien»).
        assertTrue("filo fino (≤ 2 dp)", dp(s, "COVER_RIM_WIDTH") <= 2f)
        assertTrue("el filo usa su ancho", s.contains(".border(COVER_RIM_WIDTH, COVER_RIM_BRUSH, shape)"))
        assertTrue("brillo sobre la portada, dibujado una vez con la forma de la portada", s.contains("drawOutline(outline, sheen)"))
    }

    @Test
    fun `the cover leaves room for its shadow so the slot never clips it into a square edge (row 538)`() {
        val s = source
        val room = dp(s, "COVER_SHADOW_ROOM")
        val glow = dp(s, "COVER_GLOW_ELEVATION")
        assertTrue("el resplandor cabe en el margen reservado (con holgura)", glow <= room)
        assertTrue(s.contains("(maxHeight - COVER_SHADOW_ROOM * 2).coerceAtLeast(0.dp)"))
        assertTrue("solo el reproductor nuevo reserva; el clásico queda igual", s.contains("val slotCap = if (host == ThumbnailHost.OPAQUE_DARK) {"))
    }
}
