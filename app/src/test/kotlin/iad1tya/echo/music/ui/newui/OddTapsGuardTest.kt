package iad1tya.echo.music.ui.newui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 825 and 826 (owner 2026-11-22, «toques raros» left unfinished): the bottom bar's tab edges, the
 * quick search keyboard and the TV remote.
 */
class OddTapsGuardTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root")
    }

    private fun main(path: String): String {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path se movió — actualiza este test", f.isFile)
        return f.readText()
    }

    private val shell by lazy { main("ui/newui/AuraShell.kt") }

    private fun navItem(): String {
        val item = shell.substringAfter("private fun RowScope.AuraNavItem(")
        return item.substring(0, item.indexOf("\n}\n"))
    }

    // ── 825 · tab edges ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a tab cell takes its taps before the press scale and the rounded clip`() {
        val item = navItem()
        val clickable = item.indexOf(".clickable(")
        val scale = item.indexOf(".graphicsLayer {\n                scaleX = pressScale")
        val clip = item.indexOf(".clip(AuraShapes.Highlight)")
        assertTrue(clickable > 0 && scale > 0 && clip > 0)
        assertTrue("the press scale shrinks the clickable's bounds under the finger", clickable < scale)
        assertTrue("the 14 dp corners are not hit-testable under the clip", clickable < clip)
        // The ripple is still drawn inside the clip, by the cell's own interaction source.
        assertTrue(item.contains("indication = null,"))
        assertTrue(item.indexOf(".indication(interactionSource, LocalIndication.current)") > clip)
        // The TV ring stays above the clickable, so it still observes its focus.
        assertTrue(item.indexOf(".tvFocusable(") in 0 until clickable)
        // The emulator smoke test finds the selected tab through these (test_selected_tab_is_the_cell_above_its_label).
        assertTrue(item.contains("role = Role.Tab"))
        assertTrue(item.contains("this.selected = selected"))
        assertTrue(item.contains("text = label"))
    }

    @Test
    fun `a tap on the bar that no cell answered goes to the nearest cell and never falls through`() {
        val bar = shell.substringAfter("fun AuraNavigationBar(").substringBefore("private fun AuraNavIndicator(")
        val router = bar.substringAfter(".height(AuraNavBarHeight)").substringBefore(".then(")
        assertTrue(router.contains(".pointerInput(interactiveGlass) {"))
        assertTrue(router.contains("awaitFirstDown(requireUnconsumed = false)"))
        assertTrue("a tap a cell already handled must not fire twice", router.contains("if (down.isConsumed || up.isConsumed) return@awaitEachGesture"))
        assertTrue("a drag across the bar is not a tap", router.contains("viewConfiguration.touchSlop"))
        assertTrue("the capsule's side margin is outside the row", router.contains("AuraFloatingInset.toPx()"))
        assertTrue(router.contains("auraNavCellAt(up.position.x - rowStart, cells)"))
        assertTrue(router.contains("latestOnItemClick(screen, latestIsSelected(screen))"))
        assertTrue(bar.contains("val latestOnItemClick by rememberUpdatedState(onItemClick)"))
    }

    @Test
    fun `the nearest cell of a tap`() {
        val cells = listOf(0f..100f, 100f..200f, 200f..300f)
        assertEquals(0, auraNavCellAt(50f, cells))
        assertEquals(1, auraNavCellAt(150f, cells))
        assertEquals(2, auraNavCellAt(299f, cells))
        assertEquals("left margin → first cell", 0, auraNavCellAt(-30f, cells))
        assertEquals("right margin → last cell", 2, auraNavCellAt(340f, cells))
        assertEquals(-1, auraNavCellAt(10f, emptyList()))
        assertFalse(auraNavCellAt(250f, cells) == 1)
    }

    // ── 825 · quick search keyboard ──────────────────────────────────────────────────────────────────────────────────

    private val quick by lazy { main("ui/newui/AuraPlayerQuickSearch.kt") }

    @Test
    fun `the Search key runs the search and hides the keyboard`() {
        val bar = main("ui/newui/AuraSearchScreen.kt").substringAfter("internal fun AuraSearchInputBar(")
        assertTrue(bar.contains("keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),"))
        assertTrue(bar.contains("keyboardActions = KeyboardActions(onSearch = { ActionLog.tap(ActionId.SEARCH_SUBMIT); onSubmit() }),"))
        val submit = quick.substringAfter("onSubmit = {").substringBefore("onClear = {")
        assertTrue(submit.contains("keyboardController?.hide()"))
        assertTrue(submit.contains("onlineResultsViewModel.search(query.text)"))
    }

    @Test
    fun `back hides the keyboard before it closes the quick search`() {
        val close = quick.indexOf("BackHandler(onBack = { popBrowse() })")
        val ime = quick.indexOf("BackHandler(enabled = imeVisible) { keyboardController?.hide() }")
        assertTrue(close > 0)
        assertTrue("composed after the close handler, so it wins while the keyboard is up", ime > close)
        assertTrue(quick.contains("val imeVisible = WindowInsets.isImeVisible"))
    }

    @Test
    fun `coming back to typed results does not bring the keyboard up over them`() {
        val effect = quick.substringAfter("LaunchedEffect(browseTop) {").substringBefore("fun popBrowse()")
        assertTrue(effect.contains("if (browseTop is QuickSearchBrowse.Search && query.text.isEmpty()) {"))
        assertTrue(effect.contains("focusRequester.requestFocus()"))
        // The other ways the keyboard gets out of the way of the results are still there.
        assertTrue(quick.contains("val hideKeyboardOnTouch = Modifier.pointerInput(keyboardController) {"))
        assertTrue(quick.contains(".collect { if (lazyListState.isScrollInProgress) keyboardController?.hide() }"))
    }
}
