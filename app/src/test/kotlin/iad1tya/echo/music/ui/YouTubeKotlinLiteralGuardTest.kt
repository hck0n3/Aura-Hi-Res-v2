package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 868 (plan phase 3, owner decision A1): a text written straight into Kotlin that the user can read does not
 * name «YouTube» where the name does no work. Row 815 covered «YouTube Music»; this guard covers the bare brand too, in
 * every module of the build. A visible literal that has to keep the name goes on [allowed] with the reason. Log
 * messages are not UI and stay as they are.
 */
class YouTubeKotlinLiteralGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    /** (repo-relative file, fragment of the literal) → why it keeps the name. */
    private val allowed = mapOf(
        ("app/src/main/kotlin/com/music/echo/utils/AgeGateChain.kt" to "YouTube pide confirmar la edad") to
            "functional: the age has to be confirmed on youtube.com with that same account",
        ("app/src/main/kotlin/com/music/echo/ui/screens/settings/AboutScreen.kt" to "Si pegas un enlace de YouTube") to
            "functional: the search box plays only YouTube links (YouTubeUrlParser)",
        ("migration/src/main/kotlin/com/aura/migration/source/apple/AppleMusicGuide.kt" to "elige YouTube Music como destino") to
            "functional: Apple's own transfer page names YouTube Music as the destination to pick",
        ("migration/src/main/kotlin/com/aura/migration/source/apple/AppleMusicGuide.kt" to "y en YouTube Music.") to
            "functional: Apple's transfer needs an account on the destination it names",
        ("app/src/main/kotlin/com/music/echo/viewmodels/HomeViewModel.kt" to "YouTube Music") to
            "not UI: compared with the author name the API gives its own playlists",
        ("app/src/main/kotlin/com/music/echo/ui/screens/settings/AccountSettingsScreen.kt" to "tu cuenta de YouTube") to
            "not shown: orphan screen with no route (NavigationBuilder), checked below",
    )

    private val brand = Regex("\\b(YouTube|Youtube|YT Music)\\b")

    /** Callees whose text goes to a log, never to the screen. */
    private val logCall = Regex("(^|\\.)(Timber|Log|AppLogger)(\\.tag\\([^()]*\\))?\\.(v|d|i|w|e|wtf)$|PlaybackLogManager\\.log$|^onScheduleFailed$")

    private val callee = Regex("((?:[A-Za-z_][A-Za-z0-9_]*(?:\\([^()]*\\))?\\s*\\??\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*)\\s*$")

    private class Literal(val line: Int, val text: String, val inLog: Boolean)

    /** Every string literal of a Kotlin or Java source, skipping comments, with whether a log call encloses it. */
    private fun literals(src: String): List<Literal> {
        val out = ArrayList<Literal>()
        val code = StringBuilder()
        val calls = ArrayList<String>()
        var i = 0
        var line = 1

        fun inLog() = calls.any { logCall.containsMatchIn(it) }

        fun skipComment(): Boolean {
            if (src.startsWith("//", i)) {
                while (i < src.length && src[i] != '\n') i++
                return true
            }
            if (src.startsWith("/*", i)) {
                var depth = 0
                while (i < src.length) {
                    when {
                        src.startsWith("/*", i) -> { depth++; i += 2 }
                        src.startsWith("*/", i) -> { depth--; i += 2; if (depth == 0) return true }
                        else -> { if (src[i] == '\n') line++; i++ }
                    }
                }
                return true
            }
            return false
        }

        lateinit var readCode: (Boolean) -> Unit

        fun readString() {
            val raw = src.startsWith("\"\"\"", i)
            val startLine = line
            val enclosedByLog = inLog()
            val text = StringBuilder()
            i += if (raw) 3 else 1
            while (i < src.length) {
                val c = src[i]
                when {
                    raw && src.startsWith("\"\"\"", i) -> {
                        i += 3
                        while (i < src.length && src[i] == '"') i++
                        break
                    }
                    !raw && c == '"' -> { i++; break }
                    !raw && c == '\\' -> { text.append(src.getOrElse(i + 1) { ' ' }); i += 2 }
                    src.startsWith("\${", i) -> {
                        i += 2
                        val before = code.length
                        readCode(true)
                        text.append(code, before, code.length)
                    }
                    else -> {
                        if (c == '\n') line++
                        text.append(c)
                        i++
                    }
                }
            }
            out += Literal(startLine, text.toString(), enclosedByLog)
            code.append("\"\"")
        }

        readCode = readCode@{ untilBrace ->
            var braces = 0
            while (i < src.length) {
                if (skipComment()) continue
                val c = src[i]
                when (c) {
                    '"' -> readString()
                    '\'' -> {
                        i++
                        while (i < src.length && src[i] != '\'') i += if (src[i] == '\\') 2 else 1
                        i++
                        code.append("' '")
                    }
                    '(' -> {
                        val tail = code.substring(maxOf(0, code.length - 200))
                        calls += callee.find(tail)?.groupValues?.get(1)?.replace(Regex("\\s+"), "").orEmpty()
                        code.append(c)
                        i++
                    }
                    ')' -> {
                        if (calls.isNotEmpty()) calls.removeAt(calls.size - 1)
                        code.append(c)
                        i++
                    }
                    '{' -> { braces++; code.append(c); i++ }
                    '}' -> {
                        if (untilBrace && braces == 0) { i++; return@readCode }
                        braces--
                        code.append(c)
                        i++
                    }
                    else -> {
                        if (c == '\n') line++
                        code.append(c)
                        i++
                    }
                }
            }
        }
        readCode(false)
        return out
    }

    private val sources: List<File> by lazy {
        val modules = Regex("include\\(\":([A-Za-z0-9_-]+)\"\\)")
            .findAll(File(root, "settings.gradle.kts").readText()).map { it.groupValues[1] }
            .filter { it != "smoketest" }.toList()
        assertTrue("modules not found", "app" in modules && "migration" in modules)
        modules.flatMap { module ->
            listOf("kotlin", "java").map { File(root, "$module/src/main/$it") }.filter { it.isDirectory }
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList() }
        }.filter { !it.relativeTo(root).invariantSeparatorsPath.startsWith("app/src/main/kotlin/com/music/echo/license/") }
    }

    /** "file:line «text»" of every literal that names the brand in a sentence, outside a log call. */
    private val visible: List<Triple<String, Int, String>> by lazy {
        sources.flatMap { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            literals(file.readText()).filter { lit ->
                !lit.inLog && brand.containsMatchIn(lit.text) && lit.text.any { it.isWhitespace() }
            }.map { Triple(path, it.line, it.text) }
        }
    }

    @Test
    fun `the literal scanner tells UI text from logs and comments`() {
        val sample = """
            // "YouTube in a comment"
            /* "YouTube in a /* nested */ block" */
            fun f() {
                Timber.tag(TAG).w("log about YouTube %s", x)
                timber.log.Timber.i(
                    "multi-line log on YouTube",
                )
                val c = '"'
                Text("Visible YouTube text ${'$'}{call("inner")}")
                PlaybackLogManager.log(level, "title", "playback log on YouTube")
                Text(${"\"\"\""}raw YouTube text${"\"\"\""})
            }
        """.trimIndent()
        val found = literals(sample).filter { brand.containsMatchIn(it.text) }
        assertEquals(
            listOf("log about YouTube %s" to true, "multi-line log on YouTube" to true, "Visible YouTube text call(\"\")" to false,
                "playback log on YouTube" to true, "raw YouTube text" to false),
            found.map { it.text to it.inLog },
        )
        assertEquals(9, found.single { it.text.startsWith("Visible") }.line)
    }

    @Test
    fun `no visible Kotlin literal names YouTube unless it is allowlisted`() {
        val offenders = visible.filter { (path, _, text) ->
            allowed.keys.none { (file, fragment) -> file == path && fragment in text }
        }.map { (path, line, text) -> "$path:$line «${text.take(80)}»" }
        assertTrue("Visible literals still name YouTube:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `every allowlisted literal still exists`() {
        allowed.keys.forEach { (file, fragment) ->
            assertTrue("$file «$fragment»: drop it from the allowlist", visible.any { it.first == file && fragment in it.third })
        }
    }

    @Test
    fun `the allowlisted orphan account screen still has no route`() {
        val callers = sources.filter { it.name != "AccountSettingsScreen.kt" }
            .filter { Regex("\\bAccountSettingsScreen\\s*\\(").containsMatchIn(it.readText()) }
        assertTrue("AccountSettingsScreen is reachable again: rewrite its «tu cuenta de YouTube» texts. $callers", callers.isEmpty())
    }

    @Test
    fun `the rewritten texts say Google account or online catalogue`() {
        val about = File(root, "app/src/main/kotlin/com/music/echo/ui/screens/settings/AboutScreen.kt").readText()
        assertTrue(about.contains("sin ella, el catálogo en línea solo da el año"))
        assertTrue(about.contains("En cada playlist vinculada a tu cuenta de Google tienes 'Sincronizar ahora'"))
        assertTrue(about.contains("RSS, sin depender del catálogo en línea:"))
        assertTrue(about.contains("\"Cuando el catálogo en línea cambia su reproductor y la música deja de sonar,"))
        val content = File(root, "app/src/main/kotlin/com/music/echo/ui/screens/settings/ContentSettings.kt").readText()
        assertTrue(content.contains("title = \"Configuración del catálogo en línea\","))
        assertTrue(content.contains("title = { Text(\"Opciones del catálogo en línea\") },"))
        val upload = File(root, "app/src/main/kotlin/com/music/echo/utils/LibraryUploadSync.kt").readText()
        assertTrue(upload.contains("\"Tu cuenta de Google no está aceptando más suscripciones ahora mismo; se reintentará más tarde\""))
        assertFalse(upload.contains("\"YouTube no está aceptando"))
    }
}
