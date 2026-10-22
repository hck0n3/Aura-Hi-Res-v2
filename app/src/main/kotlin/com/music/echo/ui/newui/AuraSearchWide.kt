package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.LocalItem
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.ShuffleContexts
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeAlbumRadio
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.ArtistMenu
import iad1tya.echo.music.ui.menu.PlaylistMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.utils.makeTimeString
import kotlinx.coroutines.flow.flowOf

/*
 * # Search in a WIDE window (an unfolded foldable, a tablet) — one set of pieces for every search surface
 *
 * Owner 2026-10-20 (unfolded Z Fold): *«la búsqueda, como [en modo] desplegado, los resultados los muestra muy pequeños
 * porque del otro lado de la ventana flotante hay suficiente espacio… cuando yo toque la canción no me aparezca otro
 * flotante sino que aproveche el otro espacio para mostrar las opciones, como una pantalla dividida… necesito un
 * identificador [de canción o álbum]… en los filtros las carátulas y todo sea más grande»*.
 *
 * Four things, shared by the quick search of the player, the results route, the suggestions panel and the library search
 * so they cannot drift apart:
 *
 *  1. [AuraTypeBadge] — a small pill (icon + tracked label) on EVERY result: CANCIÓN / ÁLBUM / SENCILLO / EP / ARTISTA /
 *     PLAYLIST / VÍDEO. Same shape, same palette tokens for all of them; only the tint (the accent trio) and the glyph
 *     change. The cover SHAPE already differs by kind (circle artist, 16:9 video, square release): see [auraTypeVisual].
 *  2. [AuraSearchWideRow] / [AuraSearchGridCard] — bigger covers (76 dp rows, grid cells from 150 dp) with artist, album,
 *     duration and the explicit «E» where the data exists.
 *  3. [AuraSearchSplit] + [AuraSearchDetailPane] (AuraSearchDetailPane.kt) — master-detail: tapping a result selects it and the
 *     OTHER half of the window shows what is USEFUL for it (action tiles, the track list of an album / playlist, the whole
 *     artist page, with its own back stack); the full ⋯ menu is one tap away inside it. No second floating window opens.
 *  4. The gates ([auraSearchSplitFits]) decide from the room the screen REALLY has, measured by the caller, so a content
 *     pane squeezed by the split player, a phone on its side and a folded phone all keep today's layout.
 */

// ── Gates and sizes (pure: a test pins them) ──────────────────────────────────────────────────────

/** Narrowest room (dp) in which the results and the detail can sit side by side. Material's «medium» line, as the rest of the wide chrome. */
internal const val AURA_SEARCH_SPLIT_MIN_WIDTH_DP = 600f

/** Shortest room (dp) for the split: a phone turned on its side (~300 dp of content) keeps its single column. */
internal const val AURA_SEARCH_SPLIT_MIN_HEIGHT_DP = 420f

/** Widest the quick-search card may grow in a wide window (it is 640 dp on a phone). */
const val AuraSpotlightWideMaxWidthDp = 1080

/** Is a room of [widthDp] × [heightDp] big enough for results + detail side by side? */
internal fun auraSearchSplitFits(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= AURA_SEARCH_SPLIT_MIN_WIDTH_DP && heightDp >= AURA_SEARCH_SPLIT_MIN_HEIGHT_DP

/** Width of the detail half for [total] dp of room: ~46 %, never narrower than a menu needs nor wider than a tablet wants. */
internal fun auraSearchDetailWidth(total: Dp): Dp = (total * 0.46f).coerceIn(300.dp, 520.dp)

/** Columns of a filter grid: as many [minCell]-wide cells as fit in [available], and at least [floor]. Pure. */
internal fun auraSearchGridColumns(available: Dp, minCell: Dp, spacing: Dp, floor: Int = 2): Int =
    ((available + spacing) / (minCell + spacing)).toInt().coerceAtLeast(floor)

/** Cover of a wide result row (a phone row is 54 dp): videos keep their 16:9 frame at the same height. */
internal val AuraSearchRowCover: Dp = 76.dp
internal val AuraSearchRowVideoCover: Dp = 128.dp

internal fun auraSearchRowCoverWidth(kind: AuraContentKind): Dp =
    if (kind == AuraContentKind.Video) AuraSearchRowVideoCover else AuraSearchRowCover

// ── The type identifier ───────────────────────────────────────────────────────────────────────────

@Composable
internal fun auraSearchKindLabel(kind: AuraContentKind): String = stringResource(
    when (kind) {
        AuraContentKind.Song -> R.string.aura_kind_song
        AuraContentKind.Video -> R.string.aura_kind_video
        AuraContentKind.Album -> R.string.aura_kind_album
        AuraContentKind.Ep -> R.string.aura_kind_ep
        AuraContentKind.Single -> R.string.aura_kind_single
        AuraContentKind.Playlist -> R.string.aura_kind_playlist
        AuraContentKind.Artist -> R.string.aura_kind_artist
        AuraContentKind.Podcast -> R.string.aura_kind_podcast
    },
)

/** The accent trio of the app, split by family so the eye separates tracks, releases and collections before it reads. */
@Composable
internal fun auraTypeTint(kind: AuraContentKind): Color = when (kind) {
    AuraContentKind.Song, AuraContentKind.Artist -> AuraPalette.Teal
    AuraContentKind.Album, AuraContentKind.Ep, AuraContentKind.Single, AuraContentKind.Podcast -> AuraPalette.Blue
    AuraContentKind.Video, AuraContentKind.Playlist -> AuraPalette.Violet
}

/**
 * The type pill: glyph + tracked upper-case label on the ground at 82 %, with a hairline in its own tint. The same recipe
 * for every kind (it is the app's own `typeChip` pill, made readable at a glance), so a result is never identified by the
 * title alone. [AuraPalette.Teal]/[AuraPalette.Blue]/[AuraPalette.Violet] are legible on the ground by construction.
 */
@Composable
internal fun AuraTypeBadge(
    kind: AuraContentKind,
    modifier: Modifier = Modifier,
) {
    val tint = auraTypeTint(kind)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(AuraShapes.Pill)
            .background(AuraPalette.Ground.copy(alpha = 0.82f))
            .border(1.dp, tint.copy(alpha = 0.40f), AuraShapes.Pill)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        AuraIconGlyph(icon = auraTypeIcon(kind), contentDescription = null, size = 11.dp, tint = tint)
        Text(
            text = auraSearchKindLabel(kind),
            style = AuraType.QualityBadge,
            color = tint,
            maxLines = 1,
        )
    }
}

// ── What a result row shows ───────────────────────────────────────────────────────────────────────

/** Everything a wide result shows, whatever it came from (YouTube Music or the library). */
@Immutable
internal data class AuraSearchEntry(
    val kind: AuraContentKind,
    val title: String,
    /** Artist(s) / author. Null when the source has none (an artist result). */
    val artist: String?,
    /** Album · year / song count. Null when unknown — never invented. */
    val meta: String?,
    /** `m:ss`, songs and videos only. */
    val duration: String?,
    val thumbnailUrl: String?,
    val seed: String,
    val explicit: Boolean,
)

private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

/** Pure: the entry of a YouTube Music result. */
internal fun auraSearchEntry(item: YTItem): AuraSearchEntry {
    val kind = auraContentKind(item)
    return when (item) {
        is SongItem -> AuraSearchEntry(
            kind = kind,
            title = item.title,
            artist = item.artists.joinToString { it.name }.blankToNull(),
            meta = item.album?.name.blankToNull(),
            duration = item.duration?.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
            thumbnailUrl = item.thumbnail,
            seed = item.id,
            explicit = item.explicit,
        )

        is AlbumItem -> AuraSearchEntry(
            kind = kind,
            title = item.title,
            artist = item.artists?.joinToString { it.name }.blankToNull(),
            meta = item.year?.toString(),
            duration = null,
            thumbnailUrl = item.thumbnail,
            seed = item.id,
            explicit = item.explicit,
        )

        is ArtistItem -> AuraSearchEntry(
            kind = kind,
            title = item.title,
            artist = null,
            meta = null,
            duration = null,
            thumbnailUrl = item.thumbnail,
            seed = item.id,
            explicit = false,
        )

        is PlaylistItem -> AuraSearchEntry(
            kind = kind,
            title = item.title,
            artist = item.author?.name.blankToNull(),
            meta = item.songCountText.blankToNull(),
            duration = null,
            thumbnailUrl = item.thumbnail,
            seed = item.id,
            explicit = false,
        )
    }
}

/** The entry of a library result. Composable only for the «N canciones» plural. */
@Composable
internal fun auraLocalSearchEntry(item: LocalItem): AuraSearchEntry {
    val kind = auraContentKind(item)
    return when (item) {
        is Song -> AuraSearchEntry(
            kind = kind,
            title = item.song.title,
            artist = item.artists.joinToString { it.name }.blankToNull(),
            meta = item.album?.title.blankToNull(),
            duration = item.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
            thumbnailUrl = item.song.thumbnailUrl,
            seed = item.id,
            explicit = item.song.explicit,
        )

        is Album -> AuraSearchEntry(
            kind = kind,
            title = item.album.title,
            artist = item.artists.joinToString { it.name }.blankToNull(),
            meta = listOfNotNull(
                item.album.year?.toString(),
                pluralStringResource(R.plurals.n_song, item.album.songCount, item.album.songCount),
            ).joinToString(" · "),
            duration = null,
            thumbnailUrl = item.album.thumbnailUrl,
            seed = item.id,
            explicit = item.album.explicit,
        )

        is Artist -> AuraSearchEntry(
            kind = kind,
            title = item.artist.name,
            artist = null,
            meta = pluralStringResource(R.plurals.n_song, item.songCount, item.songCount),
            duration = null,
            thumbnailUrl = item.artist.thumbnailUrl,
            seed = item.id,
            explicit = false,
        )

        is Playlist -> AuraSearchEntry(
            kind = kind,
            title = item.playlist.name,
            artist = null,
            meta = pluralStringResource(R.plurals.n_song, item.songCount, item.songCount),
            duration = null,
            thumbnailUrl = item.thumbnails.firstOrNull(),
            seed = item.id,
            explicit = false,
        )
    }
}

/** What the detail half is showing: a YouTube Music result or a library one. */
sealed interface AuraSearchPick {
    /** Stable identity: the highlight in the list and the reset of the detail's own state key off it. */
    val key: String

    data class Yt(val item: YTItem) : AuraSearchPick {
        override val key: String get() = "yt:${item.id}"
    }

    data class Local(val item: LocalItem) : AuraSearchPick {
        override val key: String get() = "local:${item.id}"
    }
}

internal fun AuraSearchPick?.isYt(item: YTItem): Boolean = this is AuraSearchPick.Yt && this.item.id == item.id

internal fun AuraSearchPick?.isLocal(item: LocalItem): Boolean = this is AuraSearchPick.Local && this.item.id == item.id

// ── Rows and cards ────────────────────────────────────────────────────────────────────────────────

/**
 * A wide result row: a 76 dp cover (128 × 72 for a video, a circle for an artist) beside the type pill, the title with
 * its «E», the artist and the album · year, and the duration at the end. [selected] is the row whose detail is open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AuraSearchWideRow(
    entry: AuraSearchEntry,
    selected: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** «Aleatorio mejorado» memory, as the phone row: a song already heard is dimmed and ticked (never the active one). */
    played: Boolean = false,
) {
    val visual = auraTypeVisual(entry.kind)
    val coverWidth = auraSearchRowCoverWidth(entry.kind)
    val playedCheck = played && !isActive
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(AuraShapes.Highlight)
            .background(if (selected) AuraPalette.Teal.copy(alpha = 0.14f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, AuraPalette.Teal.copy(alpha = 0.45f), AuraShapes.Highlight)
                else Modifier,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = entry.title,
                role = Role.Button,
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        AuraCover(
            thumbnailUrl = entry.thumbnailUrl,
            size = coverWidth,
            seed = entry.seed,
            shape = visual.shape,
            ratio = visual.ratio,
            decodeTo = 384,
            fillBleed = true,
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuraPalette.Ground.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { AuraPlayingBars(isPlaying = isPlaying) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AuraTypeBadge(entry.kind)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.title,
                    style = AuraType.RowTitle,
                    color = when {
                        isActive -> AuraPalette.Teal
                        playedCheck -> AuraPalette.OnGround.copy(alpha = 0.6f)
                        else -> AuraPalette.OnGround
                    },
                    maxLines = 2,
                    overflow = AuraDefaultOverflow,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.explicit) {
                    Spacer(Modifier.width(6.dp))
                    AuraExplicitBadge()
                }
            }
            entry.artist?.let {
                Text(
                    text = it,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            entry.meta?.let {
                Text(
                    text = it,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundFaint,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
        if (playedCheck) {
            AuraIconGlyph(
                icon = AuraIcons.Check,
                contentDescription = stringResource(R.string.aura_search_played),
                size = 16.dp,
                tint = AuraPalette.Teal,
            )
        }
        entry.duration?.let {
            AuraTechnicalText(text = it, color = AuraPalette.OnGroundFaint, style = AuraType.RowSubtitle)
        }
    }
}

/** [AuraSearchWideRow] for a YouTube Music result. */
@Composable
internal fun AuraYtWideRow(
    item: YTItem,
    selected: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val database = LocalDatabase.current
    val songFlow = remember(item.id) {
        if (item is SongItem) database.song(item.id) else flowOf(null)
    }
    val dbSong by songFlow.collectAsState(initial = null)
    AuraSearchWideRow(
        entry = remember(item) { auraSearchEntry(item) },
        selected = selected,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        played = item is SongItem && (dbSong?.song?.totalPlayTime ?: 0L) > 0L,
    )
}

/**
 * A grid cell of a filter in a wide window (Álbumes, Artistas, Playlists, Vídeos): the cover at the width of its cell with
 * the type pill on it, the title with its «E», and artist · year underneath. It is the app's own [AuraCoverCard], so the
 * cover edge, the playing marker and the radius are the ones every other shelf has.
 */
@Composable
internal fun AuraSearchGridCard(
    entry: AuraSearchEntry,
    width: Dp,
    selected: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val visual = auraTypeVisual(entry.kind)
    AuraCoverCard(
        title = entry.title,
        subtitle = listOfNotNull(entry.artist, entry.meta).joinToString(" · ").ifBlank { null },
        thumbnailUrl = entry.thumbnailUrl,
        seed = entry.seed,
        width = width,
        ratio = visual.ratio,
        shape = visual.shape,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        badge = { AuraTypeBadge(entry.kind, Modifier.align(Alignment.TopStart).padding(7.dp)) },
        explicit = entry.explicit,
        modifier = modifier.then(
            if (selected) Modifier.border(2.dp, AuraPalette.Teal.copy(alpha = 0.60f), AuraShapes.Highlight)
            else Modifier,
        ),
    )
}

// ── The split ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Results on the left, detail on the right. Only a layout: it measures nothing per frame and animates nothing; the caller
 * decides WHEN it applies ([auraSearchSplitFits] on the room it really has).
 *
 * @param endPadding air to the right of the detail card (the screens' gutter; the floating card has its own padding).
 * @param bottomPadding room kept under the detail card for the floating bottom bar, like the list's own content padding.
 */
@Composable
internal fun AuraSearchSplit(
    detailWidth: Dp,
    modifier: Modifier = Modifier,
    endPadding: Dp = AuraSpacing.Gutter,
    bottomPadding: Dp = 0.dp,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    Row(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { list() }
        Box(
            Modifier
                .width(detailWidth)
                .fillMaxHeight()
                .padding(start = 4.dp, end = endPadding, top = 4.dp, bottom = bottomPadding),
        ) { detail() }
    }
}

/** The card the detail sits in: the app's translucent surface with its hairline, 22 dp corners as the split player's cards. */
@Composable
internal fun AuraSearchDetailCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AuraSplitCardCorner)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, shape),
        content = content,
    )
}

/** The detail half before anything is picked. */
@Composable
internal fun AuraSearchDetailPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AuraIconGlyph(
            icon = AuraIcons.Search,
            contentDescription = null,
            size = 40.dp,
            tint = AuraPalette.OnGroundDisabled,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.aura_search_detail_empty_title),
            style = AuraType.RowTitle,
            color = AuraPalette.OnGroundMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.aura_search_detail_empty_body),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundGhost,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = AuraDefaultOverflow,
        )
    }
}
