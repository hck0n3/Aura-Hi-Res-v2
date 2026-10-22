package iad1tya.echo.music.ui.newui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import iad1tya.echo.music.constants.LibraryFilter
import iad1tya.echo.music.constants.LibraryPinSortDescendingKey
import iad1tya.echo.music.constants.LibraryPinSortKey
import iad1tya.echo.music.constants.LibraryPinsViewTypeKey
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.utils.LibraryPin
import iad1tya.echo.music.utils.LibraryPinKind
import iad1tya.echo.music.utils.LibraryPinSort
import iad1tya.echo.music.utils.LibraryPins
import iad1tya.echo.music.utils.LibraryPinsStore
import iad1tya.echo.music.utils.rememberLibraryPins
import iad1tya.echo.music.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.WatchEndpoint
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AlbumFilter
import iad1tya.echo.music.constants.AlbumFilterKey
import iad1tya.echo.music.constants.AlbumSortDescendingKey
import iad1tya.echo.music.constants.AlbumSortType
import iad1tya.echo.music.constants.AlbumSortTypeKey
import iad1tya.echo.music.constants.ArtistSortDescendingKey
import iad1tya.echo.music.constants.ArtistSortType
import iad1tya.echo.music.constants.ArtistSortTypeKey
import iad1tya.echo.music.constants.GridItemSize
import iad1tya.echo.music.constants.GridItemsSizeKey
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.LibraryViewType
import iad1tya.echo.music.constants.MixSortDescendingKey
import iad1tya.echo.music.constants.MixSortType
import iad1tya.echo.music.constants.MixSortTypeKey
import iad1tya.echo.music.constants.PlaylistSortDescendingKey
import iad1tya.echo.music.constants.PlaylistSortType
import iad1tya.echo.music.constants.PlaylistSortTypeKey
import iad1tya.echo.music.constants.ShowExportedPlaylistKey
import iad1tya.echo.music.constants.ShowExportedVideosPlaylistKey
import iad1tya.echo.music.constants.ShowLikedPlaylistKey
import iad1tya.echo.music.constants.ShowTopPlaylistKey
import iad1tya.echo.music.constants.ShowDownloadedPlaylistKey
import iad1tya.echo.music.constants.ShowCachedPlaylistKey
import iad1tya.echo.music.constants.SongFilter
import iad1tya.echo.music.constants.SongFilterKey
import iad1tya.echo.music.constants.SongSortDescendingKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.constants.SongSortTypeKey
import iad1tya.echo.music.constants.SongViewTypeKey
import iad1tya.echo.music.constants.YtmSyncKey
import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Artist
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.ShuffleContexts
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.EnhancedShuffleChip
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.menu.AlbumMenu
import iad1tya.echo.music.ui.menu.ArtistMenu
import iad1tya.echo.music.ui.menu.PlaylistMenu
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.LibraryAlbumsViewModel
import iad1tya.echo.music.viewmodels.LibraryArtistsViewModel
import iad1tya.echo.music.viewmodels.LibraryMixViewModel
import iad1tya.echo.music.viewmodels.LibraryPlaylistsViewModel
import iad1tya.echo.music.viewmodels.LibrarySongsViewModel
import iad1tya.echo.music.ui.component.librarySectionEmptyText
import iad1tya.echo.music.ui.component.rememberSubscribedArtistsEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The five tabs of the new Biblioteca.
 *
 * Each one holds the SAME `hiltViewModel()` the classic tab holds, reads the SAME sort/filter/view
 * preference keys, and plays through the SAME `ListQueue` with the SAME `contextId` — so the
 * "Aleatorio mejorado" no-repeat memory is one shared memory, not a second one that would make songs
 * repeat the moment the user toggles the interface.
 */

// ── Shared tab furniture ──────────────────────────────────────────────────────────────────────────

/**
 * The sort control: the current criterion opens a menu, the arrow flips ascending/descending.
 * The menu itself is a plain `DropdownMenu` — popups are not one of the six screens.
 *
 * ## It also hosts the search toggle now
 * The magnifying glass used to be pinned at the right of the Biblioteca chip strip, level with the
 * chips and clipping them — the owner's *"arruina mi diseño y se ve feo eso allí"*. It is not deleted,
 * because on Listas de reproducción it is the ONLY way to reach that tab's own name filter: it moves
 * here, next to the two controls it belongs with, inside the tab's own scrolling content. Pass
 * [onToggleSearch] together with [searchLabel] (Spanish, from the same string resource the field's
 * placeholder uses) on the three tabs that own a search field; leave both null on the ones that do not
 * — Álbumes and the hub — so nothing dead is drawn.
 */
@Composable
private fun <T : Enum<T>> AuraSortControl(
    sortType: T,
    sortDescending: Boolean,
    options: List<Pair<T, Int>>,
    onSortTypeChange: (T) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    searchLabel: String? = null,
    searchOpen: Boolean = false,
    onToggleSearch: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** false hides the ascending/descending arrow (the hub's «Mi orden» is the user's own order: a direction means nothing). */
    showDirection: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabelRes = options.firstOrNull { it.first == sortType }?.second
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter, end = 6.dp, top = 12.dp),
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    // The pill DRAWS at the render's height (~34 dp with a 14 dp glyph); the finger gets
                    // the 48 dp Android requires. minimumInteractiveComponentSize expands the touch
                    // bounds without stretching the background, so the look is unchanged.
                    .minimumInteractiveComponentSize()
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.SurfaceFill)
                    .auraClickableInternal(
                        onClick = { expanded = true },
                        contentDescription = "Cambiar el criterio de orden",
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text = currentLabelRes?.let { stringResource(it) } ?: sortType.name,
                    style = AuraType.Chip,
                    color = AuraPalette.OnGround,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
                AuraIconGlyph(
                    icon = AuraIcons.ChevronDown,
                    contentDescription = null,
                    size = 14.dp,
                    tint = AuraPalette.OnGroundMuted,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = AuraShapes.Card,
                containerColor = AuraPalette.FloatingWindowPlate,
                border = AuraMenuBorder,
            ) {
                options.forEach { (option, labelRes) ->
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(labelRes), color = AuraPalette.OnGround)
                        },
                        onClick = {
                            onSortTypeChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
        if (showDirection) {
            AuraIconButton(
                icon = AuraIcons.ChevronDown,
                // The classic header hardcodes "Toggle sort order" in English; the new UI owns this string
                // and the app's user-facing language is Spanish.
                contentDescription = "Invertir el orden",
                onClick = { onSortDescendingChange(!sortDescending) },
                size = 16.dp,
                tint = AuraPalette.OnGroundMuted,
                modifier = Modifier.graphicsLayer { rotationZ = if (sortDescending) 0f else 180f },
            )
        }
        // Grouped with the two controls it belongs with, and BEFORE the weighted spacer, so the 48 dp
        // touch target this costs is taken out of the trailing count — the only shrinkable thing in the
        // row and the one whose loss costs least, since it repeats what the list itself shows. The
        // criterion and the direction stay whole at every width. At the default sort ("Fecha añadida")
        // there is slack for the whole count on a 393 dp phone; on the longest label ("Tiempo de
        // reproducción") the count ellipsises, which it already did before this glyph arrived.
        if (onToggleSearch != null && searchLabel != null) {
            AuraIconButton(
                icon = AuraIcons.Search,
                contentDescription = searchLabel,
                onClick = onToggleSearch,
                size = 16.dp,
                tint = if (searchOpen) AuraPalette.Teal else AuraPalette.OnGroundFaint,
            )
        }
        Box(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A count line: "%d canciones", "%d álbumes"… kept as the same plural resource. */
@Composable
private fun AuraCount(text: String, modifier: Modifier = Modifier) {
    // Do NOT uppercase the whole phrase — "53 LISTAS DE REPRODUCCIÓN" was crushing the sort row into
    // "53 LIST…" on phones. Sentence case keeps the number readable.
    AuraTechnicalText(
        text = text,
        modifier = modifier.padding(end = 12.dp),
        color = AuraPalette.OnGroundGhost,
    )
}

/** The in-tab search field, shown when the header's search glyph is toggled on. */
@Composable
private fun AuraSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 10.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AuraIconGlyph(
            icon = AuraIcons.Search,
            contentDescription = null,
            size = 16.dp,
            tint = AuraPalette.OnGroundMuted,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AuraType.RowTitle.copy(color = AuraPalette.OnGround),
                cursorBrush = SolidColor(AuraPalette.Teal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            AuraIconButton(
                // A "+" turned 45° is the render's own close glyph — no separate icon needed.
                icon = AuraIcons.Plus,
                contentDescription = "Borrar la búsqueda",
                onClick = { onValueChange("") },
                size = 16.dp,
                tint = AuraPalette.OnGroundMuted,
                modifier = Modifier.graphicsLayer { rotationZ = 45f },
            )
        }
    }
}

// ── 1. Hub ────────────────────────────────────────────────────────────────────────────────────────

/**
 * Biblioteca › hub — redesigned (owner 2026-10-20: *«no quiero que se vean todos los artistas allá abajo, ni mis playlists, a
 * menos de que yo pueda pinear playlists y álbumes… e incluso artistas… y esta parte rediséñala»*).
 *
 *  1. **Shortcuts** — every button the hub always had (Me gusta, Exportado, Vídeos exportados, Mi top, Descargado, En caché,
 *     Álbumes favoritos, Novedades, Podcasts, Local), same toggles, now Inicio-style glass tiles ([AuraLibraryShortcutGrid]).
 *  2. **Fijados** — what the user PINNED (playlists, albums, artists; pinned from each item's menu, see `libraryPinMenuItem`)
 *     as a big cover grid, two columns on a phone up to four unfolded ([auraLibraryPinColumns]); the same list/grid switch and
 *     the sort the hub had; «Editar» turns it into a reorderable list with a ✕ per item. Nothing pinned → a card that says how.
 *  3. **Ver todo** — the three full lists are one tap away (the chips above are untouched), so nothing is lost: the hub just
 *     stopped dumping every artist, playlist and album under the shortcuts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraLibraryHub(
    navController: NavController,
    onOpenLocal: () -> Unit,
    onOpenFilter: (LibraryFilter) -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val topSize by viewModel.topValue.collectAsState(initial = 50)

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showExported) = rememberPreference(ShowExportedPlaylistKey, true)
    val (showExportedVideos) = rememberPreference(ShowExportedVideosPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)

    val (pinSort, onPinSortChange) = rememberEnumPreference(LibraryPinSortKey, LibraryPinSort.MANUAL)
    val (pinSortDescending, onPinSortDescendingChange) = rememberPreference(LibraryPinSortDescendingKey, false)
    var viewType by rememberEnumPreference(LibraryPinsViewTypeKey, LibraryViewType.GRID)

    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val pins by rememberLibraryPins()

    var editing by rememberSaveable { mutableStateOf(false) }
    var kindFilterOrdinal by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = editing) { editing = false }
    LaunchedEffect(pins.isEmpty()) { if (pins.isEmpty()) editing = false }

    val resolved = remember(pins, playlists, albums, artists) {
        resolveLibraryPins(pins, playlists, albums, artists)
    }
    val byKey = remember(resolved) { resolved.associateBy { it.key } }
    val presentKinds = remember(resolved) { resolved.mapTo(HashSet()) { it.pin.kind } }
    val selectedFilter = AuraPinFilter.entries.getOrNull(kindFilterOrdinal) ?: AuraPinFilter.ALL
    // A filter whose kind was just unpinned falls back to «Todo» instead of showing an empty grid.
    val activeFilter = if (selectedFilter.kind == null || selectedFilter.kind in presentKinds) selectedFilter else AuraPinFilter.ALL
    val visible = remember(resolved, activeFilter, pinSort, pinSortDescending) {
        val ofKind = activeFilter.kind?.let { kind -> resolved.filter { it.pin.kind == kind } } ?: resolved
        LibraryPins.sorted(
            items = ofKind,
            sort = pinSort,
            descending = pinSortDescending,
            pinnedAt = { it.pin.pinnedAt },
            name = { it.title },
            updatedAt = { it.updatedAtMillis() },
        )
    }

    LaunchedEffect(Unit) {
        if (ytmSync) withContext(Dispatchers.IO) { viewModel.syncAllLibrary() }
    }

    // A pin of a LOCAL playlist the user has since deleted would sit in the grid for ever. The library list cannot say "gone"
    // (it is empty before it loads), so the database is asked: no row = deleted. Only the pins the lists did not resolve are
    // asked about, so this is a no-op in the normal case.
    LaunchedEffect(resolved) {
        val unresolved = resolved.filter { it.pin.kind == LibraryPinKind.PLAYLIST && it.pin.local && it.playlist == null }
        if (unresolved.isNotEmpty()) {
            try {
                val dead = withContext(Dispatchers.IO) {
                    unresolved.filter { database.playlist(it.pin.id).first() == null }.map { it.key }
                }
                if (dead.isNotEmpty()) {
                    LibraryPinsStore.update(context) { current -> current.filterNot { it.key in dead } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
            }
        }
    }

    // ── Opening and menus: the SAME routes and the SAME menus the full lists use ──────────────────
    val openPin: (AuraResolvedPin) -> Unit = { r ->
        when (r.pin.kind) {
            LibraryPinKind.ARTIST -> navController.navigate("artist/${r.pin.id}")
            LibraryPinKind.ALBUM -> navController.navigate("album/${r.pin.id}")
            LibraryPinKind.PLAYLIST -> {
                val inLibrary = r.playlist
                if (inLibrary != null) {
                    navController.navigate(auraLibraryPlaylistRoute(inLibrary))
                } else {
                    // Not in the library list (yet / any more): ask the database, like Inicio does for its pinned playlists.
                    coroutineScope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            database.playlist(r.pin.id).first() ?: database.playlistByBrowseId(r.pin.id).first()
                        }
                        when {
                            saved != null -> navController.navigate(auraLibraryPlaylistRoute(saved))
                            // A LOCAL playlist that was deleted: say so and drop the dead pin instead of opening an empty page.
                            r.pin.local -> {
                                Toast.makeText(context, context.getString(R.string.library_pins_missing), Toast.LENGTH_SHORT).show()
                                LibraryPinsStore.update(context) { LibraryPins.without(it, r.key) }
                            }
                            else -> navController.navigate("online_playlist/${r.pin.id}")
                        }
                    }
                }
            }
        }
    }
    val openPinMenu: (AuraResolvedPin) -> Unit = { r ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show { AuraPinMenu(r, navController, coroutineScope, menuState::dismiss) }
    }
    val playAlbum: (String) -> Unit = { albumId ->
        coroutineScope.launch {
            val albumWithSongs = withContext(Dispatchers.IO) { database.albumWithSongs(albumId).firstOrNull() }
            albumWithSongs?.let {
                playerConnection.playQueue(
                    iad1tya.echo.music.playback.queues.LocalAlbumRadio(
                        it,
                        contextId = ShuffleContexts.album(it.album.id),
                    ),
                )
            }
        }
    }

    // ── Edit mode: reorder by dragging (the same library as the queue and the playlist page) ─────────
    val listState = rememberLazyListState()
    AuraScrollToTopOnReselect(navController) { listState.animateScrollToItem(0) }
    // Registry row 196: freeze the shell chrome's haze sampling while any list is mid-gesture/fling.
    ScrollStateBusReporter { listState.isScrollInProgress }
    val editKeys = remember { mutableStateListOf<String>() }
    var dragDirty by remember { mutableStateOf(false) }
    val reorderState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) { from, to ->
        // Key-based, not index arithmetic: the shortcut and header items before the rows move the list indexes.
        val fromKey = from.key as? String
        val toKey = to.key as? String
        if (fromKey != null && toKey != null) {
            val fromIndex = editKeys.indexOf(fromKey)
            val toIndex = editKeys.indexOf(toKey)
            if (fromIndex >= 0 && toIndex >= 0) {
                editKeys.add(toIndex, editKeys.removeAt(fromIndex))
                dragDirty = true
            }
        }
    }
    // The working order follows the stored pins whenever the user is not holding one (a pin removed with ✕, a pin added
    // from a menu while editing).
    LaunchedEffect(pins, editing) {
        if (editing && !reorderState.isAnyItemDragging) {
            editKeys.clear()
            editKeys.addAll(pins.map { it.key })
        }
    }
    // Commit once, when the finger lifts: one DataStore write per drag, not one per row crossed.
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging && dragDirty) {
            dragDirty = false
            val order = editKeys.toList()
            LibraryPinsStore.update(context) { LibraryPins.reordered(it, order) }
        }
    }

    val likedLabel = stringResource(R.string.liked)
    val exportedLabel = stringResource(R.string.action_exported)
    val exportedVideosLabel = stringResource(R.string.exported_videos_playlist)
    val topLabel = stringResource(R.string.my_top) + " $topSize"
    val downloadedLabel = stringResource(R.string.offline)
    val cachedLabel = stringResource(R.string.cached_playlist)
    val favoriteAlbumsLabel = stringResource(R.string.favorite_albums)
    val releaseRadarLabel = stringResource(R.string.release_radar_title)
    val podcastsLabel = stringResource(R.string.podcasts)
    val localLabel = stringResource(R.string.filter_local)
    // The tiles obey the same toggles as the classic UI (F9: every appearance setting must work in the new UI too).
    val shortcuts = buildList<AuraLibraryShortcut> {
        if (showLiked) add(AuraLibraryShortcut(AuraIcons.HeartFilled, likedLabel) { navController.navigate("auto_playlist/liked") })
        if (showExported) add(AuraLibraryShortcut(AuraIcons.Export, exportedLabel) { navController.navigate("auto_playlist/exported") })
        if (showExportedVideos) {
            add(AuraLibraryShortcut(AuraIcons.Video, exportedVideosLabel) { navController.navigate("auto_playlist/exported_videos") })
        }
        if (showTop) add(AuraLibraryShortcut(AuraIcons.Speed, topLabel) { navController.navigate("top_playlist/$topSize") })
        if (showDownloaded) add(AuraLibraryShortcut(AuraIcons.Downloaded, downloadedLabel) { navController.navigate("auto_playlist/downloaded") })
        if (showCached) add(AuraLibraryShortcut(AuraIcons.Cloud, cachedLabel) { navController.navigate("cache_playlist/cached") })
        add(AuraLibraryShortcut(AuraIcons.Album, favoriteAlbumsLabel) { navController.navigate("favorite_albums") })
        add(AuraLibraryShortcut(AuraIcons.Radio, releaseRadarLabel) { navController.navigate("release_radar") })
        add(AuraLibraryShortcut(AuraIcons.Lyrics, podcastsLabel) { navController.navigate("podcasts") })
        // Owner request: Local must be visible the moment Biblioteca opens — it switches to the LOCAL chip in place (same
        // embedded LocalSongScreen, same scan sheet — no second route, no second screen).
        add(AuraLibraryShortcut(AuraIcons.Device, localLabel, onOpenLocal))
    }

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val body: @Composable () -> Unit = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The PANE, not the window: with the player column open the Library is much narrower than the screen.
            val paneWidth = maxWidth
            val columns = auraLibraryPinColumns(paneWidth)
            val cardWidth = auraLibraryPinCardWidth(paneWidth, columns)
            LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier.fillMaxSize(),
            ) {
                // While reordering the shortcuts step aside: the list being dragged gets the screen (they are back on «Listo»).
                if (!editing) {
                    item(key = "aura_hub_shortcuts", contentType = "aura_hub_shortcuts") {
                        AuraLibraryShortcutGrid(
                            shortcuts = shortcuts,
                            columns = auraLibraryShortcutColumns(paneWidth),
                            modifier = Modifier
                                .animateItem()
                                .padding(top = 10.dp),
                        )
                    }
                }

                item(key = "aura_hub_pins_header", contentType = "aura_hub_pins_header") {
                    AuraPinsHeader(
                        count = resolved.size,
                        editing = editing,
                        onToggleEdit = {
                            if (!editing) {
                                // Seeded here too, not only by the effect, so the first edit frame is not an empty list.
                                editKeys.clear()
                                editKeys.addAll(pins.map { it.key })
                            }
                            editing = !editing
                        },
                        modifier = Modifier.animateItem(),
                    )
                }

                if (resolved.isEmpty()) {
                    item(key = "aura_hub_pins_empty", contentType = "aura_hub_pins_empty") {
                        AuraPinsEmptyCard(onOpenFilter = onOpenFilter, modifier = Modifier.animateItem())
                    }
                } else if (editing) {
                    item(key = "aura_hub_pins_edit_hint", contentType = "aura_hub_pins_edit_hint") {
                        Text(
                            text = stringResource(R.string.library_pins_edit_hint),
                            style = AuraType.RowSubtitle,
                            color = AuraPalette.OnGroundMuted,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = AuraSpacing.Gutter, vertical = 8.dp),
                        )
                    }
                    items(editKeys, key = { it }, contentType = { "aura_hub_pin_edit_row" }) { key ->
                        val r = byKey[key]
                        if (r != null) {
                            ReorderableItem(state = reorderState, key = key) { isDragging ->
                                val dragHandle: @Composable () -> Unit = {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .sizeIn(
                                                minWidth = AuraSpacing.MinTouchTarget,
                                                minHeight = AuraSpacing.MinTouchTarget,
                                            )
                                            .clip(CircleShape)
                                            .draggableHandle(),
                                    ) {
                                        AuraIconGlyph(
                                            icon = AuraIcons.DragHandle,
                                            contentDescription = stringResource(R.string.cd_reorder),
                                            size = 21.dp,
                                            tint = AuraPalette.OnGroundDisabled,
                                        )
                                    }
                                }
                                AuraRow(
                                    title = r.title,
                                    subtitle = r.subtitleText(),
                                    leading = dragHandle,
                                    artwork = {
                                        AuraCover(
                                            thumbnailUrl = r.thumbnailUrl,
                                            size = 52.dp,
                                            seed = r.pin.id,
                                            shape = auraTypeVisual(r.contentKind).shape,
                                        )
                                    },
                                    trailing = {
                                        AuraIconButton(
                                            // A "+" turned 45° is the render's own close glyph.
                                            icon = AuraIcons.Plus,
                                            contentDescription = stringResource(R.string.library_pins_remove_cd),
                                            onClick = {
                                                LibraryPinsStore.updateDetached(context) { LibraryPins.without(it, key) }
                                            },
                                            size = 20.dp,
                                            tint = AuraPalette.OnGroundMuted,
                                            modifier = Modifier.graphicsLayer { rotationZ = 45f },
                                        )
                                    },
                                    modifier = Modifier
                                        .then(
                                            if (isDragging) {
                                                Modifier.background(AuraPalette.FloatingFill, AuraShapes.Highlight)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .padding(horizontal = AuraSpacing.Gutter),
                                )
                            }
                        }
                    }
                    item(key = "aura_hub_pins_jump_edit", contentType = "aura_hub_pins_jump") {
                        AuraLibraryJumpRow(onOpenFilter = onOpenFilter, modifier = Modifier.padding(top = 8.dp))
                    }
                } else {
                    if (resolved.size >= 2) {
                        item(key = "aura_hub_pins_controls", contentType = "aura_hub_pins_controls") {
                            Column(Modifier.animateItem()) {
                                AuraSortControl(
                                    sortType = pinSort,
                                    sortDescending = pinSortDescending,
                                    options = listOf(
                                        LibraryPinSort.MANUAL to R.string.library_pins_sort_manual,
                                        LibraryPinSort.CREATE_DATE to R.string.sort_by_create_date,
                                        LibraryPinSort.NAME to R.string.sort_by_name,
                                        LibraryPinSort.LAST_UPDATED to R.string.sort_by_last_updated,
                                    ),
                                    onSortTypeChange = onPinSortChange,
                                    onSortDescendingChange = onPinSortDescendingChange,
                                    // «Mi orden» is the user's own order: a direction would mean nothing.
                                    showDirection = pinSort != LibraryPinSort.MANUAL,
                                    trailing = { AuraViewTypeToggle(viewType) { viewType = it } },
                                )
                                if (presentKinds.size > 1) {
                                    AuraSubFilterRow(
                                        options = AuraPinFilter.entries
                                            .filter { it.kind == null || it.kind in presentKinds }
                                            .map { it to it.labelRes },
                                        selected = activeFilter,
                                        onSelect = { kindFilterOrdinal = it.ordinal },
                                    )
                                }
                            }
                        }
                    }

                    if (viewType == LibraryViewType.GRID) {
                        items(
                            visible.chunked(columns),
                            key = { row -> "aura_hub_pin_row_" + row.first().key },
                            contentType = { "aura_hub_pin_row" },
                        ) { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(AURA_LIBRARY_PIN_GAP),
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = AuraSpacing.Gutter),
                            ) {
                                row.forEach { r ->
                                    AuraPinCard(
                                        resolved = r,
                                        width = cardWidth,
                                        isActive = r.pin.kind == LibraryPinKind.ALBUM && r.pin.id == mediaMetadata?.album?.id,
                                        isPlaying = isPlaying,
                                        onClick = { openPin(r) },
                                        onLongClick = { openPinMenu(r) },
                                        // Only an album that is in the library can be played from its cover (it needs its songs).
                                        onPlay = if (r.album != null) ({ playAlbum(r.pin.id) }) else null,
                                    )
                                }
                                // An incomplete last row keeps the column width instead of stretching its covers.
                                repeat(columns - row.size) { Spacer(Modifier.width(cardWidth)) }
                            }
                        }
                    } else {
                        items(visible, key = { it.key }, contentType = { "aura_hub_pin_list_row" }) { r ->
                            val visual = auraTypeVisual(r.contentKind)
                            AuraSongRow(
                                title = r.title,
                                fullTitle = r.pin.kind == LibraryPinKind.PLAYLIST,
                                subtitle = r.subtitleText(),
                                thumbnailUrl = r.thumbnailUrl,
                                seed = r.pin.id,
                                isActive = r.pin.kind == LibraryPinKind.ALBUM && r.pin.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                artworkShape = visual.shape,
                                artworkSize = visual.rowWidth,
                                typeChip = visual.label,
                                onClick = { openPin(r) },
                                onLongClick = { openPinMenu(r) },
                                onMenuClick = { openPinMenu(r) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = AuraSpacing.Gutter),
                            )
                        }
                    }

                    item(key = "aura_hub_pins_jump", contentType = "aura_hub_pins_jump") {
                        AuraLibraryJumpRow(onOpenFilter = onOpenFilter, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    if (editing) {
        // No pull-to-refresh while reordering: dragging a row near the top would be read as a pull.
        Box(Modifier.fillMaxSize()) { body() }
    } else {
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            indicator = {
                AuraPullRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                )
            },
        ) { body() }
    }
}

/** A library playlist is pinned when its local id OR its YouTube browse id is (a pin made from the YouTube menu uses the latter). */
private fun auraIsPlaylistPinned(pinnedKeys: Set<String>, playlist: Playlist): Boolean {
    if (LibraryPin.keyOf(LibraryPinKind.PLAYLIST, playlist.id) in pinnedKeys) return true
    val browseId = playlist.playlist.browseId ?: return false
    return LibraryPin.keyOf(LibraryPinKind.PLAYLIST, browseId) in pinnedKeys
}

/**
 * Where a library playlist opens. Copied verbatim from the Listas tab: a saved-but-unfetched YouTube playlist opens ONLINE,
 * everything else opens local. Getting this wrong sends a synced playlist to an empty local screen.
 */
private fun auraLibraryPlaylistRoute(playlist: Playlist): String =
    if (!playlist.playlist.isEditable &&
        playlist.songCount == 0 &&
        playlist.playlist.remoteSongCount != 0
    ) {
        "online_playlist/${playlist.playlist.browseId}"
    } else {
        "local_playlist/${playlist.id}"
    }

/**
 * The long-press / ⋯ menu of a pinned item: the library menu when the item IS in the library, the YouTube one (built from the
 * pin's snapshot) when it is not — both carry the «Anclar / Quitar de Biblioteca» row, so a pin can always be removed from here.
 */
@Composable
private fun AuraPinMenu(
    resolved: AuraResolvedPin,
    navController: NavController,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val pin = resolved.pin
    when (pin.kind) {
        LibraryPinKind.ARTIST -> {
            val artist = resolved.artist
            if (artist != null) {
                ArtistMenu(artist, coroutineScope, onDismiss)
            } else {
                YouTubeArtistMenu(
                    artist = ArtistItem(
                        id = pin.id,
                        title = resolved.title,
                        thumbnail = resolved.thumbnailUrl,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    ),
                    onDismiss = onDismiss,
                )
            }
        }

        LibraryPinKind.ALBUM -> {
            val liveAlbum by remember(pin.id) { database.album(pin.id) }.collectAsState(initial = null)
            val album = resolved.album ?: liveAlbum
            if (album != null) {
                AlbumMenu(album, navController, onDismiss)
            } else {
                YouTubeAlbumMenu(
                    albumItem = AlbumItem(
                        browseId = pin.id,
                        playlistId = pin.extra.orEmpty(),
                        title = resolved.title,
                        artists = pin.subtitle?.split(", ")?.map { com.music.innertube.models.Artist(name = it, id = null) },
                        thumbnail = resolved.thumbnailUrl.orEmpty(),
                    ),
                    navController = navController,
                    onDismiss = onDismiss,
                )
            }
        }

        LibraryPinKind.PLAYLIST -> {
            val byId by remember(pin.id) { database.playlist(pin.id) }.collectAsState(initial = null)
            val byBrowse by remember(pin.id) { database.playlistByBrowseId(pin.id) }.collectAsState(initial = null)
            val saved = resolved.playlist ?: byId ?: byBrowse
            if (saved != null) {
                PlaylistMenu(playlist = saved, coroutineScope = coroutineScope, onDismiss = onDismiss)
            } else {
                YouTubePlaylistMenu(
                    playlist = PlaylistItem(
                        id = pin.id,
                        title = resolved.title,
                        author = pin.subtitle?.let { com.music.innertube.models.Artist(name = it, id = null) },
                        songCountText = null,
                        thumbnail = resolved.thumbnailUrl,
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    ),
                    coroutineScope = coroutineScope,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

// ── 2. Canciones ──────────────────────────────────────────────────────────────────────────────────

/**
 * Biblioteca › Canciones — the tab the render actually draws: filter chips, rows, quality badge on the
 * right, download tick, and the shuffle action.
 */
@Composable
fun AuraLibrarySongsTab(
    navController: NavController,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val hideExplicit by rememberPreference(HideExplicitKey, false)
    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)
    // Uploaded filter removed from product — migrate stale prefs.
    LaunchedEffect(filter) {
        if (filter == SongFilter.UPLOADED) filter = SongFilter.LIBRARY
    }

    val songs by viewModel.allSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    LaunchedEffect(Unit) {
        if (ytmSync) {
            when (filter) {
                SongFilter.LIKED -> viewModel.syncLikedSongs()
                SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
                else -> return@LaunchedEffect
            }
        }
    }

    // `remember`ed (registry 395): this filtered the WHOLE library on every recomposition and every keystroke of the search
    // box — with 10-20k songs that is a stall on a weak phone, and free to fix on every tier.
    val filteredSongs = remember(songs, hideExplicit, searchQuery) {
        songs
            .let { list -> if (hideExplicit) list.filter { !it.song.explicit } else list }
            .let { list ->
                if (searchQuery.isBlank()) list
                else list.filter { it.song.title.contains(searchQuery, ignoreCase = true) }
            }
    }

    // Identical bucket derivation to the classic tab — the no-repeat memory must be ONE memory.
    val canonicalCollectionId = when (filter) {
        SongFilter.LIKED -> "AP:liked"
        SongFilter.DOWNLOADED -> "AP:downloaded"
        SongFilter.UPLOADED -> "LIB:LIBRARY"
        SongFilter.EXPORTED -> "AP:exported"
        SongFilter.LIBRARY -> "LIB:LIBRARY"
    }
    val libraryContextId = when {
        searchQuery.isNotBlank() -> null
        hideExplicit && filter == SongFilter.LIBRARY -> "$canonicalCollectionId:NX"
        else -> canonicalCollectionId
    }
    val shufflePlayedSet = rememberPlayedShuffleSet(libraryContextId)

    // Row 349 (C2 decision 2): list/grid switch, same key and default as the classic Canciones tab.
    var viewType by rememberEnumPreference(SongViewTypeKey, LibraryViewType.LIST)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridCellSize = auraLibraryCellSize(gridItemSize == GridItemSize.BIG)
    val isGrid = viewType == LibraryViewType.GRID
    val listState = rememberLazyGridState()
    AuraScrollToTopOnReselect(navController) { listState.animateScrollToItem(0) }
    // Registry row 196: freeze the shell chrome's haze sampling while any list is mid-gesture/fling.
    ScrollStateBusReporter { listState.isScrollInProgress }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (searchOpen) {
                AuraSearchField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = stringResource(R.string.search_library),
                )
            }
            LazyVerticalGrid(
                state = listState,
                columns = auraLibraryColumns(viewType, gridCellSize),
                horizontalArrangement = Arrangement.spacedBy(if (isGrid) 11.dp else 0.dp),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(if (isGrid) Modifier.padding(horizontal = AuraSpacing.Gutter) else Modifier),
            ) {
            item(key = "aura_songs_subfilters", contentType = "aura_songs_subfilters", span = { GridItemSpan(maxLineSpan) }) {
                AuraSubFilterRow(
                    options = listOf(
                        SongFilter.LIKED to R.string.filter_liked,
                        SongFilter.LIBRARY to R.string.filter_library,
                        SongFilter.DOWNLOADED to R.string.filter_downloaded,
                        SongFilter.EXPORTED to R.string.action_exported,
                    ),
                    selected = filter,
                    onSelect = { filter = it },
                )
            }

            item(key = "aura_songs_sort", contentType = "aura_songs_sort", span = { GridItemSpan(maxLineSpan) }) {
                AuraSortControl(
                    sortType = sortType,
                    sortDescending = sortDescending,
                    options = listOf(
                        SongSortType.CREATE_DATE to R.string.sort_by_create_date,
                        SongSortType.NAME to R.string.sort_by_name,
                        SongSortType.ARTIST to R.string.sort_by_artist,
                        SongSortType.PLAY_TIME to R.string.sort_by_play_time,
                    ),
                    onSortTypeChange = onSortTypeChange,
                    onSortDescendingChange = onSortDescendingChange,
                    searchLabel = stringResource(R.string.search_library),
                    searchOpen = searchOpen,
                    onToggleSearch = onToggleSearch,
                    trailing = {
                        AuraCount(
                            pluralStringResource(
                                R.plurals.n_song,
                                filteredSongs.size,
                                filteredSongs.size,
                            ),
                        )
                        AuraViewTypeToggle(viewType) { viewType = it }
                    },
                )
            }

            if (libraryContextId != null) {
                item(key = "aura_songs_shuffle_chip", contentType = "aura_songs_shuffle_chip", span = { GridItemSpan(maxLineSpan) }) {
                    val playedCount = remember(shufflePlayedSet, filteredSongs) {
                        filteredSongs.count { it.id in shufflePlayedSet || it.song.totalPlayTime > 0L }
                    }
                    Box(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EnhancedShuffleChip(playedCount = playedCount, total = filteredSongs.size)
                    }
                }
            }

            itemsIndexed(filteredSongs, key = { _, item -> item.song.id }) { index, song ->
                val playSong: () -> Unit = {
                    if (song.id == mediaMetadata?.id) {
                        playerConnection.togglePlayPause()
                    } else {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.queue_all_songs),
                                items = filteredSongs.map { it.toMediaItem() },
                                startIndex = index,
                                contextId = libraryContextId,
                            ),
                        )
                    }
                }
                if (isGrid) {
                    // Row 349: the classic song grid card — cover, title, artists; same tap and menu.
                    AuraCoverCard(
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnailUrl = song.song.thumbnailUrl,
                        seed = song.id,
                        width = gridCellSize,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        modifier = Modifier.animateItem(),
                        onClick = playSong,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                    return@itemsIndexed
                }
                AuraAppleListRowFrame(
                    showDivider = index < filteredSongs.lastIndex,
                    dividerInset = AuraAppleCoverDividerInset,
                ) {
                    AuraSongRow(
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnailUrl = song.song.thumbnailUrl,
                        seed = song.id,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        liked = song.song.liked,
                        explicit = song.song.explicit,
                        inLibrary = song.song.inLibrary != null,
                        // The DOWNLOADED tab is all downloads: the tick there would be noise, as today.
                        downloadId = if (filter != SongFilter.DOWNLOADED) song.id else null,
                        format = song.format,
                        playedInShuffle = song.id in shufflePlayedSet || song.song.totalPlayTime > 0L,
                        swipeMediaItem = song.toMediaItem(),
                        onClick = {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.queue_all_songs),
                                        items = filteredSongs.map { it.toMediaItem() },
                                        startIndex = index,
                                        contextId = libraryContextId,
                                    ),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onMenuClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                }
            }
        }
        }

        // Shuffle: the SAME memory prompt and the SAME unplayed-first ordering as the classic tab.
        val onShuffleClick = rememberShuffleMemoryPrompt(
            contextId = libraryContextId,
            playedCount = filteredSongs.count { it.id in shufflePlayedSet || it.song.totalPlayTime > 0L },
            totalCount = filteredSongs.size,
        ) { resetMemory ->
            val seed = ShuffleContexts.seedPlayedIds(
                resetMemory = resetMemory,
                songIds = filteredSongs.map { it.id },
                shufflePlayed = shufflePlayedSet,
                playTimeMs = { id ->
                    filteredSongs.firstOrNull { it.id == id }?.song?.totalPlayTime ?: 0L
                },
            )
            val ordered = if (resetMemory) {
                filteredSongs.shuffled()
            } else {
                val (unheard, heard) = filteredSongs.partition { it.id !in seed }
                unheard.shuffled() + heard.shuffled()
            }
            playerConnection.playQueue(
                ListQueue(
                    title = context.getString(R.string.queue_all_songs),
                    items = ordered.map { it.toMediaItem() },
                    contextId = libraryContextId,
                    startShuffled = true,
                    seedPlayedIds = seed,
                ),
            )
        }

        if (filteredSongs.isNotEmpty()) {
            val shuffleTabHazeState = LocalShellHazeState.current
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
                    .clip(CircleShape)
                    .then(
                        if (shuffleTabHazeState != null) {
                            // AUDIT P3: plate under the glass — descendant no-op insurance.
                            Modifier
                                .background(AuraPalette.PlayButtonGradient)
                                .shellGlass(shuffleTabHazeState)
                        } else {
                            Modifier.background(AuraPalette.PlayButtonGradient)
                        },
                    )
                    .auraClickableInternal(
                        onClick = onShuffleClick,
                        contentDescription = stringResource(R.string.shuffle),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Ink follows the ground: dark OnAccent on the teal gradient plate.
                Box(Modifier.padding(16.dp)) {
                    AuraIconGlyph(
                        icon = AuraIcons.Shuffle,
                        contentDescription = null,
                        size = 24.dp,
                        tint = AuraPalette.OnAccent,
                    )
                }
            }
        }
    }
}

// ── 3. Álbumes ────────────────────────────────────────────────────────────────────────────────────

@Composable
fun AuraLibraryAlbumsTab(
    navController: NavController,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    LaunchedEffect(filter) {
        if (filter == AlbumFilter.UPLOADED) filter = AlbumFilter.LIBRARY
    }
    val (sortType, onSortTypeChange) = rememberEnumPreference(AlbumSortTypeKey, AlbumSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val albums by viewModel.allAlbums.collectAsState()
    // What is already pinned to the Library shows its pin on the cover (grid view).
    val pins by rememberLibraryPins()
    val pinnedKeys = remember(pins) { pins.mapTo(HashSet()) { it.key } }

    LaunchedEffect(Unit) {
        if (ytmSync) withContext(Dispatchers.IO) { viewModel.sync() }
    }

    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridCellSize = auraLibraryCellSize(gridItemSize == GridItemSize.BIG)
    // Row 349: list/grid switch, same key as the classic tab.
    var viewType by rememberEnumPreference(iad1tya.echo.music.constants.AlbumViewTypeKey, iad1tya.echo.music.constants.LibraryViewType.GRID)
    val albumPlayScope = rememberCoroutineScope()
    val albumPlayDatabase = LocalDatabase.current
    val listState = rememberLazyGridState()
    AuraScrollToTopOnReselect(navController) { listState.animateScrollToItem(0) }
    // Registry row 196: freeze the shell chrome's haze sampling while any list is mid-gesture/fling.
    ScrollStateBusReporter { listState.isScrollInProgress }

    LazyVerticalGrid(
        state = listState,
        columns = auraLibraryColumns(viewType, gridCellSize),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AuraSpacing.Gutter),
    ) {
        item(key = "aura_albums_subfilters", contentType = "aura_albums_subfilters", span = { GridItemSpan(maxLineSpan) }) {
            AuraSubFilterRow(
                options = listOf(
                    AlbumFilter.LIKED to R.string.filter_liked,
                    AlbumFilter.LIBRARY to R.string.filter_library,
                ),
                selected = filter,
                onSelect = { filter = it },
            )
        }
        item(key = "aura_albums_sort", contentType = "aura_albums_sort", span = { GridItemSpan(maxLineSpan) }) {
            AuraSortControl(
                sortType = sortType,
                sortDescending = sortDescending,
                options = listOf(
                    AlbumSortType.CREATE_DATE to R.string.sort_by_create_date,
                    AlbumSortType.NAME to R.string.sort_by_name,
                    AlbumSortType.ARTIST to R.string.sort_by_artist,
                    AlbumSortType.YEAR to R.string.sort_by_year,
                    AlbumSortType.SONG_COUNT to R.string.sort_by_song_count,
                    AlbumSortType.LENGTH to R.string.sort_by_length,
                    AlbumSortType.PLAY_TIME to R.string.sort_by_play_time,
                ),
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                modifier = Modifier.padding(start = 0.dp),
                trailing = {
                    AuraCount(pluralStringResource(R.plurals.n_album, albums.size, albums.size))
                    AuraViewTypeToggle(viewType) { viewType = it }
                },
            )
        }

        if (albums.isEmpty()) {
            item(key = "aura_albums_empty", contentType = "aura_albums_empty", span = { GridItemSpan(maxLineSpan) }) {
                AuraEmpty(
                    text = librarySectionEmptyText(
                        section = { it.likedAlbums },
                        emptyText = stringResource(R.string.library_album_empty),
                    ),
                    modifier = Modifier.animateItem(),
                )
            }
        }

        items(albums, key = { it.id }) { album ->
            AuraLibraryCell(
                viewType = viewType,
                title = album.album.title,
                subtitle = album.artists.joinToString { it.name },
                thumbnailUrl = album.album.thumbnailUrl,
                seed = album.id,
                width = gridCellSize,
                isActive = album.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                badge = if (LibraryPin.keyOf(LibraryPinKind.ALBUM, album.id) in pinnedKeys) {
                    ({ AuraPinnedBadge(circle = false) })
                } else {
                    null
                },
                modifier = Modifier.animateItem(),
                onClick = { navController.navigate("album/${album.id}") },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show { AlbumMenu(album, navController, menuState::dismiss) }
                },
                // Row 348 (C2 audit): ▶ on the cover plays the album without opening it, like the classic
                // grid — same queue and context id as the classic button (Items.kt AlbumPlayButton).
                onPlay = {
                    albumPlayScope.launch {
                        val albumWithSongs = withContext(Dispatchers.IO) {
                            albumPlayDatabase.albumWithSongs(album.id).firstOrNull()
                        }
                        albumWithSongs?.let {
                            playerConnection.playQueue(
                                iad1tya.echo.music.playback.queues.LocalAlbumRadio(
                                    it,
                                    contextId = ShuffleContexts.album(it.album.id),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }
}

// ── 4. Artistas ───────────────────────────────────────────────────────────────────────────────────

@Composable
fun AuraLibraryArtistsTab(
    navController: NavController,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSortTypeKey, ArtistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val searchQuery by viewModel.searchQuery.collectAsState()
    val artists by viewModel.filteredArtists.collectAsState()
    val subscribedEmpty = rememberSubscribedArtistsEmpty(searching = searchQuery.isNotEmpty())
    // What is already pinned to the Library shows its pin on the cover (grid view).
    val pins by rememberLibraryPins()
    val pinnedKeys = remember(pins) { pins.mapTo(HashSet()) { it.key } }

    LaunchedEffect(Unit) {
        if (ytmSync) withContext(Dispatchers.IO) { viewModel.sync() }
    }

    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridCellSize = auraLibraryCellSize(gridItemSize == GridItemSize.BIG)
    // Row 349: list/grid switch, same key as the classic tab.
    var viewType by rememberEnumPreference(iad1tya.echo.music.constants.ArtistViewTypeKey, iad1tya.echo.music.constants.LibraryViewType.GRID)
    val artistVisual = auraTypeVisual(AuraContentKind.Artist)

    Column(Modifier.fillMaxSize()) {
        if (searchOpen) {
            AuraSearchField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = stringResource(R.string.search_library),
            )
        }
        val listState = rememberLazyGridState()
        AuraScrollToTopOnReselect(navController) { listState.animateScrollToItem(0) }
        // Registry row 196: freeze the shell chrome's haze sampling while any list is mid-gesture/fling.
        ScrollStateBusReporter { listState.isScrollInProgress }
        LazyVerticalGrid(
        state = listState,
        columns = auraLibraryColumns(viewType, gridCellSize),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .padding(horizontal = AuraSpacing.Gutter),
    ) {
        item(key = "aura_artists_sort", contentType = "aura_artists_sort", span = { GridItemSpan(maxLineSpan) }) {
            AuraSortControl(
                sortType = sortType,
                sortDescending = sortDescending,
                options = listOf(
                    ArtistSortType.CREATE_DATE to R.string.sort_by_create_date,
                    ArtistSortType.NAME to R.string.sort_by_name,
                    ArtistSortType.SONG_COUNT to R.string.sort_by_song_count,
                    ArtistSortType.PLAY_TIME to R.string.sort_by_play_time,
                ),
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                searchLabel = stringResource(R.string.search_library),
                searchOpen = searchOpen,
                onToggleSearch = onToggleSearch,
                modifier = Modifier.padding(start = 0.dp),
                trailing = {
                    AuraCount(pluralStringResource(R.plurals.n_artist, artists.size, artists.size))
                    AuraViewTypeToggle(viewType) { viewType = it }
                },
            )
        }

        if (artists.isEmpty()) {
            item(key = "aura_artists_empty", contentType = "aura_artists_empty", span = { GridItemSpan(maxLineSpan) }) {
                // The list is the account's subscriptions ONLY, so empty means "none synced yet": say
                // where artists come from and (signed in, not already syncing) offer «Sincronizar».
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.animateItem(),
                ) {
                    AuraEmpty(text = subscribedEmpty.title, secondary = subscribedEmpty.hint)
                    if (subscribedEmpty.canSync) {
                        AuraChip(
                            text = stringResource(R.string.action_sync),
                            selected = true,
                            onClick = { viewModel.sync() },
                        )
                    }
                }
            }
        }

        items(artists, key = { it.id }) { artist ->
            AuraLibraryCell(
                viewType = viewType,
                title = artist.artist.name,
                subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
                thumbnailUrl = artist.artist.thumbnailUrl,
                seed = artist.id,
                width = gridCellSize,
                shape = artistVisual.shape,
                badge = if (LibraryPin.keyOf(LibraryPinKind.ARTIST, artist.id) in pinnedKeys) {
                    ({ AuraPinnedBadge(circle = true) })
                } else {
                    null
                },
                modifier = Modifier.animateItem(),
                onClick = { navController.navigate("artist/${artist.id}") },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show { ArtistMenu(artist, coroutineScope, menuState::dismiss) }
                },
            )
        }
    }
    }
}

// ── 5. Listas ─────────────────────────────────────────────────────────────────────────────────────

@Composable
fun AuraLibraryPlaylistsTab(
    navController: NavController,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val (sortType, onSortTypeChange) = rememberEnumPreference(PlaylistSortTypeKey, PlaylistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val topSize by viewModel.topValue.collectAsState(initial = 50)

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showExported) = rememberPreference(ShowExportedPlaylistKey, true)
    val (showExportedVideos) = rememberPreference(ShowExportedVideosPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)

    val playlists by viewModel.allPlaylists.collectAsState()
    // What is already pinned to the Library shows its pin on the cover (grid view).
    val pins by rememberLibraryPins()
    val pinnedKeys = remember(pins) { pins.mapTo(HashSet()) { it.key } }
    var playlistSearchQuery by rememberSaveable { mutableStateOf("") }

    // Same diacritic-insensitive match as the classic tab: "cancion" finds "Canción".
    val filteredPlaylists = remember(playlists, playlistSearchQuery) {
        val q = playlistSearchQuery.trim()
        if (q.isEmpty()) playlists
        else {
            fun norm(s: String) =
                java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{Mn}+"), "")
            val nq = norm(q)
            playlists.filter { norm(it.playlist.name).contains(nq) }
        }
    }

    LaunchedEffect(Unit) {
        if (ytmSync) withContext(Dispatchers.IO) { viewModel.sync() }
    }

    // "Tamaño de la celda de la cuadrícula" (Grande / Pequeño). This grid used to be a bare `150.dp`
    // constant, so the setting moved the ~13 classic grids AND the new Novedades tab
    // (AuraSearchTabs.kt:600) and left the new Biblioteca alone — the user changed one control and saw
    // half the app follow it. Same key, same enum, and the two sizes are the classic pair:
    // `GridThumbnailHeight ± 24.dp` = 152 / 104 (LibraryAlbumsScreen.kt:259 and the twelve grids beside
    // it), rounded to the 150 this grid already drew so "Grande" is unchanged from what shipped.
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)
    val gridCellSize = auraLibraryCellSize(gridItemSize == GridItemSize.BIG)
    // Row 349: list/grid switch, same key as the classic tab.
    var viewType by rememberEnumPreference(iad1tya.echo.music.constants.PlaylistViewTypeKey, iad1tya.echo.music.constants.LibraryViewType.GRID)

    Column(Modifier.fillMaxSize()) {
        if (searchOpen) {
            AuraSearchField(
                value = playlistSearchQuery,
                onValueChange = { playlistSearchQuery = it },
                placeholder = stringResource(R.string.search_playlists),
            )
        }
        val listState = rememberLazyGridState()
        AuraScrollToTopOnReselect(navController) { listState.animateScrollToItem(0) }
        // Registry row 196: freeze the shell chrome's haze sampling while any list is mid-gesture/fling.
        ScrollStateBusReporter { listState.isScrollInProgress }
        LazyVerticalGrid(
        state = listState,
        columns = auraLibraryColumns(viewType, gridCellSize),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .padding(horizontal = AuraSpacing.Gutter),
    ) {
        item(key = "aura_playlists_sort", contentType = "aura_playlists_sort", span = { GridItemSpan(maxLineSpan) }) {
            AuraSortControl(
                sortType = sortType,
                sortDescending = sortDescending,
                options = listOf(
                    PlaylistSortType.CREATE_DATE to R.string.sort_by_create_date,
                    PlaylistSortType.NAME to R.string.sort_by_name,
                    PlaylistSortType.SONG_COUNT to R.string.sort_by_song_count,
                    PlaylistSortType.LAST_UPDATED to R.string.sort_by_last_updated,
                ),
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                modifier = Modifier.padding(start = 0.dp),
                // The glass that used to sit at the top right of the chip strip. This is the ONLY door
                // to this tab's name filter, so it moves rather than disappears.
                searchLabel = stringResource(R.string.search_playlists),
                searchOpen = searchOpen,
                onToggleSearch = onToggleSearch,
                trailing = {
                    AuraCount(
                        pluralStringResource(
                            R.plurals.n_playlist,
                            filteredPlaylists.size,
                            filteredPlaylists.size,
                        ),
                    )
                    AuraViewTypeToggle(viewType) { viewType = it }
                },
            )
        }
        item(key = "aura_playlists_auto", contentType = "aura_playlists_auto", span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                val tile = Modifier.weight(1f)
                if (showLiked) {
                    AuraTile(AuraIcons.HeartFilled, stringResource(R.string.liked),
                        { navController.navigate("auto_playlist/liked") }, tile)
                }
                if (showExported) {
                    AuraTile(AuraIcons.Export, stringResource(R.string.action_exported),
                        { navController.navigate("auto_playlist/exported") }, tile)
                }
                    if (showExportedVideos) {
                        AuraTile(AuraIcons.Video, stringResource(R.string.exported_videos_playlist),
                            { navController.navigate("auto_playlist/exported_videos") }, tile)
                    }
            }
        }
        item(key = "aura_playlists_header", contentType = "aura_playlists_header", span = { GridItemSpan(maxLineSpan) }) {
            AuraSectionHeader(
                title = stringResource(R.string.filter_playlists),
                modifier = Modifier.padding(start = 0.dp),
            )
        }

        // Esta pestaña no tenía NINGÚN hueco vacío: con la lista vacía dibujaba una cuadrícula en
        // blanco, que es indistinguible de "todavía está sincronizando" — justo el caso que el dueño
        // describió al iniciar sesión ("las listas… sin estar esperando que cargue"). Ahora dice cuál
        // de las dos cosas es. `library_playlist_empty` ya existía en strings.xml sin un solo uso.
        if (filteredPlaylists.isEmpty()) {
            item(key = "aura_playlists_empty", contentType = "aura_playlists_empty", span = { GridItemSpan(maxLineSpan) }) {
                AuraEmpty(
                    text = if (playlistSearchQuery.isNotBlank()) {
                        stringResource(R.string.no_results_found)
                    } else {
                        librarySectionEmptyText(
                            section = { it.playlists },
                            emptyText = stringResource(R.string.library_playlist_empty),
                        )
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        items(filteredPlaylists.distinctBy { it.id }, key = { it.id }) { playlist ->
            AuraLibraryCell(
                viewType = viewType,
                title = playlist.playlist.name,
                fullTitle = true,
                subtitle = pluralStringResource(
                    R.plurals.n_song,
                    playlist.songCount,
                    playlist.songCount,
                ),
                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                seed = playlist.id,
                // The card must be the cell, or "Pequeño" would re-column the grid and keep drawing
                // 150 dp cards inside 104 dp cells.
                width = gridCellSize,
                badge = if (auraIsPlaylistPinned(pinnedKeys, playlist)) {
                    ({ AuraPinnedBadge(circle = false) })
                } else {
                    null
                },
                // Creating, importing, deleting and re-sorting all move cards around this grid while it
                // is on screen. `animateItem` is available on LazyGridItemScope exactly as it is on the
                // list scopes, and the `it.id` key above is what makes it work.
                modifier = Modifier.animateItem(),
                // Routing copied verbatim from `LibraryPlaylistGridItem`: a saved-but-unfetched YouTube
                // playlist opens ONLINE, everything else opens local. Getting this wrong sends a synced
                // playlist to an empty local screen.
                onClick = {
                    if (!playlist.playlist.isEditable &&
                        playlist.songCount == 0 &&
                        playlist.playlist.remoteSongCount != 0
                    ) {
                        navController.navigate("online_playlist/${playlist.playlist.browseId}")
                    } else {
                        navController.navigate("local_playlist/${playlist.id}")
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        if (playlist.playlist.isEditable || playlist.songCount != 0) {
                            PlaylistMenu(
                                playlist = playlist,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                            )
                        } else {
                            playlist.playlist.browseId?.let { browseId ->
                                YouTubePlaylistMenu(
                                    playlist = PlaylistItem(
                                        id = browseId,
                                        title = playlist.playlist.name,
                                        author = null,
                                        songCountText = null,
                                        thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                                        playEndpoint = WatchEndpoint(
                                            playlistId = browseId,
                                            params = playlist.playlist.playEndpointParams,
                                        ),
                                        shuffleEndpoint = WatchEndpoint(
                                            playlistId = browseId,
                                            params = playlist.playlist.shuffleEndpointParams,
                                        ),
                                        radioEndpoint = WatchEndpoint(
                                            playlistId = "RDAMPL$browseId",
                                            params = playlist.playlist.radioEndpointParams,
                                        ),
                                        isEditable = false,
                                    ),
                                    coroutineScope = coroutineScope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
    }
}

// ── Sub-filter chips (Me gusta / Biblioteca / Subido / Descargado / Exportado) ─────────────────────

@Composable
private fun <T> AuraSubFilterRow(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter, end = AuraSpacing.Gutter, top = 8.dp),
    ) {
        options.forEach { (option, labelRes) ->
            AuraChip(
                text = stringResource(labelRes),
                selected = selected == option,
                onClick = { onSelect(option) },
            )
        }
    }
}
