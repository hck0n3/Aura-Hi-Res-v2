package iad1tya.echo.music.ui.newui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.ArtistSection
import com.music.innertube.pages.PlaylistPage
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.LocalItem
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.ShuffleContexts
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeAlbumRadio
import iad1tya.echo.music.playback.queues.YouTubePlaylistQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.shimmer.ShimmerHost
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.ArtistMenu
import iad1tya.echo.music.ui.menu.PlaylistMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.ui.utils.LocalContentPaneWidth
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.utils.LibraryPin
import iad1tya.echo.music.utils.LibraryPinKind
import iad1tya.echo.music.utils.LibraryPins
import iad1tya.echo.music.utils.LibraryPinsStore
import iad1tya.echo.music.utils.ShareLinks
import iad1tya.echo.music.utils.rememberLibraryPins
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * # The right half of a wide search: a master-detail panel that is USEFUL, not a pasted copy of the ⋯ menu
 *
 * Owner 2026-10-20 (unfolded Z Fold, quick search): the detail half was the floating menu embedded as it was — the artist's
 * name and avatar twice, «Iniciar radio / Compartir» buttons, «Suscrito / No recomendar / Anclar» rows — «no siento
 * utilidad… si toco un álbum, que pueda reproducirlo, añadir a la cola y lo que sea… y que pueda explorar todo el artista».
 *
 * What it is now, by what was tapped:
 *  · header — big cover (circle for an artist), the type pill, the title (with its «E»), subtitle. NOTHING is repeated below.
 *  · primary actions — equal-width icon tiles (Reproducir · Aleatorio · A continuación · A la cola · Radio · Suscribir),
 *    designed per kind, that wrap to a second row on a narrow half instead of truncating ([auraPaneTileColumns]).
 *  · «Más» — the secondary actions (Añadir a lista, Descargar, Compartir, Anclar, No recomendar) in one grouped list, and
 *    «Todas las opciones», which opens the FULL existing menu inside the pane (so nothing the ⋯ menu offers is lost).
 *  · content — an album / EP / single / playlist shows its track list (one page load, shimmer, error + retry); an artist
 *    shows its page (songs, albums, singles, videos…) with jump chips; tapping a release drills into ITS tracks inside the
 *    pane with a back affordance and a back stack; a song shows its album's tracks and chips to its album / artists.
 *
 * Data: `YouTube.album / artist / playlist` once per selection on IO, cancelled when the selection changes (LaunchedEffect
 * keyed on the page), a small in-memory cache ([AuraPaneCache]) so going back or re-selecting costs nothing. Nothing is
 * logged (titles and ids never reach AppLogger) and nothing runs per frame.
 */

// ── Pure pieces (a test pins them) ────────────────────────────────────────────────────────────────

/** Narrowest an action tile may be (dp) before the row wraps. */
internal const val AURA_PANE_TILE_MIN_WIDTH_DP = 72f

/** Tiles per row for [count] tiles in [widthDp]: as many as fit (at least 3), balanced over the rows they need. */
internal fun auraPaneTileColumns(widthDp: Float, count: Int): Int {
    if (count <= 0) return 1
    val fit = (widthDp / AURA_PANE_TILE_MIN_WIDTH_DP).toInt().coerceAtLeast(3)
    val rows = (count + fit - 1) / fit
    return (count + rows - 1) / rows
}

/** Lazy-list index where each section's header lands: a header plus [itemCounts] rows per section, from [firstIndex]. */
internal fun auraPaneSectionStarts(itemCounts: List<Int>, firstIndex: Int): List<Int> {
    var next = firstIndex
    return itemCounts.map { count ->
        val start = next
        next += 1 + count
        start
    }
}

/**
 * The queue for tapping row [start] of [items]: rows the source marks unavailable are dropped EXCEPT the tapped one, and the
 * returned index is where the tapped row ended up. Empty in, empty out.
 */
internal fun <T> auraPanePlayable(items: List<T>, start: Int, unavailable: (T) -> Boolean): Pair<List<T>, Int> {
    if (items.isEmpty()) return items to 0
    val tapped = start.coerceIn(0, items.lastIndex)
    val kept = items.filterIndexed { i, t -> i == tapped || !unavailable(t) }
    val index = items.take(tapped).count { !unavailable(it) }
    return kept to index
}

/** A bounded, expiring in-memory cache of loaded pages: the pane's «back» and a re-selection cost no network. */
internal class AuraPaneCache(
    private val maxEntries: Int,
    private val ttlMs: Long,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val map = object : LinkedHashMap<String, Pair<Long, Any>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, Any>>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(key: String): Any? {
        val entry = map[key] ?: return null
        if (clock() - entry.first > ttlMs) {
            map.remove(key)
            return null
        }
        return entry.second
    }

    @Synchronized
    fun put(key: String, value: Any) {
        map[key] = clock() to value
    }

    companion object {
        val shared = AuraPaneCache(maxEntries = 12, ttlMs = 5 * 60_000L)
    }
}

// ── Loading ───────────────────────────────────────────────────────────────────────────────────────

internal sealed interface AuraPaneLoad<out T> {
    data object Loading : AuraPaneLoad<Nothing>
    data object Failed : AuraPaneLoad<Nothing>
    data class Ready<T>(val value: T) : AuraPaneLoad<T>
}

private fun <T> AuraPaneLoad<T>.valueOrNull(): T? = (this as? AuraPaneLoad.Ready<T>)?.value

private class PaneLoadHandle<T>(val state: AuraPaneLoad<T>, val retry: () -> Unit)

/**
 * Loads one page for [key] on IO. The effect is keyed on [key] and the retry counter, so choosing another result (or going
 * back) CANCELS the request in flight; a cached page is shown at once. Failures are shown, never logged.
 */
@Composable
private fun <T : Any> rememberPaneLoad(key: String, load: suspend () -> Result<T>): PaneLoadHandle<T> {
    var attempt by remember(key) { mutableIntStateOf(0) }
    var state by remember(key) {
        @Suppress("UNCHECKED_CAST")
        val cached = AuraPaneCache.shared.get(key) as? T
        mutableStateOf<AuraPaneLoad<T>>(if (cached != null) AuraPaneLoad.Ready(cached) else AuraPaneLoad.Loading)
    }
    LaunchedEffect(key, attempt) {
        if (state is AuraPaneLoad.Ready) return@LaunchedEffect
        state = AuraPaneLoad.Loading
        val result = withContext(Dispatchers.IO) { load() }
        result
            .onSuccess {
                AuraPaneCache.shared.put(key, it)
                state = AuraPaneLoad.Ready(it)
            }
            .onFailure { state = AuraPaneLoad.Failed }
    }
    return PaneLoadHandle(state) { attempt++ }
}

// ── Model ─────────────────────────────────────────────────────────────────────────────────────────

/** One playable row, whether it came from YouTube Music or from the library. */
@Immutable
private class PaneTrack(
    val id: String,
    val title: String,
    val artist: String?,
    val durationSec: Int?,
    val explicit: Boolean,
    val yt: SongItem?,
    val local: Song?,
) {
    fun mediaItem(): MediaItem = yt?.toMediaItem() ?: local!!.toMediaItem()
}

private fun SongItem.toPaneTrack() = PaneTrack(
    id = id,
    title = title,
    artist = artists.joinToString { it.name }.takeIf { it.isNotBlank() },
    durationSec = duration?.takeIf { it > 0 },
    explicit = explicit,
    yt = this,
    local = null,
)

private fun Song.toPaneTrack() = PaneTrack(
    id = id,
    title = song.title,
    artist = artists.joinToString { it.name }.takeIf { it.isNotBlank() },
    durationSec = song.duration.takeIf { it > 0 },
    explicit = song.explicit,
    yt = null,
    local = this,
)

private sealed interface PaneRoute {
    val pick: AuraSearchPick
    val routeKey: String

    data class Entity(override val pick: AuraSearchPick) : PaneRoute {
        override val routeKey: String get() = pick.key
    }

    data class Options(override val pick: AuraSearchPick) : PaneRoute {
        override val routeKey: String get() = "options:${pick.key}"
    }
}

@Immutable
private class PaneTile(
    val icon: ImageVector,
    val label: String,
    val description: String = label,
    val primary: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Immutable
private class PaneMoreRow(
    val icon: ImageVector,
    val label: String,
    val detail: String? = null,
    val chevron: Boolean = false,
    val onClick: () -> Unit,
)

@Immutable
private class PaneChipData(val icon: ImageVector?, val label: String, val onClick: () -> Unit)

/** Everything an entity needs from the pane, so the entity composables stay about their own content. */
private class PaneEnv(
    val context: Context,
    val navController: NavController,
    val player: PlayerConnection,
    val database: MusicDatabase,
    val scope: CoroutineScope,
    val mediaId: String?,
    val albumId: String?,
    val isPlaying: Boolean,
    val isGuest: Boolean,
    val hideVideos: Boolean,
    val push: (AuraSearchPick) -> Unit,
    val showOptions: (AuraSearchPick) -> Unit,
    val go: (String) -> Unit,
    val openTrackMenu: (PaneTrack) -> Unit,
    val playLocalSong: ((Song) -> Unit)?,
) {
    fun toast(res: Int) = Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()

    fun share(text: String) {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** Plays [tracks] from [start] (unavailable rows skipped), or shuffled. */
    fun playList(title: String?, tracks: List<PaneTrack>, start: Int, contextId: String? = null, shuffled: Boolean = false) {
        val (kept, index) = auraPanePlayable(tracks, start) { it.yt?.unavailable == true }
        if (kept.isEmpty()) return
        val ordered = if (shuffled) kept.shuffled() else kept
        player.playQueue(
            ListQueue(
                title = title,
                items = ordered.map { it.mediaItem() },
                startIndex = if (shuffled) 0 else index,
                contextId = contextId,
                startShuffled = shuffled,
            ),
        )
    }

    fun playNext(tracks: List<PaneTrack>) {
        if (tracks.isEmpty()) return
        player.playNext(tracks.map { it.mediaItem() })
        toast(R.string.aura_pane_added_next)
    }

    fun addToQueue(tracks: List<PaneTrack>) {
        if (tracks.isEmpty()) return
        player.addToQueue(tracks.map { it.mediaItem() })
        toast(R.string.aura_pane_added_queue)
    }
}

private fun ytAlbumPick(id: String, title: String, thumbnail: String?, artists: List<com.music.innertube.models.Artist>?) =
    AuraSearchPick.Yt(
        AlbumItem(browseId = id, playlistId = "", title = title, artists = artists, thumbnail = thumbnail.orEmpty()),
    )

private fun ytArtistPick(id: String, name: String, thumbnail: String?) =
    AuraSearchPick.Yt(ArtistItem(id = id, title = name, thumbnail = thumbnail, shuffleEndpoint = null, radioEndpoint = null))

private fun AuraSearchPick.titleText(): String = when (this) {
    is AuraSearchPick.Yt -> item.title
    is AuraSearchPick.Local -> item.title
}

// ── Building blocks ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaneBackBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 14.dp),
    ) {
        AuraIconButton(
            icon = AuraIcons.ChevronRight,
            contentDescription = stringResource(R.string.back_button_desc),
            onClick = onBack,
            tint = AuraPalette.OnGround,
            modifier = Modifier.graphicsLayer { rotationZ = 180f },
        )
        Text(
            text = title,
            style = AuraType.RowTitle,
            color = AuraPalette.OnGround,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp),
        )
    }
}

/** Cover (circle for an artist), type pill, title with its «E», and the subtitle lines — shown ONCE. */
@Composable
private fun PaneHeader(entry: AuraSearchEntry, lines: List<String>) {
    val visual = auraTypeVisual(entry.kind)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fraction = if (entry.kind == AuraContentKind.Video) 0.50f else 0.36f
        val cover = (this@BoxWithConstraints.maxWidth * fraction).coerceIn(104.dp, 170.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        ) {
            AuraCover(
                thumbnailUrl = entry.thumbnailUrl,
                size = cover,
                seed = entry.seed,
                shape = visual.shape,
                ratio = visual.ratio,
                decodeTo = 512,
                fillBleed = true,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AuraTypeBadge(entry.kind)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.title,
                        style = AuraType.PlayerTitle,
                        color = AuraPalette.OnGround,
                        maxLines = 3,
                        overflow = AuraDefaultOverflow,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.explicit) {
                        Spacer(Modifier.padding(start = 6.dp))
                        AuraExplicitBadge()
                    }
                }
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                        maxLines = 2,
                        overflow = AuraDefaultOverflow,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaneTileButton(tile: PaneTile, modifier: Modifier = Modifier) {
    val ink = if (tile.primary) AuraPalette.OnAccent else AuraPalette.OnGround
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(AuraShapes.Card)
            .background(if (tile.primary) AuraPalette.Teal else AuraPalette.SurfaceFill)
            .then(if (tile.primary) Modifier else Modifier.border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card))
            .alpha(if (tile.enabled) 1f else 0.4f)
            .auraClickableInternal(onClick = tile.onClick, enabled = tile.enabled, contentDescription = tile.description)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        AuraIconGlyph(icon = tile.icon, contentDescription = null, size = 22.dp, tint = ink)
        Spacer(Modifier.height(4.dp))
        // Two lines allowed: a label wraps, it is never cut.
        Text(
            text = tile.label,
            style = AuraType.NavLabel,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun PaneTiles(tiles: List<PaneTile>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = auraPaneTileColumns(this@BoxWithConstraints.maxWidth.value, tiles.size)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTiles.forEach { PaneTileButton(it, Modifier.weight(1f)) }
                    repeat(columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PaneMoreGroup(rows: List<PaneMoreRow>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) AuraDivider(Modifier.padding(start = 48.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AuraSpacing.MinTouchTarget)
                    .auraClickableInternal(onClick = row.onClick, contentDescription = row.label)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                AuraIconGlyph(icon = row.icon, contentDescription = null, size = 20.dp, tint = AuraPalette.OnGroundMuted)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.label,
                        style = AuraType.MenuLabel,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                    row.detail?.let {
                        Text(
                            text = it,
                            style = AuraType.RowSubtitle,
                            color = AuraPalette.OnGroundFaint,
                            maxLines = 1,
                            overflow = AuraDefaultOverflow,
                        )
                    }
                }
                if (row.chevron) {
                    AuraIconGlyph(icon = AuraIcons.ChevronRight, contentDescription = null, size = 16.dp, tint = AuraPalette.OnGroundDisabled)
                }
            }
        }
    }
}

@Composable
private fun PaneChip(chip: PaneChipData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(AuraShapes.Pill)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Pill)
            .auraClickableInternal(onClick = chip.onClick, contentDescription = chip.label)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        chip.icon?.let { AuraIconGlyph(icon = it, contentDescription = null, size = 16.dp, tint = AuraPalette.Teal) }
        Text(
            text = chip.label,
            style = AuraType.Chip,
            color = AuraPalette.OnGround,
            maxLines = 1,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.widthIn(max = 200.dp),
        )
    }
}

/**
 * Header · action tiles (+ the «Más» group) · «open the full page» · chips · then the entity's [content]. One lazy list per
 * route, with a scroll state the pane keeps per route, so going back lands where the user was.
 *
 * Fixed items before [content]: header, actions, and the chips row when there are chips ([PANE_FIXED_ITEMS]).
 */
@Composable
private fun PaneScaffold(
    listState: LazyListState,
    entry: AuraSearchEntry,
    lines: List<String>,
    tiles: List<PaneTile>,
    more: List<PaneMoreRow>,
    chips: List<PaneChipData>,
    open: PaneMoreRow?,
    content: LazyListScope.() -> Unit,
) {
    var moreOpen by remember { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.aura_pane_more)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "pane_header", contentType = "pane_header") { PaneHeader(entry, lines) }
        item(key = "pane_actions", contentType = "pane_actions") {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                val all = if (more.isEmpty()) {
                    tiles
                } else {
                    tiles + PaneTile(icon = AuraIcons.More, label = moreLabel) { moreOpen = !moreOpen }
                }
                PaneTiles(all)
                AnimatedVisibility(visible = moreOpen) { PaneMoreGroup(more) }
                open?.let { PaneMoreGroup(listOf(it)) }
            }
        }
        if (chips.isNotEmpty()) {
            item(key = "pane_chips", contentType = "pane_chips") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    chips.forEach { PaneChip(it) }
                }
            }
        }
        content()
    }
}

private fun paneFixedItems(hasChips: Boolean): Int = if (hasChips) 3 else 2

private fun LazyListScope.paneTrackItems(
    keyPrefix: String,
    env: PaneEnv,
    tracks: List<PaneTrack>,
    onClick: (Int, PaneTrack) -> Unit,
) {
    itemsIndexed(tracks, key = { i, _ -> "$keyPrefix$i" }, contentType = { _, _ -> "pane_track" }) { index, track ->
        AuraAlbumTrackRow(
            trackNumber = index + 1,
            title = track.title,
            subtitle = track.artist,
            isActive = track.id == env.mediaId,
            isPlaying = env.isPlaying,
            explicit = track.explicit,
            downloadId = track.id,
            durationSeconds = track.durationSec,
            onClick = {
                if (track.id == env.mediaId) env.player.togglePlayPause() else onClick(index, track)
            },
            onMenuClick = { env.openTrackMenu(track) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private fun <T> LazyListScope.paneLoadItems(
    state: AuraPaneLoad<T>,
    errorText: String,
    onRetry: () -> Unit,
    ready: LazyListScope.(T) -> Unit,
) {
    when (state) {
        is AuraPaneLoad.Loading -> item(key = "pane_loading", contentType = "pane_loading") {
            ShimmerHost { repeat(6) { AuraDetailSkeletonRow() } }
        }

        is AuraPaneLoad.Failed -> item(key = "pane_error", contentType = "pane_error") {
            AuraDetailErrorState(message = errorText, onRetry = onRetry)
        }

        is AuraPaneLoad.Ready -> ready(state.value)
    }
}

// ── Tiles and rows shared by the entities ─────────────────────────────────────────────────────────

@Composable
private fun playTile(env: PaneEnv, active: Boolean, enabled: Boolean = true, onPlay: () -> Unit): PaneTile {
    val playing = active && env.isPlaying
    val label = stringResource(if (playing) R.string.pause else R.string.play)
    return PaneTile(
        icon = if (playing) AuraIcons.Pause else AuraIcons.Play,
        label = label,
        primary = true,
        enabled = enabled,
        onClick = { if (active) env.player.togglePlayPause() else onPlay() },
    )
}

@Composable
private fun shuffleTile(enabled: Boolean, onClick: () -> Unit) =
    PaneTile(icon = AuraIcons.Shuffle, label = stringResource(R.string.shuffle), enabled = enabled, onClick = onClick)

@Composable
private fun radioTile(onClick: () -> Unit) = PaneTile(
    icon = AuraIcons.Radio,
    label = stringResource(R.string.aura_pane_radio),
    description = stringResource(R.string.start_radio),
    onClick = onClick,
)

@Composable
private fun nextTile(enabled: Boolean, onClick: () -> Unit) = PaneTile(
    icon = AuraIcons.SkipNext,
    label = stringResource(R.string.aura_pane_next),
    description = stringResource(R.string.play_next),
    enabled = enabled,
    onClick = onClick,
)

@Composable
private fun queueTile(enabled: Boolean, onClick: () -> Unit) = PaneTile(
    icon = AuraIcons.Queue,
    label = stringResource(R.string.aura_pane_queue),
    description = stringResource(R.string.add_to_queue),
    enabled = enabled,
    onClick = onClick,
)

@Composable
private fun pinRow(env: PaneEnv, pin: LibraryPin): PaneMoreRow {
    val pins by rememberLibraryPins()
    val pinned = LibraryPins.isPinned(pins, pin.kind, pin.id)
    val full = !pinned && !LibraryPins.canAdd(pins)
    val label = stringResource(if (pinned) R.string.library_unpin else R.string.library_pin)
    val toast = when {
        full -> stringResource(R.string.library_pins_full, LibraryPins.MAX_PINS)
        pinned -> stringResource(R.string.library_unpinned_toast)
        else -> stringResource(R.string.library_pinned_toast)
    }
    return PaneMoreRow(icon = AuraIcons.Pin, label = label) {
        if (!full) LibraryPinsStore.updateDetached(env.context) { current -> LibraryPins.toggled(current, pin) }
        Toast.makeText(env.context, toast, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun allOptionsRow(env: PaneEnv, pick: AuraSearchPick) = PaneMoreRow(
    icon = AuraIcons.More,
    label = stringResource(R.string.aura_pane_all_options),
    chevron = true,
) { env.showOptions(pick) }

@Composable
private fun PaneAddToPlaylist(visible: Boolean, songIds: List<String>?, onGetSongs: suspend () -> List<String>, onDismiss: () -> Unit) {
    AddToPlaylistDialog(
        isVisible = visible,
        songIdsForMembership = songIds,
        onGetSong = { onGetSongs() },
        onDismiss = onDismiss,
    )
}

private fun artistChips(env: PaneEnv, artists: List<com.music.innertube.models.Artist>, icon: ImageVector): List<PaneChipData> =
    artists.filter { it.id != null }.distinctBy { it.id }.take(3).map { artist ->
        PaneChipData(icon, artist.name) { env.push(ytArtistPick(artist.id!!, artist.name, null)) }
    }

// ── YouTube Music: a song or a video ──────────────────────────────────────────────────────────────

@Composable
private fun PaneYtSong(env: PaneEnv, pick: AuraSearchPick, item: SongItem, listState: LazyListState) {
    val entry = remember(item) { auraSearchEntry(item) }
    var showAdd by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    val download by LocalDownloadUtil.current.getDownload(item.id).collectAsState(initial = null)
    val album = item.album
    val albumLoad = album?.let { a -> rememberPaneLoad("album:${a.id}") { YouTube.album(a.id) } }
    val albumPage = albumLoad?.state?.valueOrNull()
    val albumTracks = remember(albumPage) { albumPage?.songs.orEmpty().map { it.toPaneTrack() } }
    val track = remember(item) { listOf(item.toPaneTrack()) }

    val tiles = buildList {
        add(playTile(env, active = env.mediaId == item.id) {
            env.player.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
        })
        if (!env.isGuest) {
            add(radioTile { env.player.playQueue(YouTubeQueue.radio(item.toMediaMetadata())) })
            add(nextTile(true) {
                env.player.playNext(item.copy(thumbnail = item.thumbnail.resize(1200, 1200)).toMediaItem())
                env.toast(R.string.aura_pane_added_next)
            })
            add(queueTile(true) { env.addToQueue(track) })
        }
    }
    val more = listOf(
        PaneMoreRow(AuraIcons.PlaylistAdd, stringResource(R.string.add_to_playlist)) { showAdd = true },
        PaneMoreRow(AuraIcons.Download, stringResource(R.string.action_download)) { showDownload = true },
        PaneMoreRow(AuraIcons.Share, stringResource(R.string.share)) {
            env.share(ShareLinks.songShareText(item.id, item.title, item.artists.map { it.name }))
        },
        allOptionsRow(env, pick),
    )
    val chips = buildList {
        album?.let { a ->
            add(PaneChipData(AuraIcons.Album, a.name) { env.push(ytAlbumPick(a.id, a.name, item.thumbnail, item.artists)) })
        }
        addAll(artistChips(env, item.artists, AuraIcons.Artist))
    }
    val errorText = stringResource(R.string.aura_pane_load_failed)
    val fromAlbum = stringResource(R.string.aura_pane_from_album)

    Box(Modifier.fillMaxSize()) {
        PaneScaffold(
            listState = listState,
            entry = entry,
            lines = listOfNotNull(entry.artist, entry.duration),
            tiles = tiles,
            more = more,
            chips = chips,
            open = null,
        ) {
            if (albumLoad != null) {
                item(key = "pane_from_album", contentType = "pane_header_label") { AuraSectionHeader(title = fromAlbum) }
                paneLoadItems(albumLoad.state, errorText, albumLoad.retry) { _ ->
                    paneTrackItems("pane_t_", env, albumTracks) { index, _ ->
                        env.playList(albumPage?.album?.title, albumTracks, index, ShuffleContexts.album(album.id))
                    }
                }
            }
        }
        PaneAddToPlaylist(
            visible = showAdd,
            songIds = listOf(item.id),
            onGetSongs = {
                env.database.withTransaction { insert(item.toMediaMetadata()) }
                listOf(item.id)
            },
            onDismiss = { showAdd = false },
        )
        if (showDownload) {
            AuraDownloadSheet(
                songs = listOf(item.toAuraDownloadItem().copy(metadata = item.toMediaMetadata())),
                title = item.title,
                offlineState = download?.state ?: Download.STATE_STOPPED,
                onDismiss = { showDownload = false },
            )
        }
    }
}

// ── YouTube Music: an album, EP or single ─────────────────────────────────────────────────────────

@Composable
private fun PaneYtAlbum(env: PaneEnv, pick: AuraSearchPick, item: AlbumItem, listState: LazyListState) {
    val load = rememberPaneLoad("album:${item.id}") { YouTube.album(item.id) }
    val page = load.state.valueOrNull()
    val album = page?.album ?: item
    val tracks = remember(page) { page?.songs.orEmpty().map { it.toPaneTrack() } }
    val playlistId = album.playlistId.ifBlank { item.playlistId }
    val contextId = ShuffleContexts.album(album.id)
    var showAdd by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }

    val entry = remember(album) { auraSearchEntry(album) }
    val countLine = if (tracks.isNotEmpty()) pluralStringResource(R.plurals.n_song, tracks.size, tracks.size) else null
    val lines = listOfNotNull(
        entry.artist,
        listOfNotNull(album.year?.toString(), countLine).joinToString(" · ").takeIf { it.isNotBlank() },
    )
    val tiles = buildList {
        add(playTile(env, active = env.albumId == album.id, enabled = tracks.isNotEmpty() || playlistId.isNotBlank()) {
            if (playlistId.isNotBlank()) {
                env.player.playQueue(YouTubeAlbumRadio(playlistId, contextId = contextId))
            } else {
                env.playList(album.title, tracks, 0, contextId)
            }
        })
        add(shuffleTile(tracks.isNotEmpty()) {
            if (playlistId.isNotBlank()) env.player.service.getAutomix(playlistId)
            env.playList(album.title, tracks, 0, shuffled = true)
        })
        if (!env.isGuest) {
            add(nextTile(tracks.isNotEmpty()) { env.playNext(tracks) })
            add(queueTile(tracks.isNotEmpty()) { env.addToQueue(tracks) })
        }
    }
    val dislikeDone = stringResource(R.string.aura_pane_dislike_done)
    val more = listOf(
        PaneMoreRow(AuraIcons.PlaylistAdd, stringResource(R.string.add_to_playlist)) { if (tracks.isNotEmpty()) showAdd = true },
        PaneMoreRow(AuraIcons.Download, stringResource(R.string.action_download)) { if (tracks.isNotEmpty()) showDownload = true },
        PaneMoreRow(AuraIcons.Share, stringResource(R.string.share)) {
            env.share(ShareLinks.albumShareText(playlistId, album.browseId, album.title, album.artists.orEmpty().map { it.name }))
        },
        pinRow(
            env,
            LibraryPin(
                LibraryPinKind.ALBUM,
                album.id,
                album.title,
                subtitle = entry.artist,
                thumbnailUrl = album.thumbnail,
                extra = playlistId.takeIf { it.isNotBlank() },
            ),
        ),
        PaneMoreRow(AuraIcons.ThumbDown, stringResource(R.string.do_not_recommend), stringResource(R.string.do_not_recommend_album_desc)) {
            env.scope.launch { iad1tya.echo.music.dislike.DislikeStoreEntryPoint.get(env.context).dislikeAlbum(album.id) }
            Toast.makeText(env.context, dislikeDone, Toast.LENGTH_SHORT).show()
        },
        allOptionsRow(env, pick),
    )
    val chips = artistChips(env, album.artists.orEmpty(), AuraIcons.Artist)
    val open = PaneMoreRow(AuraIcons.Album, stringResource(R.string.aura_pane_open_album), chevron = true) {
        env.go("album/${album.id}")
    }
    val errorText = stringResource(R.string.aura_pane_load_failed)

    Box(Modifier.fillMaxSize()) {
        PaneScaffold(listState, entry, lines, tiles, more, chips, open) {
            paneLoadItems(load.state, errorText, load.retry) { _ ->
                paneTrackItems("pane_t_", env, tracks) { index, _ -> env.playList(album.title, tracks, index, contextId) }
            }
        }
        PaneAddToPlaylist(
            visible = showAdd,
            songIds = tracks.map { it.id },
            onGetSongs = {
                page?.let { p -> env.database.withTransaction { insert(p) } }
                tracks.map { it.id }
            },
            onDismiss = { showAdd = false },
        )
        if (showDownload) {
            AuraDownloadSheet(
                songs = tracks.mapNotNull { t -> t.yt?.let { it.toAuraDownloadItem().copy(metadata = it.toMediaMetadata()) } },
                title = album.title,
                offlineState = Download.STATE_STOPPED,
                onDismiss = { showDownload = false },
            )
        }
    }
}

// ── YouTube Music: a playlist ─────────────────────────────────────────────────────────────────────

@Composable
private fun PaneYtPlaylist(env: PaneEnv, pick: AuraSearchPick, item: PlaylistItem, listState: LazyListState) {
    val load = rememberPaneLoad("playlist:${item.id}") { YouTube.playlist(item.id) }
    val page = load.state.valueOrNull()
    val playlist = page?.playlist ?: item
    val songs = page?.songs.orEmpty()
    val tracks = remember(page) { songs.map { it.toPaneTrack() } }
    var showAdd by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }

    val entry = remember(playlist) { auraSearchEntry(playlist) }
    val lines = listOfNotNull(entry.artist, entry.meta ?: if (tracks.isNotEmpty()) pluralStringResource(R.plurals.n_song, tracks.size, tracks.size) else null)

    fun queueOf(initial: List<SongItem>, startIndex: Int, shuffled: Boolean) = YouTubePlaylistQueue(
        playlistId = playlist.id,
        playlistTitle = playlist.title,
        initialSongs = initial,
        initialContinuation = page?.songsContinuation,
        startIndex = startIndex,
        startShuffled = shuffled,
    )

    val tiles = buildList {
        add(playTile(env, active = false, enabled = songs.isNotEmpty() || item.playEndpoint != null) {
            if (songs.isNotEmpty()) {
                env.player.playQueue(queueOf(songs, 0, false))
            } else {
                item.playEndpoint?.let { env.player.playQueue(YouTubeQueue(it)) }
            }
        })
        add(shuffleTile(songs.isNotEmpty() || item.shuffleEndpoint != null) {
            if (songs.isNotEmpty()) {
                env.player.playQueue(queueOf(songs.shuffled(), 0, true))
            } else {
                item.shuffleEndpoint?.let { env.player.playQueue(YouTubeQueue(it)) }
            }
        })
        if (!env.isGuest) {
            add(nextTile(tracks.isNotEmpty()) { env.playNext(tracks) })
            add(queueTile(tracks.isNotEmpty()) { env.addToQueue(tracks) })
        }
    }
    val more = listOf(
        PaneMoreRow(AuraIcons.PlaylistAdd, stringResource(R.string.add_to_playlist)) { if (tracks.isNotEmpty()) showAdd = true },
        PaneMoreRow(AuraIcons.Download, stringResource(R.string.action_download)) { if (tracks.isNotEmpty()) showDownload = true },
        PaneMoreRow(AuraIcons.Share, stringResource(R.string.share)) {
            env.share(ShareLinks.playlistShareText(playlist.id, playlist.title))
        },
        pinRow(
            env,
            LibraryPin(
                LibraryPinKind.PLAYLIST,
                playlist.id,
                playlist.title,
                subtitle = entry.artist,
                thumbnailUrl = playlist.thumbnail,
                local = false,
            ),
        ),
        allOptionsRow(env, pick),
    )
    val open = PaneMoreRow(AuraIcons.Queue, stringResource(R.string.aura_pane_open_playlist), chevron = true) {
        env.go("online_playlist/${playlist.id}")
    }
    val errorText = stringResource(R.string.aura_pane_load_failed)

    Box(Modifier.fillMaxSize()) {
        PaneScaffold(listState, entry, lines, tiles, more, emptyList(), open) {
            paneLoadItems(load.state, errorText, load.retry) { _ ->
                paneTrackItems("pane_t_", env, tracks) { index, _ ->
                    env.player.playQueue(queueOf(songs, index, false))
                }
            }
        }
        PaneAddToPlaylist(
            visible = showAdd,
            songIds = tracks.map { it.id },
            onGetSongs = {
                env.database.withTransaction { songs.forEach { insert(it.toMediaMetadata()) } }
                tracks.map { it.id }
            },
            onDismiss = { showAdd = false },
        )
        if (showDownload) {
            AuraDownloadSheet(
                songs = songs.map { it.toAuraDownloadItem().copy(metadata = it.toMediaMetadata()) },
                title = playlist.title,
                offlineState = Download.STATE_STOPPED,
                onDismiss = { showDownload = false },
            )
        }
    }
}

// ── YouTube Music: an artist, explored inside the pane ────────────────────────────────────────────

private fun ArtistSection.isSongList(): Boolean =
    items.isNotEmpty() && items.all { it is SongItem } && items.none { (it as SongItem).isVideoSong }

@Composable
private fun PaneYtArtist(env: PaneEnv, pick: AuraSearchPick, item: ArtistItem, listState: LazyListState) {
    val load = rememberPaneLoad("artist:${item.id}") { YouTube.artist(item.id) }
    val page: ArtistPage? = load.state.valueOrNull()
    val artist = page?.artist ?: item
    val libraryArtist by env.database.artist(artist.id).collectAsState(initial = null)
    val subscribed = libraryArtist?.artist?.followedByUserAt != null
    val sections = remember(page, env.hideVideos) {
        page?.sections.orEmpty()
            .map { s -> s.copy(items = s.items.distinctBy { it.id }.filterNot { env.hideVideos && it is SongItem && it.isVideoSong }) }
            .filter { it.items.isNotEmpty() }
    }
    val topSongs = remember(sections) {
        sections.firstOrNull { it.isSongList() }?.items.orEmpty().filterIsInstance<SongItem>().map { it.toPaneTrack() }
    }
    val entry = remember(artist) { auraSearchEntry(artist) }
    val listeners = page?.monthlyListenerCount?.takeIf { it.isNotBlank() }?.let { "$it ${stringResource(R.string.monthly_listeners)}" }
    val subscribers = page?.subscriberCountText?.takeIf { it.isNotBlank() }?.let { "$it ${stringResource(R.string.subscribers)}" }
    val lines = listOfNotNull(listeners ?: subscribers)
    var showMenuUnused by remember { mutableStateOf(false) }
    showMenuUnused = false

    val playEndpoint = artist.playEndpoint ?: item.playEndpoint ?: artist.radioEndpoint ?: item.radioEndpoint
    val shuffleEndpoint = artist.shuffleEndpoint ?: item.shuffleEndpoint
    val radioEndpoint = artist.radioEndpoint ?: item.radioEndpoint
    val subscribeLabel = stringResource(if (subscribed) R.string.subscribed else R.string.subscribe)
    val dislikeDone = stringResource(R.string.aura_pane_dislike_done)

    val tiles = buildList {
        add(playTile(env, active = false, enabled = topSongs.isNotEmpty() || playEndpoint != null) {
            if (topSongs.isNotEmpty()) env.playList(artist.title, topSongs, 0)
            else playEndpoint?.let { env.player.playQueue(YouTubeQueue(it)) }
        })
        add(shuffleTile(shuffleEndpoint != null || topSongs.isNotEmpty()) {
            if (shuffleEndpoint != null) env.player.playQueue(YouTubeQueue(shuffleEndpoint))
            else env.playList(artist.title, topSongs, 0, shuffled = true)
        })
        if (!env.isGuest && radioEndpoint != null) add(radioTile { env.player.playQueue(YouTubeQueue(radioEndpoint)) })
        add(
            PaneTile(
                icon = if (subscribed) AuraIcons.Check else AuraIcons.Plus,
                label = subscribeLabel,
            ) {
                env.database.query {
                    val current = libraryArtist
                    if (current != null) {
                        update(current.artist.toggleLike(env.database::confirmArtistUnsubscribed))
                    } else {
                        insert(
                            ArtistEntity(
                                id = artist.id,
                                name = artist.title,
                                channelId = artist.channelId,
                                thumbnailUrl = artist.thumbnail,
                            ).toggleLike(env.database::confirmArtistUnsubscribed),
                        )
                    }
                }
            },
        )
    }
    val more = listOf(
        PaneMoreRow(AuraIcons.Share, stringResource(R.string.share)) {
            env.share(ShareLinks.artistShareText(artist.id, artist.title))
        },
        pinRow(env, LibraryPin(LibraryPinKind.ARTIST, artist.id, artist.title, thumbnailUrl = artist.thumbnail)),
        PaneMoreRow(AuraIcons.ThumbDown, stringResource(R.string.do_not_recommend), stringResource(R.string.do_not_recommend_artist_desc)) {
            env.scope.launch { iad1tya.echo.music.dislike.DislikeStoreEntryPoint.get(env.context).dislikeArtist(artist.id) }
            Toast.makeText(env.context, dislikeDone, Toast.LENGTH_SHORT).show()
        },
        allOptionsRow(env, pick),
    )
    val open = PaneMoreRow(AuraIcons.Artist, stringResource(R.string.aura_pane_open_artist), chevron = true) {
        env.go("artist/${artist.id}")
    }

    val fixed = paneFixedItems(hasChips = sections.isNotEmpty())
    val starts = remember(sections) {
        auraPaneSectionStarts(sections.map { if (it.isSongList()) it.items.size else 1 }, fixed)
    }
    val chips = sections.mapIndexed { index, section ->
        PaneChipData(null, section.title) { env.scope.launch { listState.animateScrollToItem(starts[index]) } }
    }
    val errorText = stringResource(R.string.aura_pane_load_failed)

    PaneScaffold(listState, entry, lines, tiles, more, chips, open) {
        paneLoadItems(load.state, errorText, load.retry) { _ ->
            sections.forEachIndexed { sectionIndex, section ->
                item(key = "pane_sec_h_$sectionIndex", contentType = "pane_sec_h") {
                    val moreEndpoint = section.moreEndpoint
                    AuraSectionHeader(
                        title = section.title,
                        onClick = moreEndpoint?.let { more ->
                            {
                                env.go("artist/${artist.id}/items?browseId=${more.browseId}&params=${more.params}")
                            }
                        },
                    )
                }
                if (section.isSongList()) {
                    val sectionTracks = section.items.filterIsInstance<SongItem>().map { it.toPaneTrack() }
                    paneTrackItems("pane_sec_${sectionIndex}_", env, sectionTracks) { index, _ ->
                        env.playList(section.title, sectionTracks, index)
                    }
                } else {
                    item(key = "pane_sec_row_$sectionIndex", contentType = "pane_sec_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(section.items, key = { it.id }) { shelfItem ->
                                AuraTypedYtCoverCard(
                                    item = shelfItem,
                                    cardScale = 0.8f,
                                    isActive = when (shelfItem) {
                                        is SongItem -> env.mediaId == shelfItem.id
                                        is AlbumItem -> env.albumId == shelfItem.id
                                        else -> false
                                    },
                                    isPlaying = env.isPlaying,
                                    onClick = {
                                        when (shelfItem) {
                                            is SongItem -> {
                                                val songs = section.items.filterIsInstance<SongItem>()
                                                if (shelfItem.id == env.mediaId) {
                                                    env.player.togglePlayPause()
                                                } else {
                                                    env.playList(
                                                        section.title,
                                                        songs.map { it.toPaneTrack() },
                                                        songs.indexOfFirst { it.id == shelfItem.id },
                                                    )
                                                }
                                            }

                                            else -> env.push(AuraSearchPick.Yt(shelfItem))
                                        }
                                    },
                                    onLongClick = if (shelfItem is SongItem) {
                                        { env.openTrackMenu(shelfItem.toPaneTrack()) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── The library ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaneLocalSong(env: PaneEnv, pick: AuraSearchPick, item: Song, listState: LazyListState) {
    val entry = auraLocalSearchEntry(item)
    var showAdd by remember { mutableStateOf(false) }
    val track = remember(item) { listOf(item.toPaneTrack()) }
    val tiles = buildList {
        add(playTile(env, active = env.mediaId == item.id) {
            val custom = env.playLocalSong
            if (custom != null) custom(item) else env.playList(item.song.title, track, 0)
        })
        if (!env.isGuest) {
            add(nextTile(true) { env.playNext(track) })
            add(queueTile(true) { env.addToQueue(track) })
        }
    }
    val more = listOf(
        PaneMoreRow(AuraIcons.PlaylistAdd, stringResource(R.string.add_to_playlist)) { showAdd = true },
        allOptionsRow(env, pick),
    )
    val chips = buildList {
        item.album?.let { a -> add(PaneChipData(AuraIcons.Album, a.title) { env.go("album/${a.id}") }) }
        item.artists.take(3).forEach { a -> add(PaneChipData(AuraIcons.Artist, a.name) { env.go("artist/${a.id}") }) }
    }
    Box(Modifier.fillMaxSize()) {
        PaneScaffold(listState, entry, listOfNotNull(entry.artist, entry.duration), tiles, more, chips, null) {}
        PaneAddToPlaylist(
            visible = showAdd,
            songIds = listOf(item.id),
            onGetSongs = { listOf(item.id) },
            onDismiss = { showAdd = false },
        )
    }
}

/** A library album, playlist or artist: its songs come from the database, so there is nothing to load. */
@Composable
private fun PaneLocalCollection(
    env: PaneEnv,
    pick: AuraSearchPick,
    entry: AuraSearchEntry,
    tracks: List<PaneTrack>?,
    contextId: String?,
    openLabel: String,
    openRoute: String,
    listState: LazyListState,
) {
    val songs = tracks.orEmpty()
    val tiles = buildList {
        add(playTile(env, active = false, enabled = songs.isNotEmpty()) { env.playList(entry.title, songs, 0, contextId) })
        add(shuffleTile(songs.isNotEmpty()) { env.playList(entry.title, songs, 0, shuffled = true) })
        if (!env.isGuest) {
            add(nextTile(songs.isNotEmpty()) { env.playNext(songs) })
            add(queueTile(songs.isNotEmpty()) { env.addToQueue(songs) })
        }
    }
    val open = PaneMoreRow(AuraIcons.Album, openLabel, chevron = true) { env.go(openRoute) }
    val state: AuraPaneLoad<List<PaneTrack>> = if (tracks == null) AuraPaneLoad.Loading else AuraPaneLoad.Ready(tracks)
    val errorText = stringResource(R.string.aura_pane_load_failed)
    PaneScaffold(
        listState = listState,
        entry = entry,
        lines = listOfNotNull(entry.artist, entry.meta),
        tiles = tiles,
        more = listOf(allOptionsRow(env, pick)),
        chips = emptyList(),
        open = open,
    ) {
        paneLoadItems(state, errorText, {}) { list ->
            paneTrackItems("pane_t_", env, list) { index, _ -> env.playList(entry.title, list, index, contextId) }
        }
    }
}

@Composable
private fun PaneLocalAlbum(env: PaneEnv, pick: AuraSearchPick, item: Album, listState: LazyListState) {
    val data by remember(item.id) { env.database.albumWithSongs(item.id) }.collectAsState(initial = null)
    val tracks = remember(data) { data?.songs?.map { it.toPaneTrack() } }
    PaneLocalCollection(
        env, pick, auraLocalSearchEntry(item), tracks, ShuffleContexts.album(item.id),
        stringResource(R.string.aura_pane_open_album), "album/${item.id}", listState,
    )
}

@Composable
private fun PaneLocalPlaylist(env: PaneEnv, pick: AuraSearchPick, item: Playlist, listState: LazyListState) {
    val data by remember(item.id) { env.database.playlistSongs(item.id) }.collectAsState(initial = null)
    val tracks = remember(data) { data?.map { it.song.toPaneTrack() } }
    PaneLocalCollection(
        env, pick, auraLocalSearchEntry(item), tracks, null,
        stringResource(R.string.aura_pane_open_playlist), "local_playlist/${item.id}", listState,
    )
}

@Composable
private fun PaneLocalArtist(env: PaneEnv, pick: AuraSearchPick, item: Artist, listState: LazyListState) {
    val data by remember(item.id) { env.database.artistSongsByNameAsc(item.id) }.collectAsState(initial = null)
    val tracks = remember(data) { data?.map { it.toPaneTrack() } }
    PaneLocalCollection(
        env, pick, auraLocalSearchEntry(item), tracks, null,
        stringResource(R.string.aura_pane_open_artist), "artist/${item.id}", listState,
    )
}

// ── «Todas las opciones»: the full existing menu, inside the pane ─────────────────────────────────

@Composable
private fun PaneOptionsPage(env: PaneEnv, pick: AuraSearchPick, onBack: () -> Unit, onMenuDismiss: () -> Unit) {
    val dismiss: () -> Unit = {
        onMenuDismiss()
        onBack()
    }
    Column(Modifier.fillMaxSize()) {
        // Same chrome flag and ink the floating menus get from their host, so the Material rows read the same here.
        CompositionLocalProvider(
            LocalAuraFloatingChrome provides true,
            LocalContentColor provides auraFloatingContentColor(),
        ) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                when (pick) {
                    is AuraSearchPick.Yt -> when (val item = pick.item) {
                        is SongItem -> YouTubeSongMenu(song = item, navController = env.navController, onDismiss = dismiss)
                        is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = env.navController, onDismiss = dismiss)
                        is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = dismiss)
                        is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = env.scope, onDismiss = dismiss)
                    }

                    is AuraSearchPick.Local -> when (val item = pick.item) {
                        is Song -> SongMenu(originalSong = item, navController = env.navController, onDismiss = dismiss)
                        is Album -> AlbumMenu(originalAlbum = item, navController = env.navController, onDismiss = dismiss)
                        is Artist -> ArtistMenu(originalArtist = item, coroutineScope = env.scope, onDismiss = dismiss)
                        is Playlist -> PlaylistMenu(playlist = item, coroutineScope = env.scope, onDismiss = dismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneEntity(env: PaneEnv, pick: AuraSearchPick, listState: LazyListState) {
    when (pick) {
        is AuraSearchPick.Yt -> when (val item = pick.item) {
            is SongItem -> PaneYtSong(env, pick, item, listState)
            is AlbumItem -> PaneYtAlbum(env, pick, item, listState)
            is ArtistItem -> PaneYtArtist(env, pick, item, listState)
            is PlaylistItem -> PaneYtPlaylist(env, pick, item, listState)
        }

        is AuraSearchPick.Local -> when (val item = pick.item) {
            is Song -> PaneLocalSong(env, pick, item, listState)
            is Album -> PaneLocalAlbum(env, pick, item, listState)
            is Artist -> PaneLocalArtist(env, pick, item, listState)
            is Playlist -> PaneLocalPlaylist(env, pick, item, listState)
        }
    }
}

// ── The pane ──────────────────────────────────────────────────────────────────────────────────────

/**
 * The right half of a wide search (see the file header). [pick] null shows «Selecciona un resultado».
 *
 * Inside it has its OWN back stack: the selection is the root, a release / artist opened from it is pushed, «Todas las
 * opciones» is pushed too, and BACK (the bar's chevron or the system gesture) pops one step before the host's own back
 * handling (deselect / close) sees it. The stack and every scroll position reset when [pick] changes.
 *
 * @param afterNavigate called after the pane navigated away to a full page (the suggestions panel closes itself).
 * @param onMenuDismiss called when the embedded full menu reports it is done (there is no window to close here).
 * @param playLocalSong how a library song starts (the list owner knows its searched-songs queue); null = just that song.
 */
@Composable
internal fun AuraSearchDetailPane(
    pick: AuraSearchPick?,
    navController: NavController,
    modifier: Modifier = Modifier,
    afterNavigate: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    playLocalSong: ((Song) -> Unit)? = null,
) {
    if (pick == null) {
        AuraSearchDetailPlaceholder(modifier)
        return
    }
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val menuState = LocalMenuState.current
    val listenTogether = LocalListenTogetherManager.current
    val isGuest = listenTogether?.isInRoom == true && !listenTogether.isHost
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val hideVideos by rememberPreference(HideVideoSongsKey, false)

    // Choosing a result puts the content in front of the user: the keyboard goes away (and stays away).
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(pick.key) { keyboard?.hide() }

    val stack = remember(pick.key) { mutableStateListOf<PaneRoute>(PaneRoute.Entity(pick)) }
    val listStates = remember(pick.key) { HashMap<String, LazyListState>() }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1, onBack = pop)

    val env = PaneEnv(
        context = context,
        navController = navController,
        player = playerConnection,
        database = database,
        scope = scope,
        mediaId = mediaMetadata?.id,
        albumId = mediaMetadata?.album?.id,
        isPlaying = isPlaying,
        isGuest = isGuest,
        hideVideos = hideVideos,
        push = { next -> stack.add(PaneRoute.Entity(next)) },
        showOptions = { target -> stack.add(PaneRoute.Options(target)) },
        go = { route ->
            navController.navigate(route)
            afterNavigate()
        },
        openTrackMenu = { track ->
            menuState.show {
                val yt = track.yt
                val local = track.local
                if (yt != null) {
                    YouTubeSongMenu(song = yt, navController = navController, onDismiss = menuState::dismiss)
                } else if (local != null) {
                    SongMenu(originalSong = local, navController = navController, onDismiss = menuState::dismiss)
                }
            }
        },
        playLocalSong = playLocalSong,
    )

    val top = stack.last()
    val optionsTitle = stringResource(R.string.aura_pane_all_options)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The track rows decide between their narrow and their column layout from the room they really have: this half.
        CompositionLocalProvider(LocalContentPaneWidth provides this@BoxWithConstraints.maxWidth) {
            Column(Modifier.fillMaxSize()) {
                if (stack.size > 1) {
                    PaneBackBar(
                        title = if (top is PaneRoute.Options) optionsTitle else stack[stack.lastIndex - 1].pick.titleText(),
                        onBack = pop,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(top.routeKey) {
                        when (top) {
                            is PaneRoute.Entity ->
                                PaneEntity(env, top.pick, listStates.getOrPut(top.routeKey) { LazyListState() })

                            is PaneRoute.Options -> PaneOptionsPage(env, top.pick, onBack = pop, onMenuDismiss = onMenuDismiss)
                        }
                    }
                }
            }
        }
    }
}
