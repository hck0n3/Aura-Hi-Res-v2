/*
 * Ported from SimpMusic (GPL-3.0), adapted to this app's player surface.
 *
 * SimpMusic bridges against its own `MediaPlayerHandler` — an object that lives inside its media
 * SERVICE and therefore exists for as long as music can play. This app has one player, the ExoPlayer
 * inside [MusicService], and this bridge now hangs off THE SERVICE for exactly the same reason.
 *
 * 🔴 WHY THAT CHANGED (owner report 2026-09-15: "escuchar juntos no funciona bien", after row 218
 * already fixed the wrong-thread reads): the previous port took its player from the `PlayerConnection`
 * that MainActivity hands over on bind. That seam is the ACTIVITY's, and it decays in four ways the
 * room cannot survive:
 *   1. `PlayerConnection.isPlaying` is `stateIn(lifecycleScope)` — once the Activity is destroyed it
 *      stops emitting, so the host's PLAY/PAUSE was never published again while the screen was off.
 *   2. The guest-resume and host-seek listeners were installed on `connection.player` ONCE, at bind
 *      time. The service rebuilds its ExoPlayer (`_playerFlow`), and after any rebuild both listeners
 *      were sitting on a dead player: seeks stopped publishing and guest resume stopped re-syncing.
 *   3. They were also re-installed on EVERY rebind (onStop→onStart), stacking duplicates, so a seek
 *      was published once per foreground round trip the user had made.
 *   4. `publishTrackChangesAsHost`/`publishPlayPauseAsHost` collected an inner flow inside
 *      `connection.filterNotNull().collect {}` — the outer collector can never resume, so a NEW
 *      PlayerConnection (process death, service restart) was never picked up at all.
 * Rooted in the service, the bridge follows `playerFlow`, keeps exactly one listener on the live
 * player, and is not affected by whether any UI exists.
 *
 * The DECISIONS are SimpMusic's verbatim (play intent decided before loading, playWhenReady not
 * isPlaying, guests may pause and stay paused, queue carried on the same message as the track, the
 * queue republished on its own, buffer barrier answered from bufferedPercentage); only the calls
 * into the player differ.
 *
 * The direction of travel is decided entirely by who hosts:
 * - **Host** watches the local player and publishes what it does.
 * - **Guest** watches the room and applies what the host did, and publishes nothing.
 *
 * The [applyingRemote] guard is what stops those two from feeding each other: applying a remote
 * pause makes the local player report "paused", which would otherwise be published straight back
 * to the server as a fresh command.
 */
package iad1tya.echo.music.listentogether

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import iad1tya.echo.music.extensions.currentMetadata
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playback.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val TAG = "ListenTogetherBridge"

/** What the guest reacts to. A data class so `distinctUntilChanged` compares every field. */
private data class RoomSnapshot(
    val track: TrackInfo?,
    val isPlaying: Boolean,
    val position: Long,
    val queueIds: List<String>,
)

/**
 * Joins a Listen Together room to the local player.
 *
 * Lives as long as the app does (Hilt singleton). [MusicService] attaches itself the moment its
 * player exists and detaches in `onDestroy`; nothing else may hand a player to this class, because
 * anything else would be shorter-lived than the room.
 */
@javax.inject.Singleton
class ListenTogetherPlaybackBridge @javax.inject.Inject constructor(
    private val session: ListenTogetherSession,
) {
    /** The live service, or null while no player exists. Every collector tolerates null. */
    private val serviceFlow = MutableStateFlow<MusicService?>(null)

    /**
     * El tempo que el usuario eligió a mano en «Tempo y tono», guardado mientras la sala ajusta la
     * velocidad para alinearlo. Null = no estamos corrigiendo, así que la velocidad del reproductor
     * ES la suya y se puede volver a leer.
     *
     * Hace falta guardarlo porque el ajuste MULTIPLICA sobre él: sin esto, leer la velocidad del
     * reproductor mientras corregimos devolvería el valor ya recortado y cada corrección se
     * compondría sobre la anterior, alejándose de 1.0 sin volver nunca.
     */
    private var syncUserTempo: Float? = null

    /** True while the guest is executing the host's last command; guards the publish loops. */
    @Volatile
    private var applyingRemote = false

    private var started = false
    private var lastPublishedTrackId: String? = null
    private var lastAppliedTrackId: String? = null
    private var lastAppliedQueueIds: List<String> = emptyList()
    private var lastPublishedQueueIds: List<String> = emptyList()

    /**
     * Whether the room was playing before the command currently being applied.
     *
     * Needed because change_track always says "not playing" — see [watchRoomForGuests].
     */
    private var lastRoomPlaying = false

    // ── What the ONE player listener publishes into the bridge ────────────────────────────────────
    // These replace the Activity-scoped flows the old seam collected. They are updated on the main
    // thread by [playerListener] and read from the collectors below, so no collector ever has to
    // touch the player just to learn whether it is playing.
    private val playIntent = MutableStateFlow(false)
    private val isPlayingNow = MutableStateFlow(false)

    /** Bumped whenever the host's timeline changes, so the queue can be republished on its own. */
    private val timelineRevision = MutableStateFlow(0)

    /** The player the listener is currently installed on. Main thread only. */
    private var listeningTo: ExoPlayer? = null

    // ─────────────────────────── the service seam ───────────────────────────

    /** Called by [MusicService] once its player exists. Idempotent. */
    fun attachService(service: MusicService) {
        serviceFlow.value = service
    }

    /** Called by [MusicService.onDestroy]. Identity-guarded: a newer service must not be dropped. */
    fun detachService(service: MusicService) {
        if (serviceFlow.value === service) serviceFlow.value = null
    }

    /** Idempotent: callers cannot know whether something else already started it. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        Timber.tag(TAG).i("Playback bridge started")
        scope.launch { followPlayer() }
        scope.launch { watchRoomForGuests() }
        scope.launch { publishCurrentStateOnJoin() }
        scope.launch { publishStateWhenSomeoneArrives() }
        scope.launch { publishTrackChangesAsHost() }
        scope.launch { publishPlayPauseAsHost() }
        scope.launch { publishQueueAsHost() }
        scope.launch { answerBufferBarrier() }
    }

    /**
     * Keeps exactly ONE listener on whatever player the service currently has.
     *
     * The service rebuilds its ExoPlayer (format changes, offload, secondary players), and a
     * listener left on the old instance is a publisher that has silently stopped. `flatMapLatest`
     * is what makes this a MOVE rather than an accumulation.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun followPlayer() {
        serviceFlow
            .flatMapLatest { service -> service?.playerFlow ?: flowOf<ExoPlayer?>(null) }
            .distinctUntilChanged()
            .collect { player -> withContext(Dispatchers.Main.immediate) { attachListener(player) } }
    }

    private fun attachListener(player: ExoPlayer?) {
        val previous = listeningTo
        if (previous === player) return
        previous?.removeListener(playerListener)
        listeningTo = player
        if (player == null) return
        player.addListener(playerListener)
        // Seed, or the first publish after a player swap compares against a stale intent.
        playIntent.value = player.playWhenReady
        isPlayingNow.value = player.isPlaying
        timelineRevision.value++
        Timber.tag(TAG).i("Listening to the service player")
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            playIntent.value = playWhenReady
            if (!playWhenReady) return
            // Guest-side resume: the local player started playing while we are a guest and the apply
            // loop did NOT cause it — ask the server where the room is. This is what Metrolist's and
            // SimpMusic's managers do (`requestSync` on guest play), and what makes "guests may
            // pause, and stay paused" possible at all.
            val state = session.state.value
            if (!state.inRoom || state.isHost || applyingRemote) return
            Timber.tag(TAG).i("Guest resumed — asking the server where the room is")
            session.requestSync()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isPlayingNow.value = isPlaying
        }

        override fun onTimelineChanged(
            timeline: androidx.media3.common.Timeline,
            reason: Int,
        ) {
            timelineRevision.value++
        }

        /**
         * Publishes a seek.
         *
         * Neither the metadata nor the play state changes when the host drags the scrubber, so
         * without this a seek is simply never sent and guests keep playing from wherever they were.
         * Upstream derives it from a progress flow because Desktop runs mpv; on Media3 the player
         * says so itself, which is also what Metrolist does.
         */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            val state = session.state.value
            if (!state.inRoom || !state.isHost || applyingRemote) return
            // Mismo criterio que [safePosition]: se publica lo que se OYE. Este sitio no pasa por
            // ese embudo porque la posición la trae el propio evento de salto.
            val service = serviceFlow.value
            val progress = (newPosition.positionMs - (service?.let { outputLatencyMs(it) } ?: 0))
                .coerceAtLeast(0L)
            Timber.tag(TAG).i("Host publishing SEEK to %d", progress)
            session.sendPlaybackAction(
                action = PlaybackActions.SEEK,
                trackId = "",
                position = progress,
                trackInfo = null,
            )
        }
    }

    // ─────────────────────────── guest: follow the host ───────────────────────────

    private suspend fun watchRoomForGuests() {
        session.state
            // The queue is part of the key: without it a room state that changed ONLY its queue
            // compares equal here and is dropped, so the guest never builds the host's queue at all.
            .map {
                RoomSnapshot(
                    track = it.currentTrack,
                    isPlaying = it.isPlaying,
                    position = it.position,
                    queueIds = it.queue.map { t -> t.id },
                ) to (it.inRoom && !it.isHost)
            }
            .distinctUntilChanged()
            .collect { (snapshot, shouldFollow) ->
                if (!shouldFollow) {
                    // Salir de la sala (o pasar a ser anfitrión) con la velocidad recortada la
                    // dejaría así para siempre: el ajuste solo se toca desde aquí, y aquí ya no se
                    // entra. Devolverla es parte de apagar la corrección, no un detalle.
                    restoreUserTempo()
                    return@collect
                }
                val service = serviceFlow.value ?: return@collect
                val (track, isPlaying, position, queueIds) = snapshot
                Timber.tag(TAG).i("Room says: playing=%b pos=%d queue=%d", isPlaying, position, queueIds.size)
                applyingRemote = true
                try {
                    // Rebuild when the track changes OR when the queue behind it does — the queue
                    // can legitimately arrive while the same track keeps playing.
                    val queueChanged = queueIds != lastAppliedQueueIds
                    val trackChanged =
                        track != null && track.id.isNotBlank() && (track.id != lastAppliedTrackId || queueChanged)
                    // The server forces IsPlaying=false on EVERY change_track — protocol default,
                    // not the host pausing. Obeying it pauses the track just loaded, which is why
                    // the guest sat silent on next, prev AND end-of-song alike. Carry the room's
                    // previous intent across the change; a real pause arrives as its own command,
                    // with the track unchanged, and is applied normally.
                    val playing = if (trackChanged && !isPlaying) lastRoomPlaying else isPlaying
                    lastRoomPlaying = playing
                    if (trackChanged && track != null) {
                        val sameTrack = track.id == lastAppliedTrackId
                        lastAppliedTrackId = track.id
                        lastAppliedQueueIds = queueIds
                        // Decided BEFORE loading, not corrected afterwards: loading with a hardcoded
                        // playWhenReady=true and letting applyTransport pause it is a race, and the
                        // guest wins it by starting to play in a room the host has paused.
                        // A NEW track still starts where the room is, not at zero: someone joining
                        // a room mid-song must land next to everyone else. Only the same track
                        // being rebuilt (the queue arrived late) keeps the local playhead.
                        val startAt =
                            when {
                                sameTrack -> onMain { safePosition(service) }
                                // change_track carries position 0, and 0 means "from the top" —
                                // running it through the clock correction turns it into however
                                // long ago the last command was, which can seek past the end.
                                position <= 0L -> 0L
                                else -> session.positionAt(position, playing)
                            }
                        playTrack(service, track, keepPosition = startAt, playWhenReady = playing)
                    }
                    applyTransport(service, playing, position)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to apply remote state")
                } finally {
                    applyingRemote = false
                }
            }
    }

    /**
     * Devuelve la velocidad que el usuario tenía antes de que la sala empezara a corregir.
     *
     * Exactamente su valor, no "más o menos 1.0": `Player.setOffloadEnabled` decide si pedir soporte
     * de cambio de velocidad comparando `speed != 1f`, así que un 1.0000001 residual dejaría el
     * audio offload rechazado para siempre después de una sala — batería perdida sin que nada lo
     * diga.
     */
    private fun restoreUserTempo(player: Player? = null) {
        val tempo = syncUserTempo ?: return
        syncUserTempo = null
        val target = player ?: runCatching { serviceFlow.value?.player }.getOrNull() ?: return
        if (target.playbackParameters.speed != tempo) {
            target.playbackParameters = target.playbackParameters.withSpeed(tempo)
        }
    }

    private suspend fun applyTransport(
        service: MusicService,
        isPlaying: Boolean,
        position: Long,
    ) = withContext(Dispatchers.Main.immediate) {
        // Correct the position for however long the command spent in flight; ServerClock falls back
        // to the raw value whenever it is not calibrated yet.
        val corrected = session.positionAt(position, isPlaying)
        val player = runCatching { service.player }.getOrNull() ?: return@withContext
        // ALCANZAR ESTIRANDO EL TIEMPO, como Sonos / AirPlay 2 — no saltando.
        //
        // 🔴 PETICIÓN DEL DUEÑO (2026-09-17): *"hazlo entonces como lo hace Sonos o AirPlay"*, tras
        // preguntar por qué esto nunca sonaba sincronizado del todo.
        //
        // Antes esto era `if (|error| > 750 ms) seekTo(...)`, y ese umbral no tenía ningún valor
        // bueno: pequeño, saltas a cada rato y cada salto es un corte audible; grande, aceptas hasta
        // tres cuartos de segundo de desfase como normal. El salto era la herramienta equivocada.
        // [SyncCorrection] elige entre tres regímenes y el porqué de cada número está allí.
        //
        // Solo mientras SUENA: con la reproducción parada no hay nada que converger, y tocar la
        // velocidad de un reproductor en pausa no alinea nada.
        if (isPlaying) {
            // Cuando NO estamos corrigiendo, la velocidad del reproductor es la que el usuario
            // eligió — se vuelve a leer aquí para que un cambio suyo a mitad de sala se recoja solo,
            // en vez de que se lo pisemos con el valor que capturamos al entrar.
            val userTempo = syncUserTempo ?: player.playbackParameters.speed
            // El anfitrión ya publicó lo que OYE, así que aquí se compara contra lo que oigo yo:
            // resto el retardo de mi propia salida. Ver [OutputLatency] — así los dos extremos se
            // compensan solos y el caso "los dos por Bluetooth" no sobrecorrige.
            val action = SyncCorrection.correct(
                errorMs = OutputLatency.syncErrorMs(
                    myPositionMs = player.currentPosition,
                    hostPositionMs = corrected,
                    myLatencyMs = outputLatencyMs(service),
                ),
                targetMs = corrected,
            )
            if (action is SyncAction.Resync) player.seekTo(action.positionMs)

            val target = SyncCorrection.playerSpeed(userTempo, action)
            // Recordar el tempo del usuario SOLO mientras se corrige; en cuanto deja de corregirse
            // se suelta, para que la próxima lectura vuelva a salir del reproductor.
            syncUserTempo = if (action is SyncAction.Trim) userTempo else null
            // El tono NO se toca: `withSpeed` conserva el `pitch` del usuario, y es lo que hace que
            // el estirado sea inaudible en vez de desafinar la canción.
            if (player.playbackParameters.speed != target) {
                player.playbackParameters = player.playbackParameters.withSpeed(target)
            }
        } else {
            restoreUserTempo(player)
        }
        // playWhenReady, not isPlaying: a track that is still buffering reports isPlaying=false
        // while already committed to playing, so comparing against it re-issues play() every tick
        // and, worse, lets a stale pause land on a track that was about to start.
        if (isPlaying && !player.playWhenReady) {
            // A guest whose app has just been opened has never prepared anything, and `play()` on an
            // IDLE player is a no-op that leaves the room playing alone. PlayerConnection.play() used
            // to hide this; the service player has to be told.
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        } else if (!isPlaying && player.playWhenReady) {
            player.pause()
        }
    }

    /**
     * Loads the host's track and queue.
     *
     * Built straight from the room's own [TrackInfo] — deliberately NOT by resolving metadata from
     * the catalogue first (the old implementation did that via YouTube.queue and it is what made
     * guests stall on a network round trip per track). The stream itself is still resolved
     * locally by MusicService: the room only ever carries ids.
     */
    private suspend fun playTrack(
        service: MusicService,
        info: TrackInfo,
        keepPosition: Long = 0L,
        playWhenReady: Boolean,
    ) {
        val roomQueue = session.state.value.queue
        // Metrolist's canonicalPlaybackQueue: the current track leads, upcoming follows, no dupes.
        val ordered =
            (listOf(info) + roomQueue.filter { it.id != info.id })
                .filter { it.id.isNotBlank() }
                .distinctBy { it.id }
        Timber.tag(TAG).i("Guest loading %s + %d upcoming", info.id.asLogToken(), ordered.size - 1)

        // Dispatchers.Main is mandatory, not tidiness: Media3 throws if the player is touched off
        // the main thread, and this collector runs on the bridge scope (Default).
        withContext(Dispatchers.Main.immediate) {
            val items = ordered.map { it.toRoomMediaItem() }
            val player = runCatching { service.player }.getOrNull() ?: return@withContext
            // La cola de la sala la manda el ANFITRIÓN, así que el servicio tiene que saber que la suya
            // ya no es la que estaba puesta antes. Si no, a cinco canciones del final paginaba la cola
            // anterior del invitado DENTRO de la sala — canciones que el anfitrión nunca puso. Es el mismo
            // agujero que el dueño reportó en Android Auto (2026-09-17); ver `adoptDirectQueue`, que
            // además fija el título en el mismo sitio.
            service.adoptDirectQueue(items = items, title = "Listen Together")
            player.setMediaItems(items, 0, keepPosition.coerceAtLeast(0L))
            // prepare() + the intent decided by the caller, in that order and BEFORE anything can
            // observe the player: `setMediaItems` alone leaves an idle player idle (nothing ever
            // loads), and leaving playWhenReady to be corrected afterwards is the race upstream
            // calls out — the guest wins it and plays in a room the host has paused.
            player.playWhenReady = playWhenReady
            player.prepare()
        }
    }

    /**
     * A room track as a media item.
     *
     * `musicVideoType` is left NULL on purpose: inside a room every client must be on the same
     * rendition, and for listening together that rendition is audio. Letting it be inferred from a
     * catalogue resolve is what produced video-with-no-sound on the guest in upstream's first
     * attempt — here a null type simply plays as a song.
     */
    private fun TrackInfo.toRoomMediaItem(): androidx.media3.common.MediaItem =
        MediaMetadata(
            id = id,
            title = title.ifBlank { id },
            artists =
                listOf(
                    MediaMetadata.Artist(
                        id = null,
                        name = artist.ifBlank { "" },
                    ),
                ),
            duration = if (duration > Int.MAX_VALUE) Int.MAX_VALUE else duration.coerceAtLeast(0L).toInt(),
            thumbnailUrl = thumbnail.ifBlank { null },
            album =
                album.takeIf { it.isNotBlank() }?.let {
                    MediaMetadata.Album(id = it, title = it)
                },
        ).toMediaItem()

    // ─────────────────────────── host: publish what we do ───────────────────────────

    /**
     * Publishes what is ALREADY playing the moment we become host.
     *
     * Everything else here reacts to a *change* — a track transition, a play/pause. Someone who
     * was already listening and then opens a room produces neither, so without this the room has
     * no state at all and every guest sits in silence waiting for a command that only arrives if
     * the host happens to touch the transport.
     */
    private suspend fun publishCurrentStateOnJoin() {
        session.state
            .map { it.inRoom && it.isHost }
            .distinctUntilChanged()
            .collect { isHosting ->
                if (isHosting) {
                    publishSnapshot()
                } else {
                    lastPublishedTrackId = null
                    lastPublishedQueueIds = emptyList()
                }
            }
    }

    /**
     * Re-publishes for a guest who arrives later.
     *
     * The server keeps the room's last known state, but only what the host has told it; a guest
     * approved before the host's first command would otherwise join an empty room.
     */
    private suspend fun publishStateWhenSomeoneArrives() {
        session.state
            .map { it.members.size }
            .distinctUntilChanged()
            .collect { count ->
                val state = session.state.value
                if (state.inRoom && state.isHost && count > 1) publishSnapshot()
            }
    }

    /** Everything the host publishes about the current track, read in ONE main-thread hop. */
    private data class HostSnapshot(
        val id: String,
        val trackInfo: TrackInfo,
        val queue: List<TrackInfo>,
        val queueTitle: String,
        val position: Long,
        val playWhenReady: Boolean,
    )

    /**
     * OWNER REPORT 2026-09-14 (crash "Player is accessed on the wrong thread" + guests hearing
     * nothing): this bridge runs on the manager's Dispatchers.Default scope, and every player read
     * the host made there threw. Wrapped reads silently returned null/empty — the room got no track,
     * no queue and a PAUSE — and the unwrapped playWhenReady poll crashed the app on each change.
     * Media3 players may only be touched on the main thread, so every read goes through here.
     */
    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main.immediate) { block() }

    private suspend fun hostSnapshot(service: MusicService): HostSnapshot? = onMain {
        runCatching {
            val player = service.player
            val item = player.currentMetadata ?: return@runCatching null
            if (item.id.isBlank()) return@runCatching null
            HostSnapshot(
                id = item.id,
                trackInfo = item.toTrackInfo(service),
                queue = hostQueueTracks(service),
                queueTitle = service.queueTitle.orEmpty(),
                position = safePosition(service),
                playWhenReady = player.playWhenReady,
            )
        }.onFailure { Timber.tag(TAG).w(it, "Could not read the host's player") }.getOrNull()
    }

    private suspend fun publishSnapshot() {
        val service = serviceFlow.value ?: return
        val snap = hostSnapshot(service) ?: return
        lastPublishedTrackId = snap.id
        lastPublishedQueueIds = snap.queue.map { it.id }
        session.sendPlaybackAction(
            action = PlaybackActions.CHANGE_TRACK,
            trackId = snap.id,
            position = snap.position,
            trackInfo = snap.trackInfo,
            queue = snap.queue,
            queueTitle = snap.queueTitle,
        )
        // A second command, because change_track alone does not say whether it is running —
        // the server explicitly sets IsPlaying=false on a track change.
        session.sendPlaybackAction(
            action = if (snap.playWhenReady) PlaybackActions.PLAY else PlaybackActions.PAUSE,
            trackId = "",
            position = snap.position,
            trackInfo = null,
        )
        Timber.tag(TAG).i("Published current state to the room: %s", snap.id.asLogToken())
    }

    /** The host's queue as the room sees it, read off the player timeline. */
    private fun hostQueueTracks(service: MusicService): List<TrackInfo> =
        runCatching {
            val player = service.player
            val count = player.mediaItemCount
            // The room cannot carry a 4000-song radio; the window AROUND the cursor is what the
            // guests actually need to follow along.
            val cursor = player.currentMediaItemIndex
            val from = (cursor - HOST_QUEUE_WINDOW).coerceAtLeast(0)
            val to = (cursor + HOST_QUEUE_WINDOW).coerceAtMost(count)
            (from until to).mapNotNull { i -> player.getMediaItemAt(i).let { item -> item.toQueueTrackInfo() } }
        }.getOrDefault(emptyList())

    private fun androidx.media3.common.MediaItem.toQueueTrackInfo(): TrackInfo? {
        // The app's own MediaMetadata tag (MediaItemExt.metadata); every item the player builds
        // carries one, and the extension's own null-safety keeps a malformed timeline from
        // killing the host's publishes.
        val meta = this.metadata ?: return null
        return TrackInfo(
            id = meta.id,
            title = meta.title,
            artist = meta.artists.joinToString(", ") { a -> a.name },
            album = meta.album?.title.orEmpty(),
            duration = meta.duration.toLong() * 1000L,
            thumbnail = meta.thumbnailUrl.orEmpty(),
        )
    }

    private fun MediaMetadata.toTrackInfo(service: MusicService): TrackInfo =
        TrackInfo(
            id = id,
            title = title,
            artist = artists.joinToString(", ") { it.name },
            album = album?.title.orEmpty(),
            duration = safeDuration(service),
            thumbnail = thumbnailUrl.orEmpty(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun publishTrackChangesAsHost() {
        // The SERVICE's now-playing, not the Activity's: it is updated for every transition
        // (including the crossfade swap path, registry row 96) and it outlives every screen.
        serviceFlow
            .filterNotNull()
            .flatMapLatest { service -> service.currentMediaMetadata }
            .filterNotNull()
            .distinctUntilChanged { old, new -> old.id == new.id }
            .collect { item ->
                val service = serviceFlow.value ?: return@collect
                val state = session.state.value
                if (!state.inRoom || !state.isHost || applyingRemote) return@collect
                if (item.id == lastPublishedTrackId) return@collect
                lastPublishedTrackId = item.id
                Timber.tag(TAG).i("Host publishing track change: %s", item.id.asLogToken())
                val (trackInfo, queue, queueTitle) = onMain {
                    Triple(item.toTrackInfo(service), hostQueueTracks(service), service.queueTitle.orEmpty())
                }
                lastPublishedQueueIds = queue.map { it.id }
                session.sendPlaybackAction(
                    action = PlaybackActions.CHANGE_TRACK,
                    trackId = item.id,
                    position = 0L,
                    trackInfo = trackInfo,
                    queue = queue,
                    queueTitle = queueTitle,
                )
                // change_track alone leaves the room paused: the server sets IsPlaying=false on
                // every track change. The host's own play state does NOT change when one playing
                // track follows another, so nothing else would ever send this and guests would
                // load each new track and sit there stopped.
                //
                // Whether the host is actually going to play this, decided by WAITING rather
                // than by sampling. Reading playWhenReady inline was wrong twice over: it is
                // false while a next-track buffers, and false again for a moment while the
                // player is rebuilt for a track the host picked from a list — so the PLAY that
                // guests depend on was dropped on exactly the transitions it exists for. A host
                // who is genuinely paused simply never satisfies this and the room stays paused.
                val started =
                    withTimeoutOrNull(PLAY_SETTLE_TIMEOUT_MS) {
                        // playWhenReady, not isPlaying: the intent flips the moment the load path
                        // commits, while audible playback waits for the stream URL to resolve —
                        // which can take longer than any reasonable timeout. Read from the
                        // listener's own flows, so no polling of the player is needed at all.
                        while (!playIntent.value && !isPlayingNow.value) {
                            delay(PLAY_SETTLE_POLL_MS)
                        }
                    } != null
                if (started) {
                    session.sendPlaybackAction(
                        action = PlaybackActions.PLAY,
                        trackId = "",
                        position = onMain { safePosition(service) },
                        trackInfo = null,
                    )
                }
            }
    }

    private suspend fun publishPlayPauseAsHost() {
        // No `distinctUntilChanged`: a StateFlow already conflates equal values, and asking for it
        // again is a compile error in this project (the operator is deprecated on StateFlow).
        isPlayingNow
            .collect { isPlaying ->
                val service = serviceFlow.value ?: return@collect
                val state = session.state.value
                if (!state.inRoom || !state.isHost || applyingRemote) return@collect
                // A host that merely buffers reports isPlaying=false, indistinguishable from a
                // user pause — and publishing it stops the WHOLE room on one device's hiccup.
                // playWhenReady carries the intent, so a dip where the two disagree is not news.
                val intent = playIntent.value
                if (isPlaying != intent) return@collect
                val position = onMain { safePosition(service) }
                Timber.tag(TAG).i("Host publishing %s", if (intent) "PLAY" else "PAUSE")
                session.sendPlaybackAction(
                    action = if (intent) PlaybackActions.PLAY else PlaybackActions.PAUSE,
                    // Deliberately EMPTY. The server rejects a play/pause whose trackId does
                    // not match the track it is holding ("stale_track") and drops it silently;
                    // sending nothing makes it fill in its own current track, which is always
                    // right.
                    trackId = "",
                    position = position,
                    trackInfo = null,
                )
            }
    }

    /**
     * Republishes the queue whenever the host's own queue changes — upstream's `publishQueueAsHost`.
     *
     * Without it the room only ever learns the queue that rode along with a track change, so
     * everything the host adds mid-song (añadir a la cola, the infinite radio extending itself,
     * reordenar) is invisible until the NEXT song starts, and a guest that reaches the end of the
     * short queue it was given plays on alone.
     */
    private suspend fun publishQueueAsHost() {
        // Same as above: the counter is a StateFlow, so consecutive equal values never arrive twice.
        timelineRevision
            .collect {
                val service = serviceFlow.value ?: return@collect
                val state = session.state.value
                if (!state.inRoom || !state.isHost || applyingRemote) return@collect
                val (queue, title) = onMain { hostQueueTracks(service) to service.queueTitle.orEmpty() }
                if (queue.isEmpty()) return@collect
                val ids = queue.map { it.id }
                // The timeline also changes for reasons the room already knows about (the track
                // change publishes its own queue); only a genuinely different list is news.
                if (ids == lastPublishedQueueIds) return@collect
                lastPublishedQueueIds = ids
                Timber.tag(TAG).i("Host publishing queue of %d track(s)", queue.size)
                session.sendQueue(queue, title)
            }
    }

    // ─────────────────────────── the buffer barrier ───────────────────────────

    /**
     * Answers the barrier for OURSELVES: when the room says it is waiting on this device, report
     * readiness once the current track has enough buffered.
     *
     * Nobody in the room hears anything until every member answers, so a client that never sends
     * this silently freezes playback for everyone — including the host.
     */
    private suspend fun answerBufferBarrier() {
        session.state
            .map { it.waitingFor to it.currentTrack?.id }
            .distinctUntilChanged()
            .collect { (waitingFor, trackId) ->
                val state = session.state.value
                if (trackId.isNullOrBlank() || !state.inRoom) return@collect
                if (state.selfUserId !in waitingFor) return@collect
                val service = serviceFlow.value ?: return@collect
                // bufferedPercentage, not isPlaying: the barrier asks whether the track is loaded,
                // and playback is exactly what it is holding back.
                val buffered = onMain { runCatching { service.player.bufferedPercentage }.getOrDefault(0) }
                if (buffered >= READY_BUFFER_PERCENT) {
                    session.reportBufferReady(trackId)
                } else {
                    // Not ready yet — one re-check after a short wait, then answer anyway: a
                    // stalled local resolve must not freeze everyone forever, and the server keeps
                    // its own timeout (the SimpMusic bridge answers the moment it is ready; the
                    // fallback here is the safety valve that upstream leaves to the server).
                    delay(BARRIER_RECHECK_MS)
                    session.reportBufferReady(trackId)
                }
            }
    }

    /**
     * A track id as it may appear in the SHARED log.
     *
     * Regla 4 del registro de regresiones: nothing about what the user is listening to — titles,
     * artists, IDs — goes into `filesDir/logs/app.log`, because that is the file they hand to someone
     * else from Ajustes ▸ Registros. These three lines used to write the raw videoId.
     *
     * Dropping the id outright would make a room undiagnosable, though: the whole question when two
     * phones disagree is whether they think they are on the SAME track. A truncated SHA-256 answers
     * exactly that and nothing else — the same track yields the same token on the host and on the
     * guest, and the token says nothing about the song to anyone reading the log.
     */
    private fun String.asLogToken(): String =
        runCatching {
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(toByteArray())
            digest.take(3).joinToString("") { b -> "%02x".format(b) }
        }.getOrDefault("?")

    /**
     * La posición que el anfitrión PUBLICA: lo que está oyendo, no lo que decodifica.
     *
     * 🔴 (2026-09-17) Ver [OutputLatency]. Entre el decodificador y el altavoz hay un retardo real —
     * 150-250 ms por Bluetooth — y publicar la posición cruda hace que un invitado por altavoz oiga
     * la música antes que el propio anfitrión. Restar aquí el retardo de MI salida es lo que permite
     * que cada extremo compense lo suyo sin tener que saber nada del otro, y sin un campo nuevo en
     * el protocolo: es otro número en el campo de posición que ya existía.
     *
     * Único embudo a propósito: todos los sitios donde el anfitrión publica una posición pasan por
     * aquí, así que la compensación no se puede olvidar en uno de ellos.
     */
    private fun safePosition(service: MusicService): Long =
        runCatching { service.player.currentPosition - outputLatencyMs(service) }
            .getOrDefault(0L)
            .coerceAtLeast(0L)

    /**
     * Lo que tarda el audio en salir por la salida activa de ESTE aparato.
     *
     * Se resuelve en cada llamada en vez de cachearse: el usuario conecta y desconecta el Bluetooth
     * a mitad de sala, y un valor cacheado dejaría la compensación puesta (o quitada) justo cuando
     * cambia lo único que la justifica.
     */
    private fun outputLatencyMs(context: android.content.Context): Int =
        runCatching {
            OutputLatency.defaultMsFor(
                iad1tya.echo.music.eq.data.EqDeviceProfileStore.currentOutputKey(context),
            )
        }.getOrDefault(0)

    private fun safeDuration(service: MusicService): Long =
        runCatching { service.player.duration }.getOrDefault(0L).coerceAtLeast(0L)

    private companion object {
        const val PLAY_SETTLE_TIMEOUT_MS = 2_000L

        /** Poll step for the settle wait; the play intent is edge-triggered, not a suspending wait. */
        const val PLAY_SETTLE_POLL_MS = 50L

        /**
         * Metrolist's own hard-sync threshold (`HARD_SYNC_THRESHOLD_MS`). Below it a seek costs
         * more in stutter than it buys in sync; above it the room is audibly apart.
         */

        /** How much of the host's timeline rides along with a track change. */
        const val HOST_QUEUE_WINDOW = 25

        const val READY_BUFFER_PERCENT = 5

        /** One re-check before giving the room our ready anyway (the server has its own timeout). */
        const val BARRIER_RECHECK_MS = 2_000L
    }
}
