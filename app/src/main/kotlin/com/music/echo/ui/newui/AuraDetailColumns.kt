package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import iad1tya.echo.music.ui.utils.LocalContentPaneWidth

/*
 * Plan, phase 1 item 4 (registry row 876): the album, playlist (local and online), artist and automatic-list pages in TWO
 * columns on a wide pane — the header (cover, title, buttons, «Acerca de») in a left column that stays in view, the tracks
 * and shelves in the right one, each scrolling on its own. The decision is pure (`auraDetailHeaderWidthDp`,
 * AuraLibraryDetailLayout.kt); this file is the Compose side every detail page shares, so the five pages cannot drift
 * into five different splits.
 *
 * One column (a compact window: a phone, the folded Fold7; a short window; a pane under 720 dp) is EXACTLY the page it
 * always was: the same LazyColumn, the same state, the same items in the same order (the header items, then the body).
 */

/** Below the status bar, the floating AuraDetailTopBar (back · title · actions) is this tall: the track column starts under it. */
internal val AURA_DETAIL_BODY_TOP_CLEARANCE = 56.dp

/**
 * Width of the header column for the pane this page is laid out in, or `null` = one column. Reads the window layer
 * (`auraPaneWidthDp()`: the content pane, so the split player's half does not count; `isCompactHeight`). Event-driven:
 * it changes only when the window does (a fold, a rotation, a resize).
 */
@Composable
internal fun rememberAuraDetailHeaderWidth(): Dp? =
    auraDetailHeaderWidthDp(auraPaneWidthDp(), auraWindowInfo().isCompactHeight)?.dp

/**
 * The scrolling body of a detail page.
 *
 * [headerWidth] `null`: ONE LazyColumn on [listState] with [modifier] and [contentPadding] — [header] then [body], i.e.
 * byte-for-byte the list the page drew before (the caller emits the header items only where it emitted them before).
 *
 * [headerWidth] set: a Row of two lists. The left one, [headerWidth] wide on [headerListState], holds [header] and starts
 * under the status bar like the phone hero; the right one, on [listState] (the one the top bar, the fast scroller, the
 * reorder, the paging and AuraOpenAtTop already follow), holds [body] and starts below the floating top bar. Each column
 * tells the rows inside it how wide it really is ([LocalContentPaneWidth]), so the header draws its narrow (phone) form and
 * the track rows pick their stacked or column layout from the room they have. Both lists report their scrolling to the
 * shell (row 196).
 */
@Composable
internal fun AuraDetailColumns(
    headerWidth: Dp?,
    listState: LazyListState,
    headerListState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: LazyListScope.() -> Unit,
    body: LazyListScope.() -> Unit,
) {
    if (headerWidth == null) {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = contentPadding,
        ) {
            header()
            body()
        }
        return
    }

    // Registry row 196: the header column scrolls too, so it freezes the shell's haze sampling like the track list does.
    ScrollStateBusReporter { headerListState.isScrollInProgress }
    val direction = LocalLayoutDirection.current
    val start = contentPadding.calculateStartPadding(direction)
    val end = contentPadding.calculateEndPadding(direction)
    val top = contentPadding.calculateTopPadding()
    val bottom = contentPadding.calculateBottomPadding()
    val bodyTop = max(top, auraStatusBarPadding() + AURA_DETAIL_BODY_TOP_CLEARANCE)
    BoxWithConstraints(modifier = modifier) {
        val bodyWidth = (maxWidth - headerWidth).coerceAtLeast(0.dp)
        Row(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalContentPaneWidth provides headerWidth) {
                LazyColumn(
                    state = headerListState,
                    modifier = Modifier
                        .width(headerWidth)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(start = start, top = top, bottom = bottom),
                    content = header,
                )
            }
            CompositionLocalProvider(LocalContentPaneWidth provides bodyWidth) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(end = end, top = bodyTop, bottom = bottom),
                    content = body,
                )
            }
        }
    }
}
