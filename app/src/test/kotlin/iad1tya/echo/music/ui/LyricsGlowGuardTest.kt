package iad1tya.echo.music.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 769: with «brillo» on, the current lyric line ran `while (true) { pulseProgress.animateTo(...) }`, which
 * recomposed it on every frame while lyrics were visible — paused or not. The pulse now runs only while the line is
 * current, music plays and the system allows motion (row 771).
 */
class LyricsGlowGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val lyrics: String by lazy {
        File(root, "app/src/main/kotlin/com/music/echo/ui/component/Lyrics.kt").readText()
    }

    private val glowBranch: String by lazy {
        lyrics.substringAfter("} else if (isActiveLine && lyricsGlowEffect) {")
            .substringBefore("} else if (isActiveLine && !lyricsGlowEffect) {")
    }

    @Test
    fun `the glow pulse is not an unconditional endless loop`() {
        assertTrue(glowBranch.contains("pulseProgress.animateTo("))
        assertFalse(glowBranch.contains("while (true)"))
        assertFalse(glowBranch.contains("LaunchedEffect(Unit) {\n                                while"))
    }

    @Test
    fun `the glow pulse stops when paused or when the system reduces motion`() {
        assertTrue(lyrics.contains("val glowReducedMotion = iad1tya.echo.music.utils.rememberReducedMotion()"))
        assertTrue(glowBranch.contains("val glowPlaying by playerConnection.isPlaying.collectAsState()"))
        assertTrue(glowBranch.contains("iad1tya.echo.music.utils.ReducedMotion.musicLoopRuns("))
        assertTrue(glowBranch.contains("LaunchedEffect(pulseRuns) {"))
        assertTrue(glowBranch.contains("if (!pulseRuns) {"))
    }

    /** Row 769: the colour picker built a fresh ImageLoader (own caches and dispatchers) on every open. */
    @Test
    fun `the colour picker uses the app's shared image loader`() {
        assertFalse(lyrics.contains("ImageLoader(context)"))
        assertTrue(lyrics.contains("val loader = SingletonImageLoader.get(context)"))
    }
}
