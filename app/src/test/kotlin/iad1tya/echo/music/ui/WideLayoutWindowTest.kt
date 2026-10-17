package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.utils.isWideLayoutWindow
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 394 (owner 2026-10-15): «cuando giro la app en modo no plegable se divide la pantalla como si fuera
 * plegable». The wide layouts were decided by WIDTH alone, and a phone on its side is ~800 dp wide.
 */
class WideLayoutWindowTest {
    @Test
    fun `a phone turned on its side is still a phone`() {
        assertFalse(isWideLayoutWindow(widthDp = 800f, heightDp = 360f)) // 6.5" phone, landscape
        assertFalse(isWideLayoutWindow(widthDp = 915f, heightDp = 412f)) // big phone, landscape
        assertFalse(isWideLayoutWindow(widthDp = 411f, heightDp = 915f)) // phone, portrait
    }

    @Test
    fun `an unfolded foldable and a tablet keep the wide layout in both orientations`() {
        assertTrue(isWideLayoutWindow(widthDp = 750f, heightDp = 832f)) // Galaxy Z Fold 7, inner screen
        assertTrue(isWideLayoutWindow(widthDp = 832f, heightDp = 750f))
        assertTrue(isWideLayoutWindow(widthDp = 960f, heightDp = 600f)) // 8" tablet, landscape
        assertTrue(isWideLayoutWindow(widthDp = 1280f, heightDp = 800f)) // 10" tablet, landscape
    }

    @Test
    fun `the thresholds are inclusive and a narrow or short pane stays single`() {
        assertTrue(isWideLayoutWindow(600f, 480f))
        assertFalse(isWideLayoutWindow(599.9f, 480f))
        assertFalse(isWideLayoutWindow(600f, 479.9f))
        // A narrow multi-window pane on a big display.
        assertFalse(isWideLayoutWindow(500f, 900f))
    }

    @Test
    fun `the chrome asks the height-aware rule and the width-only layouts are left alone`() {
        var root = File("").absoluteFile
        while (!File(root, "settings.gradle.kts").isFile) root = root.parentFile ?: error("repo root not found")
        fun src(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()
        assertTrue(src("MainActivity.kt").contains("val isWideLayout = iad1tya.echo.music.ui.utils.rememberIsWideShell()"))
        assertTrue(src("ui/newui/AuraPlayer.kt").contains("val isWideLayout = rememberIsWideShell() && !inSplitPane"))
        // The equalizer is a protected zone: its layout rule did not change.
        assertTrue(src("ui/screens/equalizer/axion/AxionEqScreen.kt").contains("rememberIsWideLayout()"))
    }
}
