package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.newui.AURA_SETTINGS_PANE_CHILDREN
import iad1tya.echo.music.ui.newui.AURA_SETTINGS_PANE_ROUTES
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 873 (plan, phase 1 item 2): Ajustes in two panes on a wide window, with the SAME routes and screens as a
 * phone. There is no Android SDK in the unit job, so the wiring is pinned on the source text:
 *  - every page of the two-pane list is registered through `auraSettingsPane` (or it would open full width, without the
 *    index) and every index row that opens a page says which (or it would never be highlighted);
 *  - a phone keeps exactly the screen and the navigation it had.
 */
class SettingsTwoPaneGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private val nav by lazy { main("ui/screens/NavigationBuilder.kt") }
    private val screen by lazy { main("ui/newui/AuraSettingsScreen.kt") }
    private val frame by lazy { main("ui/newui/AuraSettingsTwoPane.kt") }

    /** The body of the registration of [route] in NavigationBuilder (up to the next registration). */
    private fun registration(route: String): String {
        val head = listOf("composable(\"$route\")", "composable(route = \"$route\")", "route = \"$route")
            .firstOrNull { nav.contains(it) } ?: error("$route no está registrada")
        return nav.substringAfter(head).substringBefore("\n    composable(")
    }

    @Test
    fun `every page of the two-pane Ajustes and the index itself keep their registration and wear the frame`() {
        val routes = AURA_SETTINGS_PANE_ROUTES + AURA_SETTINGS_PANE_CHILDREN.keys + "settings"
        for (route in routes) {
            val key = if (route == "settings/appearance/liquidglass") "LIQUID_GLASS_ROUTE" else route
            val body = if (key == "LIQUID_GLASS_ROUTE") nav.substringAfter("composable(LIQUID_GLASS_ROUTE) {").substringBefore("\n    composable(")
            else registration(key)
            assertTrue("$route lleva el marco de dos paneles", body.contains("AuraSettingsPaneFrame(navController, "))
        }
        assertEquals("one frame per route, no more", routes.size, Regex("""AuraSettingsPaneFrame\(navController, """).findAll(nav).count())
        assertTrue(main("ui/screens/settings/SearchableSettings.kt").contains("const val LIQUID_GLASS_ROUTE = \"settings/appearance/liquidglass\""))
        // The registrations other guards pin are untouched (rows 494, 811, 813, 805, 871…).
        listOf("settings/content", "settings/accounts", "settings/backup_restore", "settings/content/hidden").forEach {
            assertTrue(it, nav.contains("composable(\"$it\") {"))
        }
        assertTrue(nav.contains("composable(route = \"settings/device_sync/held\")"))
    }

    @Test
    fun `the pages that keep their own full screen are not panes`() {
        for (own in listOf("settings/equalizer", "settings/youtube_decryption", "settings/terms", "settings/privacy_policy", "settings/changelog")) {
            assertFalse("$own keeps its own full screen", registration(own).contains("AuraSettingsPaneFrame"))
        }
        assertFalse("Spotify import has a welcome mode", registration("settings/spotify_import?onboarding={onboarding}&link={link}").contains("AuraSettingsPaneFrame"))
        // ytm_sync is a pane, but never in its welcome mode.
        assertTrue(frame.contains("arguments?.getBoolean(\"onboarding\") == true"))
        assertTrue(frame.contains("|| welcome) {"))
    }

    @Test
    fun `a page the welcome opens is a step of the welcome, never a pane with the index`() {
        // «Conecta tu música ▸ Restaurar copia» opens a plain settings/backup_restore (no onboarding argument).
        assertTrue(main("ui/screens/welcome/WelcomeConnectScreen.kt").contains("open(\"settings/backup_restore\")"))
        val step = frame.substringAfter("private fun NavController.isWelcomeStep(entry: NavBackStackEntry): Boolean {").substringBefore("\n}")
        assertTrue(step.contains("if (entry.isOnboarding()) return true"))
        assertTrue("the entry right below it is the welcome, as in MainActivity's inOnboarding",
            step.contains("return index > 0 && stack[index - 1].destination.route == WELCOME_CONNECT_ROUTE"))
        assertTrue(main("MainActivity.kt").contains("navController.previousBackStackEntry?.destination?.route ==\n                        iad1tya.echo.music.ui.screens.welcome.WELCOME_CONNECT_ROUTE"))
        // The frame and the transitions use the same test.
        assertTrue(frame.contains("val welcome = remember(entry) { navController.isWelcomeStep(entry) }"))
        assertTrue(frame.contains("fromOnboarding = navController.isWelcomeStep(from),"))
        assertTrue(frame.contains("toOnboarding = navController.isWelcomeStep(to),"))
    }

    @Test
    fun `each index row that opens a page navigates there itself and is marked with that same page`() {
        val marked = Regex("""navController\.navigate\("([^"]+)"\)\s*\}\s*\.opensPage\("([^"]+)"\)""").findAll(screen).toList()
        marked.forEach { assertEquals("the row and its mark must name the same page", it.groupValues[1], it.groupValues[2]) }
        assertEquals("every page of the list has its row", AURA_SETTINGS_PANE_ROUTES.toSet(), marked.map { it.groupValues[2] }.toSet())
        assertEquals("no other .opensPage than those", marked.size, Regex("""\.opensPage\("""").findAll(screen).count())
        // The full screens of their own keep their plain navigation.
        assertTrue(screen.contains("AuraSettingsEntry(migrateText) { navController.navigate(\"migration\") },"))
        assertTrue(screen.contains("entries = listOf(AuraSettingsEntry(statsText) { navController.navigate(\"stats\") }),"))
        assertTrue(screen.contains("navController.navigateAsTab(Screens.ListenTogether.route)"))
    }

    @Test
    fun `a pick replaces the page on the right and is a no-op on a phone`() {
        val replace = screen.substringAfter("fun replaceShownPage(target: String) {").substringBefore("\n    }")
        assertTrue("off a two-pane page nothing is popped", replace.contains("if (shownPage == null || auraSettingsPaneSelectionOf(target) == null) return"))
        assertTrue(replace.contains("if (!navController.popBackStack(AURA_SETTINGS_ROUTE, inclusive = false)) navController.popBackStack()"))
        assertTrue("shownPage is null on a phone", screen.contains("val shownPage = if (twoPanes) auraSettingsRouteBase(hostRoute)?.takeIf { it != AURA_SETTINGS_ROUTE } else null"))
        val opens = screen.substringAfter("fun AuraSettingsEntry.opensPage(route: String): AuraSettingsEntry {").substringBefore("\n    }")
        assertTrue("the entry's own lambda still navigates", opens.contains("open()"))
        // The search result replaces too, then jumps to its row as before (P22D).
        val result = screen.substringAfter("ActionLog.tap(ActionId.SETIDX_SEARCH_RESULT)").substringBefore("},")
        assertTrue(result.contains("replaceShownPage(route)\n                                SettingsSearchJump.navigate(navController, view, route, title)"))
    }

    @Test
    fun `with two panes every copy of the index keeps the scroll, stored at the end of a gesture, and a phone keeps its own`() {
        assertTrue("only with two panes", screen.contains("val sharedScroll = sharedIndexState?.takeIf { twoPanes }"))
        // Plain remember with two panes: the entry's own saveable scroll would win on the way back (review of row 873).
        assertTrue("two panes start from the shared place",
            screen.contains("remember(sharedScroll) { ScrollState(sharedScroll.get<Int>(INDEX_KEY_SCROLL) ?: 0) }"))
        assertTrue("a phone keeps its own saveable scroll", screen.contains("} else {\n        rememberScrollState()\n    }"))
        assertFalse(screen.contains("rememberScrollState(initial = sharedScroll"))
        assertTrue("stored when the scroll stops, not per frame",
            screen.contains("snapshotFlow { scrollState.isScrollInProgress }.collect { moving ->\n                if (!moving) sharedScroll[INDEX_KEY_SCROLL] = scrollState.value"))
        assertFalse(screen.contains("snapshotFlow { scrollState.value }"))
        assertTrue(screen.contains("private const val INDEX_KEY_SCROLL = \"aura_settings_index_scroll\""))
    }

    @Test
    fun `scrolling the two-pane index does not collapse the top bar of the page beside it`() {
        // The index and the page share the NavHost's nested-scroll parent, which feeds the shell's app-bar behavior that the
        // pages' top bars use (AboutScreen, LogsScreen, FeedbackScreen, SubscriptionCleanupScreen…).
        val activity = main("MainActivity.kt")
        assertTrue(activity.contains(".nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        val canScroll = activity.substringAfter("val topAppBarScrollBehavior = appBarScrollBehavior(").substringBefore("\n                )")
        assertTrue(canScroll.contains("!inSearchScreen &&"))
        assertTrue(canScroll.contains("(playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed) &&"))
        assertTrue(canScroll.contains("!iad1tya.echo.music.ui.newui.AuraSettingsPanes.indexScrolling()"))
        // Only the composed two-pane index registers its probe, and clears only its own.
        val probe = screen.substringAfter("    if (twoPanes) {\n        DisposableEffect(scrollState) {").substringBefore("\n    }\n")
        assertTrue(probe.contains("val probe: () -> Boolean = { scrollState.isScrollInProgress }"))
        assertTrue(probe.contains("AuraSettingsPanes.indexScrolling = probe"))
        assertTrue(probe.contains("if (AuraSettingsPanes.indexScrolling === probe) AuraSettingsPanes.indexScrolling = AuraSettingsPanes.NotScrolling"))
        val panes = main("ui/newui/AuraSettingsPanes.kt")
        assertTrue(panes.contains("var indexScrolling: () -> Boolean = NotScrolling"))
        assertTrue(panes.contains("val NotScrolling: () -> Boolean = { false }"))
    }

    @Test
    fun `Suscripcion Pro opened beside a page covers the window without throwing the page away`() {
        // A phone (and `settings` itself) keeps the early return: the same full screen as always.
        assertTrue(screen.contains("if (showSubscribe && page == null) {\n        SubscriptionEntryScreen("))
        // Beside a page it is drawn over the two panes, which stay composed.
        val over = screen.substringAfter("        if (showSubscribe) {\n            Box(").substringBefore("\n        }\n    }\n}")
        assertTrue(over.contains(".fillMaxSize()"))
        assertTrue("opaque", over.contains(".auraGroundFill()"))
        assertTrue("takes every touch", over.contains(".pointerInput(Unit) {}"))
        assertTrue("back closes it before any back handler of the page", over.contains("BackHandler { showSubscribe = false }"))
        assertTrue(over.contains("onActivated = { showSubscribe = false },"))
        assertTrue(over.contains("onBack = { showSubscribe = false },"))
        assertTrue("the panes underneath are hidden from TalkBack",
            screen.contains(".then(if (showSubscribe) Modifier.clearAndSetSemantics {} else Modifier)"))
        assertEquals("still one subscription screen per path", 2, Regex("""SubscriptionEntryScreen\(""").findAll(screen).count())
    }

    @Test
    fun `a phone draws the index exactly as before`() {
        val phone = screen.substringAfter("if (!twoPanes) {\n        Box(").substringBefore("return\n    }")
        assertTrue(phone.contains(".auraScreenBackground(bloom, intensity = 0.32f)"))
        assertTrue(phone.contains("indexColumn(Modifier.fillMaxSize())"))
        assertTrue(screen.contains("else WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom"))
        assertTrue(screen.contains("else LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)"))
        // A page on a phone (or with the classic interface) is the page itself, nothing around it.
        assertTrue(frame.contains("if (auraSettingsPaneSelectionOf(route) == null || !newUi || !twoPanes || welcome) {\n        page()"))
        // A phone keeps the app's slide: only a move between two-pane Ajustes routes skips it, in the four transitions.
        val activity = main("MainActivity.kt")
        val swap = "if (iad1tya.echo.music.ui.newui.auraSettingsInstantSwap(navController, initialState, targetState)) {"
        assertEquals(4, Regex(Regex.escape(swap)).findAll(activity).count())
        assertEquals(2, Regex("""\} else AuraNavMotion\.enter\(""").findAll(activity).count())
        assertEquals(2, Regex("""\} else AuraNavMotion\.exit\(""").findAll(activity).count())
        assertTrue(frame.contains("twoPanes = AuraSettingsPanes.twoPanesNow,"))
        assertTrue(frame.contains("SideEffect { AuraSettingsPanes.twoPanesNow = newUi && twoPanes }"))
    }

    @Test
    fun `the two-pane decision reads the window layer, is event-driven and logs nothing`() {
        val decide = frame.substringAfter("fun rememberAuraSettingsTwoPanes(): Boolean {").substringBefore("\n}")
        assertTrue(decide.contains("auraWindowInfo().heightDp"))
        assertTrue(decide.contains("auraPaneWidthDp()"))
        assertTrue(decide.contains("remember(paneWidthDp, heightDp) { auraSettingsUsesTwoPanes(paneWidthDp, heightDp) }"))
        for (src in listOf(frame, screen)) {
            assertFalse(src.contains("withFrameNanos"))
            assertFalse(src.contains("while (true)"))
            assertFalse(src.contains("Timber."))
            assertFalse(src.contains("AppLogger"))
        }
        // The page is movable content: folding or unfolding keeps its scroll and its state.
        assertTrue(frame.contains("val page = remember { movableContentOf { currentContent() } }"))
    }

    @Test
    fun `the selected row is highlighted and announced, and the right pane is laid out for its own width`() {
        assertTrue(screen.contains(".semantics { this.selected = true }"))
        assertEquals("index row and sub row", 2, Regex("""\.auraSettingsSelected\(selected\)""").findAll(screen).count())
        assertTrue(screen.contains("CompositionLocalProvider(LocalContentPaneWidth provides (paneWidthDp.dp - indexWidth - 1.dp))"))
        assertTrue(screen.contains("if (page != null) page() else AuraSettingsPanePlaceholder()"))
        assertTrue(screen.contains("text = stringResource(R.string.settings_pane_pick)"))
        listOf("values", "values-es").forEach { dir ->
            val xml = File(root, "app/src/main/res/$dir/strings.xml").readText()
            assertTrue("$dir has the hint", xml.contains("<string name=\"settings_pane_pick\">"))
        }
    }
}
