package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.HomeRichLayoutKey
import iad1tya.echo.music.constants.TopSize
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.UpcomingReleaseEntity
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.shimmer.ShimmerHost
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.utils.rememberOfflineState
import iad1tya.echo.music.utils.claimUnique
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.NovedadesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraNovedadesScreen(
    navController: NavController,
    viewModel: NovedadesViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val radar by viewModel.radarReleases.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val newAlbums by viewModel.newAlbums.collectAsState()
    val featured by viewModel.featuredSongs.collectAsState()
    val moment by viewModel.momentSongs.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val topSongs by viewModel.topSongs.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val (topSize) = rememberPreference(TopSize, "50")
    val (homeRichLayout) = rememberPreference(HomeRichLayoutKey, true)
    val cardScale = if (homeRichLayout) 1f else 1f / 1.25f

    val radarAlbums = remember(radar) {
        radar.mapNotNull { r ->
            val browse = r.playId.ifBlank { return@mapNotNull null }
            AlbumItem(
                browseId = browse,
                playlistId = "",
                title = r.title,
                artists = listOf(Artist(name = r.artist, id = r.artistId.ifEmpty { null })),
                year = runCatching { r.releaseDate.year }.getOrNull(),
                thumbnail = r.artworkUri ?: "",
            )
        }
    }
    val heroRaw = (radarAlbums + newAlbums).distinctBy { it.id }.take(8)
    // First-seen id wins across every shelf so the same album/song never repeats in Novedades.
    val unique = remember(
        heroRaw, featured, newAlbums, radarAlbums, moment, listening, topSongs, upcoming,
    ) {
        val seen = linkedSetOf<String>()
        NovedadesUnique(
            hero = seen.claimUnique(heroRaw) { it.id },
            featured = seen.claimUnique(featured) { it.id },
            newAlbums = seen.claimUnique(newAlbums) { it.id },
            radarAlbums = seen.claimUnique(radarAlbums) { it.id },
            moment = seen.claimUnique(moment) { it.id },
            listening = seen.claimUnique(listening) { it.id },
            topSongs = seen.claimUnique(topSongs.take(12)) { it.id },
            upcoming = seen.claimUnique(upcoming) { it.id },
        )
    }

    val allShelvesEmpty = with(unique) {
        hero.isEmpty() && featured.isEmpty() && newAlbums.isEmpty() && radarAlbums.isEmpty() &&
            moment.isEmpty() && listening.isEmpty() && topSongs.isEmpty() && upcoming.isEmpty()
    }

    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    // Registry row 196: freeze the shell chrome's haze sampling while any list is mid-gesture/fling.
    ScrollStateBusReporter { listState.isScrollInProgress }
    val bloom = rememberAuraBloom(mediaMetadata?.id)

    val openYt: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()),
            )
            is AlbumItem -> navController.navigate("album/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
        }
    }
    val ytMenu: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(item, navController, menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(item, navController, menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = scope,
                    onDismiss = menuState::dismiss,
                )
                else -> {}
            }
        }
    }

    // 🔴 SIN CONEXIÓN (punto 4 del dueño, 2026-09-17). Novedades es 100 % red — estrenos, próximos
    // lanzamientos, lo más escuchado — así que aquí no hay una versión local que enseñar: lo honesto
    // es decirlo y prometer que vuelve solo, en vez de siete estantes vacíos o siete errores. Es la
    // única de las cuatro pantallas sin cuerpo local, y por eso no usa [DownloadedOnlyView].
    val offlineState = rememberOfflineState()
    if (offlineState.offline) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .auraScreenBackground(bloom, intensity = 1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            ) {
                AuraScreenHeader(
                    title = stringResource(R.string.tab_novedades),
                    trailing = { AuraTopActions() },
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = AuraSpacing.Gutter),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            if (offlineState.automatic) R.string.offline_auto_title else R.string.offline_mode,
                        ),
                        style = AuraType.SheetTitle,
                        color = AuraPalette.OnGround,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.offline_auto_novedades),
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 1f),
    ) {
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            indicator = {
                AuraPullRefreshIndicator(state = pullRefreshState, isRefreshing = isRefreshing)
            },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "aura_novedades_header", contentType = "aura_novedades_header") {
                    AuraScreenHeader(
                        title = stringResource(R.string.tab_novedades),
                        trailing = { AuraTopActions() },
                    )
                }

                // Ronda 5 (premium UX, dueño): la pantalla no tenía NINGÚN estado de carga — mientras
                // loadFeeds() corre (isRefreshing ya es true desde el init{} del ViewModel, antes de
                // cualquier pull-to-refresh manual) no había nada debajo del header. Mismo patrón que
                // Inicio (isLoading && homeSections.isEmpty()): shimmer solo en la carga EN FRÍO, nunca
                // sobre un refresh que ya tiene contenido que mostrar mientras llega el nuevo.
                if (isRefreshing && allShelvesEmpty) {
                    item(key = "aura_novedades_skeleton", contentType = "aura_novedades_skeleton") {
                        ShimmerHost(modifier = Modifier.animateItem()) {
                            AuraHomeShelfSkeleton(cardScale = cardScale)
                            repeat(5) { AuraDetailSkeletonRow() }
                        }
                    }
                }

                if (unique.hero.isNotEmpty()) {
                    item(key = "aura_novedades_hero", contentType = "aura_novedades_hero") {
                        Column(Modifier.padding(top = AuraSpacing.SectionGap)) {
                            AuraSectionHeader(title = stringResource(R.string.tab_novedades))
                            AuraShelf {
                                items(unique.hero, key = { it.id }) { album ->
                                    AuraTypedYtCoverCard(
                                        item = album,
                                        cardScale = cardScale * 1.15f,
                                        isActive = album.id == mediaMetadata?.album?.id,
                                        isPlaying = isPlaying,
                                        onClick = { openYt(album) },
                                        onLongClick = { ytMenu(album) },
                                    )
                                }
                            }
                        }
                    }
                }

                novedadesSongShelf(
                    key = "featured",
                    titleRes = R.string.novedades_featured_songs,
                    songs = unique.featured,
                    cardScale = cardScale,
                    isPlaying = isPlaying,
                    activeId = mediaMetadata?.id,
                    onOpen = openYt,
                    onMenu = ytMenu,
                )
                novedadesAlbumShelf(
                    key = "latest",
                    titleRes = R.string.novedades_latest_albums,
                    albums = unique.newAlbums,
                    cardScale = cardScale,
                    isPlaying = isPlaying,
                    activeAlbumId = mediaMetadata?.album?.id,
                    onOpen = openYt,
                    onMenu = ytMenu,
                )
                novedadesAlbumShelf(
                    key = "recent",
                    titleRes = R.string.novedades_recent_releases,
                    albums = unique.radarAlbums,
                    cardScale = cardScale,
                    isPlaying = isPlaying,
                    activeAlbumId = mediaMetadata?.album?.id,
                    onOpen = openYt,
                    onMenu = ytMenu,
                )
                // 🔴 "Playlists actualizadas" FUERA (punto 9 del dueño, 2026-09-17: *"eliminar la
                // categoría de 'playlists actualizadas' para evitar contenido repetido. Mantener
                // únicamente la sección de novedades"*). Era la única sección de Novedades que no
                // hablaba de lanzamientos: listas que YouTube marca como "actualizadas" y que ya
                // aparecen en Inicio, así que en la práctica repetía contenido de otra pestaña.
                novedadesLocalSongShelf(
                    key = "moment",
                    titleRes = R.string.novedades_songs_of_the_moment,
                    songs = unique.moment,
                    queue = unique.moment,
                    cardScale = cardScale,
                    isPlaying = isPlaying,
                    activeId = mediaMetadata?.id,
                    onPlayAt = { index, queueSongs, title ->
                        playerConnection.playQueue(
                            ListQueue(
                                title = title,
                                items = queueSongs.map { it.toMediaItem() },
                                startIndex = index,
                            ),
                        )
                    },
                    onMenu = { song ->
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
                if (unique.listening.isNotEmpty()) {
                    item(key = "aura_novedades_listening", contentType = "aura_novedades_listening") {
                        Column {
                            AuraSectionHeader(title = stringResource(R.string.novedades_everyones_listening))
                            AuraShelf {
                                items(unique.listening, key = { it.id }) { item ->
                                    AuraTypedYtCoverCard(
                                        item = item,
                                        cardScale = cardScale,
                                        isActive = item.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        onClick = { openYt(item) },
                                        onLongClick = { ytMenu(item) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (unique.topSongs.isNotEmpty()) {
                    novedadesLocalSongShelf(
                        key = "daily_top",
                        titleRes = R.string.novedades_daily_top,
                        songs = unique.topSongs,
                        queue = topSongs,
                        cardScale = cardScale,
                        isPlaying = isPlaying,
                        activeId = mediaMetadata?.id,
                        headerClick = { navController.navigate("top_playlist/$topSize") },
                        onPlayAt = { index, queueSongs, title ->
                            playerConnection.playQueue(
                                ListQueue(
                                    title = title,
                                    items = queueSongs.map { it.toMediaItem() },
                                    startIndex = index,
                                ),
                            )
                        },
                        onMenu = { song ->
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
                }
                if (unique.upcoming.isNotEmpty()) {
                    item(key = "aura_novedades_coming_soon", contentType = "aura_novedades_coming_soon") {
                        Column {
                            AuraSectionHeader(title = stringResource(R.string.novedades_coming_soon))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                                horizontalArrangement = Arrangement.spacedBy(AuraSpacing.ShelfItemGap),
                                modifier = Modifier.padding(top = AuraSpacing.SectionGap),
                            ) {
                                items(unique.upcoming, key = { it.id }) { item ->
                                    UpcomingCard(
                                        item = item,
                                        cardScale = cardScale,
                                        onOpen = {
                                            item.youtubeBrowseId?.let { navController.navigate("album/$it") }
                                        },
                                        onPresave = { viewModel.togglePresave(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NovedadesUnique(
    val hero: List<AlbumItem>,
    val featured: List<SongItem>,
    val newAlbums: List<AlbumItem>,
    val radarAlbums: List<AlbumItem>,
    val moment: List<Song>,
    val listening: List<YTItem>,
    val topSongs: List<Song>,
    val upcoming: List<UpcomingReleaseEntity>,
)

private fun androidx.compose.foundation.lazy.LazyListScope.novedadesSongShelf(
    key: String,
    titleRes: Int,
    songs: List<SongItem>,
    cardScale: Float,
    isPlaying: Boolean,
    activeId: String?,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
) {
    // Misma regla que los estantes de álbumes — ver [NovedadesShelves].
    if (!NovedadesShelves.worthShowing(songs.size)) return
    item(key = "aura_novedades_$key", contentType = "aura_novedades") {
        Column {
            AuraSectionHeader(title = stringResource(titleRes))
            AuraShelf {
                items(songs, key = { it.id }) { song ->
                    AuraTypedYtCoverCard(
                        item = song,
                        cardScale = cardScale,
                        isActive = song.id == activeId,
                        isPlaying = isPlaying,
                        onClick = { onOpen(song) },
                        onLongClick = { onMenu(song) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.novedadesLocalSongShelf(
    key: String,
    titleRes: Int,
    songs: List<Song>,
    queue: List<Song>,
    cardScale: Float,
    isPlaying: Boolean,
    activeId: String?,
    onPlayAt: (Int, List<Song>, String) -> Unit,
    onMenu: (Song) -> Unit,
    headerClick: (() -> Unit)? = null,
) {
    // Misma regla que los estantes de álbumes — ver [NovedadesShelves].
    if (!NovedadesShelves.worthShowing(songs.size)) return
    item(key = "aura_novedades_$key", contentType = "aura_novedades") {
        val title = stringResource(titleRes)
        val visual = auraTypeVisual(AuraContentKind.Song)
        Column {
            AuraSectionHeader(title = title, onClick = headerClick)
            AuraShelf {
                items(songs, key = { it.id }) { song ->
                    AuraCoverCard(
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name }
                            .takeIf { it.isNotBlank() },
                        thumbnailUrl = song.song.thumbnailUrl,
                        seed = song.id,
                        width = visual.shelfWidth * cardScale,
                        isActive = song.id == activeId,
                        isPlaying = isPlaying,
                        onClick = {
                            val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                            onPlayAt(index, queue, title)
                        },
                        onLongClick = { onMenu(song) },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.novedadesAlbumShelf(
    key: String,
    titleRes: Int,
    albums: List<AlbumItem>,
    cardScale: Float,
    isPlaying: Boolean,
    activeAlbumId: String?,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
) {
    // Ver [NovedadesShelves]: un estante con una sola tarjeta no es variedad, es una sección rota
    // con un encabezado que promete más de lo que hay.
    if (!NovedadesShelves.worthShowing(albums.size)) return
    item(key = "aura_novedades_$key", contentType = "aura_novedades") {
        Column {
            AuraSectionHeader(title = stringResource(titleRes))
            val w = auraTypeVisual(AuraContentKind.Album).shelfWidth * cardScale
            AuraDoubleRowShelf(
                rowHeight = auraShelfCardStackHeight(w),
                itemCount = albums.size,
            ) {
                lazyGridItems(albums, key = { it.id }) { album ->
                    AuraTypedYtCoverCard(
                        item = album,
                        cardScale = cardScale,
                        isActive = album.id == activeAlbumId,
                        isPlaying = isPlaying,
                        onClick = { onOpen(album) },
                        onLongClick = { onMenu(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingCard(
    item: UpcomingReleaseEntity,
    cardScale: Float,
    onOpen: () -> Unit,
    onPresave: () -> Unit,
) {
    val w = auraTypeVisual(AuraContentKind.Album).shelfWidth * cardScale
    val date = remember(item.releaseEpochMs) {
        LocalDate.ofInstant(Instant.ofEpochMilli(item.releaseEpochMs), ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
    Column {
        AuraCoverCard(
            title = item.title,
            subtitle = "${item.artistName} · $date",
            thumbnailUrl = item.artworkUri,
            seed = item.id,
            width = w,
            onClick = onOpen,
        )
        androidx.compose.material3.TextButton(onClick = onPresave) {
            Text(
                text = stringResource(
                    if (item.presaved) R.string.action_presaved else R.string.action_presave,
                ),
                style = AuraType.RowSubtitle,
                color = AuraPalette.Teal,
            )
        }
    }
}
