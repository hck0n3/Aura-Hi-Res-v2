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

    // Row 858: the sync status/schedule screen (settings/ytm_sync) was reachable only from an account-sheet row labelled
    // «Programar sincronización», and from no row of Ajustes ▸ Cuentas y sincronización.
    private val syncLabel = "Estado y programación de la sincronización"
    private val syncDesc = "Cada 3 días por defecto, o elige cuándo"

    @Test
    fun `the account sheet row says it is the sync status`() {
        val sheet = src("ui/screens/SettingDialoge.kt")
        assertFalse(sheet.contains("Text(\"Programar sincronización\")"))
        val row = sheet.substringAfter("title = { Text(\"$syncLabel\") },").substringBefore("Material3SettingsGroup(")
        assertTrue(row.contains("\"$syncDesc\""))
        assertTrue(row.contains("onNavigate(\"settings/ytm_sync\")"))
        assertFalse("decision A1 (row 815): no service name in visible text", syncLabel.contains("YouTube"))
    }

    @Test
    fun `Ajustes lists the sync status under Cuentas y sincronizacion, signed in or not`() {
        val accounts = settings.substringAfter("title = groupAccounts,").substringBefore("title = storageText,")
        val entry = "title = \"$syncLabel\",\n                            description = \"$syncDesc\",\n" +
            "                        ) { navController.navigate(\"settings/ytm_sync\") }"
        assertTrue("the entry, with the account sheet's label and description", accounts.contains(entry))
        val before = accounts.substringBefore(entry)
        assertFalse("not gated on the sign-in (the screen offers it)", before.substringAfterLast("add(AuraSettingsEntry(accountsText)").contains("if ("))
        // Ajustes search lists the index rows themselves, so the entry is findable by its own words.
        assertTrue(settings.contains("val topMatches = (groups + more).flatMap { group ->"))
    }

    @Test
    fun `the search index reaches the sync screen by the words on it, without echoing the Ajustes row`() {
        val index = src("ui/screens/settings/SearchableSettings.kt")
        val screen = src("ui/screens/settings/YtmSyncScreen.kt")
        listOf("Sincronizar desde tu cuenta de Google", "Sincronización automática").forEach { text ->
            assertTrue("$text is on the screen", screen.contains("\"$text\""))
            assertTrue("$text is indexed", index.contains("Triple(\"$text\", \"Sincronización con la cuenta\", \"settings/ytm_sync\"),"))
        }
        assertFalse("INDEX-ECHO RULE: an Ajustes row is not repeated in the index", index.contains("Triple(\"$syncLabel\""))
    }
}
