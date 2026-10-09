package iad1tya.echo.music.playback

import iad1tya.echo.music.models.MediaMetadata
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fila 361 — the background fill of "Pedir música" goes STRAIGHT to the service.
 *
 * It used to travel through a flow collected by the Home card, so leaving Inicio before the fill arrived (a few
 * seconds after the music started) dropped it silently and the request ended after its first 8 songs. The
 * service lives as long as the music does; it collects this and calls [MusicService.extendQueueForContext],
 * which still refuses a fill whose queue was replaced in the meantime.
 */
object MusicRequestFill {
    data class Event(val contextId: String, val songs: List<MediaMetadata>)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun post(contextId: String, songs: List<MediaMetadata>) {
        if (songs.isNotEmpty()) _events.tryEmit(Event(contextId, songs))
    }
}
