package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P22D (row 735): the settings search opens the destination scrolled to the exact row and flashes it.
 * There is no Compose runtime in the unit job, so the key plumbing is pinned as source text: both search
 * screens hand the result's key to [iad1tya.echo.music.ui.utils.SettingsSearchJump], every settings row and
 * group header wears the anchor, and the jump stays a one-shot that costs nothing while it is not armed.
 */
class SettingsSearchJumpGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    private val jumpCall = "SettingsSearchJump.navigate(navController, view, route, title)"

    @Test
    fun `both settings searches pass the result key to the jump`() {
        listOf("ui/screens/settings/SettingsScreen.kt", "ui/newui/AuraSettingsScreen.kt").forEach { path ->
            val s = src(path)
            assertTrue("$path: result must jump to its row", s.contains(jumpCall))
            assertTrue("$path: the jump needs the window's view", s.contains("val view = LocalView.current"))
            assertFalse("$path: a bare navigate(route) opens the screen at the top again", s.contains("onClick = { navController.navigate(route) }"))
        }
    }

    @Test
    fun `every settings row and group header is a jump anchor`() {
        val group = src("ui/component/Material3SettingsGroup.kt")
        assertTrue("row", group.contains(".then(rememberSettingsJumpAnchor(settingsJumpWash(skin)))"))
        assertTrue("header", group.contains("modifier = rememberSettingsJumpAnchor(settingsJumpWash(skin)).padding("))
    }

    @Test
    fun `the jump is one-shot, bounded, unlogged and free while idle`() {
        val jump = src("ui/utils/SettingsSearchJump.kt")
        assertTrue("idle rows add nothing", jump.contains("if (!SettingsSearchJump.armed) return Modifier"))
        assertTrue("runs on the destination entry only", jump.contains("entry.lifecycleScope.launch"))
        assertTrue("waits for the enter transition", jump.contains("it.isAtLeast(Lifecycle.State.RESUMED)"))
        assertTrue("bounded", jump.contains("withTimeoutOrNull(JUMP_TIMEOUT_MS)"))
        assertTrue("the wash is read in the draw phase only", jump.contains("val a = SettingsSearchJump.flashAlpha"))
        assertTrue("centres with the scroller's own action", jump.contains("scroller.config[SemanticsActions.ScrollBy].action?.invoke(0f, dy)"))
        assertTrue("~1.5 s", jump.contains("FLASH_IN_MS = 200") && jump.contains("FLASH_HOLD_MS = 800L") && jump.contains("FLASH_OUT_MS = 500"))
        // AGENTS.md rule 4: the key is a setting title; nothing about the search goes to app.log.
        assertFalse(jump.contains("AppLogger"))
        assertFalse(jump.contains("Log."))
    }
}
