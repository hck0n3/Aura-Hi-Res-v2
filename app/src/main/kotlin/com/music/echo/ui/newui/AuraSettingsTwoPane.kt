package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/*
 * The Compose side of the two-pane Ajustes (registry row 873). The decisions are pure, in AuraSettingsPanes.kt; the index
 * itself is AuraSettingsScreen.kt (`AuraSettingsIndexHost`).
 *
 * Every Ajustes route keeps its own `composable("…")` registration in NavigationBuilder.kt; its body is wrapped in
 * [AuraSettingsPaneFrame], and the NavHost's four transitions in MainActivity ask [auraSettingsInstantSwap] first.
 */

/** Does Ajustes use two panes in the current window (and content pane)? Read from the window layer, event-driven. */
@Composable
fun rememberAuraSettingsTwoPanes(): Boolean {
    val heightDp = auraWindowInfo().heightDp
    val paneWidthDp = auraPaneWidthDp()
    return remember(paneWidthDp, heightDp) { auraSettingsUsesTwoPanes(paneWidthDp, heightDp) }
}

/** A page opened in its welcome mode (`?onboarding=true`) is a step of the welcome, never a pane of Ajustes. */
private fun NavBackStackEntry.isOnboarding(): Boolean = arguments?.getBoolean("onboarding") == true

/**
 * For the NavHost's transitions: is the move from [from] to [to] only a change of the right pane of the two-pane Ajustes?
 * Then it has no transition (the full-width slide would carry the unchanged index out and back in). False on a phone,
 * when leaving Ajustes, and for a page in its welcome mode: the app's own transition runs as before.
 */
fun auraSettingsInstantSwap(from: NavBackStackEntry, to: NavBackStackEntry): Boolean =
    auraSettingsSwapIsInstant(
        twoPanes = AuraSettingsPanes.twoPanesNow,
        fromRoute = from.destination.route,
        fromOnboarding = from.isOnboarding(),
        toRoute = to.destination.route,
        toOnboarding = to.isOnboarding(),
    )

/**
 * Wraps the screen of an Ajustes route ([AURA_SETTINGS_ROUTE], or a page of [AURA_SETTINGS_PANE_ROUTES] /
 * [AURA_SETTINGS_PANE_CHILDREN]). The page as it always was — or, on a two-pane window with the new interface and not
 * in welcome mode, the same page in the right pane of the Ajustes index. `settings` itself draws its own two panes.
 *
 * The page is movable content, so folding or unfolding the device while it is open keeps its scroll and its state
 * instead of building it again.
 */
@Composable
fun AuraSettingsPaneFrame(
    navController: NavController,
    entry: NavBackStackEntry,
    content: @Composable () -> Unit,
) {
    val newUi = rememberNewUiEnabled()
    val twoPanes = rememberAuraSettingsTwoPanes()
    SideEffect { AuraSettingsPanes.twoPanesNow = newUi && twoPanes }
    val route = entry.destination.route
    val currentContent by rememberUpdatedState(content)
    val page = remember { movableContentOf { currentContent() } }
    if (auraSettingsPaneSelectionOf(route) == null || !newUi || !twoPanes || entry.isOnboarding()) {
        page()
    } else {
        AuraSettingsIndexHost(navController = navController, hostRoute = route.orEmpty(), page = page)
    }
}
