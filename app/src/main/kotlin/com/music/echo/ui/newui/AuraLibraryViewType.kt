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
 * they grow to 176 / 132 dp — about three or four per row on a Fold, still adaptive. [GridItemsSizeKey] still decides
 * Grande vs Pequeño on both.
 *
 * Fila 465 (dueño 2026-10-21, Galaxy Tab S12 Ultra: «las listas de reproducción siguen siendo muy pequeñas»): a 14.6"
 * tablet is ~1480 dp wide, and a cell that stopped growing at 176 dp gave seven or eight covers per row. From
 * [AURA_LIBRARY_TABLET_MIN_DP] the number of COLUMNS is what is chosen (3 / 4 / 5 Grande, 4 / 5 / 6 Pequeño) and the cell
 * fills its share of the pane, so the cover grows with the screen. The Fold keeps exactly what it had.
 */
@Composable
internal fun auraLibraryCellSize(big: Boolean): Dp {
    val windowWidth = iad1tya.echo.music.ui.utils.LocalContentPaneWidth.current?.value
        ?: androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
    return auraLibraryCellDp(windowWidth, big).dp
}

/** The width from which the Biblioteca grids pick a column count instead of a fixed cell (a tablet, not a Fold). */
internal const val AURA_LIBRARY_TABLET_MIN_DP = 900f

private const val AURA_LIBRARY_GRID_GAP_DP = 11f

/**
 * Pure: the minimum cell width, in dp, for a pane [windowWidthDp] wide. Below 600 dp the phone sizes; from 600 dp the
 * Fold sizes; from [AURA_LIBRARY_TABLET_MIN_DP] a size that makes EXACTLY the wanted number of columns fit
 * (`GridCells.Adaptive` counts `floor((pane + gap) / (min + gap))` columns, so the minimum is the share of one column
 * less a dp of slack for rounding).
 */
internal fun auraLibraryCellDp(windowWidthDp: Float, big: Boolean): Float {
    if (windowWidthDp < 600f) return if (big) 150f else 104f
    if (windowWidthDp < AURA_LIBRARY_TABLET_MIN_DP) return if (big) 176f else 132f
    val columns = when {
        windowWidthDp < 1100f -> if (big) 3 else 4
        windowWidthDp < 1300f -> if (big) 4 else 5
        else -> if (big) 5 else 6
    }
    val pane = windowWidthDp - 2 * AuraSpacing.Gutter.value
    return (pane - (columns - 1) * AURA_LIBRARY_GRID_GAP_DP) / columns - 1f
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
