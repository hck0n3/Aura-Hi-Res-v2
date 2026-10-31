package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.LibraryFilter
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.utils.LibraryPin
import iad1tya.echo.music.utils.LibraryPinKind
import java.time.ZoneId

/**
 * Biblioteca ▸ «Fijados» — the pieces of the redesigned hub (owner 2026-10-20): the lower part of Biblioteca shows ONLY what
 * the user pinned, as a big cover grid, instead of dumping every artist, playlist and album. The full lists stay one tap away
 * (the chips «Artistas», «Listas», «Álbumes» are untouched).
 *
 * The data side is `utils/LibraryPins.kt`; the hub that assembles these pieces is [AuraLibraryHub].
 */

// ── Layout numbers (pure, pinned by AuraLibraryPinsLayoutTest) ─────────────────────────────────────

/** Gap between pinned covers, both ways. */
internal val AURA_LIBRARY_PIN_GAP = 12.dp

/** Narrowest a pinned cover may get before the grid drops a column. */
internal val AURA_LIBRARY_PIN_MIN_CARD = 140.dp

/**
 * Columns of the pinned cover grid for a pane [paneWidth] wide: TWO on a phone, THREE on a half-open foldable, FOUR on an
 * unfolded one — never one (a lone full-width cover is a poster, not a shelf) and never more than four (owner 2026-10-18:
 * «cuatro por fila en un Fold»; past that the covers turn into stamps). Measured on the PANE, not the window: with the player
 * column open the pane is much narrower than the screen.
 */
internal fun auraLibraryPinColumns(paneWidth: Dp): Int {
    val available = paneWidth - AuraSpacing.Gutter * 2
    val fit = ((available + AURA_LIBRARY_PIN_GAP) / (AURA_LIBRARY_PIN_MIN_CARD + AURA_LIBRARY_PIN_GAP)).toInt()
    return fit.coerceIn(2, 4)
}

/** Width of one pinned cover so [columns] of them plus their gaps fill the pane between the gutters exactly. */
internal fun auraLibraryPinCardWidth(paneWidth: Dp, columns: Int): Dp =
    (paneWidth - AuraSpacing.Gutter * 2 - AURA_LIBRARY_PIN_GAP * (columns - 1)) / columns

internal val AURA_LIBRARY_TILE_GAP = 10.dp
internal val AURA_LIBRARY_TILE_MIN_WIDTH = 168.dp

/** Columns of the shortcut tiles (Me gusta, Descargado, Local…): two on a phone, up to five when the pane is wide. */
internal fun auraLibraryShortcutColumns(paneWidth: Dp): Int {
    val available = paneWidth - AuraSpacing.Gutter * 2
    val fit = ((available + AURA_LIBRARY_TILE_GAP) / (AURA_LIBRARY_TILE_MIN_WIDTH + AURA_LIBRARY_TILE_GAP)).toInt()
    return fit.coerceIn(2, 5)
}

// ── A pin resolved against the library ─────────────────────────────────────────────────────────────

/**
 * A [LibraryPin] together with the live library entity it points at, when there is one. The live entity wins at draw time
 * (a renamed playlist, a new cover), and the pin's own snapshot is what keeps an item drawable when it is NOT in the library
 * lists — a YouTube album the user pinned without saving it, say.
 */
@Immutable
internal class AuraResolvedPin(
    val pin: LibraryPin,
    val playlist: Playlist?,
    val album: Album?,
    val artist: Artist?,
) {
    val key: String get() = pin.key

    val title: String
        get() = playlist?.playlist?.name ?: album?.album?.title ?: artist?.artist?.name ?: pin.title

    val thumbnailUrl: String?
        get() = when (pin.kind) {
            LibraryPinKind.PLAYLIST -> playlist?.thumbnails?.firstOrNull() ?: pin.thumbnailUrl
            LibraryPinKind.ALBUM -> album?.album?.thumbnailUrl ?: pin.thumbnailUrl
            LibraryPinKind.ARTIST -> artist?.artist?.thumbnailUrl ?: pin.thumbnailUrl
        }

    val contentKind: AuraContentKind
        get() = when (pin.kind) {
            LibraryPinKind.PLAYLIST -> AuraContentKind.Playlist
            LibraryPinKind.ALBUM -> AuraContentKind.Album
            LibraryPinKind.ARTIST -> AuraContentKind.Artist
        }

    /** When the item last changed (library entity), else when it was pinned. Feeds the «Fecha de actualización» order. */
    fun updatedAtMillis(): Long {
        val at = playlist?.playlist?.lastUpdateTime ?: album?.album?.lastUpdateTime ?: artist?.artist?.lastUpdateTime
        return at?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: pin.pinnedAt
    }
}

/** Pins matched to the library lists. A playlist pin matches by local id OR by the YouTube browse id. */
internal fun resolveLibraryPins(
    pins: List<LibraryPin>,
    playlists: List<Playlist>,
    albums: List<Album>,
    artists: List<Artist>,
): List<AuraResolvedPin> {
    if (pins.isEmpty()) return emptyList()
    val playlistById = HashMap<String, Playlist>(playlists.size)
    val playlistByBrowse = HashMap<String, Playlist>()
    playlists.forEach { p ->
        playlistById.putIfAbsent(p.id, p)
        p.playlist.browseId?.let { b ->
            playlistByBrowse.putIfAbsent(b, p)
            playlistByBrowse.putIfAbsent(b.removePrefix("VL"), p)
        }
    }
    val albumById = albums.associateBy { it.id }
    val artistById = artists.associateBy { it.id }
    return pins.map { pin ->
        when (pin.kind) {
            LibraryPinKind.PLAYLIST -> AuraResolvedPin(
                pin = pin,
                playlist = playlistById[pin.id] ?: playlistByBrowse[pin.id] ?: playlistByBrowse[pin.id.removePrefix("VL")],
                album = null,
                artist = null,
            )
            LibraryPinKind.ALBUM -> AuraResolvedPin(pin, null, albumById[pin.id], null)
            LibraryPinKind.ARTIST -> AuraResolvedPin(pin, null, null, artistById[pin.id])
        }
    }
}

/** «Playlist · 12 canciones», «Álbum · Artista», «Artista · 34 canciones»: the type first, so a mixed grid reads at a glance. */
@Composable
internal fun AuraResolvedPin.subtitleText(): String {
    val label = auraTypeLabel(contentKind)
    val detail: String? = when (pin.kind) {
        LibraryPinKind.PLAYLIST ->
            playlist?.let { pluralStringResource(R.plurals.n_song, it.songCount, it.songCount) } ?: pin.subtitle
        LibraryPinKind.ALBUM ->
            album?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() } ?: pin.subtitle
        LibraryPinKind.ARTIST ->
            artist?.let { pluralStringResource(R.plurals.n_song, it.songCount, it.songCount) }
    }
    return if (detail.isNullOrBlank()) label else "$label · $detail"
}

/** The kind filter of the pinned section. */
internal enum class AuraPinFilter(val kind: LibraryPinKind?, val labelRes: Int) {
    ALL(null, R.string.filter_all),
    PLAYLISTS(LibraryPinKind.PLAYLIST, R.string.library_pins_filter_playlists),
    ALBUMS(LibraryPinKind.ALBUM, R.string.filter_albums),
    ARTISTS(LibraryPinKind.ARTIST, R.string.filter_artists),
}

// ── Shortcut tiles ─────────────────────────────────────────────────────────────────────────────────

internal class AuraLibraryShortcut(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The Library's shortcut buttons (Me gusta, Exportado, Mi top, Descargado, En caché, Álbumes favoritos, Novedades, Podcasts,
 * Local…) in the vocabulary of Inicio's quick-access tiles: a glass plate with its hairline and the cover-driven accent in the
 * icon chip. Every one of them stays — only the arrangement changed: [columns] per row, all rows the height of the tallest tile.
 */
@Composable
internal fun AuraLibraryShortcutGrid(
    shortcuts: List<AuraLibraryShortcut>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AURA_LIBRARY_TILE_GAP),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 6.dp),
    ) {
        shortcuts.chunked(columns).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(AURA_LIBRARY_TILE_GAP),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                row.forEach { shortcut ->
                    AuraLibraryShortcutTile(
                        icon = shortcut.icon,
                        label = shortcut.label,
                        onClick = shortcut.onClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AuraLibraryShortcutTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(onClick = onClick, contentDescription = label)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // The chip follows the cover (Teal / Blue are the live accents) at low alpha: tinted, never a block of colour — the
        // gradient plate stays the privilege of the play button and the primary actions.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(AuraPalette.Teal.copy(alpha = 0.34f), AuraPalette.Blue.copy(alpha = 0.16f)),
                    ),
                ),
        ) {
            AuraIconGlyph(icon = icon, contentDescription = null, size = 19.dp, tint = AuraPalette.Teal)
        }
        Text(
            text = label,
            style = AuraType.MiniTitle,
            color = AuraPalette.OnGround,
            // Row 567: «Canciones que me gust…» was cut at two lines; three keep every label whole (the row grows, the tiles of a row stay level).
            maxLines = 3,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Pinned section: header, card, empty state, jump row ─────────────────────────────────────────────

/** «📌 Fijados · 12 elementos» with the «Editar» pill (only when there is something to edit). */
@Composable
internal fun AuraPinsHeader(
    count: Int,
    editing: Boolean,
    onToggleEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter, end = AuraSpacing.Gutter, top = AuraSpacing.SectionTop),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AuraPalette.PlayButtonGradient),
        ) {
            AuraIconGlyph(icon = AuraIcons.Pin, contentDescription = null, size = 17.dp, tint = AuraPalette.OnAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.library_pins_title),
                style = AuraType.ContentSection,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            if (count > 0) {
                Text(
                    text = pluralStringResource(R.plurals.library_pins_count, count, count),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
        if (count > 0) {
            val label = stringResource(if (editing) R.string.library_pins_done else R.string.library_pins_edit)
            val description = stringResource(if (editing) R.string.library_pins_done else R.string.library_pins_edit_cd)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(AuraShapes.Pill)
                    .then(
                        if (editing) {
                            Modifier.background(AuraPalette.PlayButtonGradient)
                        } else {
                            Modifier
                                .background(AuraPalette.SurfaceFill)
                                .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
                        },
                    )
                    .auraClickableInternal(onClick = onToggleEdit, contentDescription = description)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                if (editing) {
                    AuraIconGlyph(icon = AuraIcons.Check, contentDescription = null, size = 15.dp, tint = AuraPalette.OnAccent)
                }
                Text(
                    text = label,
                    style = AuraType.Chip,
                    color = if (editing) AuraPalette.OnAccent else AuraPalette.OnGround,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One pinned cover: the Inicio shelf card at the grid's width. Albums keep their ▶; artists keep their circle. */
@Composable
internal fun AuraPinCard(
    resolved: AuraResolvedPin,
    width: Dp,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AuraCoverCard(
        title = resolved.title,
        subtitle = resolved.subtitleText(),
        thumbnailUrl = resolved.thumbnailUrl,
        seed = resolved.pin.id,
        width = width,
        shape = auraTypeVisual(resolved.contentKind).shape,
        isActive = isActive,
        isPlaying = isPlaying,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        // The ▶ sits in the corner of a square cover; a circle would clip it, so only albums get it (as in Álbumes).
        onPlay = if (resolved.pin.kind == LibraryPinKind.ALBUM) onPlay else null,
        // Lists show their whole name (registry row 399); the rest keep the two-line block.
        fullTitle = resolved.pin.kind == LibraryPinKind.PLAYLIST,
    )
}

/**
 * Nothing pinned yet: a card that says what to do and offers the three doors to the full lists, so the section is never a
 * blank hole under the shortcuts.
 */
@Composable
internal fun AuraPinsEmptyCard(
    onOpenFilter: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .padding(horizontal = AuraSpacing.Gutter, vertical = 14.dp)
            .fillMaxWidth()
            .clip(AuraShapes.Card)
            .background(
                Brush.verticalGradient(
                    listOf(AuraPalette.Teal.copy(alpha = 0.16f), AuraPalette.SurfaceFill),
                ),
            )
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AuraPalette.PlayButtonGradient),
        ) {
            AuraIconGlyph(icon = AuraIcons.Pin, contentDescription = null, size = 26.dp, tint = AuraPalette.OnAccent)
        }
        Text(
            text = stringResource(R.string.library_pins_empty_title),
            style = AuraType.PlayerTitle,
            color = AuraPalette.OnGround,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.library_pins_empty_body),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        AuraLibraryJumpChips(onOpenFilter = onOpenFilter, centered = true)
    }
}

/** «Ver todo»: the three full lists. Under the pinned grid, and in the empty card. */
@Composable
internal fun AuraLibraryJumpRow(
    onOpenFilter: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.library_pins_see_all),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundGhost,
        )
        AuraLibraryJumpChips(onOpenFilter = onOpenFilter, centered = false)
    }
}

@Composable
private fun AuraLibraryJumpChips(
    onOpenFilter: (LibraryFilter) -> Unit,
    centered: Boolean,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(
            8.dp,
            if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AuraChip(
            text = stringResource(R.string.library_pins_filter_playlists),
            selected = false,
            onClick = { onOpenFilter(LibraryFilter.PLAYLISTS) },
        )
        AuraChip(
            text = stringResource(R.string.filter_albums),
            selected = false,
            onClick = { onOpenFilter(LibraryFilter.ALBUMS) },
        )
        AuraChip(
            text = stringResource(R.string.filter_artists),
            selected = false,
            onClick = { onOpenFilter(LibraryFilter.ARTISTS) },
        )
    }
}

/**
 * The small pin on a cover in the full lists (Listas, Álbumes, Artistas) that marks what is already pinned. [circle] = an artist's
 * circular portrait: the badge moves in from the corner, which a circle would clip.
 */
@Composable
internal fun BoxScope.AuraPinnedBadge(circle: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (circle) Alignment.BottomEnd else Alignment.TopStart)
            .padding(if (circle) 14.dp else 7.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(AuraPalette.Ground.copy(alpha = 0.72f)),
    ) {
        AuraIconGlyph(
            icon = AuraIcons.Pin,
            contentDescription = stringResource(R.string.library_pinned_cd),
            size = 12.dp,
            tint = AuraPalette.Teal,
        )
    }
}
