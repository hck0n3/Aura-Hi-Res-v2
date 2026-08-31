

package iad1tya.echo.music.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.graphics.Color
import iad1tya.echo.music.ui.newui.LocalShellHazeState
import iad1tya.echo.music.ui.newui.ScrollStateBusReporter
import iad1tya.echo.music.ui.newui.detailShellGlass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import iad1tya.echo.music.ui.newui.AuraPalette
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ArtistSongSortDescendingKey
import iad1tya.echo.music.constants.ArtistSongSortType
import iad1tya.echo.music.constants.ArtistSongSortTypeKey
import iad1tya.echo.music.constants.CONTENT_TYPE_HEADER
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.HideOnScrollFAB
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.SortHeader
import iad1tya.echo.music.ui.component.rememberPlayedShuffleSet
import iad1tya.echo.music.ui.component.rememberShuffleMemoryPrompt
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.newui.AuraAppleCoverDividerInset
import iad1tya.echo.music.ui.newui.AuraAppleListRowFrame
import iad1tya.echo.music.ui.newui.AuraSongRow
import iad1tya.echo.music.ui.newui.auraAppleDurationLabel
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.ArtistSongsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistSongsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        ArtistSongSortTypeKey,
        ArtistSongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        ArtistSongSortDescendingKey,
        true
    )
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val artist by viewModel.artist.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val lazyListState = rememberLazyListState()
    // Registry row 196: freeze the shell chrome's haze sampling while any list is mid-gesture/fling.
    ScrollStateBusReporter { lazyListState.isScrollInProgress }

    // Enhanced Shuffle context for THIS artist: "AR:" + the LOCAL artist row id (ArtistEntity's primary
    // key — the same id the route was opened with), matching ArtistScreen's header shuffle so both share
    // one memory. NOT "PL:": DatabaseDao's startup orphan prune deletes every "PL:%" context with no
    // matching `playlist` row, which would erase the artist's no-repeat memory on every launch.
    val artistShuffleContextId = artist?.id?.let { "AR:$it" }
    val artistPlayedForStart = rememberPlayedShuffleSet(artistShuffleContextId)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            item(
                key = "header",
                contentType = CONTENT_TYPE_HEADER,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { sortType ->
                            when (sortType) {
                                ArtistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                ArtistSongSortType.NAME -> R.string.sort_by_name
                                ArtistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                            }
                        },
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, item -> item.id },
            ) { index, song ->
                // Apple-style renglones (owner request): "all songs" is a PURE song list and is
                // reached from the Aura artist page, so it gets the same row design as
                // Local/Downloads/History — unconditional, both shells.
                AuraAppleListRowFrame(
                    showDivider = index < songs.lastIndex,
                    dividerInset = AuraAppleCoverDividerInset,
                    modifier = Modifier.animateItem(),
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
                        format = song.format,
                        durationLabel = auraAppleDurationLabel(song.song.duration.takeIf { it > 0 }),
                        swipeMediaItem = song.toMediaItem(),
                        onClick = {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.queue_all_songs),
                                        items = songs.map { it.toMediaItem() },
                                        startIndex = index,
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

        // Glass when the toggle is on (owner directive 2026-08-29 + UNIFICATION 2026-08-31): the
        // classic artist screens sit inside the same shell haze source Box, so they get the
        // exact recipe the nav bar uses. UNIFIED CONTRACT: always sampling like the chrome —
        // the row-196 sig-11 came from DESCENDANT bars over MUTATING sources; the source gate
        // is gone, haze 1.7.2 carries the Samsung shader fix, and this TopAppBar reads the same
        // single source the chrome does (the SimpMusic production pattern). No source →
        // Material default solid bar, byte-identical to what shipped before.
        val classicBarHazeState = LocalShellHazeState.current
        TopAppBar(
            title = { Text(artist?.artist?.name.orEmpty()) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = null,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            colors = if (classicBarHazeState != null) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                )
            } else {
                TopAppBarDefaults.topAppBarColors()
            },
            modifier = Modifier.then(
                if (classicBarHazeState != null) {
                    Modifier.detailShellGlass(classicBarHazeState)
                } else {
                    Modifier
                },
            ),
        )

        HideOnScrollFAB(
            lazyListState = lazyListState,
            icon = R.drawable.shuffle,
            onClick = rememberShuffleMemoryPrompt(
                // Ask "continue or start over" only when this artist already has no-repeat memory.
                contextId = artistShuffleContextId,
                playedCount = songs.count { it.id in artistPlayedForStart },
                totalCount = songs.size,
            ) { resetMemory ->
                // UNPLAYED-FIRST start: the opener is guaranteed to be an unheard song while any
                // remain (a uniform scramble could re-open with something just heard).
                // After a reset the memory is empty, so a plain shuffle IS the unplayed-first order.
                val ordered = if (resetMemory) {
                    songs.shuffled()
                } else {
                    val (unheard, heard) = songs.partition { it.id !in artistPlayedForStart }
                    unheard.shuffled() + heard.shuffled()
                }
                playerConnection.playQueue(
                    ListQueue(
                        title = artist?.artist?.name,
                        items = ordered.map { it.toMediaItem() },
                        contextId = artistShuffleContextId,
                        // Turn shuffle MODE on so Enhanced Shuffle actually drives the order and
                        // records plays. Pre-shuffling alone left the mode off: the icon stayed
                        // off and the order was a frozen scramble.
                        startShuffled = true,
                    ),
                )
            },
        )
    }
}
