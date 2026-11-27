package iad1tya.echo.music.reco

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 906 — «Tu gusto». The AI call must sit behind the consent gate, read the stored consent right before sending,
 * run only from its button, log counts only and never run in the background; the policies must say what is sent. Android,
 * DataStore and Compose do not run in a JVM test, so the wiring is pinned as source.
 */
class TasteGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    private val main = "app/src/main/kotlin/com/music/echo"
    private val vm by lazy { src("$main/viewmodels/TasteViewModel.kt") }
    private val screen by lazy { src("$main/ui/screens/settings/TasteScreen.kt") }

    @Test
    fun `the AI is asked only through the gate, with the consent read right before`() {
        val ask = vm.substringAfter("fun askAi() {").substringBefore("\n    /**")
        assertTrue(ask.contains("val prefs = context.dataStore.data.first()"))
        assertTrue(ask.contains("TasteAiGate.ask(prefs[TasteAiConsentKey], stats, language) { system, user ->"))
        assertTrue("the call is inside the gate's lambda", ask.indexOf("TasteAiGate.ask(") < ask.indexOf("AiPlaylistService.describeTaste("))
        assertEquals(1, Regex("""AiPlaylistService\.describeTaste\(""").findAll(vm).count())
        val callers = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && it.readText().contains("describeTaste(") }
            .map { it.name }.toSet()
        assertEquals(setOf("TasteViewModel.kt", "AiPlaylistService.kt"), callers)
        val service = src("$main/api/AiPlaylistService.kt").substringAfter("suspend fun describeTaste(").substringBefore("\n    private fun toJsonArray")
        assertTrue("same provider chain as the AI playlists", service.contains("runChain("))
        assertTrue(service.contains("TasteSummary.parseAnswer(content) != null"))
    }

    @Test
    fun `the consent is off until accepted, and only the screen writes it`() {
        assertTrue(src("$main/reco/TasteAiConsent.kt").contains("val TasteAiConsentKey = intPreferencesKey(\"taste_ai_consent_version\")"))
        val writers = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && Regex("""it\[TasteAiConsentKey]\s*=|remove\(TasteAiConsentKey\)""").containsMatchIn(it.readText()) }
            .map { it.name }.toSet()
        assertEquals(setOf("TasteViewModel.kt"), writers)
        assertTrue(vm.contains("it[TasteAiConsentKey] = TasteAiGate.CONSENT_VERSION"))
        assertTrue(vm.contains(".stateIn(viewModelScope, SharingStarted.Eagerly, false)"))
        // The ask button exists only past the consent; the dialog's «Aceptar» only stores the acceptance.
        val accepted = screen.substringAfter("if (!accepted) {").substringBefore("\n            item { Spacer(Modifier.height(16.dp)) }")
        assertTrue(accepted.indexOf("} else {") in 0 until accepted.indexOf("viewModel.askAi()"))
        assertEquals(1, Regex("""viewModel\.askAi\(\)""").findAll(screen).count())
        val accept = vm.substringAfter("fun acceptConsent() {").substringBefore("\n    /**")
        assertFalse("accepting sends nothing", accept.contains("askAi") || accept.contains("describeTaste"))
    }

    @Test
    fun `nothing runs in the background and the logs carry counts only`() {
        listOf("WorkManager", "PeriodicWorkRequest", "while (true)", "delay(", "setInitialDelay").forEach {
            assertFalse("background work in «Tu gusto»: $it", vm.contains(it) || screen.contains(it))
        }
        val logs = Regex("""Timber\.\w\([^\n]*(\n[^\n]*){0,3}""").findAll(vm).map { it.value }.toList()
        assertTrue(logs.isNotEmpty())
        logs.forEach { line ->
            listOf(".name", "summary", "chips", "taste.", "prefs[", "user)", "system)").forEach { assertFalse("$it in a log: $line", line.contains(it)) }
        }
        assertTrue(vm.contains("\"TASTE_AI outcome=%s artists=%d genres=%d decades=%d\""))
    }

    @Test
    fun `both privacy policies say exactly what is sent, and the consent text says the same`() {
        val es = src("PRIVACIDAD.md")
        val en = src("PRIVACY_POLICY.md")
        assertTrue(es.contains("| **\"Tu gusto\": descripción con IA** | **Desactivada**"))
        assertTrue(en.contains("| **\"Your taste\": AI description** | **Off**"))
        listOf(es, en).forEach { policy ->
            val row = policy.lines().single { it.contains("\"Tu gusto\": descripción con IA") || it.contains("\"Your taste\": AI description") }
            listOf("8", "5", "4").forEach { assertTrue("$it in: $row", row.contains(" $it ")) }
        }
        val consentEs = src("app/src/main/res/values-es/strings.xml").lines().single { it.contains("name=\"taste_consent_body\"") }
        val consentEn = src("app/src/main/res/values/strings.xml").lines().single { it.contains("name=\"taste_consent_body\"") }
        listOf(consentEs, consentEn).forEach { line ->
            listOf("8", "5", "4", "48").forEach { assertTrue("$it in: $line", line.contains(it)) }
        }
        assertEquals(8, TasteSummary.MAX_ARTISTS)
        assertEquals(5, TasteSummary.MAX_GENRES)
        assertEquals(4, TasteSummary.MAX_DECADES)
    }

    @Test
    fun `the screen is reachable from Ajustes, registered as a pane, indexed, and every text exists in English and Spanish`() {
        assertTrue(src("$main/ui/screens/settings/AiSettings.kt").contains("navController.navigate(\"settings/ai/taste\")"))
        assertTrue(src("$main/ui/screens/NavigationBuilder.kt").contains("composable(\"settings/ai/taste\") {"))
        assertTrue(src("$main/ui/newui/AuraSettingsPanes.kt").contains("\"settings/ai/taste\" to \"settings/ai\","))
        assertTrue(src("$main/ui/screens/settings/SearchableSettings.kt").contains("Triple(stringResource(R.string.taste_title), "))
        val keys = { path: String -> Regex("""name="(taste_\w+)"""").findAll(src(path)).map { it.groupValues[1] }.toSet() }
        val en = keys("app/src/main/res/values/strings.xml")
        val es = keys("app/src/main/res/values-es/strings.xml")
        assertTrue(en.size >= 20)
        assertEquals(en, es)
    }
}
