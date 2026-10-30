package iad1tya.echo.music.edge

import android.content.Context
import iad1tya.echo.music.utils.AppLogger
import iad1tya.echo.music.utils.dataStore
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

/**
 * The app's only door to Aura's edge Worker. Rules (docs/PLAN_WORKER_9_FUNCIONES.md, Phase A): off when there is no address; short
 * time limits; one attempt per call, never a retry loop; the last good config is kept; the log carries status codes and counts only.
 */
object EdgeClient {
    private const val TAG = "Edge"
    private const val PREFS = "aura_edge"
    private const val CHECK_EVERY_MS = 6 * 3600_000L
    private const val TIMEOUT_MS = 5_000
    private const val MAX_CONFIG_BYTES = 16 * 1024
    private const val MAX_DIAG_BYTES = 900 * 1024

    /** What a screen can say about the layer. */
    enum class State { Off, NoAddress, NoKey, NeverChecked, Ok, Failed }

    @Volatile
    var lastState: State = State.NeverChecked
        private set

    private data class Settings(val enabled: Boolean, val base: String, val key: String)

    private suspend fun settings(context: Context): Settings {
        val d = context.dataStore.data.first()
        return Settings(
            enabled = d[EdgeEnabledKey] ?: true,
            base = (d[EdgeUrlKey] ?: "").trim().ifBlank { EDGE_DEFAULT_URL }.trimEnd('/'),
            key = EDGE_PUBLIC_KEY.ifBlank { (d[EdgePublicKeyKey] ?: "").trim() },
        )
    }

    /** At app start: install the last good config at once (no network), then refresh if it is time. */
    suspend fun start(context: Context) = withContext(Dispatchers.IO) {
        val s = settings(context)
        if (!s.enabled) {
            RemoteConfigs.install(null)
            lastState = State.Off
            return@withContext
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("envelope", null)?.let { stored ->
            if (s.key.isNotBlank()) RemoteConfigs.install(RemoteConfigs.accept(stored, s.key))
        }
        if (System.currentTimeMillis() - prefs.getLong("checkedAt", 0L) >= CHECK_EVERY_MS) refresh(context)
    }

    /** One conditional GET of `/v1/config`. */
    suspend fun refresh(context: Context): State = withContext(Dispatchers.IO) {
        val s = settings(context)
        val state = when {
            !s.enabled -> State.Off
            s.base.isBlank() -> State.NoAddress
            s.key.isBlank() -> State.NoKey
            else -> fetchConfig(context, s)
        }
        lastState = state
        state
    }

    private fun fetchConfig(context: Context, s: Settings): State {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("${s.base}/v1/config").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                prefs.getString("etag", null)?.let { setRequestProperty("If-None-Match", it) }
            }
            val code = conn.responseCode
            prefs.edit().putLong("checkedAt", System.currentTimeMillis()).apply()
            when (code) {
                304 -> State.Ok
                200 -> {
                    val body = readCapped(conn, MAX_CONFIG_BYTES)
                    val config = body?.let { RemoteConfigs.accept(it, s.key) }
                    if (config == null) {
                        Timber.tag(TAG).w("EDGE config rejected (size or signature)")
                        State.Failed
                    } else {
                        RemoteConfigs.install(config)
                        prefs.edit().putString("envelope", body).putString("etag", conn.getHeaderField("ETag")).apply()
                        Timber.tag(TAG).i("EDGE config ok flags=%d notice=%b", config.flags.size, config.notice != null)
                        State.Ok
                    }
                }
                else -> {
                    Timber.tag(TAG).i("EDGE config status=%d", code)
                    if (code == 404) State.Ok else State.Failed // 404 = nothing published yet: every default stays
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).i("EDGE config failed: %s", e.javaClass.simpleName)
            State.Failed
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Function 8: the user pressed «Enviar registro». Uploads the same `app.log` they would otherwise share by hand (already
     * filtered by AppLogger, rule 4) and returns the short case code to hand over. Never called on its own.
     */
    suspend fun sendDiagnostics(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val s = settings(context)
        if (!s.enabled || s.base.isBlank()) return@withContext Result.failure(IllegalStateException("no-address"))
        var conn: HttpURLConnection? = null
        try {
            val file = AppLogger.logFile(context)
            if (!file.exists() || file.length() == 0L) return@withContext Result.failure(IllegalStateException("empty"))
            val bytes = file.inputStream().use { input ->
                val skip = (file.length() - MAX_DIAG_BYTES).coerceAtLeast(0L)
                input.skip(skip)
                input.readBytes()
            }
            conn = (URL("${s.base}/v1/diag").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            if (code != 200) return@withContext Result.failure(IllegalStateException("status-$code"))
            val body = readCapped(conn, 2048) ?: return@withContext Result.failure(IllegalStateException("bad-answer"))
            val id = (Json.parseToJsonElement(body).jsonObject["code"] as? JsonPrimitive)?.contentOrNull
            Timber.tag(TAG).i("EDGE diag sent bytes=%d", bytes.size)
            if (id.isNullOrBlank()) Result.failure(IllegalStateException("bad-answer")) else Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private fun readCapped(conn: HttpURLConnection, max: Int): String? {
        val out = ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(2048)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > max) return null
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
