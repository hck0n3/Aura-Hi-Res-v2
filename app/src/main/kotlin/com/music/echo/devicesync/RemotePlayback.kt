package iad1tya.echo.music.devicesync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Row 534/537 — the other device's playback, shown and controlled by THIS device's own player, the way Cast and Spotify Connect do
 * (owner 2026-10-30: *«que arriba de la portada aparezca continuar desde aquí… que en la misma barra de tiempo vaya avanzando… y que
 * los controles de mi reproductor controlen a donde se está reproduciendo, para que no haya redundancia, como lo hace Spotify»*).
 *
 * The player already knows how to be a remote: for Cast it swaps its position, duration and play/pause state for the cast ones and
 * routes the transport buttons to the cast device. This object offers the SAME shape ([position], [duration], [playing], and
 * commands), so the player treats "playing on another of my devices" exactly like "casting", with no second set of controls.
 *
 * It is active only while another paired device is playing (or was just controlled from here) and THIS device is silent; the engine
 * decides ([shouldBeActive]) and keeps the flows moving. No flow runs while it is inactive (AGENTS rule 7).
 */
object RemotePlayback {
    /** How long a PAUSED remote stays «ours» after the last order sent from here, so it can be resumed from here. */
    const val CONTROL_HOLD_MS = 10 * 60_000L

    /** A playing remote must have spoken within this long (it heartbeats every 20 s); three missed beats and it is gone. */
    const val FRESH_MS = 70_000L

    /** The device being shown and controlled, with its last published state; null = this device plays for itself. */
    val target = MutableStateFlow<NowState?>(null)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val playing = MutableStateFlow(false)

    /** The other device's app volume as the slider here shows it (0..1). Follows what it publishes unless the user is dragging. */
    val volume = MutableStateFlow(1f)

    /** The other device's shuffle as the switch here shows it; follows what it publishes. */
    val shuffle = MutableStateFlow(false)

    @Volatile
    private var lastControlAt = 0L

    /** When (device clock) this device last sent an order to the other one. */
    val lastControlAtMs: Long get() = lastControlAt

    @Volatile
    private var lastVolumeAt = 0L
    private var lastVolumeSentAt = 0L
    private var volumeTrailing: Job? = null
    private val volumeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The volume slider while another device plays: sends at most ~8 times a second while dragging and always the last value.
     * Returns false when nothing is being controlled (the caller then moves its own volume).
     */
    fun setVolume(level: Float): Boolean {
        val t = target.value ?: return false
        val v = level.coerceIn(0f, 1f)
        val now = System.currentTimeMillis()
        lastControlAt = now
        lastVolumeAt = now
        volume.value = v
        volumeTrailing?.cancel()
        if (now - lastVolumeSentAt >= VOLUME_SEND_EVERY_MS) {
            lastVolumeSentAt = now
            DeviceSyncHub.controller?.control(t.deviceId, "volume", levelPermille = (v * 1000).toInt())
        } else {
            volumeTrailing = volumeScope.launch {
                delay(VOLUME_SEND_EVERY_MS)
                lastVolumeSentAt = System.currentTimeMillis()
                DeviceSyncHub.controller?.control(t.deviceId, "volume", levelPermille = (v * 1000).toInt())
            }
        }
        return true
    }

    private const val VOLUME_SEND_EVERY_MS = 120L

    val active: Boolean get() = target.value != null

    fun controllingRecently(nowMs: Long = System.currentTimeMillis()): Boolean =
        lastControlAt > 0L && nowMs - lastControlAt < CONTROL_HOLD_MS

    // ── commands (what the player's buttons call while [active]) ─────────────────────────────────────────────────────────────

    fun play(): Boolean = send("play", optimisticPlaying = true)
    fun pause(): Boolean = send("pause", optimisticPlaying = false)
    fun toggle(): Boolean = if (playing.value) pause() else play()
    fun next(): Boolean = send("next")
    fun previous(): Boolean = send("prev")

    /** «No me gusta» / «Me gusta» on the song shown here, done by the device that really plays it (see DeviceSyncEngine.runCommand). */
    /** The shuffle switch while another device plays: THAT device shuffles (false = nothing is controlled, the caller shuffles its own). */
    fun setShuffle(on: Boolean): Boolean {
        val t = target.value ?: return false
        lastControlAt = System.currentTimeMillis()
        shuffle.value = on
        DeviceSyncHub.controller?.control(t.deviceId, "shuffle", flag = on)
        return true
    }

    /**
     * The one door of every shuffle switch (row 577): while another device is controlled the switch drives THAT device, from its own
     * state; otherwise it flips the local player through [setLocal]. [localCurrent] is what the switch shows when nothing is controlled.
     */
    fun toggleShuffle(localCurrent: Boolean, setLocal: (Boolean) -> Unit) {
        if (active) setShuffle(!shuffle.value) else setLocal(!localCurrent)
    }

    fun dislike(): Boolean = sendForCurrentSong("dislike")
    fun like(): Boolean = sendForCurrentSong("like")

    /**
     * Row 564 — a row tapped in the queue list while another device is controlled: THAT device jumps to the song and plays it, as
     * the buttons already do. Before, the tap sought this device's own player and this device started playing — the remote was
     * left behind (owner 2026-10-31: «si cambio una canción desde mi cel y estoy mandando a la tablet se quita la conexión y me
     * empieza a reproducir desde el cel»).
     */
    fun playItem(trackId: String): Boolean {
        val t = target.value ?: return false
        if (trackId.isBlank()) return false
        lastControlAt = System.currentTimeMillis()
        playing.value = true
        DeviceSyncHub.controller?.control(t.deviceId, "playitem", trackId = trackId)
        return true
    }

    private fun sendForCurrentSong(command: String): Boolean {
        val t = target.value ?: return false
        val id = t.current?.id ?: return false
        lastControlAt = System.currentTimeMillis()
        DeviceSyncHub.controller?.control(t.deviceId, command, trackId = id)
        return true
    }

    fun seekTo(positionMs: Long): Boolean {
        val t = target.value ?: return false
        lastControlAt = System.currentTimeMillis()
        position.value = positionMs.coerceAtLeast(0L)
        DeviceSyncHub.controller?.control(t.deviceId, "seek", positionMs)
        return true
    }

    /** «Continuar aquí»: bring the playback to THIS device from the same second, and the other one pauses. */
    fun continueHere(): Boolean {
        val t = target.value ?: return false
        lastControlAt = 0L
        DeviceSyncHub.controller?.takeOverFrom(t.deviceId)
        return true
    }

    private fun send(command: String, optimisticPlaying: Boolean? = null): Boolean {
        val t = target.value ?: return false
        lastControlAt = System.currentTimeMillis()
        // The button answers at once; the other device's real state arrives within a second and overwrites this.
        if (optimisticPlaying != null) playing.value = optimisticPlaying
        DeviceSyncHub.controller?.control(t.deviceId, command)
        return true
    }

    // ── state, fed by the engine ─────────────────────────────────────────────────────────────────────────────────────────────

    /** Make [state] (or nothing) the remote being shown. Keeps the optimistic play state while the user is mid-command. */
    fun update(state: NowState?, serverNowMs: Long) {
        target.value = state
        if (state == null) {
            playing.value = false
            position.value = 0L
            duration.value = 0L
            return
        }
        playing.value = state.playing
        duration.value = state.durationMs
        state.shuffle?.let { shuffle.value = it }
        // Do not fight the slider while the user is moving it: the device's own report catches up a couple of seconds later.
        if (state.volume >= 0f && System.currentTimeMillis() - lastVolumeAt > 2_500L) volume.value = state.volume
        position.value = state.estimatedPositionMs(serverNowMs).coerceIn(0L, if (state.durationMs > 0) state.durationMs else Long.MAX_VALUE)
    }

    /** One tick of the timeline (the engine calls it once a second, only while the remote plays). */
    fun tick(serverNowMs: Long) {
        val s = target.value ?: return
        if (!playing.value) return
        position.value = s.estimatedPositionMs(serverNowMs).coerceIn(0L, if (s.durationMs > 0) s.durationMs else Long.MAX_VALUE)
    }

    /**
     * Pure (row 563): true when showing the other device would REPLACE the song this one was just playing with an old one. That is only the
     * case for a remote that is not playing (it is shown only because this device controlled it recently, so its state is stale), while this
     * device is silent after playing within [graceMs] and is not already controlling it. A remote that is PLAYING is always shown.
     */
    fun holdBack(localPlaying: Boolean, remotePlaying: Boolean, remoteActive: Boolean, sinceLocalPlayedMs: Long, graceMs: Long): Boolean =
        !localPlaying && !remotePlaying && !remoteActive && sinceLocalPlayedMs in 0 until graceMs

    /**
     * Row 595 (owner 2026-10-31: «cuando le di pausa a los dos, los dos dispositivos volvieron a la cola anterior… se puso otra canción en
     * pausa»): the other device's queue replaces this device's own only while what it published is NEWER than the last order sent from here
     * ([lastControlServerMs], the same moment on the room's clock) or while it is actually playing. A paused report published before our last
     * order is the old queue; mirroring it swapped the queues of two paused devices. Pure.
     */
    fun shouldMirrorQueue(remote: NowState, lastControlServerMs: Long, slackMs: Long = MIRROR_SLACK_MS): Boolean =
        remote.playing || lastControlServerMs <= 0L || remote.atMs + slackMs >= lastControlServerMs

    const val MIRROR_SLACK_MS = 1_500L

    /** An order from another device that arrived this recently explains why this device just started playing: it is not a user's tap here. */
    const val COMMAND_GRACE_MS = 5_000L

    /**
     * Row 594 (owner 2026-10-31: «si está en remoto solo uno de los dos dispositivos debe mandar audio y obedecer al otro»): the device the
     * user just started playing on is the one that sounds. True when THIS device has just started playing by its own user's action while
     * ANOTHER device was audibly playing (fresh report), so the other must be told to pause. Orders that arrived from the other device a
     * moment ago ([msSinceOrderHere] under [COMMAND_GRACE_MS]: «continuar aquí», a queue sent here, a play) are not a tap and never trigger
     * it, so two devices cannot pause each other. Pure.
     */
    fun shouldPauseOther(
        localStartedNow: Boolean,
        remote: NowState?,
        thisDeviceId: String,
        handoffEnabled: Boolean,
        connected: Boolean,
        msSinceOrderHere: Long,
        serverNowMs: Long,
    ): Boolean =
        localStartedNow && handoffEnabled && connected && remote != null &&
            remote.deviceId.isNotBlank() && remote.deviceId != thisDeviceId && remote.playing &&
            serverNowMs - remote.atMs <= FRESH_MS && msSinceOrderHere > COMMAND_GRACE_MS

    /**
     * Pure: should THIS device show and control [remote]? Another device, that is playing and recently heard — or paused but just
     * controlled from here — while this device is silent, connected, and the user allows handing over.
     */
    fun shouldBeActive(
        remote: NowState?,
        thisDeviceId: String,
        handoffEnabled: Boolean,
        connected: Boolean,
        localPlaying: Boolean,
        controllingRecently: Boolean,
        serverNowMs: Long,
    ): Boolean {
        if (remote == null || !handoffEnabled || !connected || localPlaying) return false
        if (remote.deviceId.isBlank() || remote.deviceId == thisDeviceId || remote.current == null) return false
        return if (remote.playing) serverNowMs - remote.atMs <= FRESH_MS else controllingRecently
    }
}
