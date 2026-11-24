package iad1tya.echo.music.migration

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 844-845 (owner 2026-11-24: «me interesa que se agregue Amazon Music»): the app side of the
 * read-only Amazon Music source. The parsing, paging and cookie allowlist are tested in the `:migration`
 * module (AmazonMusicSourceTest); this pins what can only be read from the app's sources: the source is
 * offered where the others are and behind the same YouTube gate, the session is stored encrypted and
 * cleared on logout, the login screen does not poll, and nothing from the session reaches the shared log.
 */
class AmazonMigrationWiringGuardTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    private fun between(text: String, start: String, end: String): String {
        val from = text.indexOf(start)
        assertTrue("missing «$start»", from >= 0)
        val to = text.indexOf(end, from + start.length)
        assertTrue("missing «$end» after «$start»", to > from)
        return text.substring(from, to)
    }

    @Test
    fun `Amazon Music is a source of the picker, behind the same YouTube gate as the others`() {
        val screen = main("ui/newui/AuraMigrationScreen.kt")
        val picker = between(
            screen,
            "private fun androidx.compose.foundation.lazy.LazyListScope.auraPickerContent(",
            "// ── COLLECTION",
        )
        val gateEnd = picker.indexOf("        return\n    }")
        val apple = picker.indexOf("ActionLog.tap(ActionId.MIG_SOURCE_APPLE); onApple()")
        val amazon = picker.indexOf("ActionLog.tap(ActionId.MIG_SOURCE_AMAZON); onAmazon()")
        assertTrue("the YouTube gate is where it was", gateEnd > 0)
        assertTrue("the Amazon row exists", amazon > 0)
        assertTrue("the Amazon row sits after the gate, with the other sources", amazon > gateEnd && amazon > apple)
        assertTrue(screen.contains("onAmazon = { navController.navigate(\"migration/amazon\") },"))

        val nav = main("ui/screens/NavigationBuilder.kt")
        assertTrue(nav.contains("composable(\"migration/amazon\") {\n        MigrationAmazonScreenHost(navController)\n    }"))
        assertTrue(main("ui/newui/NewUiHosts.kt").contains("fun MigrationAmazonScreenHost(navController: NavController)"))
    }

    @Test
    fun `the login window is private to the app and event-driven`() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val entry = between(manifest, "android:name=\".migration.AmazonMusicLoginActivity\"", "/>")
        assertTrue("not reachable from other apps", entry.contains("android:exported=\"false\""))

        val activity = main("migration/AmazonMusicLoginActivity.kt")
        for (poll in listOf("postDelayed", "Handler(", "Timer(", "while (true)", "delay(")) {
            assertFalse("rule 7: the login screen must not poll ($poll)", activity.contains(poll))
        }
        assertTrue(activity.contains("override fun onPageFinished(view: WebView, url: String?) = tryConnect(url, force = false)"))
        // Every way out leaves no Amazon cookie in the WebView's plain jar.
        assertTrue(between(activity, "private fun finishLogin(", "override fun onResume").contains("expireAmazonWebCookies()"))
        assertTrue(between(activity, "override fun onDestroy()", "private fun dp(").contains("expireAmazonWebCookies()"))
    }

    @Test
    fun `the session is stored encrypted, cleared on logout, and only replayed without redirects`() {
        val store = main("migration/AmazonMusicSessionStore.kt")
        assertTrue(store.contains("EncryptedSharedPreferences.create("))
        assertFalse("never the plain settings DataStore", store.contains("dataStore"))
        assertFalse("never a whole-file wipe the settings guard forbids", store.contains("deleteSharedPreferences"))

        val module = main("di/MigrationModule.kt")
        assertTrue(module.contains("): AmazonSessionStore = EncryptedAmazonSessionStore(context)"))
        assertTrue(module.contains("AmazonMusicClient(OkHttpAmazonHttp(migrationHttpClient), store)"))

        val vm = main("ui/screens/migration/MigrationViewModel.kt")
        val logout = between(vm, "fun logoutAmazon()", "fun dismissAmazonError()")
        assertTrue(logout.contains("amazonClient.logout()"))
        assertTrue(logout.contains("expireAmazonWebCookies()"))
    }

    @Test
    fun `a sign-out stops the library load so the old account cannot come back`() {
        val vm = main("ui/screens/migration/MigrationViewModel.kt")
        val logout = between(vm, "fun logoutAmazon()", "fun dismissAmazonError()")
        val cancel = logout.indexOf("amazonLoadJob?.cancel()")
        val bump = logout.indexOf("amazonGeneration++")
        val clear = logout.indexOf("amazonClient.logout()")
        assertTrue("the load is cancelled and the generation bumped before the vault is cleared", cancel in 0 until clear && bump in 0 until clear)

        val load = between(vm, "fun loadAmazonCollection()", "fun logoutAmazon()")
        assertTrue(load.contains("amazonLoadJob = viewModelScope.launch {"))
        assertTrue(
            "a result read for a session that was signed out is dropped",
            Regex("""if \(startedIn != amazonGeneration\) return@launch""").findAll(load).count() == 2,
        )
        val refresh = between(vm, "fun refreshAmazonAuth()", "fun loadAmazonCollection()")
        assertTrue(refresh.contains("if (startedIn != amazonGeneration) return@launch"))
    }

    /** Source without string literals, so a log FORMAT may say «session» while its ARGUMENTS may not. */
    private fun withoutLiterals(code: String): String = code.replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "\"\"")

    @Test
    fun `no Amazon log line carries a cookie, a header, a URL or a name from the library`() {
        val vm = main("ui/screens/migration/MigrationViewModel.kt")
        val sources = mapOf(
            "AmazonMusicLoginActivity.kt" to main("migration/AmazonMusicLoginActivity.kt"),
            "AmazonMusicSessionStore.kt" to main("migration/AmazonMusicSessionStore.kt"),
            "AmazonMusicErrors.kt" to main("migration/AmazonMusicErrors.kt"),
            "MigrationViewModel.kt (Amazon)" to between(vm, "// ── AMAZON MUSIC (read only)", "// ── IMPORT"),
        )
        val call = Regex("""Timber(?:\.tag\([^)]*\))?\.[a-z]+\(""")
        var seen = 0
        for ((name, text) in sources) {
            for (m in call.findAll(text)) {
                seen++
                var depth = 1
                var i = m.range.last + 1
                while (i < text.length && depth > 0) {
                    when (text[i]) { '(' -> depth++; ')' -> depth-- }
                    i++
                }
                val args = withoutLiterals(text.substring(m.range.last + 1, i - 1))
                assertFalse("$name passes a throwable (its message may echo a response): $args", Regex("""^\s*(e|it|t)\s*,""").containsMatchIn(args))
                for (id in Regex("[A-Za-z_][A-Za-z0-9_]*").findAll(args).map { it.value.lowercase() }) {
                    val leak = listOf("cookie", "header", "url", "host", "playlist", "message", "body", "token", "session")
                        .firstOrNull { it in id } ?: id.takeIf { it in setOf("name", "title", "artists", "it") }
                    assertTrue("$name logs «$leak»: $args", leak == null)
                }
            }
        }
        assertTrue("the Amazon lines are where they were", seen >= 5)
    }

    @Test
    fun `every Amazon text exists in English and Spanish and names no Google service`() {
        fun keys(dir: String): Map<String, String> =
            Regex("""<string name="(migrate_(?:source_)?amazon[a-z_]*)">([^<]*)</string>""")
                .findAll(File(root, "app/src/main/res/$dir/strings.xml").readText())
                .associate { it.groupValues[1] to it.groupValues[2] }
        val en = keys("values")
        val es = keys("values-es")
        assertTrue("the Amazon strings are there", en.size >= 20)
        assertEquals(en.keys, es.keys)
        for ((key, text) in en + es) assertFalse("$key says «YouTube Music»", text.contains("YouTube Music"))

        val code = listOf(
            main("ui/newui/AuraMigrationScreen.kt"),
            main("migration/AmazonMusicLoginActivity.kt"),
            main("migration/AmazonMusicErrors.kt"),
            main("ui/screens/migration/MigrationViewModel.kt"),
        ).joinToString("\n")
        for (used in Regex("""R\.string\.(migrate_(?:source_)?amazon[a-z_]*)""").findAll(code).map { it.groupValues[1] }.toSet()) {
            assertTrue("R.string.$used is used but not defined", used in en)
        }
    }
}
