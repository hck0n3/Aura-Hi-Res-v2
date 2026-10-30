package iad1tya.echo.music.devicesync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow

/** Another of the user's devices, as the room lists it. */
data class SyncDevice(val id: String, val name: String, val kind: String)

/** One song of a queue that travels between devices: enough to show it and to play it from the id. */
data class NowItem(val id: String, val title: String, val artist: String, val cover: String)

/** What a device is playing, as published to the room. [atMs] is the SERVER clock when it was published. */
data class NowState(
    val items: List<NowItem>,
    val index: Int,
    val positionMs: Long,
    val playing: Boolean,
    val deviceId: String,
    val deviceName: String,
    val atMs: Long,
) {
    val current: NowItem? get() = items.getOrNull(index)

    /** Where the song is NOW if it kept playing since it was published (the clocks are the room's, so no skew between devices). */
    fun estimatedPositionMs(serverNowMs: Long): Long =
        if (playing) positionMs + (serverNowMs - atMs).coerceAtLeast(0L) else positionMs
}

/**
 * The window between the screens and the engine that lives in the playback service (row 528). The screens only read the flows and
 * call the functions; [DeviceSyncEngine] fills the flows and answers the calls. When no engine is running (not paired, service
 * down) every call is a no-op and [connection] says why.
 */
object DeviceSyncHub {
    sealed interface Connection {
        data object Off : Connection
        data object Connecting : Connection
        data object Connected : Connection
        data class Retrying(val inSeconds: Int) : Connection
    }

    val connection = MutableStateFlow<Connection>(Connection.Off)
    val devices = MutableStateFlow<List<SyncDevice>>(emptyList())
    val remoteNow = MutableStateFlow<NowState?>(null)
    val thisDeviceId = MutableStateFlow("")

    /** Short messages for the screen to show once (a device that is not there, a hand-over that did not arrive). */
    val notices: SharedFlow<String> get() = _notices
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    fun notice(text: String) {
        _notices.tryEmit(text)
    }

    interface Controller {
        /** play / pause / next / prev to [deviceId]. */
        fun control(deviceId: String, command: String)

        /** Bring what [deviceId] is playing to THIS device and carry on from where it was. */
        fun takeOverFrom(deviceId: String)

        /** Send what THIS device is playing to [deviceId] and pause here. */
        fun sendTo(deviceId: String)

        /** The library changed here (likes, lists): tell the others so they refresh at once. */
        fun libraryChanged(what: String)
    }

    @Volatile
    var controller: Controller? = null
}
