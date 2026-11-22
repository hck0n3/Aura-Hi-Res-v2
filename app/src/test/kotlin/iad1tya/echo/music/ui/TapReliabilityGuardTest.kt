package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22: «verifica que cuando toque cualquier botón de la app no lo tenga que tocar varias veces para su
 * ejecución … no quiero que si toco un botón haga otra cosa».
 *
 * None of these fails to compile and none shows in a JVM test of the logic, which is how taps get lost again. What can
 * be pinned is the shape of each fix, as source text.
 */
class TapReliabilityGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the collapsed strip is not placed over the expanded sheet, so it cannot block the buttons under it`() {
        val sheet = main("ui/component/BottomSheet.kt")
        val strip = sheet.substringAfter("if (onDismiss == null || !state.isDismissed) {")
        assertTrue("un clickable deshabilitado sigue recibiendo el hit-test", strip.contains(".layout { measurable, constraints ->"))
        assertTrue(strip.contains("if (!state.isExpanded) placeable.place(0, 0)"))
    }

    @Test
    fun `the sheet drag detector is not restarted by isCollapsed in the middle of a drag`() {
        val sheet = main("ui/component/BottomSheet.kt")
        val keys = Regex("""\.pointerInput\(state, isExpandable[^{]*\{""").find(sheet)?.value
        assertTrue("falta el pointerInput del arrastre de la hoja", keys != null)
        assertFalse("isCollapsed como clave suelta reinicia el arrastre", keys!!.contains(", state.isCollapsed)"))
        assertTrue("fila 274: la barra de la cola sigue sin arrastre colapsada", keys.contains("!dragCollapsedContent && state.isCollapsed"))
    }

    @Test
    fun `a collapsed sheet sliding to a new collapsed bound still counts as collapsed`() {
        val sheet = main("ui/component/BottomSheet.kt")
        assertTrue(sheet.contains("value <= collapsedBound + 1.dp || followingCollapsedBound"))
        val remember = sheet.substringAfter("fun rememberBottomSheetState(")
        assertTrue(remember.contains("previousAnchor == collapsedAnchor && !animatable.isRunning"))
        assertTrue("la marca se apaga siempre, también al cancelar", remember.contains("finally") && remember.contains("followingCollapsedBound = false"))
    }

    @Test
    fun `menu and page panels swallow their own taps and the fading scrim does not eat the next one`() {
        val blocker = ".pointerInput(Unit) { detectTapGestures { } }"
        val menu = main("ui/component/BottomSheetMenu.kt")
        assertEquals(1, menu.split(blocker).size - 1)
        assertEquals(1, Regex("""if \(visible\) \{\s*Modifier\.pointerInput\(Unit\) \{\s*detectTapGestures \{ focusManager\.clearFocus\(\); state\.dismiss\(\) \}""").findAll(menu).count())
        val page = main("ui/component/BottomSheetPage.kt")
        assertEquals("panel de la hoja y panel de la búsqueda", 2, page.split(blocker).size - 1)
        assertEquals("los dos fondos", 2, Regex("""if \(visible\) \{\s*Modifier\.pointerInput\(Unit\) \{\s*detectTapGestures \{ focusManager\.clearFocus\(\); state\.dismiss\(\) \}""").findAll(page).count())
    }

    @Test
    fun `a swipe removes a song only when it really reached the full width`() {
        for (file in listOf("ui/newui/AuraQueue.kt", "ui/newui/AuraLocalPlaylistScreen.kt")) {
            val src = main(file)
            val state = src.substringAfter("dismissBoxState = rememberSwipeToDismissBoxState(").substringBefore("positionalThreshold")
            assertTrue("$file: sin confirmValueChange el umbral de ancho completo no se aplica", state.contains("confirmValueChange = { value ->"))
            assertTrue("$file: requireOffset protegido (crash 2026-09-23)", state.contains("runCatching { dismissBoxState.requireOffset() }"))
            assertTrue("$file: solo al ancho completo", state.replace(Regex("\\s+"), " ").contains(">= dismissRowWidthPx - 1f"))
            assertTrue("$file: el ancho se mide en la propia caja", src.contains("Modifier.onSizeChanged { dismissRowWidthPx = it.width }"))
        }
    }
}
