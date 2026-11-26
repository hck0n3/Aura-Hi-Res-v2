package iad1tya.echo.music.ui.newui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.unit.LayoutDirection
import iad1tya.echo.music.ui.utils.TvModalStack
import iad1tya.echo.music.ui.utils.holdsElementOf
import iad1tya.echo.music.ui.utils.isDirectionalFocusMove
import iad1tya.echo.music.ui.utils.tvTrailingIsRight
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 897 (plan «Fase 6.3»): every screen of the Aura UI is usable with a TV remote — a visible focus ring on every
 * control, lists that give the focus back, sheets that take it and keep the D-pad inside — while a touch phone composes
 * exactly what it did (every helper is gated by TvUi's `rememberIsTvOrCar`).
 */
class TvRemoteFocusGuardTest {
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

    /**
     * The screens, sheets and dialogs of the Aura UI, and the files that draw their bodies. `auraTvRing(` counts: it IS
     * `tvFocusable` inside an `AuraTvFocusScope` (TvFocusExtras.kt).
     */
    private val auraScreens = listOf(
        "ui/newui/AuraHomeScreen.kt",
        "ui/newui/AuraHomeLayout.kt",
        "ui/newui/AuraNovedadesScreen.kt",
        "ui/newui/AuraLibraryScreen.kt",
        "ui/newui/AuraLibraryTabs.kt",
        "ui/newui/AuraLibraryPins.kt",
        "ui/newui/AuraLibraryViewType.kt",
        "ui/newui/AuraSearchScreen.kt",
        "ui/newui/AuraSearchResultScreen.kt",
        "ui/newui/AuraSearchTabs.kt",
        "ui/newui/AuraSearchWide.kt",
        "ui/newui/AuraSearchDetailPane.kt",
        "ui/newui/AuraSettingsScreen.kt",
        "ui/newui/AuraStatsScreen.kt",
        "ui/newui/AuraMigrationScreen.kt",
        "ui/newui/AuraAlbumScreen.kt",
        "ui/newui/AuraAlbumGridScreens.kt",
        "ui/newui/AuraArtistScreen.kt",
        "ui/newui/AuraArtistItemsScreen.kt",
        "ui/newui/AuraCollectionScreens.kt",
        "ui/newui/AuraLocalPlaylistScreen.kt",
        "ui/newui/AuraOnlinePlaylistScreen.kt",
        "ui/newui/AuraDownloadsScreen.kt",
        "ui/newui/AuraLyricsScreen.kt",
        "ui/newui/AuraQueue.kt",
        "ui/newui/AuraPlayer.kt",
        "ui/newui/AuraPlayerMenu.kt",
        "ui/newui/AuraPlayerQuickSearch.kt",
        "ui/newui/AuraDownloadSheet.kt",
        "ui/newui/AuraMusicRequest.kt",
        "ui/newui/AuraGlassSlab.kt",
        "ui/newui/AuraRelatedShelf.kt",
        "ui/newui/AuraDetailChrome.kt",
        "ui/newui/AuraStyleFollow.kt",
        "ui/newui/AuraSleepTimer.kt",
        "ui/newui/AuraOfflinePlaybackNotice.kt",
        "ui/screens/ListenTogetherScreen.kt",
    )

    /** Files of the Aura UI with no focus mark of their own, and why that is right. */
    private val allowList = mapOf(
        "ui/newui/AuraShareSheet.kt" to "only DownloadSheetRow (AuraDownloadSheet.kt, ringed) and a Material TextButton, in a scoped sheet",
        "ui/newui/AuraDevicesSheet.kt" to "only DownloadSheetRow (AuraDownloadSheet.kt, ringed), inside BottomSheetPage's scope",
        "ui/newui/AuraOfflineSearch.kt" to "rows are AuraRow / AuraSongRow (ring in AuraPrimitives) inside the scoped Buscar",
        "ui/newui/NovedadesShelves.kt" to "pure layout decisions, nothing drawn",
        "ui/newui/ArtistLatestRelease.kt" to "pure data for the artist page, nothing drawn",
        "ui/newui/AuraWideHero.kt" to "the wide header's artwork and text, no control of its own",
        "ui/newui/AuraScrollToTop.kt" to "a re-select listener, nothing drawn",
    )

    private val marks = Regex("""\.(tvFocusable|tvFocusableItem|tvFocusRestorer|auraTvRing)\(""")

    @Test
    fun `every Aura screen carries the TV remote's focus marks`() {
        for (path in auraScreens) {
            assertTrue("$path: sin anillo ni restaurador de foco para el mando", marks.containsMatchIn(main(path)))
        }
        for ((path, reason) in allowList) {
            assertTrue(reason, reason.isNotBlank())
            main(path) // still exists — remove it from the list when it moves
        }
    }

    @Test
    fun `the primitives draw the ring themselves, only inside a scope, never twice`() {
        val prim = main("ui/newui/AuraPrimitives.kt")
        for (name in listOf("AuraIconButton", "AuraPlayButton", "AuraRow", "AuraMenuRow", "AuraSwitch", "AuraChip")) {
            val body = prim.substringAfter("fun $name(").substringBefore("\n}\n")
            assertTrue("$name sin anillo de mando", body.contains("auraTvRing("))
        }
        val extras = main("ui/utils/TvFocusExtras.kt")
        val ring = extras.substringAfter("fun Modifier.auraTvRing(").substringBefore("\n}\n")
        assertTrue("fuera de un AuraTvFocusScope en TV no cambia nada", ring.contains("if (LocalAuraTvRing.current != true) return this"))
        assertTrue("un anillo que ya trae quien llama no se duplica", ring.contains("if (holdsElementOf(ring)) this else this.then(ring)"))
        assertTrue("la puerta es la de TvUi", extras.contains("CompositionLocalProvider(LocalAuraTvRing provides rememberIsTvOrCar(), content = content)"))
        // AuraRow checks the CALLER'S chain (AuraQueue passes tvFocusableItem into it).
        assertTrue(prim.contains(".let { if (rowClickable) it.auraTvRing(AuraShapes.Highlight) else it }"))
    }

    @Test
    fun `only the screens built for it are scoped, so the hand-ringed screens keep one ring`() {
        val hosts = main("ui/newui/NewUiHosts.kt")
        for (screen in listOf(
            "AuraNovedadesScreen(navController)",
            "AuraLibraryScreen(navController)",
            "AuraSearchScreen(navController = navController, pureBlack = pureBlack)",
            "AuraSearchResultScreen(navController = navController, pureBlack = pureBlack)",
            "AuraMigrationScreen(navController)",
            "AuraMigrationTidalScreen(navController)",
            "AuraMigrationAppleScreen(navController)",
            "AuraMigrationAmazonScreen(navController)",
        )) {
            assertTrue(screen, hosts.contains("new = { AuraTvFocusScope { $screen } },"))
        }
        // These ring their own rows (AuraSongRow with tvFocusable on its outer box): a scope would ring AuraRow inside it too.
        for (screen in listOf("AuraHomeScreen(", "AuraAutoPlaylistScreen(", "AuraAlbumScreen(", "AuraArtistScreen(", "AuraStatsScreen(")) {
            val line = hosts.lines().first { it.contains(screen) && !it.contains("fun ") }
            assertFalse("$screen no debe ir dentro de AuraTvFocusScope", line.contains("AuraTvFocusScope"))
        }
        assertTrue(hosts.contains("AuraTvFocusScope {\n            AuraPlayerMenu("))
    }

    @Test
    fun `floating windows take the focus, keep the D-pad inside and ring their content`() {
        val dialog = main("ui/newui/AuraInWindowDialog.kt")
        assertTrue(dialog.contains(".then(if (visible) Modifier.tvModalFocusGroup(initialFocus = tvInitialFocus) else Modifier)"))
        // A panel that opens on a text field would raise the keyboard over itself: «Añadir música» lands on its list.
        val addMusic = main("ui/screens/playlist/AddMusicSheet.kt")
        assertTrue(addMusic.contains("tvInitialFocus = tvListFocus,"))
        assertTrue(addMusic.contains(".then(if (listFocus != null) Modifier.focusRequester(listFocus) else Modifier),"))
        assertTrue(dialog.contains("AuraTvFocusScope { this@Column.content() }"))
        for (path in listOf("ui/component/BottomSheetMenu.kt", "ui/component/BottomSheetPage.kt")) {
            val src = main(path)
            assertTrue(path, src.contains(".then(if (visible) Modifier.tvModalFocusGroup(state.content) else Modifier)"))
            assertTrue(path, src.contains("AuraTvFocusScope { state.content(this) }"))
        }
        assertEquals("el panel y la búsqueda rápida", 2, Regex(Regex.escape("Modifier.tvModalFocusGroup(state.content)")).findAll(main("ui/component/BottomSheetPage.kt")).count())
        assertTrue(main("ui/component/AuraMaterialDialogs.kt").contains("iad1tya.echo.music.ui.utils.AuraTvFocusScope { content() }"))
        // Modal, not a trap: only D-pad moves are refused; Back still closes (the panels' own back callbacks).
        val modal = main("ui/utils/TvFocusExtras.kt").substringAfter("fun Modifier.tvModalFocusGroup(").substringBefore("\n}\n")
        assertTrue(modal.contains("onExit = { if (isDirectionalFocusMove(requestedFocusDirection)) cancelFocusChange() }"))
        assertTrue(modal.contains("if (!rememberAuraTvFocus()) return this"))
        assertTrue(modal.contains("LaunchedEffect(key) { requestPanelFocus(initialFocus, requester) }"))
        // An initial target with nothing focusable yet (an empty list) falls back to the panel: focus never stays under the veil.
        assertTrue(main("ui/utils/TvFocusExtras.kt").contains("if (initialFocus == null || !requestFocusWithRetry(initialFocus)) requestFocusWithRetry(panel)"))
        // A dialog closing over a menu that stays open hands the focus back to the menu (only at close, bounded).
        assertTrue(modal.contains("DisposableEffect(requester) {"))
        assertTrue(modal.contains("tvModalPanels.opened(regainFocus)"))
        assertTrue(modal.contains("onDispose { tvModalPanels.closed(regainFocus)?.invoke() }"))
        assertTrue(modal.contains("scope.launch { requestPanelFocus(currentInitialFocus, requester) }"))
        assertTrue(dialog.contains("override fun handleOnBackPressed() {"))
    }

    @Test
    fun `the settings index restorer sits in front of its scroll, so it acts on the scroll's group`() {
        val screen = main("ui/newui/AuraSettingsScreen.kt")
        val index = screen.substringAfter("val indexColumn: @Composable (Modifier) -> Unit = { columnModifier ->").substringBefore(") {\n")
        val restorer = index.indexOf(".tvFocusRestorer()")
        val scroll = index.indexOf(".verticalScroll(scrollState)")
        assertTrue("restorer and scroll both present", restorer >= 0 && scroll >= 0)
        // After the scroll it would configure each leaf row instead of the group around them: a placebo.
        assertTrue("tvFocusRestorer must come before verticalScroll", restorer < scroll)
    }

    @Test
    fun `initial focus is bounded and only at entry`() {
        val extras = main("ui/utils/TvFocusExtras.kt")
        assertTrue(extras.contains("internal const val TV_INITIAL_FOCUS_ATTEMPTS = 20"))
        assertTrue(extras.contains("internal const val TV_INITIAL_FOCUS_RETRY_MS = 50L"))
        val retry = extras.substringAfter("private suspend fun requestFocusWithRetry(").substringBefore("\n}\n")
        assertTrue("never an endless loop while music plays", retry.contains("repeat(TV_INITIAL_FOCUS_ATTEMPTS)"))
        assertTrue(main("ui/newui/AuraMigrationScreen.kt").contains("modifier = Modifier.fillMaxSize().tvInitialFocus().tvFocusRestorer(),"))
    }

    @Test
    fun `the trailing button of a row is reached by key, not left out`() {
        val prim = main("ui/newui/AuraPrimitives.kt")
        val row = prim.substringAfter("fun AuraRow(").substringBefore("\n}\n")
        assertTrue(row.contains(".tvRowTrailingKeys(tvTrailing, layoutDirection)"))
        assertTrue(row.contains(".tvRowTarget(tvTrailing)"))
        assertTrue("the target sits right in front of the clickable", row.indexOf(".tvRowTarget(tvTrailing)") < row.indexOf("Modifier.combinedClickable("))
        assertTrue(row.contains("if (trailing != null) TvRowTrailingSlot(tvTrailing, AuraSpacing.RowInner, trailing)"))
        val slot = main("ui/utils/TvFocusExtras.kt").substringAfter("fun TvRowTrailingSlot(").substringBefore("\n}\n")
        assertTrue("without a remote the slot emits the content exactly as before", slot.contains("if (focus == null) {\n        content()\n        return\n    }"))
        // Two controls in the slot (✕ then ↖ in Buscar): toward the start walks to the previous one first, and only the
        // slot's first control returns to the row. The slot is a group that refuses to let that search out.
        val extras = main("ui/utils/TvFocusExtras.kt")
        val keys = extras.substringAfter("fun Modifier.tvRowTrailingKeys(").substringBefore("\n}\n")
        assertTrue(keys.contains("focus.moveWithinSlot(startMove) ||\n                    runCatching { focus.row.requestFocus(FocusDirection.Enter) }.getOrDefault(false)"))
        val within = extras.substringAfter("private fun TvRowTrailingFocus.moveWithinSlot(").substringBefore("\n}\n")
        assertTrue(within.contains("return moved && !exitRefused"))
        assertTrue("the flag never stays on", within.contains("} finally {\n        searchingInSlot = false\n    }"))
        assertTrue(slot.contains("if (focus.searchingInSlot) {\n                        focus.exitRefused = true\n                        cancelFocusChange()"))
        assertTrue(slot.indexOf(".focusProperties {") < slot.indexOf(".focusGroup(),"))
    }

    @Test
    fun `listen together rings its controls and its list gives the focus back`() {
        val lt = main("ui/screens/ListenTogetherScreen.kt")
        assertTrue(lt.contains(".tvFocusRestorer(),"))
        assertTrue(Regex("""\.tvFocusable\(isTvOrCar""").findAll(lt).count() >= 14)
        val nav = main("ui/screens/NavigationBuilder.kt")
        assertEquals(2, Regex(Regex.escape("iad1tya.echo.music.ui.utils.AuraTvFocusScope { ListenTogetherScreen(")).findAll(nav).count())
    }

    // ── Pure helpers ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a modal panel refuses only D-pad and tab moves`() {
        for (d in listOf(FocusDirection.Left, FocusDirection.Right, FocusDirection.Up, FocusDirection.Down, FocusDirection.Next, FocusDirection.Previous)) {
            assertTrue("$d", isDirectionalFocusMove(d))
        }
        assertFalse("requestFocus / clearFocus are never refused", isDirectionalFocusMove(FocusDirection.Enter))
        assertFalse(isDirectionalFocusMove(FocusDirection.Exit))
    }

    @Test
    fun `closing the top panel gives the focus back to the one under it, closing one underneath does not`() {
        val stack = TvModalStack<String>()
        stack.opened("menu")
        stack.opened("dialog")
        assertSame("menu", stack.closed("dialog"))
        assertNull("the last panel closed: nothing to give the focus to", stack.closed("menu"))

        stack.opened("menu")
        stack.opened("dialog")
        assertNull("a menu closing under its dialog takes nothing", stack.closed("menu"))
        assertNull(stack.closed("dialog"))
        assertNull("closing a panel never opened is harmless", stack.closed("unknown"))
    }

    @Test
    fun `the trailing slot is on the right except right to left`() {
        assertTrue(tvTrailingIsRight(LayoutDirection.Ltr))
        assertFalse(tvTrailingIsRight(LayoutDirection.Rtl))
    }

    private object ElementA : Modifier.Element
    private object ElementB : Modifier.Element

    @Test
    fun `a ring already in the chain is found by its class`() {
        assertTrue(Modifier.then(ElementB).then(ElementA).holdsElementOf(ElementA))
        assertFalse(Modifier.then(ElementB).holdsElementOf(ElementA))
        assertFalse("an empty probe matches nothing", Modifier.then(ElementA).holdsElementOf(Modifier))
    }
}
