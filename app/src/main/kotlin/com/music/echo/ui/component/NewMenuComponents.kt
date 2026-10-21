package iad1tya.echo.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable

// This file used to also carry NewActionButton / NewMenuItem / NewMenuSectionHeader / NewMenuContent /
// NewIconButton / NewMenuContainer. None of them had a single caller anywhere in the app — the bottom
// sheets build their rows from ListItem directly — so they were removed. NewActionGrid + NewAction are
// the live part: every menu sheet (Song/Album/Artist/Playlist/Queue/Lyrics/Player/YouTube*) uses them.
// MenuHeader + MenuLikeButton (2026-10-20) are the compact header every item menu opens with.

/** Cover / avatar size of [MenuHeader]. */
val MenuHeaderThumbSize = 56.dp

private val MenuTileCorner = 16.dp
private val MenuTileMinHeight = 72.dp
private const val MenuTileLabelSp = 12f
private const val MenuTileLabelMinSp = 9f

/**
 * The row of quick actions at the top of an item menu (Reproducir · Aleatorio · Compartir…).
 *
 * Owner 2026-10-20: the three buttons showed «Reprod… / Aleator… / Compa…» — an icon BESIDE the label inside a
 * connected ButtonGroup left ~50 dp for the text. Now they are equal-width TILES with the icon ABOVE a centred label of up
 * to two lines. The label never truncates: it wraps on word boundaries and, if a single word is still wider than the
 * tile (a narrow 4-up row, a large system font), it steps its size down until it fits ([MenuTileLabel]).
 *
 * A tile in a short last row keeps the width of its siblings (empty cells are padded) instead of stretching across
 * the sheet. With one column (a Listen Together guest) the tile is a horizontal bar.
 */
@Composable
fun NewActionGrid(
    actions: List<NewAction>,
    modifier: Modifier = Modifier,
    columns: Int = 3
) {
    val isTvOrCar = rememberIsTvOrCar()
    val skin = rememberAuraPanelSkin()
    val scheme = MaterialTheme.colorScheme
    val tileFill = if (skin.enabled) skin.fill else scheme.surfaceVariant.copy(alpha = 0.5f)
    val tileLine = if (skin.enabled) skin.line else Color.Transparent
    val tileInk = if (skin.enabled) skin.ink else scheme.onSurface
    val cells = columns.coerceAtLeast(1)
    val stacked = cells > 1
    val shape = RoundedCornerShape(MenuTileCorner)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.chunked(cells).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowActions.forEach { action ->
                    var performAction by remember { mutableStateOf(false) }

                    if (performAction) {
                        action.onClick()
                        // SideEffect, NOT LaunchedEffect: it runs synchronously as soon as this composition
                        // is applied, so the flag is cleared before any recomposition can re-enter the
                        // branch. The old LaunchedEffect dispatched the reset to a coroutine, and the state
                        // change caused BY the action itself (queue changed, download started, playback
                        // moved) recomposed first — running the side effect a second time. That is why
                        // "Reproducir a continuación" / "Añadir a la cola" / "Descargar" / "Compartir" /
                        // "Iniciar radio" could fire twice from one tap.
                        SideEffect { performAction = false }
                    }

                    val bgColor = if (action.backgroundColor != Color.Unspecified) action.backgroundColor else tileFill
                    val contentCol = if (action.contentColor != Color.Unspecified) action.contentColor else tileInk
                    val shownContent = if (action.enabled) contentCol else contentCol.copy(alpha = 0.4f)

                    val tileModifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (stacked) MenuTileMinHeight else 56.dp)
                        .clip(shape)
                        .background(bgColor)
                        .then(if (tileLine != Color.Transparent) Modifier.border(1.dp, tileLine, shape) else Modifier)
                        .clickable(
                            enabled = action.enabled,
                            role = Role.Button,
                            onClick = { performAction = true },
                        )
                        .tvFocusable(isTvOrCar, shape, scaleFocused = 1f)

                    CompositionLocalProvider(LocalContentColor provides shownContent) {
                        if (stacked) {
                            Column(
                                modifier = tileModifier.padding(horizontal = 6.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                action.icon()
                                Spacer(Modifier.size(width = 1.dp, height = 6.dp))
                                MenuTileLabel(text = action.text, color = shownContent)
                            }
                        } else {
                            Row(
                                modifier = tileModifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                action.icon()
                                Spacer(Modifier.width(10.dp))
                                MenuTileLabel(text = action.text, color = shownContent)
                            }
                        }
                    }
                }
                // Empty cells keep the last, shorter row aligned to the same grid as the rows above it.
                repeat(cells - rowActions.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The label of a quick-action tile: centred, at most two lines, and NEVER an ellipsis for a normal label. Wrapping can
 * break a long word in the middle («Reproduci/r»), so after layout it checks for that (or for overflow past two lines) and
 * steps the size down by half a sp, once per layout pass, until it is clean or it reaches [MenuTileLabelMinSp].
 */
@Composable
private fun MenuTileLabel(text: String, color: Color) {
    var sizeSp by remember(text) { mutableFloatStateOf(MenuTileLabelSp) }
    Text(
        text = text,
        color = color,
        style = AuraType.RowSubtitle.copy(
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp + 3f).sp,
        ),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (sizeSp > MenuTileLabelMinSp && (result.hasVisualOverflow || result.breaksInsideWord())) {
                sizeSp = (sizeSp - 0.5f).coerceAtLeast(MenuTileLabelMinSp)
            }
        },
    )
}

/** True when a soft wrap fell in the MIDDLE of a word (neither side of the break is whitespace). */
private fun TextLayoutResult.breaksInsideWord(): Boolean {
    val source = layoutInput.text.text
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line)
        if (end in 1 until source.length && !source[end - 1].isWhitespace() && !source[end].isWhitespace()) return true
    }
    return false
}

data class NewAction(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: @Composable () -> Unit,
    val enabled: Boolean = true,
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified
)

/**
 * The compact header every item menu opens with: cover · title · one clean line of «artist • meta» · a trailing
 * action (the like heart), all on ONE row with no divider under it.
 *
 * Owner 2026-10-20: the old header was the list row of the item (a 48 dp cover, a one-line subtitle that ended in
 * «• 14 …» and a heart floating detached at the far edge). Here the title may take two lines, the [subtitle] (the
 * artist) is the only part that ellipsizes, the [meta] («14 canciones • 2023», «3:42») always stays whole, and the
 * [trailing] slot is a fixed 44 dp cell so the heart is aligned with the cover's centre.
 *
 * [thumbnail] must size itself to [MenuHeaderThumbSize] (the callers pass `Modifier.size(MenuHeaderThumbSize)`).
 */
@Composable
fun MenuHeader(
    title: String,
    thumbnail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val skin = rememberAuraPanelSkin()
    val scheme = MaterialTheme.colorScheme
    val ink = if (skin.enabled) skin.ink else scheme.onSurface
    val inkMuted = if (skin.enabled) skin.inkMuted else scheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(MenuHeaderThumbSize), contentAlignment = Alignment.Center) { thumbnail() }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ink,
                style = AuraType.RowTitle.copy(fontSize = 18.sp, lineHeight = 22.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val hasSubtitle = !subtitle.isNullOrBlank()
            val hasMeta = !meta.isNullOrBlank()
            if (hasSubtitle || hasMeta) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasSubtitle) {
                        Text(
                            text = subtitle.orEmpty(),
                            color = inkMuted,
                            style = AuraType.RowSubtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (hasMeta) {
                        Text(
                            text = if (hasSubtitle) "  •  ${meta.orEmpty()}" else meta.orEmpty(),
                            color = inkMuted,
                            style = AuraType.RowSubtitle,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) { trailing() }
        }
    }
}

/**
 * The cover / avatar slot of a [MenuHeader]: the item's artwork at [MenuHeaderThumbSize], rounded (or a circle for an artist;
 * a VIDEO fills its square frame instead of letterboxing its 4:3 thumbnail, as the list rows do).
 */
@Composable
fun MenuHeaderCover(
    thumbnailUrl: String?,
    circle: Boolean = false,
    isVideo: Boolean = false,
) {
    ItemThumbnail(
        thumbnailUrl = thumbnailUrl,
        isActive = false,
        isPlaying = false,
        shape = if (circle) CircleShape else RoundedCornerShape(10.dp),
        fillBleed = isVideo,
        // Artist avatars are "…=s…" URLs that resize() would break (see ItemThumbnail).
        resize = !circle,
        modifier = Modifier.size(MenuHeaderThumbSize),
    )
}

/** The heart of a [MenuHeader]: a quiet tonal circle, filled with the error colour when liked. */
@Composable
fun MenuLikeButton(
    liked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val skin = rememberAuraPanelSkin()
    val scheme = MaterialTheme.colorScheme
    val idle = if (skin.enabled) skin.ink else scheme.onSurface
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (skin.enabled) skin.fill else scheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
            tint = if (liked) scheme.error else idle,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
    }
}
