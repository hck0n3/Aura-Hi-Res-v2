package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.LibraryViewType

/**
 * Row 349 (owner 2026-10-08, C2 decision 2: «recupera el interruptor»): the list/grid switch of the
 * classic Biblioteca tabs. Same preference keys as the classic screens (SongViewTypeKey, AlbumViewTypeKey,
 * ArtistViewTypeKey, PlaylistViewTypeKey), so a choice made in either interface is the same choice.
 */
@Composable
internal fun AuraViewTypeToggle(viewType: LibraryViewType, onChange: (LibraryViewType) -> Unit) {
    val next = viewType.toggle()
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .auraClickableInternal(
                onClick = { onChange(next) },
                contentDescription = if (next == LibraryViewType.GRID) "Ver en cuadrícula" else "Ver en lista",
            )
            .padding(6.dp),
    ) {
        Icon(
            painter = painterResource(
                if (viewType == LibraryViewType.LIST) R.drawable.grid_view else R.drawable.list,
            ),
            contentDescription = null,
            tint = AuraPalette.OnGroundMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Minimum cell width of the Biblioteca grids (Listas, Canciones, Álbumes, Artistas). 150 / 104 dp on a phone, as
 * always. On a window of 600 dp or more (an unfolded foldable, a tablet) the same cells came out as six tiny
 * thumbnails per row (owner screenshot 2026-10-18: «en modo plegable están muy pequeñas las miniaturas»), so there
 * they grow to 176 / 132 dp — about four per row on a Fold, still adaptive. [GridItemsSizeKey] still decides
 * Grande vs Pequeño on both.
 */
@Composable
internal fun auraLibraryCellSize(big: Boolean): Dp {
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    return when {
        big && wide -> 176.dp
        big -> 150.dp
        wide -> 132.dp
        else -> 104.dp
    }
}

/** One column of full-width rows in list view; the adaptive grid otherwise. */
internal fun auraLibraryColumns(viewType: LibraryViewType, gridCellSize: Dp): GridCells =
    if (viewType == LibraryViewType.LIST) GridCells.Fixed(1) else GridCells.Adaptive(minSize = gridCellSize)

/**
 * A Biblioteca item in either view: the cover card in the grid, a row (same data, same taps) in the list.
 * The grids pad themselves by the gutter, so the row bleeds back to the full width it is designed for.
 */
@Composable
internal fun AuraLibraryCell(
    viewType: LibraryViewType,
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    seed: String?,
    width: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = AuraShapes.Artwork,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    /** Lists pass true: their name is shown in full, never cut with an ellipsis. */
    fullTitle: Boolean = false,
) {
    if (viewType == LibraryViewType.LIST) {
        AuraSongRow(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            seed = seed,
            isActive = isActive,
            isPlaying = isPlaying,
            artworkShape = shape,
            showQualityBadge = false,
            fullTitle = fullTitle,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.auraBleedHorizontal(AuraSpacing.Gutter),
        )
    } else {
        AuraCoverCard(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            seed = seed,
            width = width,
            shape = shape,
            isActive = isActive,
            isPlaying = isPlaying,
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            onPlay = onPlay,
            fullTitle = fullTitle,
        )
    }
}
