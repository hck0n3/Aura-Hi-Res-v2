package iad1tya.echo.music.ui.newui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.music.innertube.YouTube
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
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.UpcomingReleaseEntity
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.ShuffleContexts
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.playback.queues.YouTubeAlbumRadio
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.component.shimmer.ShimmerHost
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.menu.YouTubeAlbumMenu
import iad1tya.echo.music.ui.menu.YouTubePlaylistMenu
import iad1tya.echo.music.ui.menu.YouTubeSongMenu
import iad1tya.echo.music.utils.claimUnique
import iad1tya.echo.music.utils.rememberLeanMode
import iad1tya.echo.music.utils.rememberOfflineState
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.reportException
import iad1tya.echo.music.viewmodels.NovedadesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Novedades — the «New» tab of the new interface.
 *
 * Owner 2026-10-20: *«la parte de novedades no me gusta, cómo se ve muy básico; o no sé si es la manera en
 * cómo está distribuida las cosas allí, muy sencillo»*. It was a header and seven identical shelves of cover
 * cards: nothing said which release was the freshest, nothing said how new each one was, and on an unfolded
 * foldable the same phone shelves just stretched. Now:
 *
 *  · a HERO for the freshest release (large cover on a glass plate tinted with THAT cover's own colours, type
 *    label, artist, age pill, «E», Reproducir / Abrir / ⋯);
 *  · type filters (Todo · Álbumes · Sencillos y EP · Canciones · Próximamente) that only appear when they have
 *    something behind them;
 *  · every shelf with a real title and «Ver todo» (expands it in place into a multi-column grid);
 *  · «Hoy» / «Ayer» / «Esta semana» / «Este mes» pills where the data HAS a date (the radar rows do; YouTube's
 *    «new albums» only carry a year, so those get nothing rather than a guessed label);
 *  · on a wide window the hero sits beside its text and «Más estrenos» is the same multi-browse carousel as
 *    Inicio's, the grids take as many columns as the pane fits.
 *
 * NOTHING of the data changed: same view-model flows, same first-seen de-duplication across shelves (row 141),
 * same follow-only radar (row 366) and «Lo más nuevo» filtered with the whole taste (row 431).
 */
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
    val context = LocalContext.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val radar by viewModel.radarReleases.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val newAlbums by viewModel.newAlbums.collectAsState()
    val featured by viewModel.featuredSongs.collectAsState()
    val moment by viewModel.momentSongs.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
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
    // Only the radar rows carry an exact date (Spotify's, or the year for the YouTube fallback): the age pill
    // is drawn for those and for nobody else.
    val ages: Map<String, ReleaseAge> = remember(radar) {
        val today = LocalDate.now()
        buildMap<String, ReleaseAge> {
            radar.forEach { r ->
                if (r.playId.isBlank()) return@forEach
                NovedadesShelves.ageOf(r.releaseDate.toLocalDate(), today)?.let { put(r.playId, it) }
            }
        }
    }
    val heroRaw = (radarAlbums + newAlbums).distinctBy { it.id }.take(8)
    // First-seen id wins across every shelf so the same album/song never repeats in Novedades.
    val unique = remember(
        heroRaw, featured, newAlbums, radarAlbums, moment, listening, upcoming,
    ) {
        val seen = linkedSetOf<String>()
        NovedadesUnique(
            hero = seen.claimUnique(heroRaw) { it.id },
            featured = seen.claimUnique(featured) { it.id },
            newAlbums = seen.claimUnique(newAlbums) { it.id },
            radarAlbums = seen.claimUnique(radarAlbums) { it.id },
            moment = seen.claimUnique(moment) { it.id },
            listening = seen.claimUnique(listening) { it.id },
            upcoming = seen.claimUnique(upcoming) { it.id },
        )
    }

    val allShelvesEmpty = with(unique) {
        hero.isEmpty() && featured.isEmpty() && newAlbums.isEmpty() && radarAlbums.isEmpty() &&
            moment.isEmpty() && listening.isEmpty() && upcoming.isEmpty()
    }

    // Type filters. Which ones are offered depends on what the lists really contain.
    val visibleFilters = remember(unique) {
        val albumKinds = (unique.hero + unique.newAlbums + unique.radarAlbums).map { auraContentKind(it) }
        NovedadesShelves.visibleFilters(
            hasAlbums = albumKinds.any { it == AuraContentKind.Album },
            hasSinglesAndEps = albumKinds.any { it == AuraContentKind.Single || it == AuraContentKind.Ep },
            hasSongs = unique.featured.isNotEmpty() || unique.moment.isNotEmpty() ||
                unique.listening.any { NovedadesShelves.matches(NovedadesFilter.Songs, auraContentKind(it)) },
            hasUpcoming = unique.upcoming.isNotEmpty(),
        )
    }
    var selectedFilter by rememberSaveable { mutableStateOf(NovedadesFilter.All) }
    val filter = if (selectedFilter in visibleFilters) selectedFilter else NovedadesFilter.All
    val filtered = filter != NovedadesFilter.All
    val shown = remember(unique, filter) { unique.filteredBy(filter) }
    // One shelf at a time is expanded into its grid by «Ver todo».
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }

    val pullRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    // Row 348: re-tapping the Novedades tab scrolls back to the top, like Inicio.
    AuraScrollToTopOnReselect(navController) { listState.animateScrollToItem(0) }
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
    val playLocal: (Int, List<Song>, String) -> Unit = { index, queueSongs, title ->
        playerConnection.playQueue(
            ListQueue(
                title = title,
                items = queueSongs.map { it.toMediaItem() },
                startIndex = index,
            ),
        )
    }
    val localMenu: (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            SongMenu(
                originalSong = song,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }
    // The hero's «Reproducir»: the whole release as its own queue, the same album-radio path the classic
    // NewReleaseScreen and the radar's ▶ use. Radar rows have no playlist id, so it is resolved on the tap
    // (one request per tap, never per frame); a failure says so instead of doing nothing.
    val playAlbum: (AlbumItem) -> Unit = { album ->
        scope.launch(Dispatchers.IO) {
            val playlistId = album.playlistId.ifBlank {
                YouTube.album(album.id).onFailure { reportException(it) }.getOrNull()?.album?.playlistId.orEmpty()
            }
            withContext(Dispatchers.Main) {
                if (playlistId.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                } else {
                    playerConnection.playQueue(
                        YouTubeAlbumRadio(playlistId, contextId = ShuffleContexts.album(album.id)),
                    )
                }
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
                    collapseTrailing = true,
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

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .auraScreenBackground(bloom, intensity = 1f),
    ) {
        val paneWidth = maxWidth
        // Same rule as Inicio's carousel: a wide pane that is not a phone on its side.
        val wide = paneWidth >= AURA_WIDE_HERO_MIN_WIDTH && screenHeightDp >= 480
        val shelfScale = if (wide) cardScale * 1.15f else cardScale
        val layout = NovedadesShelfLayout(
            paneWidth = paneWidth,
            columns = auraSeeAllColumns(paneWidth),
            filtered = filtered,
            expandedKey = expandedKey,
            onToggle = { k -> expandedKey = if (expandedKey == k) null else k },
        )
        val activeId = mediaMetadata?.id
        val activeAlbumId = mediaMetadata?.album?.id
        val seeAll = stringResource(R.string.novedades_see_all)
        val seeLess = stringResource(R.string.novedades_see_less)
        // The shelf builders run in a non-composable scope, so every title is resolved here.
        val moreReleasesTitle = stringResource(R.string.novedades_more_releases)
        val featuredTitle = stringResource(R.string.novedades_featured_songs)
        val latestTitle = stringResource(R.string.novedades_latest_albums)
        val recentTitle = stringResource(R.string.novedades_recent_releases)
        val momentTitle = stringResource(R.string.novedades_songs_of_the_moment)
        val listeningTitle = stringResource(R.string.novedades_everyones_listening)
        val comingSoonTitle = stringResource(R.string.novedades_coming_soon)

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
                        collapseTrailing = true,
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
                            NovedadesHeroSkeleton()
                            AuraHomeShelfSkeleton(cardScale = cardScale)
                            repeat(3) { AuraDetailSkeletonRow() }
                        }
                    }
                }

                // Nothing to show and nothing loading: say so, and offer the retry the pull gesture also gives.
                if (!isRefreshing && allShelvesEmpty) {
                    item(key = "aura_novedades_empty", contentType = "aura_novedades_empty") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AuraEmpty(
                                text = stringResource(R.string.release_radar_empty),
                                secondary = stringResource(R.string.novedades_empty_hint),
                            )
                            AuraChip(
                                text = stringResource(R.string.retry),
                                selected = false,
                                onClick = viewModel::refresh,
                            )
                        }
                    }
                }

                if (visibleFilters.size > 1) {
                    item(key = "aura_novedades_filters", contentType = "aura_novedades_filters") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            items(visibleFilters.size, key = { visibleFilters[it].name }) { i ->
                                val f = visibleFilters[i]
                                AuraChip(
                                    text = stringResource(f.labelRes()),
                                    selected = f == filter,
                                    onClick = {
                                        selectedFilter = f
                                        expandedKey = null
                                    },
                                )
                            }
                        }
                    }
                }

                val hero = shown.hero.firstOrNull()
                if (hero != null) {
                    item(key = "aura_novedades_hero", contentType = "aura_novedades_hero") {
                        NovedadesHero(
                            album = hero,
                            age = ages[hero.id],
                            wide = wide,
                            paneWidth = paneWidth,
                            isActive = hero.id == activeAlbumId,
                            isPlaying = isPlaying,
                            onPlay = {
                                if (hero.id == activeAlbumId) playerConnection.togglePlayPause() else playAlbum(hero)
                            },
                            onOpen = { openYt(hero) },
                            onArtist = hero.artists?.firstOrNull()?.id?.let { id ->
                                { navController.navigate("artist/$id") }
                            },
                            onMenu = { ytMenu(hero) },
                        )
                    }
                }

                val heroRest = shown.hero.drop(1)
                val heroRestCarousel: (@Composable () -> Unit)? =
                    if (wide && heroRest.size >= NovedadesShelves.MIN_SHELF_ITEMS) {
                        val carousel: @Composable () -> Unit = {
                            NovedadesWideCarousel(
                                albums = heroRest,
                                paneWidth = paneWidth,
                                screenHeightDp = screenHeightDp,
                                ages = ages,
                                activeAlbumId = activeAlbumId,
                                isPlaying = isPlaying,
                                onOpen = { openYt(it) },
                                onMenu = { ytMenu(it) },
                            )
                        }
                        carousel
                    } else {
                        null
                    }
                novedadesShelf(
                    layout = layout,
                    key = "hero_rest",
                    title = moreReleasesTitle,
                    count = heroRest.size,
                    minItems = 1,
                    itemKey = { heroRest[it].id },
                    seeAllLabel = seeAll,
                    seeLessLabel = seeLess,
                    carouselOverride = heroRestCarousel,
                ) { index, width ->
                    NovedadesYtCard(
                        item = heroRest[index],
                        width = width,
                        cardScale = shelfScale,
                        age = ages[heroRest[index].id],
                        isActive = heroRest[index].id == activeAlbumId,
                        isPlaying = isPlaying,
                        onOpen = openYt,
                        onMenu = ytMenu,
                    )
                }

                novedadesShelf(
                    layout = layout,
                    key = "featured",
                    title = featuredTitle,
                    count = shown.featured.size,
                    itemKey = { shown.featured[it].id },
                    seeAllLabel = seeAll,
                    seeLessLabel = seeLess,
                ) { index, width ->
                    NovedadesYtCard(
                        item = shown.featured[index],
                        width = width,
                        cardScale = shelfScale,
                        age = null,
                        isActive = shown.featured[index].id == activeId,
                        isPlaying = isPlaying,
                        onOpen = openYt,
                        onMenu = ytMenu,
                    )
                }
                novedadesShelf(
                    layout = layout,
                    key = "latest",
                    title = latestTitle,
                    count = shown.newAlbums.size,
                    itemKey = { shown.newAlbums[it].id },
                    seeAllLabel = seeAll,
                    seeLessLabel = seeLess,
                    twoRowsHeight = auraShelfCardStackHeight(auraTypeVisual(AuraContentKind.Album).shelfWidth * shelfScale),
                ) { index, width ->
                    NovedadesYtCard(
                        item = shown.newAlbums[index],
                        width = width,
                        cardScale = shelfScale,
                        age = ages[shown.newAlbums[index].id],
                        isActive = shown.newAlbums[index].id == activeAlbumId,
                        isPlaying = isPlaying,
                        onOpen = openYt,
                        onMenu = ytMenu,
                    )
                }
                novedadesShelf(
                    layout = layout,
                    key = "recent",
                    title = recentTitle,
                    count = shown.radarAlbums.size,
                    itemKey = { shown.radarAlbums[it].id },
                    seeAllLabel = seeAll,
                    seeLessLabel = seeLess,
                    twoRowsHeight = auraShelfCardStackHeight(auraTypeVisual(AuraContentKind.Album).shelfWidth * shelfScale),
                ) { index, width ->
                    NovedadesYtCard(
                        item = shown.radarAlbums[index],
                        width = width,
                        cardScale = shelfScale,
                        age = ages[shown.radarAlbums[index].id],
                        isActive = shown.radarAlbums[index].id == activeAlbumId,
                        isPlaying = isPlaying,
                        onOpen = openYt,
                        onMenu = ytMenu,
                    )
                }
                // 🔴 "Playlists actualizadas" FUERA (punto 9 del dueño, 2026-09-17: *"eliminar la
                // categoría de 'playlists actualizadas' para evitar contenido repetido. Mantener
                // únicamente la sección de novedades"*). Era la única sección de Novedades que no
                // hablaba de lanzamientos: listas que YouTube marca como "actualizadas" y que ya
                // aparecen en Inicio, así que en la práctica repetía contenido de otra pestaña.
                novedadesShelf(
                    layout = layout,
                    key = "moment",
                    title = momentTitle,
                    count = shown.moment.size,
                    itemKey = { shown.moment[it].id },
                    seeAllLabel = seeAll,
                    seeLessLabel = seeLess,
                ) { index, width ->
                    NovedadesLocalSongCard(
                        song = shown.moment[index],
                        width = width,
                        cardScale = shelfScale,
                        isActive = shown.moment[index].id == activeId,
                        isPlaying = isPlaying,
                        onClick = {
                            val queue = shown.moment
                            val at = queue.indexOfFirst { it.id == shown.moment[index].id }.coerceAtLeast(0)
                            playLocal(at, queue, momentTitle)
                        },
                        onLongClick = { localMenu(shown.moment[index]) },
                    )
                }
                novedadesShelf(
                    layout = layout,
                    key = "listening",
                    title = listeningTitle,
                    count = shown.listening.size,
                    minItems = 1,
                    itemKey = { shown.listening[it].id },
                    seeAllLabel = seeAll,
                    seeLessLabel = seeLess,
                ) { index, width ->
                    NovedadesYtCard(
                        item = shown.listening[index],
                        width = width,
                        cardScale = shelfScale,
                        age = null,
                        isActive = shown.listening[index].id == activeId,
                        isPlaying = isPlaying,
                        onOpen = openYt,
                        onMenu = ytMenu,
                    )
                }
                novedadesShelf(
                    layout = layout,
                    key = "coming_soon",
                    title = comingSoonTitle,
                    count = shown.upcoming.size,
                    minItems = 1,
                    itemKey = { shown.upcoming[it].id },
                    seeAllLabel = seeAll,
                    seeLessLabel = seeLess,
                ) { index, width ->
                    val item = shown.upcoming[index]
                    UpcomingCard(
                        item = item,
                        width = width ?: (auraTypeVisual(AuraContentKind.Album).shelfWidth * shelfScale),
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

private data class NovedadesUnique(
    val hero: List<AlbumItem>,
    val featured: List<SongItem>,
    val newAlbums: List<AlbumItem>,
    val radarAlbums: List<AlbumItem>,
    val moment: List<Song>,
    val listening: List<YTItem>,
    val upcoming: List<UpcomingReleaseEntity>,
) {
    /** The same lists after the type filter. [NovedadesFilter.All] is the identity. */
    fun filteredBy(which: NovedadesFilter): NovedadesUnique {
        if (which == NovedadesFilter.All) return this
        fun <T : YTItem> List<T>.keep(): List<T> = this.filter { NovedadesShelves.matches(which, auraContentKind(it)) }
        val songsWanted = NovedadesShelves.matches(which, AuraContentKind.Song)
        return NovedadesUnique(
            hero = hero.keep(),
            featured = featured.keep(),
            newAlbums = newAlbums.keep(),
            radarAlbums = radarAlbums.keep(),
            moment = if (songsWanted) moment else emptyList(),
            listening = listening.keep(),
            upcoming = if (which == NovedadesFilter.Upcoming) upcoming else emptyList(),
        )
    }
}

private fun NovedadesFilter.labelRes(): Int = when (this) {
    NovedadesFilter.All -> R.string.filter_all
    NovedadesFilter.Albums -> R.string.filter_albums
    NovedadesFilter.SinglesAndEps -> R.string.novedades_filter_singles
    NovedadesFilter.Songs -> R.string.filter_songs
    NovedadesFilter.Upcoming -> R.string.novedades_coming_soon
}

@Composable
private fun ReleaseAge.label(): String = stringResource(
    when (this) {
        ReleaseAge.Today -> R.string.today
        ReleaseAge.Yesterday -> R.string.yesterday
        ReleaseAge.ThisWeek -> R.string.this_week
        ReleaseAge.ThisMonth -> R.string.novedades_this_month
    },
)

// ── Shelves ───────────────────────────────────────────────────────────────────────────────────────

/** What every shelf of the screen needs to know about the pane and the user's choices. */
private class NovedadesShelfLayout(
    val paneWidth: Dp,
    val columns: Int,
    val filtered: Boolean,
    val expandedKey: String?,
    val onToggle: (String) -> Unit,
)

/** A grid never draws more than this many cards of one shelf (the radar table can hold hundreds). */
private const val NOVEDADES_GRID_CAP = 60

/**
 * One shelf: a real title with «Ver todo» and either the carousel the shelf always was or — expanded, filtered
 * or too short to scroll — a multi-column grid whose rows are LAZY items of the page (a 60-card grid composes
 * only the rows on screen).
 *
 * [card] draws the item at an index; [width] is null in the carousel (the card keeps its own shelf width) and the
 * cell width in the grid. [onSeeAll] replaces the in-place expansion when a shelf has a screen of its own.
 * [minItems] is [NovedadesShelves.MIN_SHELF_ITEMS] unless the shelf is one the owner never wants hidden.
 */
private fun LazyListScope.novedadesShelf(
    layout: NovedadesShelfLayout,
    key: String,
    title: String,
    count: Int,
    itemKey: (Int) -> Any,
    seeAllLabel: String,
    seeLessLabel: String,
    minItems: Int = NovedadesShelves.MIN_SHELF_ITEMS,
    twoRowsHeight: Dp? = null,
    onSeeAll: (() -> Unit)? = null,
    carouselOverride: (@Composable () -> Unit)? = null,
    card: @Composable (index: Int, width: Dp?) -> Unit,
) {
    // Ver [NovedadesShelves]: un estante con una sola tarjeta no es variedad, es una sección rota
    // con un encabezado que promete más de lo que hay.
    // With a type filter on, a short result is still the answer to what was asked: nothing is hidden.
    if (count < (if (layout.filtered) 1 else minItems)) return
    val expanded = layout.expandedKey == key
    val asGrid = NovedadesShelves.asGrid(layout.filtered, expanded, count)
    val headerKey = "aura_novedades_$key"
    // «Ver todo» only where there is something more to see: a carousel to open, an open grid to close, or
    // the shelf's own destination.
    val action: Pair<String, () -> Unit>? = when {
        onSeeAll != null -> seeAllLabel to onSeeAll
        expanded -> seeLessLabel to { layout.onToggle(key) }
        !asGrid -> seeAllLabel to { layout.onToggle(key) }
        else -> null
    }
    if (!asGrid) {
        item(key = headerKey, contentType = "aura_novedades") {
            Column {
                NovedadesShelfHeader(title = title, action = action)
                when {
                    carouselOverride != null -> carouselOverride()
                    twoRowsHeight != null -> AuraDoubleRowShelf(rowHeight = twoRowsHeight, itemCount = count) {
                        items(count, key = itemKey) { card(it, null) }
                    }
                    else -> AuraShelf {
                        items(count, key = itemKey) { card(it, null) }
                    }
                }
            }
        }
        return
    }
    item(key = headerKey, contentType = "aura_novedades_grid_header") {
        NovedadesShelfHeader(title = title, action = action)
    }
    val gap = AuraSpacing.ShelfItemGap
    val columns = layout.columns
    val cell = (layout.paneWidth - AuraSpacing.Gutter * 2 - gap * (columns - 1)) / columns
    val shownCount = minOf(count, NOVEDADES_GRID_CAP)
    items(
        NovedadesShelves.rowCount(shownCount, columns),
        key = { "${headerKey}_row_$it" },
        contentType = { "aura_novedades_grid_row" },
    ) { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AuraSpacing.Gutter,
                    end = AuraSpacing.Gutter,
                    top = if (row == 0) AuraSpacing.SectionGap else gap,
                ),
        ) {
            for (col in 0 until columns) {
                val index = row * columns + col
                Box(Modifier.width(cell)) {
                    if (index < shownCount) card(index, cell)
                }
            }
        }
    }
}

/** The section title with its «Ver todo» / «Ver menos» pill. */
@Composable
private fun NovedadesShelfHeader(
    title: String,
    action: Pair<String, () -> Unit>?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AuraSpacing.Gutter, end = 10.dp, top = AuraSpacing.SectionTop),
    ) {
        Text(
            text = title,
            style = AuraType.ContentSection,
            color = AuraPalette.OnGround,
            maxLines = 2,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            val (label, onClick) = action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .sizeIn(minHeight = AuraSpacing.MinTouchTarget)
                    .clip(AuraShapes.Pill)
                    .auraClickableInternal(onClick = onClick, contentDescription = "$label · $title")
                    .padding(start = 12.dp, end = 8.dp),
            ) {
                Text(
                    text = label,
                    style = AuraType.Chip,
                    color = AuraPalette.Teal,
                    maxLines = 1,
                )
                AuraIconGlyph(
                    icon = AuraIcons.ChevronRight,
                    contentDescription = null,
                    size = 16.dp,
                    tint = AuraPalette.Teal,
                )
            }
        }
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────────────────────────

/** A YouTube item as a shelf / grid card, with the age pill and the «E» where the data has them. */
@Composable
private fun NovedadesYtCard(
    item: YTItem,
    width: Dp?,
    cardScale: Float,
    age: ReleaseAge?,
    isActive: Boolean,
    isPlaying: Boolean,
    onOpen: (YTItem) -> Unit,
    onMenu: (YTItem) -> Unit,
) {
    val visual = auraTypeVisual(item)
    // In a grid the card takes the cell's width exactly; in a carousel its own shelf width.
    val scale = if (width != null) width / visual.shelfWidth else cardScale
    AuraTypedYtCoverCard(
        item = item,
        cardScale = scale,
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = { onOpen(item) },
        onLongClick = { onMenu(item) },
        badge = {
            // A video already carries its own pill at the top-left corner.
            // The «E» is drawn after the title by AuraTypedYtCoverCard (explicit = item.explicit): not a second one here.
            NovedadesCoverBadges(
                age = age.takeIf { visual.kind != AuraContentKind.Video },
                explicit = false,
            )
        },
    )
}

@Composable
private fun NovedadesLocalSongCard(
    song: Song,
    width: Dp?,
    cardScale: Float,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val visual = auraTypeVisual(AuraContentKind.Song)
    AuraCoverCard(
        title = song.song.title,
        subtitle = song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
        thumbnailUrl = song.song.thumbnailUrl,
        seed = song.id,
        width = width ?: (visual.shelfWidth * cardScale),
        isActive = isActive,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        badge = { NovedadesCoverBadges(age = null, explicit = song.song.explicit) },
    )
}

/** The age pill (top-left) and the «E» (bottom-right) drawn over a cover. */
@Composable
private fun BoxScope.NovedadesCoverBadges(age: ReleaseAge?, explicit: Boolean) {
    if (age != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(AuraShapes.Pill)
                .background(AuraPalette.Ground.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = age.label(),
                style = AuraType.QualityBadge,
                color = AuraPalette.Teal,
                maxLines = 1,
            )
        }
    }
    if (explicit) {
        val description = stringResource(R.string.explicit)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AuraPalette.Ground.copy(alpha = 0.72f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
                .semantics { contentDescription = description },
        ) {
            Text(
                text = "E",
                style = AuraType.QualityBadge,
                color = AuraPalette.OnGround,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun UpcomingCard(
    item: UpcomingReleaseEntity,
    width: Dp,
    onOpen: () -> Unit,
    onPresave: () -> Unit,
) {
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
            width = width,
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

// ── Hero ──────────────────────────────────────────────────────────────────────────────────────────

private val NovedadesHeroShape = RoundedCornerShape(24.dp)

/** Phone: the cover never grows past this; wide: the cover beside the text. */
private val NOVEDADES_HERO_PHONE_COVER_MAX = 320.dp
private val NOVEDADES_HERO_WIDE_COVER = 240.dp
private val NOVEDADES_HERO_PADDING = 18.dp

/**
 * The freshest release, large. A glass plate over the page: the cover itself, decoded tiny and stretched, is the
 * soft backdrop (a 48 px bitmap scaled up IS a blur — no RenderEffect to re-run while the list scrolls), a wash
 * in the COVER'S OWN colours (the same extraction the player and the album page use, once per cover, cached) and
 * the page's ground under the text so it stays legible on any cover. Decorative colours only: the text keeps the
 * app's ink.
 */
@Composable
private fun NovedadesHero(
    album: AlbumItem,
    age: ReleaseAge?,
    wide: Boolean,
    paneWidth: Dp,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onArtist: (() -> Unit)?,
    onMenu: () -> Unit,
) {
    val lean = rememberLeanMode()
    val context = LocalContext.current
    val coverUrl = album.thumbnail
    val coverKey = remember(coverUrl) { AuraCoverFocus.keyOf(coverUrl) }
    LaunchedEffect(coverKey) {
        if (coverUrl.isNotBlank()) AuraBloomCache.ensure(context, coverKey, coverUrl)
    }
    val entry = AuraBloomCache.entry(coverKey)
    val tint = entry?.ground ?: AuraPalette.GroundRaised
    val glow = entry?.accentSeed ?: AuraPalette.Teal

    val kind = auraContentKind(album)
    val typeLabel = stringResource(
        when (kind) {
            AuraContentKind.Single -> R.string.novedades_type_single
            AuraContentKind.Ep -> R.string.novedades_type_ep
            else -> R.string.novedades_type_album
        },
    )
    val kicker = "${stringResource(R.string.novedades_new_label)} · $typeLabel".uppercase(Locale.ROOT)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter)
            .padding(top = AuraSpacing.SectionGap)
            .clip(NovedadesHeroShape)
            .background(AuraPalette.GroundRaised.copy(alpha = 0.6f))
            .border(1.dp, AuraPalette.SurfaceLine, NovedadesHeroShape),
    ) {
        if (coverUrl.isNotBlank() && !lean) {
            AuraStableCoverImage(
                url = coverUrl,
                contentScale = ContentScale.Crop,
                decodeTo = 48,
                seed = album.id,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.55f),
            )
        }
        // The cover's colours as a wash, then the page's ground rising under the text.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.linearGradient(listOf(glow.copy(alpha = 0.32f), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0.25f), AuraPalette.Ground.copy(alpha = 0.72f)),
                    ),
                ),
        )

        val cover: @Composable (Dp) -> Unit = { size ->
            AuraCover(
                thumbnailUrl = coverUrl,
                size = size,
                seed = album.id,
                shape = AuraShapes.Artwork,
                decodeTo = if (lean) 512 else 720,
                modifier = Modifier.auraClickableInternal(onClick = onOpen, contentDescription = album.title),
            )
        }
        val info: @Composable (Modifier) -> Unit = { modifier ->
            NovedadesHeroInfo(
                album = album,
                kicker = kicker,
                age = age,
                isActive = isActive,
                isPlaying = isPlaying,
                onPlay = onPlay,
                onOpen = onOpen,
                onArtist = onArtist,
                onMenu = onMenu,
                modifier = modifier,
            )
        }

        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NOVEDADES_HERO_PADDING),
            ) {
                cover(NOVEDADES_HERO_WIDE_COVER)
                info(Modifier.weight(1f))
            }
        } else {
            val inner = paneWidth - AuraSpacing.Gutter * 2 - NOVEDADES_HERO_PADDING * 2
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NOVEDADES_HERO_PADDING),
            ) {
                cover(minOf(inner, NOVEDADES_HERO_PHONE_COVER_MAX))
                info(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NovedadesHeroInfo(
    album: AlbumItem,
    kicker: String,
    age: ReleaseAge?,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onArtist: (() -> Unit)?,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistLine = album.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AuraSectionLabel(text = kicker, color = AuraPalette.Teal)
        Text(
            text = album.title,
            style = AuraType.SheetTitle,
            color = AuraPalette.OnGround,
            maxLines = 3,
            overflow = AuraDefaultOverflow,
        )
        if (artistLine != null) {
            Text(
                text = artistLine,
                style = AuraType.PlayerArtist,
                color = AuraPalette.OnGroundMuted,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
                modifier = if (onArtist != null) {
                    Modifier
                        .clip(AuraShapes.Pill)
                        .auraClickableInternal(onClick = onArtist, contentDescription = artistLine)
                } else {
                    Modifier
                },
            )
        }
        // Date chip, year and «E»: only what the data has.
        val year = album.year
        if (age != null || year != null || album.explicit) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                if (age != null) {
                    Box(
                        modifier = Modifier
                            .clip(AuraShapes.Pill)
                            .background(AuraPalette.Teal.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = age.label(),
                            style = AuraType.QualityBadge,
                            color = AuraPalette.Teal,
                            maxLines = 1,
                        )
                    }
                }
                if (year != null) {
                    AuraTechnicalText(text = year.toString(), color = AuraPalette.OnGroundMuted)
                }
                if (album.explicit) {
                    val description = stringResource(R.string.explicit)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AuraPalette.SurfaceFill)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .semantics { contentDescription = description },
                    ) {
                        Text(
                            text = "E",
                            style = AuraType.QualityBadge,
                            color = AuraPalette.OnGround,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            AuraDetailActionButton(
                icon = if (isActive && isPlaying) AuraIcons.Pause else AuraIcons.Play,
                label = stringResource(if (isActive && isPlaying) R.string.pause else R.string.play),
                onClick = onPlay,
                accent = true,
                modifier = Modifier.weight(1f),
            )
            AuraDetailActionButton(
                icon = AuraIcons.Album,
                label = stringResource(R.string.open),
                onClick = onOpen,
                accent = false,
                modifier = Modifier.weight(1f),
            )
            AuraDetailCircleButton(
                icon = AuraIcons.More,
                contentDescription = stringResource(R.string.more_options),
                onClick = onMenu,
            )
        }
    }
}

/** The hero's cold-load placeholder: same plate, no content. */
@Composable
private fun NovedadesHeroSkeleton() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter)
            .padding(top = AuraSpacing.SectionGap)
            .height(300.dp)
            .clip(NovedadesHeroShape)
            .background(AuraPalette.SurfaceFill),
    )
}

// ── Wide carousel ─────────────────────────────────────────────────────────────────────────────────

private val NovedadesCarouselScrim: Brush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f)),
)

/**
 * «Más estrenos» on a wide pane: Inicio's carousel (registry 447, 461) — the multi-browse strategy lays several
 * masked, rounded cards of the same size across the width, the next ones peeking smaller — with the release's
 * title and artist over its cover. The re-key on the count is the guard Inicio documents: a CarouselState laid out
 * for another count renders blank.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NovedadesWideCarousel(
    albums: List<AlbumItem>,
    paneWidth: Dp,
    screenHeightDp: Int,
    ages: Map<String, ReleaseAge>,
    activeAlbumId: String?,
    isPlaying: Boolean,
    onOpen: (AlbumItem) -> Unit,
    onMenu: (AlbumItem) -> Unit,
) {
    val size = auraWideHeroCardSize(paneWidth, screenHeightDp)
    key(albums.size) {
        val state = rememberCarouselState { albums.size }
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = size.width,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AuraSpacing.SectionGap)
                .height(size.height),
        ) { index ->
            val album = albums.getOrNull(index)
            if (album != null) {
                val age = ages[album.id]
                val placeholder = remember(album.id) { AuraPalette.coverPlaceholder(album.id) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .maskClip(AuraShapes.Card)
                        .maskBorder(BorderStroke(1.dp, AuraPalette.SurfaceLine), AuraShapes.Card)
                        .background(placeholder)
                        .combinedClickable(
                            onClick = { onOpen(album) },
                            onClickLabel = album.title,
                            onLongClick = { onMenu(album) },
                        ),
                ) {
                    if (album.thumbnail.isNotBlank()) {
                        AuraStableCoverImage(
                            url = album.thumbnail,
                            contentScale = ContentScale.Crop,
                            decodeTo = 640,
                            seed = album.id,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(Modifier.fillMaxSize().background(NovedadesCarouselScrim))
                    if (album.id == activeAlbumId && isPlaying) {
                        AuraPlayingBars(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                        )
                    }
                    NovedadesCoverBadges(age = age, explicit = album.explicit)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 12.dp),
                    ) {
                        Text(
                            text = album.title,
                            style = AuraType.RowTitle,
                            color = AuraPalette.OnGround,
                            maxLines = 2,
                            overflow = AuraDefaultOverflow,
                        )
                        album.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = AuraType.RowSubtitle,
                                color = AuraPalette.OnGroundMuted,
                                maxLines = 1,
                                overflow = AuraDefaultOverflow,
                            )
                        }
                    }
                }
            }
        }
    }
}
