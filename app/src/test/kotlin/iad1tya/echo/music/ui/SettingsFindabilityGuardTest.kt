package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner's on-device test of 2026-11-24 (Galaxy Z Fold7, beta8), Ajustes findability.
 *
 * Row 857: the «Acerca de» section of Ajustes (it holds «Actualizaciones» and «Acerca de») did not unfold, because the trailing
 * «MÁS» groups were drawn with `expanded = false, onToggle = {}` — fine for the one-row groups that navigate straight away, a
 * dead tap for the only two-row one.
 */
class SettingsFindabilityGuardTest {
    private val main =
        File(
            generateSequence(File("").absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "settings.gradle.kts").isFile }
                ?: error("could not locate the repository root from ${File("").absolutePath}"),
            "app/src/main/kotlin/com/music/echo",
        )

    private fun src(path: String) = File(main, path).readText()

    private val settings by lazy { src("ui/newui/AuraSettingsScreen.kt") }

    @Test
    fun `the MAS groups unfold with the same toggle as the seven above`() {
        assertFalse("a MÁS group with a no-op toggle never unfolds", settings.contains("expanded = false, onToggle = {}"))
        val toggle = "expandedGroup = if (expandedGroup == group.title) null else group.title"
        assertEquals("both lists of groups share the one-open-at-a-time toggle", 2, Regex(Regex.escape(toggle)).findAll(settings).count())
        val moreLoop = settings.substringAfter("AuraMenuGroupLabel(\"MÁS\")").substringBefore("} else {")
        assertTrue(moreLoop.contains("more.forEach { group ->"))
        assertTrue(moreLoop.contains("expanded = expandedGroup == group.title"))
        assertTrue(moreLoop.contains(toggle))
    }

    @Test
    fun `Acerca de is a two-row group, so it needs the toggle`() {
        val about = settings.substringAfter("title = aboutText,").substringBefore("// Only one group is open at a time")
        assertTrue(about.contains("title = \"Actualizaciones\""))
        assertTrue(about.contains("navController.navigate(\"settings/update\")"))
        assertTrue(about.contains("AuraSettingsEntry(aboutText) { navController.navigate(\"settings/about\") }"))
        // Single-row groups still navigate straight there; only a group with several rows calls onToggle.
        assertTrue(settings.contains("if (single) group.entries.first().onClick() else onToggle()"))
    }
}
