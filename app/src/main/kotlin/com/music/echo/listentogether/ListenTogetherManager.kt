package iad1tya.echo.music.listentogether

import android.content.Context
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.ListenTogetherBlockedUsersKey
import iad1tya.echo.music.constants.ListenTogetherSessionTokenKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Role of this device in a room; NONE whenever no room is live. */
enum class RoomRole {
    NONE,
    HOST,
    GUEST,
}

/**
 * The app-facing façade of Listen Together.
 *
 * Upstream SimpMusic splits this across `ListenTogetherRepository` (domain interface), its Impl and
 * a Koin module; this app has one entry point, and it is this. It OWNS the three real objects
 * ([ListenTogetherClient] transport, [ListenTogetherSession] room state machine,
 * [ListenTogetherPlaybackBridge] player glue) and exposes them with the shape the app already
 * consumes (role/roomState/connectionState flows that the previous implementation published), so
 * screens keep reading one source of truth.
 *
 * Lives for as long as the app does (@Singleton): the room outlives its screen, because playback —
 * the thing the room is about — keeps running in the service.
 */
@Singleton
class ListenTogetherManager @Inject constructor(
    val client: ListenTogetherClient,
    val session: ListenTogetherSession,
    val bridge: ListenTogetherPlaybackBridge,
    private val tokenStore: SessionTokenStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ListenTogetherManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Room state, as every screen reads it. Null `roomCode` inside = not in a room. */
    val roomState: StateFlow<ListenTogetherState?> = session.state

    /** Connection state of the socket. */
    val connectionState: StateFlow<ConnectionState> =
        session.state
            .map { it.connection }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, ConnectionState.Disconnected)

    /** HOST / GUEST / NONE, derived once per room-state change. */
    val role: StateFlow<RoomRole> =
        session.state
            .map {
                when {
                    !it.inRoom -> RoomRole.NONE
                    it.isHost -> RoomRole.HOST
                    else -> RoomRole.GUEST
                }
            }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, RoomRole.NONE)

    /** Users still loading the current track (the buffer barrier said so). */
    val bufferingUsers: StateFlow<List<String>> =
        session.state
            .map { it.waitingFor }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** Users waiting to be let in. */
    val pendingJoinRequests: StateFlow<List<PendingJoin>> =
        session.state
            .map { it.joinRequests }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** Track suggestions waiting for the host. */
    val pendingSuggestions: StateFlow<List<PendingSuggestion>> =
        session.state
            .map { it.suggestions }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    /** Client-side blocklist by USERNAME (names, not ids — the server mints fresh ids per connect). */
    private val _blockedUsernames = MutableStateFlow<List<String>>(emptyList())
    val blockedUsernames: StateFlow<List<String>> = _blockedUsernames.asStateFlow()

    /** The last room code this device was in, persisted. */
    val persistedRoomCode: String? get() = tokenStore.cachedRoomCode

    val isInRoom: Boolean get() = session.state.value.inRoom
    val isHost: Boolean get() = session.state.value.isHost

    init {
        // Host conveniences from settings, mirrored onto the session for as long as the app runs
        // (upstream does this from the screen's ViewModel; the session here outlives screens, so
        // the mirror must too).
        scope.launch {
            context.dataStore.data.collect { prefs ->
                session.autoApproveJoins = prefs[iad1tya.echo.music.constants.ListenTogetherAutoApprovalKey] ?: false
            }
        }
        // Blocklist, read once per process; the setters keep the file in sync.
        scope.launch {
            val raw = context.dataStore.get(ListenTogetherBlockedUsersKey, "")
            _blockedUsernames.value = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }
        // NOTE: session resumption runs from MainActivity.onCreate via [connectIfResumable] —
        // NOT here — so a plain Hilt graph instantiation (tests, :phoenix process) does not open
        // sockets on its own.
    }

    fun connect() = session.connect()

    /**
     * The manual "Reconectar" of the room dialog (UI_INVENTORY 5.2). Not the same as [connect]:
     * the socket may be wedged-but-technically-alive (half-open TCP, stalled handshake), which
     * [ListenTogetherSession.connect] cannot fix because the client's connect() ignores a
     * connection it considers active. This closes the transport WITHOUT clearing the session
     * token, so the next [connect] replays it and the room resumes — clearing the token here
     * would turn a user-requested refresh into losing the room (registry row 144).
     */
    fun forceReconnect() {
        client.dropSocketKeepToken()
        session.connect()
    }

    /**
     * Called once from MainActivity.onCreate: opens the socket ONLY when a persisted session
     * token exists (the room is resumable). A cold start with no room stays quiet — upstream's
     * `createdAtStart` bridge is what handles the always-on part, and here the bridge starts on
     * the first PlayerConnection.
     */
    fun connectIfResumable() {
        scope.launch {
            val token = context.dataStore.get(ListenTogetherSessionTokenKey, "")
            if (token.isNotBlank()) session.connect()
        }
    }

    fun disconnect() = session.disconnect()

    fun createRoom(username: String) = session.createRoom(username)

    fun joinRoom(
        roomCode: String,
        username: String,
    ) = session.joinRoom(roomCode, username)

    fun leaveRoom() = session.leaveRoom()

    fun approveJoin(userId: String) = session.approveJoin(userId)

    fun rejectJoin(
        userId: String,
        reason: String? = null,
    ) = session.rejectJoin(userId)

    fun kickUser(
        userId: String,
        reason: String? = null,
    ) = session.kickUser(userId)

    fun transferHost(newHostId: String) = session.transferHost(newHostId)

    fun suggestTrack(track: TrackInfo) = session.suggestTrack(track)

    fun approveSuggestion(suggestionId: String) = session.approveSuggestion(suggestionId)

    fun rejectSuggestion(
        suggestionId: String,
        reason: String? = null,
    ) = session.rejectSuggestion(suggestionId)

    fun requestSync() = session.requestSync()

    fun clearError() = session.clearError()

    fun blockUser(username: String) {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            context.dataStore.edit { prefs ->
                val raw = prefs[ListenTogetherBlockedUsersKey].orEmpty()
                val names = raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                if (names.none { it.equals(trimmed, ignoreCase = true) }) {
                    names += trimmed
                    prefs[ListenTogetherBlockedUsersKey] = names.joinToString("\n")
                }
            }
            if (_blockedUsernames.value.none { it.equals(trimmed, ignoreCase = true) }) {
                _blockedUsernames.value = (_blockedUsernames.value + trimmed).distinct()
            }
        }
    }

    fun unblockUser(username: String) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val raw = prefs[ListenTogetherBlockedUsersKey].orEmpty()
                val names =
                    raw.split('\n')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.equals(username, ignoreCase = true) }
                prefs[ListenTogetherBlockedUsersKey] = names.joinToString("\n")
            }
            _blockedUsernames.value = _blockedUsernames.value.filterNot { it.equals(username, ignoreCase = true) }
        }
    }

    // ─────────────────── the player seam (PlayerConnection / MainActivity) ───────────────────

    /**
     * The UI process hands its PlayerConnection over exactly once per bind; the bridge keeps it
     * and installs the guest-resume / host-seek listeners on the live player.
     */
    fun setPlayerConnection(connection: iad1tya.echo.music.playback.PlayerConnection?) {
        bridge.setPlayerConnection(connection)
        if (connection != null) {
            bridge.start(scope)
            runCatching {
                bridge.installGuestResumeListener(connection.player)
                bridge.installHostSeekPublisher(connection.player)
            }
        }
    }

    /**
     * The guest must not drive the queue through the normal UI paths — the room owns it while we
     * follow someone. PlayerConnection.shouldBlockPlaybackChanges consults this.
     */
    fun shouldBlockPlaybackChanges(): Boolean = isInRoom && !isHost
}
