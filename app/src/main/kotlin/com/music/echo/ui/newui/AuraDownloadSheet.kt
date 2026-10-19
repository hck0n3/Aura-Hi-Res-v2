package iad1tya.echo.music.ui.newui

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.music.innertube.models.SongItem
import iad1tya.echo.music.LocalDatabase
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableExportAsMp3Key
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playback.AudioExportService
import iad1tya.echo.music.playback.AudioExportTask
import iad1tya.echo.music.playback.enqueueSongDownloads
import iad1tya.echo.music.playback.removeSongDownloads
import iad1tya.echo.music.ui.component.AuraModalBottomSheet
import iad1tya.echo.music.ui.utils.ExportFormat
import iad1tya.echo.music.ui.utils.rememberExportVideoAvailability
import iad1tya.echo.music.ui.utils.rememberMp3ExportFolderAccess
import iad1tya.echo.music.utils.rememberPreference

/**
 * One song the unified download sheet can act on, whatever it came from (a library row, a YouTube list item or
 * the song playing now).
 *
 * [metadata] is only for a song the library may not have yet (the one playing, an online result): it is inserted
 * before an offline download so the downloaded copy shows up in the library, exactly as the player always did.
 */
@Immutable
data class AuraDownloadItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUrl: String,
    /** A real music video exists for it (not an art-track): the only songs «Exportar a vídeo» applies to. */
    val isVideo: Boolean,
    val metadata: MediaMetadata? = null,
    /** Whether an OFFLINE download of this single song also fetches its companion video (what the player always did). */
    val downloadsCompanionVideo: Boolean = isVideo,
)

fun Song.toAuraDownloadItem(): AuraDownloadItem = AuraDownloadItem(
    id = id,
    title = song.title,
    artist = artists.joinToString(", ") { it.name },
    album = song.albumName.orEmpty(),
    thumbnailUrl = song.thumbnailUrl.orEmpty(),
    isVideo = song.isVideo,
)

fun SongItem.toAuraDownloadItem(): AuraDownloadItem = AuraDownloadItem(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.name.orEmpty(),
    thumbnailUrl = thumbnail,
    isVideo = isVideoSong,
)

fun MediaMetadata.toAuraDownloadItem(): AuraDownloadItem = AuraDownloadItem(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.title.orEmpty(),
    thumbnailUrl = thumbnailUrl.orEmpty(),
    isVideo = isVideoSong || !podcastVideoUrl.isNullOrEmpty(),
    metadata = this,
    // The player's own download only ever paired the companion video with a music-video SONG, never a podcast video.
    downloadsCompanionVideo = isVideoSong,
)

private enum class DownloadSheetStep { Format, Scope, Pick, ConfirmRemove }

/**
 * THE download button's sheet (owner 2026-10-19: *«que el botón de descargar esté unificado: que cuando toque
 * descargar aparezcan todos los tipos de descarga en un solo botón — offline, exportar a MP3, exportar a vídeo — y si
 * estoy en un álbum o playlist que pregunte si deseo seleccionar las que yo quiero o descargarlo todo; que no descargue
 * de golpe»*).
 *
 *  1. FORMAT — «Descargar sin conexión» · «Exportar a MP3» · «Exportar a vídeo». The last two only with «Exportar como
 *     MP3» on (the existing setting), and the video one only for what has a real music video.
 *  2. SCOPE — only for a list of more than one song: «Todas (N)» or «Elegir canciones…» (checkbox list).
 *  3. The work: offline downloads go to the download service; MP3 / video go to ONE export queue
 *     ([AudioExportService.startBatch]) that shows a single progress notification.
 *
 * A single song skips step 2. Every action this replaces still lives in the ⋮ menus (nothing is removed).
 *
 * @param offlineState `Download.STATE_*` of the whole set, so the offline row can offer to cancel / remove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraDownloadSheet(
    songs: List<AuraDownloadItem>,
    title: String,
    offlineState: Int,
    onDismiss: () -> Unit,
    /** Called when something was actually queued (offline or export), before [onDismiss]: lets a host menu close too. */
    onDone: () -> Unit = {},
    /** True while the live video of the playing song is on screen: an offline download of it waits for that to end. */
    deferOfflineWhileLiveVideo: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val (enableExportAsMp3) = rememberPreference(EnableExportAsMp3Key, true)
    val (exportDirectoryUri, onExportDirectoryUriChange) = rememberPreference(ExportDirectoryUriKey, "")
    val ensureMp3Folder = rememberMp3ExportFolderAccess(
        exportDirectoryUri = exportDirectoryUri,
        onExportDirectoryUriChange = onExportDirectoryUriChange,
    )

    val single = songs.size == 1
    val videoItems = remember(songs) { songs.filter { it.isVideo } }
    // One song: probe its video for real, like the old chooser. A list: the songs flagged as video are the ones offered.
    val availability = rememberExportVideoAvailability(
        songId = if (single) songs[0].id else "",
        hasMusicVideo = single && songs[0].isVideo,
    )

    var step by remember { mutableStateOf(DownloadSheetStep.Format) }
    var chosen by remember { mutableStateOf(ExportFormat.Offline) }
    var picked by remember { mutableStateOf(emptySet<String>()) }

    fun scopeItems(format: ExportFormat): List<AuraDownloadItem> =
        if (format == ExportFormat.Video) videoItems else songs

    fun perform(format: ExportFormat, selection: List<AuraDownloadItem>) {
        if (selection.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.playlist_download_empty), Toast.LENGTH_SHORT).show()
            onDismiss()
            return
        }
        when (format) {
            ExportFormat.Offline -> {
                selection.forEach { item ->
                    item.metadata?.let { meta -> database.transaction { insert(meta) } }
                    enqueueSongDownloads(
                        context = context,
                        songId = item.id,
                        title = item.title,
                        // The companion video only for the single song, as the player always did; a whole list is audio.
                        isVideoSong = single && item.downloadsCompanionVideo,
                        deferWhileLiveVideo = single && deferOfflineWhileLiveVideo,
                    )
                }
                onDone()
                onDismiss()
            }

            ExportFormat.Mp3, ExportFormat.Video -> ensureMp3Folder { directoryUri ->
                AudioExportService.startBatch(
                    context.applicationContext,
                    selection.map { item ->
                        AudioExportTask(
                            songId = item.id,
                            title = item.title,
                            artist = item.artist,
                            album = item.album,
                            artworkUrl = item.thumbnailUrl,
                            targetDirectoryUri = directoryUri,
                            exportAsVideo = format == ExportFormat.Video,
                        )
                    },
                )
                if (selection.size > 1) {
                    Toast.makeText(
                        context,
                        context.resources.getQuantityString(R.plurals.export_batch_started, selection.size, selection.size),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                onDone()
                onDismiss()
            }
        }
    }

    /** A format was tapped: a list asks «todas o elegir» first; a single song (or a list of one) just goes. */
    fun chooseFormat(format: ExportFormat) {
        chosen = format
        val pool = scopeItems(format)
        if (pool.size <= 1) {
            perform(format, pool)
        } else {
            step = DownloadSheetStep.Scope
        }
    }

    AuraModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (step) {
                DownloadSheetStep.Format -> {
                    SheetHeader(
                        title = stringResource(R.string.download_or_export_title),
                        subtitle = if (single) title else pluralStringResource(R.plurals.n_song, songs.size, songs.size) + " · " + title,
                    )
                    when (offlineState) {
                        Download.STATE_COMPLETED -> DownloadSheetRow(
                            icon = AuraIcons.Downloaded,
                            title = stringResource(R.string.download_sheet_remove),
                            description = stringResource(R.string.download_sheet_remove_desc),
                            onClick = { step = DownloadSheetStep.ConfirmRemove },
                        )

                        Download.STATE_DOWNLOADING, Download.STATE_QUEUED -> DownloadSheetRow(
                            icon = AuraIcons.Download,
                            title = stringResource(R.string.downloading),
                            description = stringResource(R.string.download_sheet_cancel_hint),
                            busy = true,
                            onClick = {
                                songs.forEach { removeSongDownloads(context, it.id, single && it.downloadsCompanionVideo) }
                                onDismiss()
                            },
                        )

                        else -> DownloadSheetRow(
                            icon = AuraIcons.Download,
                            title = stringResource(R.string.download_offline_only),
                            description = stringResource(R.string.download_offline_only_desc),
                            onClick = { chooseFormat(ExportFormat.Offline) },
                        )
                    }
                    if (enableExportAsMp3) {
                        DownloadSheetRow(
                            icon = AuraIcons.Export,
                            title = stringResource(R.string.export_as_mp3),
                            description = stringResource(R.string.export_as_mp3_desc),
                            onClick = { chooseFormat(ExportFormat.Mp3) },
                        )
                        if (videoItems.isNotEmpty()) {
                            val videoDescription = when {
                                !single -> stringResource(R.string.download_sheet_video_count, videoItems.size)
                                availability.loading -> stringResource(R.string.export_video_probing)
                                availability.enabled -> stringResource(R.string.export_as_video_desc)
                                else -> stringResource(availability.messageRes ?: R.string.export_video_unavailable)
                            }
                            DownloadSheetRow(
                                icon = AuraIcons.Video,
                                title = stringResource(R.string.export_as_video),
                                description = videoDescription,
                                enabled = !single || availability.enabled,
                                busy = single && availability.loading,
                                onClick = { chooseFormat(ExportFormat.Video) },
                            )
                        }
                    }
                }

                DownloadSheetStep.Scope -> {
                    val pool = scopeItems(chosen)
                    SheetHeader(title = formatTitle(chosen), subtitle = title)
                    DownloadSheetRow(
                        icon = AuraIcons.Check,
                        title = stringResource(R.string.download_sheet_all, pool.size),
                        description = stringResource(R.string.download_sheet_all_desc),
                        onClick = { perform(chosen, pool) },
                    )
                    DownloadSheetRow(
                        icon = AuraIcons.Music,
                        title = stringResource(R.string.download_sheet_pick),
                        description = stringResource(R.string.download_sheet_pick_desc),
                        onClick = {
                            picked = pool.map { it.id }.toSet()
                            step = DownloadSheetStep.Pick
                        },
                    )
                    SheetBack { step = DownloadSheetStep.Format }
                }

                DownloadSheetStep.Pick -> {
                    val pool = scopeItems(chosen)
                    SheetHeader(title = formatTitle(chosen), subtitle = title)
                    PickRow(
                        label = stringResource(R.string.download_sheet_select_all),
                        checked = picked.size == pool.size,
                        onToggle = { picked = if (picked.size == pool.size) emptySet() else pool.map { it.id }.toSet() },
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(pool, key = { it.id }) { item ->
                            PickRow(
                                label = item.title,
                                supporting = item.artist,
                                checked = item.id in picked,
                                onToggle = { picked = if (item.id in picked) picked - item.id else picked + item.id },
                            )
                        }
                    }
                    AuraDetailActionButton(
                        icon = AuraIcons.Download,
                        label = stringResource(R.string.download_sheet_start, picked.size),
                        accent = true,
                        onClick = { if (picked.isNotEmpty()) perform(chosen, pool.filter { it.id in picked }) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SheetBack { step = DownloadSheetStep.Scope }
                }

                DownloadSheetStep.ConfirmRemove -> {
                    SheetHeader(
                        title = stringResource(R.string.download_sheet_remove),
                        subtitle = stringResource(R.string.download_sheet_remove_confirm, songs.size),
                    )
                    AuraDetailActionButton(
                        icon = AuraIcons.Downloaded,
                        label = stringResource(R.string.download_sheet_remove),
                        accent = true,
                        onClick = {
                            songs.forEach { removeSongDownloads(context, it.id, single && it.downloadsCompanionVideo) }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SheetBack { step = DownloadSheetStep.Format }
                }
            }
        }
    }
}

@Composable
private fun formatTitle(format: ExportFormat): String = stringResource(
    when (format) {
        ExportFormat.Offline -> R.string.download_offline_only
        ExportFormat.Mp3 -> R.string.export_as_mp3
        ExportFormat.Video -> R.string.export_as_video
    },
)

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(text = title, style = AuraType.RowTitle, color = AuraPalette.OnGround, maxLines = 1, overflow = AuraDefaultOverflow)
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = AuraType.MiniArtist,
                color = AuraPalette.OnGroundMuted,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
        }
    }
}

@Composable
private fun SheetBack(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = stringResource(R.string.download_sheet_back), color = AuraPalette.OnGroundMuted)
    }
}

@Composable
private fun DownloadSheetRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val ink: Color = if (enabled) AuraPalette.OnGround else AuraPalette.OnGroundFaint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AuraPalette.SurfaceFill)
            .border(1.dp, AuraPalette.SurfaceLine, RoundedCornerShape(16.dp))
            .auraClickableInternal(onClick = onClick, enabled = enabled, contentDescription = title)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(AuraPalette.SurfaceLine),
        ) {
            AuraIconGlyph(icon = icon, contentDescription = null, size = 20.dp, tint = ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AuraType.MenuLabel, color = ink, maxLines = 1, overflow = AuraDefaultOverflow)
            Text(
                text = description,
                style = AuraType.RowSubtitle,
                color = AuraPalette.OnGroundFaint,
                maxLines = 2,
                overflow = AuraDefaultOverflow,
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AuraPalette.Teal)
        } else {
            AuraIconGlyph(icon = AuraIcons.ChevronRight, contentDescription = null, size = 18.dp, tint = AuraPalette.OnGroundFaint)
        }
    }
}

@Composable
private fun PickRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    supporting: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .auraClickableInternal(onClick = onToggle, contentDescription = label)
            .padding(vertical = 2.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = AuraType.RowSubtitle, color = AuraPalette.OnGround, maxLines = 1, overflow = AuraDefaultOverflow)
            if (!supporting.isNullOrBlank()) {
                Text(text = supporting, style = AuraType.MiniArtist, color = AuraPalette.OnGroundMuted, maxLines = 1, overflow = AuraDefaultOverflow)
            }
        }
    }
}
