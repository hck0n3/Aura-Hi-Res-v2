package com.music.innertube

/**
 * Pure decision core that skips a stream extractor which is failing repeatedly
 * (HALLAZGO-042, RONDA 3 FASE 1 second pass).
 *
 * Background: on some device/IP combinations PipePipe's anonymous extraction runs into
 * YouTube's anti-bot wall and fails EVERY time (~1.7 s wasted per resolve) before the resolve
 * chain falls back to BraveNewPipe. Owner-device logs (BETA-005) showed every single song
 * paying that toll. The breaker opens after [failureThreshold] consecutive failures and skips
 * the broken extractor for [cooldownMs], going straight to the fallback that is known to work.
 * A success closes it immediately; after the cooldown expires one probe attempt is allowed
 * (half-open) and a failure re-opens it.
 *
 * Deliberately dumb and allocation-free: no Android dependencies, unit-testable on the JVM.
 * The wiring lives in [com.music.innertube.pages.NewPipeExtractor.newPipePlayer].
 */
class ExtractorCircuitBreaker(
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    private var consecutiveFailures = 0
    private var openUntilMs = 0L

    @Synchronized
    fun shouldSkip(nowMs: Long): Boolean = nowMs < openUntilMs

    @Synchronized
    fun recordResult(success: Boolean, nowMs: Long) {
        if (success) {
            consecutiveFailures = 0
            openUntilMs = 0L
        } else {
            consecutiveFailures++
            if (consecutiveFailures >= failureThreshold) {
                openUntilMs = nowMs + cooldownMs
            }
        }
    }

    companion object {
        const val DEFAULT_FAILURE_THRESHOLD = 3
        const val DEFAULT_COOLDOWN_MS = 10 * 60 * 1000L
    }
}
