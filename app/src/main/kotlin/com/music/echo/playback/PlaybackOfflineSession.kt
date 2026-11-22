package iad1tya.echo.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner 2026-11-22: «que Aura detecte que no hay internet por cualquier motivo y pase el reproductor y su cola a modo sin
 * conexión, para que la siguiente canción nunca se quede cargando; y que vuelva a la normalidad cuando regrese la red».
 *
 * An IN-MEMORY state of the player, never written anywhere: it never touches `OfflineModeKey` (registry 284 — that switch
 * is the owner's, persistent, and a wrong detection must not leave it changed). While active, the resolver refuses items
 * that need the network at once (no 15/30 s timeout), onPlayerError goes straight to the next offline-playable song, and
 * the radio / pagination / URL preload do not hit the network.
 *
 * Entering needs EVIDENCE, because declaring "offline" with a working network is the expensive error (it hides the user's
 * online music): the system reports no network at all; or a playback error correctly classified as a network failure while
 * the network is not VALIDATED; or two such failures in a row (VALIDATED alone is not enough either way: VPN, IPv6-only and
 * signed-in captive portals carry traffic and never validate — ronda 9 / row 284); or a stall with evidence.
 *
 * Leaving: the default network becomes VALIDATED, or a fresh network resolve succeeds (or, for a session whose only
 * evidence was "no network at all", any new network appearing). The resolver lets a resolve through
 * (a "probe") as soon as the network changed since the last evidence, or [PROBE_AFTER_MS] after it — so a network that
 * never validates still finds its way back, without any polling: probes only happen when the player needs a song anyway.
 */
internal enum class OfflineSessionReason(val log: String) {
    NO_NETWORK("no_network"),
    UNVALIDATED_FAILURE("unvalidated_failure"),
    REPEATED_FAILURE("repeated_failure"),
    STALL("stall"),
}

/** The pure decisions. Every time and network fact comes in as an argument, so the rules are testable on the JVM. */
internal class OfflineSessionMachine(private val probeAfterMs: Long) {
    var active = false
        private set
    var reason: OfflineSessionReason? = null
        private set

    private var consecutiveFailures = 0
    private var lastEvidenceAtMs: Long? = null
    private var epochAtEvidence = Long.MIN_VALUE

    private fun recentEvidence(nowMs: Long): Boolean =
        lastEvidenceAtMs?.let { nowMs - it < probeAfterMs } == true

    private fun recordEvidence(epoch: Long, nowMs: Long) {
        lastEvidenceAtMs = nowMs
        epochAtEvidence = epoch
    }

    private fun enter(reason: OfflineSessionReason): OfflineSessionReason {
        active = true
        this.reason = reason
        return reason
    }

    /**
     * A playback error classified as a network failure: the network was really tried and failed. Returns the reason when
     * this call started the session, null otherwise (already active, or not enough evidence yet).
     */
    fun onNetworkFailure(systemHasNetwork: Boolean, validated: Boolean, epoch: Long, nowMs: Long): OfflineSessionReason? {
        // "Two in a row" means close together: a failure long ago followed by hours of playback is not a streak.
        if (!recentEvidence(nowMs)) consecutiveFailures = 0
        consecutiveFailures++
        recordEvidence(epoch, nowMs)
        if (active) return null
        val reason = when {
            !systemHasNetwork -> OfflineSessionReason.NO_NETWORK
            !validated -> OfflineSessionReason.UNVALIDATED_FAILURE
            consecutiveFailures >= 2 -> OfflineSessionReason.REPEATED_FAILURE
            else -> null
        } ?: return null
        return enter(reason)
    }

    /** The system itself says there is no network at all (checked live by the caller). */
    fun onNoNetwork(epoch: Long, nowMs: Long): OfflineSessionReason? {
        recordEvidence(epoch, nowMs)
        return if (active) null else enter(OfflineSessionReason.NO_NETWORK)
    }

    /** A stall the caller already confirmed with [stallHasEvidence]. */
    fun onStall(epoch: Long, nowMs: Long): OfflineSessionReason? {
        recordEvidence(epoch, nowMs)
        return if (active) null else enter(OfflineSessionReason.STALL)
    }

    /** A long BUFFERING is only blamed on the network when something else says the network is the problem. */
    fun stallHasEvidence(systemHasNetwork: Boolean, validationLost: Boolean, nowMs: Long): Boolean =
        active || !systemHasNetwork || validationLost || (consecutiveFailures > 0 && recentEvidence(nowMs))

    /** Whether the resolver must refuse a network-only item right now instead of trying (and stalling). */
    fun shouldRejectResolve(systemHasNetwork: Boolean, epoch: Long, nowMs: Long): Boolean =
        active && (!systemHasNetwork || (epoch == epochAtEvidence && recentEvidence(nowMs)))

    /** A fresh resolve over the network worked. True when it ended the session. */
    fun onResolveSucceeded(): Boolean {
        consecutiveFailures = 0
        return exit()
    }

    /** The default network validated. True when it ended the session. */
    fun onValidated(): Boolean = exit()

    /**
     * A new default network appeared (onAvailable). It voids the evidence "there is no network at all", so a NO_NETWORK
     * session ends (a network that never validates — VPN — would otherwise keep it until something resolves). Sessions
     * entered on real failures stay: for them the new network only makes the next resolve a probe.
     */
    fun onNetworkAvailable(): Boolean = if (reason == OfflineSessionReason.NO_NETWORK) exit() else false

    private fun exit(): Boolean {
        if (!active) return false
        active = false
        reason = null
        consecutiveFailures = 0
        lastEvidenceAtMs = null
        epochAtEvidence = Long.MIN_VALUE
        return true
    }
}

/** The process-wide session: the player writes it, the resolver (loader threads) and the UI read it. */
internal object PlaybackOfflineSession {
    /** One rebuffer this long, with evidence, skips to the next offline-playable song. Owner to confirm on device. */
    const val STALL_TIMEOUT_MS = 10_000L

    /** Same value as MusicService's single dead-end re-check (DEAD_END_RECHECK_MS): one probe per window at most. */
    const val PROBE_AFTER_MS = 45_000L

    private val machine = OfflineSessionMachine(PROBE_AFTER_MS)
    private val _active = MutableStateFlow(false)

    /** For the UI (Inicio/Biblioteca/Buscar switch to their offline body while this is true). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    val isActive: Boolean get() = _active.value

    private fun now() = android.os.SystemClock.elapsedRealtime()

    private inline fun <T> locked(block: OfflineSessionMachine.() -> T): T = synchronized(machine) {
        val result = machine.block()
        _active.value = machine.active
        result
    }

    fun onNetworkFailure(systemHasNetwork: Boolean, validated: Boolean, epoch: Long): OfflineSessionReason? =
        locked { onNetworkFailure(systemHasNetwork, validated, epoch, now()) }

    fun onNoNetwork(epoch: Long): OfflineSessionReason? = locked { onNoNetwork(epoch, now()) }

    fun onStall(epoch: Long): OfflineSessionReason? = locked { onStall(epoch, now()) }

    fun stallHasEvidence(systemHasNetwork: Boolean, validationLost: Boolean): Boolean =
        locked { stallHasEvidence(systemHasNetwork, validationLost, now()) }

    fun shouldRejectResolve(systemHasNetwork: Boolean, epoch: Long): Boolean =
        locked { shouldRejectResolve(systemHasNetwork, epoch, now()) }

    fun onResolveSucceeded(): Boolean = locked { onResolveSucceeded() }

    fun onValidated(): Boolean = locked { onValidated() }

    fun onNetworkAvailable(): Boolean = locked { onNetworkAvailable() }
}
