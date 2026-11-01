package iad1tya.echo.music.ui.newui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.EnableExportAsMp3Key
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.constants.ExportedSongIdsKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.playback.AudioExportService
import iad1tya.echo.music.playback.AudioExportTask
import iad1tya.echo.music.ui.component.AuraModalBottomSheet
import iad1tya.echo.music.ui.utils.rememberExportVideoAvailability
import iad1tya.echo.music.ui.utils.rememberMp3ExportFolderAccess
import iad1tya.echo.music.utils.ShareLinks
import iad1tya.echo.music.utils.lookupExportedFileUri
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.utils.shareContentUri
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * One song the user asked to share. Every «Compartir» of a song opens [AuraShareSheet] with one of these, so the choice is the
 * same everywhere (player, ⋯ menus, queue).
 */
@Immutable
data class ShareSongRequest(
    val songId: String,
    val title: String,
    val artists: List<String>,
    val album: String = "",
    val thumbnailUrl: String = "",
    /** A real music video exists for it: the only songs offered «Vídeo». */
    val hasVideo: Boolean = false,
)

/** The switch that opens the share sheet, read by [ShareChoiceHost]. A global like the recognition window's. */
object ShareChoiceState {
    val request = mutableStateOf<ShareSongRequest?>(null)

    fun open(request: ShareSongRequest) {
        if (request.songId.isBlank()) return
        this.request.value = request
    }

    fun close() {
        request.value = null
    }
}

/** Composed once, near the other floating windows: reads [ShareChoiceState] so the app does not recompose when it opens. */
@Composable
fun ShareChoiceHost() {
    val request = ShareChoiceState.request.value ?: return
    AuraShareSheet(request = request, onDismiss = ShareChoiceState::close)
}

private enum class ShareKind { Link, Mp3, Video }

/**
 * Owner 2026-10-29: «la opción de compartir que pregunte si quiero compartir por enlace o por MP3 si solo hay audio; si la
 * canción tiene vídeo que también pregunte; y si no están descargadas y elijo MP3 o vídeo, que lo descargue al seleccionar y
 * luego me aparezca el menú de compartir para ver con quién lo comparto».
 *
 * Enlace → the system share sheet at once. MP3 / Vídeo → the file is shared at once when it is already saved; otherwise it is
 * exported (the same service and folder as «Exportar a MP3/vídeo»), the sheet shows the progress, and when the file exists the
 * system share sheet opens by itself. Closing the sheet never cancels the export; it just stops waiting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraShareSheet(request: ShareSongRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (enableExportAsMp3) = rememberPreference(EnableExportAsMp3Key, true)
    val (exportDirectoryUri, onExportDirectoryUriChange) = rememberPreference(ExportDirectoryUriKey, "")
    val (exportedSongIds) = rememberPreference(ExportedSongIdsKey, "")
    val (exportedVideoIds) = rememberPreference(ExportedVideoIdsKey, "")
    val ensureFolder = rememberMp3ExportFolderAccess(
        exportDirectoryUri = exportDirectoryUri,
        onExportDirectoryUriChange = onExportDirectoryUriChange,
    )
    val availability = rememberExportVideoAvailability(songId = request.songId, hasMusicVideo = request.hasVideo)
    val liveProgress by AudioExportService.liveProgress.collectAsState()

    val mp3Saved = request.songId in exportedSongIds.split(',').map { it.trim() }
    val videoSaved = request.songId in exportedVideoIds.split(',').map { it.trim() }

    var waiting by remember(request.songId) { mutableStateOf<ShareKind?>(null) }

    fun shareLink() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, ShareLinks.songShareText(request.songId, request.title, request.artists))
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        onDismiss()
    }

    fun startExport(kind: ShareKind) {
        ensureFolder { directoryUri ->
            AudioExportService.startBatch(
                context.applicationContext,
                listOf(
                    AudioExportTask(
                        songId = request.songId,
                        title = request.title,
                        artist = request.artists.joinToString(),
                        album = request.album,
                        artworkUrl = request.thumbnailUrl,
                        targetDirectoryUri = directoryUri,
                        exportAsVideo = kind == ShareKind.Video,
                    ),
                ),
            )
            waiting = kind
        }
    }

    // A file that was exported before and is still there goes straight to the system share sheet.
    suspend fun shareSavedFile(kind: ShareKind): Boolean {
        val uri = lookupExportedFileUri(context, request.songId) ?: return false
        return shareContentUri(context, uri, if (kind == ShareKind.Video) "video/mp4" else "audio/mpeg")
    }

    var checking by remember(request.songId) { mutableStateOf<ShareKind?>(null) }
    LaunchedEffect(checking) {
        val kind = checking ?: return@LaunchedEffect
        checking = null
        val saved = if (kind == ShareKind.Video) videoSaved else mp3Saved
        Timber.tag("SHARE").i("SHARE_CHOICE kind=%s saved=%b", kind, saved)
        if (saved && shareSavedFile(kind)) {
            onDismiss()
        } else {
            startExport(kind)
        }
    }

    // After an export was started: wait for the file (at most 10 minutes), then open the system share sheet.
    LaunchedEffect(waiting) {
        val kind = waiting ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + EXPORT_WAIT_MS
        var started = false
        while (System.currentTimeMillis() < deadline) {
            val running = AudioExportService.liveProgress.value.containsKey(request.songId)
            if (running) started = true
            val uri = lookupExportedFileUri(context, request.songId)
            if (uri != null && (started || System.currentTimeMillis() - (deadline - EXPORT_WAIT_MS) > EXPORT_GRACE_MS) && !running) {
                if (shareContentUri(context, uri, if (kind == ShareKind.Video) "video/mp4" else "audio/mpeg")) {
                    Timber.tag("SHARE").i("SHARE_CHOICE exported kind=%s -> system share", kind)
                    onDismiss()
                    return@LaunchedEffect
                }
            }
            delay(EXPORT_POLL_MS)
        }
        Timber.tag("SHARE").w("SHARE_CHOICE export did not finish in time kind=%s", kind)
        Toast.makeText(context, context.getString(R.string.share_export_timeout), Toast.LENGTH_LONG).show()
        waiting = null
    }

    val skin = rememberAuraPanelSkin()
    val inWindowGlass = skin.enabled && skin.darkGround && LocalOverlayHazeState.current != null && auraOverlayHostAvailable()

    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val kind = waiting
            if (kind == null) {
                SheetHeader(title = stringResource(R.string.share_choice_title), subtitle = request.title)
                DownloadSheetRow(
                    icon = AuraIcons.Share,
                    title = stringResource(R.string.share_choice_link),
                    description = stringResource(R.string.share_choice_link_desc),
                    onClick = { shareLink() },
                )
                if (enableExportAsMp3) {
                    DownloadSheetRow(
                        icon = AuraIcons.Export,
                        title = stringResource(R.string.share_choice_mp3),
                        description = stringResource(if (mp3Saved) R.string.share_choice_saved_desc else R.string.share_choice_mp3_desc),
                        onClick = { checking = ShareKind.Mp3 },
                    )
                    if (request.hasVideo) {
                        val videoDescription = when {
                            availability.loading -> stringResource(R.string.export_video_probing)
                            availability.enabled ->
                                stringResource(if (videoSaved) R.string.share_choice_saved_desc else R.string.share_choice_video_desc)
                            else -> stringResource(availability.messageRes ?: R.string.export_video_unavailable)
                        }
                        DownloadSheetRow(
                            icon = AuraIcons.Video,
                            title = stringResource(R.string.share_choice_video),
                            description = videoDescription,
                            enabled = availability.enabled,
                            busy = availability.loading,
                            onClick = { checking = ShareKind.Video },
                        )
                    }
                }
            } else {
                val percent = liveProgress[request.songId]?.toInt()
                SheetHeader(
                    title = stringResource(
                        if (kind == ShareKind.Video) R.string.share_preparing_video else R.string.share_preparing_mp3,
                    ),
                    subtitle = request.title,
                )
                DownloadSheetRow(
                    icon = if (kind == ShareKind.Video) AuraIcons.Video else AuraIcons.Export,
                    title = if (percent != null && percent in 0..100) "$percent %" else stringResource(R.string.share_preparing_wait),
                    description = stringResource(R.string.share_preparing_desc),
                    busy = true,
                    onClick = {},
                    enabled = false,
                )
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.share_preparing_hide), color = AuraPalette.OnGroundMuted)
                }
            }
        }
    }

    if (inWindowGlass) {
        AuraInWindowDialog(visible = true, onDismiss = onDismiss, center = false, fullHeight = false) { body() }
    } else {
        AuraModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { body() }
    }
}

private const val EXPORT_WAIT_MS = 10 * 60 * 1000L
private const val EXPORT_POLL_MS = 600L

/** A file left from an earlier export of the same song must not count as «done» in the first moments of a new one. */
private const val EXPORT_GRACE_MS = 4_000L
