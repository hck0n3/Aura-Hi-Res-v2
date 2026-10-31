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
    private const val TAGS_TIMEOUT_MS = 1_500
    private const val MAX_CONFIG_BYTES = 16 * 1024
    private const val MAX_DIAG_BYTES = 900 * 1024

    /** What a screen can say about the layer. */
    enum class State { Off, NoAddress, NoKey, NeverChecked, Ok, Failed }

    @Volatile
    var lastState: State = State.NeverChecked
        private set

    /** Kept at app start so callers without a Context (the AI request chain) can still use the shared caches. */
    @Volatile
    private var appContext: Context? = null

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
        appContext = context.applicationContext
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
            // The log rotates: what happened a few minutes ago may already be in the backup file, so send both, oldest first
            // (owner 2026-10-30: the first upload held 9 minutes and missed the library sync that had run before).
            val files = listOf(AppLogger.backupFile(context), AppLogger.logFile(context)).filter { it.exists() && it.length() > 0L }
            if (files.isEmpty()) return@withContext Result.failure(IllegalStateException("empty"))
            val all = ByteArrayOutputStream()
            files.forEach { all.write(it.readBytes()) }
            val bytes = all.toByteArray().let { if (it.size > MAX_DIAG_BYTES) it.copyOfRange(it.size - MAX_DIAG_BYTES, it.size) else it }
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

    /**
     * Function 4: the style tags of one artist from the shared cache of the Worker, or null — no address, switched off, slow
     * (1.5 s), not found, anything — and the caller then asks Last.fm directly, exactly as before. Only the artist NAME travels (the
     * same thing the app already sends to Last.fm); it is never written to the log.
     */
    suspend fun artistTags(context: Context, artist: String): List<Pair<String, Int>>? = withContext(Dispatchers.IO) {
        val s = settings(context)
        if (!s.enabled || s.base.isBlank() || artist.isBlank()) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("${s.base}/v1/tags?artist=" + java.net.URLEncoder.encode(artist.take(80), "UTF-8")).openConnection() as HttpURLConnection).apply {
                connectTimeout = TAGS_TIMEOUT_MS
                readTimeout = TAGS_TIMEOUT_MS
            }
            if (conn.responseCode != 200) return@withContext null
            readCapped(conn, MAX_CONFIG_BYTES)?.let(EdgeTags::parse)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    // ── Function 5: encrypted cloud backup ─────────────────────────────────────────────────────────────────────────────────────

    /** What the user's account of copies holds: how many, and when the newest was made (epoch ms). */
    data class BackupInfo(val copies: Int, val newestAtMs: Long, val newestSize: Long)

    private const val BACKUP_MAX_BYTES = 24 * 1024 * 1024
    private const val BACKUP_TIMEOUT_MS = 60_000

    /** Encrypts [plain] with [secret] (see [BackupCrypto]) and stores it as the newest copy. Failure reasons: no-address, too-big, status-N. */
    suspend fun backupUpload(context: Context, secret: String, plain: java.io.File): Result<Int> = withContext(Dispatchers.IO) {
        val s = settings(context)
        if (!s.enabled || s.base.isBlank()) return@withContext Result.failure(IllegalStateException("no-address"))
        var conn: HttpURLConnection? = null
        try {
            if (plain.length() > BACKUP_MAX_BYTES - 1024) return@withContext Result.failure(IllegalStateException("too-big"))
            val bytes = BackupCrypto.encrypt(secret, plain.readBytes())
            conn = (URL("${s.base}/v1/backup/${BackupCrypto.backupId(secret)}").openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = TIMEOUT_MS
                readTimeout = BACKUP_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            Timber.tag(TAG).i("EDGE backup upload status=%d bytes=%d", code, bytes.size)
            if (code == 200) Result.success(bytes.size) else Result.failure(IllegalStateException("status-$code"))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /** The newest copy, decrypted into [out]. Failure reasons: no-address, no-backup, wrong-secret (or a damaged copy), status-N. */
    suspend fun backupDownload(context: Context, secret: String, out: java.io.File): Result<Unit> = withContext(Dispatchers.IO) {
        val s = settings(context)
        if (!s.enabled || s.base.isBlank()) return@withContext Result.failure(IllegalStateException("no-address"))
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("${s.base}/v1/backup/${BackupCrypto.backupId(secret)}").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = BACKUP_TIMEOUT_MS
            }
            val code = conn.responseCode
            if (code == 404) return@withContext Result.failure(IllegalStateException("no-backup"))
            if (code != 200) return@withContext Result.failure(IllegalStateException("status-$code"))
            val all = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    all.write(buf, 0, n)
                    if (all.size() > BACKUP_MAX_BYTES + 1024) return@withContext Result.failure(IllegalStateException("too-big"))
                }
            }
            val plain = BackupCrypto.decrypt(secret, all.toByteArray()) ?: return@withContext Result.failure(IllegalStateException("wrong-secret"))
            out.writeBytes(plain)
            Timber.tag(TAG).i("EDGE backup download ok bytes=%d", plain.size)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun backupInfo(context: Context, secret: String): BackupInfo? = withContext(Dispatchers.IO) {
        val s = settings(context)
        if (!s.enabled || s.base.isBlank()) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("${s.base}/v1/backup/${BackupCrypto.backupId(secret)}/list").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            if (conn.responseCode != 200) return@withContext null
            val copies = Json.parseToJsonElement(readCapped(conn, 4096) ?: return@withContext null).jsonObject["copies"] as? kotlinx.serialization.json.JsonArray
            val first = copies?.firstOrNull() as? kotlinx.serialization.json.JsonObject ?: return@withContext BackupInfo(0, 0L, 0L)
            fun num(k: String) = (first[k] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
            BackupInfo(copies.size, num("at"), num("size"))
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Function 3: the shared motion cover of an album (key = [iad1tya.echo.music.ui.player.CanvasAlbumMemo.key]), or null → the provider chain as before. */
    suspend fun canvasLookup(context: Context, key: String): iad1tya.echo.music.canvas.CanvasArtwork? = withContext(Dispatchers.IO) {
        val s = settings(context)
        if (!s.enabled || s.base.isBlank() || key.isBlank()) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("${s.base}/v1/canvas?k=" + java.net.URLEncoder.encode(key, "UTF-8")).openConnection() as HttpURLConnection).apply {
                connectTimeout = TAGS_TIMEOUT_MS
                readTimeout = TAGS_TIMEOUT_MS
            }
            if (conn.responseCode != 200) return@withContext null
            readCapped(conn, MAX_CONFIG_BYTES)?.let(EdgeCanvas::parse)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Function 3: tell the cache what the provider chain found, so nobody else repeats the search. Best effort, once, never for «none». */
    suspend fun canvasShare(context: Context, key: String, artwork: iad1tya.echo.music.canvas.CanvasArtwork) = withContext(Dispatchers.IO) {
        val s = settings(context)
        if (!s.enabled || s.base.isBlank() || key.isBlank() || artwork.preferredAnimationUrl.isNullOrBlank()) return@withContext
        var conn: HttpURLConnection? = null
        try {
            val bytes = EdgeCanvas.body(key, artwork).toByteArray(Charsets.UTF_8)
            conn = (URL("${s.base}/v1/canvas").openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            Timber.tag(TAG).i("EDGE canvas shared status=%d", conn.responseCode)
        } catch (_: Exception) {
            // Sharing is a courtesy: a failure changes nothing for this user.
        } finally {
            conn?.disconnect()
        }
    }

    /** «Pedir música»: the answer another listener already got for the same request, or null → the AI is asked as before. */
    suspend fun askLookup(key: String): List<iad1tya.echo.music.api.TrackQuery>? = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext null
        val s = settings(context)
        if (!s.enabled || s.base.isBlank() || key.isBlank()) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("${s.base}/v1/ask?k=" + java.net.URLEncoder.encode(key, "UTF-8")).openConnection() as HttpURLConnection).apply {
                connectTimeout = TAGS_TIMEOUT_MS
                readTimeout = TAGS_TIMEOUT_MS
            }
            if (conn.responseCode != 200) return@withContext null
            readCapped(conn, MAX_CONFIG_BYTES)?.let(EdgeAsk::parse)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** «Pedir música»: file what the AI proposed so the next identical request skips it. Best effort; the typed text never travels. */
    suspend fun askShare(key: String, tracks: List<iad1tya.echo.music.api.TrackQuery>) = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext
        val s = settings(context)
        if (!s.enabled || s.base.isBlank() || key.isBlank() || tracks.size < 3) return@withContext
        var conn: HttpURLConnection? = null
        try {
            val bytes = EdgeAsk.body(key, tracks).toByteArray(Charsets.UTF_8)
            conn = (URL("${s.base}/v1/ask").openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            Timber.tag(TAG).i("EDGE ask shared status=%d tracks=%d", conn.responseCode, tracks.size)
        } catch (_: Exception) {
            // Sharing is a courtesy: a failure changes nothing for this user.
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
