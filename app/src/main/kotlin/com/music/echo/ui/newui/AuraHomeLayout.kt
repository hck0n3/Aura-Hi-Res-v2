package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.TopSize
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable

// ── Inicio: pieces whose job is the LAYOUT of a wide window and the shortcuts under the greeting ──────────────────

/**
 * The large card of the Inicio carousel in a wide window (an unfolded foldable, a tablet).
 *
 * Owner 2026-10-19: *«el carrusel está muy pequeño en modo plegable»*. The card used to be the phone's 340 × 260 dp in a
 * 700–900 dp window, a postage stamp in a sea of Ground. Now the large card FOLLOWS the pane: [AURA_WIDE_HERO_CARD_FRACTION]
 * of its width (so two large cards plus the medium and small ones peeking fit the row), between
 * [AURA_WIDE_HERO_CARD_MIN_WIDTH] and [AURA_WIDE_HERO_CARD_MAX_WIDTH], and a height that keeps the phone card's landscape
 * proportion ([AURA_WIDE_HERO_CARD_ASPECT]) but never takes more than [AURA_WIDE_HERO_MAX_HEIGHT_FRACTION] of the window's
 * height, so a landscape window still shows the section under it.
 *
 * Pure on purpose: it is the one place the number comes from, and a unit test pins the bounds.
 */
internal class AuraHeroCardSize(val width: Dp, val height: Dp)

internal fun auraWideHeroCardSize(paneWidth: Dp, windowHeightDp: Int): AuraHeroCardSize {
    val width = (paneWidth * AURA_WIDE_HERO_CARD_FRACTION)
        .coerceIn(AURA_WIDE_HERO_CARD_MIN_WIDTH, AURA_WIDE_HERO_CARD_MAX_WIDTH)
    val byWidth = (width * AURA_WIDE_HERO_CARD_ASPECT).coerceIn(AURA_WIDE_HERO_MIN_HEIGHT, AURA_WIDE_HERO_MAX_HEIGHT)
    val byWindow = (windowHeightDp * AURA_WIDE_HERO_MAX_HEIGHT_FRACTION).dp.coerceAtLeast(AURA_WIDE_HERO_FLOOR_HEIGHT)
    return AuraHeroCardSize(width = width, height = minOf(byWidth, byWindow))
}

internal const val AURA_WIDE_HERO_CARD_FRACTION = 0.46f
internal val AURA_WIDE_HERO_CARD_MIN_WIDTH = 320.dp
internal val AURA_WIDE_HERO_CARD_MAX_WIDTH = 520.dp
internal const val AURA_WIDE_HERO_CARD_ASPECT = 0.8f
internal val AURA_WIDE_HERO_MIN_HEIGHT = 280.dp
internal val AURA_WIDE_HERO_MAX_HEIGHT = 400.dp
internal const val AURA_WIDE_HERO_MAX_HEIGHT_FRACTION = 0.55f
internal val AURA_WIDE_HERO_FLOOR_HEIGHT = 240.dp

// ── Song rows in one or two columns ───────────────────────────────────────────────────────────────────────────────

/**
 * A stack of song rows that becomes TWO columns when the pane is wide enough ([AURA_ROWS_TWO_COLUMNS_MIN_WIDTH]).
 *
 * Owner 2026-10-19: Inicio "se ve muy básico" in the foldable. A song row stretched across 800 dp is mostly empty: the
 * title at the left, the ⋯ a thumb's reach away at the right. Two columns put the same rows where the eye can take them in,
 * and the page gets shorter instead of longer. Read order is row by row (left, right, next row). On a phone it is the
 * single column it always was; the rows themselves are unchanged — only where they sit.
 *
 * [row] draws the item at an index; it is called for exactly the indexes `0 until count`.
 */
@Composable
internal fun AuraRowsInColumns(
    count: Int,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= AURA_ROWS_TWO_COLUMNS_MIN_WIDTH) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (start in 0 until count step columns) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AURA_ROWS_COLUMN_GAP),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (offset in 0 until columns) {
                        val index = start + offset
                        Box(modifier = Modifier.weight(1f)) {
                            if (index < count) row(index)
                        }
                    }
                }
            }
        }
    }
}

/** The inner width (gutters already taken off) from which song rows go into two columns. */
internal val AURA_ROWS_TWO_COLUMNS_MIN_WIDTH = 600.dp
private val AURA_ROWS_COLUMN_GAP = 20.dp

// ── Cross-shelf dedupe ────────────────────────────────────────────────────────────────────────────────────────────

/**
 * This shelf's songs without the ones already shown above it ([shownAbove]). A shelf that would lose EVERYTHING keeps its
 * original list — the same guard the view model's own cross-section dedupe uses — so a dedupe can make a shelf shorter, never
 * empty or missing.
 */
internal fun <T> List<T>.auraDedupedAgainst(shownAbove: Set<String>, idOf: (T) -> String): List<T> =
    if (shownAbove.isEmpty()) this else filterNot { idOf(it) in shownAbove }.ifEmpty { this }

internal fun List<iad1tya.echo.music.db.entities.Song>.auraDedupedAgainst(shownAbove: Set<String>) =
    auraDedupedAgainst(shownAbove) { it.id }

// ── Acceso rápido ─────────────────────────────────────────────────────────────────────────────────────────────────

/** How many tiles a row of the quick-access block holds: 2 on a phone, 4 once the pane is wide. */
internal fun auraQuickAccessColumns(paneWidth: Dp): Int = if (paneWidth >= AURA_QUICK_ACCESS_WIDE_MIN_WIDTH) 4 else 2

/** Inner width (gutters already taken off) from which the block lays its four tiles in one row. */
internal val AURA_QUICK_ACCESS_WIDE_MIN_WIDTH = 520.dp

private val AURA_QUICK_TILE_HEIGHT = 68.dp
private val AURA_QUICK_TILE_GAP = 10.dp

private class AuraQuickTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val loading: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * "Acceso rápido": four tiles right under the greeting, the way Spotify and YouTube Music open on the things you go back
 * to — Canciones que me gustan, Descargado, Historial and the shuffle button.
 *
 * Why these four. They are the user's OWN places, one tap from Inicio instead of two or three through Biblioteca, and they
 * cost no network and no image decoding (an icon and two lines of text each), so the block is free for the thermal budget.
 * The fourth is the five-dot "botón aleatorio" the inventory lists on Inicio (§10) and that no Aura Home had drawn since the
 * redesign: tapping it while it loads CANCELS the pending pick, exactly as the inventory records it.
 *
 * The two counts are read here, not in the Home, so a download finishing recomposes four tiles, never the whole screen.
 */
@Composable
internal fun AuraQuickAccess(
    navController: NavController,
    surprising: Boolean,
    onSurprise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val likedCount by remember(database) { database.likedSongsCount() }.collectAsState(initial = 0)
    val downloads by LocalDownloadUtil.current.downloads.collectAsState()
    val downloadedCount = remember(downloads) { downloads.values.count { it.state == Download.STATE_COMPLETED } }
    val isTvOrCar = rememberIsTvOrCar()

    val likedTitle = stringResource(R.string.liked)
    val likedSubtitle = pluralStringResource(R.plurals.n_song, likedCount, likedCount)
    val downloadedTitle = stringResource(R.string.offline)
    val downloadedSubtitle = pluralStringResource(R.plurals.n_song, downloadedCount, downloadedCount)
    // «Historial» is already in the header's actions (the same on Inicio, Buscar and Novedades): not repeated here. Mi top is
    // in Biblioteca only, two taps away.
    val topSize by rememberPreference(TopSize, "50")
    val topTitle = stringResource(R.string.my_top)
    val topCount = topSize.toIntOrNull() ?: 50
    val topSubtitle = pluralStringResource(R.plurals.n_song, topCount, topCount)
    val shuffleTitle = stringResource(R.string.shuffle)
    val shuffleSubtitle = stringResource(R.string.home_quick_surprise_hint)

    val tiles = listOf(
        AuraQuickTile(AuraIcons.HeartFilled, likedTitle, likedSubtitle, onClick = { navController.navigate("auto_playlist/liked") }),
        AuraQuickTile(AuraIcons.Downloaded, downloadedTitle, downloadedSubtitle, onClick = { navController.navigate("auto_playlist/downloaded") }),
        AuraQuickTile(AuraIcons.Music, topTitle, topSubtitle, onClick = { navController.navigate("top_playlist/$topSize") }),
        AuraQuickTile(AuraIcons.Shuffle, shuffleTitle, shuffleSubtitle, loading = surprising, onClick = onSurprise),
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = auraQuickAccessColumns(maxWidth - AuraSpacing.Gutter * 2)
        Column(
            verticalArrangement = Arrangement.spacedBy(AURA_QUICK_TILE_GAP),
            modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
        ) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AURA_QUICK_TILE_GAP),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowTiles.forEach { tile ->
                        AuraQuickTileCard(tile = tile, isTvOrCar = isTvOrCar, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AuraQuickTileCard(tile: AuraQuickTile, isTvOrCar: Boolean, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(AURA_QUICK_TILE_HEIGHT)
            // TV/car: the ring sits ABOVE the clip and the clickable so it draws outside them. No-op on touch.
            .tvFocusable(isTvOrCar, AuraShapes.Card, scaleFocused = 1f)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(onClick = tile.onClick, contentDescription = tile.title),
    ) {
        // The play button's own gradient with its own ink: the one pair that is already guaranteed to read on
        // whatever accent the cover gives the app.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AURA_QUICK_TILE_HEIGHT)
                .background(AuraPalette.PlayButtonGradient),
        ) {
            if (tile.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = AuraPalette.OnAccent,
                )
            } else {
                AuraIconGlyph(
                    icon = tile.icon,
                    contentDescription = null,
                    size = 26.dp,
                    tint = AuraPalette.OnAccent,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            // The whole name, on two lines at most: «Canciones que me gustan» does not fit on one in half a phone.
            Text(
                text = tile.title,
                style = AuraType.MiniTitle,
                color = AuraPalette.OnGround,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
            if (tile.subtitle != null) {
                Text(
                    text = tile.subtitle,
                    style = AuraType.MiniArtist,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
    }
}
