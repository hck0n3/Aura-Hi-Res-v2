package iad1tya.echo.music.ui.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Row 348 (C2 audit, UI_INVENTORY §1.6): tapping the tab that is ALREADY selected scrolls its screen back
 * to the top. The shell sets `scrollToTop` on the current back-stack entry (MainActivity `onNavItemClick`);
 * only Inicio listened under the new UI, so re-tapping Biblioteca or Novedades did nothing — the classic
 * screens did it. One listener per scrollable; the first to handle it clears the flag.
 */
@Composable
fun AuraScrollToTopOnReselect(navController: NavController, scrollToTop: suspend () -> Unit) {
    val entry by navController.currentBackStackEntryAsState()
    val requested = entry?.savedStateHandle?.getStateFlow(SCROLL_TO_TOP_KEY, false)?.collectAsState()
    val action by rememberUpdatedState(scrollToTop)
    LaunchedEffect(requested?.value) {
        if (requested?.value == true) {
            entry?.savedStateHandle?.set(SCROLL_TO_TOP_KEY, false)
            action()
        }
    }
}

const val SCROLL_TO_TOP_KEY = "scrollToTop"
