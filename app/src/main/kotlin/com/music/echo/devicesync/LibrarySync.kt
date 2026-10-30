package iad1tya.echo.music.devicesync

import android.content.Context
import androidx.room.InvalidationTracker
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.devicesync.LibrarySyncCore.Reg
import iad1tya.echo.music.dislike.DislikeStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * Row 536 — the library (likes, dislikes, playlists) kept equal across the user's devices THROUGH THE WORKER, not through the
 * YouTube account (owner 2026-10-30: *«las sincronizaciones las quiero a través del Worker»* and *«que cuando esté conectado entre
 * dispositivos la sincronización a la nube de YouTube no intervenga con esto»*).
 *
 * How it works, in one paragraph: every fact is a register with the time of its last change ([LibrarySyncCore]); the newest change
 * wins. The ledger on disk remembers what this device last agreed on. A change HERE shows up as a difference between the database and
 * the ledger → registers pushed to the room (`lib_push`); the room tells the others (`lib_ops`), who pull what changed since the last
 * sequence number they saw (`lib_pull` → `lib_state`) and apply it. When a device was offline, the same two steps run when it
 * connects, so nothing is lost and nothing needs to be «in real time» for it to arrive.
 *
 * While this runs and the room has answered once, YouTube's PULL syncs of likes and playlists stay out of the way
 * ([DeviceSyncHub.libraryOwnedByDevices]); the pushes of the user's own actions to their YouTube account are not touched.
 *
 * Safety: removals that look like a mass deletion are held ([LibrarySyncCore.brakeTrips]) and the user is told; registers arrive
 * in a fixed order (songs, playlists, members, likes, dislikes); the log carries counts only (AGENTS rule 4).
 */
class LibrarySync(
    private val context: Context,
    private val database: MusicDatabase,
    dislikes: DislikeStore,
    private val dislikeFlow: kotlinx.coroutines.flow.Flow<*>,
    private val scope: CoroutineScope,
    private val sendMessage: (JsonObject) -> Boolean,
    private val isEnabled: () -> Boolean,
) {
    private val adapter = LibrarySyncAdapter(database, dislikes)
    private val store = LibraryLedgerStore(File(context.filesDir, "device_sync/library_ledger.tsv"))
    private val prefs = context.getSharedPreferences("device_sync_state", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private var ledgerMap: HashMap<String, Reg>? = null
    private var since = prefs.getLong(PREF_SINCE, 0L)
    private val inFlight = ArrayDeque<List<Reg>>()
    private val sentKeys = HashSet<String>()
    private var kickJob: Job? = null
    private var pullWatchdog: Job? = null
    private var lastDiffAt = 0L

    @Volatile private var connected = false
    @Volatile private var pulledOnce = false

    private val observer = object : InvalidationTracker.Observer("song", "playlist", "playlist_song_map") {
        override fun onInvalidated(tables: Set<String>) {
            kick(fast = false)
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        runCatching { database.invalidationTracker.addObserver(observer) }
        scope.launch {
            // The dislike sets live in a preferences file, not in the database: watch them separately.
            dislikeFlow.drop(1).debounce(1_000L).collect { kick(fast = true) }
        }
    }

    fun stop() {
        runCatching { database.invalidationTracker.removeObserver(observer) }
        connected = false
        pulledOnce = false
        DeviceSyncHub.libraryOwnedByDevices = false
        kickJob?.cancel()
    }

    /**
     * The socket is up and the room said hello: ask for what changed while this device was away. [serverLib] is the library protocol
     * version the deployed Worker announced in `hello_ok` (0 when it announced none = an older deployment that ignores `lib_pull`).
     */
    fun onConnected(serverLib: Int) {
        connected = true
        pulledOnce = false
        inFlight.clear()
        sentKeys.clear()
        if (!isEnabled()) {
            DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Off
            return
        }
        if (serverLib < MIN_SERVER_LIB) {
            // Without this the sync would sit silent forever and look like «never works»: say what is wrong.
            DeviceSyncHub.libraryOwnedByDevices = false
            DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.ServerOutdated
            Timber.tag(TAG).w("LIB_SYNC server is older than this app (lib=%d)", serverLib)
            DeviceSyncHub.notice("El servidor de sincronización está desactualizado: vuelve a desplegarlo para sincronizar la biblioteca.")
            return
        }
        requestPull()
    }

    fun onDisconnected() {
        connected = false
        pulledOnce = false
        pullWatchdog?.cancel()
        DeviceSyncHub.libraryOwnedByDevices = false
        if (isEnabled()) DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Waiting
    }

    /** «Sincronizar ahora»: pull again, and the pull answer pushes whatever is different here. */
    fun resync() {
        if (!connected || !isEnabled()) return
        requestPull()
    }

    /** Called by the user's own actions (a like, a playlist edit): look for differences very soon. */
    fun kick(fast: Boolean) {
        if (!connected || !isEnabled()) return
        if (!fast && kickJob?.isActive == true) return // an observer storm collapses into one pass
        if (fast) kickJob?.cancel()
        kickJob = scope.launch {
            val wait = if (fast) FAST_DELAY_MS else maxOf(SLOW_DELAY_MS, MIN_INTERVAL_MS - (System.currentTimeMillis() - lastDiffAt))
            delay(wait)
            mutex.withLock {
                lastDiffAt = System.currentTimeMillis()
                runCatching { pushDiffLocked() }.onFailure { Timber.tag(TAG).w("LIB_SYNC diff failed: %s", it.javaClass.simpleName) }
            }
        }
    }

    /** Handles the library messages of the room. True when [msg] was one of them. */
    suspend fun handle(msg: JsonObject): Boolean {
        when ((msg["t"] as? JsonPrimitive)?.contentOrNull) {
            "lib_state" -> if (isEnabled()) mutex.withLock {
                runCatching { onStateLocked(msg) }.onFailure {
                    Timber.tag(TAG).w("LIB_SYNC state failed: %s", it.javaClass.simpleName)
                    DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Failed(it.javaClass.simpleName)
                }
            }
            "lib_ops" -> if (isEnabled()) requestPull()
            "lib_ack" -> mutex.withLock { onAckLocked() }
            else -> return false
        }
        return true
    }

    private fun requestPull() {
        DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Syncing
        if (!sendMessage(buildJsonObject { put("t", "lib_pull"); put("since", since) })) {
            DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Failed("sin conexión")
            return
        }
        // A server that does not answer is a server that does not understand: do not wait for ever in silence.
        pullWatchdog?.cancel()
        pullWatchdog = scope.launch {
            delay(PULL_TIMEOUT_MS)
            if (DeviceSyncHub.libraryStatus.value == DeviceSyncHub.LibraryStatus.Syncing && !pulledOnce) {
                DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Failed("el servidor no responde")
                Timber.tag(TAG).w("LIB_SYNC no answer to lib_pull")
            }
        }
    }

    // ── incoming ───────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private suspend fun onStateLocked(msg: JsonObject) {
        val ledger = ledger()
        if ((msg["reset"] as? JsonPrimitive)?.booleanOrNull == true) {
            // The room was emptied or recreated: it has nothing of ours any more. Everything we know is pending again.
            since = 0L
            ledger.replaceAll { _, r -> r.copy(pending = true) }
            store.rewrite(ledger)
            sentKeys.clear()
            inFlight.clear()
        }
        val incoming = ((msg["items"] as? JsonArray).orEmpty()).mapNotNull { item ->
            val a = item as? JsonArray ?: return@mapNotNull null
            if (a.size < 3) return@mapNotNull null
            val key = (a[0] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val ts = (a[1] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            val value = (a[2] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            Reg(key, ts, value)
        }
        var accepted = LibrarySyncCore.acceptedFrom(ledger, incoming)
        var plan = LibrarySyncCore.plan(accepted)
        val known = ledger.values.count { it.value != LibrarySyncCore.OFF && !it.key.startsWith("sg:") }
        if (LibrarySyncCore.brakeTrips(plan.removalCount, known)) {
            // A pile of removals at once is not a person tidying up. Keep every addition, hold every removal, and say so.
            val held = accepted.filter { it.value == LibrarySyncCore.OFF && !it.key.startsWith("sg:") }
            accepted = accepted - held.toSet()
            plan = LibrarySyncCore.plan(accepted)
            Timber.tag(TAG).w("LIB_SYNC held removals=%d known=%d", held.size, known)
            DeviceSyncHub.notice("Otro dispositivo quitó ${held.size} elementos de golpe; no los quité aquí por seguridad.")
        }
        val result = adapter.apply(plan)
        val stored = accepted.map { it.copy(pending = false) }
        stored.forEach { ledger[it.key] = it }
        store.append(stored)
        store.compactIfBloated(ledger)
        since = maxOf(since, (msg["seq"] as? JsonPrimitive)?.longOrNull ?: since)
        prefs.edit().putLong(PREF_SINCE, since).apply()
        Timber.tag(TAG).i("LIB_SYNC pulled items=%d applied=%d unresolved=%d", incoming.size, result.applied, result.unresolved)

        if ((msg["more"] as? JsonPrimitive)?.booleanOrNull == true) {
            requestPull()
            return
        }
        // Caught up: from here the room is the source of truth between the devices, and what is different HERE goes out.
        pulledOnce = true
        pullWatchdog?.cancel()
        DeviceSyncHub.libraryOwnedByDevices = connected && isEnabled()
        pushDiffLocked()
        publishStatusLocked()
    }

    private fun publishStatusLocked() {
        val ledger = ledgerMap ?: return
        DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Synced(
            atMs = System.currentTimeMillis(),
            known = ledger.values.count { it.value != LibrarySyncCore.OFF && !it.key.startsWith("sg:") },
            pending = ledger.values.count { it.pending },
        )
    }

    private suspend fun onAckLocked() {
        val batch = inFlight.removeFirstOrNull() ?: return
        val ledger = ledger()
        val settled = ArrayList<Reg>()
        for (sent in batch) {
            sentKeys.remove(sent.key)
            // Only if it was not changed again while the room was answering.
            val now = ledger[sent.key]
            if (now != null && now.ts == sent.ts && now.value == sent.value && now.pending) {
                val done = now.copy(pending = false)
                ledger[sent.key] = done
                settled += done
            }
        }
        store.append(settled)
        if (pulledOnce) publishStatusLocked()
    }

    // ── outgoing ───────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private suspend fun pushDiffLocked() {
        if (!connected || !isEnabled() || !pulledOnce) return
        val ledger = ledger()
        val current = adapter.readLocal { key -> ledger.containsKey(key) }
        val fresh = LibrarySyncCore.localOps(current, ledger, DeviceSyncHub.serverNow())
        fresh.forEach { ledger[it.key] = it }
        store.append(fresh)

        // Everything not yet acknowledged, parents before children, in batches the room accepts.
        val pending = ledger.values.filter { it.pending && it.key !in sentKeys }.sortedBy { rank(it.key) }
        if (pending.isEmpty()) return
        var sentCount = 0
        for (batch in pending.chunked(BATCH)) {
            val message = buildJsonObject {
                put("t", "lib_push")
                put("ops", buildJsonArray {
                    batch.forEach { r ->
                        add(buildJsonArray {
                            add(JsonPrimitive(r.key))
                            add(JsonPrimitive(r.ts))
                            add(JsonPrimitive(r.value))
                        })
                    }
                })
            }
            if (!sendMessage(message)) break
            inFlight.addLast(batch)
            batch.forEach { sentKeys += it.key }
            sentCount += batch.size
        }
        Timber.tag(TAG).i("LIB_SYNC pushed changes=%d pending=%d", fresh.size, sentCount)
        store.compactIfBloated(ledger)
    }

    private fun rank(key: String) = when (key.substringBefore(':')) {
        "sg" -> 0
        "pl" -> 1
        "pm" -> 2
        "lk" -> 3
        else -> 4
    }

    private suspend fun ledger(): HashMap<String, Reg> =
        ledgerMap ?: store.load().also { ledgerMap = it }

    private companion object {
        const val TAG = "DeviceSync"
        const val PREF_SINCE = "libSince"
        const val BATCH = 200
        const val FAST_DELAY_MS = 800L
        const val SLOW_DELAY_MS = 4_000L
        const val MIN_INTERVAL_MS = 30_000L
        const val PULL_TIMEOUT_MS = 12_000L
        const val MIN_SERVER_LIB = 2
    }
}
