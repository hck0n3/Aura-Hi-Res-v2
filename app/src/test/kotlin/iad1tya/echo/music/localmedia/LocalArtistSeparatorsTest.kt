package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 880 (plan, phase 4: «separadores de artistas»): «;», « / », « feat. », « ft. », « & », «, » and « x » split a local
 * artist tag as the user chose in Ajustes ▸ Contenido ▸ Música local, and a name that is one act never splits.
 */
class LocalArtistSeparatorsTest {

    private val all = LocalArtistSplitRules(separators = LocalArtistSeparator.entries.toSet())

    private fun only(vararg separators: LocalArtistSeparator) = LocalArtistSplitRules(separators = separators.toSet())

    @Test
    fun `the default splits the usual tag separators and leaves « & » alone`() {
        val default = LocalArtistSplitRules.Default
        assertFalse(LocalArtistSeparator.AMPERSAND in default.separators)
        assertEquals(listOf("Daft Punk", "Julian Casablancas"), splitArtistNames("Daft Punk / Julian Casablancas", default))
        assertEquals(listOf("Rosalía", "J Balvin", "El Guincho"), splitArtistNames("Rosalía; J Balvin; El Guincho", default))
        assertEquals(listOf("Bad Bunny", "Bomba Estéreo"), splitArtistNames("Bad Bunny feat. Bomba Estéreo", default))
        assertEquals(listOf("Eminem", "Rihanna"), splitArtistNames("Eminem ft. Rihanna", default))
        assertEquals(listOf("Calvin Harris", "Dua Lipa"), splitArtistNames("Calvin Harris, Dua Lipa", default))
        assertEquals(listOf("Sigala", "Ella Eyre"), splitArtistNames("Sigala x Ella Eyre", default))
        assertEquals(listOf("Beyoncé & Jay-Z"), splitArtistNames("Beyoncé & Jay-Z", default))
        assertEquals("no spaces around the slash: one name", listOf("AC/DC"), splitArtistNames("AC/DC", default))
        assertEquals(listOf("Lil Nas X"), splitArtistNames("Lil Nas X", default))
    }

    @Test
    fun `« & » splits only when switched on, and never a band joined by « & the »`() {
        assertEquals(listOf("Beyoncé", "Jay-Z"), splitArtistNames("Beyoncé & Jay-Z", all))
        assertEquals(listOf("Kool & the Gang"), splitArtistNames("Kool & the Gang", all))
        assertEquals(listOf("Echo & The Bunnymen"), splitArtistNames("Echo & The Bunnymen", all))
        assertEquals(listOf("Bob Marley & the Wailers"), splitArtistNames("Bob Marley & the Wailers", all))
        assertEquals(listOf("Shakira", "Wyclef Jean", "Pitbull"), splitArtistNames("Shakira, Wyclef Jean & Pitbull", all))
    }

    @Test
    fun `one act is never split, whatever is switched on`() {
        listOf(
            "AC/DC", "Simon & Garfunkel", "Earth, Wind & Fire", "Crosby, Stills, Nash & Young", "Tyler, The Creator",
            "Mumford & Sons", "Hall & Oates", "Chloe x Halle", "Wisin & Yandel", "Peter, Paul and Mary",
        ).forEach { name ->
            assertEquals(name, listOf(name), splitArtistNames(name, all))
        }
        assertEquals("letter case and spacing of the tag are kept", listOf("simon & garfunkel"), splitArtistNames("simon & garfunkel", all))
    }

    @Test
    fun `a protected name inside a longer tag stays whole and the rest splits`() {
        assertEquals(
            listOf("Simon & Garfunkel", "Bad Bunny"),
            splitArtistNames("Simon & Garfunkel feat. Bad Bunny", all),
        )
        assertEquals(
            listOf("Earth, Wind & Fire", "The Emotions"),
            splitArtistNames("Earth, Wind & Fire; The Emotions", all),
        )
        assertEquals(
            listOf("Chloe x Halle", "Swae Lee"),
            splitArtistNames("Chloe x Halle x Swae Lee", all),
        )
        assertEquals("only whole words: not inside another word", listOf("Simon", "Garfunkelites"), splitArtistNames("Simon & Garfunkelites", all))
    }

    @Test
    fun `digits in a tag are kept as they are, with or without a protected name`() {
        listOf("Maroon 5", "U2", "blink-182", "50 Cent", "2Pac", "Sum 41", "The 1975", "10,000 Maniacs", "2147483648").forEach { name ->
            assertEquals(name, listOf(name), splitArtistNames(name, all))
            assertEquals(name, listOf(name), splitArtistNames(name, LocalArtistSplitRules.Default))
        }
        assertEquals(listOf("U2", "50 Cent"), splitArtistNames("U2 feat. 50 Cent", all))
        assertEquals(listOf("AC/DC", "2Pac"), splitArtistNames("AC/DC feat. 2Pac", all))
        assertEquals(listOf("Earth, Wind & Fire", "0"), splitArtistNames("Earth, Wind & Fire feat. 0", all))
        assertEquals(listOf("Simon & Garfunkel", "Sum 41", "AC/DC"), splitArtistNames("Simon & Garfunkel; Sum 41; AC/DC", all))
        assertEquals("a stray mask character is not a protected name", listOf("A\uE0007\uE001"), splitArtistNames("A\uE0007\uE001", all))
    }

    @Test
    fun `the user's own names are never split either`() {
        val rules = LocalArtistSplitRules(separators = LocalArtistSeparator.entries.toSet(), exceptions = setOf("Los Tigres / Del Norte", " Me & My "))
        assertEquals(listOf("Los Tigres / Del Norte"), splitArtistNames("Los Tigres / Del Norte", rules))
        assertEquals(listOf("Me & My", "Juanes"), splitArtistNames("Me & My feat. Juanes", rules))
        assertEquals(listOf("Me", "My"), splitArtistNames("Me & My", all))
    }

    @Test
    fun `each separator can be switched off on its own`() {
        assertEquals(listOf("Daft Punk / Julian Casablancas"), splitArtistNames("Daft Punk / Julian Casablancas", only(LocalArtistSeparator.SEMICOLON)))
        assertEquals(listOf("A;B"), splitArtistNames("A;B", only(LocalArtistSeparator.SLASH)))
        assertEquals(listOf("Drake feat. Rihanna"), splitArtistNames("Drake feat. Rihanna", only(LocalArtistSeparator.FT)))
        assertEquals(listOf("Drake", "Rihanna"), splitArtistNames("Drake (feat. Rihanna)", only(LocalArtistSeparator.FEAT)))
        assertEquals(listOf("Eminem ft. Dido"), splitArtistNames("Eminem ft. Dido", only(LocalArtistSeparator.FEAT)))
        assertEquals(listOf("Eminem", "Dido"), splitArtistNames("Eminem ft. Dido", only(LocalArtistSeparator.FT)))
        assertEquals(listOf("Bad Bunny, Jhay Cortez"), splitArtistNames("Bad Bunny, Jhay Cortez", only(LocalArtistSeparator.SEMICOLON)))
        assertEquals(listOf("Sigala x Ella Eyre"), splitArtistNames("Sigala x Ella Eyre", only(LocalArtistSeparator.COMMA)))
        assertEquals("nothing switched on: the tag as it is", listOf("A; B / C feat. D"), splitArtistNames("A; B / C feat. D", only()))
    }

    @Test
    fun `the same name gets the same artist id wherever it is split from`() {
        val alone = splitArtistNames("Rosalía").map(::localArtistId)
        val slash = splitArtistNames("Rosalía / The Weeknd").map(::localArtistId)
        assertEquals(alone[0], slash[0])
        assertNotEquals(slash[0], slash[1])
    }

    @Test
    fun `the saved settings become the rules, nothing saved is the default`() {
        assertEquals(LocalArtistSplitRules.Default, LocalArtistSplitRules.fromPreferences(null, null))
        assertEquals(
            setOf(LocalArtistSeparator.SEMICOLON, LocalArtistSeparator.AMPERSAND),
            LocalArtistSplitRules.fromPreferences(setOf("semicolon", "ampersand", "gone-in-a-later-version"), null).separators,
        )
        assertTrue("everything switched off stays off", LocalArtistSplitRules.fromPreferences(emptySet(), null).separators.isEmpty())
        assertEquals(setOf("Me & My"), LocalArtistSplitRules.fromPreferences(null, setOf(" Me & My ", " ")).exceptions)
        assertEquals(
            setOf("Me & My", "Los Tigres / Del Norte"),
            LocalArtistSplitRules.exceptionsFromText("Me & My\n\n  Los Tigres / Del Norte \nme & my\n"),
        )
        assertEquals("the saved keys never change", listOf("semicolon", "slash", "feat", "ft", "ampersand", "comma", "x"), LocalArtistSeparator.entries.map { it.key })
    }

    @Test
    fun `another choice changes the automatic scan stamp, the same choice does not`() {
        val config = LocalSongScanConfig()
        val volumes = mapOf("external_primary" to "v:1")
        val base = localAutoScanStamp(volumes, config)
        assertEquals(base, localAutoScanStamp(volumes, config.copy(artistSplit = LocalArtistSplitRules.fromPreferences(null, null))))
        assertNotEquals(base, localAutoScanStamp(volumes, config.copy(artistSplit = all)))
        assertNotEquals(base, localAutoScanStamp(volumes, config.copy(artistSplit = LocalArtistSplitRules(exceptions = setOf("Me & My")))))
    }

    private val root: File =
        generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")

    private fun text(path: String) = File(root, path).readText()

    @Test
    fun `the setting reaches every scan and is reachable from Ajustes`() {
        val keys = text("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertTrue(keys.contains("val LocalArtistSeparatorsKey = stringSetPreferencesKey(\"local_artist_separators\")"))
        assertTrue(keys.contains("val LocalArtistNameExceptionsKey = stringSetPreferencesKey(\"local_artist_name_exceptions\")"))
        val split = "artistSplit = LocalArtistSplitRules.fromPreferences("
        assertTrue("the scan sheet", text("app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt").contains(split))
        val auto = text("app/src/main/kotlin/com/music/echo/localmedia/LocalLibraryAutoScan.kt")
        assertTrue("the automatic scan", auto.contains(split))
        assertTrue("a change of the setting asks for a scan", auto.contains(".map { prefs -> prefs[LocalArtistSeparatorsKey] to prefs[LocalArtistNameExceptionsKey] }"))
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        assertTrue(scanner.contains("splitArtistNames(artistTag.ifBlank { unknownArtist }, rules)"))
        assertTrue(scanner.contains("albumArtistTag?.let { splitArtistNames(it, rules) }"))
        val settings = text("app/src/main/kotlin/com/music/echo/ui/screens/settings/ContentSettings.kt")
        assertTrue(settings.contains("title = { Text(stringResource(R.string.local_artist_separators)) },"))
        assertTrue(settings.contains("title = { Text(stringResource(R.string.local_artist_exceptions)) },"))
        assertTrue(settings.contains("onArtistNameExceptionsChange(LocalArtistSplitRules.exceptionsFromText(exceptionsText))"))
        val index = text("app/src/main/kotlin/com/music/echo/ui/screens/settings/SearchableSettings.kt")
        assertTrue(index.contains("Triple(stringResource(R.string.local_artist_separators), \"Contenido\", \"settings/content\"),"))
        assertTrue(index.contains("Triple(stringResource(R.string.local_artist_exceptions), \"Contenido\", \"settings/content\"),"))
        for (values in listOf("values", "values-es")) {
            val strings = text("app/src/main/res/$values/echo_strings.xml")
            listOf("local_music_settings", "local_artist_separators", "local_artist_separators_desc", "local_artist_separators_note",
                "local_artist_exceptions", "local_artist_exceptions_desc", "local_artist_exceptions_hint", "local_artist_exceptions_builtin",
            ).forEach { name -> assertTrue("$values: $name", strings.contains("name=\"$name\"")) }
        }
    }
}
