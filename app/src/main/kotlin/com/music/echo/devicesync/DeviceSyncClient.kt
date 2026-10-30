package iad1tya.echo.music.devicesync

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The socket to the user's sync room (row 528): connects, says hello, reconnects with a growing pause, and hands every message
 * to [messages]. No battery tricks beyond the standard ping: it is only started while the app is paired and has something to say.
 */
class DeviceSyncClient(private val scope: CoroutineScope) {
    val state = MutableStateFlow<DeviceSyncHub.Connection>(DeviceSyncHub.Connection.Off)
    val messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 128)

    private val http = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var socket: WebSocket? = null
    private var loop: Job? = null

    fun start(url: String, hello: () -> JsonObject) {
        stop()
        loop = scope.launch {
            var attempt = 0
            while (isActive) {
                state.value = DeviceSyncHub.Connection.Connecting
                val closed = CompletableDeferred<Boolean>()
                val request = runCatching { Request.Builder().url(url).build() }.getOrNull()
                if (request == null) {
                    state.value = DeviceSyncHub.Connection.Off
                    return@launch
                }
                socket = http.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            attempt = 0
                            webSocket.send(hello().toString())
                            state.value = DeviceSyncHub.Connection.Connected
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
                            messages.tryEmit(obj)
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            // The room closes a connection with «replaced» when ANOTHER one says hello with the same device id. If it is
                            // not this device's own reconnect, two pieces of hardware share an id (a copied backup): say it, do not loop.
                            if (reason == "replaced" && socket === webSocket) {
                                DeviceSyncHub.notice("Otro dispositivo usa el mismo identificador de sincronización. Reinicia la app en los dos.")
                            }
                            closed.complete(true)
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            closed.complete(false)
                        }
                    },
                )
                closed.await()
                socket = null
                attempt++
                val wait = (2 shl attempt.coerceAtMost(5)).coerceAtMost(60)
                for (left in wait downTo 1) {
                    state.value = DeviceSyncHub.Connection.Retrying(left)
                    delay(1_000)
                }
            }
        }
    }

    fun send(message: JsonObject): Boolean = socket?.send(message.toString()) == true

    fun stop() {
        loop?.cancel()
        loop = null
        socket?.close(1000, "bye")
        socket = null
        state.value = DeviceSyncHub.Connection.Off
    }
}
