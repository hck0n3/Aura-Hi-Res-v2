package iad1tya.echo.music.playback

import java.util.concurrent.atomic.AtomicLong

/**
 * Aggregate-only diagnostics for the streaming pipeline (SimpMusic-model port, "Diagnostic
 * Harness" pillar).
 *
 * Deliberately carries NO per-track data — no mediaIds, titles, artists, URLs or response bodies —
 * because INFO-level Timber output is persisted to filesDir/logs/app.log, the file users share from
 * Settings ▸ Logs (AGENTS.md rule 4). Only session-wide counters live here; [snapshot] renders them
 * as a single loggable line at error/recovery boundaries.
 */
object StreamHealth {
    private val freshResolves = AtomicLong()
    private val freshResolveFailures = AtomicLong()
    private val freshResolveMsTotal = AtomicLong()
    private val urlCacheHits = AtomicLong()
    private val rebufferEvents = AtomicLong()
    private val expiredUrlRecoveries = AtomicLong()
    private val refreshAheadCompletions = AtomicLong()

    fun freshResolveStarted() {
        freshResolves.incrementAndGet()
    }

    fun freshResolveCompleted(durationMs: Long) {
        freshResolveMsTotal.addAndGet(durationMs)
    }

    fun freshResolveFailed() {
        freshResolveFailures.incrementAndGet()
    }

    fun cacheHit() {
        urlCacheHits.incrementAndGet()
    }

    fun rebuffers(count: Int) {
        if (count > 0) rebufferEvents.addAndGet(count.toLong())
    }

    fun expiredUrlRecovery() {
        expiredUrlRecoveries.incrementAndGet()
    }

    fun refreshAheadCompleted() {
        refreshAheadCompletions.incrementAndGet()
    }

    /** One-line aggregate summary, safe for the shared app.log (no user data). */
    fun snapshot(): String {
        val resolves = freshResolves.get()
        val avgMs = if (resolves > 0) freshResolveMsTotal.get() / resolves else 0L
        return "StreamHealth resolves=$resolves failures=${freshResolveFailures.get()} " +
            "avgResolveMs=$avgMs urlCacheHits=${urlCacheHits.get()} rebuffers=${rebufferEvents.get()} " +
            "expiredUrlRecoveries=${expiredUrlRecoveries.get()} refreshAhead=${refreshAheadCompletions.get()}"
    }
}
