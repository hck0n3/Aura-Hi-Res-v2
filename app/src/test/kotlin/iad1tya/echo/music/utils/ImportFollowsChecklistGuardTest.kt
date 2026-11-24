package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 831 (owner 2026-11-24, K1): «N artistas importados esperan confirmación» → «Suscribir» released the whole held set
 * while showing only a number. The dialog now lists every held artist by name with a checkbox, none ticked, and each button acts
 * on the ticked ones only, through the same hold/release writes as before (row 646). Source-text checks: it is a Compose dialog.
 */
class ImportFollowsChecklistGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String): String {
        val f = File(root, path)
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val screen by lazy { read("app/src/main/kotlin/com/music/echo/ui/screens/settings/YtmSyncScreen.kt") }

    @Test
    fun `the held artists are listed by name, none ticked when the list opens`() {
        assertTrue(screen.contains("val awaitingImportArtists by androidx.compose.runtime.produceState(emptyList<ArtistEntity>(), awaitingImportIds) {"))
        assertTrue(screen.contains("}.sortedBy { it.name.lowercase() }"))
        assertTrue(screen.contains("val awaitingImportCounted = remember(awaitingImportArtists) { awaitingImportArtists.mapTo(HashSet()) { it.id } }"))
        assertTrue(screen.contains("val checkedImportIds = remember { androidx.compose.runtime.mutableStateOf(emptySet<String>()) }"))
        assertTrue(
            "opening the list clears the ticks",
            screen.contains(
                "ActionLog.tap(ActionId.YTMSYNC_IMPORT_FOLLOWS)\n" +
                    "                    checkedImportIds.value = emptySet()\n" +
                    "                    showImportFollowsConfirm.value = true",
            ),
        )
        assertTrue(screen.contains("items(awaitingImportArtists, key = { it.id }) { artist ->"))
        assertTrue(screen.contains("Checkbox(checked = artist.id in checked, onCheckedChange = null)"))
        assertTrue(screen.contains("Text(artist.name, modifier = Modifier.padding(start = 8.dp))"))
        assertTrue(screen.contains("ActionLog.tap(ActionId.YTMSYNC_IMPORT_PICK)"))
    }

    @Test
    fun `each button releases or keeps only the ticked artists`() {
        assertTrue(screen.contains("val confirmed = awaitingImportCounted intersect checkedImportIds.value"))
        assertTrue(screen.contains("val keptLocal = awaitingImportCounted intersect checkedImportIds.value"))
        assertFalse("the whole set is never released at once", Regex("""val confirmed = awaitingImportCounted\s*\n""").containsMatchIn(screen))
        assertFalse(Regex("""val keptLocal = awaitingImportCounted\s*\n""").containsMatchIn(screen))
        assertTrue("nothing ticked, nothing to do", Regex("""enabled = anyChecked,""").findAll(screen).count() == 2)
        // The same hold/release writes as row 646.
        assertTrue(screen.contains("it[ImportFollowsAwaitingConfirmKey].orEmpty() - confirmed"))
        assertTrue(screen.contains("it[ImportFollowsLocalOnlyKey] = it[ImportFollowsLocalOnlyKey].orEmpty() + keptLocal"))
        assertTrue(screen.contains("R.string.ytm_upload_import_subscribe_checked"))
        assertTrue(screen.contains("R.string.ytm_upload_import_local_only_checked"))
        assertTrue(screen.contains("R.string.ytm_upload_import_pick_hint"))
    }

    @Test
    fun `one tap ticks or clears the whole list, and only on purpose`() {
        assertTrue(screen.contains("val allChecked = awaitingImportCounted.isNotEmpty() && awaitingImportCounted.all { it in checked }"))
        assertTrue(screen.contains("ActionLog.tap(ActionId.YTMSYNC_IMPORT_PICK_ALL)"))
        assertTrue(screen.contains("checkedImportIds.value = if (allChecked) emptySet() else awaitingImportCounted"))
        assertTrue(screen.contains("if (allChecked) R.string.ytm_upload_import_uncheck_all else R.string.ytm_upload_import_check_all"))
        for (dir in listOf("values-es", "values-es-rUS")) {
            val strings = read("app/src/main/res/$dir/echo_strings.xml")
            assertTrue(dir, strings.contains("<string name=\"ytm_upload_import_check_all\">Marcar todos</string>"))
            assertTrue(dir, strings.contains("<string name=\"ytm_upload_import_uncheck_all\">Desmarcar todos</string>"))
        }
        val en = read("app/src/main/res/values/echo_strings.xml")
        assertTrue(en.contains("<string name=\"ytm_upload_import_check_all\">"))
        assertTrue(en.contains("<string name=\"ytm_upload_import_uncheck_all\">"))
    }

    @Test
    fun `the button texts are the owner's words`() {
        for (dir in listOf("values-es", "values-es-rUS")) {
            val strings = read("app/src/main/res/$dir/echo_strings.xml")
            assertTrue(dir, strings.contains("<string name=\"ytm_upload_import_subscribe_checked\">Suscribir a los marcados</string>"))
            assertTrue(dir, strings.contains("<string name=\"ytm_upload_import_local_only_checked\">Descartar los marcados</string>"))
            assertTrue(dir, strings.contains("<string name=\"ytm_upload_import_pick_hint\">"))
        }
        val en = read("app/src/main/res/values/echo_strings.xml")
        assertTrue(en.contains("<string name=\"ytm_upload_import_subscribe_checked\">"))
        assertTrue(en.contains("<string name=\"ytm_upload_import_local_only_checked\">"))
        assertTrue(en.contains("<string name=\"ytm_upload_import_pick_hint\">"))
    }
}
