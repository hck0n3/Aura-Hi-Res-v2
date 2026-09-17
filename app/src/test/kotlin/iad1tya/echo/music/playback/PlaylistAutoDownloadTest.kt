package iad1tya.echo.music.playback

import androidx.media3.exoplayer.offline.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloquea el punto 3 del dueño (2026-09-17): *"si está activo, todo el contenido actual y cualquier
 * canción que se agregue en el futuro a esa lista se descargará automáticamente"*.
 *
 * El fallo que hay que impedir no da ningún error: es **encolar de más** (una tormenta de peticiones
 * sobre una lista grande, cada vez que se reconcilia) o **de menos** (canciones que él cree descargadas
 * y no están).
 */
class PlaylistAutoDownloadTest {

    @Test
    fun `encola lo que no esta descargado`() {
        val pending = PlaylistAutoDownload.songsToEnqueue(
            inPlaylists = listOf("a", "b", "c"),
            stateById = mapOf("a" to Download.STATE_COMPLETED),
        )
        assertEquals(listOf("b", "c"), pending)
    }

    /** Idempotente: reconciliar dos veces no puede encolar nada dos veces. */
    @Test
    fun `lo que ya esta en marcha no se vuelve a encolar`() {
        val states = mapOf(
            "a" to Download.STATE_COMPLETED,
            "b" to Download.STATE_DOWNLOADING,
            "c" to Download.STATE_QUEUED,
            "d" to Download.STATE_RESTARTING,
        )
        assertTrue(
            PlaylistAutoDownload.songsToEnqueue(listOf("a", "b", "c", "d"), states).isEmpty(),
        )
    }

    /**
     * Pero una que FALLÓ sí se reintenta: sin red, disco lleno o un 403 son estados de los que se sale,
     * y dejarla fuera para siempre sería justo lo contrario de lo que el interruptor promete.
     */
    @Test
    fun `una descarga fallida se reintenta`() {
        assertEquals(
            listOf("a"),
            PlaylistAutoDownload.songsToEnqueue(listOf("a"), mapOf("a" to Download.STATE_FAILED)),
        )
    }

    /** Y una en pausa también: si el usuario reanuda las descargas tiene que seguir en la cola. */
    @Test
    fun `una descarga en pausa sigue en la cola`() {
        assertEquals(
            listOf("a"),
            PlaylistAutoDownload.songsToEnqueue(listOf("a"), mapOf("a" to Download.STATE_STOPPED)),
        )
    }

    /**
     * Una canción en DOS listas con descarga automática se encola UNA vez. Sin esto, cada lista pediría
     * la misma descarga y el `DownloadService` recibiría peticiones duplicadas por cada solapamiento.
     */
    @Test
    fun `una cancion en dos listas se encola una sola vez`() {
        assertEquals(
            listOf("a", "b"),
            PlaylistAutoDownload.songsToEnqueue(listOf("a", "b", "a", "a"), emptyMap()),
        )
    }

    /** Un id vacío no se encola: vendría de una fila a medio escribir y pediría una descarga imposible. */
    @Test
    fun `los ids vacios no se encolan`() {
        assertEquals(
            listOf("a"),
            PlaylistAutoDownload.songsToEnqueue(listOf("", "  ", "a"), emptyMap()),
        )
    }

    /** Sin listas con el interruptor puesto no hay nada que hacer. */
    @Test
    fun `sin listas no encola nada`() {
        assertTrue(PlaylistAutoDownload.songsToEnqueue(emptyList(), emptyMap()).isEmpty())
    }
}
