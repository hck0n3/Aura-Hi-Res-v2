package iad1tya.echo.music.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 * GUIDING RULE (review 2026-11-23): a false positive — blocking online music while the internet works — is WORSE than the
 * stall it prevents. So: when in doubt, do not enter, and leave early.
 *
 * Entering needs proof that the DEVICE has no internet: the system reports no network at all (checked live), or TWO
 * connectivity failures (no DNS / could not connect / no route / ENETUNREACH, see PlaybackErrorClassifier.isConnectivityFailure)
 * on two different items within [PlaybackOfflineSession.PROBE_AFTER_MS] (a network change or a network song that plays
 * clears them, session or not), or a stall backed by one of those. Being active is never evidence by
 * itself, and a YouTube-side failure (timeout of the resolver, cipher, 429) never counts.
 *
 * Leaving: any new network (onAvailable); a capabilities change with the network VALIDATED; a network item reaching READY or
 * a fresh network resolve; the "que se ponga solo" switch turned off; the user's «Reintentar en línea»; and a hard cap —
 * [PlaybackOfflineSession.MAX_AGE_MS] after its last real evidence while a network exists. The resolver also lets one
 * resolve through (a "probe") [PlaybackOfflineSession.PROBE_AFTER_MS] after the last evidence. No polling anywhere.
 */
internal enum class OfflineSessionReason(val log: String) {
    NO_NETWORK("no_network"),
    REPEATED_FAILURE("repeated_failure"),
    STALL("stall"),
    AFTER_EXPIRY("after_expiry"),
}

/**
 * The pure decisions. Every time and network fact comes in as an argument, so the rules are testable on the JVM.
 * [reentryAfterExpiryMs] (row 717): the hard cap ending a session is no proof the network works, so for this long after it,
 * on the same network, ONE connectivity failure is enough to come back (0 turns the rule off).
 */
internal class OfflineSessionMachine(
    private val probeAfterMs: Long,
    private val maxAgeMs: Long,
    private val reentryAfterExpiryMs: Long = 0L,
) {
    var active = false
        private set
    var reason: OfflineSessionReason? = null
        private set

    private var consecutiveFailures = 0
    private var lastFailureAtMs: Long? = null
    private var lastFailureItemId: String? = null
    private var lastEvidenceAtMs: Long? = null
    private var epochAtEvidence = Long.MIN_VALUE
    private var expiredAtMs: Long? = null
    private var epochAtExpiry = Long.MIN_VALUE

    private fun recentFailure(nowMs: Long): Boolean =
        lastFailureAtMs?.let { nowMs - it < probeAfterMs } == true

    private fun justExpired(epoch: Long, nowMs: Long): Boolean =
        epoch == epochAtExpiry && expiredAtMs?.let { nowMs - it < reentryAfterExpiryMs } == true

    private fun recordEvidence(epoch: Long, nowMs: Long) {
        lastEvidenceAtMs = nowMs
        epochAtEvidence = epoch
    }

    private fun enter(reason: OfflineSessionReason): OfflineSessionReason {
        active = true
        this.reason = reason
        expiredAtMs = null
        return reason
    }

    private fun clearFailures() {
        consecutiveFailures = 0
        lastFailureAtMs = null
        lastFailureItemId = null
        expiredAtMs = null
    }

    /** Something worth clearing: a session, failures still counting toward one, or a session that only timed out. */
    fun hasEvidence(): Boolean = active || consecutiveFailures > 0 || (reentryAfterExpiryMs > 0 && expiredAtMs != null)

    /**
     * A playback error with a real connectivity cause in its chain, for [itemId]. Returns the reason when this call started
     * the session, null otherwise. One failure never starts it while a network exists: a second one, close to the first and
     * on a DIFFERENT item, is needed — the automatic retry of the same song a few seconds later is the same evidence again.
     */
    fun onConnectivityFailure(itemId: String?, systemHasNetwork: Boolean, epoch: Long, nowMs: Long): OfflineSessionReason? {
        if (!recentFailure(nowMs)) {
            consecutiveFailures = 0
            lastFailureItemId = null
        }
        if (consecutiveFailures == 0 || itemId == null || itemId != lastFailureItemId) consecutiveFailures++
        lastFailureItemId = itemId
        lastFailureAtMs = nowMs
        recordEvidence(epoch, nowMs)
        if (active) return null
        val reason = when {
            !systemHasNetwork -> OfflineSessionReason.NO_NETWORK
            consecutiveFailures >= 2 -> OfflineSessionReason.REPEATED_FAILURE
            justExpired(epoch, nowMs) -> OfflineSessionReason.AFTER_EXPIRY
            else -> null
        } ?: return null
        return enter(reason)
    }

    /** The system itself says there is no network at all — the caller checked it live, at this moment. */
    fun onNoNetwork(epoch: Long, nowMs: Long): OfflineSessionReason? {
        recordEvidence(epoch, nowMs)
        return if (active) null else enter(OfflineSessionReason.NO_NETWORK)
    }

    /**
     * A long BUFFERING is blamed on the network only with independent proof: no network now, or — while the session is OFF —
     * two qualifying connectivity failures a moment ago (never a single one: an isolated refusal of one googlevideo node, or
     * a wifi→data switch, followed by a slow fresh resolve on a low-end phone, is not "no internet"). While the session is ON,
     * the network item that buffers is the probe (or one the resolver let through) and must be allowed to finish.
     */
    fun stallHasEvidence(systemHasNetwork: Boolean, nowMs: Long): Boolean =
        !systemHasNetwork || (!active && consecutiveFailures >= 2 && recentFailure(nowMs))

    /** A stall the caller confirmed with [stallHasEvidence]. Never refreshes the evidence: a stall proves nothing new. */
    fun onStall(systemHasNetwork: Boolean, epoch: Long, nowMs: Long): OfflineSessionReason? = when {
        !systemHasNetwork -> onNoNetwork(epoch, nowMs)
        active -> null
        else -> enter(OfflineSessionReason.STALL)
    }

    /** Whether the resolver must refuse a network-only item right now instead of trying (and stalling). */
    fun shouldRejectResolve(systemHasNetwork: Boolean, epoch: Long, nowMs: Long): Boolean =
        active && (!systemHasNetwork ||
            (epoch == epochAtEvidence && lastEvidenceAtMs?.let { nowMs - it < probeAfterMs } == true))

    /** The network demonstrably works: a fresh resolve, or a network item reached READY. True when it ended the session. */
    fun onNetworkWorked(): Boolean {
        clearFailures()
        return exit()
    }

    /**
     * A new default network appeared, or the network reported VALIDATED: whatever the evidence was, it is void now — the
     * failures counted on the old network too, with or without a session (a failure on a dying wifi plus one on the fresh
     * mobile data is not a streak).
     */
    fun onNetworkChanged(): Boolean {
        clearFailures()
        return exit()
    }

    /** The switch was turned off, or the user asked to retry online. */
    fun forceExit(): Boolean = exit()

    /** Milliseconds until the hard cap, or null when there is no session (or nothing to count from). */
    fun msUntilExpiry(nowMs: Long): Long? {
        if (!active) return null
        val last = lastEvidenceAtMs ?: return 0L
        return (last + maxAgeMs - nowMs).coerceAtLeast(0L)
    }

    /**
     * The hard cap: [maxAgeMs] after the last real evidence the session ends — only while a network exists (with none at
     * all, it is right, and the network's return ends it anyway). True when it ended the session.
     */
    fun expireIfDue(systemHasNetwork: Boolean, nowMs: Long): Boolean {
        if (!active || !systemHasNetwork) return false
        val last = lastEvidenceAtMs
        if (last != null && nowMs - last < maxAgeMs) return false
        val epoch = epochAtEvidence
        if (!exit()) return false
        expiredAtMs = nowMs
        epochAtExpiry = epoch
        return true
    }

    private fun exit(): Boolean {
        if (!active) return false
        active = false
        reason = null
        clearFailures()
        lastEvidenceAtMs = null
        epochAtEvidence = Long.MIN_VALUE
        return true
    }
}

/** The process-wide session: the player writes it, the resolver (loader threads) and the UI read it. */
internal object PlaybackOfflineSession {
    /** One rebuffer this long, with independent evidence, skips to the next offline-playable song. Owner to confirm. */
    const val STALL_TIMEOUT_MS = 10_000L

    /** Same value as MusicService's single dead-end re-check (DEAD_END_RECHECK_MS): one probe per window at most. */
    const val PROBE_AFTER_MS = 45_000L

    /** Hard cap: the session never outlives its last real evidence by more than this while a network exists. */
    const val MAX_AGE_MS = 120_000L

    /** Row 717: after the hard cap, one connectivity failure on the same network brings the session back. Chosen, not measured. */
    const val REENTRY_AFTER_EXPIRY_MS = 10 * 60_000L

    private val machine = OfflineSessionMachine(PROBE_AFTER_MS, MAX_AGE_MS, REENTRY_AFTER_EXPIRY_MS)
    private val _active = MutableStateFlow(false)

    /** For the UI (Inicio/Biblioteca/Buscar switch to their offline body while this is true). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    val isActive: Boolean get() = _active.value

    private val _userRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted when the user's «Reintentar en línea» ended the session (MusicService re-prepares a waiting queue). */
    val userRetries: SharedFlow<Unit> = _userRetries.asSharedFlow()

    private fun now() = android.os.SystemClock.elapsedRealtime()

    private inline fun <T> locked(block: (OfflineSessionMachine) -> T): T = synchronized(machine) {
        val result = block(machine)
        _active.value = machine.active
        result
    }

    fun onConnectivityFailure(itemId: String?, systemHasNetwork: Boolean, epoch: Long): OfflineSessionReason? =
        locked { it.onConnectivityFailure(itemId, systemHasNetwork, epoch, now()) }

    fun hasEvidence(): Boolean = locked { it.hasEvidence() }

    fun onNoNetwork(epoch: Long): OfflineSessionReason? = locked { it.onNoNetwork(epoch, now()) }

    fun stallHasEvidence(systemHasNetwork: Boolean): Boolean = locked { it.stallHasEvidence(systemHasNetwork, now()) }

    fun onStall(systemHasNetwork: Boolean, epoch: Long): OfflineSessionReason? =
        locked { it.onStall(systemHasNetwork, epoch, now()) }

    fun shouldRejectResolve(systemHasNetwork: Boolean, epoch: Long): Boolean =
        locked { it.shouldRejectResolve(systemHasNetwork, epoch, now()) }

    fun onNetworkWorked(): Boolean = locked { it.onNetworkWorked() }

    fun onNetworkChanged(): Boolean = locked { it.onNetworkChanged() }

    fun forceExit(): Boolean = locked { it.forceExit() }

    fun msUntilExpiry(): Long? = locked { it.msUntilExpiry(now()) }

    fun expireIfDue(systemHasNetwork: Boolean): Boolean = locked { it.expireIfDue(systemHasNetwork, now()) }

    /** «Reintentar en línea» (offline banner). Ends the session at once, even with no MusicService running. */
    fun requestRetryOnline() {
        if (forceExit()) _userRetries.tryEmit(Unit)
    }
}
