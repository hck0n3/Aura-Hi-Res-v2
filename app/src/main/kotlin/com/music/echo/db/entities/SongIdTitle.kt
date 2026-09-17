package iad1tya.echo.music.db.entities

/**
 * Solo el id y el título de una canción.
 *
 * Existe para [iad1tya.echo.music.playback.PlaylistAutoDownload]: encolar una descarga necesita el
 * título (va en `DownloadRequest.setData`, y es lo que la notificación del sistema muestra) y **nada
 * más**. Traerse la fila `Song` entera para eso serían todas las columnas, sus `@Relation` de artistas
 * y álbum, y un `@Transaction` — por cada canción de cada lista con descarga automática, en cada
 * reconciliación.
 */
data class SongIdTitle(
    val id: String,
    val title: String,
)
