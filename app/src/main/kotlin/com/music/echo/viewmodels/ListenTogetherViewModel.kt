package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import iad1tya.echo.music.listentogether.ListenTogetherManager
import iad1tya.echo.music.listentogether.TrackInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin window over the [ListenTogetherManager] singleton (SimpMusic's ListenTogetherViewModel,
 * minus the Koin/DataStore bits that this app's manager already owns).
 *
 * The manager outlives the screen — leaving the screen does NOT leave the room, which is the whole
 * point of a feature whose playback keeps running in the service.
 */
@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    private val manager: ListenTogetherManager
) : ViewModel() {

    val connectionState = manager.connectionState
    val roomState = manager.roomState
    val role = manager.role
    val pendingJoinRequests = manager.pendingJoinRequests
    val pendingSuggestions = manager.pendingSuggestions
    val bufferingUsers = manager.bufferingUsers
    val blockedUsernames = manager.blockedUsernames

    val isInRoom: Boolean get() = manager.isInRoom
    val isHost: Boolean get() = manager.isHost

    fun connect() = manager.connect()

    fun disconnect() = manager.disconnect()

    fun createRoom(username: String) = manager.createRoom(username)

    fun joinRoom(roomCode: String, username: String) = manager.joinRoom(roomCode, username)

    fun leaveRoom() = manager.leaveRoom()

    fun approveJoin(userId: String) = manager.approveJoin(userId)

    fun rejectJoin(userId: String, reason: String? = null) = manager.rejectJoin(userId, reason)

    fun kickUser(userId: String, reason: String? = null) = manager.kickUser(userId, reason)

    fun blockUser(username: String) = manager.blockUser(username)

    fun unblockUser(username: String) = manager.unblockUser(username)

    fun transferHost(newHostId: String) = manager.transferHost(newHostId)

    fun suggestTrack(track: TrackInfo) = manager.suggestTrack(track)

    fun approveSuggestion(suggestionId: String) = manager.approveSuggestion(suggestionId)

    fun rejectSuggestion(suggestionId: String, reason: String? = null) = manager.rejectSuggestion(suggestionId, reason)

    fun requestSync() = manager.requestSync()

    fun clearError() = manager.clearError()
}
