package iad1tya.echo.music.playback

/**
 * Pure decision core for the crossfade lifecycle extracted from [MusicService.scheduleCrossfade],
 * [MusicService.startCrossfade], [MusicService.onTailSilenceDetected] and the fade loop in
 * [MusicService.performCrossfadeSwap] (HALLAZGO-021, RONDA 3 FASE B paso 1).
 *
 * The service keeps every side effect (building/releasing the secondary ExoPlayer, launching the
 * preload/trigger/ready-wait/recheck jobs, arming the silence detector, writing the tail memory
 * map, running the volume ramp); this object only decides WHAT to do given a snapshot of the
 * player state, so the five decision regions that have accumulated a dozen owner-tuned fixes can
 * be characterized without a device.
 *
 * The regions pinned here (each one is a regression in waiting if it drifts):
 *  1. keepPreload — reuse the buffered secondary player across the ~6 reschedule sites instead of
 *     rebuilding 2-4 ExoPlayers per song (thermal audit), but ONLY when the timeline version is
 *     unchanged, the next target still matches, and every early-return below would not fire.
 *  2. tailHint anchoring — the learned trailing-silence hint anchors the trigger at the musical
 *     end, clamped to 2/5 of the duration and dropped entirely when it would leave less than 3s
 *     of audible pre-fade play (the REPEAT_ONE endless fade→swap loop guard).
 *  3. READY-wait — the bounded wait for the incoming player never gives up while the outgoing
 *     still has more than 800ms of audible life (a late, shorter blend always beats a cut).
 *  4. durOut cap — the outgoing decay must complete within the fading track's audible life
 *     (owner: "la que sale NO baja"): min(configured, remaining−250ms, floor 600ms).
 *  5. tail tiers — true silence needs 7s persistence far from the end (skit/grand-pause safety)
 *     and fires anywhere otherwise; the −25dB "musical end" tier only acts inside (fade+4s) of
 *     the real end and defers to a live recheck until then.
 */
object CrossfadePlanning {

    // Mirror the media3 constants by value so the core stays Android-free in unit tests.
    const val TIME_UNSET = Long.MIN_VALUE // C.TIME_UNSET
    const val INDEX_UNSET = -1 // C.INDEX_UNSET

    /** Preload lead: the incoming player starts building this long before the fade trigger. */
    const val PRELOAD_LEAD_MS = 15_000L

    /** READY-wait floor: never swap with less than this much outgoing life left to land on. */
    const val READY_WAIT_FLOOR_MS = 800L

    /** True-silence persistence required far from the end (mid-song skit/grand-pause safety). */
    const val FAR_SILENCE_PERSISTENCE_MS = 7_000L

    /** Minimum audible pre-fade play the tail hint may leave; below it the hint is dropped. */
    const val MIN_PREFADE_PLAY_MS = 3_000L

    /** The tail hint never pulls the trigger earlier than this fraction of the duration. */
    const val TAIL_HINT_MAX_FRACTION_NUM = 2L
    const val TAIL_HINT_MAX_FRACTION_DEN = 5L

    /** Extra window past the fade duration where the quiet tier is allowed to fire.
     * OWNER DIRECTIVE 2026-09-04 ("los adioses de las canciones no se cortan"): was 4_000L — the
     * quiet tier (−25 dBFS, ≥2.5s) could fire up to fade+4s (~9s with the 5s house fade) before
     * the real end, burying a soft-but-audible outro (piano/reverb decay) under the outgoing
     * ramp + the incoming track rising. Now 0: the quiet tier may only fire INSIDE the fade
     * window itself — the outro plays at full level until the normal blend point. The tier
     * still exists for its real purpose (digital-silence endings starting the fade early),
     * it just can no longer jump the gun on audible endings. */
    const val QUIET_TIER_EXTRA_WINDOW_MS = 0L

    /** Floor for any scheduled tail recheck (also the recheck granularity of the READY-wait). */
    const val MIN_RECHECK_DELAY_MS = 250L

    /** durOut headroom: the outgoing ramp ends this long before the content does. */
    const val DUR_OUT_END_HEADROOM_MS = 250L

    /** durOut floor: the outgoing decay is never shorter than this, however late the start. */
    const val DUR_OUT_FLOOR_MS = 600L

    /**
     * The queue index the crossfade targets: under REPEAT_ONE the SAME item plays again, so the
     * secondary must copy the current index; otherwise it is media3's next index (which may be
     * [INDEX_UNSET] at the end of the queue).
     */
    fun crossfadeTargetIndex(repeatOne: Boolean, currentIndex: Int, nextIndex: Int): Int =
        if (repeatOne) currentIndex else nextIndex

    /**
     * Region 1 — keepPreload. A still-valid preloaded secondary survives a reschedule ONLY when
     * every condition holds; any miss releases it (the caller's side effect). The timeline-version
     * check is the adversarial-round fix: the secondary carries a queue COPY that becomes live at
     * the swap, so ANY timeline mutation makes it stale. [gaplessBypass] is
     * (crossfadeGapless && isNextItemGapless()).
     */
    fun shouldKeepPreload(
        liveTargetMediaId: String?,
        timelineVersionUnchanged: Boolean,
        secondaryMediaId: String?,
        highPerformanceModeHint: Boolean,
        crossfadeEnabled: Boolean,
        videoMode: Boolean,
        durationMs: Long,
        crossfadeDurationMs: Long,
        gaplessBypass: Boolean,
    ): Boolean = liveTargetMediaId != null &&
        timelineVersionUnchanged &&
        secondaryMediaId == liveTargetMediaId &&
        !highPerformanceModeHint && crossfadeEnabled && !videoMode &&
        durationMs != TIME_UNSET && durationMs > crossfadeDurationMs &&
        !gaplessBypass

    /**
     * Region 2 — effective tail hint. Clamps the learned trailing-silence hint to 2/5 of the
     * duration (a bad hint can never pull the trigger absurdly early) and drops it entirely when
     * it would leave less than [MIN_PREFADE_PLAY_MS] of audible pre-fade play — under REPEAT_ONE
     * that state re-arms itself into an endless fade→swap loop, building an ExoPlayer per pass.
     */
    fun effectiveTailHint(rawHintMs: Long, durationMs: Long, crossfadeDurationMs: Long): Long {
        var tailHint = rawHintMs.coerceAtMost(
            (durationMs * TAIL_HINT_MAX_FRACTION_NUM) / TAIL_HINT_MAX_FRACTION_DEN
        )
        if (durationMs - tailHint - crossfadeDurationMs < MIN_PREFADE_PLAY_MS) tailHint = 0L
        return tailHint
    }

    /** Region 2 — trigger instant: (musical end − fade window), anchored by the effective hint. */
    fun triggerTimeMs(durationMs: Long, tailHintMs: Long, crossfadeDurationMs: Long): Long =
        durationMs - tailHintMs - crossfadeDurationMs

    /**
     * Region 2 — delay until the trigger. Already INSIDE the fade window (near-end seek; radio
     * items landing during the last seconds) → 0, i.e. fire NOW instead of bailing: the old
     * `return` there is why a late re-arm could still end in a hard cut.
     */
    fun triggerDelayMs(triggerTimeMs: Long, currentPositionMs: Long): Long =
        (triggerTimeMs - currentPositionMs).coerceAtLeast(0L)

    /** Region 2 — the preload job fires this long before the trigger (never negative). */
    fun preloadDelayMs(triggerDelayMs: Long, preloadLeadMs: Long = PRELOAD_LEAD_MS): Long =
        (triggerDelayMs - preloadLeadMs).coerceAtLeast(0L)

    /**
     * Region 3 — READY-wait continuation. Polled every 50ms while the incoming player is not
     * STATE_READY: keep waiting only while the outgoing still has more than [READY_WAIT_FLOOR_MS]
     * of audible life AND the world has not moved on (paused, skipped, track changed). When it
     * returns false and the incoming is still not ready, the caller falls through to media3's
     * single-player auto-advance (a clean hard cut) rather than a clipped pop-in.
     */
    fun readyWaitShouldContinue(
        outgoingRemainingMs: Long,
        isPlaying: Boolean,
        sameTrackStillCurrent: Boolean,
    ): Boolean = outgoingRemainingMs > READY_WAIT_FLOOR_MS && isPlaying && sameTrackStillCurrent

    /**
     * Region 4 — durOut. The outgoing decay must COMPLETE within the fading track's audible life:
     * a fade that starts late (dynamic ready-wait, tail fire near the end) used to run its full
     * configured length, so the outgoing's file ENDED while its ramp was still near full volume —
     * heard as no decay at all (owner: "la que sale NO baja"). [fadingRemainingMs] is null when
     * there is no fading player or its duration is TIME_UNSET (uncapped → Long.MAX_VALUE).
     */
    fun outgoingFadeDurationMs(configuredMs: Long, fadingRemainingMs: Long?): Long {
        val fpRemaining = fadingRemainingMs ?: Long.MAX_VALUE
        return minOf(configuredMs, (fpRemaining - DUR_OUT_END_HEADROOM_MS).coerceAtLeast(DUR_OUT_FLOOR_MS))
    }

    /** Region 5 — the window from the real end where the quiet tier may fire: fade + 4s. */
    fun tailFireWindowMs(crossfadeDurationMs: Long): Long =
        crossfadeDurationMs + QUIET_TIER_EXTRA_WINDOW_MS

    /**
     * Region 5 — tail-tier decision. [silentNow] = true silence (≥3.5s under ~−42 dBFS);
     * otherwise the episode is the quiet "musical end" tier (≥2.5s under ~−25 dBFS).
     *  • SILENCE far from the end needs [FAR_SILENCE_PERSISTENCE_MS] of continuous silence before
     *    firing (a skit/grand-pause can't skip real music) → [TailDecision.Recheck] for the
     *    remaining persistence; near the end (or already persistent enough) it fires anywhere —
     *    nothing audible remains and every extra second is dead air.
     *  • QUIET only fires inside [tailFireWindowMs] of the real end — the blend must land where it
     *    would soon happen anyway, just anchored to the music instead of the file; earlier than
     *    that it defers to a live recheck at the moment the fade would be due.
     *  • [TailDecision.Hold] bails WITHOUT disarming: paused (frozen position/counters would
     *    re-arm a recheck forever) or unknown duration. The file-end trigger stays the fallback.
     */
    sealed interface TailDecision {
        data object Fire : TailDecision
        data class Recheck(val delayMs: Long) : TailDecision
        data object Hold : TailDecision
    }

    fun tailTierDecision(
        silentNow: Boolean,
        isPlaying: Boolean,
        durationMs: Long,
        currentPositionMs: Long,
        silenceDurationMs: Long,
        crossfadeDurationMs: Long,
    ): TailDecision {
        val fireWindowMs = tailFireWindowMs(crossfadeDurationMs)
        if (silentNow) {
            if (durationMs != TIME_UNSET) {
                val remaining = durationMs - currentPositionMs
                if (remaining > fireWindowMs && silenceDurationMs < FAR_SILENCE_PERSISTENCE_MS) {
                    if (!isPlaying) return TailDecision.Hold
                    return TailDecision.Recheck(
                        (FAR_SILENCE_PERSISTENCE_MS - silenceDurationMs).coerceAtLeast(MIN_RECHECK_DELAY_MS)
                    )
                }
            }
            return TailDecision.Fire
        }
        if (!isPlaying) return TailDecision.Hold
        if (durationMs == TIME_UNSET) return TailDecision.Hold
        val remaining = durationMs - currentPositionMs
        if (remaining > fireWindowMs) {
            return TailDecision.Recheck((remaining - fireWindowMs).coerceAtLeast(MIN_RECHECK_DELAY_MS))
        }
        return TailDecision.Fire
    }

    /**
     * The pure half of [MusicService.storeTailHint] — what a finished measurement means for the
     * per-song tail memory. Sub-2s "tails" mean the song has no real silent tail, so an EXACT
     * (EOS) measurement saying so also CLEARS any stale learned value; implausible values (more
     * than half the song) are ignored outright. The map itself (size cap, put/remove) stays in
     * the service.
     */
    sealed interface TailHintAction {
        data object Ignore : TailHintAction
        data object ClearEntry : TailHintAction
        data object Store : TailHintAction
    }

    fun classifyTailHint(hintMs: Long, durationMs: Long): TailHintAction {
        if (durationMs == TIME_UNSET) return TailHintAction.Ignore
        if (hintMs > durationMs / 2) return TailHintAction.Ignore
        if (hintMs < 2_000L) return TailHintAction.ClearEntry
        return TailHintAction.Store
    }
}
