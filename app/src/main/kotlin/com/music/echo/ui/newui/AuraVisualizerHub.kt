package iad1tya.echo.music.ui.newui

import android.media.audiofx.Visualizer
import android.os.Build
import androidx.media3.common.C
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * The ONE [Visualizer] owner for the whole process (HALLAZGO-048, RONDA 3).
 *
 * Android allows a single live [Visualizer] per audio session. Two screens need one — the player
 * ground rhythm ([rememberAuraRhythmLevel]) and the EQ FFT meter — and the first design gave each
 * its own instance plus an exclusivity handoff (`AuraVisualizerExclusive`). Every trip to the EQ
 * screen and back ran up to FOUR binder attach/release cycles against AudioFlinger on the live
 * audio chain: the rhythm capture was torn down, the meter built its own 350 ms later, and on the
 * way out the meter released synchronously on the main thread while the rhythm rebuilt — the
 * audible cuts the owner reported when entering/leaving the equalizer. It was the exact class of
 * bug HALLAZGO-027 fixed for the bottom sheet, re-introduced by a second consumer that never
 * inherited the keep-alive pattern.
 *
 * The fix is the FASE 1 lesson applied process-wide: **capture lives as long as playback exists,
 * not as long as a screen exists.** Consumers register a [Consumer] flag; the hub holds exactly
 * one [Visualizer] and never attaches or removes it while at least one consumer stays active.
 * Entering/leaving the EQ flips a boolean — zero binder operations, zero effect churn.
 *
 * HALLAZGO-058: the consumer flags are composition-scoped, and the player composable lives in TWO
 * composition positions (MainActivity's bottomBar branch and its rail/wide branch — `showRail`
 * flips with orientation): rotating the phone disposes one AuraPlayer and composes the other, so
 * the rhythm consumer flips off and back on within one frame. The immediate teardown that ran on
 * "zero consumers" released and re-attached the Visualizer on the live audio chain once per
 * rotation — the audible micro-cuts the owner reported (10 attach cycles in 12 s in the BETA-014
 * log). The teardown is now deferred by [TEARDOWN_GRACE_MS]; a consumer returning inside the
 * window cancels it, so a branch swap costs zero binder operations.
 *
 * Both callbacks are always enabled while the capture is attached: switching listener modes would
 * need a `setEnabled(false)`/`setEnabled(true)` cycle, which IS effect churn. The FFT processing
 * (the only non-trivial per-callback work) is gated on [Consumer.EQ_FFT], so with only the rhythm
 * active the FFT callback returns immediately. Observation-only: nothing here touches the audio
 * path, Superpowered or the EQ engine.
 *
 * All [Visualizer] construction and release happens on the process-lifetime IO [scope] under
 * [mutex] — never on the main thread and never mid-composition (HALLAZGO-027).
 */
object AuraVisualizerHub {

    /** The two capture consumers. At most one [Visualizer] exists regardless of how many are on. */
    enum class Consumer { RHYTHM, EQ_FFT }

    /** One processed FFT frame for the EQ meter: log-spaced bars + peak, both normalized 0..1. */
    data class FftFrame(
        val bars: FloatArray,
        val peak: Float,
        val atMs: Long,
    )

    /** Process-lifetime scope: a release started from any thread must run to completion. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializes every attach/teardown so consumer flips can never interleave two lifecycles. */
    private val mutex = Mutex()

    @Volatile private var rhythmActive = false
    @Volatile private var eqFftActive = false
    @Volatile private var eqBarCount = DEFAULT_FFT_BAR_COUNT

    /** Last valid session any consumer passed; rebinds target it, never a stale dispose value. */
    @Volatile private var lastKnownSession: Int = C.AUDIO_SESSION_ID_UNSET

    private var visualizer: Visualizer? = null
    private var boundSession: Int = C.AUDIO_SESSION_ID_UNSET

    /** HALLAZGO-058: pending deferred teardown; a consumer returning inside the grace cancels it. */
    private var teardownJob: Job? = null

    private val _rhythmLevel = MutableStateFlow(0f)
    /** Waveform RMS 0..1 for the player ground. Written at the capture rate, read per frame. */
    val rhythmLevel: StateFlow<Float> = _rhythmLevel.asStateFlow()

    private val _fftFrame = MutableStateFlow<FftFrame?>(null)
    /** Latest processed FFT frame, or null when nothing was captured yet. */
    val fftFrame: StateFlow<FftFrame?> = _fftFrame.asStateFlow()

    private val _attached = MutableStateFlow(false)
    /** True while a [Visualizer] is live — the meter's "Sin señal" state reads this. */
    val attached: StateFlow<Boolean> = _attached.asStateFlow()

    private val _needsPermission = MutableStateFlow(false)
    /**
     * True when the capture is blocked ONLY by the missing RECORD_AUDIO permission (Android 14+
     * requirement, HALLAZGO-053). The EQ meter shows a "grant microphone" hint instead of the
     * generic "Sin señal", and requesting the permission there re-arms the capture via
     * [onPermissionGranted].
     */
    val needsPermission: StateFlow<Boolean> = _needsPermission.asStateFlow()

    private val lastCallbackAt = AtomicLong(0L)
    @Volatile private var lastRebindAt = 0L

    /**
     * HALLAZGO-055: consecutive failed attach CYCLES (a cycle = session attempt + global
     * fallback). Some OEM frameworks refuse the Visualizer outright (owner's S26 Ultra
     * fails even session 0 with error -3), so an unbounded 10 s-cadenced retry is pure
     * binder churn + log spam + thermal load that surfaces minutes later as jank. After
     * [MAX_CONSECUTIVE_ATTACH_FAILURES] the watchdog path stops re-arming; the gate reopens
     * on a new audio session or a permission grant.
     */
    @Volatile private var consecutiveAttachFailures = 0

    /**
     * Wired by the Application class to the live RECORD_AUDIO check. Defaults to "granted" so
     * pre-wiring and pre-Android-14 behavior is unchanged; on 14+ a wrong "true" only reproduces
     * today's attach failure, never corrupts the audio chain.
     */
    var recordAudioGrantedProvider: () -> Boolean = { true }

    private const val TAG = "AuraVisualizerHub"

    /** A video/track swap can silently kill the capture; wait for real silence before re-arming. */
    private const val SILENCE_BEFORE_REBIND_MS = 4_000L

    /** Re-attach is expensive on the chain; at most one per window. */
    private const val REBIND_THROTTLE_MS = 10_000L

    /** HALLAZGO-055: failed attach cycles after which the watchdog path stops re-arming. */
    private const val MAX_CONSECUTIVE_ATTACH_FAILURES = 3

    /**
     * HALLAZGO-058: how long the capture survives the last consumer leaving. The orientation
     * branch swap disposes and re-registers the rhythm consumer within one frame (~16-100 ms);
     * the window is comfortably wider than that, and short enough that a real goodbye (player
     * torn down, new-UI flag flipped off) still releases the effect promptly.
     */
    private const val TEARDOWN_GRACE_MS = 1_500L

    /** Bars the EQ meter draws; also the default when a consumer activates without saying. */
    const val DEFAULT_FFT_BAR_COUNT = 24

    /**
     * Activates or deactivates a consumer. Idempotent; safe from any thread and from several
     * compositions at once. [sessionId] feeds [lastKnownSession] but a stale value on deactivation
     * never rebinds — reconciliation only attaches when the capture is missing entirely.
     */
    fun setConsumer(
        consumer: Consumer,
        active: Boolean,
        sessionId: Int,
        fftBarCount: Int = DEFAULT_FFT_BAR_COUNT,
    ) {
        when (consumer) {
            Consumer.RHYTHM -> rhythmActive = active
            Consumer.EQ_FFT -> {
                eqFftActive = active
                if (active) eqBarCount = fftBarCount
            }
        }
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0) {
            if (sessionId != lastKnownSession) {
                // HALLAZGO-055: a different session is a fresh chance — the old failures were
                // against another AudioFlinger client, so the cap reopens.
                consecutiveAttachFailures = 0
            }
            lastKnownSession = sessionId
        }
        scope.launch { mutex.withLock { reconcileLocked() } }
    }

    /**
     * Rebuilds the capture after it went silently dead (video swap on the same session id), or
     * builds it after the FIRST attach never succeeded. The caller detects silence; the throttle
     * and the freshness check here keep a healthy capture from ever being re-attached.
     *
     * HALLAZGO-053: the original guard was `visualizer != null && consumer active` — a no-op when
     * the initial attach failed (track not created yet, or an OEM transient), because nothing
     * else ever retried: the meter's watchdog calls here every 1.2 s, the consumer flips only run
     * on session/enabled changes, so the meter sat on "Sin señal" until the EQ screen was
     * re-entered. A failed attach now re-arms on the same throttled path as a dead one.
     */
    fun requestRebind() {
        // HALLAZGO-053: while the capture is blocked ONLY by the missing permission, retrying is
        // pure churn — the owner's BETA-012 log accumulated 367 failed attaches this way. The
        // gate re-opens on its own once recordAudioGrantedProvider() flips (onPermissionGranted
        // re-arms immediately; a grant in system settings is picked up by the next watchdog call
        // that passes the throttle).
        if (_needsPermission.value && !recordAudioGrantedProvider()) return
        // HALLAZGO-055: the framework already proved it will not give us a capture; keep
        // hammering it every 10 s and the only output is binder churn, log spam and heat.
        if (!visualizerRebindAllowed(consecutiveAttachFailures, MAX_CONSECUTIVE_ATTACH_FAILURES)) {
            logRebindSuspendedOnce()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastRebindAt < REBIND_THROTTLE_MS) return
        if (now - lastCallbackAt.get() < SILENCE_BEFORE_REBIND_MS) return
        lastRebindAt = now
        scope.launch {
            mutex.withLock {
                if (!rhythmActive && !eqFftActive) return@withLock
                if (visualizer != null) teardownLocked()
                attachLocked()
            }
        }
    }

    /**
     * The user just granted RECORD_AUDIO from the EQ screen: re-arm right now, bypassing the
     * rebind throttle (which may have stamped a failed attempt seconds earlier).
     */
    fun onPermissionGranted() {
        consecutiveAttachFailures = 0
        scope.launch {
            mutex.withLock {
                if (!rhythmActive && !eqFftActive) return@withLock
                teardownJob?.cancel()
                teardownJob = null
                if (visualizer == null) attachLocked()
            }
        }
    }

    private fun reconcileLocked() {
        if (!rhythmActive && !eqFftActive) {
            scheduleTeardownLocked()
            return
        }
        teardownJob?.cancel()
        teardownJob = null
        if (visualizer != null) {
            // HALLAZGO-058: a consumer that came back with a DIFFERENT audio session (the
            // crossfade dual-player swap changes it) must rebind now — the capture kept alive by
            // the grace window is bound to the old session and would silently observe a dead
            // chain until the watchdog noticed four seconds later.
            val target = if (lastKnownSession <= 0) 0 else lastKnownSession
            if (visualizerSessionRebindNeeded(boundSession, target)) {
                teardownLocked()
                attachLocked()
            }
            return
        }
        attachLocked()
    }

    /**
     * HALLAZGO-058: defers the teardown of the last-consumer-left case by [TEARDOWN_GRACE_MS].
     * The player composable swaps composition position on rotation (bottomBar branch vs rail/wide
     * branch), so the rhythm consumer flips off and back on within one frame; tearing down
     * immediately released and re-attached the Visualizer on the live audio chain once per
     * rotation = the audible micro-cuts of HALLAZGO-058. A consumer returning inside the window
     * cancels the job in [reconcileLocked], so the swap costs zero binder operations; if nobody
     * returns, the capture is released a beat later. The re-check under [mutex] is the guard:
     * only a job that survives to the end of the window AND still sees zero consumers tears down.
     */
    private fun scheduleTeardownLocked() {
        if (visualizer == null) return
        if (teardownJob?.isActive == true) return
        teardownJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            delay(TEARDOWN_GRACE_MS)
            mutex.withLock {
                if (visualizerTeardownDue(
                        elapsedMs = System.currentTimeMillis() - startedAt,
                        graceMs = TEARDOWN_GRACE_MS,
                        anyConsumerActive = rhythmActive || eqFftActive,
                    )
                ) {
                    teardownLocked()
                }
            }
        }
    }

    private fun teardownLocked() {
        teardownJob?.cancel()
        teardownJob = null
        val victim = visualizer
        visualizer = null
        boundSession = C.AUDIO_SESSION_ID_UNSET
        _attached.value = false
        _rhythmLevel.value = 0f
        _fftFrame.value = null
        if (victim != null) {
            // Already off the main thread (the scope is IO): the binder teardown never blocks UI,
            // and running it to completion here is safe because the capture is observation-only.
            runCatching {
                victim.setEnabled(false)
                victim.release()
            }
        }
    }

    private fun attachLocked() {
        // HALLAZGO-053 root cause: Android 14+ refuses Visualizer init (ERROR_NO_INIT, -3)
        // without RECORD_AUDIO — constructing one here would throw for BOTH the session and the
        // global fallback. Gate before building, surface the reason, and skip the throw storm.
        if (!visualizerCaptureAllowed(Build.VERSION.SDK_INT, recordAudioGrantedProvider())) {
            visualizer = null
            boundSession = C.AUDIO_SESSION_ID_UNSET
            _attached.value = false
            if (!_needsPermission.value) {
                _needsPermission.value = true
                Timber.tag(TAG).w(
                    "Visualizer attach skipped — RECORD_AUDIO not granted (mandatory on Android 14+)",
                )
            }
            return
        }
        _needsPermission.value = false
        val target = if (lastKnownSession <= 0) 0 else lastKnownSession
        val v = tryAttach(target)
        if (v == null && target != 0) {
            // The old EQ retry loop ended here: the global mix captures whatever plays on the
            // device, so the meter degrades to it instead of showing "Sin señal". NOTE (#53): on
            // Android 14+ the global fallback needs RECORD_AUDIO granted, which the app only asks
            // for voice search — so a failed session attach usually ends with no capture at all.
            val fallback = tryAttach(0)
            visualizer = fallback
            boundSession = if (fallback != null) 0 else C.AUDIO_SESSION_ID_UNSET
            _attached.value = fallback != null
            logAttachResult(fallback != null, boundSession)
            recordAttachOutcome(fallback != null)
            return
        }
        visualizer = v
        boundSession = if (v != null) target else C.AUDIO_SESSION_ID_UNSET
        _attached.value = v != null
        logAttachResult(v != null, boundSession)
        recordAttachOutcome(v != null)
    }

    /** HALLAZGO-055: feeds the rebind cap; a success clears it, a full failure grows it. */
    private fun recordAttachOutcome(attached: Boolean) {
        consecutiveAttachFailures = if (attached) 0 else consecutiveAttachFailures + 1
    }

    private val rebindSuspendedLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /** One line per process: the meter is dark by framework decision, not by silence. */
    private fun logRebindSuspendedOnce() {
        if (rebindSuspendedLogged.compareAndSet(false, true)) {
            Timber.tag(TAG).w(
                "Visualizer rebind suspended after %d consecutive failed attaches — " +
                    "retries resume on a new audio session or permission grant",
                MAX_CONSECUTIVE_ATTACH_FAILURES,
            )
        }
    }

    /** #53: the attach outcome is the single fact the "Sin señal" diagnosis hinges on. */
    private fun logAttachResult(attached: Boolean, session: Int) {
        if (attached) {
            Timber.tag(TAG).i("Visualizer attached (session=%d)", session)
        } else {
            Timber.tag(TAG).w(
                "Visualizer attach FAILED (session and global fallback) — meter will show 'Sin señal'",
            )
        }
    }

    private fun tryAttach(session: Int): Visualizer? = runCatching {
        Visualizer(session).apply {
            // Max capture size: the EQ meter needs the bin resolution; the waveform RMS is happy
            // with any size. One configuration for both consumers, set once at attach.
            captureSize = Visualizer.getCaptureSizeRange()[1]
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                runCatching { setScalingMode(Visualizer.SCALING_MODE_NORMALIZED) }
            }
            val maxRate = Visualizer.getMaxCaptureRate()
            val rate = min(20_000, (maxRate / 2).coerceAtLeast(4_000))
            setDataCaptureListener(captureListener, rate, true, true)
            setEnabled(true)
        }
    }.onFailure { error ->
        // #53: attach failures used to vanish inside getOrNull() — "el FFT no sirve" on the
        // owner's S26 Ultra shipped with zero evidence in app.log. Session id + exception class
        // carry no user data; they are what the next diagnosis round needs.
        Timber.tag(TAG).w(error, "Visualizer attach failed (session=%d)", session)
    }.getOrNull()

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer?,
            waveform: ByteArray?,
            samplingRate: Int,
        ) {
            lastCallbackAt.set(System.currentTimeMillis())
            if (!rhythmActive) return
            if (waveform == null || waveform.isEmpty()) return
            var sum = 0.0
            for (b in waveform) {
                val centered = (b.toInt() and 0xFF) - 128
                sum += centered * centered
            }
            val rms = sqrt(sum / waveform.size).toFloat() / 128f
            _rhythmLevel.value = rms.coerceIn(0f, 1f)
        }

        override fun onFftDataCapture(
            visualizer: Visualizer?,
            fft: ByteArray?,
            samplingRate: Int,
        ) {
            lastCallbackAt.set(System.currentTimeMillis())
            // The FFT math runs ONLY while the EQ meter is on screen — with just the rhythm
            // active this callback is a timestamp update and two flag reads.
            if (!eqFftActive) return
            if (fft == null || fft.size < 4) return
            val frame = computeEqFftBars(fft, eqBarCount) ?: return
            _fftFrame.value = frame.copy(atMs = System.currentTimeMillis())
        }
    }
}

/**
 * Android 14 (API 34) behavior change: the [Visualizer] API refuses to initialize —
 * `Cannot initialize Visualizer engine, error: -3` (ERROR_NO_INIT) — unless RECORD_AUDIO is
 * granted. This applies to session-attached and global captures alike, and the capture is
 * observation-only (the app reads its own playback; the microphone is never recorded). Pure and
 * JVM-testable: the hub passes the live SDK int and permission state.
 *
 * HALLAZGO-053: the app only ever requested the permission for voice search, so on the owner's
 * targetSdk-36 device every attach threw and the EQ meter sat on "Sin señal".
 */
fun visualizerCaptureAllowed(sdkInt: Int, recordAudioGranted: Boolean): Boolean =
    sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || recordAudioGranted

/**
 * HALLAZGO-055: whether the watchdog may re-arm the capture after [consecutiveFailures]
 * failed attach cycles. Pure and JVM-testable; the hub passes its live counter and cap.
 * The cap reopens on a new audio session or a permission grant, never on time alone —
 * time-based retry against a framework that refuses the Visualizer is exactly the storm
 * that filled the owner's BETA-012 log with failed attaches.
 */
fun visualizerRebindAllowed(consecutiveFailures: Int, maxFailures: Int): Boolean =
    consecutiveFailures < maxFailures

/**
 * HALLAZGO-058: the deferred-teardown decision. The hub keeps the [android.media.audiofx.Visualizer]
 * alive for a grace window after the last consumer leaves, because the player composable swaps
 * composition position on rotation (off and back on within one frame); tearing down immediately
 * released and re-attached the effect on the live audio chain once per rotation — the audible
 * micro-cuts the owner reported (10 attach cycles in 12 s in the BETA-014 log). The teardown is
 * due only once the window has fully elapsed AND no consumer came back inside it. Pure and
 * JVM-testable; the hub passes its live elapsed time, window and consumer state.
 */
fun visualizerTeardownDue(elapsedMs: Long, graceMs: Long, anyConsumerActive: Boolean): Boolean =
    elapsedMs >= graceMs && !anyConsumerActive

/**
 * HALLAZGO-058: a capture kept alive by the grace window stays bound to the session it was built
 * for. When a consumer re-activates with a different session (the crossfade dual-player swap
 * changes it), the capture must be rebuilt against the new one instead of silently observing a
 * dead session until the watchdog notices. Pure and JVM-testable.
 */
fun visualizerSessionRebindNeeded(boundSession: Int, targetSession: Int): Boolean =
    boundSession != targetSession

/**
 * Turns a raw [Visualizer] FFT payload into the EQ meter's log-spaced bars.
 *
 * Pure and deterministic — the exact binning the meter shipped with (HALLAZGO-048 extracted it
 * verbatim from the capture listener so the hub refactor could not drift the look): quadratic-log
 * bin spacing across [barCount] bars, DC skipped, magnitudes normalized by 48 (real-content FFT
 * mags sit well below 128), everything clamped to 0..1.
 *
 * @return null when the payload is too short to hold a single complex bin.
 */
fun computeEqFftBars(fft: ByteArray, barCount: Int): AuraVisualizerHub.FftFrame? {
    if (fft.size < 4 || barCount <= 0) return null
    val binCount = fft.size / 2
    var peak = 0f
    val bars = FloatArray(barCount)
    for (b in 0 until barCount) {
        val t0 = b.toDouble() / barCount
        val t1 = (b + 1).toDouble() / barCount
        // Log-ish spacing across bins (skip DC at 0).
        val start = (1 + t0 * t0 * (binCount - 2)).toInt().coerceIn(1, binCount - 1)
        val end = (1 + t1 * t1 * (binCount - 2)).toInt().coerceIn(start + 1, binCount)
        var sum = 0.0
        var count = 0
        for (i in start until end) {
            val re = fft.getOrNull(i * 2)?.toInt() ?: continue
            val im = fft.getOrNull(i * 2 + 1)?.toInt() ?: 0
            val mag = hypot(re.toDouble(), im.toDouble()).toFloat()
            sum += mag
            count++
            if (mag > peak) peak = mag
        }
        // Visualizer FFT mags are typically well below 128 on real content — boost so the
        // traffic-light meter actually moves.
        val avg = if (count > 0) (sum / count).toFloat() else 0f
        bars[b] = (avg / 48f).coerceIn(0f, 1f)
    }
    return AuraVisualizerHub.FftFrame(
        bars = bars,
        peak = (peak / 48f).coerceIn(0f, 1f),
        atMs = 0L,
    )
}
