package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.LocalPlayerConnection
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ExportedSongIdsKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.constants.OfflineModeKey
import iad1tya.echo.music.constants.SongSortType
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.ui.component.EmptyPlaceholder
import iad1tya.echo.music.ui.component.LocalMenuState
import iad1tya.echo.music.ui.menu.SongMenu
import iad1tya.echo.music.ui.newui.AuraAppleCoverDividerInset
import iad1tya.echo.music.ui.newui.AuraAppleListRowFrame
import iad1tya.echo.music.ui.newui.AuraSongRow
import iad1tya.echo.music.ui.newui.auraAppleDurationLabel
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.CachePlaylistViewModel
import kotlinx.coroutines.flow.flowOf

/**
 * Offline body: shown instead of network feeds when [OfflineModeKey] is ON. Disable toggle lives ON
 * THIS SCREEN (owner request) — not only in Settings.
 *
 * OWNER DIRECTIVE 2026-09-14 ("el modo sin conexión puede reproducir lo que está en caché, lo
 * descargado y lo exportado a video y a MP3"): it lists EVERYTHING offline playback can serve —
 * downloads, songs whose listen-cache copy is COMPLETE, exported MP3s and exported videos. The
 * player already plays all four with no network (MusicService's download / exported-URI / full
 * listen-cache branches); before, this screen only listed downloads, so the rest was unreachable.
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
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    var offlineMode by rememberPreference(OfflineModeKey, false)

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

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

    val downloadsTitle = stringResource(R.string.downloaded_songs)
    val cachedTitle = stringResource(R.string.cached_playlist)
    val exportedTitle = stringResource(R.string.action_exported)
    val exportedVideosTitle = stringResource(R.string.exported_videos_playlist)

    val nothingOffline = downloadedSongs.isEmpty() && cachedSongs.isEmpty() &&
        exportedSongs.isEmpty() && exportedVideos.isEmpty()

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "offline_banner") {
            OfflineBanner(
                automatic = automatic,
                onDisable = { offlineMode = false },
            )
        }

        if (nothingOffline) {
            item(key = "offline_empty") {
                EmptyPlaceholder(
                    icon = R.drawable.download,
                    text = stringResource(R.string.offline_empty_all),
                )
            }
        }

        // One section per offline source; a tap plays that section as the queue.
        fun section(sectionKey: String, title: String, songs: List<Song>) {
            if (songs.isEmpty()) return
            item(key = "header_$sectionKey") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            itemsIndexed(
                items = songs,
                key = { _, song -> "${sectionKey}_${song.id}" },
            ) { index, song ->
                // Apple-style renglones (owner request): the offline body is a PURE song list, with the
                // same row design as Local and the library lists, inside BOTH shells.
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
                                        title = title,
                                        items = songs.map { it.toMediaItem() },
                                        startIndex = index,
                                    )
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

        section("downloaded", downloadsTitle, downloadedSongs)
        section("cached", cachedTitle, cachedSongs)
        section("exported", exportedTitle, exportedSongs)
        section("exported_videos", exportedVideosTitle, exportedVideos)
    }
}

@Composable
private fun OfflineBanner(
    automatic: Boolean,
    onDisable: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (automatic) R.string.offline_auto_title else R.string.offline_mode,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = stringResource(
                        if (automatic) R.string.offline_auto_hint else R.string.offline_banner_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (!automatic) {
                TextButton(onClick = onDisable) {
                    Text(stringResource(R.string.offline_disable))
                }
            }
        }
    }
}
