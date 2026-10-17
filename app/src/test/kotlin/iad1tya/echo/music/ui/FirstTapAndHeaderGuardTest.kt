package iad1tya.echo.music.ui

import iad1tya.echo.music.utils.RefreshMode
import iad1tya.echo.music.utils.RefreshRatePlanning
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 396 (owner 2026-10-15): header with the name on top and the buttons behind one toggle; refresh rate
 * that follows the device's state; Cast and the other player buttons on the first tap.
 */
class FirstTapAndHeaderGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root")
    }

    private fun src(path: String): String {
        val f = File(repoRoot, path)
        assertTrue("$path se movió — actualiza este test", f.isFile)
        return f.readText()
    }

    private val m60 = RefreshMode(1, 1080, 2340, 60f)
    private val m90 = RefreshMode(2, 1080, 2340, 90f)
    private val m120 = RefreshMode(3, 1080, 2340, 120f)
    private val other = RefreshMode(4, 720, 1560, 120f)

    @Test
    fun `a cool phone gets the highest mode of its current resolution, as before`() {
        val pick = RefreshRatePlanning.pick(listOf(m60, m90, m120, other), m60, RefreshRatePlanning.ceilingHz(false, 0))
        assertEquals(m120, pick)
    }

    @Test
    fun `heat and battery saver lower the ceiling`() {
        assertEquals(RefreshRatePlanning.NO_CEILING, RefreshRatePlanning.ceilingHz(false, 1), 0f)
        assertEquals(90f, RefreshRatePlanning.ceilingHz(false, 2), 0f)
        assertEquals(60f, RefreshRatePlanning.ceilingHz(false, 3), 0f)
        assertEquals(60f, RefreshRatePlanning.ceilingHz(true, 0), 0f)
        val modes = listOf(m60, m90, m120)
        assertEquals(m90, RefreshRatePlanning.pick(modes, m120, 90f))
        assertEquals(m60, RefreshRatePlanning.pick(modes, m120, 60f))
    }

    @Test
    fun `a panel that reports 59 99 still counts as 60 and a ceiling below every mode takes the lowest`() {
        val m5999 = RefreshMode(5, 1080, 2340, 59.99f)
        assertEquals(m5999, RefreshRatePlanning.pick(listOf(m5999, m120), m120, 60f))
        assertEquals(m90, RefreshRatePlanning.pick(listOf(m90, m120), m120, 60f))
    }

    @Test
    fun `the resolution is never changed and an empty list is null`() {
        assertEquals(m60, RefreshRatePlanning.pick(listOf(m60, other), m60, RefreshRatePlanning.NO_CEILING))
        assertNull(RefreshRatePlanning.pick(emptyList(), m60, 60f))
    }

    @Test
    fun `the window follows the display, battery saver and heat and never re-asserts the same mode`() {
        val main = src("app/src/main/kotlin/com/music/echo/MainActivity.kt")
        assertTrue(main.contains("registerDisplayListener("))
        assertTrue(main.contains("ACTION_POWER_SAVE_MODE_CHANGED"))
        assertTrue(main.contains("RefreshRatePlanning.ceilingHz(powerSave, thermalStatus)"))
        assertTrue(main.contains("layoutParams.preferredDisplayModeId != target.id"))
    }

    @Test
    fun `the header puts the name first and the greeting under it, with the buttons behind one toggle`() {
        val c = src("app/src/main/kotlin/com/music/echo/ui/newui/AuraContent.kt")
        val header = c.substring(c.indexOf("fun AuraScreenHeader("), c.indexOf("private val AuraHeaderGap"))
        assertTrue("el saludo va DEBAJO del nombre", header.indexOf("text = styledTitle") < header.indexOf("AuraSectionLabel(label)"))
        assertTrue(header.contains("AuraCollapsibleHeader("))
        for (f in listOf("AuraHomeScreen", "AuraSearchScreen", "AuraNovedadesScreen")) {
            assertTrue("$f debe ocultar los botones tras el interruptor", src("app/src/main/kotlin/com/music/echo/ui/newui/$f.kt").contains("collapseTrailing = true"))
        }
    }

    @Test
    fun `cast and the player buttons fire on lift even when the thumb rolls`() {
        val cast = src("app/src/gms/kotlin/com/music/echo/ui/component/CastButton.kt")
        assertTrue(cast.contains(".unstealableClick(onCastClick)"))
        assertTrue(cast.contains(".clickable(onClick = onCastClick)"))
        val shell = src("app/src/main/kotlin/com/music/echo/ui/newui/AuraShell.kt")
        // The detector must NOT restart when the lambda changes mid-press.
        assertTrue(shell.contains("pointerInput(Unit) {\n        awaitEachGesture {"))
        assertTrue(shell.contains("rememberUpdatedState(onClick)"))
        assertTrue(src("app/src/main/kotlin/com/music/echo/ui/newui/AuraQueue.kt").contains("Modifier.unstealableClick(onClick)"))
        assertTrue(src("app/src/main/kotlin/com/music/echo/ui/newui/AuraPlayer.kt").contains("Registry row 396: a thumb that rolls"))
    }
}
