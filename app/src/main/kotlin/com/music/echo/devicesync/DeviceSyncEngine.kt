package iad1tya.echo.music.devicesync

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import iad1tya.echo.music.db.entities.EnhancedShuffleContextEntity
import iad1tya.echo.music.db.entities.EnhancedShufflePlayedEntity
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playback.MusicService
import iad1tya.echo.music.playback.queues.ListQueue
import iad1tya.echo.music.utils.dataStore
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber

/**
 * Row 528 — the user's devices, in step. Lives inside the playback service (it needs the player and the database) and only does
 * anything while the app is PAIRED: by the license (purchase email typed at activation — nothing to configure) or by a code typed in Ajustes ▸ Sincronizar dispositivos.
 *
 *  · Shuffle progress: the songs already played in each shuffled list are reported to the room and the other devices' reports are
 *    merged into the local `enhanced_shuffle_played` memory ([ShuffleMerge]).
 *  · Hand-over (like Spotify Connect): the device that plays publishes what it plays; another one can take it over from the same
 *    second, send its own playback away, or press play / pause / next / previous on it.
 *  · A like or a list change here makes the others refresh at once.
 *
 * What goes to the room: song ids, titles, artists and cover URLs of the queue window, the position, device names. Nothing about the
 * account. The log carries counts and kinds only (AGENTS.md rule 4).
 */
class DeviceSyncEngine(private val service: MusicService) : DeviceSyncHub.Controller {
    private val appContext: Context = service.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = DeviceSyncClient(scope)
    private val statePrefs = appContext.getSharedPreferences("device_sync_state", Context.MODE_PRIVATE)

    @Volatile private var deviceId = ""
    @Volatile private var deviceName = ""
    @Volatile private var serverOffsetMs = 0L
    @Volatile private var shuffleEnabled = true
    @Volatile private var handoffEnabled = true
    @Volatile private var everPlayed = false
    private var lastSignature = ""
    private var lastPublishedAt = 0L
    private var lastPublishedPosition = 0L
    private var lastPublishedPlaying = false
    private var pendingTake: Job? = null
    private var libraryPing: Job? = null
    private val pushedEpoch = HashMap<String, Int>()
    @Volatile private var lastPushedAt = statePrefs.getLong("lastPushedAt", 0L)

    fun start() {
        DeviceSyncHub.controller = this
        loadPushedEpochs()
        scope.launch {
            client.state.collect { DeviceSyncHub.connection.value = it }
        }
        scope.launch { client.messages.collect { runCatching { handle(it) }.onFailure { e -> Timber.tag(TAG).w("handle failed: %s", e.javaClass.simpleName) } } }
        scope.launch { configLoop() }
        scope.launch { tickLoop() }
    }

    fun stop() {
        if (DeviceSyncHub.controller === this) DeviceSyncHub.controller = null
        client.stop()
        DeviceSyncHub.devices.value = emptyList()
        DeviceSyncHub.remoteNow.value = null
        scope.cancel()
    }

    // ── configuration ──────────────────────────────────────────────────────────────────────────────

    private suspend fun configLoop() {
        kotlinx.coroutines.flow.combine(appContext.dataStore.data, DeviceSyncHub.pairingRefresh) { prefs, _ -> prefs }
            .map {
                val identity = iad1tya.echo.music.license.LicenseManager.purchaseIdentity(appContext)
                listOf(
                    it[DeviceSyncServerKey].orEmpty(),
                    it[DeviceSyncCodeKey].orEmpty(),
                    it[DeviceSyncDeviceNameKey].orEmpty(),
                    (it[DeviceSyncShuffleKey] ?: true).toString(),
                    (it[DeviceSyncHandoffKey] ?: true).toString(),
                    (it[DeviceSyncAutoKey] ?: true).toString(),
                    SyncCode.autoGroupId(identity?.first, identity?.second).orEmpty(),
                )
            }
            .distinctUntilChanged()
            .collectLatest { cfg ->
                val server = cfg[0]; val code = cfg[1]; val name = cfg[2]
                val shuffle = cfg[3]; val handoff = cfg[4]; val auto = cfg[5]; val autoGroup = cfg[6]
                shuffleEnabled = shuffle.toBoolean()
                handoffEnabled = handoff.toBoolean()
                deviceName = name.ifBlank { Build.MODEL.orEmpty().ifBlank { "Aura" } }.take(40)
                client.stop()
                DeviceSyncHub.devices.value = emptyList()
                DeviceSyncHub.remoteNow.value = null
                // A typed code wins; otherwise the license pairs the buyer's devices on its own.
                val normalized = SyncCode.normalize(code)
                val (groupId, by) = when {
                    normalized != null -> SyncCode.groupId(normalized) to DeviceSyncHub.PairedBy.Code
                    auto.toBoolean() && autoGroup.isNotEmpty() -> autoGroup to DeviceSyncHub.PairedBy.License
                    else -> null to DeviceSyncHub.PairedBy.None
                }
                DeviceSyncHub.pairedBy.value = by
                val url = groupId?.let { SyncCode.socketUrl(server.ifBlank { DEVICE_SYNC_DEFAULT_SERVER }, it) }
                if (url == null) return@collectLatest
                deviceId = ensureDeviceId()
                DeviceSyncHub.thisDeviceId.value = deviceId
                client.start(url) { hello() }
                Timber.tag(TAG).i("DEVICE_SYNC paired by=%s, connecting", by.name)
            }
    }

    private suspend fun ensureDeviceId(): String {
        val existing = appContext.dataStore.data.first()[DeviceSyncDeviceIdKey]
        if (!existing.isNullOrBlank()) return existing
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        val fresh = "d" + buildString { repeat(19) { append(alphabet[random.nextInt(alphabet.length)]) } }
        appContext.dataStore.edit { it[DeviceSyncDeviceIdKey] = fresh }
        return fresh
    }

    private fun hello(): JsonObject = buildJsonObject {
        put("t", "hello")
        put("device", buildJsonObject {
            put("id", deviceId)
            put("name", deviceName)
            put("kind", if (appContext.resources.configuration.smallestScreenWidthDp >= 600) "tablet" else "phone")
        })
    }

    // ── incoming ───────────────────────────────────────────────────────────────────────────────────

    private suspend fun handle(msg: JsonObject) {
        when (msg.str("t")) {
            "hello_ok" -> {
                msg.long("serverTime")?.let { serverOffsetMs = it - System.currentTimeMillis(); DeviceSyncHub.serverOffsetMs = serverOffsetMs }
                DeviceSyncHub.devices.value = parseDevices(msg["devices"])
                (msg["now"] as? JsonObject)?.let { setRemoteNow(it) }
                Timber.tag(TAG).i("DEVICE_SYNC connected devices=%d", DeviceSyncHub.devices.value.size)
                onConnected()
            }
            "devices" -> DeviceSyncHub.devices.value = parseDevices(msg["devices"])
            "now" -> (msg["state"] as? JsonObject)?.let { setRemoteNow(it) }
            "shuffle" -> {
                val ctx = msg.str("ctx") ?: return
                applyShuffle(ctx, msg.int("epoch") ?: 0, msg.strings("add"), msg.str("last"))
            }
            "shuffle_full" -> {
                var contexts = 0
                (msg["items"] as? JsonArray)?.forEach { item ->
                    val o = item as? JsonObject ?: return@forEach
                    val ctx = o.str("ctx") ?: return@forEach
                    applyShuffle(ctx, o.int("epoch") ?: 0, o.strings("ids"), o.str("last"))
                    contexts++
                }
                Timber.tag(TAG).i("DEVICE_SYNC shuffle pulled contexts=%d", contexts)
            }
            "cmd" -> runCommand(msg.str("from").orEmpty(), msg.str("cmd").orEmpty(), msg["args"] as? JsonObject ?: JsonObject(emptyMap()))
            "cmd_fail" -> DeviceSyncHub.notice("Ese dispositivo no está conectado ahora.")
            "library" -> {
                Timber.tag(TAG).i("DEVICE_SYNC library ping kind=%s", msg.str("what").orEmpty())
                val what = msg.str("what").orEmpty()
                withContext(Dispatchers.Main) {
                    runCatching {
                        when (what) {
                            "likes" -> service.syncUtils.syncLikedSongs()
                            "playlists" -> service.syncUtils.syncSavedPlaylists()
                            else -> {
                                service.syncUtils.syncLikedSongs()
                                service.syncUtils.syncSavedPlaylists()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setRemoteNow(state: JsonObject) {
        val now = parseNow(state) ?: return
        DeviceSyncHub.remoteNow.value = if (now.deviceId == deviceId) null else now
    }

    private fun parseDevices(el: JsonElement?): List<SyncDevice> =
        (el as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            SyncDevice(id, o.str("name").orEmpty().ifBlank { "Dispositivo" }, o.str("kind").orEmpty())
        }

    private fun parseNow(o: JsonObject): NowState? {
        val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { e ->
            val i = e as? JsonObject ?: return@mapNotNull null
            NowItem(i.str("id") ?: return@mapNotNull null, i.str("t").orEmpty(), i.str("a").orEmpty(), i.str("c").orEmpty())
        }
        if (items.isEmpty()) return null
        return NowState(
            items = items,
            index = (o.int("index") ?: 0).coerceIn(0, items.lastIndex),
            positionMs = o.long("positionMs") ?: 0L,
            playing = o["playing"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
            deviceId = o.str("deviceId").orEmpty(),
            deviceName = o.str("deviceName").orEmpty(),
            atMs = o.long("at") ?: serverNow(),
            durationMs = o.long("durationMs") ?: 0L,
        )
    }

    // ── commands ───────────────────────────────────────────────────────────────────────────────────

    private suspend fun runCommand(from: String, cmd: String, args: JsonObject) {
        Timber.tag(TAG).i("DEVICE_SYNC command %s", cmd)
        if (cmd == "play" || cmd == "pause" || cmd == "next" || cmd == "prev" || cmd == "seek") {
            // The device that sent the order is showing OUR timeline: tell it the new state as soon as the player has settled,
            // not at the next heartbeat (row 534).
            scope.launch {
                delay(RESPOND_AFTER_COMMAND_MS)
                runCatching { publishNow(force = true) }
            }
        }
        when (cmd) {
            "play" -> onMain { service.player.play() }
            "pause" -> onMain { service.player.pause() }
            "next" -> onMain { service.player.seekToNext() }
            "prev" -> onMain { service.player.seekToPrevious() }
            "seek" -> args.long("positionMs")?.let { p -> onMain { service.player.seekTo(p) } }
            "handoff" -> if (handoffEnabled) {
                val to = args.str("to") ?: from
                val state = buildNow()
                if (state != null && to.isNotBlank()) {
                    client.send(buildJsonObject {
                        put("t", "cmd"); put("to", to); put("cmd", "take"); put("args", buildJsonObject { put("state", state) })
                    })
                    onMain { service.player.pause() }
                }
            }
            "take" -> if (handoffEnabled) {
                pendingTake?.cancel()
                (args["state"] as? JsonObject)?.let { parseNow(it) }?.let { play(it) }
            }
        }
    }

    private suspend fun play(state: NowState) {
        val items = state.items.map { item ->
            MediaMetadata(
                id = item.id,
                title = item.title,
                artists = listOf(MediaMetadata.Artist(id = null, name = item.artist)),
                duration = 0,
                thumbnailUrl = item.cover.ifBlank { null },
            ).toMediaItem()
        }
        val position = state.estimatedPositionMs(serverNow()).coerceAtLeast(0L)
        Timber.tag(TAG).i("DEVICE_SYNC taking over items=%d index=%d", items.size, state.index)
        onMain {
            service.playQueue(ListQueue(title = state.deviceName.ifBlank { null }, items = items, startIndex = state.index, position = position), playWhenReady = true)
        }
    }

    override fun control(deviceId: String, command: String, positionMs: Long?) {
        scope.launch {
            val sent = client.send(buildJsonObject {
                put("t", "cmd"); put("to", deviceId); put("cmd", command)
                if (positionMs != null) put("args", buildJsonObject { put("positionMs", positionMs.coerceAtLeast(0L)) })
            })
            if (!sent) {
                DeviceSyncHub.notice("Sin conexión con la sincronización.")
            }
        }
    }

    override fun takeOverFrom(deviceId: String) {
        scope.launch {
            val fallback = DeviceSyncHub.remoteNow.value?.takeIf { it.deviceId == deviceId }
            val sent = client.send(buildJsonObject {
                put("t", "cmd"); put("to", deviceId); put("cmd", "handoff"); put("args", buildJsonObject { put("to", this@DeviceSyncEngine.deviceId) })
            })
            if (!sent) {
                DeviceSyncHub.notice("Sin conexión con la sincronización.")
                return@launch
            }
            pendingTake?.cancel()
            pendingTake = launch {
                // The other device answers in a blink with its exact position; if it never does (asleep, killed), carry on from the
                // last thing it published, moved on by the time that has passed.
                delay(TAKE_FALLBACK_MS)
                if (fallback != null) {
                    play(fallback)
                } else {
                    DeviceSyncHub.notice("No se pudo traer la reproducción de ese dispositivo.")
                }
            }
        }
    }

    override fun sendTo(deviceId: String) {
        scope.launch {
            val state = buildNow()
            if (state == null) {
                DeviceSyncHub.notice("No hay nada sonando para enviar.")
                return@launch
            }
            val sent = client.send(buildJsonObject {
                put("t", "cmd"); put("to", deviceId); put("cmd", "take"); put("args", buildJsonObject { put("state", state) })
            })
            if (sent) onMain { service.player.pause() } else DeviceSyncHub.notice("Sin conexión con la sincronización.")
        }
    }

    override fun libraryChanged(what: String) {
        libraryPing?.cancel()
        libraryPing = scope.launch {
            delay(LIBRARY_DEBOUNCE_MS)
            client.send(buildJsonObject { put("t", "library"); put("what", what) })
        }
    }

    // ── what this device plays ─────────────────────────────────────────────────────────────────────

    private suspend fun onConnected() {
        lastSignature = ""
        publishNow(force = true)
        if (shuffleEnabled) {
            client.send(buildJsonObject { put("t", "shuffle_get") })
            pushShuffle()
        }
    }

    private suspend fun tickLoop() {
        while (scope.isActive) {
            delay(TICK_MS)
            if (DeviceSyncHub.connection.value !is DeviceSyncHub.Connection.Connected) continue
            runCatching {
                publishNow(force = false)
                if (shuffleEnabled) pushShuffle()
            }.onFailure { Timber.tag(TAG).w("tick failed: %s", it.javaClass.simpleName) }
        }
    }

    /** The queue window around the current song, with the position — null when nothing is loaded. */
    private suspend fun buildNow(): JsonObject? = onMain {
        val p = playerOrNull() ?: return@onMain null
        if (p.mediaItemCount == 0) return@onMain null
        val index = p.currentMediaItemIndex.coerceAtLeast(0)
        val start = (index - WINDOW_BEHIND).coerceAtLeast(0)
        val end = (index + WINDOW_AHEAD).coerceAtMost(p.mediaItemCount)
        val items = buildJsonArray {
            for (i in start until end) {
                val mi = p.getMediaItemAt(i)
                add(nowItem(mi))
            }
        }
        buildJsonObject {
            put("items", items)
            put("index", index - start)
            put("positionMs", p.currentPosition)
            p.duration.takeIf { it > 0L }?.let { put("durationMs", it) }
            put("playing", p.playWhenReady && p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED)
            put("at", serverNow())
        }
    }

    /** The service builds its player a moment after it starts; a request in that gap simply has nothing to say yet. */
    private fun playerOrNull(): androidx.media3.exoplayer.ExoPlayer? =
        try {
            service.player
        } catch (e: UninitializedPropertyAccessException) {
            null
        }

    private fun nowItem(mi: MediaItem): JsonObject {
        val meta = mi.metadata
        return buildJsonObject {
            put("id", mi.mediaId)
            put("t", (meta?.title ?: mi.mediaMetadata.title?.toString().orEmpty()).take(120))
            put("a", (meta?.artists?.joinToString { it.name } ?: mi.mediaMetadata.artist?.toString().orEmpty()).take(120))
            put("c", (meta?.thumbnailUrl ?: mi.mediaMetadata.artworkUri?.toString().orEmpty()).take(220))
        }
    }

    /** Publish while this device plays, once more when it stops, and never before it has played anything (a restored queue says nothing). */
    private suspend fun publishNow(force: Boolean) {
        val state = buildNow() ?: return
        val playing = (state["playing"] as? JsonPrimitive)?.booleanOrNull == true
        if (playing) everPlayed = true
        if (!everPlayed && !force) return
        val currentId = ((state["items"] as? JsonArray)?.getOrNull(state.int("index") ?: 0) as? JsonObject)?.str("id").orEmpty()
        val signature = "$currentId|$playing"
        val now = System.currentTimeMillis()
        val position = state.long("positionMs") ?: 0L
        // A seek (here or from another device) changes neither the song nor play/pause, so it would only reach the others at the
        // next heartbeat: compare the position with where it should be by now.
        val changed = signature != lastSignature || NowSync.jumped(lastPublishedPosition, lastPublishedAt, lastPublishedPlaying, position, now)
        val due = playing && now - lastPublishedAt >= HEARTBEAT_MS
        if (!force && !changed && !due) return
        if (!everPlayed) return
        lastSignature = signature
        lastPublishedAt = now
        lastPublishedPosition = position
        lastPublishedPlaying = playing
        client.send(buildJsonObject { put("t", "now"); put("state", state) })
    }

    // ── shuffle progress ───────────────────────────────────────────────────────────────────────────

    private suspend fun applyShuffle(ctx: String, epoch: Int, ids: List<String>, last: String?) {
        if (!shuffleEnabled || ctx.isBlank()) return
        val db = service.database
        val local = db.getEnhancedContext(ctx)
        val plan = ShuffleMerge.plan(local?.cycleCount ?: 0, db.playedSongIdsForContext(ctx).toHashSet(), epoch, ids)
        // The rows written here carry a stamp the outbox has already passed, so they are not echoed back.
        val stamp = maxOf(lastPushedAt, 1L)
        when (plan) {
            ShuffleMerge.Plan.Ignore -> return
            is ShuffleMerge.Plan.Reset -> {
                db.clearEnhancedContext(ctx)
                db.upsertEnhancedContext(
                    EnhancedShuffleContextEntity(ctx, last ?: local?.lastSongId, local?.lastPositionMs ?: 0L, plan.epoch, System.currentTimeMillis()),
                )
                plan.ids.forEach { db.insertEnhancedPlayed(EnhancedShufflePlayedEntity(ctx, it, stamp)) }
                pushedEpoch[ctx] = plan.epoch
            }
            is ShuffleMerge.Plan.Add -> plan.ids.forEach { db.insertEnhancedPlayed(EnhancedShufflePlayedEntity(ctx, it, stamp)) }
        }
        Timber.tag(TAG).i("DEVICE_SYNC shuffle applied %s", plan::class.simpleName.orEmpty())
        savePushedEpochs()
    }

    private suspend fun pushShuffle() {
        val db = service.database
        var since = lastPushedAt
        var sent = 0
        while (true) {
            val rows = db.enhancedPlayedSince(since, PUSH_BATCH)
            val contexts = db.allEnhancedContexts().associateBy { it.contextId }
            val byContext = rows.groupBy { it.contextId }
            val moved = contexts.values.filter { c -> pushedEpoch[c.contextId].let { it == null && c.cycleCount > 0 || it != null && c.cycleCount > it } }
                .map { it.contextId }
            val ids = byContext.keys + moved
            for (ctx in ids) {
                val epoch = contexts[ctx]?.cycleCount ?: 0
                val pushed = pushedEpoch[ctx]
                val all = if (pushed == null || epoch > pushed) db.playedSongIdsForContext(ctx) else emptyList()
                val (outEpoch, outIds) = ShuffleMerge.outgoing(epoch, pushed, all, byContext[ctx].orEmpty().map { it.songId }) ?: continue
                val chunks = if (outIds.isEmpty()) listOf(emptyList()) else outIds.chunked(CHUNK_IDS)
                for (chunk in chunks) {
                    val ok = client.send(buildJsonObject {
                        put("t", "shuffle"); put("ctx", ctx); put("epoch", outEpoch)
                        put("add", JsonArray(chunk.map { JsonPrimitive(it) }))
                        contexts[ctx]?.lastSongId?.let { put("last", it) }
                    })
                    if (!ok) return
                    sent += chunk.size
                }
                pushedEpoch[ctx] = outEpoch
            }
            val newest = rows.maxOfOrNull { it.playedAt } ?: break
            since = maxOf(since, newest)
            lastPushedAt = since
            if (rows.size < PUSH_BATCH) break
        }
        if (sent > 0) Timber.tag(TAG).i("DEVICE_SYNC shuffle pushed ids=%d", sent)
        statePrefs.edit().putLong("lastPushedAt", lastPushedAt).apply()
        savePushedEpochs()
    }

    private fun loadPushedEpochs() {
        statePrefs.getString("epochs", null)?.split('\n')?.forEach { line ->
            val i = line.lastIndexOf('\t')
            if (i > 0) line.substring(i + 1).toIntOrNull()?.let { pushedEpoch[line.substring(0, i)] = it }
        }
    }

    private fun savePushedEpochs() {
        statePrefs.edit().putString("epochs", pushedEpoch.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }

    // ── small helpers ──────────────────────────────────────────────────────────────────────────────

    private fun serverNow() = System.currentTimeMillis() + serverOffsetMs

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private companion object {
        const val TAG = "DeviceSync"
        const val TICK_MS = 4_000L
        const val HEARTBEAT_MS = 20_000L
        const val TAKE_FALLBACK_MS = 2_500L
        const val RESPOND_AFTER_COMMAND_MS = 600L
        const val LIBRARY_DEBOUNCE_MS = 3_000L
        const val WINDOW_BEHIND = 5
        const val WINDOW_AHEAD = 60
        const val PUSH_BATCH = 2_000
        const val CHUNK_IDS = 1_000
    }
}
