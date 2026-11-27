package iad1tya.echo.music.tvlink

import android.os.Build
import iad1tya.echo.music.listentogether.ListenTogetherClient
import java.security.KeyPair
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber

/**
 * Registry row 907: the two ends of «Iniciar sesión en la TV» over the rooms Worker (docs/worker/rooms, the tvlink routes). The relay
 * only sees [TvLinkCode.relayId] and sealed blobs. Nothing here logs a code, a key, a blob or the session: only HTTP status codes.
 * No retry loop and no polling: the TV keeps ONE socket open while its screen shows the code, and the Worker pushes the session
 * (or «expired», at most 2 minutes later) to it.
 */
object TvLinkRelay {
    private const val TAG = "TvLink"

    private val httpBase: String get() = ListenTogetherClient.AURA_ROOMS_URL.replaceFirst("wss://", "https://")

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val socketHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    /** This device as the other end will read it, e.g. «samsung SM-S938B». A model name, never the user-given device name. */
    fun thisDevice(): String {
        val maker = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        return TvLinkPayload.deviceLabel(if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model")
    }

    enum class Failure { NO_LINK, WRONG_CODE, ALREADY_USED, TOO_BIG, NETWORK }

    sealed interface Outcome<out T> {
        data class Ok<T>(val value: T) : Outcome<T>
        data class Failed(val why: Failure) : Outcome<Nothing>
    }

    private fun failureOf(status: Int): Failure = when (status) {
        404 -> Failure.NO_LINK
        409 -> Failure.ALREADY_USED
        413 -> Failure.TOO_BIG
        else -> Failure.NETWORK
    }

    /** Phone: which device is asking for this code. A code typed wrong opens nothing ([Failure.WRONG_CODE] or [Failure.NO_LINK]). */
    suspend fun helloOf(normalized: String): Outcome<TvLinkHello> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$httpBase/tvlink/hello?id=${TvLinkCode.relayId(normalized)}").get().build()
        runCatching {
            http.newCall(request).execute().use { res ->
                if (!res.isSuccessful) {
                    Timber.tag(TAG).w("hello: http %d", res.code)
                    return@use Outcome.Failed(failureOf(res.code))
                }
                val blob = JSONObject(res.body.string()).optString("hello")
                val hello = TvLinkSeal.open(normalized, TvLinkSeal.HELLO, blob)?.let(TvLinkPayload::parseHello)
                if (hello == null) Outcome.Failed(Failure.WRONG_CODE) else Outcome.Ok(hello)
            }
        }.getOrElse {
            Timber.tag(TAG).w("hello: %s", it.javaClass.simpleName)
            Outcome.Failed(Failure.NETWORK)
        }
    }

    /**
     * Phone: seals [session] with the code and to the TV's key from its hello ([TvLinkSessionSeal]) and hands it to the relay.
     * Ok(true) = the TV had it at once; Ok(false) = on its reconnect.
     */
    suspend fun send(normalized: String, tvPublicKey: ByteArray, session: TvLinkSession): Outcome<Boolean> = withContext(Dispatchers.IO) {
        val tvKey = TvLinkKeys.publicFrom(tvPublicKey) ?: return@withContext Outcome.Failed(Failure.WRONG_CODE)
        val sealed = TvLinkSessionSeal.seal(normalized, tvKey, TvLinkPayload.session(session))
        val body = JSONObject().put("session", sealed).toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$httpBase/tvlink/send?id=${TvLinkCode.relayId(normalized)}").post(body).build()
        runCatching {
            http.newCall(request).execute().use { res ->
                if (!res.isSuccessful) {
                    Timber.tag(TAG).w("send: http %d", res.code)
                    return@use Outcome.Failed(failureOf(res.code))
                }
                Outcome.Ok(JSONObject(res.body.string()).optBoolean("delivered"))
            }
        }.getOrElse {
            Timber.tag(TAG).w("send: %s", it.javaClass.simpleName)
            Outcome.Failed(Failure.NETWORK)
        }
    }

    /** TV: one socket while the code is on screen. [stop] it when the screen goes away. */
    class Receiver {
        sealed interface State {
            data object Connecting : State
            data class Waiting(val expiresAt: Long) : State
            data class Received(val session: TvLinkSession) : State
            data object Expired : State
            data object AlreadyUsed : State
            /** Something arrived that does not open with this code: never imported. */
            data object Invalid : State
            data object Disconnected : State
        }

        private val _state = MutableStateFlow<State>(State.Connecting)
        val state: StateFlow<State> = _state.asStateFlow()

        @Volatile private var socket: WebSocket? = null

        /** [keys] is the pair made for this code: the same one on every reconnect, or the phone's session would not open. */
        fun start(normalized: String, device: String, keys: KeyPair) {
            stop()
            _state.value = State.Connecting
            val request = runCatching {
                Request.Builder().url("${ListenTogetherClient.AURA_ROOMS_URL}/tvlink/wait?id=${TvLinkCode.relayId(normalized)}").build()
            }.getOrNull() ?: run { _state.value = State.Disconnected; return }
            socket = socketHttp.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val hello = TvLinkSeal.seal(normalized, TvLinkSeal.HELLO, TvLinkPayload.hello(device, keys.public.encoded))
                        webSocket.send(JSONObject().put("type", "hello").put("payload", JSONObject().put("hello", hello)).toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (socket !== webSocket) return
                        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                        val payload = msg.optJSONObject("payload") ?: JSONObject()
                        when (msg.optString("type")) {
                            "waiting" -> _state.value = State.Waiting(payload.optLong("expiresAt"))
                            "session" -> {
                                val session = TvLinkSessionSeal.open(normalized, keys.private, payload.optString("session"))
                                    ?.let(TvLinkPayload::parseSession)
                                // A blob that does not open with this code and this TV's key is not from the user's phone: never imported.
                                _state.value = if (session != null) State.Received(session) else State.Invalid
                            }
                            "expired" -> _state.value = State.Expired
                            "error" -> if (payload.optString("code") == "already_used") _state.value = State.AlreadyUsed
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = ended(webSocket)

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Timber.tag(TAG).w("wait: %s", t.javaClass.simpleName)
                        ended(webSocket)
                    }
                },
            )
        }

        private fun ended(webSocket: WebSocket) {
            if (socket !== webSocket) return
            socket = null
            val s = _state.value
            if (s is State.Connecting || s is State.Waiting) _state.value = State.Disconnected
        }

        /** The code ran out on this side (the Worker says so too, but a dead network would never tell). */
        fun expire() {
            stop()
            if (_state.value !is State.Received) _state.value = State.Expired
        }

        fun stop() {
            val s = socket
            socket = null
            s?.close(1000, "bye")
        }
    }
}
