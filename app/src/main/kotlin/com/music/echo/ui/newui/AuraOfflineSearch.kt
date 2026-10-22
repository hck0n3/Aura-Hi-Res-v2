package iad1tya.echo.music.ui.newui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ExportedSongIdsKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.db.OfflineCollection
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.PlayerConnection
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.CachePlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * # Sin conexión: lo disponible y su buscador
 *
 * Owner 2026-10-20: *«el modo sin conexión también lo veo básico y me gustaría que tenga su propio buscador, pero que solo
 * busque de las que están disponibles en el modo sin conexión, que no busque de otro lado»*.
 *
 * Two things live here, both shared by the offline Inicio/Biblioteca body (`DownloadedOnlyView`) and by the Buscar tab while
 * offline:
 *  · [OfflineLibrary] / [rememberOfflineLibrary] — WHAT plays with no network. It is the exact set the offline body has always
 *    listed (owner directive 2026-09-14, registry 225): downloads, listen-cache copies that are COMPLETE (the same test
 *    `MusicService.fullyCachedListenUri` applies, via `CachePlaylistViewModel.fullyCachedSongs`), exported MP3s and exported
 *    videos — plus the downloaded albums and playlists of registry 425. There is ONE definition of "available offline" and
 *    the list and the search both read it, so they cannot disagree.
 *  · the search presentation over [OfflineSearchIndex]. The index only receives that set; the classic library search
 *    (`LocalSearchViewModel`) queries every song of the library — including the ones that fail without a network — and that
 *    is precisely what the owner did not want offline.
 */
@Immutable
internal class OfflineLibrary(
    val downloaded: List<Song>,
    val cached: List<Song>,
    val exported: List<Song>,
    val exportedVideos: List<Song>,
    val albums: List<OfflineCollection>,
    val playlists: List<OfflineCollection>,
) {
    /**
     * Every song that plays with no network, once each. A song present in several sources keeps the first one in this order
     * (download, cache, MP3, video) — the order of the sections of the body.
     */
    val allSongs: List<Song> by lazy {
        (downloaded + cached + exported + exportedVideos).distinctBy { it.id }
    }

    val isEmpty: Boolean get() = downloaded.isEmpty() && cached.isEmpty() && exported.isEmpty() && exportedVideos.isEmpty()
}

/** Collects the offline sources. Cheap to call from two screens: Room flows and one subscriber-gated cache scan. */
@Composable
internal fun rememberOfflineLibrary(): OfflineLibrary {
    val database = LocalDatabase.current

    val downloadedSongs by database
        .downloadedSongs(SongSortType.CREATE_DATE, descending = true)
        .collectAsState(initial = emptyList())

    // Complete listen-cache copies only: a partial one cannot play without the network.
    val cacheViewModel: CachePlaylistViewModel = hiltViewModel()
    val cachedSongs by cacheViewModel.fullyCachedSongs.collectAsState()

    val (exportedSongIdsRaw) = rememberPreference(ExportedSongIdsKey, "")
    val (exportedVideoIdsRaw) = rememberPreference(ExportedVideoIdsKey, "")
    val exportedSongIds = remember(exportedSongIdsRaw) { exportedSongIdsRaw.split(",").filter { it.isNotBlank() } }
    val exportedVideoIds = remember(exportedVideoIdsRaw) { exportedVideoIdsRaw.split(",").filter { it.isNotBlank() } }
    val exportedSongs by remember(exportedSongIds) {
        if (exportedSongIds.isEmpty()) flowOf(emptyList()) else database.getSongsByIdsFlow(exportedSongIds)
    }.collectAsState(initial = emptyList())
    val exportedVideosRaw by remember(exportedVideoIds) {
        if (exportedVideoIds.isEmpty()) flowOf(emptyList()) else database.getSongsByIdsFlow(exportedVideoIds)
    }.collectAsState(initial = emptyList())
    // Exported videos open in video (the export has no audio-only rendition), same as Biblioteca's list.
    val exportedVideos = remember(exportedVideosRaw) {
        exportedVideosRaw.map { song -> if (song.song.isVideo) song else song.copy(song = song.song.copy(isVideo = true)) }
    }

    val downloadedAlbums by database.downloadedAlbums().collectAsState(initial = emptyList())
    val downloadedPlaylists by database.downloadedPlaylists().collectAsState(initial = emptyList())

    return remember(
        downloadedSongs, cachedSongs, exportedSongs, exportedVideos, downloadedAlbums, downloadedPlaylists,
    ) {
        OfflineLibrary(
            downloaded = downloadedSongs,
            cached = cachedSongs,
            exported = exportedSongs,
            exportedVideos = exportedVideos,
            albums = downloadedAlbums,
            playlists = downloadedPlaylists,
        )
    }
}

/** «12 canciones», or «3 de 12 descargadas» for a collection that is only partly offline: say what will really play. */
@Composable
internal fun offlineCollectionSubtitle(collection: OfflineCollection): String =
    if (collection.downloaded >= collection.total) {
        pluralStringResource(R.plurals.n_song, collection.downloaded, collection.downloaded)
    } else {
        stringResource(R.string.offline_collection_progress, collection.downloaded, collection.total)
    }

internal enum class OfflineSearchFilter { ALL, SONG, ALBUM, PLAYLIST, ARTIST }

/** What a result row needs to act: the player, the menu, the navigation. Built once per screen by [rememberOfflineSearchUi]. */
internal class OfflineSearchUi(
    val navController: NavController,
    val onDismiss: () -> Unit,
    val playingId: String?,
    val isPlaying: Boolean,
    val queueTitle: String,
    val playSongs: (title: String, songs: List<Song>, startIndex: Int, shuffled: Boolean) -> Unit,
    val togglePlayPause: () -> Unit,
    val showSongMenu: (Song) -> Unit,
    val longPressSongMenu: (Song) -> Unit,
)

@Composable
internal fun rememberOfflineSearchUi(
    playerConnection: PlayerConnection,
    navController: NavController,
    onDismiss: () -> Unit,
): OfflineSearchUi {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle = stringResource(R.string.queue_searched_songs)
    val showMenu: (Song) -> Unit = { song ->
        menuState.show {
            SongMenu(
                originalSong = song,
                navController = navController,
                onDismiss = {
                    onDismiss()
                    menuState.dismiss()
                },
            )
        }
    }
    return OfflineSearchUi(
        navController = navController,
        onDismiss = onDismiss,
        playingId = mediaMetadata?.id,
        isPlaying = isPlaying,
        queueTitle = queueTitle,
        playSongs = { title, songs, startIndex, shuffled ->
            if (songs.isNotEmpty()) {
                playerConnection.playQueue(
                    ListQueue(
                        title = title,
                        items = songs.map { it.toMediaItem() },
                        startIndex = startIndex.coerceIn(0, songs.lastIndex),
                        startShuffled = shuffled,
                    ),
                )
            }
        },
        togglePlayPause = playerConnection::togglePlayPause,
        showSongMenu = showMenu,
        longPressSongMenu = { song ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            showMenu(song)
        },
    )
}

/**
 * The result of [query] over [index], computed OFF the main thread: the first keystroke builds the folded text of the whole
 * offline library, which on a big one is not something to do inside a frame. Blank query = no work at all.
 */
@Composable
internal fun rememberOfflineSearchResult(index: OfflineSearchIndex, query: String): State<OfflineSearchResult> =
    produceState(initialValue = OfflineSearchResult.Empty, index, query) {
        value = if (query.isBlank()) {
            OfflineSearchResult.Empty
        } else {
            withContext(Dispatchers.Default) { index.search(query) }
        }
    }

/** Drops the keyboard when the user drags the list, like every other search panel of the app. */
@Composable
internal fun OfflineHideKeyboardOnScroll(listState: LazyListState) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { keyboard?.hide() }
    }
}

private const val OFFLINE_PREVIEW_SONGS = 5
private const val OFFLINE_PREVIEW_OTHERS = 3

private fun offlineFilterLabel(filter: OfflineSearchFilter): Int = when (filter) {
    OfflineSearchFilter.ALL -> R.string.filter_all
    OfflineSearchFilter.SONG -> R.string.filter_songs
    OfflineSearchFilter.ALBUM -> R.string.filter_albums
    OfflineSearchFilter.PLAYLIST -> R.string.offline_search_playlists
    OfflineSearchFilter.ARTIST -> R.string.filter_artists
}

/**
 * The grouped results (Canciones / Álbumes / Listas / Artistas) as list items, so the offline body can put them under its own
 * header and field, and the Buscar tab can put them in a list of their own. Under «Todo» each group is a short preview whose
 * header jumps to that group; a filter chip shows it whole. The actions are the app's own: a song plays the matching songs as
 * a queue and opens its song menu; an album or list opens its screen; an artist opens [offlineArtistItems].
 */
internal fun LazyListScope.offlineSearchItems(
    ui: OfflineSearchUi,
    result: OfflineSearchResult,
    filter: OfflineSearchFilter,
    onFilter: (OfflineSearchFilter) -> Unit,
    onOpenArtist: (OfflineArtist) -> Unit,
) {
    item(key = "offs_filters", contentType = "offs_filters") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter, vertical = 2.dp),
        ) {
            OfflineSearchFilter.values().forEach { option ->
                AuraChip(
                    text = stringResource(offlineFilterLabel(option)),
                    selected = filter == option,
                    onClick = { onFilter(option) },
                )
            }
        }
    }

    if (result.isEmpty) {
        item(key = "offs_none", contentType = "offs_none") {
            AuraEmpty(
                text = stringResource(R.string.no_results_found),
                secondary = stringResource(R.string.offline_search_only_offline),
            )
        }
        return
    }

    val all = filter == OfflineSearchFilter.ALL

    if ((all || filter == OfflineSearchFilter.SONG) && result.songs.isNotEmpty()) {
        if (all) {
            item(key = "offs_h_songs", contentType = "offs_header") {
                AuraSectionHeader(
                    title = stringResource(R.string.filter_songs),
                    onClick = { onFilter(OfflineSearchFilter.SONG) },
                )
            }
        }
        val shown = if (all) result.songs.take(OFFLINE_PREVIEW_SONGS) else result.songs
        itemsIndexed(shown, key = { _, song -> "offs_song_${song.id}" }, contentType = { _, _ -> "offs_song" }) { _, song ->
            AuraSongRow(
                title = song.song.title,
                subtitle = song.artists.joinToString { it.name },
                thumbnailUrl = song.song.thumbnailUrl,
                seed = song.id,
                isActive = song.id == ui.playingId,
                isPlaying = ui.isPlaying,
                liked = song.song.liked,
                explicit = song.song.explicit,
                inLibrary = song.song.inLibrary != null,
                downloadId = song.id,
                format = song.format,
                swipeMediaItem = song.toMediaItem(),
                onClick = {
                    if (song.id == ui.playingId) {
                        ui.togglePlayPause()
                    } else {
                        ui.playSongs(ui.queueTitle, result.songs, result.songs.indexOfFirst { it.id == song.id }, false)
                    }
                },
                onLongClick = { ui.longPressSongMenu(song) },
                onMenuClick = { ui.showSongMenu(song) },
                modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
            )
        }
    }

    if ((all || filter == OfflineSearchFilter.ALBUM) && result.albums.isNotEmpty()) {
        if (all) {
            item(key = "offs_h_albums", contentType = "offs_header") {
                AuraSectionHeader(
                    title = stringResource(R.string.filter_albums),
                    onClick = { onFilter(OfflineSearchFilter.ALBUM) },
                )
            }
        }
        val shown = if (all) result.albums.take(OFFLINE_PREVIEW_OTHERS) else result.albums
        itemsIndexed(shown, key = { _, c -> "offs_album_${c.id}" }, contentType = { _, _ -> "offs_collection" }) { _, collection ->
            OfflineCollectionResultRow(
                collection = collection,
                kind = AuraContentKind.Album,
                onClick = {
                    ui.onDismiss()
                    ui.navController.navigate("album/${collection.id}")
                },
            )
        }
    }

    if ((all || filter == OfflineSearchFilter.PLAYLIST) && result.playlists.isNotEmpty()) {
        if (all) {
            item(key = "offs_h_playlists", contentType = "offs_header") {
                AuraSectionHeader(
                    title = stringResource(R.string.offline_search_playlists),
                    onClick = { onFilter(OfflineSearchFilter.PLAYLIST) },
                )
            }
        }
        val shown = if (all) result.playlists.take(OFFLINE_PREVIEW_OTHERS) else result.playlists
        itemsIndexed(shown, key = { _, c -> "offs_playlist_${c.id}" }, contentType = { _, _ -> "offs_collection" }) { _, collection ->
            OfflineCollectionResultRow(
                collection = collection,
                kind = AuraContentKind.Playlist,
                onClick = {
                    ui.onDismiss()
                    ui.navController.navigate("local_playlist/${collection.id}")
                },
            )
        }
    }

    if ((all || filter == OfflineSearchFilter.ARTIST) && result.artists.isNotEmpty()) {
        if (all) {
            item(key = "offs_h_artists", contentType = "offs_header") {
                AuraSectionHeader(
                    title = stringResource(R.string.filter_artists),
                    onClick = { onFilter(OfflineSearchFilter.ARTIST) },
                )
            }
        }
        val shown = if (all) result.artists.take(OFFLINE_PREVIEW_OTHERS) else result.artists
        itemsIndexed(shown, key = { _, a -> "offs_artist_${a.id}" }, contentType = { _, _ -> "offs_artist" }) { _, artist ->
            val visual = auraTypeVisual(AuraContentKind.Artist)
            AuraSongRow(
                title = artist.name,
                subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
                thumbnailUrl = artist.thumbnailUrl,
                seed = artist.id,
                artworkShape = visual.shape,
                artworkSize = visual.rowWidth,
                typeChip = visual.label,
                onClick = { onOpenArtist(artist) },
                modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
            )
        }
    }

    item(key = "offs_bottom_spacer", contentType = "offs_spacer") { Spacer(Modifier.height(24.dp)) }
}

@Composable
private fun OfflineCollectionResultRow(
    collection: OfflineCollection,
    kind: AuraContentKind,
    onClick: () -> Unit,
) {
    val visual = auraTypeVisual(kind)
    AuraSongRow(
        title = collection.title,
        subtitle = "${visual.label} · ${offlineCollectionSubtitle(collection)}",
        thumbnailUrl = collection.thumbnailUrl,
        seed = collection.id,
        artworkShape = visual.shape,
        artworkSize = visual.rowWidth,
        typeChip = visual.label,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
    )
}

/**
 * One artist, opened in place. An artist's own page is a YouTube page and has nothing to show without a network, so
 * «abrir un artista» here means what is true offline: the songs of theirs that play, with Reproducir and Aleatorio.
 */
internal fun LazyListScope.offlineArtistItems(
    ui: OfflineSearchUi,
    artist: OfflineArtist,
    songs: List<Song>,
    onBack: () -> Unit,
) {
    item(key = "offs_artist_header", contentType = "offs_artist_header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp),
        ) {
            AuraIconButton(
                icon = AuraIcons.ChevronRight,
                contentDescription = stringResource(R.string.dismiss),
                onClick = onBack,
                size = 18.dp,
                tint = AuraPalette.OnGroundMuted,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = AuraType.RowTitle,
                    color = AuraPalette.OnGround,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
            AuraIconButton(
                icon = AuraIcons.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                onClick = { ui.playSongs(artist.name, songs, 0, true) },
                size = 20.dp,
                tint = AuraPalette.OnGroundMuted,
            )
            AuraIconButton(
                icon = AuraIcons.Play,
                contentDescription = stringResource(R.string.play_all),
                onClick = { ui.playSongs(artist.name, songs, 0, false) },
                size = 20.dp,
                tint = AuraPalette.Teal,
            )
        }
    }
    itemsIndexed(songs, key = { _, song -> "offs_artist_song_${song.id}" }, contentType = { _, _ -> "offs_song" }) { index, song ->
        AuraSongRow(
            title = song.song.title,
            subtitle = song.album?.title ?: song.artists.joinToString { it.name },
            thumbnailUrl = song.song.thumbnailUrl,
            seed = song.id,
            isActive = song.id == ui.playingId,
            isPlaying = ui.isPlaying,
            liked = song.song.liked,
            explicit = song.song.explicit,
            inLibrary = song.song.inLibrary != null,
            downloadId = song.id,
            format = song.format,
            swipeMediaItem = song.toMediaItem(),
            onClick = {
                if (song.id == ui.playingId) ui.togglePlayPause() else ui.playSongs(artist.name, songs, index, false)
            },
            onLongClick = { ui.longPressSongMenu(song) },
            onMenuClick = { ui.showSongMenu(song) },
            modifier = Modifier.padding(horizontal = AuraSpacing.Gutter),
        )
    }
    item(key = "offs_artist_bottom", contentType = "offs_spacer") { Spacer(Modifier.height(24.dp)) }
}

/**
 * The panel of the Buscar tab while the app is offline (manual or automatic). Same position, same chips-and-sections
 * vocabulary as [AuraLocalSearchResults] — but over [OfflineLibrary] and not over the whole library, so a song that would fail
 * without a network never shows up, and the artist, album and title of what does show up are all searchable without accents.
 * It never touches the network: the only inputs are the query and the offline lists.
 */
@Composable
fun AuraOfflineSearchResults(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val library = rememberOfflineLibrary()
    val ui = rememberOfflineSearchUi(playerConnection, navController, onDismiss)
    val index = remember(library) { OfflineSearchIndex(library.allSongs, library.albums, library.playlists) }
    val result by rememberOfflineSearchResult(index, query)

    var filter by rememberSaveable { mutableStateOf(OfflineSearchFilter.ALL) }
    var artistId by rememberSaveable { mutableStateOf<String?>(null) }
    val openArtist = remember(index, artistId) { artistId?.let { id -> index.artists.firstOrNull { it.id == id } } }
    val openArtistSongs = remember(index, openArtist) { openArtist?.let { index.songsOf(it.id) }.orEmpty() }
    BackHandler(enabled = openArtist != null) { artistId = null }

    val listState = rememberLazyListState()
    ScrollStateBusReporter { listState.isScrollInProgress }
    OfflineHideKeyboardOnScroll(listState)

    LazyColumn(
        state = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        modifier = modifier
            .fillMaxSize()
            .focusProperties { canFocus = false },
    ) {
        when {
            openArtist != null -> offlineArtistItems(ui, openArtist, openArtistSongs) { artistId = null }

            query.isBlank() -> item(key = "offs_hint", contentType = "offs_hint") {
                AuraEmpty(
                    text = if (library.isEmpty) {
                        stringResource(R.string.offline_empty_all)
                    } else {
                        stringResource(R.string.offline_search_hint_title)
                    },
                    secondary = if (library.isEmpty) {
                        null
                    } else {
                        stringResource(
                            R.string.offline_search_hint_counts,
                            pluralStringResource(R.plurals.n_song, library.allSongs.size, library.allSongs.size),
                            library.albums.size,
                            library.playlists.size,
                        )
                    },
                )
            }

            else -> offlineSearchItems(
                ui = ui,
                result = result,
                filter = filter,
                onFilter = { filter = it },
                onOpenArtist = { artistId = it.id },
            )
        }
    }
}
