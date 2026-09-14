/*
 * Ported from SimpMusic (GPL-3.0), adapted to this app's player surface:
 * SimpMusic bridges against its own `MediaPlayerHandler` abstraction (KMP, mpv on desktop); this
 * app has ONE player, the ExoPlayer inside MusicService, reached through PlayerConnection. The
 * DECISIONS are SimpMusic's verbatim (play intent decided before loading, playWhenReady not
 * isPlaying, guests may pause and stay paused, queue carried on the same message as the track,
 * buffer barrier answered from bufferedPercentage); only the calls into the player differ.
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

import iad1tya.echo.music.extensions.currentMetadata
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.abs
import kotlin.time.TimeSource

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
 * Lives as long as the app does (Hilt singleton) and talks only to [PlayerConnection], which is the
 * app's one seam to the player — MusicService itself is NEVER touched from here, per the registry's
 * hot-file rule: every MusicSurface consumer reads state through PlayerConnection.
 */
@javax.inject.Singleton
class ListenTogetherPlaybackBridge @javax.inject.Inject constructor(
    private val session: ListenTogetherSession,
    private val tokenStore: SessionTokenStore,
) {
    /**
     * The connection, handed over by MainActivity (same lifecycle seam the previous implementation
     * used). Null until the UI process binds to MusicService; the collectors below tolerate that.
     */
    private val connection = MutableStateFlow<PlayerConnection?>(null)

    /** True while the guest is executing the host's last command; guards the publish loops. */
    @Volatile
    private var applyingRemote = false

    private var started = false
    private var lastPublishedTrackId: String? = null
    private var lastAppliedTrackId: String? = null
    private var lastAppliedQueueIds: List<String> = emptyList()

    /**
     * Whether the room was playing before the command currently being applied.
     *
     * Needed because change_track always says "not playing" — see [watchRoomForGuests].
     */
    private var lastRoomPlaying = false

    private var lastProgress = 0L
    private var lastProgressAt = 0L
    private val processStart = TimeSource.Monotonic.markNow()

    fun setPlayerConnection(value: PlayerConnection?) {
        connection.value = value
    }

    /** Idempotent: callers cannot know whether something else already started it. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        Timber.tag(TAG).i("Playback bridge started")
        scope.launch { watchRoomForGuests() }
        scope.launch { publishCurrentStateOnJoin() }
        scope.launch { publishStateWhenSomeoneArrives() }
        scope.launch { publishTrackChangesAsHost() }
        scope.launch { publishPlayPauseAsHost() }
        scope.launch { answerBufferBarrier() }
        // Seeks publish through the player listener (installHostSeekPublisher), not a collector:
        // PlayerConnection exposes no progress flow and polling one would burn battery inside a
        // room (regla 7 AGENTS).
    }

    // ─────────────────────────── guest: follow the host ───────────────────────────

    /**
     * Guest-side resume detection: the local player started playing while we are a guest and the
     * apply loop did NOT cause it — ask the server where the room is. This is what Metrolist's and
     * SimpMusic's managers do (`requestSync` on guest play), and what makes "guests may pause, and
     * stay paused" possible at all.
     */
    fun installGuestResumeListener(player: androidx.media3.common.Player) {
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                if (!playWhenReady) return
                val state = session.state.value
                if (!state.inRoom || state.isHost || applyingRemote) return
                Timber.tag(TAG).i("Guest resumed — asking the server where the room is")
                session.requestSync()
            }
        })
    }

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
                if (!shouldFollow) return@collect
                val conn = connection.value ?: return@collect
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
                                sameTrack -> onMain { safePosition(conn) }
                                // change_track carries position 0, and 0 means "from the top" —
                                // running it through the clock correction turns it into however
                                // long ago the last command was, which can seek past the end.
                                position <= 0L -> 0L
                                else -> session.positionAt(position, playing)
                            }
                        playTrack(conn, track, keepPosition = startAt, playWhenReady = playing)
                    }
                    applyTransport(conn, playing, position)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to apply remote state")
                } finally {
                    applyingRemote = false
                }
            }
    }

    private suspend fun applyTransport(
        conn: PlayerConnection,
        isPlaying: Boolean,
        position: Long,
    ) = withContext(Dispatchers.Main) {
        // Correct the position for however long the command spent in flight; ServerClock falls back
        // to the raw value whenever it is not calibrated yet.
        val corrected = session.positionAt(position, isPlaying)
        val player = runCatching { conn.player }.getOrNull() ?: return@withContext
        // A small drift is normal and seeking on every tick would stutter; only a real gap is worth
        // a seek, which is also why the host publishes position with each command.
        if (abs(player.currentPosition - corrected) > SEEK_TOLERANCE_MS) {
            conn.seekTo(corrected)
        }
        // playWhenReady, not isPlaying: a track that is still buffering reports isPlaying=false
        // while already committed to playing, so comparing against it re-issues play() every tick
        // and, worse, lets a stale pause land on a track that was about to start.
        if (isPlaying && !player.playWhenReady) {
            conn.play()
        } else if (!isPlaying && player.playWhenReady) {
            conn.pause()
        }
    }

    /**
     * Loads the host's track and queue.
     *
     * Built straight from the room's own [TrackInfo] — deliberately NOT by resolving metadata from
     * the catalogue first (the old implementation did that via YouTube.queue and it is what made
     // guests stall on a network round trip per track). The stream itself is still resolved
     * locally by MusicService: the room only ever carries ids.
     */
    private suspend fun playTrack(
        conn: PlayerConnection,
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
        Timber.tag(TAG).i("Guest loading %s + %d upcoming", info.id, ordered.size - 1)

        // Dispatchers.Main is mandatory, not tidiness: Media3 throws if the player is touched off
        // the main thread, and this collector runs on the bridge scope (Default).
        withContext(Dispatchers.Main) {
            val items = ordered.map { it.toRoomMediaItem() }
            val player = conn.player
            // The room is the source of the queue while we are a guest: the shouldBlockPlaybackChanges
            // gate in PlayerConnection allows this internal sync through (allowInternalSync).
            conn.allowInternalSync = true
            try {
                player.setMediaItems(items, 0, keepPosition.coerceAtLeast(0L))
                conn.service.queueTitle = "Listen Together"
            } finally {
                conn.allowInternalSync = false
            }
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

    private suspend fun hostSnapshot(conn: PlayerConnection): HostSnapshot? = onMain {
        runCatching {
            val item = conn.player.currentMetadata ?: return@runCatching null
            if (item.id.isBlank()) return@runCatching null
            HostSnapshot(
                id = item.id,
                trackInfo = item.toTrackInfo(conn),
                queue = hostQueueTracks(conn),
                queueTitle = conn.service.queueTitle.orEmpty(),
                position = safePosition(conn),
                playWhenReady = conn.player.playWhenReady,
            )
        }.onFailure { Timber.tag(TAG).w(it, "Could not read the host's player") }.getOrNull()
    }

    private suspend fun publishSnapshot() {
        val conn = connection.value ?: return
        val snap = hostSnapshot(conn) ?: return
        lastPublishedTrackId = snap.id
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
        Timber.tag(TAG).i("Published current state to the room: %s", snap.id)
    }

    /** The host's queue as the room sees it, read off the player timeline. */
    private fun hostQueueTracks(conn: PlayerConnection): List<TrackInfo> =
        runCatching {
            val player = conn.player
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

    private fun MediaMetadata.toTrackInfo(conn: PlayerConnection): TrackInfo =
        TrackInfo(
            id = id,
            title = title,
            artist = artists.joinToString(", ") { it.name },
            album = album?.title.orEmpty(),
            duration = safeDuration(conn),
            thumbnail = thumbnailUrl.orEmpty(),
        )

    private suspend fun publishTrackChangesAsHost() {
        connection
            .filterNotNull()
            .collect { conn ->
                conn.mediaMetadata
                    .filterNotNull()
                    .distinctUntilChanged { old, new -> old.id == new.id }
                    .collect { item ->
                        val state = session.state.value
                        if (!state.inRoom || !state.isHost || applyingRemote) return@collect
                        if (item.id == lastPublishedTrackId) return@collect
                        lastPublishedTrackId = item.id
                        Timber.tag(TAG).i("Host publishing track change: %s", item.id)
                        val (trackInfo, queue, queueTitle) = onMain {
                            Triple(item.toTrackInfo(conn), hostQueueTracks(conn), conn.service.queueTitle.orEmpty())
                        }
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
                                // playWhenReady, not isPlaying: the intent flips the moment the load
                                // path commits, while audible playback waits for the stream URL to
                                // resolve — which can take longer than any reasonable timeout.
                                // Polled, because playWhenReady is a plain property with no flow.
                                while (!onMain { runCatching { conn.player.playWhenReady || conn.player.isPlaying }.getOrDefault(false) }) {
                                    delay(PLAY_SETTLE_POLL_MS)
                                }
                            } != null
                        if (started) {
                            session.sendPlaybackAction(
                                action = PlaybackActions.PLAY,
                                trackId = "",
                                position = onMain { safePosition(conn) },
                                trackInfo = null,
                            )
                        }
                    }
            }
    }

    private suspend fun publishPlayPauseAsHost() {
        connection
            .filterNotNull()
            .collect { conn ->
                conn.isPlaying
                    .collect { isPlaying ->
                        val state = session.state.value
                        if (!state.inRoom || !state.isHost || applyingRemote) return@collect
                        // A host that merely buffers reports isPlaying=false, indistinguishable from a
                        // user pause — and publishing it stops the WHOLE room on one device's hiccup.
                        // playWhenReady carries the intent, so a dip where the two disagree is not news.
                        val (intent, position) = onMain {
                            runCatching { conn.player.playWhenReady to safePosition(conn) }.getOrNull()
                        } ?: return@collect
                        if (isPlaying != intent) return@collect
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
    }

    /**
     * Publishes a seek.
     *
     * Neither `mediaMetadata` nor `isPlaying` changes when the host drags the scrubber, so without
     * this a seek is simply never sent and guests keep playing from wherever they were.
     *
     * A seek is a position that moved further than wall-clock time could account for; ordinary
     * playback advances roughly in step with it. (Upstream detects this off a Progress state class;
     * this app has no equivalent flow on PlayerConnection, so the player listener does it — see
     * [installHostSeekPublisher].)
     */
    fun installHostSeekPublisher(player: androidx.media3.common.Player) {
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int,
            ) {
                val state = session.state.value
                if (!state.inRoom || !state.isHost || applyingRemote) return
                if (reason != androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK) return
                val progress = newPosition.positionMs
                Timber.tag(TAG).i("Host publishing SEEK to %d", progress)
                session.sendPlaybackAction(
                    action = PlaybackActions.SEEK,
                    trackId = "",
                    position = progress,
                    trackInfo = null,
                )
            }
        })
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
                val conn = connection.value ?: return@collect
                // bufferedPercentage, not isPlaying: the barrier asks whether the track is loaded,
                // and playback is exactly what it is holding back.
                val buffered = onMain { runCatching { conn.player.bufferedPercentage }.getOrDefault(0) }
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

    private fun safePosition(conn: PlayerConnection): Long =
        runCatching { conn.player.currentPosition }.getOrDefault(0L)

    private fun safeDuration(conn: PlayerConnection): Long =
        runCatching { conn.player.duration }.getOrDefault(0L).coerceAtLeast(0L)

    private companion object {
        const val PLAY_SETTLE_TIMEOUT_MS = 2_000L

        /** Poll step for the settle wait; playWhenReady has no flow to collect. */
        const val PLAY_SETTLE_POLL_MS = 50L

        /**
         * Metrolist's own hard-sync threshold (`HARD_SYNC_THRESHOLD_MS`). Below it a seek costs
         * more in stutter than it buys in sync; above it the room is audibly apart.
         */
        const val SEEK_TOLERANCE_MS = 750L

        /** How much of the host's timeline rides along with a track change. */
        const val HOST_QUEUE_WINDOW = 25

        const val READY_BUFFER_PERCENT = 5

        /** One re-check before giving the room our ready anyway (the server has its own timeout). */
        const val BARRIER_RECHECK_MS = 2_000L
    }
}
