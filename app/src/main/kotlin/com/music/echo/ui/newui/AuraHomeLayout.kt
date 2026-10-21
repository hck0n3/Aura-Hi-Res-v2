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
import androidx.compose.ui.text.style.TextAlign
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
 * Owner 2026-10-19: *«el carrusel está muy pequeño en modo plegable»*, and then — liking the style — *«solo se ven tres; que se vean 4
 * portadas para que mantenga la misma visualización»*: the cards are narrower now (about a quarter of the pane), so three large ones
 * plus the medium and small strips of the multi-browse layout make four to five covers visible. The card used to be the phone's 340 × 260 dp in a
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

internal const val AURA_WIDE_HERO_CARD_FRACTION = 0.26f
internal val AURA_WIDE_HERO_CARD_MIN_WIDTH = 200.dp
internal val AURA_WIDE_HERO_CARD_MAX_WIDTH = 300.dp
internal const val AURA_WIDE_HERO_CARD_ASPECT = 1.1f
internal val AURA_WIDE_HERO_MIN_HEIGHT = 220.dp
internal val AURA_WIDE_HERO_MAX_HEIGHT = 330.dp
internal const val AURA_WIDE_HERO_MAX_HEIGHT_FRACTION = 0.55f
internal val AURA_WIDE_HERO_FLOOR_HEIGHT = 220.dp

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

// ── Pedir música + Acceso rápido: un solo lenguaje ─────────────────────────────────────────────────────────────────

/**
 * How many tiles a row of the quick-access block holds: 2 on a phone and on a medium-wide pane, 4 once the pane is really wide. The tiles are equal-width,
 * equal-height cards (the gradient block at the left, the name and the count beside it), so 2 × 2 on a phone and one row of four
 * on a foldable look like the same block, with «Pedir música» above as the same card across the whole row.
 */
internal fun auraQuickAccessColumns(paneWidth: Dp): Int = if (paneWidth >= AURA_QUICK_ACCESS_WIDE_MIN_WIDTH) 4 else 2

/**
 * Inner width (gutters already taken off) from which the block lays its four tiles in one row. The full-height gradient block takes
 * 72 dp of every tile, so a tile needs about 190 dp to leave the name ~95 dp: below 880 dp a wide pane (an unfolded foldable is
 * ~800 dp) keeps two columns, which are roomy tiles (~400 dp), not a tile stretched absurdly or a name cut to two letters.
 */
internal val AURA_QUICK_ACCESS_WIDE_MIN_WIDTH = 880.dp

/**
 * One fixed height for every tile (no jumps between «Mi top» and «Canciones que me gustan») and the space between tiles. The icon
 * block is a SQUARE of exactly this height, so it is also the block's width.
 */
internal val AURA_QUICK_TILE_HEIGHT = 72.dp
internal val AURA_QUICK_TILE_GAP = 10.dp

/** The «Pedir música» card: the same height as a tile (it is the same card, spanning the whole row). */
internal val AURA_INVITE_CARD_HEIGHT = AURA_QUICK_TILE_HEIGHT

private class AuraQuickTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val loading: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * "Pedir música" built from the SAME piece as the four tiles ([AuraBlockCard]). Owner 2026-10-20, with the screenshot of the
 * tiles they like: *«el estilo de estos botones los quiero de nuevo… y así mismo quiero ese mismo estilo para el botón de pedir
 * música; también quiero el mismo diseño»*. The card is a tile that spans the row: the same dark plate with its hairline, the same
 * corners, the same full-height block with the play button's gradient (cover-driven, like the tiles) with the sparkle in it, the
 * same title and subtitle styles, the same height. [icon] draws inside the block; [loading] swaps it for the spinner.
 */
@Composable
internal fun AuraHomeInviteCard(
    title: String,
    subtitle: String,
    loading: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    AuraBlockCard(
        title = title,
        subtitle = subtitle,
        titleMaxLines = 1,
        subtitleMaxLines = 2,
        loading = loading,
        contentDescription = contentDescription,
        onClick = onClick,
        isTvOrCar = rememberIsTvOrCar(),
        modifier = modifier.fillMaxWidth(),
        icon = icon,
    )
}

/**
 * The one card of the Inicio shortcuts — the four tiles AND «Pedir música» are drawn by this, so they cannot drift apart: a fixed
 * [AURA_QUICK_TILE_HEIGHT] row on the SurfaceFill plate with its SurfaceLine hairline and the card corners; at the left a SQUARE
 * block that fills the card's whole height with the play button's own gradient (so it follows the cover's colours, with its own
 * ink guaranteed to read); at the right the title ([titleMaxLines] lines at most) and the subtitle.
 */
@Composable
private fun AuraBlockCard(
    title: String,
    subtitle: String?,
    titleMaxLines: Int,
    subtitleMaxLines: Int,
    loading: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    isTvOrCar: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(AURA_QUICK_TILE_HEIGHT)
            // TV/car: the ring sits ABOVE the clip and the clickable so it draws outside them. No-op on touch.
            .tvFocusable(isTvOrCar, AuraShapes.Card, scaleFocused = 1f)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(onClick = onClick, contentDescription = contentDescription),
    ) {
        // The play button's own gradient with its own ink: the one pair that is already guaranteed to read on
        // whatever accent the cover gives the app. Full-bleed: the card's clip rounds its outer corners.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AURA_QUICK_TILE_HEIGHT)
                .background(AuraPalette.PlayButtonGradient),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = AuraPalette.OnAccent,
                )
            } else {
                icon()
            }
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            // The whole name: «Canciones que me gustan» does not fit on one line in half a phone.
            Text(
                text = title,
                style = AuraType.MiniTitle,
                color = AuraPalette.OnGround,
                maxLines = titleMaxLines,
                overflow = AuraDefaultOverflow,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AuraType.MiniArtist,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = subtitleMaxLines,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
    }
}

/**
 * "Acceso rápido": four tiles right under «Pedir música», the way Spotify and YouTube Music open on the things you go back
 * to — Canciones que me gustan, Descargado, Mi top and the shuffle button.
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
    /** Songs of the last «Mezcla de tu gusto» this tile built (0 = none yet). */
    tasteMixCount: Int = 0,
    /** The library was too small to mix: the tile played one random song + its radio. */
    tasteMixFallback: Boolean = false,
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
    // The subtitle says what the tile DOES (owner 2026-10-20: «él hace referencia a algo de mi gusto y no sé cómo estará
    // funcionando»): a mix weighted by the user's taste, then how many songs the last tap put in it; and when the library
    // was too small to mix, that it was just something random.
    val shuffleSubtitle = when {
        tasteMixFallback -> stringResource(R.string.home_quick_surprise_fallback)
        tasteMixCount > 0 -> pluralStringResource(R.plurals.home_quick_surprise_built, tasteMixCount, tasteMixCount)
        else -> stringResource(R.string.home_quick_surprise_hint)
    }

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

/**
 * One tile: [AuraBlockCard] with its glyph. Owner 2026-10-20: the full-height gradient block with the dark body is the design they
 * like and want kept (the 2026-10-19 round that shrank the block to a small rounded square is undone — registry 472 had misread
 * «los colores» as «el diseño»).
 */
@Composable
private fun AuraQuickTileCard(tile: AuraQuickTile, isTvOrCar: Boolean, modifier: Modifier = Modifier) {
    AuraBlockCard(
        title = tile.title,
        subtitle = tile.subtitle,
        titleMaxLines = 2,
        subtitleMaxLines = 1,
        loading = tile.loading,
        contentDescription = tile.title,
        onClick = tile.onClick,
        isTvOrCar = isTvOrCar,
        modifier = modifier,
    ) {
        AuraIconGlyph(
            icon = tile.icon,
            contentDescription = null,
            size = AURA_QUICK_TILE_GLYPH,
            tint = AuraPalette.OnAccent,
        )
    }
}

internal val AURA_QUICK_TILE_GLYPH = 26.dp
