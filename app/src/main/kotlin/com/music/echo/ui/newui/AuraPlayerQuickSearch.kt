package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.constants.SearchSource
import iad1tya.echo.music.constants.SearchSourceKey
import iad1tya.echo.music.db.entities.SearchHistory
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubeArtistMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberOfflineState
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.OnlineSearchSuggestionViewModel
import iad1tya.echo.music.viewmodels.PlayerOnlineSearchViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Player quick-search: same sources as Buscar (biblioteca ↔ YouTube Music), full YTM summary results.
 * Artist / album / playlist open **inside** this frost sheet (local browse stack) — the sheet stays open
 * until the user closes it (back / leading chevron). Songs keep the sheet open for play / next / queue.
 */
@Composable
fun AuraPlayerQuickSearchContent(
    navController: NavController,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onBrowseAway: () -> Unit = onDismiss,
    isListenTogetherGuest: Boolean = false,
    modifier: Modifier = Modifier,
    suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
    onlineResultsViewModel: PlayerOnlineSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    // Panel compacto estilo Spotlight (dueño 2026-10-18): se mide a sí mismo, sin título, y los resultados ocupan
    // solo lo que necesitan (`weight(fill = false)`), no todo el alto disponible.
    val compact = LocalAuraSpotlightSheet.current
    val menuState = LocalMenuState.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Owner 2026-09-13: touching a suggestion or a result hides the keyboard so the content is visible.
    // Observed on the Initial pass without consuming, so the row still receives its own tap/scroll.
    val hideKeyboardOnTouch = Modifier.pointerInput(keyboardController) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            keyboardController?.hide()
        }
    }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Manual o automático sin red, igual que la pantalla de Buscar (punto 4, 2026-09-17).
    val offlineMode = rememberOfflineState().offline
    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    val effectiveSource = if (offlineMode) SearchSource.LOCAL else searchSource

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val hideVideoSongs by rememberPreference(HideVideoSongsKey, false)

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPlaylistSong by remember { mutableStateOf<SongItem?>(null) }

    var browseStack by remember { mutableStateOf<List<QuickSearchBrowse>>(listOf(QuickSearchBrowse.Search)) }
    val browseTop = browseStack.lastOrNull() ?: QuickSearchBrowse.Search

    // WIDE WINDOW (owner 2026-10-20, unfolded Z Fold): *«cuando yo toque la canción… no me aparezca otro flotante sino que
    // aproveche el otro espacio para mostrar las opciones, como una pantalla dividida»*. The card grows (see
    // AuraSpotlightHost) and splits in two: the search stays on the left and the OTHER half is AuraSearchDetailPane — action
    // tiles, the track list of an album / playlist, the whole artist page, with ITS OWN back stack (it handles drilling into
    // a release itself). On a phone none of this runs: tapping still opens the floating menu, browsing still replaces the page.
    val wide = rememberAuraWideWindows()
    var pickedInDetail by remember { mutableStateOf<AuraSearchPick?>(null) }

    val focusRequester = remember { FocusRequester() }
    // Defer IME/focus until after the frost sheet finishes expanding — focusing on the first
    // frame races ModalBottomSheet.show() and causes the open animation to hitch then resume.
    LaunchedEffect(browseTop) {
        if (browseTop is QuickSearchBrowse.Search) {
            kotlinx.coroutines.delay(280)
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun popBrowse() {
        if (wide && pickedInDetail != null) {
            pickedInDetail = null
        } else if (browseStack.size > 1) {
            browseStack = browseStack.dropLast(1)
        } else {
            onDismiss()
        }
    }

    fun pushBrowse(dest: QuickSearchBrowse) {
        browseStack = browseStack + dest
    }

    BackHandler(onBack = { popBrowse() })

    // The embedded menus navigate ("Ver álbum", "Ver artista") and then call their onDismiss, which in the detail half has
    // no window to close. A navigation while this card is open means the user left: close the card with it, or it would
    // stay on top of the screen they went to. Wide only — the phone path is untouched.
    val navEntry by navController.currentBackStackEntryAsState()
    val openedOnEntry = remember { navController.currentBackStackEntry?.id }
    LaunchedEffect(wide, navEntry?.id) {
        if (wide && navEntry?.id != openedOnEntry) onDismiss()
    }

    val suggestionState by suggestionViewModel.viewState.collectAsState()
    val summaryPage = onlineResultsViewModel.summaryPage
    val onlineLoading = onlineResultsViewModel.loading
    val committedOnline = onlineResultsViewModel.committedQuery

    LaunchedEffect(query.text, effectiveSource) {
        if (effectiveSource == SearchSource.ONLINE && !offlineMode) {
            suggestionViewModel.query.value = query.text
            if (committedOnline != null && query.text.trim() != committedOnline) {
                onlineResultsViewModel.clearCommitted()
            }
        }
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        songIdsForMembership = pendingPlaylistSong?.id?.let { listOf(it) },
        onGetSong = {
            val item = pendingPlaylistSong ?: return@AddToPlaylistDialog emptyList()
            val meta = item.toMediaMetadata()
            database.withTransaction { insert(meta) }
            listOf(meta.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
            pendingPlaylistSong = null
        },
    )

    val voice = rememberAuraVoiceSearch(
        onPartial = { spoken -> query = TextFieldValue(spoken, TextRange(spoken.length)) },
        onResult = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
            if (effectiveSource == SearchSource.ONLINE && !offlineMode) {
                onlineResultsViewModel.search(spoken)
            }
        },
    )

    val openSongActions: (SongItem) -> Unit = { song ->
        menuState.show {
            AuraPlayerQuickSearchSongMenu(
                title = song.title,
                isGuest = isListenTogetherGuest,
                onDismiss = menuState::dismiss,
                onPlayNow = {
                    menuState.dismiss()
                    playerConnection.playQueue(
                        YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata()),
                    )
                },
                onPlayNext = {
                    menuState.dismiss()
                    playerConnection.playNext(song.toMediaItem())
                },
                onAddToQueue = {
                    menuState.dismiss()
                    playerConnection.addToQueue(song.toMediaItem())
                },
                onAddToPlaylist = {
                    menuState.dismiss()
                    pendingPlaylistSong = song
                    showChoosePlaylistDialog = true
                },
            )
        }
    }

    val openYtMenu: (YTItem) -> Unit = { item ->
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
                    coroutineScope = scope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    val openBrowse: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> openSongActions(item)
            is AlbumItem -> pushBrowse(QuickSearchBrowse.Album(item.id, item.title))
            is ArtistItem -> pushBrowse(QuickSearchBrowse.Artist(item.id, item.title))
            is PlaylistItem -> pushBrowse(QuickSearchBrowse.Playlist(item.id, item.title))
        }
    }

    // ── Wide: what the detail half is showing ─────────────────────────────────────────────────────
    // The list row that is "open" is the picked result; everything deeper (an album of an artist, its tracks) lives in the
    // pane's own back stack.
    val selectedKey = pickedInDetail?.key
    val pickInDetail: (YTItem) -> Unit = { item -> pickedInDetail = AuraSearchPick.Yt(item) }
    val pickLocalInDetail: (AuraSearchPick) -> Unit = { pick -> pickedInDetail = pick }

    // The search itself: the field and the results / suggestions. On a phone it IS the page; in a wide window it is the left
    // half and its rows are the big ones (cover, type pill, artist, album, duration) that select instead of opening a menu.
    val searchColumn: @Composable ColumnScope.(Boolean) -> Unit = { wideMode ->
        if (!compact) {
            Text(
                text = stringResource(R.string.search),
                style = AuraType.ScreenTitle,
                color = AuraPalette.OnGround,
                modifier = Modifier.padding(horizontal = AuraSpacing.Gutter, vertical = 4.dp),
            )
        }

        AuraSearchInputBar(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(
                when (effectiveSource) {
                    SearchSource.LOCAL -> R.string.search_library
                    SearchSource.ONLINE -> R.string.search_yt_music
                },
            ),
            active = true,
            onLeadingClick = onDismiss,
            onSubmit = {
                keyboardController?.hide()
                if (effectiveSource == SearchSource.ONLINE && !offlineMode) {
                    onlineResultsViewModel.search(query.text)
                }
            },
            onClear = {
                query = TextFieldValue("")
                onlineResultsViewModel.clearCommitted()
            },
            onVoice = voice.launch,
            voiceActive = voice.active,
            focusRequester = focusRequester,
            onFieldTap = {},
            showSource = !offlineMode,
            offlineMode = offlineMode,
            sourceIsLocal = effectiveSource == SearchSource.LOCAL,
            onToggleSource = { searchSource = searchSource.toggle() },
        )

        when (effectiveSource) {
            SearchSource.LOCAL -> {
                // Library destinations still leave the sheet (local screens), but do not
                // collapse the player — only dismiss this frost plate.
                // Offline: only what plays with no network (see AuraOfflineSearchResults).
                val localModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = !compact || wideMode)
                    .then(hideKeyboardOnTouch)
                if (offlineMode) {
                    AuraOfflineSearchResults(
                        query = query.text,
                        navController = navController,
                        onDismiss = onDismiss,
                        modifier = localModifier,
                    )
                } else {
                    AuraLocalSearchResults(
                        query = query.text,
                        navController = navController,
                        onDismiss = onDismiss,
                        onPick = if (wideMode) pickLocalInDetail else null,
                        wideRows = wideMode,
                        selectedKey = selectedKey,
                        modifier = localModifier,
                    )
                }
            }

            SearchSource.ONLINE -> {
                val showCommitted = committedOnline != null &&
                    query.text.trim() == committedOnline &&
                    (summaryPage != null || onlineLoading)

                if (showCommitted) {
                    PlayerOnlineSummaryResults(
                        summaryPage = summaryPage,
                        loading = onlineLoading,
                        hideVideoSongs = hideVideoSongs,
                        isPlaying = isPlaying,
                        mediaId = mediaMetadata?.id,
                        albumId = mediaMetadata?.album?.id,
                        wide = wideMode,
                        selectedKey = selectedKey,
                        onSongClick = { song ->
                            if (wideMode) {
                                pickInDetail(song)
                            } else if (song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                openSongActions(song)
                            }
                        },
                        onSongLongClick = { song ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (wideMode) pickInDetail(song) else openSongActions(song)
                        },
                        onItemClick = if (wideMode) pickInDetail else openBrowse,
                        onItemMenu = if (wideMode) pickInDetail else openYtMenu,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = !compact || wideMode)
                            .then(hideKeyboardOnTouch),
                    )
                } else {
                    PlayerOnlineSuggestionPanel(
                        query = query.text,
                        viewState = suggestionState,
                        isPlaying = isPlaying,
                        mediaId = mediaMetadata?.id,
                        albumId = mediaMetadata?.album?.id,
                        wide = wideMode,
                        selectedKey = selectedKey,
                        onRunSearch = { picked ->
                            query = TextFieldValue(picked, TextRange(picked.length))
                            onlineResultsViewModel.search(picked)
                        },
                        onFillQuery = { filled ->
                            query = TextFieldValue(filled, TextRange(filled.length))
                        },
                        onClearHistory = { database.query { clearSearchHistory() } },
                        onDeleteHistory = { history ->
                            database.query { delete(history) }
                        },
                        onSongClick = if (wideMode) pickInDetail else openSongActions,
                        onItemClick = if (wideMode) pickInDetail else openBrowse,
                        onItemMenu = if (wideMode) pickInDetail else openYtMenu,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = !compact || wideMode)
                            .then(hideKeyboardOnTouch),
                    )
                }
            }
        }
    }

    if (wide) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val detailWidth = auraSearchDetailWidth(maxWidth)
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) { searchColumn(true) }
                Box(
                    Modifier
                        .width(detailWidth)
                        .fillMaxHeight()
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        .then(hideKeyboardOnTouch),
                ) {
                    AuraSearchDetailCard {
                        AuraSearchDetailPane(
                            pick = pickedInDetail,
                            navController = navController,
                        )
                    }
                }
            }
        }
    } else {
        Column(modifier = if (compact) modifier.fillMaxWidth() else modifier.fillMaxSize()) {
            when (val page = browseTop) {
                is QuickSearchBrowse.Search -> searchColumn(false)

                is QuickSearchBrowse.Artist,
                is QuickSearchBrowse.Album,
                is QuickSearchBrowse.Playlist,
                -> {
                    QuickSearchBrowsePane(
                        dest = page,
                        isPlaying = isPlaying,
                        mediaId = mediaMetadata?.id,
                        albumId = mediaMetadata?.album?.id,
                        hideVideoSongs = hideVideoSongs,
                        onBack = { popBrowse() },
                        onSongClick = { song ->
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                openSongActions(song)
                            }
                        },
                        onSongLongClick = { song ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            openSongActions(song)
                        },
                        onItemClick = openBrowse,
                        onItemMenu = openYtMenu,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = !compact)
                            .then(hideKeyboardOnTouch),
                    )
                }
            }
        }
    }
}

private sealed class QuickSearchBrowse {
    data object Search : QuickSearchBrowse()
    data class Artist(val id: String, val title: String) : QuickSearchBrowse()
    data class Album(val id: String, val title: String) : QuickSearchBrowse()
    data class Playlist(val id: String, val title: String) : QuickSearchBrowse()
}

@Composable
private fun QuickSearchBrowsePane(
    dest: QuickSearchBrowse,
    isPlaying: Boolean,
    mediaId: String?,
    albumId: String?,
    hideVideoSongs: Boolean,
    onBack: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (dest) {
        is QuickSearchBrowse.Artist -> dest.title
        is QuickSearchBrowse.Album -> dest.title
        is QuickSearchBrowse.Playlist -> dest.title
        QuickSearchBrowse.Search -> stringResource(R.string.search)
    }
    var loading by remember(dest) { mutableStateOf(true) }
    var error by remember(dest) { mutableStateOf(false) }
    var sections by remember(dest) { mutableStateOf<List<Pair<String, List<YTItem>>>>(emptyList()) }

    LaunchedEffect(dest) {
        loading = true
        error = false
        sections = emptyList()
        val result = runCatching {
            when (dest) {
                is QuickSearchBrowse.Artist -> {
                    val page = com.music.innertube.YouTube.artist(dest.id).getOrThrow()
                    page.sections.map { it.title to it.items }
                }
                is QuickSearchBrowse.Album -> {
                    val page = com.music.innertube.YouTube.album(dest.id).getOrThrow()
                    listOf("" to page.songs)
                }
                is QuickSearchBrowse.Playlist -> {
                    val page = com.music.innertube.YouTube.playlist(dest.id).getOrThrow()
                    listOf("" to page.songs)
                }
                QuickSearchBrowse.Search -> emptyList()
            }
        }
        loading = false
        if (result.isFailure) {
            error = true
        } else {
            sections = result.getOrDefault(emptyList())
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter, vertical = 4.dp),
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
                style = AuraType.ScreenTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
        }

        when {
            loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = AuraPalette.Teal)
                }
            }
            error -> {
                Text(
                    text = stringResource(R.string.error_unknown),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    modifier = Modifier.padding(AuraSpacing.Gutter),
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    sections.forEach { (sectionTitle, items) ->
                        val filtered = items.filterNot {
                            hideVideoSongs && it is SongItem && it.isVideoSong
                        }
                        if (filtered.isEmpty()) return@forEach
                        if (sectionTitle.isNotBlank()) {
                            item(key = "qb_h_$sectionTitle", contentType = "qb_h") {
                                AuraSectionHeader(title = sectionTitle)
                            }
                        }
                        items(filtered, key = { "qb_${sectionTitle}_${it.id}" }) { item ->
                            val active = when (item) {
                                is SongItem -> mediaId == item.id
                                is AlbumItem -> albumId == item.id
                                else -> false
                            }
                            val click: () -> Unit = {
                                when (item) {
                                    is SongItem -> onSongClick(item)
                                    else -> onItemClick(item)
                                }
                            }
                            val longClick: () -> Unit = {
                                if (item is SongItem) onSongLongClick(item) else onItemMenu(item)
                            }
                            AuraYtItemRow(
                                item = item,
                                isActive = active,
                                isPlaying = isPlaying,
                                onClick = click,
                                onLongClick = longClick,
                                onMenuClick = { onItemMenu(item) },
                            )
                        }
                    }
                    item(key = "qb_spacer", contentType = "qb_spacer") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PlayerOnlineSuggestionPanel(
    query: String,
    viewState: iad1tya.echo.music.viewmodels.SearchSuggestionViewState,
    isPlaying: Boolean,
    mediaId: String?,
    albumId: String?,
    onRunSearch: (String) -> Unit,
    onFillQuery: (String) -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    onClearHistory: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Wide window: big rows that select (the detail half shows the rest). */
    wide: Boolean = false,
    selectedKey: String? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val lazyListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { if (lazyListState.isScrollInProgress) keyboardController?.hide() }
    }
    LaunchedEffect(query, viewState) {
        if (!lazyListState.isScrollInProgress) {
            lazyListState.scrollToItem(0)
        }
    }

    LazyColumn(state = lazyListState, modifier = modifier) {
        if (viewState.history.isNotEmpty()) {
            item(key = "pq_history_header", contentType = "pq_history_header") {
                AuraSearchHistoryHeader(onClearAll = onClearHistory)
            }
            itemsIndexed(viewState.history, key = { _, h -> "pq_hist_${h.query}" }) { index, history ->
                AuraPlayerQueryRow(
                    text = history.query,
                    fromHistory = true,
                    first = index == 0,
                    last = index == viewState.history.lastIndex,
                    onClick = { onRunSearch(history.query) },
                    onDelete = { onDeleteHistory(history) },
                    onFill = { onFillQuery(history.query) },
                )
            }
        }

        if (viewState.suggestions.isNotEmpty()) {
            item(key = "pq_sug_header", contentType = "pq_sug_header") {
                AuraSectionHeader(title = stringResource(R.string.suggestions))
            }
            itemsIndexed(viewState.suggestions, key = { _, q -> "pq_sug_$q" }) { index, suggestion ->
                AuraPlayerQueryRow(
                    text = suggestion,
                    fromHistory = false,
                    first = index == 0,
                    last = index == viewState.suggestions.lastIndex,
                    onClick = { onRunSearch(suggestion) },
                    onDelete = null,
                    onFill = { onFillQuery(suggestion) },
                )
            }
        }

        if (viewState.items.isNotEmpty()) {
            item(key = "pq_top_header", contentType = "pq_top_header") {
                AuraSectionHeader(
                    title = stringResource(
                        if (viewState.isFromLink) R.string.parsed_from_link else R.string.top_result,
                    ),
                    accent = AuraPalette.Teal,
                )
            }
            items(viewState.items, key = { "pq_item_${it.id}" }) { item ->
                val active = when (item) {
                    is SongItem -> mediaId == item.id
                    is AlbumItem -> albumId == item.id
                    else -> false
                }
                val click: () -> Unit = {
                    if (item is SongItem) onSongClick(item) else onItemClick(item)
                }
                if (wide) {
                    AuraYtWideRow(
                        item = item,
                        selected = selectedKey == "yt:${item.id}",
                        isActive = active,
                        isPlaying = isPlaying,
                        onClick = click,
                        onLongClick = { onItemMenu(item) },
                        modifier = Modifier.padding(horizontal = AuraSpacing.Gutter - 8.dp),
                    )
                } else {
                    AuraYtItemRow(
                        item = item,
                        isActive = active,
                        isPlaying = isPlaying,
                        onClick = click,
                        onLongClick = { onItemMenu(item) },
                        onMenuClick = { onItemMenu(item) },
                    )
                }
            }
        }

        if (query.isNotBlank() &&
            viewState.history.isEmpty() &&
            viewState.suggestions.isEmpty() &&
            viewState.items.isEmpty()
        ) {
            item(key = "pq_hint", contentType = "pq_hint") {
                Text(
                    text = stringResource(R.string.search_yt_music),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    modifier = Modifier.padding(
                        horizontal = AuraSpacing.Gutter,
                        vertical = 16.dp,
                    ),
                )
            }
        }

        item(key = "pq_sug_spacer", contentType = "pq_sug_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PlayerOnlineSummaryResults(
    summaryPage: com.music.innertube.pages.SearchSummaryPage?,
    loading: Boolean,
    hideVideoSongs: Boolean,
    isPlaying: Boolean,
    mediaId: String?,
    albumId: String?,
    onSongClick: (SongItem) -> Unit,
    onSongLongClick: (SongItem) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: (YTItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Wide window: big rows that select (the detail half shows the rest). */
    wide: Boolean = false,
    selectedKey: String? = null,
) {
    val lazyListState = rememberLazyListState()

    LazyColumn(state = lazyListState, modifier = modifier) {
        if (loading && summaryPage == null) {
            item(key = "pq_loading", contentType = "pq_loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = AuraPalette.Teal)
                }
            }
        }

        summaryPage?.summaries
            ?.sortedBy { summary ->
                when (summary.items.firstOrNull()) {
                    is ArtistItem -> 0
                    is SongItem -> 1
                    is AlbumItem -> 2
                    is PlaylistItem -> 3
                    else -> 4
                }
            }
            ?.forEach { summary ->
                val musicItems = summary.items.filterNot {
                    hideVideoSongs && it is SongItem && it.isVideoSong
                }
                if (musicItems.isEmpty()) return@forEach

                item(key = "pq_sum_h_${summary.title}", contentType = "pq_sum_h") {
                    AuraSectionHeader(title = summary.title)
                }
                items(musicItems, key = { "pq_sum_${summary.title}_${it.id}" }) { item ->
                    val active = when (item) {
                        is SongItem -> mediaId == item.id
                        is AlbumItem -> albumId == item.id
                        else -> false
                    }
                    val click: () -> Unit = {
                        when (item) {
                            is SongItem -> onSongClick(item)
                            else -> onItemClick(item)
                        }
                    }
                    val longClick: () -> Unit = {
                        if (item is SongItem) onSongLongClick(item) else onItemMenu(item)
                    }
                    if (wide) {
                        AuraYtWideRow(
                            item = item,
                            selected = selectedKey == "yt:${item.id}",
                            isActive = active,
                            isPlaying = isPlaying,
                            onClick = click,
                            onLongClick = longClick,
                            modifier = Modifier.padding(horizontal = AuraSpacing.Gutter - 8.dp),
                        )
                    } else {
                        AuraYtItemRow(
                            item = item,
                            isActive = active,
                            isPlaying = isPlaying,
                            onClick = click,
                            onLongClick = longClick,
                            onMenuClick = { onItemMenu(item) },
                        )
                    }
                }
            }

        if (!loading && summaryPage != null &&
            summaryPage.summaries.all { s ->
                s.items.none { !(hideVideoSongs && it is SongItem && it.isVideoSong) }
            }
        ) {
            item(key = "pq_empty", contentType = "pq_empty") {
                Text(
                    text = stringResource(R.string.no_results_found),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundGhost,
                    modifier = Modifier.padding(
                        horizontal = AuraSpacing.Gutter,
                        vertical = 16.dp,
                    ),
                )
            }
        }

        item(key = "pq_sum_spacer", contentType = "pq_sum_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Fila 501 — la misma losa de cristal del historial de Buscar ([AuraQueryRow]) dentro de la búsqueda rápida del reproductor
 * (dueño 2026-10-21: «me faltó esta»). Mismos gestos: tocar busca, ✕ borra del historial, ↖ copia al campo.
 */
@Composable
private fun AuraPlayerQueryRow(
    text: String,
    fromHistory: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onFill: () -> Unit,
) {
    AuraQueryRow(
        query = text,
        fromHistory = fromHistory,
        onClick = onClick,
        onDelete = onDelete,
        onFillTextField = onFill,
        first = first,
        last = last,
    )
}

@Composable
internal fun AuraPlayerQuickSearchSongMenu(
    title: String,
    isGuest: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = AuraType.RowTitle,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.padding(
                horizontal = AuraSpacing.Gutter,
                vertical = 8.dp,
            ),
        )

        AuraMenuRow(
            icon = AuraIcons.Play,
            label = stringResource(R.string.play),
            onClick = onPlayNow,
        )

        if (!isGuest) {
            AuraMenuRow(
                icon = AuraIcons.SkipNext,
                label = stringResource(R.string.play_next),
                onClick = onPlayNext,
            )
            AuraMenuRow(
                icon = AuraIcons.Queue,
                label = stringResource(R.string.add_to_queue),
                onClick = onAddToQueue,
            )
        }

        AuraMenuRow(
            icon = AuraIcons.PlaylistAdd,
            label = stringResource(R.string.add_to_playlist),
            onClick = onAddToPlaylist,
        )

        Spacer(Modifier.height(12.dp))
    }
}
