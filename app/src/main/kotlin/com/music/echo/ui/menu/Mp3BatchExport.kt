package iad1tya.echo.music.ui.menu

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ExportDirectoryUriKey
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.playback.AudioExportService
import iad1tya.echo.music.playback.AudioExportTask
import iad1tya.echo.music.utils.rememberPreference

/** What the export service needs to write one MP3 (file, tags and cover). */
data class Mp3ExportTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
)

fun Song.toMp3ExportTrack(): Mp3ExportTrack = Mp3ExportTrack(
    id = id,
    title = song.title,
    artist = artists.joinToString(", ") { it.name },
    album = song.albumName.orEmpty(),
    artworkUrl = song.thumbnailUrl.orEmpty(),
)

/**
 * «Descargar todo el álbum a MP3» (dueño, 2026-10-18: *«quiero poder descargar todo el álbum a MP3, o singles, o EPs,
 * o playlists enteras»*). Las listas ya tenían «Exportar a MP3» en su menú ⋮; los álbumes, EPs y sencillos no. Esta es
 * la misma mecánica en un solo sitio, para los menús de álbum: elige carpeta una vez y escribe TODAS las canciones, de
 * una en una, con etiquetas y portada.
 *
 * 2026-10-19: la lista entera se entrega al servicio de una vez (`AudioExportService.startBatch`) y él la recorre bajo
 * UNA notificación con barra de progreso. Antes cada canción era un arranque del servicio, con su notificación que se
 * quitaba y volvía a salir, y el bucle vivía en el scope del propio menú (que se cancelaba al cerrarlo).
 *
 * Devuelve la función que se llama con las canciones: abre el selector de carpeta y, al elegirla, lanza la exportación
 * en segundo plano. [onStarted] cierra el menú.
 */
@Composable
fun rememberMp3BatchExport(onStarted: () -> Unit): (List<Mp3ExportTrack>) -> Unit {
    val context = LocalContext.current
    val (_, onExportDirectoryUriChange) = rememberPreference(key = ExportDirectoryUriKey, defaultValue = "")
    var pending by remember { mutableStateOf<List<Mp3ExportTrack>>(emptyList()) }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val tracks = pending
        pending = emptyList()
        if (uri == null || tracks.isEmpty()) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        onExportDirectoryUriChange(uri.toString())
        Toast.makeText(
            context,
            context.resources.getQuantityString(R.plurals.export_batch_started, tracks.size, tracks.size),
            Toast.LENGTH_LONG,
        ).show()
        onStarted()
        // The whole list goes to the service in ONE start (see AudioExportService.startBatch): it works the queue off
        // song after song under a single progress notification, and no longer depends on this menu staying
        // composed (this scope used to be the menu's own, cancelled when [onStarted] closed it).
        AudioExportService.startBatch(
            context.applicationContext,
            tracks.map { track ->
                AudioExportTask(
                    songId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.artworkUrl,
                    targetDirectoryUri = uri.toString(),
                )
            },
        )
    }

    return { tracks ->
        if (tracks.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.export_batch_nothing), Toast.LENGTH_SHORT).show()
        } else {
            pending = tracks
            try {
                folderLauncher.launch(null)
            } catch (_: ActivityNotFoundException) {
                pending = emptyList()
                Toast.makeText(
                    context,
                    context.getString(R.string.export_directory_picker_unavailable),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
