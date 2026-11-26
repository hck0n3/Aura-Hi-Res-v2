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

    /** The flavour source sets (Cast lives in `gms`) hold buttons too (row 824). */
    private val flavourDirs = listOf("gms", "foss").map { File(root, "app/src/$it/kotlin/com/music/echo") }

    private val sources: List<File> by lazy {
        (listOf(mainDir) + flavourDirs.filter { it.isDirectory })
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
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

    // ── Row 824 (J1, owner «sin excepciones»): every screen file with a button logs it ──

    private val classic = "classic screen: only the `classic =` side of NewUiGate, never composed while SINGLE_UI is true"
    private val classicChild = "only called from a classic screen (or the classic shell) that SINGLE_UI never composes"
    private val noCaller = "no call site: nothing in the app composes it"
    private val forwarder = "generic component: its click is the caller's own lambda, which carries the tap where the caller writes it"

    /** Files of the app's own screens with a user handler and, for the reason given, no tap of their own. */
    private val noTapAllowlist: Map<String, String> = mapOf(
        "ui/menu/OldPlayerMenu.kt" to classic,
        "ui/player/Queue.kt" to classic,
        "ui/screens/AlbumScreen.kt" to classic,
        "ui/screens/StatsScreen.kt" to classic,
        "ui/screens/artist/ArtistAlbumsGridScreen.kt" to classic,
        "ui/screens/artist/ArtistItemsScreen.kt" to classic,
        "ui/screens/artist/ArtistScreen.kt" to classic,
        "ui/screens/library/FavoriteAlbumsScreen.kt" to classic,
        "ui/screens/library/LibraryScreen.kt" to classic,
        "ui/screens/migration/MigrationAppleScreen.kt" to classic,
        "ui/screens/migration/MigrationScreen.kt" to classic,
        "ui/screens/migration/MigrationTidalScreen.kt" to classic,
        "ui/screens/playlist/AutoPlaylistScreen.kt" to classic,
        "ui/screens/playlist/CachePlaylistScreen.kt" to classic,
        "ui/screens/playlist/LocalPlaylistScreen.kt" to classic,
        "ui/screens/playlist/OnlinePlaylistScreen.kt" to classic,
        "ui/screens/playlist/TopPlaylistScreen.kt" to classic,
        "ui/screens/search/OnlineSearchResult.kt" to classic,
        "ui/screens/search/SearchScreen.kt" to classic,
        "ui/screens/settings/SettingsScreen.kt" to classic,
        "ui/screens/library/LibraryArtistsScreen.kt" to classicChild,
        "ui/screens/library/LibraryMixScreen.kt" to classicChild,
        "ui/screens/library/LibraryPlaylistsScreen.kt" to classicChild,
        "ui/screens/library/LibrarySongsScreen.kt" to classicChild,
        "ui/screens/search/LocalSearchScreen.kt" to classicChild,
        "ui/screens/search/OnlineSearchScreen.kt" to classicChild,
        "ui/screens/search/suggestions/TabNewsSuggestion.kt" to classicChild,
        "ui/component/AutoPlaylistButton.kt" to classicChild,
        "ui/component/ExpandableText.kt" to classicChild,
        "ui/component/FloatingNavigationToolbar.kt" to "the classic bottom bar: the else branch of newUiShell in MainActivity",
        "ui/component/SortHeader.kt" to noCaller,
        "ui/component/RandomizeGridItem.kt" to noCaller,
        "ui/player/MiniPlayer.kt" to noCaller,
        "ui/screens/ChartsScreen.kt" to noCaller,
        "ui/screens/LoginScreen.kt" to "no call site for the composable: the login route opens YouTubeLoginActivity",
        "ui/screens/SpotifyLoginScreen.kt" to "no call site for the composable: spotify_login opens SpotifyLoginActivity",
        "ui/screens/settings/AccountSettingsScreen.kt" to noCaller,
        "ui/component/EchoMusicLyrics.kt" to forwarder,
        "ui/component/MetroLyrics.kt" to forwarder,
        "ui/component/IconButton.kt" to forwarder,
        "ui/component/Material3SettingsGroup.kt" to forwarder,
        "ui/component/Menu.kt" to forwarder,
        "ui/component/NavigationTitle.kt" to forwarder,
        "ui/component/NewMenuComponents.kt" to forwarder,
        "ui/newui/AuraDetailChrome.kt" to forwarder,
        "ui/newui/AuraPrimitives.kt" to forwarder,
        "ui/newui/AuraSearchWide.kt" to forwarder,
        "ui/screens/settings/HiddenItemsScreen.kt" to "HiddenItemsTest forbids any «Log.» in this screen; excluded until the owner relaxes that guard",
    )

    /** A non-empty click / long-click lambda, or a clickable modifier. Imports and comments do not count. */
    private val userHandler = Regex("""\b(?:onClick|onLongClick)\s*=\s*\{(?!\s*\})|\.(?:clickable|combinedClickable)\b(?!\s*\{\s*\})""")

    private fun screenCode(file: File): String =
        code(file.readText()).lines().filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")

    @Test
    fun `every screen file with a button logs a tap, or says in the allowlist why not`() {
        val screenFiles = listOf("ui", "echomusic", "legal").flatMap { File(mainDir, it).walkTopDown().toList() } +
            File(mainDir, "MainActivity.kt") +
            flavourDirs.flatMap { File(it, "ui").walkTopDown().toList() }
        val files = screenFiles.filter { it.isFile && it.extension == "kt" }
        assertTrue("the screens are where they were", files.size > 150)
        val missing = files.filter { f ->
            val src = screenCode(f)
            userHandler.containsMatchIn(src) && !src.contains("ActionLog.tap(") &&
                f.relativeTo(mainDir).invariantSeparatorsPath !in noTapAllowlist
        }.map { it.relativeTo(root).invariantSeparatorsPath }
        assertTrue("buttons without an ActionLog.tap (add one, or an allowlist entry with its reason): $missing", missing.isEmpty())
    }

    @Test
    fun `the allowlist stays honest`() {
        for ((path, reason) in noTapAllowlist) {
            val f = File(mainDir, path)
            assertTrue("$path is gone: drop it from the allowlist", f.isFile)
            assertTrue("$path needs a reason", reason.isNotBlank())
            val src = screenCode(f)
            assertTrue("$path has no button any more: drop it from the allowlist", userHandler.containsMatchIn(src))
            assertFalse("$path logs taps now: drop it from the allowlist", src.contains("ActionLog.tap("))
        }
        // The classic entries are only true while there is one interface.
        assertTrue(main("ui/newui/NewUiAvailability.kt").contains("const val SINGLE_UI: Boolean = true"))
    }

    @Test
    fun `the bottom bar logs one code per tab`() {
        val shell = code(main("ui/newui/AuraShell.kt"))
        assertEquals("both bars", 2, Regex("""onClick = \{ auraNavTap\(screen\); onItemClick\(screen, selected\) \}""").findAll(shell).count())
        for (tab in listOf("Home", "Novedades", "Library", "Search", "ListenTogether")) {
            assertTrue("tab $tab", Regex("""Screens\.$tab -> ActionLog\.tap\(ActionId\.NAV_[A-Z]+\)""").containsMatchIn(shell))
        }
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
