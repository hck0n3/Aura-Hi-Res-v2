package iad1tya.echo.music.listentogether

import android.content.Context
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.ListenTogetherRoomCodeKey
import iad1tya.echo.music.constants.ListenTogetherSessionTokenKey
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Persists the room session so a room survives backgrounding and process death.
 *
 * The client replays the stored token as `reconnect` on the next socket, which is the only way a
 * dropped connection can return to the SAME room — the server mints a new user id on every fresh
 * join (fila 144 del registro de regresiones: los móviles del dueño deben reanudar la sala tras
 * segundo plano; la reproducción sigue en el servicio, la sala no puede vivir solo en la pantalla).
 *
 * Upstream SimpMusic keeps the token in memory only (its CLAUDE.md documents the same trade-off);
 * this app keeps the promise the previous implementation already made to its users.
 */
class SessionTokenStore(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    @Volatile
    var cachedToken: String? = null
        private set

    @Volatile
    var cachedRoomCode: String? = null
        private set

    fun save(
        token: String,
        roomCode: String,
    ) {
        if (token.isBlank()) return
        cachedToken = token
        cachedRoomCode = roomCode
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[ListenTogetherSessionTokenKey] = token
                prefs[ListenTogetherRoomCodeKey] = roomCode
            }
        }
    }

    fun clear() {
        cachedToken = null
        cachedRoomCode = null
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs.remove(ListenTogetherSessionTokenKey)
                prefs.remove(ListenTogetherRoomCodeKey)
            }
        }
    }
}
