package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

/**
 * An album / playlist page that OPENS on its cover.
 *
 * Owner 2026-10-19: *«cuando entre a listas y álbumes que inicien mostrando la portada, no que inicien desde abajo»*.
 *
 * The cause is how a [LazyListState] survives a change of its items: it remembers the KEY of the first visible item and,
 * when the list is rebuilt, follows that key to its new index. These pages load in stages (the playlist row first, the
 * songs and the header a moment later), and whatever sits at index 0 while it loads is a different item from the header
 * that will sit there once it has loaded — a footer spacer, the first of the Descargado cards. When the header is then
 * inserted ABOVE that item, the state "keeps the user where they were" by moving to the item's new index: the page opens
 * scrolled past its cover, "desde abajo".
 *
 * The structural fixes live in the screens (the footer is only emitted once there is content). This is the net under
 * them, for any stage that puts something else first: while the page was OPENED at the top and the user has not touched it,
 * a drift away from index 0 is put back. A position RESTORED from an earlier visit (the user's own, index > 0) is never
 * touched, and the first real scroll — finger, mouse wheel, D-pad — switches this off for good.
 */
@Composable
internal fun AuraOpenAtTop(listState: LazyListState) {
    // Captured once, on the first composition: a restored position (index > 0) belongs to the user.
    val openedAtTop = remember(listState) {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
    if (!openedAtTop) return
    LaunchedEffect(listState) {
        // Our own scrollToItem makes isScrollInProgress true for a moment: it must not count as the user scrolling.
        var snapping by mutableStateOf(false)
        var userScrolled by mutableStateOf(false)
        launch {
            snapshotFlow { listState.isScrollInProgress && !snapping }.collect { moving ->
                if (moving) userScrolled = true
            }
        }
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (!userScrolled && index != 0) {
                snapping = true
                try {
                    listState.scrollToItem(0)
                } finally {
                    snapping = false
                }
            }
        }
    }
}
