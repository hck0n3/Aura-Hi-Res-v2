package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 730 (owner 2026-11-23: «un ID de registro distinto por cada acción o botón»).
 *
 * Every tap writes `ACT a=<n> <code>` to the shared `app.log`; for a few seconds after it every other line ends in ` a=<n>`.
 * This guards the three things that make that safe and useful: one stable code per action (no duplicates, documented),
 * nothing but an [ActionId] (or, for settings, a key and a boolean/enum) ever reaching the log (AGENTS rule 4), and the join.
 */
class ActionLogGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private val mainDir = File(root, "app/src/main/kotlin/com/music/echo")

    private fun main(path: String) = File(mainDir, path).readText()

    /**
     * Source without comments, so a KDoc that names `ActionLog.tap` is not mistaken for a call. String and char literals are
     * kept as they are: a MIME type or a URL inside a string must not open or close a comment.
     */
    private fun code(text: String): String {
        val out = StringBuilder(text.length)
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                text.startsWith("\"\"\"", i) -> {
                    val close = text.indexOf("\"\"\"", i + 3)
                    val end = if (close < 0) n else close + 3
                    out.append(text, i, end)
                    i = end
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < n && text[j] != c && text[j] != '\n') {
                        if (text[j] == '\\') j++
                        j++
                    }
                    val end = minOf(j + 1, n)
                    out.append(text, i, end)
                    i = end
                }
                text.startsWith("//", i) -> {
                    val eol = text.indexOf('\n', i)
                    i = if (eol < 0) n else eol
                }
                text.startsWith("/*", i) -> {
                    var depth = 1
                    var j = i + 2
                    while (j < n && depth > 0) {
                        when {
                            text.startsWith("/*", j) -> { depth++; j += 2 }
                            text.startsWith("*/", j) -> { depth--; j += 2 }
                            else -> j++
                        }
                    }
                    out.append(' ')
                    i = j
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private val sources: List<File> by lazy {
        mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Argument text of every `ActionLog.<fn>(…)` call, with the file it is in. Balanced parentheses, so nested calls are kept. */
    private fun calls(): List<Triple<File, String, String>> {
        val found = mutableListOf<Triple<File, String, String>>()
        val start = Regex("""\bActionLog\.([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        for (file in sources) {
            if (file.name == "ActionLog.kt") continue
            val text = code(file.readText())
            for (m in start.findAll(text)) {
                var depth = 1
                var i = m.range.last + 1
                while (i < text.length && depth > 0) {
                    when (text[i]) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                    i++
                }
                found += Triple(file, m.groupValues[1], text.substring(m.range.last + 1, i - 1).trim())
            }
        }
        return found
    }

    @Test
    fun `every code is unique, well formed and documented`() {
        val codes = ActionId.entries.map { it.code }
        assertTrue("there are codes", codes.size >= 100)
        val dupes = codes.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate action codes: $dupes", dupes.isEmpty())
        for (code in codes) {
            assertTrue("malformed code $code", Regex("""UI(\.[A-Z][A-Z0-9_]*){2}""").matches(code))
        }
        val docs = File(root, "docs/ACTION_LOG_CODES.md").readText()
        for (code in codes) assertTrue("$code is missing from docs/ACTION_LOG_CODES.md", docs.contains("`$code`"))
    }

    @Test
    fun `every code is used by at least one button`() {
        val all = sources.filter { it.name != "ActionId.kt" }.joinToString("\n") { code(it.readText()) }
        for (id in ActionId.entries) {
            assertTrue("ActionId.${id.name} is never logged", Regex("""\bActionId\.${id.name}\b""").containsMatchIn(all))
        }
    }

    @Test
    fun `no ActionLog call carries free text`() {
        val found = calls()
        assertTrue("the buttons are wired", found.count { it.second == "tap" } >= 100)
        val enumArg = Regex("""(iad1tya\.echo\.music\.utils\.)?ActionId\.[A-Z][A-Z0-9_]*""")
        for ((file, fn, args) in found) {
            val where = "${file.name}: ActionLog.$fn($args)"
            assertFalse("text in $where", args.contains('"') || args.contains('$') || args.contains('+'))
            when (fn) {
                "tap" -> assertTrue("only an ActionId constant may be passed: $where", enumArg.matches(args))
                "correlationSuffix" -> assertEquals("only the logger joins lines: $where", "AppLogger.kt", file.name)
                "setting" -> {
                    assertEquals("settings are logged only by the preference setters: $where", "DataStore.kt", file.name)
                    assertEquals(where, "key, value", args)
                }
                else -> error("unexpected ActionLog call: $where")
            }
        }
    }

    @Test
    fun `a changed switch or choice logs its key and new value, and nothing else`() {
        val store = code(main("utils/DataStore.kt"))
        val plain = store.substringAfter("fun <T> rememberPreference(").substringBefore("fun <reified T : Enum<T>> rememberEnumPreference(")
        assertTrue("only on a real change", plain.contains("if (value != state.value) {"))
        assertTrue(plain.contains("is Boolean -> ActionLog.setting(key, value)"))
        assertTrue(plain.contains("is Enum<*> -> ActionLog.setting(key, value)"))
        assertFalse("never a String, Int or Float preference (free text, ids, sliders)", Regex("is (String|Int|Long|Float|Double)\\b").containsMatchIn(plain))
        val enumPref = store.substringAfter("fun <reified T : Enum<T>> rememberEnumPreference(")
        assertTrue(enumPref.contains("if (value != state.value) ActionLog.setting(key, value)"))
        assertEquals("the two setters and nothing else", 3, Regex("ActionLog\\.setting\\(").findAll(store).count())
    }

    @Test
    fun `ActionLog itself only takes an id, a key and a boolean or an enum`() {
        val src = code(main("utils/ActionLog.kt"))
        val publicFuns = Regex("""\n    fun ([a-zA-Z]+)\(([^)]*)\)""").findAll(src).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(setOf("tap", "setting", "correlationSuffix"), publicFuns.map { it.first }.toSet())
        for ((name, params) in publicFuns) {
            assertFalse("$name takes free text: $params", Regex("""\b(String|CharSequence|Any)\b""").containsMatchIn(params))
        }
        // The only three lines it writes.
        val lines = Regex("Timber\\.tag\\(TAG\\)\\.i\\(\"([^\"]*)\"").findAll(src).map { it.groupValues[1] }.toList()
        assertEquals(listOf("ACT a=%d %s", "ACT a=%d %s key=%s value=%b", "ACT a=%d %s key=%s value=%s"), lines)
    }

    @Test
    fun `the lines after a tap carry its number for the join window, then stop`() {
        val n = ActionLog.record(atMs = 1_000_000L)
        assertEquals(" a=$n", ActionLog.suffixAt(1_000_000L))
        assertEquals(" a=$n", ActionLog.suffixAt(1_000_000L + ActionLog.JOIN_WINDOW_MS))
        assertEquals("", ActionLog.suffixAt(1_000_000L + ActionLog.JOIN_WINDOW_MS + 1))
        val next = ActionLog.record(atMs = 2_000_000L)
        assertTrue("the action number only grows", next > n)
        assertEquals(" a=$next", ActionLog.suffixAt(2_000_500L))

        val logger = main("utils/AppLogger.kt")
        assertTrue(logger.contains("val action = if (tag == ActionLog.TAG) \"\" else ActionLog.correlationSuffix()"))
        assertTrue(logger.contains(".append(message).append(action).append('\\n')"))
    }
}
