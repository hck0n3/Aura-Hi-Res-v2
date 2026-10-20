package iad1tya.echo.music.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.innertube.YouTube
import com.music.innertube.utils.parseCookieString
import dagger.hilt.android.EntryPointAccessors
import iad1tya.echo.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The pure rules of [AccountPush]. Kept free of Android so they can be pinned by a JVM test.
 */
object AccountPushPolicy {
    /**
     * What the user did, in terms of the call that tells their YouTube account. [group] is the namespace
     * of the target id inside the outbox (a song id is not a playlist id), so a later action on the same
     * target REPLACES the earlier one — only the final state is ever sent. [negative] marks the actions
     * a stale copy of the account's own list would silently undo.
     */
    enum class Op(val logKind: String, val group: String, val negative: Boolean) {
        SONG_LIKE("like", "r", false),
        SONG_UNLIKE("unlike", "r", true),
        SONG_DISLIKE("dislike", "r", true),
        LIBRARY_ADD("library_add", "l", false),
        LIBRARY_REMOVE("library_remove", "l", true),
        PLAYLIST_SAVE("playlist_save", "p", false),
        PLAYLIST_UNSAVE("playlist_unsave", "p", true),

        /** Not an HTTP call of its own: "run the artist unsubscribe flush when the network is back". */
        ARTIST_UNSUBSCRIBE_PASS("unsubscribe", "a", true),
    }

    /**
     * How long a CONFIRMED negative action keeps vetoing the account's own list. YouTube's lists are
     * eventually consistent: a sync that starts seconds after a successful unlike can still read the
     * song as liked and would otherwise hand the heart straight back (registry 105-108 / 487 use the
     * same shape of grace for unfollows).
     */
    const val CONFIRMED_GRACE_MS = 10 * 60_000L

    /** An action that still has not landed after this long is abandoned, not retried for ever. */
    const val MAX_AGE_MS = 7L * 24 * 60 * 60_000L

    /** Rejections that are not "the network is down" (4xx, unexpected body) before giving up. */
    const val MAX_HARD_FAILURES = 6

    /** 30 s, 60 s, 2 min ... capped at 6 h. A trickle, never a storm. */
    fun backoffMs(failures: Int): Long {
        val shift = (failures - 1).coerceIn(0, 10)
        return minOf(30_000L shl shift, 6L * 60 * 60_000L)
    }

    /** Should the account's list be ignored for this target right now? */
    fun restoreSuppressed(op: Op, confirmedAt: Long, now: Long): Boolean =
        op.negative && (confirmedAt == 0L || now - confirmedAt < CONFIRMED_GRACE_MS)

    fun shouldGiveUp(hardFailures: Int, createdAt: Long, now: Long): Boolean =
        hardFailures >= MAX_HARD_FAILURES || now - createdAt > MAX_AGE_MS
}

/**
 * Real-time, durable push of the user's own taps to their YouTube Music account: unlike, "No me gusta",
 * remove from library, un-save a playlist/album — and the retry of an artist unsubscribe whose live call
 * did not land.
 *
 * Owner (2026-10-20): *"que de todo lo que yo me desuscriba, se desuscriba en tiempo real de YouTube, al
 * igual que los 'no me gusta'"*.
 *
 * ### What was wrong
 * Every one of these actions was already sent on the tap, but fire-and-forget: the `Result` was never
 * read, nothing was queued, nothing was retried, and the sync then used the account's (stale) list as
 * the truth — `insertLikedSongsPage`, the library page and the liked-albums pass all re-marked locally
 * what the user had just removed. An unlike made offline therefore came back on the next sync, and the
 * account never learned about it. "No me gusta" never left the phone at all.
 *
 * ### How it works
 *  1. [submit] writes the action to a small file in `noBackupFilesDir` FIRST (a restore onto another
 *     device must not replay it against another account), then sends it at once on an application-scoped
 *     IO scope that no screen can cancel.
 *  2. On success the entry stays as a CONFIRMED tombstone for [AccountPushPolicy.CONFIRMED_GRACE_MS];
 *     on failure it stays pending with exponential backoff and a network-constrained [AccountPushWorker]
 *     retries it. A transport failure (offline) never counts against the entry; a rejection does.
 *  3. The sync reads [suppressesRemoteSongLike] / [suppressesRemoteLibraryAdd] /
 *     [suppressesRemotePlaylistSave] and does not undo a pending or just-confirmed action.
 *
 * Last action on a target wins (a like then an unlike sends only the unlike), each call is idempotent,
 * and it is always one item per user tap — never a bulk operation. Log lines carry no ids or titles.
 */
object AccountPush {
    private const val FILE_NAME = "account_push_outbox.json"
    private const val WORK_NAME = "account_push_flush"
    private const val ARTIST_KEY = "a:pass"
    private const val MAX_ENTRIES = 200
    private const val MAX_PER_FLUSH = 40
    private const val FLUSH_SPACING_MS = 250L
    private const val TOAST_MIN_GAP_MS = 45_000L
    private const val MAX_ARTIST_PASSES = 3
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    private data class Entry(
        val key: String,
        val op: AccountPushPolicy.Op,
        val id: String,
        val seq: Long,
        val createdAt: Long,
        val attempts: Int = 0,
        val hard: Int = 0,
        val nextAttemptAt: Long = 0L,
        val confirmedAt: Long = 0L,
    )

    @Volatile private var appContext: Context? = null
    private val lock = Any()
    private var loaded = false
    private var accountTag: String? = null
    private val entries = LinkedHashMap<String, Entry>()
    private val seq = AtomicLong(System.currentTimeMillis())
    private val keyLocks = ConcurrentHashMap<String, Mutex>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var lastToastAt = 0L

    // ---- lifecycle ----------------------------------------------------------------------------

    /** Just remember the application context (the worker may run in a fresh process). */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /** Called once from `App.onCreate`: attach, and re-arm the retry if something is still pending. */
    fun init(context: Context) {
        attach(context)
        scope.launch { runCatching { resumePending() } }
    }

    private fun resumePending() {
        val now = System.currentTimeMillis()
        val anyPending = synchronized(lock) {
            ensureLoaded()
            val before = entries.size
            entries.values.removeAll { e ->
                (e.confirmedAt != 0L && now - e.confirmedAt >= AccountPushPolicy.CONFIRMED_GRACE_MS) ||
                    (e.confirmedAt == 0L && now - e.createdAt > AccountPushPolicy.MAX_AGE_MS)
            }
            if (entries.size != before) persist()
            entries.values.any { it.confirmedAt == 0L }
        }
        if (anyPending) scheduleWorker()
    }

    // ---- public API ---------------------------------------------------------------------------

    fun isSignedIn(): Boolean = currentAccountTag() != null

    /**
     * Record the user's action and send it NOW. Safe from any thread, never throws, a no-op when
     * signed out (the sync reconciles that case by itself when they sign in).
     */
    fun submit(op: AccountPushPolicy.Op, id: String) {
        if (appContext == null || id.isBlank()) return
        // Local files and imported placeholders have no YouTube video behind them: sending their id would
        // only be rejected (and retried). A real video id is always 11 URL-safe characters.
        if ((op.group == "r" || op.group == "l") && !VIDEO_ID.matches(id)) {
            Timber.i("SYNC_PUSH kind=%s status=skipped_not_remote", op.logKind)
            return
        }
        if (!isSignedIn()) {
            Timber.i("SYNC_PUSH kind=%s status=skipped_signed_out", op.logKind)
            return
        }
        val key = keyOf(op, id)
        val now = System.currentTimeMillis()
        synchronized(lock) {
            ensureLoaded()
            adoptAccount()
            entries[key] = Entry(key = key, op = op, id = id, seq = seq.incrementAndGet(), createdAt = now)
            trim()
            persist()
        }
        scope.launch { runCatching { drive(key, force = true) } }
    }

    /**
     * The live artist unsubscribe finished (see `ArtistEntity.toggleLike`). On failure the DB marker
     * `unfollowedByUserAt` is already the durable instruction; what was missing was something that
     * carries it out soon, so this arms a network-constrained retry of the uploader's pending-unsubscribe
     * flush instead of waiting for the next explicit sync.
     */
    fun onLiveArtistUnsubscribe(confirmed: Boolean) {
        val op = AccountPushPolicy.Op.ARTIST_UNSUBSCRIBE_PASS
        if (confirmed) {
            Timber.i("SYNC_PUSH kind=%s status=ok", op.logKind)
            return
        }
        if (appContext == null) return
        if (!isSignedIn()) {
            Timber.i("SYNC_PUSH kind=%s status=skipped_signed_out", op.logKind)
            return
        }
        synchronized(lock) {
            ensureLoaded()
            adoptAccount()
            entries[ARTIST_KEY] = Entry(
                key = ARTIST_KEY, op = op, id = "pass",
                seq = seq.incrementAndGet(), createdAt = System.currentTimeMillis(),
            )
            persist()
        }
        Timber.i("SYNC_PUSH kind=%s status=queued", op.logKind)
        scheduleWorker()
        toastFailure(gaveUp = false)
    }

    /** The live follow finished. Logged only: a follow that did not land is the uploader's pending SUBSCRIBE. */
    fun onLiveArtistSubscribe(confirmed: Boolean) {
        Timber.i("SYNC_PUSH kind=subscribe status=%s", if (confirmed) "ok" else "failed")
    }

    /** Is a (pending, or just confirmed) action on this song vetoing a "liked" reading from the account? */
    fun suppressesRemoteSongLike(songId: String): Boolean = suppressed("r", songId)

    fun suppressesRemoteLibraryAdd(songId: String): Boolean = suppressed("l", songId)

    fun suppressesRemotePlaylistSave(playlistId: String?): Boolean =
        !playlistId.isNullOrBlank() && suppressed("p", playlistId)

    /** The rating action currently on record for this song (pending or within the grace window). */
    fun currentSongRatingOp(songId: String): AccountPushPolicy.Op? {
        val now = System.currentTimeMillis()
        return synchronized(lock) {
            ensureLoaded()
            val e = entries["r:$songId"] ?: return@synchronized null
            if (e.confirmedAt == 0L || now - e.confirmedAt < AccountPushPolicy.CONFIRMED_GRACE_MS) e.op else null
        }
    }

    /**
     * Send everything that is due. [force] ignores the per-entry backoff (the worker is already spaced
     * by WorkManager); other callers pass false so a burst of triggers cannot become a request burst.
     * Returns true when something is still pending afterwards.
     */
    suspend fun flush(force: Boolean): Boolean {
        val keys = synchronized(lock) {
            ensureLoaded()
            entries.values
                .filter { it.confirmedAt == 0L && it.op != AccountPushPolicy.Op.ARTIST_UNSUBSCRIBE_PASS }
                .sortedBy { it.createdAt }
                .take(MAX_PER_FLUSH)
                .map { it.key }
        }
        for (k in keys) {
            drive(k, force)
            delay(FLUSH_SPACING_MS)
        }
        return hasPending()
    }

    fun hasPending(): Boolean = synchronized(lock) {
        ensureLoaded()
        entries.values.any { it.confirmedAt == 0L }
    }

    fun hasPendingArtistPass(): Boolean = synchronized(lock) {
        ensureLoaded()
        entries.containsKey(ARTIST_KEY)
    }

    /** Worker side of [onLiveArtistUnsubscribe]: run the uploader's flush and retire the request. */
    suspend fun runArtistPass(context: Context) {
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            YtmSyncWorker.YtmSyncEntryPoint::class.java,
        )
        try {
            ep.libraryUploadSync().runUploadPass()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("SYNC_PUSH kind=unsubscribe pass failed: %s", privacySafeSummary(e))
        }
        val left = try {
            ep.database().countPendingUnsubscribes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            1
        }
        var status = "ok"
        synchronized(lock) {
            val cur = entries[ARTIST_KEY] ?: return
            if (left == 0) {
                entries.remove(ARTIST_KEY)
            } else if (cur.attempts + 1 >= MAX_ARTIST_PASSES) {
                entries.remove(ARTIST_KEY)
                status = "failed"
            } else {
                entries[ARTIST_KEY] = cur.copy(attempts = cur.attempts + 1)
                status = "queued"
            }
            persist()
        }
        Timber.i("SYNC_PUSH kind=unsubscribe status=%s", status)
        if (status == "failed") toastFailure(gaveUp = true)
    }

    // ---- the sender ---------------------------------------------------------------------------

    private fun keyOf(op: AccountPushPolicy.Op, id: String) = op.group + ":" + id

    private fun suppressed(group: String, id: String): Boolean {
        if (id.isBlank()) return false
        val now = System.currentTimeMillis()
        return synchronized(lock) {
            ensureLoaded()
            val e = entries["$group:$id"] ?: return@synchronized false
            AccountPushPolicy.restoreSuppressed(e.op, e.confirmedAt, now)
        }
    }

    /**
     * Send the CURRENT entry for [key] until it is confirmed, superseded-and-confirmed, or failed. One
     * request per target at a time; an older call that finds a newer action already sent simply returns.
     */
    private suspend fun drive(key: String, force: Boolean) {
        val mutex = keyLocks.getOrPut(key) { Mutex() }
        mutex.withLock {
            while (true) {
                val e = synchronized(lock) { ensureLoaded(); entries[key] } ?: return
                if (e.confirmedAt != 0L) return
                if (e.op == AccountPushPolicy.Op.ARTIST_UNSUBSCRIBE_PASS) return
                if (!force && e.nextAttemptAt > System.currentTimeMillis()) return
                if (!isSignedIn()) {
                    Timber.i("SYNC_PUSH kind=%s status=skipped_signed_out", e.op.logKind)
                    return
                }
                val outcome = send(e)
                val failure = outcome.exceptionOrNull()
                if (failure is CancellationException) throw failure
                val now = System.currentTimeMillis()
                if (failure == null) {
                    val superseded = synchronized(lock) {
                        val cur = entries[key]
                        if (cur != null && cur.seq == e.seq) {
                            entries[key] = cur.copy(confirmedAt = now, attempts = 0, hard = 0, nextAttemptAt = 0L)
                            persist()
                            false
                        } else {
                            true
                        }
                    }
                    Timber.i("SYNC_PUSH kind=%s status=ok", e.op.logKind)
                    if (!superseded) return
                    continue
                }
                // A transport failure (offline, DNS, timeout) is the network's fault, not the entry's.
                val transport = failure is IOException
                var gaveUp = false
                var firstFailure = false
                var superseded = false
                synchronized(lock) {
                    val cur = entries[key]
                    if (cur == null || cur.seq != e.seq) {
                        superseded = true
                        return@synchronized
                    }
                    val attempts = cur.attempts + 1
                    val hard = if (transport) cur.hard else cur.hard + 1
                    if (AccountPushPolicy.shouldGiveUp(hard, cur.createdAt, now)) {
                        entries.remove(key)
                        gaveUp = true
                    } else {
                        entries[key] = cur.copy(
                            attempts = attempts,
                            hard = hard,
                            nextAttemptAt = now + AccountPushPolicy.backoffMs(attempts),
                        )
                        firstFailure = cur.attempts == 0
                    }
                    persist()
                }
                if (superseded) continue
                if (gaveUp) {
                    Timber.i("SYNC_PUSH kind=%s status=failed", e.op.logKind)
                    toastFailure(gaveUp = true)
                } else {
                    Timber.i(
                        "SYNC_PUSH kind=%s status=queued reason=%s",
                        e.op.logKind, if (transport) "network" else "rejected",
                    )
                    scheduleWorker()
                    if (firstFailure) toastFailure(gaveUp = false)
                }
                return
            }
        }
    }

    private suspend fun send(e: Entry): Result<Unit> = when (e.op) {
        AccountPushPolicy.Op.SONG_LIKE -> YouTube.likeVideo(e.id, true).map { }
        AccountPushPolicy.Op.SONG_UNLIKE -> YouTube.likeVideo(e.id, false).map { }
        AccountPushPolicy.Op.SONG_DISLIKE -> YouTube.dislikeVideo(e.id).map { }
        AccountPushPolicy.Op.LIBRARY_ADD ->
            YouTube.toggleSongLibrary(e.id, true).mapCatching { check(it) { "not processed" } }
        AccountPushPolicy.Op.LIBRARY_REMOVE ->
            YouTube.toggleSongLibrary(e.id, false).mapCatching { check(it) { "not processed" } }
        AccountPushPolicy.Op.PLAYLIST_SAVE -> YouTube.likePlaylist(e.id, true).map { }
        AccountPushPolicy.Op.PLAYLIST_UNSAVE -> YouTube.likePlaylist(e.id, false).map { }
        AccountPushPolicy.Op.ARTIST_UNSUBSCRIBE_PASS -> Result.success(Unit)
    }

    // ---- scheduling, surfacing ----------------------------------------------------------------

    private fun scheduleWorker() {
        val ctx = appContext ?: return
        runCatching {
            val request = OneTimeWorkRequestBuilder<AccountPushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // KEEP: one retry job at a time. It ends with Result.retry() while anything is pending, so an
            // entry added while it runs is still covered.
            WorkManager.getInstance(ctx).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }.onFailure { Timber.w("SYNC_PUSH could not schedule the retry: %s", privacySafeSummary(it)) }
    }

    /** One short toast, only on failure, at most one per [TOAST_MIN_GAP_MS]. It always says it will retry. */
    private fun toastFailure(gaveUp: Boolean) {
        val ctx = appContext ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastToastAt < TOAST_MIN_GAP_MS) return
        lastToastAt = now
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(
                    ctx,
                    ctx.getString(if (gaveUp) R.string.account_push_gave_up else R.string.account_push_queued),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    // ---- storage ------------------------------------------------------------------------------

    /** Hash prefix of the session's SAPISID: only to tell "same account" from "another one". Never logged. */
    private fun currentAccountTag(): String? {
        val sapisid = runCatching { parseCookieString(YouTube.cookie.orEmpty())["SAPISID"] }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(sapisid.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /** Entries queued for account A are never replayed against account B. Call inside [lock]. */
    private fun adoptAccount() {
        val tag = currentAccountTag() ?: return
        val stored = accountTag
        if (stored != null && stored != tag && entries.isNotEmpty()) {
            Timber.i("SYNC_PUSH dropped %d entries queued for another account", entries.size)
            entries.clear()
        }
        accountTag = tag
    }

    private fun trim() {
        while (entries.size > MAX_ENTRIES) {
            val victim = entries.values.firstOrNull { it.confirmedAt != 0L } ?: entries.values.first()
            entries.remove(victim.key)
        }
    }

    private fun file(): File? = appContext?.let { File(it.noBackupFilesDir, FILE_NAME) }

    /** Call inside [lock]. */
    private fun ensureLoaded() {
        if (loaded) return
        val f = file() ?: return
        loaded = true
        runCatching {
            if (!f.exists()) return@runCatching
            val root = JSONObject(f.readText())
            accountTag = root.optString("account").takeIf { it.isNotBlank() }
            val arr = root.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val op = runCatching { AccountPushPolicy.Op.valueOf(o.getString("op")) }.getOrNull() ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                val key = keyOf(op, id)
                entries[key] = Entry(
                    key = key, op = op, id = id,
                    seq = o.optLong("seq"), createdAt = o.optLong("createdAt"),
                    attempts = o.optInt("attempts"), hard = o.optInt("hard"),
                    nextAttemptAt = o.optLong("nextAttemptAt"), confirmedAt = o.optLong("confirmedAt"),
                )
            }
        }.onFailure { Timber.w("SYNC_PUSH could not read the outbox: %s", privacySafeSummary(it)) }
    }

    /** Call inside [lock]. Write-then-rename so a kill mid-write never leaves half a file. */
    private fun persist() {
        val f = file() ?: return
        runCatching {
            val arr = JSONArray()
            entries.values.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("op", e.op.name).put("id", e.id).put("seq", e.seq)
                        .put("createdAt", e.createdAt).put("attempts", e.attempts).put("hard", e.hard)
                        .put("nextAttemptAt", e.nextAttemptAt).put("confirmedAt", e.confirmedAt),
                )
            }
            val root = JSONObject().put("account", accountTag ?: "").put("entries", arr)
            val tmp = File(f.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }.onFailure { Timber.w("SYNC_PUSH could not write the outbox: %s", privacySafeSummary(it)) }
    }
}

/**
 * The retry of [AccountPush]. Network-constrained and not expedited, so it costs nothing while offline;
 * ends with `Result.retry()` (exponential backoff, WorkManager caps it at 5 h) while anything is pending,
 * and stops by itself the moment the outbox is empty. Not a loop: one pass per WorkManager run.
 */
class AccountPushWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            AccountPush.attach(applicationContext)
            if (!AccountPush.isSignedIn()) return@withContext Result.success()
            AccountPush.flush(force = true)
            if (AccountPush.hasPendingArtistPass()) AccountPush.runArtistPass(applicationContext)
            if (AccountPush.hasPending()) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("SYNC_PUSH worker failed: %s", privacySafeSummary(e))
            Result.retry()
        }
    }
}
