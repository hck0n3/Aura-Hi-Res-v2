package iad1tya.echo.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.db.OfflineCollection
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.extensions.tryOrNull
import iad1tya.echo.music.ui.newui.AURA_QUICK_TILE_GAP
import iad1tya.echo.music.ui.newui.AURA_QUICK_TILE_HEIGHT
import iad1tya.echo.music.ui.newui.AuraAppleCoverDividerInset
import iad1tya.echo.music.ui.newui.AuraAppleListRowFrame
import iad1tya.echo.music.ui.newui.AuraCoverCard
import iad1tya.echo.music.ui.newui.AuraDefaultOverflow
import iad1tya.echo.music.ui.newui.AuraIconGlyph
import iad1tya.echo.music.ui.newui.AuraIcons
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraScreenHeader
import iad1tya.echo.music.ui.newui.AuraSearchInputBar
import iad1tya.echo.music.ui.newui.AuraSectionHeader
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraSongRow
import iad1tya.echo.music.ui.newui.AuraSpacing
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.OfflineHideKeyboardOnScroll
import iad1tya.echo.music.ui.newui.OfflineSearchFilter
import iad1tya.echo.music.ui.newui.OfflineSearchIndex
import iad1tya.echo.music.ui.newui.OfflineSearchUi
import iad1tya.echo.music.ui.newui.ScrollStateBusReporter
import iad1tya.echo.music.ui.newui.auraAppleDurationLabel
import iad1tya.echo.music.ui.newui.auraClickableInternal
import iad1tya.echo.music.ui.newui.auraQuickAccessColumns
import iad1tya.echo.music.ui.newui.auraScreenBackground
import iad1tya.echo.music.ui.newui.offlineArtistItems
import iad1tya.echo.music.ui.newui.offlineCollectionSubtitle
import iad1tya.echo.music.ui.newui.offlineSearchItems
import iad1tya.echo.music.ui.newui.rememberAuraBloom
import iad1tya.echo.music.ui.newui.rememberAuraVoiceSearch
import iad1tya.echo.music.ui.newui.rememberOfflineLibrary
import iad1tya.echo.music.ui.newui.rememberOfflineSearchResult
import iad1tya.echo.music.ui.newui.rememberOfflineSearchUi
import iad1tya.echo.music.ui.utils.formatFileSize
import iad1tya.echo.music.ui.utils.rememberIsTvOrCar
import iad1tya.echo.music.ui.utils.tvFocusable
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Offline body: shown instead of network feeds when [OfflineModeKey] is ON. Disable toggle lives ON
 * THIS SCREEN (owner request) — not only in Settings.
 *
 * OWNER DIRECTIVE 2026-09-14 ("el modo sin conexión puede reproducir lo que está en caché, lo
 * descargado y lo exportado a video y a MP3"): it lists EVERYTHING offline playback can serve —
 * downloads, songs whose listen-cache copy is COMPLETE, exported MP3s and exported videos. The
 * player already plays all four with no network (MusicService's download / exported-URI / full
 * listen-cache branches); before, this screen only listed downloads, so the rest was unreachable.
 * The set itself is [rememberOfflineLibrary]: ONE definition shared with the search below.
 *
 * OWNER 2026-10-20 ("el modo sin conexión también lo veo básico y me gustaría que tenga su propio buscador, pero que
 * solo busque de las que están disponibles"): a header that says how much is available, the offline search (a field that
 * filters ONLY that set, in place, grouped as Canciones / Álbumes / Listas / Artistas), quick tiles (Reproducir todo,
 * Aleatorio, Mis descargas, Almacenamiento), the cover shelves and what was played recently, in the colours of the cover
 * that is playing, and two columns of rows once the pane is wide (unfolded).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadedOnlyView(
    navController: NavController,
    modifier: Modifier = Modifier,
    /**
     * 🔴 true cuando aquí nos ha traído la FALTA DE RED y no el interruptor del dueño (punto 4,
     * 2026-09-17). Solo cambia el cartel de arriba, porque el cuerpo — lo descargado, lo cacheado
     * completo y lo exportado — es el mismo en los dos casos.
     *
     * Y el cartel tiene que cambiar: el del manual ofrece "Desactivar", que con el automático sería
     * un placebo — apagaría un interruptor que ya está apagado y la app seguiría sin conexión porque
     * sigue sin haber red. En automático el cartel explica lo que pasa y promete lo que hace
     * (volver solo), que es lo único cierto.
     */
    automatic: Boolean = false,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    var offlineMode by rememberPreference(OfflineModeKey, false)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val library = rememberOfflineLibrary()
    val ui = rememberOfflineSearchUi(playerConnection, navController, onDismiss = {})
    val index = remember(library) { OfflineSearchIndex(library.allSongs, library.albums, library.playlists) }
    val availableById = remember(library) { library.allSongs.associateBy { it.id } }

    // Owner 2026-10-18: «en el modo sin conexión quiero que muestre SIEMPRE las listas y álbumes descargados». Both shelves
    // come first, and the empty state only appears when there is truly nothing to play or open.
    val nothingOffline = library.isEmpty && library.albums.isEmpty() && library.playlists.isEmpty()

    // ── Search (state of this screen; never stored, never sent anywhere) ─────────────────────────────────────────────
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var fieldActive by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(OfflineSearchFilter.ALL) }
    var artistId by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val searching = query.text.isNotBlank()
    val result by rememberOfflineSearchResult(index, query.text)
    val openArtist = remember(index, artistId) { artistId?.let { id -> index.artists.firstOrNull { it.id == id } } }
    val openArtistSongs = remember(index, openArtist) { openArtist?.let { index.songsOf(it.id) }.orEmpty() }
    BackHandler(enabled = openArtist != null || searching) {
        if (openArtist != null) {
            artistId = null
        } else {
            query = TextFieldValue("")
            fieldActive = false
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    val voice = rememberAuraVoiceSearch(
        onPartial = { spoken -> query = TextFieldValue(spoken, TextRange(spoken.length)) },
        onResult = { spoken ->
            query = TextFieldValue(spoken, TextRange(spoken.length))
            fieldActive = true
        },
    )

    // ── Recently played that plays with no network ──────────────────────────────────────────────────────────────────
    val recentSongs by remember(database) { database.recentlyPlayedSongs(RECENT_SCAN) }.collectAsState(initial = emptyList())
    val recentOffline = remember(recentSongs, availableById) {
        recentSongs.mapNotNull { availableById[it.id] }.take(RECENT_SHOWN)
    }

    // ── Storage used by the downloads: one measurement per change of the list, never a loop ─────────────────────────
    val downloadUtil = LocalDownloadUtil.current
    var storageBytes by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(library.downloaded.size) {
        storageBytes = if (library.downloaded.isEmpty()) {
            -1L
        } else {
            withContext(Dispatchers.IO) { tryOrNull { downloadUtil.downloadCache.cacheSpace } ?: -1L }
        }
    }

    val downloadsTitle = stringResource(R.string.downloaded_songs)
    val cachedTitle = stringResource(R.string.cached_playlist)
    val exportedTitle = stringResource(R.string.action_exported)
    val exportedVideosTitle = stringResource(R.string.exported_videos_playlist)
    val offlineQueueTitle = stringResource(R.string.offline_home_title)
    val recentTitle = stringResource(R.string.offline_recent_title)

    val bloom = rememberAuraBloom(ui.playingId)
    val listState = rememberLazyListState()
    ScrollStateBusReporter { listState.isScrollInProgress }
    OfflineHideKeyboardOnScroll(listState)

    BoxWithConstraints(modifier = modifier.fillMaxSize().auraScreenBackground(bloom, intensity = 1f)) {
        // Wide (unfolded): two columns of song rows and bigger cover cards. The phone keeps the single column it had.
        val twoColumns = maxWidth >= OFFLINE_TWO_COLUMNS_MIN_WIDTH
        val columns = if (twoColumns) 2 else 1
        val cardWidth = if (twoColumns) OFFLINE_WIDE_CARD_WIDTH else OFFLINE_CARD_WIDTH
        val paneInner = maxWidth - AuraSpacing.Gutter * 2

        val downloadedRows = remember(library.downloaded, columns) { library.downloaded.chunked(columns) }
        val cachedRows = remember(library.cached, columns) { library.cached.chunked(columns) }
        val exportedRows = remember(library.exported, columns) { library.exported.chunked(columns) }
        val exportedVideoRows = remember(library.exportedVideos, columns) { library.exportedVideos.chunked(columns) }

        LazyColumn(
            state = listState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "offline_header", contentType = "offline_header") {
                AuraScreenHeader(
                    title = stringResource(R.string.offline_home_title),
                    label = if (library.isEmpty) {
                        null
                    } else {
                        pluralStringResource(
                            R.plurals.offline_songs_available,
                            library.allSongs.size,
                            library.allSongs.size,
                        ).uppercase(Locale.ROOT)
                    },
                )
            }

            // The offline search: filters ONLY what is available offline. Nothing to search when nothing is available.
            if (!nothingOffline) {
                item(key = "offline_search", contentType = "offline_search") {
                    AuraSearchInputBar(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.offline_search_placeholder),
                        active = fieldActive || searching,
                        onLeadingClick = {
                            if (fieldActive || searching) {
                                query = TextFieldValue("")
                                fieldActive = false
                                focusManager.clearFocus()
                                keyboard?.hide()
                            } else {
                                fieldActive = true
                                runCatching { focusRequester.requestFocus() }
                            }
                        },
                        onSubmit = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        },
                        onClear = { query = TextFieldValue("") },
                        onVoice = voice.launch,
                        voiceActive = voice.active,
                        focusRequester = focusRequester,
                        onFieldTap = { fieldActive = true },
                        offlineMode = true,
                        sourceIsLocal = true,
                    )
                }
            }

            when {
                openArtist != null -> offlineArtistItems(ui, openArtist, openArtistSongs) { artistId = null }

                searching -> offlineSearchItems(
                    ui = ui,
                    result = result,
                    filter = filter,
                    onFilter = { filter = it },
                    onOpenArtist = { artistId = it.id },
                )

                else -> {
                    item(key = "offline_banner", contentType = "offline_banner") {
                        OfflineStatusCard(
                            automatic = automatic,
                            // In the empty state the same action is the big button below.
                            showDisable = !nothingOffline,
                            onDisable = { offlineMode = false },
                        )
                    }

                    if (nothingOffline) {
                        item(key = "offline_empty", contentType = "offline_empty") {
                            OfflineEmptyState(automatic = automatic, onDisable = { offlineMode = false })
                        }
                        return@LazyColumn
                    }

                    // Quick tiles: the things you do first when you have no data.
                    item(key = "offline_tiles", contentType = "offline_tiles") {
                        val tiles = buildList {
                            if (library.allSongs.isNotEmpty()) {
                                add(
                                    OfflineTile(
                                        icon = AuraIcons.Play,
                                        title = stringResource(R.string.play_all),
                                        subtitle = pluralStringResource(
                                            R.plurals.n_song, library.allSongs.size, library.allSongs.size,
                                        ),
                                        onClick = { ui.playSongs(offlineQueueTitle, library.allSongs, 0, false) },
                                    ),
                                )
                                add(
                                    OfflineTile(
                                        icon = AuraIcons.Shuffle,
                                        title = stringResource(R.string.shuffle),
                                        subtitle = stringResource(R.string.offline_tile_shuffle_hint),
                                        onClick = { ui.playSongs(offlineQueueTitle, library.allSongs, 0, true) },
                                    ),
                                )
                            }
                            if (library.downloaded.isNotEmpty()) {
                                add(
                                    OfflineTile(
                                        icon = AuraIcons.Downloaded,
                                        title = stringResource(R.string.offline_tile_downloads),
                                        subtitle = pluralStringResource(
                                            R.plurals.n_song, library.downloaded.size, library.downloaded.size,
                                        ),
                                        onClick = { navController.navigate("auto_playlist/downloaded") },
                                    ),
                                )
                                add(
                                    OfflineTile(
                                        icon = AuraIcons.Download,
                                        title = stringResource(R.string.storage),
                                        // Until the first measurement lands nothing is printed: a "0 B" that later
                                        // jumps would be a number the screen never knew.
                                        subtitle = storageBytes.takeIf { it >= 0L }?.let { formatFileSize(it) },
                                        onClick = { navController.navigate("settings/storage") },
                                    ),
                                )
                            }
                        }
                        OfflineTiles(tiles = tiles, paneInner = paneInner, modifier = Modifier.padding(top = 4.dp))
                    }

                    if (library.albums.isNotEmpty()) {
                        item(key = "offline_albums", contentType = "offline_collections") {
                            OfflineCollectionRow(
                                title = stringResource(R.string.offline_downloaded_albums),
                                collections = library.albums,
                                cardWidth = cardWidth,
                                onClick = { navController.navigate("album/${it.id}") },
                                onPlay = { collection ->
                                    scope.launch {
                                        val songs = withContext(Dispatchers.Default) { index.songsOfAlbum(collection.id) }
                                        ui.playSongs(collection.title, songs, 0, false)
                                    }
                                },
                            )
                        }
                    }
                    if (library.playlists.isNotEmpty()) {
                        item(key = "offline_playlists", contentType = "offline_collections") {
                            OfflineCollectionRow(
                                title = stringResource(R.string.offline_downloaded_playlists),
                                collections = library.playlists,
                                cardWidth = cardWidth,
                                onClick = { navController.navigate("local_playlist/${it.id}") },
                                onPlay = { collection ->
                                    scope.launch {
                                        // Only the songs of the list that play with no network, in the list's order.
                                        val songs = withContext(Dispatchers.IO) {
                                            database.playlistSongs(collection.id).first()
                                                .mapNotNull { availableById[it.song.id] }
                                        }
                                        ui.playSongs(collection.title, songs, 0, false)
                                    }
                                },
                            )
                        }
                    }

                    if (recentOffline.isNotEmpty()) {
                        item(key = "offline_recent", contentType = "offline_recent") {
                            OfflineRecentRow(
                                title = recentTitle,
                                songs = recentOffline,
                                cardWidth = cardWidth,
                                ui = ui,
                            )
                        }
                    }

                    // One section per offline source; a tap plays that section as the queue.
                    fun section(sectionKey: String, title: String, songs: List<Song>, rows: List<List<Song>>) {
                        if (songs.isEmpty()) return
                        item(key = "header_$sectionKey", contentType = "offline_section_header") {
                            AuraSectionHeader(
                                title = title,
                                onPlayAll = { ui.playSongs(title, songs, 0, false) },
                            )
                        }
                        itemsIndexed(
                            items = rows,
                            key = { _, row -> "${sectionKey}_${row.first().id}" },
                            contentType = { _, _ -> "offline_song_row" },
                        ) { rowIndex, row ->
                            Row(modifier = Modifier.fillMaxWidth().animateItem()) {
                                row.forEachIndexed { column, song ->
                                    val songIndex = rowIndex * columns + column
                                    Box(modifier = Modifier.weight(1f)) {
                                        OfflineSongCell(
                                            song = song,
                                            songIndex = songIndex,
                                            songs = songs,
                                            title = title,
                                            ui = ui,
                                        )
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    section("downloaded", downloadsTitle, library.downloaded, downloadedRows)
                    section("cached", cachedTitle, library.cached, cachedRows)
                    section("exported", exportedTitle, library.exported, exportedRows)
                    section("exported_videos", exportedVideosTitle, library.exportedVideos, exportedVideoRows)
                }
            }
        }
    }
}

/** The Apple-style renglón of a song of the offline sections (same design as Local and the library lists). */
@Composable
private fun OfflineSongCell(
    song: Song,
    songIndex: Int,
    songs: List<Song>,
    title: String,
    ui: OfflineSearchUi,
) {
    // Apple-style renglones (owner request): the offline body is a PURE song list, with the
    // same row design as Local and the library lists, inside BOTH shells.
    AuraAppleListRowFrame(
        showDivider = songIndex < songs.lastIndex,
        dividerInset = AuraAppleCoverDividerInset,
    ) {
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
            format = song.format,
            durationLabel = auraAppleDurationLabel(song.song.duration.takeIf { it > 0 }),
            swipeMediaItem = song.toMediaItem(),
            onClick = {
                if (song.id == ui.playingId) {
                    ui.togglePlayPause()
                } else {
                    ui.playSongs(title, songs, songIndex, false)
                }
            },
            onLongClick = { ui.longPressSongMenu(song) },
            onMenuClick = { ui.showSongMenu(song) },
        )
    }
}

private val OFFLINE_TWO_COLUMNS_MIN_WIDTH = 600.dp
private val OFFLINE_CARD_WIDTH = 140.dp
private val OFFLINE_WIDE_CARD_WIDTH = 168.dp
private const val RECENT_SCAN = 60
private const val RECENT_SHOWN = 15

// ── Status ────────────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Why you are looking at this and what the app promises: the old banner, in the app's own material (SurfaceFill plate, hairline,
 * the play button's gradient for the glyph) instead of a Material `secondaryContainer`. "Volver online" exists only for the
 * manual switch — with the automatic mode it would be a placebo (see [DownloadedOnlyView]).
 */
@Composable
private fun OfflineStatusCard(
    automatic: Boolean,
    showDisable: Boolean,
    onDisable: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter, vertical = 6.dp)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AuraPalette.PlayButtonGradient),
        ) {
            AuraIconGlyph(
                icon = AuraIcons.CloudOff,
                contentDescription = null,
                size = 22.dp,
                tint = AuraPalette.OnAccent,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(if (automatic) R.string.offline_auto_title else R.string.offline_mode),
                style = AuraType.RowTitle,
                color = AuraPalette.OnGround,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
            Text(
                text = stringResource(if (automatic) R.string.offline_auto_hint else R.string.offline_banner_hint),
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundMuted,
                maxLines = 3,
                overflow = AuraDefaultOverflow,
            )
        }
        if (!automatic && showDisable) {
            TextButton(onClick = onDisable) {
                Text(
                    text = stringResource(R.string.offline_disable),
                    style = AuraType.RowSubtitle,
                    color = AuraPalette.Teal,
                )
            }
        }
    }
}

/**
 * Nothing downloaded, cached or exported. Calm, and with the one thing that helps: how to get music here, and — only when the
 * user chose offline mode themselves — the button that leaves it. With the automatic mode there is nothing to press (it comes
 * back by itself), so it says that instead of showing a button that would do nothing.
 */
@Composable
private fun OfflineEmptyState(
    automatic: Boolean,
    onDisable: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter + 8.dp, vertical = 36.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AuraPalette.PlayButtonGradient),
        ) {
            AuraIconGlyph(
                icon = AuraIcons.Downloaded,
                contentDescription = null,
                size = 34.dp,
                tint = AuraPalette.OnAccent,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.offline_empty_title),
            style = AuraType.RowTitle,
            color = AuraPalette.OnGround,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.offline_empty_all),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.offline_empty_how),
            style = AuraType.RowSubtitle,
            color = AuraPalette.OnGroundFaint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        if (automatic) {
            Text(
                text = stringResource(R.string.offline_empty_auto),
                style = AuraType.RowSubtitle,
                color = AuraPalette.Teal,
                textAlign = TextAlign.Center,
            )
        } else {
            val cta = stringResource(R.string.offline_empty_cta)
            Text(
                text = cta,
                style = AuraType.RowTitle,
                color = AuraPalette.OnAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(AuraShapes.Pill)
                    .background(AuraPalette.PlayButtonGradient)
                    .auraClickableInternal(onClick = onDisable, contentDescription = cta)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

// ── Quick tiles ───────────────────────────────────────────────────────────────────────────────────────────────────────

private class OfflineTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val onClick: () -> Unit,
)

/** The tiles of Inicio's quick access, for offline: 2 per row (4 once the pane is really wide). */
@Composable
private fun OfflineTiles(tiles: List<OfflineTile>, paneInner: Dp, modifier: Modifier = Modifier) {
    val isTvOrCar = rememberIsTvOrCar()
    val columns = auraQuickAccessColumns(paneInner)
    Column(
        verticalArrangement = Arrangement.spacedBy(AURA_QUICK_TILE_GAP),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuraSpacing.Gutter),
    ) {
        tiles.chunked(columns).forEach { rowTiles ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(AURA_QUICK_TILE_GAP),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowTiles.forEach { tile ->
                    OfflineTileCard(tile = tile, isTvOrCar = isTvOrCar, modifier = Modifier.weight(1f))
                }
                repeat(columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One tile, drawn like the Inicio ones: SurfaceFill plate, hairline, a full-height square with the play button's gradient. */
@Composable
private fun OfflineTileCard(tile: OfflineTile, isTvOrCar: Boolean, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(AURA_QUICK_TILE_HEIGHT)
            // TV/car: the ring sits ABOVE the clip and the clickable so it draws outside them. No-op on touch.
            .tvFocusable(isTvOrCar, AuraShapes.Card, scaleFocused = 1f)
            .clip(AuraShapes.Card)
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, AuraShapes.Card)
            .auraClickableInternal(onClick = tile.onClick, contentDescription = tile.title),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AURA_QUICK_TILE_HEIGHT)
                .background(AuraPalette.PlayButtonGradient),
        ) {
            AuraIconGlyph(
                icon = tile.icon,
                contentDescription = null,
                size = 24.dp,
                tint = AuraPalette.OnAccent,
            )
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = tile.title,
                style = AuraType.MiniTitle,
                color = AuraPalette.OnGround,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
            if (tile.subtitle != null) {
                Text(
                    text = tile.subtitle,
                    style = AuraType.MiniArtist,
                    color = AuraPalette.OnGroundMuted,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
    }
}

// ── Shelves ───────────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * A shelf of downloaded albums or playlists. A collection that is only partly downloaded says so ("3 de 12"): offline,
 * promising twelve songs when three will play is worse than saying three. The ▶ on the cover plays what plays, without opening it.
 */
@Composable
private fun OfflineCollectionRow(
    title: String,
    collections: List<OfflineCollection>,
    cardWidth: Dp,
    onClick: (OfflineCollection) -> Unit,
    onPlay: (OfflineCollection) -> Unit,
) {
    Column {
        AuraSectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.ShelfItemGap),
        ) {
            items(collections, key = { it.id }) { collection ->
                AuraCoverCard(
                    title = collection.title,
                    fullTitle = true,
                    subtitle = offlineCollectionSubtitle(collection),
                    thumbnailUrl = collection.thumbnailUrl,
                    seed = collection.id,
                    width = cardWidth,
                    onClick = { onClick(collection) },
                    onPlay = { onPlay(collection) },
                )
            }
        }
    }
}

/** What was played recently AND plays with no network: tap = that list as the queue, from the tapped song. */
@Composable
private fun OfflineRecentRow(
    title: String,
    songs: List<Song>,
    cardWidth: Dp,
    ui: OfflineSearchUi,
) {
    Column {
        AuraSectionHeader(title = title, onPlayAll = { ui.playSongs(title, songs, 0, false) })
        LazyRow(
            contentPadding = PaddingValues(horizontal = AuraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.ShelfItemGap),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                AuraCoverCard(
                    title = song.song.title,
                    subtitle = song.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                    thumbnailUrl = song.song.thumbnailUrl,
                    seed = song.id,
                    width = cardWidth,
                    isActive = song.id == ui.playingId,
                    isPlaying = ui.isPlaying,
                    onClick = {
                        if (song.id == ui.playingId) ui.togglePlayPause() else ui.playSongs(title, songs, index, false)
                    },
                    onLongClick = { ui.longPressSongMenu(song) },
                )
            }
        }
    }
}
