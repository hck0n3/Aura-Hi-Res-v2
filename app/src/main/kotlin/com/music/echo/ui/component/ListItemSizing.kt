package iad1tya.echo.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.constants.ListItemHeight
import iad1tya.echo.music.constants.ListThumbnailSize

/**
 * How big a [ListItem] row is drawn. The default is the row every list in the app has always had (48 dp cover, a fixed
 * 64 dp, one-line title); a screen that wants roomier rows wraps its content in
 * `CompositionLocalProvider(LocalListItemSizing provides roomyListItemSizing())`.
 *
 * Owner 2026-10-18, «Añadir a lista» on an unfolded foldable: *«las portadas de la lista se ven muy diminutas… y que se
 * vea el nombre completo de las listas»*. A local instead of one more parameter on the three `ListItem` overloads and
 * `PlaylistListItem`: every other list stays byte-for-byte what it was.
 */
@Immutable
data class ListItemSizing(
    val thumbnail: Dp,
    val minHeight: Dp,
    val titleLines: Int,
    /** true = the row is exactly [minHeight] tall (the classic rows); false = at least that, growing with its title. */
    val fixedHeight: Boolean,
)

val DefaultListItemSizing = ListItemSizing(
    thumbnail = ListThumbnailSize,
    minHeight = ListItemHeight,
    titleLines = 1,
    fixedHeight = true,
)

val LocalListItemSizing = staticCompositionLocalOf { DefaultListItemSizing }

/** Widest-phone / narrowest-tablet breakpoint, the same 560 dp the rest of the wide chrome uses. */
const val ROOMY_LIST_WIDE_DP = 560

/** Rows for a picker: the whole name always, and a big cover when the window is wide (an unfolded foldable, a tablet). */
@Composable
fun roomyListItemSizing(): ListItemSizing =
    if (LocalConfiguration.current.screenWidthDp >= ROOMY_LIST_WIDE_DP) {
        ListItemSizing(thumbnail = 72.dp, minHeight = 88.dp, titleLines = 3, fixedHeight = false)
    } else {
        ListItemSizing(thumbnail = ListThumbnailSize, minHeight = ListItemHeight, titleLines = 3, fixedHeight = false)
    }

/** [PlaylistListItem] in a picker: the whole name and, in a wide window, a big cover (see [roomyListItemSizing]). */
@Composable
fun RoomyPlaylistListItem(
    playlist: iad1tya.echo.music.db.entities.Playlist,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    trailingContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalListItemSizing provides roomyListItemSizing()) {
        PlaylistListItem(
            playlist = playlist,
            modifier = modifier,
            shape = shape,
            trailingContent = trailingContent,
        )
    }
}
