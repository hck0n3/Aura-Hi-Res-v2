package iad1tya.echo.music.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Registry row 815 (owner decision A1, 2026-11-24, replaces H27): the sign-in prompt says «Inicia sesión con tu cuenta de
 * Google» and the visible texts stop saying «YouTube Music». A string that still names the service has to be on the
 * allowlist below with the reason it must keep the name.
 */
class GoogleSignInWordingGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val folders = listOf("values", "values-es", "values-es-rUS")

    /** Keys allowed to keep «YouTube Music», each with WHY. Keys and preference names are never renamed (only texts). */
    private val allowed = mapOf(
        "string:open_links_youtube_title" to "row 676: the Abrir enlaces toggles list each service by name",
        "string:open_links_prompt_body" to "row 676: says which links Aura opens; Android shows them by service name",
        "string:migrate_apple_intro" to "Apple's own transfer page names YouTube Music as the destination the user must pick",
        "string:listen_on_youtube_music" to "no R.string reference in the app (never shown); key kept, not renamed",
    )

    /** "tag:name" → its text (plurals and arrays: every item joined), from every XML file of one values folder. */
    private fun entries(dir: String): Map<String, String> {
        val folder = File(root, "app/src/main/res/$dir")
        assertTrue("$dir moved", folder.isDirectory)
        val out = LinkedHashMap<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        folder.listFiles { f -> f.name.endsWith(".xml") }!!.sortedBy { it.name }.forEach { file ->
            val children = factory.newDocumentBuilder().parse(file).documentElement.childNodes
            for (i in 0 until children.length) {
                val el = children.item(i) as? Element ?: continue
                val name = el.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
                out["${el.tagName}:$name"] = el.textContent.trim()
            }
        }
        return out
    }

    @Test
    fun `the sign-in prompt asks for the Google account in es, es-rUS and en`() {
        val key = "string:sync_login_required"
        assertEquals("Inicia sesión con tu cuenta de Google", entries("values-es")[key])
        assertEquals("Sign in with your Google account", entries("values")[key])
        // es-rUS is what the owner's es-419 resolves to first: it must not shadow the values-es wording.
        val latam = entries("values-es-rUS")[key]
        assertTrue("values-es-rUS: $latam", latam == null || latam == "Inicia sesión con tu cuenta de Google")
    }

    @Test
    fun `Ajustes Cuentas shows the Google account card with the sign-in prompt`() {
        val accounts = File(root, "app/src/main/kotlin/com/music/echo/ui/screens/settings/AccountsScreen.kt").readText()
        assertTrue(accounts.contains("title = \"Cuenta de Google\","))
        assertTrue(accounts.contains("!ytLoggedIn -> stringResource(R.string.sync_login_required)"))
        assertTrue(!accounts.contains("title = \"YouTube Music\","))
    }

    @Test
    fun `no es, es-rUS or en string says YouTube Music unless it is allowlisted`() {
        folders.forEach { dir ->
            val offenders = entries(dir).filter { (key, text) -> "YouTube Music" in text && key !in allowed }
            assertTrue("$dir still says «YouTube Music»: ${offenders.keys.joinToString()}", offenders.isEmpty())
        }
    }

    @Test
    fun `every allowlisted key still exists and still names the service`() {
        val en = entries("values")
        allowed.keys.forEach { key ->
            assertTrue("$key: drop it from the allowlist", en[key]?.contains("YouTube Music") == true)
        }
    }

    @Test
    fun `no Kotlin UI literal says YouTube Music`() {
        // Not UI: a log line or a comparison with the name YouTube gives its own playlists.
        val notUi = setOf(
            "App.kt",
            "viewmodels/HomeViewModel.kt",
            "ui/screens/recognition/RecognitionScreen.kt",
            "ui/screens/search/suggestions/SuggestionsViewModel.kt",
        )
        val base = File(root, "app/src/main/kotlin/com/music/echo")
        val literal = Regex("\"[^\"\\n]*YouTube Music[^\"\\n]*\"")
        val offenders = base.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.relativeTo(base).invariantSeparatorsPath !in notUi }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) ->
                        val t = line.trimStart()
                        !t.startsWith("*") && !t.startsWith("//") && !t.startsWith("/*") && literal.containsMatchIn(line)
                    }
                    .map { (i, _) -> "${file.relativeTo(base).invariantSeparatorsPath}:${i + 1}" }
            }.toList()
        assertTrue("Kotlin UI literals still say «YouTube Music»: $offenders", offenders.isEmpty())
    }
}
