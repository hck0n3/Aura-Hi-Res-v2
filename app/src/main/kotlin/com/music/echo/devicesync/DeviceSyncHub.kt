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
    /** Length of the current song, or 0 when the player does not know it yet. */
    val durationMs: Long = 0L,
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

    /** Server clock minus this device's clock, from the last `hello_ok`; lets a screen tell how old a published state is. */
    @Volatile
    var serverOffsetMs: Long = 0L

    fun serverNow(): Long = System.currentTimeMillis() + serverOffsetMs

    /**
     * True while the library (likes, dislikes, playlists) is kept equal between the user's devices through the Worker and the room
     * has answered once: YouTube's PULL syncs of likes and playlists stay out of the way then (row 536). Pushes of the user's own
     * actions to their YouTube account are not affected.
     */
    @Volatile
    var libraryOwnedByDevices: Boolean = false

    /** What the library sync (likes, dislikes, playlists) is doing, for Ajustes — so «nunca funciona» is never a mystery (row 536). */
    sealed interface LibraryStatus {
        data object Off : LibraryStatus
        data object Waiting : LibraryStatus

        /** The deployed Worker does not speak the library protocol (older than this app): it must be deployed again. */
        data object ServerOutdated : LibraryStatus
        data object Syncing : LibraryStatus
        data class Synced(val atMs: Long, val known: Int, val pending: Int) : LibraryStatus
        data class Failed(val reason: String) : LibraryStatus
    }

    val libraryStatus = MutableStateFlow<LibraryStatus>(LibraryStatus.Off)

    /** How this device got paired: the screens say it in words. */
    enum class PairedBy { None, License, Code }

    val pairedBy = MutableStateFlow(PairedBy.None)

    /** Bumped when the license changes (activation) so the engine re-reads who it is paired with. */
    val pairingRefresh = MutableStateFlow(0)

    /** Short messages for the screen to show once (a device that is not there, a hand-over that did not arrive). */
    val notices: SharedFlow<String> get() = _notices
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    fun notice(text: String) {
        _notices.tryEmit(text)
    }

    interface Controller {
        /** play / pause / next / prev / seek (with [positionMs]) to [deviceId]. */
        fun control(deviceId: String, command: String, positionMs: Long? = null)

        /** Bring what [deviceId] is playing to THIS device and carry on from where it was. */
        fun takeOverFrom(deviceId: String)

        /** Send what THIS device is playing to [deviceId] and pause here. */
        fun sendTo(deviceId: String)

        /** The library changed here (likes, lists): tell the others so they refresh at once. */
        fun libraryChanged(what: String)

        /** «Sincronizar ahora» of the library: pull what changed, then push what is different here. */
        fun syncLibraryNow()
    }

    @Volatile
    var controller: Controller? = null
}
