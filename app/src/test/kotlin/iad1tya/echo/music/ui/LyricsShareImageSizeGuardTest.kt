package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 878 (plan, phase 1.3 «desajuste de densidad», row 870 (e)): the share-as-image of the lyrics passed
 * `LocalWindowInfo.containerSize` (already PIXELS) multiplied by the density again, ~2.6x the window on the Fold.
 * The window's pixels go through unchanged.
 */
class LyricsShareImageSizeGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the shared lyrics image takes the window pixels once`() {
        val call = main("ui/component/Lyrics.kt")
            .substringAfter("val screenWidth = configuration.containerSize.width")
            .substringBefore("backgroundColor = previewBackgroundColor.toArgb(),")
        assertTrue("el tamaño sale de containerSize (píxeles)", call.contains("val screenHeight = configuration.containerSize.height"))
        assertTrue(call.contains("ComposeToImage.createLyricsImage("))
        assertTrue(call.contains("width = screenWidth,"))
        assertTrue(call.contains("height = screenHeight,"))
        assertFalse("los píxeles no se vuelven a multiplicar por la densidad", call.contains("density"))
    }
}
