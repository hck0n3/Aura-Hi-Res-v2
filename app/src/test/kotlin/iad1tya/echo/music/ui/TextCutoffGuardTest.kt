package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 870 (plan, phase 1.3 «nada cortado»): a text limited to one or two lines must say how it ends when it
 * does not fit. Compose's default overflow is Clip and a View's TextView has no ellipsis by default, so a label with
 * `maxLines = 1` and nothing else is cut mid-letter with large font or on the Fold cover screen.
 *
 * Source-text guard (no Android SDK here): every `maxLines = 1` / `maxLines = 2` under `com/music/echo` must sit in a
 * call or block that also sets `overflow` (Compose) or `ellipsize` (View), unless the file is in [ALLOWED] with its
 * reason. Comments and string contents are masked before scanning, so commented-out code never counts.
 */
class TextCutoffGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }
    private val srcRoot: File get() = File(root, "app/src/main/kotlin/com/music/echo")

    private fun main(path: String) =
        File(srcRoot, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private companion object {
        /** File (relative to com/music/echo) → (most hits it may keep, why). */
        val ALLOWED: Map<String, Pair<Int, String>> = mapOf(
            "MainActivity.kt" to (1 to "window-measurement file owned by the phase 1.1 package; reviewed in wave 2"),
            "ui/newui/AuraHomeScreen.kt" to (3 to "window-measurement file owned by the phase 1.1 package; reviewed in wave 2"),
            "ui/newui/AuraTrackColumns.kt" to (1 to "window-measurement file owned by the phase 1.1 package; reviewed in wave 2"),
            "ui/player/Thumbnail.kt" to (1 to "window-measurement file owned by the phase 1.1 package; reviewed in wave 2"),
            "ui/screens/equalizer/axion/AxionEqScreen.kt" to
                (3 to "equalizer screen: AGENTS.md rule 6, only touched when the owner asks"),
            "ui/screens/settings/LastFMSettingsScreen.kt" to
                (1 to "TextFieldDialog(maxLines = 1): a one-line token input field, not a label"),
        )

        val MAX_LINES = Regex("""\bmaxLines\s*=\s*([12])\b""")
    }

    /** Replaces comments and string contents with spaces (same length) so brackets and keywords are code only. */
    private fun mask(src: String): String {
        val out = StringBuilder(src)
        // Each frame: 'C' code (int = brace depth inside a ${} template, -1 for top level), 'S' string, 'R' raw string.
        val modes = ArrayDeque<Pair<Char, Int>>().apply { addLast('C' to -1) }
        var i = 0
        fun blank(from: Int, to: Int) { for (k in from until to) if (out[k] != '\n') out[k] = ' ' }
        while (i < src.length) {
            val (mode, depth) = modes.last()
            val c = src[i]
            when (mode) {
                'C' -> when {
                    src.startsWith("//", i) -> {
                        val end = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                        blank(i, end); i = end
                    }
                    src.startsWith("/*", i) -> {
                        var nest = 0
                        var k = i
                        while (k < src.length) {
                            if (src.startsWith("/*", k)) { nest++; k += 2; continue }
                            if (src.startsWith("*/", k)) { nest--; k += 2; if (nest == 0) break; continue }
                            k++
                        }
                        blank(i, k); i = k
                    }
                    src.startsWith("\"\"\"", i) -> { modes.addLast('R' to 0); i += 3 }
                    c == '"' -> { modes.addLast('S' to 0); i++ }
                    c == '\'' -> {
                        var k = i + 1
                        while (k < src.length && src[k] != '\'') k += if (src[k] == '\\') 2 else 1
                        blank(i + 1, k); i = k + 1
                    }
                    c == '{' && depth >= 0 -> { modes.removeLast(); modes.addLast('C' to depth + 1); i++ }
                    c == '}' && depth == 0 -> { modes.removeLast(); i++ }
                    c == '}' && depth > 0 -> { modes.removeLast(); modes.addLast('C' to depth - 1); i++ }
                    else -> i++
                }
                else -> {
                    val raw = mode == 'R'
                    when {
                        !raw && c == '\\' -> { blank(i, minOf(i + 2, src.length)); i += 2 }
                        raw && src.startsWith("\"\"\"", i) -> { modes.removeLast(); i += 3 }
                        !raw && c == '"' -> { modes.removeLast(); i++ }
                        src.startsWith("\${", i) -> { modes.addLast('C' to 0); i += 2 }
                        else -> { if (c != '\n') out[i] = ' '; i++ }
                    }
                }
            }
        }
        return out.toString()
    }

    /** The innermost `( … )` or `{ … }` around [index] in the masked text. */
    private fun enclosing(masked: String, index: Int): String {
        var depth = 0
        var start = index
        while (start > 0) {
            start--
            when (masked[start]) {
                ')', '}' -> depth++
                '(', '{' -> if (depth == 0) break else depth--
            }
        }
        depth = 0
        var end = start
        while (end < masked.length) {
            when (masked[end]) {
                '(', '{' -> depth++
                ')', '}' -> { depth--; if (depth == 0) break }
            }
            end++
        }
        return masked.substring(start, minOf(end + 1, masked.length))
    }

    private fun unendedLimits(): Map<String, List<Int>> {
        val hits = sortedMapOf<String, MutableList<Int>>()
        srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val masked = mask(file.readText())
            for (m in MAX_LINES.findAll(masked)) {
                val block = enclosing(masked, m.range.first)
                if ("overflow" in block || "ellipsize" in block) continue
                val line = masked.substring(0, m.range.first).count { it == '\n' } + 1
                hits.getOrPut(file.relativeTo(srcRoot).invariantSeparatorsPath) { mutableListOf() }.add(line)
            }
        }
        return hits
    }

    @Test
    fun `every one- or two-line text says how it ends when it does not fit`() {
        val hits = unendedLimits()
        val unexpected = hits.filterKeys { it !in ALLOWED }
        assertTrue(
            "maxLines = 1/2 sin overflow/ellipsize (añade TextOverflow.Ellipsis, o 2 líneas + Ellipsis si es un botón): $unexpected",
            unexpected.isEmpty(),
        )
        for ((path, allowance) in ALLOWED) {
            val count = hits[path]?.size ?: 0
            assertTrue("$path: $count > ${allowance.first} (${allowance.second})", count <= allowance.first)
        }
    }

    @Test
    fun `every allow-listed file still exists`() {
        for (path in ALLOWED.keys) assertTrue("$path se movió: actualiza la lista", File(srcRoot, path).isFile)
    }

    @Test
    fun `the scanner sees through comments, strings and templates`() {
        val src = """
            Text(text = "a (b", maxLines = 1)
            // Text(maxLines = 1)
            /* Text(maxLines = 1) */
            Text(text = "${'$'}{if (x) "(" else ")"}", maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextView(this).apply {
                maxLines = 2
                ellipsize = END
            }
        """.trimIndent()
        val masked = mask(src)
        assertEquals("the masked text keeps its length", src.length, masked.length)
        val found = MAX_LINES.findAll(masked).map { it.range.first }.toList()
        assertEquals("comments are not code", 3, found.size)
        val ended = found.map { enclosing(masked, it).let { b -> "overflow" in b || "ellipsize" in b } }
        assertEquals(listOf(false, true, true), ended)
    }

    @Test
    fun `button labels wrap to a second line before the ellipsis`() {
        for ((path, anchor) in listOf(
            "echomusic/AudioDeviceBottomSheet.kt" to "Text(text = doneText, style = AuraType.Chip, color = AuraPalette.OnGround, maxLines = 2, overflow = TextOverflow.Ellipsis)",
            "ui/newui/AuraQueue.kt" to "text = stringResource(R.string.start_an_radio),",
            "ui/newui/AuraLyricsScreen.kt" to "text = stringResource(R.string.reset),",
        )) {
            val call = main(path).substringAfter(anchor).take(260)
            assertTrue("$path: el botón admite dos líneas", anchor.contains("maxLines = 2") || call.contains("maxLines = 2,"))
        }
    }
}
