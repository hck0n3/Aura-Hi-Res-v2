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
    @Volatile private var libraryEnabled = true
    private var library: LibrarySync? = null
    @Volatile private var helloLib = 0
    private var remoteTick: Job? = null
    private var playerListenerAttached = false
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
        val dislikeStore = iad1tya.echo.music.dislike.DislikeStoreEntryPoint.get(appContext)
        library = LibrarySyncAndroid.create(appContext, service.database, dislikeStore, dislikeStore.disliked, scope, { client.send(it) }, { libraryEnabled })
            .also { it.start() }
        scope.launch {
            client.state.collect {
                DeviceSyncHub.connection.value = it
                if (it !is DeviceSyncHub.Connection.Connected) {
                    library?.onDisconnected()
                    RemotePlayback.update(null, 0L)
                }
            }
        }
        scope.launch { client.messages.collect { runCatching { handle(it) }.onFailure { e -> Timber.tag(TAG).w("handle failed: %s", e.javaClass.simpleName) } } }
        scope.launch { configLoop() }
        scope.launch { tickLoop() }
    }

    fun stop() {
        if (DeviceSyncHub.controller === this) DeviceSyncHub.controller = null
        library?.stop()
        library = null
        RemotePlayback.update(null, 0L)
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
                    (it[DeviceSyncLibraryKey] ?: true).toString(),
                )
            }
            .distinctUntilChanged()
            .collectLatest { cfg ->
                val server = cfg[0]; val code = cfg[1]; val name = cfg[2]
                val shuffle = cfg[3]; val handoff = cfg[4]; val auto = cfg[5]; val autoGroup = cfg[6]
                libraryEnabled = cfg[7].toBoolean()
                if (!libraryEnabled) {
                    DeviceSyncHub.libraryOwnedByDevices = false
                    DeviceSyncHub.libraryStatus.value = DeviceSyncHub.LibraryStatus.Off
                }
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
        val stored = appContext.dataStore.data.first()[DeviceSyncDeviceIdKey]
        // Row 540: the id comes from this hardware. A stored id that is different was COPIED here (backup, device transfer) or is the old
        // random one: replace it, so two devices can never share one.
        val hardware = DeviceIdentity.fromHardware(
            runCatching { android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID) }.getOrNull(),
        )
        if (hardware != null) {
            if (stored != hardware) {
                appContext.dataStore.edit { it[DeviceSyncDeviceIdKey] = hardware }
                if (!stored.isNullOrBlank()) Timber.tag(TAG).i("DEVICE_SYNC device id re-derived from this hardware (it was copied or old)")
            }
            return hardware
        }
        if (!stored.isNullOrBlank()) return stored
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
        if (library?.handle(msg) == true) return
        when (msg.str("t")) {
            "hello_ok" -> {
                helloLib = msg.int("lib") ?: 0
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
                // Row 536: with the library synced through the room there is nothing to ask YouTube for.
                if (libraryEnabled) return
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
        scope.launch { runCatching { evaluateRemote() } }
    }

    // ── remote control: this device's own player shows and controls the other one (row 537) ───────────────────────────────────

    /** Decide whether the player is in «remote» mode, and keep the mirror and the timeline moving while it is. */
    private suspend fun evaluateRemote() {
        val remote = DeviceSyncHub.remoteNow.value
        val localPlaying = onMain {
            val p = playerOrNull()
            p != null && p.playWhenReady && p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED
        }
        val nowMs = System.currentTimeMillis()
        if (localPlaying) lastLocalPlayingAt = nowMs
        // Owner 2026-10-31: «cuando le puse pausa, de la nada me cambió la canción y me puso lo que se escuchaba en la Galaxy Tab, yo no
        // le dije que hiciera eso». Pausing HERE made this device look «silent», so it switched to showing the other device and replaced
        // its own queue with that one. A device that was playing a moment ago and was paused keeps its own song: the remote is shown
        // only to a device that has been idle (or after the user acts on a device from here).
        // Row 563 (owner 2026-10-31, beta 8: «el control remoto ya no funciona… no me sale Continuar desde aquí, la línea de tiempo no se registra, no aparece
        // el volumen»): beta 8 held the remote back after ANY pause here, which also switched off «Continuar aquí», the timeline and the volume while
        // the other device was PLAYING. The hold-back is only for the case that hurt: a remote that is NOT playing (shown only because this device
        // controlled it a while ago, so what it published is stale) must not replace the song this device was just playing.
        val heldBack = RemotePlayback.holdBack(
            localPlaying = localPlaying,
            remotePlaying = remote?.playing == true,
            remoteActive = RemotePlayback.active,
            sinceLocalPlayedMs = nowMs - lastLocalPlayingAt,
            graceMs = LOCAL_PAUSE_GRACE_MS,
        )
        if (heldBack && remote != null) Timber.tag(TAG).i("DEVICE_SYNC remote control held back: stale paused remote, paused here a moment ago")
        val active = !heldBack && RemotePlayback.shouldBeActive(
            remote = remote,
            thisDeviceId = deviceId,
            handoffEnabled = handoffEnabled,
            connected = DeviceSyncHub.connection.value is DeviceSyncHub.Connection.Connected,
            localPlaying = localPlaying,
            controllingRecently = RemotePlayback.controllingRecently(),
            serverNowMs = serverNow(),
        )
        if (!active || remote == null) {
            if (RemotePlayback.active) {
                RemotePlayback.update(null, serverNow())
                remoteTick?.cancel()
                remoteTick = null
                Timber.tag(TAG).i("DEVICE_SYNC remote control off")
            }
            return
        }
        if (!RemotePlayback.active) Timber.tag(TAG).i("DEVICE_SYNC remote control on")
        RemotePlayback.update(remote, serverNow())
        mirrorRemote(remote)
        if (remote.playing && remoteTick?.isActive != true) {
            // One tick a second, only while the other device plays and this player is showing it.
            remoteTick = scope.launch {
                while (isActive && RemotePlayback.active && RemotePlayback.playing.value) {
                    delay(1_000L)
                    RemotePlayback.tick(serverNow())
                }
            }
        }
    }

    /**
     * So this device's player has something to show — the other device's song, with its cover, title and lyrics — load its queue
     * window WITHOUT preparing or playing it (the same «restore» path the app uses at boot): nothing is downloaded and nothing sounds.
     */
    private suspend fun mirrorRemote(state: NowState) {
        val current = state.current ?: return
        onMain {
            val p = playerOrNull() ?: return@onMain
            if (p.currentMediaItem?.mediaId == current.id) return@onMain
            for (i in 0 until p.mediaItemCount) {
                if (p.getMediaItemAt(i).mediaId == current.id) {
                    p.seekTo(i, 0L)
                    return@onMain
                }
            }
            val items = state.items.map { item ->
                MediaMetadata(
                    id = item.id,
                    title = item.title,
                    artists = listOf(MediaMetadata.Artist(id = null, name = item.artist)),
                    duration = 0,
                    thumbnailUrl = item.cover.ifBlank { null },
                ).toMediaItem()
            }
            service.playQueue(
                ListQueue(title = state.deviceName.ifBlank { null }, items = items, startIndex = state.index, position = 0L),
                playWhenReady = false,
                isRestore = true,
            )
        }
    }

    /** Re-evaluate the instant THIS device starts or stops playing, not at the next 4 s tick. */
    private suspend fun attachPlayerListener() {
        if (playerListenerAttached) return
        onMain {
            val p = playerOrNull() ?: return@onMain
            p.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    scope.launch { runCatching { evaluateRemote() } }
                }
            })
            playerListenerAttached = true
        }
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
            volume = o.int("vol")?.let { it.coerceIn(0, 1000) / 1000f } ?: -1f,
        )
    }

    // ── commands ───────────────────────────────────────────────────────────────────────────────────

    private suspend fun runCommand(from: String, cmd: String, args: JsonObject) {
        Timber.tag(TAG).i("DEVICE_SYNC command %s", cmd)
        if (cmd == "play" || cmd == "pause" || cmd == "next" || cmd == "prev" || cmd == "seek" || cmd == "dislike" || cmd == "like" || cmd == "playitem") {
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
            "next" -> onMain {
                val p = service.player
                if (p.hasNextMediaItem()) p.seekToNext() else armPendingNext(p)
            }
            "prev" -> onMain { service.player.seekToPrevious() }
            "seek" -> args.long("positionMs")?.let { p -> onMain { service.player.seekTo(p) } }
            // Owner 2026-10-31: «toqué el no me gusta en mi cel mientras sonaba en la tablet… la tablet registró el no me gusta pero no
            // hizo lo que hace». The song is judged where it PLAYS: this device does the whole thing (remember it, drop it from the
            // queue, skip), but only if it is still the song the other device was looking at.
            "dislike" -> args.str("trackId")?.let { id ->
                onMain {
                    if (service.player.currentMediaItem?.mediaId == id) service.dislikeCurrentSong()
                    else Timber.tag(TAG).i("DEVICE_SYNC command dislike ignored: the song is no longer the current one")
                }
            }
            // The volume slider of the device that CONTROLS reaches the one that plays (owner 2026-10-31). It is this app's own volume
            // (the same one the in-app slider sets), 0..1000 per mille.
            "volume" -> args.int("level")?.let { level ->
                if (service.playerVolumeInitialized) service.playerVolume.value = (level.coerceIn(0, 1000) / 1000f)
            }
            // A row tapped in the other device's queue list: jump to that song here and play it.
            "playitem" -> args.str("trackId")?.let { id ->
                onMain {
                    val p = service.player
                    var found = false
                    for (i in 0 until p.mediaItemCount) {
                        if (p.getMediaItemAt(i).mediaId == id) {
                            p.seekTo(i, 0L)
                            p.play()
                            found = true
                            break
                        }
                    }
                    if (!found) Timber.tag(TAG).i("DEVICE_SYNC command playitem ignored: the song is not in this queue")
                }
            }
            "like" -> args.str("trackId")?.let { id ->
                onMain {
                    if (service.player.currentMediaItem?.mediaId == id) service.toggleLike()
                    else Timber.tag(TAG).i("DEVICE_SYNC command like ignored: the song is no longer the current one")
                }
            }
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
                // A state that is old is not a request to start playing NOW (a delayed or misrouted message must never make a device
                // start by itself — owner 2026-10-30: «de la nada me empezó a reproducir»).
                (args["state"] as? JsonObject)?.let { parseNow(it) }
                    ?.takeIf { serverNow() - it.atMs <= TAKE_MAX_AGE_MS }
                    ?.let { play(it) }
            }
        }
    }

    // Owner log 2026-10-30 (tablet): «toqué siguiente y la tablet no cambiaba». At the end of the queue the smart queue takes ~10 s to
    // find the next songs, and a `next` in that gap did nothing — six taps in a row, only the seventh (after the refill) skipped. A
    // `next` that finds no next song now waits for the refill and skips as soon as one arrives (row 554). Taps while waiting count
    // (up to 3), the wait gives up after 30 s.
    @Volatile private var lastLocalPlayingAt = 0L

    private var pendingNexts = 0
    private var pendingNextListener: Player.Listener? = null
    private var pendingNextTimeout: Job? = null

    private fun armPendingNext(p: androidx.media3.exoplayer.ExoPlayer) {
        pendingNexts = (pendingNexts + 1).coerceAtMost(3)
        if (pendingNextListener == null) {
            val l = object : Player.Listener {
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    if (pendingNexts > 0 && p.hasNextMediaItem()) {
                        pendingNexts--
                        p.seekToNext()
                        Timber.tag(TAG).i("DEVICE_SYNC next applied after the queue grew")
                        scope.launch { delay(RESPOND_AFTER_COMMAND_MS); runCatching { publishNow(force = true) } }
                    }
                    if (pendingNexts == 0) disarmPendingNext(p)
                }
            }
            pendingNextListener = l
            p.addListener(l)
        }
        pendingNextTimeout?.cancel()
        pendingNextTimeout = scope.launch {
            delay(30_000L)
            onMain { disarmPendingNext(p) }
        }
        Timber.tag(TAG).i("DEVICE_SYNC next waits for the queue (pending=%d)", pendingNexts)
    }

    private fun disarmPendingNext(p: androidx.media3.exoplayer.ExoPlayer) {
        pendingNexts = 0
        pendingNextListener?.let { p.removeListener(it) }
        pendingNextListener = null
        pendingNextTimeout?.cancel()
        pendingNextTimeout = null
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
            service.remoteRedirectBypass = true
            try {
                service.playQueue(ListQueue(title = state.deviceName.ifBlank { null }, items = items, startIndex = state.index, position = position), playWhenReady = true)
            } finally {
                service.remoteRedirectBypass = false
            }
        }
    }

    override fun control(deviceId: String, command: String, positionMs: Long?, trackId: String?, levelPermille: Int?) {
        scope.launch {
            val sent = client.send(buildJsonObject {
                put("t", "cmd"); put("to", deviceId); put("cmd", command)
                if (positionMs != null || trackId != null || levelPermille != null) put("args", buildJsonObject {
                    if (positionMs != null) put("positionMs", positionMs.coerceAtLeast(0L))
                    if (trackId != null) put("trackId", trackId)
                    if (levelPermille != null) put("level", levelPermille.coerceIn(0, 1000))
                })
            })
            if (!sent) {
                DeviceSyncHub.notice("Sin conexión con la sincronización.")
            }
        }
    }

    override fun takeOverFrom(deviceId: String) {
        scope.launch {
            // Owner 2026-10-31: after the link between two devices dropped, «Continuar aquí» brought back the OLD queue and played
            // what the other one had before. What we last heard from a device that was PLAYING and then went quiet is not what it
            // plays now: only a state that is still fresh may stand in for an answer that never comes (row 553).
            val fallback = DeviceSyncHub.remoteNow.value
                ?.takeIf { it.deviceId == deviceId }
                ?.takeIf { !it.playing || serverNow() - it.atMs <= RemotePlayback.FRESH_MS }
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
                    // The other device never answered the hand-over: make sure it stops if it IS reachable, so both do not play.
                    client.send(buildJsonObject { put("t", "cmd"); put("to", deviceId); put("cmd", "pause") })
                } else {
                    DeviceSyncHub.notice("Ese dispositivo no responde. Cuando vuelva a conectarse, inténtalo de nuevo.")
                }
            }
        }
    }

    /**
     * A queue the user started on THIS device while it controls another one: the other device plays it (as a hand-over in the other
     * direction, without pausing anything here — nothing plays here). False when there is nothing to send or no one to send it to.
     */
    suspend fun sendQueueToRemote(status: iad1tya.echo.music.playback.queues.Queue.Status): Boolean {
        val target = RemotePlayback.target.value ?: return false
        if (status.items.isEmpty()) return false
        val first = (status.mediaItemIndex - WINDOW_BEHIND).coerceAtLeast(0)
        val window = status.items.drop(first).take(WINDOW_BEHIND + WINDOW_AHEAD + 1)
        val state = buildJsonObject {
            put("items", buildJsonArray { window.forEach { add(nowItem(it)) } })
            put("index", (status.mediaItemIndex - first).coerceIn(0, window.lastIndex))
            put("positionMs", status.position)
            put("playing", true)
            put("at", serverNow())
        }
        val sent = client.send(buildJsonObject {
            put("t", "cmd"); put("to", target.deviceId); put("cmd", "take"); put("args", buildJsonObject { put("state", state) })
        })
        if (sent) {
            Timber.tag(TAG).i("DEVICE_SYNC queue sent to the controlled device items=%d", window.size)
            RemotePlayback.playing.value = true
        } else {
            DeviceSyncHub.notice("Sin conexión con la sincronización.")
        }
        return sent
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

    override fun syncLibraryNow() {
        library?.resync()
    }

    override fun libraryChanged(what: String) {
        // Row 536: the room carries the change itself; the old «go and ask YouTube» ping is only for when this is switched off.
        val lib = library
        if (libraryEnabled && lib != null) {
            lib.kick(fast = true)
            return
        }
        libraryPing?.cancel()
        libraryPing = scope.launch {
            delay(LIBRARY_DEBOUNCE_MS)
            client.send(buildJsonObject { put("t", "library"); put("what", what) })
        }
    }

    // ── what this device plays ─────────────────────────────────────────────────────────────────────

    private suspend fun onConnected() {
        library?.onConnected(helloLib)
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
                attachPlayerListener()
                evaluateRemote()
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
            if (service.playerVolumeInitialized) put("vol", (service.playerVolume.value.coerceIn(0f, 1f) * 1000).toInt())
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
        // While this player is only SHOWING the other device's playback it must not announce it as its own (it would overwrite
        // the real state in the room and the two devices would fight over who is playing).
        if (RemotePlayback.active) return
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
        const val TAKE_MAX_AGE_MS = 30_000L
        const val RESPOND_AFTER_COMMAND_MS = 600L
        const val LIBRARY_DEBOUNCE_MS = 3_000L
        /** After pausing here, the other device is not shown (and this queue is not replaced) for this long. */
        const val LOCAL_PAUSE_GRACE_MS = 10 * 60_000L
        const val WINDOW_BEHIND = 5
        const val WINDOW_AHEAD = 60
        const val PUSH_BATCH = 2_000
        const val CHUNK_IDS = 1_000
    }
}
