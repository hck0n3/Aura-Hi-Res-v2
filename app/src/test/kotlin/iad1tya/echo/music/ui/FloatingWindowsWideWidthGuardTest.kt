package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.newui.AuraWindowInfo
import iad1tya.echo.music.ui.utils.isWideLayoutWindow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 874 (plan, phase 1 item 2, second half): «diálogos y hojas centrados». On a wide window every dialog and sheet
 * container is a width-capped card in the middle of the window — the window-dialog path of DefaultDialog / ListDialog was the
 * one still spanning the whole unfolded screen — and every container decides «wide» from the same reading of the window as
 * the wide shell. On a phone nothing changes. No Android SDK in the unit job: the wiring is pinned on the source text.
 */
class FloatingWindowsWideWidthGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val containers = listOf(
        "ui/component/Dialog.kt",
        "ui/component/BottomSheetMenu.kt",
        "ui/component/BottomSheetPage.kt",
        "ui/component/AuraMaterialDialogs.kt",
        "ui/newui/AuraInWindowDialog.kt",
        "ui/newui/AuraFloatingChrome.kt",
        "ui/newui/AuraWindowRecipe.kt",
    )

    /** What the decision sees: the window's own size (what `auraWindowInfo().widthDp/heightDp` holds). */
    private fun wide(widthDp: Float, heightDp: Float) =
        AuraWindowInfo(widthDp = widthDp, heightDp = heightDp, configWidthDp = widthDp.toInt(), configHeightDp = heightDp.toInt())
            .let { isWideLayoutWindow(it.widthDp, it.heightDp) }

    @Test
    fun `the owner's Z Fold7 inner screen gets cards and the cover keeps its sheets`() {
        assertFalse("cover, portrait", wide(360f, 780f))
        assertFalse("cover, large display size", wide(410f, 900f))
        assertFalse("cover on its side", wide(800f, 360f))
        assertTrue("inner screen", wide(884f, 1000f))
        assertTrue("inner screen on its side", wide(1000f, 884f))
        assertTrue("inner screen, large display size", wide(750f, 900f))
    }

    @Test
    fun `a dialog shown as its own window is capped and centred like the in-window card, and only on a wide window`() {
        val dialog = main("ui/component/Dialog.kt")
        assertEquals("DefaultDialog and ListDialog", 2, Regex(Regex.escape("val wideWindow = iad1tya.echo.music.ui.newui.rememberAuraWideWindows()")).findAll(dialog).count())
        assertTrue(dialog.contains("if (wideWindow) Modifier.widthIn(max = iad1tya.echo.music.ui.newui.AuraDialogMaxWidthDp.dp)\n                        else Modifier,"))
        assertTrue(dialog.contains("if (wideWindow) Modifier.widthIn(max = iad1tya.echo.music.ui.newui.AuraWideCompactWidthDp.dp)\n                        else Modifier,"))
        assertEquals("the phone's 24 dp margin stays on both windows", 2, Regex(Regex.escape("modifier = Modifier\n                    .padding(24.dp)\n                    .then(")).findAll(dialog).count())
        assertTrue("the window itself keeps the platform width off", dialog.contains("properties = DialogProperties(usePlatformDefaultWidth = false)"))
    }

    @Test
    fun `every other container already floats centred with a cap on a wide window`() {
        val inWindow = main("ui/newui/AuraInWindowDialog.kt")
        assertTrue(inWindow.contains(".widthIn(max = AuraDialogMaxWidthDp.dp)"))
        assertTrue(inWindow.contains(".widthIn(max = (if (fullHeight) AuraWidePageWidthDp else AuraWideCompactWidthDp).dp)"))
        assertTrue(inWindow.contains(".widthIn(max = AuraSheetMaxWidthDp.dp)"))
        assertTrue(main("ui/component/BottomSheetMenu.kt").contains(".widthIn(max = iad1tya.echo.music.ui.newui.AuraWideMenuWidthDp.dp)"))
        assertTrue(main("ui/component/BottomSheetPage.kt").contains(".widthIn(max = iad1tya.echo.music.ui.newui.AuraWidePageWidthDp.dp)"))
        val material = main("ui/component/AuraMaterialDialogs.kt")
        assertTrue("Material sheets share the cap", material.contains("sheetMaxWidth: Dp = iad1tya.echo.music.ui.newui.AuraSheetMaxWidthDp.dp,"))
        assertTrue("the glass alert dialog is a DefaultDialog", material.contains("    DefaultDialog(\n        onDismiss = onDismissRequest,"))
        val chrome = main("ui/newui/AuraFloatingChrome.kt")
        assertTrue(chrome.contains("const val AuraSheetMaxWidthDp = 560"))
        assertTrue(chrome.contains("const val AuraDialogMaxWidthDp = 520"))
    }

    @Test
    fun `the containers read the window the way the wide shell does, not Configuration`() {
        // The two phone-only height caps keep Configuration's height on purpose (next test); nothing else may read it.
        val phoneCaps = mapOf(
            "ui/newui/AuraFloatingChrome.kt" to 1,
            "ui/component/BottomSheetPage.kt" to 1,
        )
        for (path in containers) {
            val src = main(path)
            assertFalse("$path still reads Configuration's width", src.contains("configWidthDp"))
            assertEquals("$path reads Configuration's height only for a phone cap", phoneCaps[path] ?: 0,
                Regex("configHeightDp").findAll(src).count())
        }
        assertTrue(main("ui/utils/TvUi.kt").contains("val wide = isWideLayoutWindow(window.widthDp, window.heightDp)"))
        val recipe = main("ui/newui/AuraWindowRecipe.kt").substringAfter("fun rememberAuraWideWindows(): Boolean {").substringBefore("\n}")
        assertTrue(recipe.contains("val width = window.widthDp"))
        assertTrue(recipe.contains("val height = window.heightDp"))
        assertEquals(1, Regex(Regex.escape("auraWindowInfo().heightDp *")).findAll(main("ui/component/BottomSheetMenu.kt")).count())
        assertEquals(1, Regex(Regex.escape("auraWindowInfo().heightDp *")).findAll(main("ui/component/BottomSheetPage.kt")).count())
        assertTrue("the wide quick search reads the window",
            main("ui/component/BottomSheetPage.kt").contains("((if (wideSpotlight) window.heightDp else screenHeightDp.toFloat()) *"))
        assertTrue(main("ui/newui/AuraInWindowDialog.kt").contains("val wideMaxHeight = (auraWindowInfo().heightDp *"))
    }

    @Test
    fun `a phone sheet keeps the height cap it had, read from Configuration`() {
        // Configuration's height leaves the system bars out on older Android versions; the window's own size may not, so a
        // phone sheet would grow there (review of row 874). The phone caps stay on the reading they always had.
        val chrome = main("ui/newui/AuraFloatingChrome.kt").substringAfter("fun rememberAuraSheetMaxHeight(): Dp {").substringBefore("\n}")
        assertTrue(chrome.contains("val screenHeightDp = auraWindowInfo().configHeightDp"))
        val spotlight = main("ui/component/BottomSheetPage.kt").substringAfter("private fun AuraSpotlightHost(")
        assertTrue(spotlight.contains("val window = iad1tya.echo.music.ui.newui.auraWindowInfo()\n    val screenHeightDp = window.configHeightDp\n"))
        assertTrue("the phone keeps its 72 % cap", spotlight.contains("else 0.72f"))
        // And only the phone paths use them.
        listOf("ui/component/BottomSheetMenu.kt", "ui/component/BottomSheetPage.kt").forEach {
            assertTrue(it, main(it).contains("val sheetMaxHeight = rememberAuraSheetMaxHeight()"))
        }
    }
}
