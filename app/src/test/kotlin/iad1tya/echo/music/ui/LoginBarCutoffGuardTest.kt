package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 870 (plan, phase 1.3): the View-based sign-in screens (YouTube, Spotify, Amazon Music). Their bar buttons
 * wrap to a second line and end in an ellipsis instead of being clipped, and the Spotify bar, which stacks two rows, is
 * as tall as its rows: a fixed 56 dp hid the second row («pegar cookie», «abrir en el navegador») entirely.
 */
class LoginBarCutoffGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val logins = listOf(
        "ui/screens/YouTubeLoginActivity.kt",
        "spotify/SpotifyLoginActivity.kt",
        "migration/AmazonMusicLoginActivity.kt",
    )

    @Test
    fun `bar buttons wrap to two lines and end in an ellipsis`() {
        for (path in logins) {
            val button = main(path).substringAfter("private fun barButton(").substringBefore("\n    }\n")
            assertTrue("$path: dos líneas", button.contains("maxLines = 2"))
            assertTrue("$path: termina en «…»", button.contains("ellipsize = ") && button.contains("TruncateAt.END"))
        }
    }

    @Test
    fun `the Spotify two-row top bar wraps its height`() {
        val src = main("spotify/SpotifyLoginActivity.kt")
        assertTrue(src.contains("addView(topBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))"))
        assertFalse("sin alto fijo", src.contains("addView(topBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp("))
        val bar = src.substringAfter("private fun topBar(): View").substringBefore("private fun barButton(")
        assertTrue("sigue teniendo las dos filas", bar.contains("R.string.login_manual_cookie") && bar.contains("R.string.spotify_login_open_in_browser"))
    }
}
