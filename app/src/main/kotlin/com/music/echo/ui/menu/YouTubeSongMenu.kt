

package iad1tya.echo.music.ui.menu

import iad1tya.echo.music.ui.component.MenuLikeButton
import iad1tya.echo.music.ui.component.MenuHeaderCover
import iad1tya.echo.music.ui.component.MenuHeader
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalDownloadUtil
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.LocalSyncUtils
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableExportAsMp3Key
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.constants.ExportedSongIdsKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.ExportingSongIdsKey
import iad1tya.echo.music.constants.ListItemHeight
import iad1tya.echo.music.constants.ListThumbnailSize
import iad1tya.echo.music.playback.AudioExportService
import iad1tya.echo.music.playback.enqueueSongDownloads
import iad1tya.echo.music.playback.removeSongDownloads
import iad1tya.echo.music.ui.newui.toAuraDownloadItem
import iad1tya.echo.music.constants.ThumbnailCornerRadius
import iad1tya.echo.music.db.entities.SpeedDialItem
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.rememberResolvedAlbum
import iad1tya.echo.music.models.toMediaMetadata
import iad1tya.echo.music.playback.queues.YouTubeQueue
import iad1tya.echo.music.ui.component.ListDialog
import iad1tya.echo.music.applicationScope
import iad1tya.echo.music.ui.component.LocalBottomSheetPageState
import iad1tya.echo.music.ui.component.Material3MenuGroup
import iad1tya.echo.music.ui.component.Material3MenuItemData
import iad1tya.echo.music.ui.component.NewAction
import iad1tya.echo.music.ui.component.NewActionGrid
import iad1tya.echo.music.ui.utils.ShowMediaInfo
import iad1tya.echo.music.ui.utils.resize
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import iad1tya.echo.music.utils.joinByBullet
import iad1tya.echo.music.utils.lookupExportedFileUri
import iad1tya.echo.music.utils.makeTimeString
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.shareContentUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@SuppressLint("MutableCollectionMutableState")
@Composable
fun YouTubeSongMenu(
    song: SongItem,
    navController: NavController,
    onDismiss: () -> Unit,
    onHistoryRemoved: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val librarySong by database.songWithEquivalent(song.id, song.title, song.artists.firstOrNull()?.name).collectAsState(initial = null)
    val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val ringtoneViewModel = iad1tya.echo.music.LocalRingtoneViewModel.current
    val isPinned by database.speedDialDao.isPinned(song.id).collectAsState(initial = false)
    val artists = remember(song.artists) {
        song.artists.map { artist ->
            MediaMetadata.Artist(id = artist.id, name = artist.name)
        }
    }

    // Recover the album on-demand so "view album" appears even when the search/next renderer omitted it
    // from the byline (one-shot lookup, only when missing; true videos with no album stay hidden).
    val resolvedAlbum = rememberResolvedAlbum(
        songId = song.id,
        initial = song.album?.let { MediaMetadata.Album(id = it.id, title = it.name) },
        dbAlbumId = librarySong?.song?.albumId,
        dbAlbumName = librarySong?.song?.albumName,
    )

    val (enableExportAsMp3) = rememberPreference(key = EnableExportAsMp3Key, defaultValue = true)
    val (exportDirectoryUri, onExportDirectoryUriChange) = rememberPreference(key = ExportDirectoryUriKey, defaultValue = "")
    val (exportingSongIds) = rememberPreference(key = ExportingSongIdsKey, defaultValue = "")
    val (exportedSongIds) = rememberPreference(key = ExportedSongIdsKey, defaultValue = "")
    val (exportedVideoIds) = rememberPreference(key = ExportedVideoIdsKey, defaultValue = "")
    val ensureMp3Folder = iad1tya.echo.music.ui.utils.rememberMp3ExportFolderAccess(
        exportDirectoryUri = exportDirectoryUri,
        onExportDirectoryUriChange = onExportDirectoryUriChange,
    )

    val isExporting = remember(exportingSongIds, song.id) { exportingSongIds.split(",").contains(song.id) }
    val isExported = remember(exportedSongIds, exportedVideoIds, song.id) {
        exportedSongIds.split(",").contains(song.id) || exportedVideoIds.split(",").contains(song.id)
    }
    val isExportedVideo = remember(exportedVideoIds, song.id) {
        exportedVideoIds.split(",").contains(song.id)
    }
    var showExportFormatDialog by rememberSaveable { mutableStateOf(false) }

    var showChoosePlaylistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    // Remote sync is NOT done here: AddToPlaylistDialog itself pushes the returned ids to the synced
    // playlist's browseId — adding here too sent the song TWICE to the remote playlist.
    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        songIdsForMembership = listOf(song.id),
        onGetSong = { _ ->
            database.withTransaction {
                insert(song.toMediaMetadata())
            }
            listOf(song.id)
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    var showSelectArtistDialog by rememberSaveable {  
        mutableStateOf(false)  
    }  

    if (showExportFormatDialog) {
        // The unified download sheet (owner 2026-10-19): the «Exportar» row now offers every output of a song in one
        // place — sin conexión · MP3 · vídeo. The metadata goes along so an offline copy gets its library row.
        iad1tya.echo.music.ui.newui.AuraDownloadSheet(
            songs = listOf(song.toAuraDownloadItem().copy(metadata = song.toMediaMetadata())),
            title = song.title,
            offlineState = download?.state ?: Download.STATE_STOPPED,
            onDismiss = { showExportFormatDialog = false },
            onDone = onDismiss,
        )
    }

    if (showSelectArtistDialog) {  
        ListDialog(  
            onDismiss = { showSelectArtistDialog = false },  
        ) {  
            items(artists) { artist ->  
                Row(  
                    verticalAlignment = Alignment.CenterVertically,  
                    modifier =  
                    Modifier  
                        .height(ListItemHeight)  
                        .clickable {  
                            if (artist.id != null) {
                                navController.navigate("artist/${artist.id}")  
                            } else {
                                navController.navigate("search/${java.net.URLEncoder.encode(artist.name, "UTF-8")}")
                            }
                            showSelectArtistDialog = false  
                            onDismiss()  
                        }  
                        .padding(horizontal = 12.dp),  
                ) {  
                    Box(  
                        contentAlignment = Alignment.CenterStart,  
                        modifier =  
                        Modifier  
                            .fillParentMaxWidth()  
                            .height(ListItemHeight)  
                            .clickable {  
                                if (artist.id != null) {
                                    navController.navigate("artist/${artist.id}")  
                                } else {
                                    navController.navigate("search/${java.net.URLEncoder.encode(artist.name, "UTF-8")}")
                                }
                                showSelectArtistDialog = false  
                                onDismiss()  
                            }  
                            .padding(horizontal = 24.dp),  
                    ) {  
                        Text(  
                            text = artist.name,  
                            fontSize = 18.sp,  
                            fontWeight = FontWeight.Bold,  
                            maxLines = 1,  
                            overflow = TextOverflow.Ellipsis,  
                        )  
                    }  
                }  
            }  
        }  
    }  

    MenuHeader(
        title = song.title,
        subtitle = song.artists.joinToString { it.name },
        meta = song.duration?.let { makeTimeString(it * 1000L) },
        thumbnail = { MenuHeaderCover(thumbnailUrl = song.thumbnail, isVideo = song.isVideoSong) },
        trailing = {
            MenuLikeButton(
                liked = librarySong?.song?.liked == true,
                onClick = {
                    database.transaction {
                        librarySong.let { librarySong ->
                            val s: SongEntity
                            if (librarySong == null) {
                                insert(song.toMediaMetadata(), SongEntity::toggleLike)
                                s = song.toMediaMetadata().toSongEntity().let(SongEntity::toggleLike)
                            } else {
                                s = librarySong.song.toggleLike()
                                upsert(s) // insert-or-update so the like persists for not-yet-saved songs
                            }
                            syncUtils.likeSong(s)
                        }
                    }
                },
            )
        },
    )

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = listOfNotNull(
                    if (!isGuest) {
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.radio),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            text = stringResource(R.string.start_radio),
                            onClick = {
                                ActionLog.tap(ActionId.YTSONGMENU_START_RADIO)
                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                onDismiss()
                            }
                        )
                    } else null,
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.playlist_add),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        text = stringResource(R.string.add_to_playlist),
                        onClick = {
                            ActionLog.tap(ActionId.YTSONGMENU_ADD_TO_PLAYLIST)
                            showChoosePlaylistDialog = true
                        }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        text = stringResource(R.string.share),
                        onClick = {
                            ActionLog.tap(ActionId.YTSONGMENU_SHARE)
                            // Row 520: link / MP3 / video, asked every time (the choice window saves what is missing).
                            coroutineScope.launch {
                                iad1tya.echo.music.ui.newui.ShareChoiceState.open(
                                    iad1tya.echo.music.ui.newui.ShareSongRequest(
                                        songId = song.id,
                                        title = song.title,
                                        artists = song.artists.map { it.name },
                                        album = song.album?.name.orEmpty(),
                                        thumbnailUrl = song.thumbnail,
                                        hasVideo = song.isVideoSong,
                                    ),
                                )
                                onDismiss()
                            }
                        }
                    )
                ),
                columns = 3,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        item {
            Material3MenuGroup(
                items = listOfNotNull(
                    if (listenTogetherManager != null && listenTogetherManager.isInRoom && !listenTogetherManager.isHost) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.suggest_to_host)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                ActionLog.tap(ActionId.YTSONGMENU_SUGGEST_TO_HOST)
                                val durationMs = if (song.duration != null && song.duration!! > 0) song.duration!! * 1000L else 180000L
                                val trackInfo = iad1tya.echo.music.listentogether.TrackInfo(
                                    id = song.id,
                                    title = song.title,
                                    artist = artists.joinToString(", ") { it.name },
                                    // metroproto TrackInfo is non-null String — orEmpty() at the seam.
                                    album = song.album?.name.orEmpty(),
                                    duration = durationMs,
                                    thumbnail = song.thumbnail.orEmpty()
                                )
                                listenTogetherManager.suggestTrack(trackInfo)
                                onDismiss()
                            }
                        )
                    } else null,
                    if (!isGuest) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.play_next)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.playlist_play),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                ActionLog.tap(ActionId.YTSONGMENU_PLAY_NEXT)
                                playerConnection.playNext(song.copy(thumbnail = song.thumbnail.resize(1200, 1200)).toMediaItem())
                                confirmQueueEdit(context, playerConnection, R.string.play_next)
                                onDismiss()
                            }
                        )
                    } else null,
                    if (!isGuest) {
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_queue)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                ActionLog.tap(ActionId.YTSONGMENU_ADD_TO_QUEUE)
                                playerConnection.addToQueue(song.toMediaItem())
                                confirmQueueEdit(context, playerConnection, R.string.add_to_queue)
                                onDismiss()
                            }
                        )
                    } else null,
                    // Phase B #7 — "Menos de esto": graded soft feedback. Mildly demotes similar songs in the
                    // radio (a bounded penalty in MusicService.orderedByTaste), never a hard block. Separate from
                    // the hard "No me gusta". Always available (works offline, local preference).
                    Material3MenuItemData(
                        title = { Text(text = "Menos de esto") },
                        description = { Text(text = "Se recomendará menos parecido a esto") },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.thumb_down),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            ActionLog.tap(ActionId.YTSONGMENU_LESS_LIKE_THIS)
                            val primary = song.artists.firstOrNull()
                            // Row 721: «menos parecido» reaches the artist too (Home + radio read it). Never the smart
                            // queue's 30-day skip memory: that one rejects the artist outright (row 525). One edit on the
                            // process scope: the menu's own scope dies with the sheet a moment after this click.
                            val artistKey = iad1tya.echo.music.reco.SoftDislikeRank.artistKey(primary?.id, primary?.name)
                            // Row 803: answered by the shell's «Se recomendará menos a X · Deshacer» (this sheet closes
                            // now), which takes back only what this tap added.
                            val artistName = primary?.name
                            context.applicationScope.launch {
                                val store = iad1tya.echo.music.dislike.DislikeStoreEntryPoint.get(context)
                                val before = store.snapshot()
                                store
                                    .softDislikeSongAndArtist(song.id, artistKey)
                                iad1tya.echo.music.dislike.SoftDislikeUndo.offer(before, song.id, artistKey, artistName)
                            }
                            onDismiss()
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    if (song.historyRemoveToken != null) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_history)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.delete),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    ActionLog.tap(ActionId.YTSONGMENU_REMOVE_FROM_HISTORY)
                                    coroutineScope.launch {
                                        YouTube.feedback(listOf(song.historyRemoveToken!!))
                                            .onSuccess { processed ->
                                                if (!processed) timber.log.Timber.w("HISTORY_FEEDBACK not processed")
                                            }
                                            .onFailure { e ->
                                                if (e !is kotlinx.coroutines.CancellationException) {
                                                    timber.log.Timber.w("HISTORY_FEEDBACK failed: %s", iad1tya.echo.music.utils.privacySafeSummary(e))
                                                }
                                            }
                                        delay(500)
                                        onHistoryRemoved()
                                        onDismiss()
                                    }
                                }
                            )
                        )
                    }
                    add(
                        Material3MenuItemData(
                            title = {
                                Text(text = if (librarySong?.song?.inLibrary != null) stringResource(R.string.remove_from_library) else stringResource(R.string.add_to_library))
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(if (librarySong?.song?.inLibrary != null) R.drawable.library_add_check else R.drawable.library_add),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                ActionLog.tap(ActionId.YTSONGMENU_LIBRARY_TOGGLE)
                                val isInLibrary = librarySong?.song?.inLibrary != null

                                
                                coroutineScope.launch {
                                    YouTube.toggleSongLibrary(song.id, !isInLibrary)
                                }

                                if (isInLibrary) {
                                    database.query {
                                        inLibrary(song.id, null)
                                    }
                                } else {
                                    database.transaction {
                                        insert(song.toMediaMetadata())
                                        inLibrary(song.id, LocalDateTime.now())
                                        addLibraryTokens(
                                            song.id,
                                            song.libraryAddToken,
                                            song.libraryRemoveToken
                                        )
                                    }
                                }
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOf(
                    when (download?.state) {
                        Download.STATE_COMPLETED -> {
                            Material3MenuItemData(
                                title = {
                                    Text(
                                        text = stringResource(R.string.remove_download)
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    ActionLog.tap(ActionId.YTSONGMENU_REMOVE_DOWNLOAD)
                                    removeSongDownloads(context, song.id, song.isVideoSong)
                                }
                            )
                        }
                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.downloading)) },
                                icon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                },
                                onClick = {
                                    ActionLog.tap(ActionId.YTSONGMENU_CANCEL_DOWNLOAD)
                                    removeSongDownloads(context, song.id, song.isVideoSong)
                                }
                            )
                        }
                        else -> {
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.action_download)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    ActionLog.tap(ActionId.YTSONGMENU_DOWNLOAD)
                                    database.transaction {
                                        insert(song.toMediaMetadata())
                                    }
                                    enqueueSongDownloads(
                                        context,
                                        song.id,
                                        song.title,
                                        isVideoSong = song.isVideoSong,
                                    )
                                }
                            )
                        }
                    }
                )
            )
        }

        if (enableExportAsMp3) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                Material3MenuGroup(
                    items = listOf(
                        when {
                            isExporting -> Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.exporting)) },
                                icon = {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                },
                                onClick = {}
                            )
                            else -> Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.action_export)) },
                                description = { Text(text = stringResource(R.string.export_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.file_export),
                                        contentDescription = null,
                                    )
                                },
                                onClick = { ActionLog.tap(ActionId.YTSONGMENU_EXPORT); showExportFormatDialog = true }
                            )
                        }
                    )
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = { Text(text = stringResource(R.string.set_as_ringtone)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.notification),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = {
                            ActionLog.tap(ActionId.YTSONGMENU_SET_AS_RINGTONE)
                            // Always open the trimmer: WRITE_SETTINGS only gates the optional
                            // direct-apply step inside RingtoneHelper.downloadAndTrimAsRingtone;
                            // the success dialog offers the grant when it wasn't held.
                            ringtoneViewModel.showTrimmer(song.id, song.title, song.artists.joinToString { it.name }, song.duration ?: 0)
                            onDismiss()
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items = buildList {
                    if (artists.isNotEmpty()) {
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.view_artist)) },
                                showChevron = true,
                                description = { Text(text = song.artists.joinToString { it.name }) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.artist),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    ActionLog.tap(ActionId.YTSONGMENU_VIEW_ARTIST)
                                    if (artists.size == 1) {
                                        if (artists[0].id != null) {
                                            navController.navigate("artist/${artists[0].id}")
                                        } else {
                                            navController.navigate("search/${java.net.URLEncoder.encode(artists[0].name, "UTF-8")}")
                                        }
                                        onDismiss()
                                    } else {
                                        showSelectArtistDialog = true
                                    }
                                }
                            )
                        )
                    }
                    resolvedAlbum?.let { album ->
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.view_album)) },
                                showChevron = true,
                                description = { Text(text = album.title) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.album),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    ActionLog.tap(ActionId.YTSONGMENU_VIEW_ALBUM)
                                    navController.navigate("album/${album.id}")
                                    onDismiss()
                                }
                            )
                        )
                    }
                    add(
                        Material3MenuItemData(
                            title = { Text(text = stringResource(R.string.details)) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.info),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                ActionLog.tap(ActionId.YTSONGMENU_DETAILS)
                                onDismiss()
                                bottomSheetPageState.show {
                                    ShowMediaInfo(song.id)
                                }
                            }
                        )
                    )
                }
            )
        }
    }
}
