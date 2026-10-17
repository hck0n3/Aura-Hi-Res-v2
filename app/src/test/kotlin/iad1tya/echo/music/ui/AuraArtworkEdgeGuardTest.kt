package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 385 (owner 2026-10-15, Apple Music screenshot): «las orillas de las portadas… idénticas a las de
 * Apple Music».
 *
 * The hairline already existed as `.border(0.5.dp, ArtworkEdge, shape)` on the cover frames and never showed:
 * `Modifier.border` is drawn in the frame's own layer, BEFORE the image child, so every opaque cover covered it.
 * It compiled, it looked right in the code, and it did nothing — so the edge must be drawn after the content.
 */
class AuraArtworkEdgeGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun main(path: String): String {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path se movió — actualiza este test para seguirlo", f.isFile)
        return f.readText()
    }

    @Test
    fun `the edge is painted after the cover, not under it`() {
        val prim = main("ui/newui/AuraPrimitives.kt")
        val start = prim.indexOf("fun Modifier.auraArtworkEdge(")
        assertTrue(start > 0)
        val body = prim.substring(start, prim.indexOf("// ── Internals", start))
        val content = body.indexOf("drawContent()")
        val stroke = body.indexOf("drawOutline(")
        assertTrue("el trazo debe dibujarse DESPUÉS de drawContent()", content in 0 until stroke)
    }

    @Test
    fun `the cover frames use it and none keeps the invisible border`() {
        val content = main("ui/newui/AuraContent.kt")
        assertFalse(content.contains(".border(0.5.dp, AuraPalette.ArtworkEdge"))
        assertTrue(Regex("\\.auraArtworkEdge\\(shape\\)").findAll(content).count() >= 2) // AuraCover + AuraPosterCard
        assertTrue(main("ui/newui/AuraPrimitives.kt").contains(".auraArtworkEdge(shape)")) // AuraArtwork
        val items = main("ui/component/Items.kt")
        assertTrue(items.contains("Modifier.auraArtworkEdge(frameShape)"))
        // Circular artist avatars never get the rounded-rect hairline.
        assertTrue(items.contains("frameShape != CircleShape"))
    }
}
