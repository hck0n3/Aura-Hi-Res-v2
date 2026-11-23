package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX audit P22C (registry row 734): TalkBack read most icon-only buttons of the new UI as a bare «Botón». The
 * description was passed only as `onClickLabel` (the «double-tap to …» hint) while the icon inside had
 * `contentDescription = null`, so the node had no name. Nothing about that fails to compile, so the shape of the fix
 * is pinned as source text.
 */
class IconButtonNameGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val ui = "app/src/main/kotlin/com/music/echo/ui/newui"

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path se movió — actualiza este test", it.isFile) }.readText()

    @Test
    fun `the helper gives the node a real name, not just a click hint`() {
        val helper = src("$ui/AuraA11y.kt")
        assertTrue(helper.contains("internal fun Modifier.auraButtonName(name: String): Modifier = this.semantics { contentDescription = name }"))
    }

    @Test
    fun `icon buttons and the play button are named on their clickable path too`() {
        val prim = src("$ui/AuraPrimitives.kt")
        // AuraIconButton and AuraPlayButton: the first-tap path already sets contentDescription; the clickable path now too.
        assertEquals(2, Regex("""\.auraButtonName\(contentDescription\)""").findAll(prim).count())
        assertTrue(prim.contains("contentDescription: String = stringResource(if (isPlaying) R.string.pause else R.string.play),"))
    }

    @Test
    fun `every icon-only helper of the new UI names itself`() {
        val expected = mapOf(
            "AuraAlbumScreen.kt" to 1, // AuraDetailCircleButton
            "AuraCollectionScreens.kt" to 1, // AuraHeaderCircleButton
            "AuraStatsScreen.kt" to 1, // AuraStatsShuffleButton
            "AuraLocalPlaylistScreen.kt" to 1, // AuraDrawableIconButton
            "AuraQueue.kt" to 2, // AuraBarButton, AuraPainterIconButton
            "AuraDetailChrome.kt" to 1, // AuraDetailBackButton
            "AuraSettingsScreen.kt" to 1, // AuraBackButton
            "AuraShell.kt" to 2, // AuraMiniOutputButton, account avatar
            "AuraLibraryViewType.kt" to 1, // AuraViewTypeToggle
            "AuraLibraryTabs.kt" to 1, // Biblioteca shuffle button
        )
        for ((file, count) in expected) {
            val text = src("$ui/$file")
            assertTrue("$file: faltan nombres de botón", Regex("""\.auraButtonName\(""").findAll(text).count() >= count)
        }
        val shell = src("$ui/AuraShell.kt")
        assertTrue("celda de navegación sin texto", shell.contains("if (!showLabel) contentDescription = label"))
    }

    @Test
    fun `no hardcoded Spanish descriptions are left in the new UI`() {
        val literal = Regex("""(contentDescription|onClickLabel) = "[A-Za-zÁÉÍÓÚÑáéíóúñ]""")
        File(root, ui).listFiles { f -> f.extension == "kt" }!!.forEach { file ->
            val hit = literal.find(file.readText())
            assertTrue("${file.name}: descripción escrita a mano (${hit?.value}); va en res/values y values-es", hit == null)
        }
    }

    @Test
    fun `the new descriptions exist in English and Spanish`() {
        val keys = listOf(
            "cd_close_lyrics", "cd_lyrics_menu", "cd_minimize_player", "cd_lyrics_full_screen",
            "cd_player_full_view", "cd_player_split_view", "cd_change_period_type", "cd_view_as_grid", "cd_view_as_list",
        )
        val en = src("app/src/main/res/values/ux_a11y_strings.xml")
        val es = src("app/src/main/res/values-es/ux_a11y_strings.xml")
        for (key in keys) {
            assertTrue("$key (en)", en.contains("<string name=\"$key\">"))
            assertTrue("$key (es)", es.contains("<string name=\"$key\">"))
        }
    }
}
