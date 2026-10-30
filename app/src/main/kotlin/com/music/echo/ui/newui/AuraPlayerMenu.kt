package iad1tya.echo.music.ui.newui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.LocalRingtoneViewModel
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ExportedSongIdsKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.ExportingSongIdsKey
import iad1tya.echo.music.constants.ListItemHeight
import iad1tya.echo.music.extensions.toggleRepeatMode
import iad1tya.echo.music.listentogether.RoomRole
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.rememberResolvedAlbum
import iad1tya.echo.music.navigateAsTab
import iad1tya.echo.music.playback.SongModeAvailability
import iad1tya.echo.music.playback.SongModePlanning
import iad1tya.echo.music.playback.removeSongDownloads
import iad1tya.echo.music.ui.component.BottomSheetState
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.ui.component.VolumeSlider
import iad1tya.echo.music.ui.menu.AddToPlaylistDialog
import iad1tya.echo.music.ui.menu.ListenTogetherDialog
import iad1tya.echo.music.ui.menu.TempoPitchDialog
import iad1tya.echo.music.echomusic.AudioDeviceBottomSheet
import iad1tya.echo.music.ui.player.rememberHidePlayerVolume
import iad1tya.echo.music.ui.player.showPlayerVolumeControl
import iad1tya.echo.music.utils.shareSong
import iad1tya.echo.music.utils.isLocalMediaId
import iad1tya.echo.music.utils.needsOnlineBrowseResolution
import iad1tya.echo.music.utils.rememberEnumPreference
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.resolveOnlineAlbumBrowseId
import iad1tya.echo.music.utils.resolveOnlineArtistBrowseId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

private const val SECTION_PLAYBACK = "playback"
private const val SECTION_LIBRARY = "library"
private const val SECTION_MORE = "more"

/**
 * # "Interfaz nueva" — Menú del reproductor (acordeón)
 *
 * Header always visible; the rest folds into one-open accordion sections so the sheet stays short
 * and does not duplicate quick-access chrome (like / dislike / download icon).
 *
 * **Presentation only.** Every `onClick` still calls the same service / dialog / route the classic
 * menus use. The sleep timer row (2026-10-13) opens the shared [SleepTimerDialog].
 */
@Composable
fun AuraPlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val ringtoneViewModel = LocalRingtoneViewModel.current

    val playerVolume by playerConnection.service.playerVolume.collectAsState()
    val hidePlayerVolume = rememberHidePlayerVolume()
    val castHandler = remember(playerConnection) {
        runCatching { playerConnection.service.castConnectionHandler }.getOrNull()
    }
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castVolume by castHandler?.castVolume?.collectAsState() ?: remember { mutableFloatStateOf(1f) }
    val castDeviceName by castHandler?.castDeviceName?.collectAsState()
        ?: remember { mutableStateOf<String?>(null) }

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)

    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST
    val pendingSuggestions by listenTogetherManager?.pendingSuggestions
        ?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val librarySong by database
        .songWithEquivalent(
            mediaMetadata.id,
            mediaMetadata.title,
            mediaMetadata.artists.firstOrNull()?.name,
        )
        .collectAsState(initial = null)
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val videoMode by playerConnection.videoMode.collectAsState()

    val artists = remember(mediaMetadata.artists) { mediaMetadata.artists }
    val resolvedAlbum = rememberResolvedAlbum(
        songId = mediaMetadata.id,
        initial = mediaMetadata.album,
        dbAlbumId = librarySong?.song?.albumId,
        dbAlbumName = librarySong?.song?.albumName,
    )

    val (exportingSongIds) = rememberPreference(ExportingSongIdsKey, "")
    val (enableExportAsMp3) = rememberPreference(iad1tya.echo.music.constants.EnableExportAsMp3Key, true)
    val (exportedSongIds) = rememberPreference(ExportedSongIdsKey, "")
    val (exportedVideoIds) = rememberPreference(ExportedVideoIdsKey, "")
    val isExporting = remember(exportingSongIds, mediaMetadata.id) {
        exportingSongIds.split(",").contains(mediaMetadata.id)
    }
    val isExported = remember(exportedSongIds, exportedVideoIds, mediaMetadata.id) {
        val id = mediaMetadata.id
        exportedSongIds.split(",").contains(id) || exportedVideoIds.split(",").contains(id)
    }
    val isExportedVideo = remember(exportedVideoIds, mediaMetadata.id) {
        exportedVideoIds.split(",").contains(mediaMetadata.id)
    }
    val isLocalTrack = mediaMetadata.id.isLocalMediaId() || currentSong?.song?.isLocal == true

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showListenTogetherDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }
    var showPitchTempoDialog by rememberSaveable { mutableStateOf(false) }
    var showAudioDeviceSheet by rememberSaveable { mutableStateOf(false) }
    var showExportFormatDialog by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }
    // Start collapsed: pinned rows (artist/album/EQ/details/settings) are the only visible actions.
    var openSection by rememberSaveable { mutableStateOf("") }

    fun toggleSection(key: String) {
        openSection = if (openSection == key) "" else key
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        songIdsForMembership = listOf(mediaMetadata.id),
        onGetSong = {
            database.withTransaction { insert(mediaMetadata) }
            onDismiss()
            listOf(mediaMetadata.id)
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    ListenTogetherDialog(
        visible = showListenTogetherDialog,
        mediaMetadata = mediaMetadata,
        onDismiss = { showListenTogetherDialog = false },
    )

    if (showPitchTempoDialog) {
        TempoPitchDialog(onDismiss = { showPitchTempoDialog = false })
    }

    if (showAudioDeviceSheet) {
        AudioDeviceBottomSheet(onDismiss = { showAudioDeviceSheet = false })
    }

    if (showSleepTimerDialog) {
        iad1tya.echo.music.ui.component.SleepTimerDialog(onDismiss = { showSleepTimerDialog = false })
    }

    if (showExportFormatDialog) {
        // The unified download sheet (owner 2026-10-19): the «Exportar» row now opens the same three outputs as the
        // player's download button (sin conexión · MP3 · vídeo) instead of an export-only chooser.
        AuraDownloadSheet(
            songs = listOf(mediaMetadata.toAuraDownloadItem()),
            title = mediaMetadata.title,
            offlineState = download?.state ?: Download.STATE_STOPPED,
            onDismiss = { showExportFormatDialog = false },
            onDone = onDismiss,
            deferOfflineWhileLiveVideo = videoMode && playerConnection.mediaMetadata.value?.id == mediaMetadata.id,
        )
    }

    if (showSelectArtistDialog) {
        ListDialog(onDismiss = { showSelectArtistDialog = false }) {
            items(artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            coroutineScope.launch {
                                val browseId = if (needsOnlineBrowseResolution(artist.id)) {
                                    withContext(Dispatchers.IO) {
                                        resolveOnlineArtistBrowseId(artist.name)
                                    }
                                } else {
                                    artist.id
                                }
                                if (!browseId.isNullOrBlank()) {
                                    navController.navigate("artist/$browseId")
                                } else {
                                    navController.navigate(
                                        "search/${URLEncoder.encode(artist.name, "UTF-8")}"
                                    )
                                }
                                showSelectArtistDialog = false
                                playerBottomSheetState.collapseAfterNavigate()
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                }
            }
        }
    }

    val isInLibrary = librarySong?.song?.inLibrary != null
    // Owner 2026-10-20: no «Canción» way out for a video whose song audio is refused (SongModePlanning).
    val songBlockedIds by SongModeAvailability.blocked.collectAsState()
    val hasVideo = (mediaMetadata.hasCompatibleVideo &&
        SongModePlanning.offerSongOption(videoMode, mediaMetadata.isVideoSong, mediaMetadata.id in songBlockedIds)) ||
        isExportedVideo ||
        !mediaMetadata.podcastVideoUrl.isNullOrEmpty()
    // Sheet already paints FrostFill ([BottomSheetMenu] + LocalAuraFloatingChrome).
    val floatingChrome = LocalAuraFloatingChrome.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .auraBleedHorizontal(20.dp)
            .background(if (floatingChrome) Color.Transparent else AuraPalette.GroundRaised),
        contentPadding = PaddingValues(
            bottom = 16.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        // ── Cabecera (siempre) ────────────────────────────────────────────────────────────────────
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraSpacing.Gutter, vertical = 12.dp),
            ) {
                AuraCover(
                    thumbnailUrl = mediaMetadata.thumbnailUrl,
                    size = 48.dp,
                    seed = mediaMetadata.id,
                    fillBleed = true,
                    ratio = if (mediaMetadata.isVideoSong) 16f / 9f else 1f,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = mediaMetadata.title,
                        style = AuraType.RowTitle,
                        color = AuraPalette.OnGround,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                    Text(
                        text = mediaMetadata.artists.joinToString(", ") { it.name },
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundMuted,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                    )
                }
            }
        }

        if (isCasting && castDeviceName != null) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AuraSpacing.Gutter, end = AuraSpacing.Gutter, bottom = 8.dp),
                ) {
                    AuraIconGlyph(AuraIcons.Cast, null, size = 18.dp, tint = AuraPalette.Teal)
                    AuraTechnicalText(
                        text = stringResource(R.string.casting_to, castDeviceName ?: ""),
                        color = AuraPalette.Teal,
                    )
                }
            }
        }

        if (showPlayerVolumeControl(hidePlayerVolume)) {
            item {
                VolumeSlider(
                    value = if (isCasting) castVolume else playerVolume,
                    onValueChange = { volume ->
                        if (isCasting) castHandler?.setVolume(volume)
                        else playerConnection.service.playerVolume.value = volume
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AuraSpacing.Gutter),
                    accentColor = AuraPalette.Teal,
                )
            }
        }

        // Streaming quality cycling (Opus / Saavn / Qobuz) removed — Opus is the only stream path.
        item { Spacer(Modifier.height(6.dp)) }

        // ── Fijas (siempre visibles): UNA losa de cristal, como el menú ⋯ de una canción ────────────
        item {
            AuraGlassSlab(modifier = Modifier.padding(horizontal = AuraSpacing.Gutter)) {
                SlabRowsColumn {
                    if (artists.isNotEmpty()) {
                        row(
                            icon = AuraIcons.Artist,
                            label = stringResource(R.string.view_artist),
                            onClick = {
                                if (artists.size == 1) {
                                    val only = artists[0]
                                    coroutineScope.launch {
                                        val browseId = if (needsOnlineBrowseResolution(only.id)) {
                                            withContext(Dispatchers.IO) {
                                                resolveOnlineArtistBrowseId(only.name)
                                            }
                                        } else {
                                            only.id
                                        }
                                        if (!browseId.isNullOrBlank()) {
                                            navController.navigateOnce("artist/$browseId")
                                        } else {
                                            navController.navigate(
                                                "search/${URLEncoder.encode(only.name, "UTF-8")}"
                                            )
                                        }
                                        playerBottomSheetState.collapseAfterNavigate()
                                        onDismiss()
                                    }
                                } else {
                                    showSelectArtistDialog = true
                                }
                            },
                        )
                    }
                    resolvedAlbum?.let { album ->
                        row(
                            icon = AuraIcons.Album,
                            label = stringResource(R.string.view_album),
                            onClick = {
                                coroutineScope.launch {
                                    val browseId = if (needsOnlineBrowseResolution(album.id)) {
                                        val query = listOfNotNull(
                                            album.title.takeIf { it.isNotBlank() },
                                            artists.joinToString(" ") { it.name }.takeIf { it.isNotBlank() },
                                        ).joinToString(" ")
                                        withContext(Dispatchers.IO) {
                                            resolveOnlineAlbumBrowseId(query)
                                        }
                                    } else {
                                        album.id
                                    }
                                    if (!browseId.isNullOrBlank()) {
                                        navController.navigateOnce("album/$browseId")
                                    } else if (album.title.isNotBlank()) {
                                        navController.navigate(
                                            "search/${URLEncoder.encode(album.title, "UTF-8")}"
                                        )
                                    }
                                    playerBottomSheetState.collapseAfterNavigate()
                                    onDismiss()
                                }
                            },
                        )
                    }
                    row(
                        icon = AuraIcons.Equalizer,
                        label = stringResource(R.string.equalizer),
                        onClick = {
                            playerBottomSheetState.collapseAfterNavigate()
                            navController.navigate("settings/equalizer") { launchSingleTop = true }
                            onDismiss()
                        },
                    )
                    row(
                        icon = AuraIcons.Volume,
                        label = "Sonido",
                        onClick = {
                            playerBottomSheetState.collapseAfterNavigate()
                            navController.navigate("settings/sound")
                            onDismiss()
                        },
                    )
                    row(
                        icon = AuraIcons.Search,
                        label = stringResource(R.string.details),
                        onClick = {
                            onShowDetailsDialog()
                            onDismiss()
                        },
                    )
                    row(
                        icon = AuraIcons.Settings,
                        label = stringResource(R.string.settings),
                        onClick = {
                            playerBottomSheetState.collapseAfterNavigate()
                            navController.navigateAsTab("settings")
                            onDismiss()
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // ── REPRODUCCIÓN (contraída) ───────────────────────────────────────────────────────────────
        item {
            AuraMenuExpandableSection(
                title = "Reproducción",
                expanded = openSection == SECTION_PLAYBACK,
                onToggle = { toggleSection(SECTION_PLAYBACK) },
            ) {
                if (!isListenTogetherGuest) {
                    row(
                        icon = AuraIcons.Shuffle,
                        label = stringResource(R.string.shuffle),
                        iconTint = if (shuffleModeEnabled) AuraPalette.Teal
                        else AuraPalette.OnGround.copy(alpha = 0.75f),
                        onClick = {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                            onDismiss()
                        },
                        trailing = {
                            AuraSwitch(
                                checked = shuffleModeEnabled,
                                onCheckedChange = {
                                    playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                                    onDismiss()
                                },
                                contentDescription = stringResource(R.string.shuffle),
                            )
                        },
                    )
                    row(
                        icon = AuraIcons.Repeat,
                        label = stringResource(R.string.repeat),
                        iconTint = if (repeatMode != Player.REPEAT_MODE_OFF) AuraPalette.Teal
                        else AuraPalette.OnGround.copy(alpha = 0.75f),
                        onClick = { playerConnection.player.toggleRepeatMode() },
                        trailing = {
                            AuraTechnicalText(
                                text = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> "UNA"
                                    Player.REPEAT_MODE_ALL -> "TODO"
                                    else -> "NO"
                                },
                                color = if (repeatMode != Player.REPEAT_MODE_OFF) AuraPalette.Teal
                                else AuraPalette.OnGroundDisabled,
                            )
                        },
                    )
                    val startingRadioText = stringResource(R.string.starting_radio)
                    row(
                        icon = AuraIcons.Radio,
                        label = stringResource(R.string.start_radio),
                        onClick = {
                            Toast.makeText(context, startingRadioText, Toast.LENGTH_SHORT).show()
                            playerConnection.startRadioSeamlessly()
                            onDismiss()
                        },
                    )
                }
                row(
                    icon = AuraIcons.Speed,
                    label = stringResource(R.string.advanced),
                    onClick = { showPitchTempoDialog = true },
                )
                row(
                    icon = AuraIcons.Timer,
                    label = stringResource(R.string.sleep_timer),
                    onClick = { showSleepTimerDialog = true },
                )
                row(
                    icon = AuraIcons.Radio,
                    label = stringResource(R.string.refetch),
                    onClick = { playerConnection.refetchCurrentInOpus() },
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // ── BIBLIOTECA ────────────────────────────────────────────────────────────────────────────
        item {
            AuraMenuExpandableSection(
                title = "Biblioteca",
                expanded = openSection == SECTION_LIBRARY,
                onToggle = { toggleSection(SECTION_LIBRARY) },
            ) {
                row(
                    icon = if (isInLibrary) AuraIcons.Check else AuraIcons.Plus,
                    label = stringResource(
                        if (isInLibrary) R.string.remove_from_library else R.string.add_to_library
                    ),
                    onClick = {
                        playerConnection.toggleLibrary()
                        onDismiss()
                    },
                )
                row(
                    icon = AuraIcons.PlaylistAdd,
                    label = stringResource(R.string.add_to_playlist),
                    onClick = { showChoosePlaylistDialog = true },
                )
                // Row 348 (C2 audit, UI_INVENTORY §3/§5): export a song that is ALREADY downloaded (or not) as
                // MP3/video. The dialog was wired here but no row opened it, and the player's download
                // button only offers export for songs that are not downloaded yet.
                if (enableExportAsMp3 && !isLocalTrack) {
                    if (isExporting) {
                        row(
                            icon = AuraIcons.Export,
                            label = stringResource(R.string.exporting),
                            onClick = {},
                        )
                    } else {
                        row(
                            icon = AuraIcons.Export,
                            label = stringResource(R.string.action_export),
                            onClick = { showExportFormatDialog = true },
                        )
                    }
                }
                row(
                    icon = AuraIcons.Timer,
                    label = stringResource(R.string.set_as_ringtone),
                    onClick = {
                        ringtoneViewModel.showTrimmer(
                            mediaMetadata.id,
                            mediaMetadata.title,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                        )
                        onDismiss()
                    },
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // ── MÁS OPCIONES (resto contraído) ─────────────────────────────────────────────────────────
        item {
            AuraMenuExpandableSection(
                title = "Más opciones",
                expanded = openSection == SECTION_MORE,
                onToggle = { toggleSection(SECTION_MORE) },
            ) {
                if (mediaMetadata.id.startsWith("http")) {
                    row(
                        icon = AuraIcons.Queue,
                        label = stringResource(R.string.go_to_podcast),
                        onClick = {
                            val epId = mediaMetadata.id
                            coroutineScope.launch {
                                val feed = iad1tya.echo.music.podcast.PodcastStoreEntryPoint
                                    .get(context).get(epId)?.feedUrl
                                playerBottomSheetState.collapseAfterNavigate()
                                onDismiss()
                                navController.navigate(
                                    if (!feed.isNullOrBlank())
                                        "podcasts?feedUrl=" + URLEncoder.encode(feed, "UTF-8")
                                    else "podcasts"
                                )
                            }
                        },
                    )
                }
                if (hasVideo) {
                    row(
                        icon = AuraIcons.Video,
                        label = "Vídeo",
                        iconTint = if (videoMode) AuraPalette.Teal
                        else AuraPalette.OnGround.copy(alpha = 0.75f),
                        onClick = {
                            playerConnection.toggleVideoMode()
                            onDismiss()
                        },
                    )
                }
                // AUDIT FIX (2026-09-04): this was a DUPLICATE "Sonido" row (the fixed section
                // above already navigates to settings/sound) with its icon swapped with the
                // Equalizer's — removed; the fixed row keeps Volume + "Sonido".
                row(
                    icon = AuraIcons.Volume,
                    label = stringResource(R.string.audio_devices),
                    onClick = { showAudioDeviceSheet = true },
                )
                row(
                    icon = AuraIcons.People,
                    label = stringResource(R.string.listen_together),
                    onClick = { showListenTogetherDialog = true },
                    trailing = if (pendingSuggestions.isEmpty()) null else {
                        {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(AuraShapes.Pill)
                                    .background(AuraPalette.Teal)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                AuraTechnicalText(
                                    text = pendingSuggestions.size.toString(),
                                    color = AuraPalette.OnAccent,
                                )
                            }
                        }
                    },
                )
                if (isListenTogetherGuest) {
                    row(
                        icon = AuraIcons.Radio,
                        label = stringResource(R.string.resync),
                        onClick = {
                            listenTogetherManager?.requestSync()
                            onDismiss()
                        },
                    )
                }
                row(
                    icon = AuraIcons.Album,
                    label = stringResource(R.string.ambient_mode),
                    onClick = {
                        playerBottomSheetState.collapseSoft()
                        navController.navigate("ambient_mode") { launchSingleTop = true }
                        onDismiss()
                    },
                )
                row(
                    icon = AuraIcons.Share,
                    label = stringResource(R.string.share),
                    onClick = {
                        coroutineScope.launch {
                            // Cuerpo compartido con el botón de compartir del reproductor a pantalla
                            // completa (local → exportado → enlace). Ver [shareSong].
                            shareSong(
                                context = context,
                                songId = mediaMetadata.id,
                                title = mediaMetadata.title,
                                artists = mediaMetadata.artists.map { it.name },
                                isLocalTrack = isLocalTrack,
                                isExported = isExported,
                                isExportedVideo = isExportedVideo,
                                album = mediaMetadata.album?.title.orEmpty(),
                                thumbnailUrl = mediaMetadata.thumbnailUrl.orEmpty(),
                                hasVideo = mediaMetadata.isVideoSong,
                            )
                            onDismiss()
                        }
                    },
                )
            }
        }
    }

}

/**
 * The rows a slab collects: [row] records one, [SlabRowsColumn] lays them out with the thin divider between two of them
 * (the menu's rows are conditional, so the divider cannot be written next to each one).
 */
private class SlabRows {
    val rows = mutableListOf<@Composable () -> Unit>()

    fun row(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
        iconTint: Color = AuraPalette.OnGround.copy(alpha = 0.75f),
        trailing: (@Composable () -> Unit)? = null,
    ) {
        rows.add { AuraGlassVectorRow(title = label, onClick = onClick, icon = icon, iconTint = iconTint, trailing = trailing) }
    }
}

@Composable
private fun SlabRowsColumn(build: @Composable SlabRows.() -> Unit) {
    val collected = SlabRows()
    collected.build()
    collected.rows.forEachIndexed { index, content ->
        if (index > 0) AuraGlassDivider(inset = 56.dp)
        content()
    }
}

/**
 * Accordion group (Settings-style chevron) for the player Más menu, as ONE glass slab of the ⋯ menu: the header row is
 * the slab's first row and the section's rows unfold under it. Only one section should be open at a time — the host owns
 * that via [expanded] / [onToggle].
 */
@Composable
private fun AuraMenuExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    sectionBody: @Composable SlabRows.() -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = AuraMotion.float,
        label = "aura-player-menu-chevron",
    )
    AuraGlassSlab(modifier = Modifier.padding(horizontal = AuraSpacing.Gutter)) {
        AuraGlassRow(
            title = title,
            onClick = onToggle,
            trailing = {
                Icon(
                    imageVector = AuraIcons.ChevronRight,
                    contentDescription = null,
                    tint = AuraPalette.OnGroundMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation),
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = AuraMotion.intSize) +
                fadeIn(animationSpec = AuraMotion.float),
            exit = shrinkVertically(animationSpec = AuraMotion.intSize) +
                fadeOut(animationSpec = AuraMotion.float),
        ) {
            Column(Modifier.fillMaxWidth()) {
                AuraGlassDivider(inset = 16.dp)
                SlabRowsColumn(sectionBody)
            }
        }
    }
}

/**
 * Grows the content horizontally by [amount] on each side and shifts it back, so a child can paint
 * edge-to-edge inside a parent that already applied a horizontal padding. No-op when the incoming
 * constraints are unbounded.
 */
internal fun Modifier.auraBleedHorizontal(amount: Dp): Modifier = this.layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extra = amount.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = width, maxWidth = width),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
