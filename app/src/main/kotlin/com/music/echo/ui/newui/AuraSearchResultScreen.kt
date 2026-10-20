package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST_EPISODE
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.PauseSearchHistoryKey
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.shimmer.ShimmerHost
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.utils.forAllTabSearchPreview
import iad1tya.echo.music.utils.rememberOfflineState
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.OnlineSearchViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.launch

/**
 * # Resultados de búsqueda — "Interfaz nueva"
 *
 * The `search/{query}` route. Replaces `OnlineSearchResult`, keeping every one of its controls:
 *  · the bar (back, the query itself, clear, mic, and IME "buscar" which re-searches in place),
 *  · the **eight** filter chips — Todo / Canciones / Vídeos / Álbumes / Artistas / Listas de la
 *    comunidad / Listas destacadas / Podcasts,
 *  · the "Todo" view's section order (artistas → canciones → álbumes → listas, podcasts last) and its
 *    "Ocultar vídeos" preference, which the explicit Vídeos chip deliberately ignores,
 *  · the universal podcast results, which navigate to the feed,
 *  · infinite scroll (`loadMore` when the loading row becomes visible), the skeleton while a page is
 *    in flight, and "no se han encontrado resultados",
 *  · the suggestions panel that covers the results while the field has focus, dismissed with Atrás.
 *
 * All of it reads `OnlineSearchViewModel` — same query, same retry, same podcast repository.
 */
@Composable
fun AuraSearchResultScreen(
    navController: NavController,
    pureBlack: Boolean = false,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(HideVideoSongsKey, defaultValue = false)
    // Was hardcoded `false` below — this results screen ignored offline mode (manual AND automatic)
    // entirely, so re-searching from here always hit the network even with no connection.
    val offlineMode = rememberOfflineState().offline

    var isSearchFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = isSearchFocused) {
        isSearchFocused = false
        focusManager.clearFocus()
    }

    val encodedQuery = navController.currentBackStackEntry?.arguments?.getString("query") ?: ""
    val decodedQuery = remember(encodedQuery) {
        runCatching { URLDecoder.decode(encodedQuery, "UTF-8") }.getOrDefault(encodedQuery)
    }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(decodedQuery, TextRange(decodedQuery.length)))
    }
    LaunchedEffect(decodedQuery) {
        query = TextFieldValue(decodedQuery, TextRange(decodedQuery.length))
    }

    // Re-searching from this screen REPLACES it instead of stacking a second results page — the same
    // popUpTo the classic screen uses.
    val onSearch: (String) -> Unit = { searchQuery ->
        isSearchFocused = false
        focusManager.clearFocus()
        auraRunSearch(
            rawQuery = searchQuery,
            playerConnection = playerConnection,
            navController = navController,
            database = database,
            scope = coroutineScope,
            offlineMode = offlineMode,
            pauseSearchHistory = pauseSearchHistory,
            navigateToResults = { encoded ->
                navController.navigate("search/$encoded") {
                    popUpTo("search/${URLEncoder.encode(decodedQuery, "UTF-8")}") {
                        inclusive = true
                    }
                }
            },
        )
    }

    val voice = rememberAuraVoiceSearch(
        onPartial = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
        },
        onResult = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
            onSearch(spoken)
        },
    )

    val lazyListState = rememberLazyListState()
    // Registry row 196: freeze the shell chrome's haze sampling while any list is mid-gesture/fling.
    ScrollStateBusReporter { lazyListState.isScrollInProgress }
    // WIDE WINDOW: the single-filter views of albums / artists / playlists / videos are a grid of big covers.
    val gridState = rememberLazyGridState()
    ScrollStateBusReporter { gridState.isScrollInProgress }
    // WIDE WINDOW: the result whose detail is open in the other half (null = none yet).
    var picked by remember { mutableStateOf<AuraSearchPick?>(null) }
    val searchFilter by viewModel.filter.collectAsState()
    val searchSummary = viewModel.summaryPage
    val searchFailed = viewModel.hasFailed

    LaunchedEffect(searchSummary, searchFilter) {
        if (searchSummary != null || searchFilter != null) {
            lazyListState.scrollToItem(0)
            gridState.scrollToItem(0)
        }
    }

    val itemsPage by remember(searchFilter) {
        derivedStateOf { searchFilter?.value?.let { viewModel.viewStateMap[it] } }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "aura_results_loading" }
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) viewModel.loadMore()
        }
    }

    // Same infinite scroll for the grid (it has its own state and its own loading cell).
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.any { it.key == "aura_results_loading" }
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) viewModel.loadMore()
        }
    }

    val bloom = rememberAuraBloom(mediaMetadata?.id)

    val openMenu: (YTItem) -> Unit = { item ->
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(
                    song = item,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )

                is AlbumItem -> YouTubeAlbumMenu(
                    albumItem = item,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )

                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)

                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    val onItemClick: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> if (item.id == mediaMetadata?.id) {
                playerConnection.togglePlayPause()
            } else {
                playerConnection.playQueue(
                    YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()),
                )
            }

            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    // Same fix as AuraSearchScreen's `bodyInsets`: the bar above already clears the status bar via
    // `.windowInsetsPadding(...Top)` on the Column below, so anything composed BENEATH it (the results
    // list, and the suggestions overlay while the field is focused) must not reserve Top a second
    // time — that double reservation was the visible dead band under the bar/filters.
    val currentInsets = LocalPlayerAwareWindowInsets.current
    val bodyInsets = remember(currentInsets) {
        object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getLeft(density, layoutDirection)

            override fun getTop(density: Density) = 0

            override fun getRight(density: Density, layoutDirection: LayoutDirection) =
                currentInsets.getRight(density, layoutDirection)

            override fun getBottom(density: Density) = currentInsets.getBottom(density)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (pureBlack) Modifier.background(Color.Black)
                else Modifier.auraScreenBackground(bloom, intensity = 0.40f)
            )
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        AuraSearchInputBar(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_yt_music),
            // The leading glyph is the back chevron on this route, and it leaves the results.
            active = true,
            onLeadingClick = { navController.navigateUp() },
            onSubmit = { onSearch(query.text) },
            onClear = { query = TextFieldValue("") },
            onVoice = voice.launch,
            voiceActive = voice.active,
            focusRequester = focusRequester,
            onFieldTap = { isSearchFocused = true },
            showSource = false,
        )

        // WIDE WINDOW (owner 2026-10-20, unfolded Z Fold: *«desplegado… se desaprovecha todo ese espacio… cuando encuentre
        // algo se podría aprovechar el otro espacio»*, *«en los filtros las carátulas y todo sea más grande»*). Decided on the
        // room THIS screen really has (a content pane squeezed by the split player, a folded phone and a phone on its side
        // all keep the single column): the results go on the left with big rows (and a grid of big covers for the
        // albums / artists / playlists / videos filters), every one with a type pill, and tapping one shows its hero and its
        // real menu in the other half instead of playing / navigating / opening a floating menu.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val split = auraSearchSplitFits(maxWidth.value, maxHeight.value)
            val itemsPageNow = itemsPage
            // The grid needs the page to exist: while it loads (or failed) the list shows the skeleton / the error.
            val useGrid = split && itemsPageNow != null && auraResultFilterIsGrid(searchFilter)
            val detailBottom = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding() + 12.dp

            // One row of the single-column list: the phone row, or in a wide window the big one that selects.
            val resultRow: @Composable (YTItem, Modifier) -> Unit = { item, rowModifier ->
                val active = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                }
                if (split) {
                    AuraYtWideRow(
                        item = item,
                        selected = picked.isYt(item),
                        isActive = active,
                        isPlaying = isPlaying,
                        onClick = { picked = AuraSearchPick.Yt(item) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            picked = AuraSearchPick.Yt(item)
                        },
                        modifier = rowModifier.padding(horizontal = AuraSpacing.Gutter - 8.dp),
                    )
                } else {
                    AuraYtItemRow(
                        item = item,
                        isActive = active,
                        isPlaying = isPlaying,
                        onClick = { onItemClick(item) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            openMenu(item)
                        },
                        onMenuClick = { openMenu(item) },
                        modifier = rowModifier,
                    )
                }
            }

            val resultsBody: @Composable () -> Unit = {
                if (useGrid) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val spacing = 14.dp
                        val available = maxWidth - AuraSpacing.Gutter * 2
                        val minCell = if (searchFilter == FILTER_VIDEO) 230.dp else 150.dp
                        val columns = auraSearchGridColumns(
                            available = available,
                            minCell = minCell,
                            spacing = spacing,
                            floor = if (searchFilter == FILTER_VIDEO) 1 else 2,
                        )
                        val cellWidth = (available - spacing * (columns - 1)) / columns
                        val filteredItems = itemsPageNow?.items.orEmpty().distinctBy { it.id }

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(columns),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                            contentPadding = PaddingValues(
                                start = AuraSpacing.Gutter,
                                end = AuraSpacing.Gutter,
                                top = 10.dp,
                                bottom = 24.dp + LocalPlayerAwareWindowInsets.current.asPaddingValues()
                                    .calculateBottomPadding(),
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            gridItems(filteredItems, key = { "aura_filtered_${it.id}" }) { item ->
                                AuraSearchGridCard(
                                    entry = remember(item) { auraSearchEntry(item) },
                                    width = cellWidth,
                                    selected = picked.isYt(item),
                                    isActive = when (item) {
                                        is SongItem -> mediaMetadata?.id == item.id
                                        is AlbumItem -> mediaMetadata?.album?.id == item.id
                                        else -> false
                                    },
                                    isPlaying = isPlaying,
                                    onClick = { picked = AuraSearchPick.Yt(item) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        picked = AuraSearchPick.Yt(item)
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            if (itemsPageNow?.continuation != null) {
                                item(
                                    key = "aura_results_loading",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "aura_results_loading",
                                ) {
                                    ShimmerHost { repeat(2) { AuraSkeletonRow() } }
                                }
                            }

                            if (itemsPageNow?.items?.isEmpty() == true) {
                                item(
                                    key = "aura_results_empty_filtered",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "aura_results_empty_filtered",
                                ) {
                                    AuraEmpty(text = stringResource(R.string.no_results_found))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        // Top dropped on purpose: the status bar is already cleared by this screen's own
                        // `.windowInsetsPadding(...Top)` above, and the search bar + filter row sit above
                        // this list too. Padding the FULL inset here (Top included) stacked a second gap
                        // on top of both, leaving a visible dead band between the filters and the results.
                        contentPadding = LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (searchFilter == null) {
                            // Same order as the classic screen: artists, then songs (YouTube already ranks
                            // them), then albums, then playlists. Podcasts are appended last, below.
                            searchSummary?.summaries
                                ?.sortedBy { summary ->
                                    when (summary.items.firstOrNull()) {
                                        is ArtistItem -> 0
                                        is SongItem -> 1
                                        is AlbumItem -> 2
                                        is PlaylistItem -> 3
                                        else -> 4
                                    }
                                }
                                ?.forEachIndexed { summaryIndex, summary ->
                                    // "Ocultar vídeos" applies here (default = show); a section left empty
                                    // by it is dropped rather than drawn as a bare header.
                                    // All tab: dedicated Artists shelf caps at 2; mixed Top result
                                    // (artist card + songs) must NOT be truncated — that 0.6.190 take(2)
                                    // emptied Buscar. Full artist list stays on the Artistas chip.
                                    val musicItems = summary.items.filterNot {
                                        hideVideoSongs && it is SongItem && it.isVideoSong
                                    }.forAllTabSearchPreview()
                                    if (musicItems.isEmpty()) return@forEachIndexed

                                    item(key = "aura_summary_${summaryIndex}_${summary.title}", contentType = "aura_summary") {
                                        AuraSectionHeader(
                                            title = summary.title,
                                            modifier = Modifier.animateItem(),
                                        )
                                    }

                                    itemsIndexed(
                                        items = musicItems,
                                        key = { index, item -> "${summary.title}/${item.id}/$index" },
                                        // Reuse slots only within one item type, so reordering sections can
                                        // never hand an artist's slot to a song.
                                        contentType = { _, item -> item::class },
                                    ) { _, item ->
                                        resultRow(item, Modifier.animateItem())
                                    }
                                }

                            // Universal search: podcasts (Apple/RSS) alongside the music results.
                            val podcasts = viewModel.podcastResults
                            if (podcasts.isNotEmpty()) {
                                item(key = "aura_podcasts_title", contentType = "aura_podcasts_title") {
                                    AuraSectionHeader(
                                        title = stringResource(R.string.podcasts),
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                items(podcasts, key = { "aura_podcast_${it.id}" }) { show ->
                                    val openShow = {
                                        navController.navigate(
                                            "podcasts?feedUrl=" +
                                                URLEncoder.encode(show.feedUrl, "UTF-8"),
                                        )
                                    }
                                    if (split) {
                                        AuraSearchWideRow(
                                            entry = AuraSearchEntry(
                                                kind = AuraContentKind.Podcast,
                                                title = show.title,
                                                artist = show.author,
                                                meta = null,
                                                duration = null,
                                                thumbnailUrl = show.artworkUrl,
                                                seed = show.id,
                                                explicit = false,
                                            ),
                                            selected = false,
                                            isActive = false,
                                            isPlaying = false,
                                            onClick = { openShow() },
                                            modifier = Modifier
                                                .animateItem()
                                                .padding(horizontal = AuraSpacing.Gutter - 8.dp),
                                        )
                                    } else {
                                        AuraRow(
                                            title = show.title,
                                            subtitle = show.author,
                                            contentDescription = show.title,
                                            onClick = { openShow() },
                                            artwork = {
                                                AuraCover(
                                                    thumbnailUrl = show.artworkUrl,
                                                    size = 50.dp,
                                                    seed = show.id,
                                                    fillBleed = true,
                                                )
                                            },
                                            modifier = Modifier
                                                .animateItem()
                                                .padding(horizontal = AuraSpacing.Gutter),
                                        )
                                    }
                                }
                            }

                            if (searchSummary?.summaries?.isEmpty() == true && podcasts.isEmpty()) {
                                item(key = "aura_results_empty", contentType = "aura_results_empty") {
                                    AuraEmpty(text = stringResource(R.string.no_results_found))
                                }
                            }
                        } else {
                            val filtered = itemsPage?.items.orEmpty().distinctBy { it.id }
                            items(filtered, key = { "aura_filtered_${it.id}" }) { item ->
                                resultRow(item, Modifier.animateItem())
                            }

                            if (itemsPage?.continuation != null) {
                                item(key = "aura_results_loading", contentType = "aura_results_loading") {
                                    ShimmerHost { repeat(3) { AuraSkeletonRow() } }
                                }
                            }

                            if (itemsPage?.items?.isEmpty() == true) {
                                item(key = "aura_results_empty_filtered", contentType = "aura_results_empty_filtered") {
                                    AuraEmpty(text = stringResource(R.string.no_results_found))
                                }
                            }
                        }

                        if (searchFailed &&
                            ((searchFilter == null && searchSummary == null) ||
                                (searchFilter != null && itemsPage == null))
                        ) {
                            item(key = "aura_results_error", contentType = "aura_results_error") {
                                AuraDetailErrorState(
                                    message = stringResource(R.string.couldnt_load_search),
                                    onRetry = viewModel::retry,
                                )
                            }
                        } else if (searchFilter == null && searchSummary == null ||
                            searchFilter != null && itemsPage == null
                        ) {
                            item(key = "aura_results_skeleton", contentType = "aura_results_skeleton") {
                                ShimmerHost { repeat(8) { AuraSkeletonRow() } }
                            }
                        }

                        item(key = "aura_results_bottom_spacer", contentType = "aura_results_bottom_spacer") { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }

            Column(Modifier.fillMaxSize()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(
                        items = auraResultFilters(),
                        key = { it.first?.value ?: "all" },
                    ) { (filter, labelRes) ->
                        AuraChip(
                            text = stringResource(labelRes),
                            selected = searchFilter == filter,
                            onClick = {
                                if (viewModel.filter.value != filter) viewModel.filter.value = filter
                                // Only the scroller that is on screen: an unattached state has nothing to animate.
                                coroutineScope.launch {
                                    if (useGrid) gridState.animateScrollToItem(0) else lazyListState.animateScrollToItem(0)
                                }
                            },
                        )
                    }
                }

                if (split) {
                    AuraSearchSplit(
                        detailWidth = auraSearchDetailWidth(maxWidth),
                        bottomPadding = detailBottom,
                        list = resultsBody,
                        detail = {
                            AuraSearchDetailCard {
                                AuraSearchDetailPane(pick = picked, navController = navController)
                            }
                        },
                    )
                } else {
                    resultsBody()
                }
            }

            // The suggestions panel covers the results while the field has focus, exactly as today.
            // Wrapped in the same Top-stripped insets as the results list above: without this, the
            // panel's own internal list reserved the status-bar height a second time, opening a gap
            // between the bar/filters and the first suggestion the moment the user started typing.
            if (isSearchFocused) {
                CompositionLocalProvider(LocalPlayerAwareWindowInsets provides bodyInsets) {
                    AuraOnlineSearchSuggestions(
                        query = query.text,
                        onQueryChange = { query = it },
                        navController = navController,
                        onSearch = onSearch,
                        onDismiss = {
                            isSearchFocused = false
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.background(
                            if (pureBlack) Color.Black else AuraPalette.Ground,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Filters whose single-filter view is a grid of big covers in a wide window: the ones that are about a cover or a portrait.
 * Songs and podcasts stay as rows (a long list of tracks reads better as rows, and the row already carries the cover).
 */
private fun auraResultFilterIsGrid(filter: YouTube.SearchFilter?): Boolean =
    filter == FILTER_ALBUM || filter == FILTER_ARTIST || filter == FILTER_COMMUNITY_PLAYLIST ||
        filter == FILTER_FEATURED_PLAYLIST || filter == FILTER_VIDEO

/** The eight filter chips of the results screen, in the classic order. */
private fun auraResultFilters(): List<Pair<YouTube.SearchFilter?, Int>> = listOf(
    null to R.string.filter_all,
    FILTER_SONG to R.string.filter_songs,
    FILTER_ARTIST to R.string.filter_artists,
    FILTER_ALBUM to R.string.filter_albums,
    FILTER_COMMUNITY_PLAYLIST to R.string.filter_community_playlists,
    FILTER_FEATURED_PLAYLIST to R.string.filter_featured_playlists,
    FILTER_VIDEO to R.string.filter_videos,
    FILTER_PODCAST_EPISODE to R.string.podcasts,
)

/**
 * The skeleton row of the redesign. Drawn inside the app's existing [ShimmerHost], so the shimmer is
 * the one already shipping — only the shapes are Aura's, because the classic `ListItemPlaceHolder`
 * paints Material surfaces that would stamp pale rectangles on the near-black ground.
 */
@Composable
private fun AuraSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuraSpacing.RowInner),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 8.dp),
    ) {
        Spacer(
            Modifier
                .size(50.dp)
                .clip(AuraShapes.Artwork)
                .background(AuraPalette.SurfaceFill),
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Spacer(
                Modifier
                    .width(170.dp)
                    .height(13.dp)
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.SurfaceFill),
            )
            Spacer(
                Modifier
                    .width(110.dp)
                    .height(11.dp)
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.SurfaceFill),
            )
        }
    }
}
