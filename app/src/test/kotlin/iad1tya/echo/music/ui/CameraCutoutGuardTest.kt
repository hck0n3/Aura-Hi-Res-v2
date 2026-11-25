package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 877 (plan, phase 1.3 «recortes de cámara», row 870 (d)): with targetSdk 37 the window draws under the
 * camera cut-out in landscape. The screens' bodies padded `LocalPlayerAwareWindowInsets` (system bars only) and the
 * detail headers (`AuraDetailTopBar`) only the top system bar, so both drew under the camera.
 *
 * The fix is ONE padding at the NavHost's container: every screen, header and body alike, moves off the horizontal
 * cut-out together (so they cannot misalign), and `windowInsetsPadding` consumes it so no descendant adds it twice. The
 * player-aware insets stay system bars only: adding the cut-out there too would double it for every `asPaddingValues()`.
 */
class CameraCutoutGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val pad = ".windowInsetsPadding(cutoutInsets.only(WindowInsetsSides.Horizontal))"

    @Test
    fun `the NavHost container moves every screen off the camera cut-out, once`() {
        val src = main("MainActivity.kt")
        assertTrue(src.contains("val cutoutInsets = WindowInsets.displayCutout"))
        assertEquals("one padding for every screen", 1, Regex(Regex.escape(pad)).findAll(src).count())
        // The padded Box is the one that holds the NavHost (and nothing else draws screens).
        val container = src.substringAfter(pad).substringBefore("NavHost(")
        assertTrue("the padded box holds the NavHost", container.contains("SharedTransitionLayout {"))
        val before = src.substringBefore(pad).substringAfterLast("Box(")
        assertTrue("it is the screens' container", before.contains(".padding(end = splitContentEnd)"))
        assertTrue("the haze source still covers the camera strip", before.contains("Modifier.shellHazeSource(shellHazeState)"))
    }

    @Test
    fun `the player-aware insets stay system bars, so the cut-out is not added twice`() {
        val src = main("MainActivity.kt")
        val insets = src.substringAfter("val playerAwareWindowInsets = remember(").substringBefore("val topAppBarScrollBehavior")
        assertTrue(insets.contains("windowsInsets\n                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)"))
        assertFalse(insets.contains("cutout"))
    }

    @Test
    fun `the shell top bar keeps its own cut-out padding, so it stays aligned with the screens`() {
        val src = main("MainActivity.kt")
        assertTrue(src.contains(".add(cutoutInsets.only(WindowInsetsSides.Start))"))
        assertTrue(src.contains("cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)"))
    }
}
