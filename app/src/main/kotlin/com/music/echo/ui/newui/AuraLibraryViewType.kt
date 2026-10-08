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
        )
    }
}
