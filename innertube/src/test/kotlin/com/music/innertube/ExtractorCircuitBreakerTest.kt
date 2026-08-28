package com.music.innertube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the PipePipe extraction circuit breaker (HALLAZGO-042, RONDA 3 FASE 1
 * second pass). Pinned behaviour: the breaker never skips on a fresh start; it opens only
 * after [ExtractorCircuitBreaker.DEFAULT_FAILURE_THRESHOLD] CONSECUTIVE failures; while open
 * it skips until the cooldown expires; after the cooldown one probe is allowed (half-open);
 * a success closes it immediately and resets the failure count.
 */
class ExtractorCircuitBreakerTest {

    private val breaker = ExtractorCircuitBreaker(failureThreshold = 3, cooldownMs = 10_000L)

    @Test
    fun freshBreakerNeverSkips() {
        assertFalse(breaker.shouldSkip(0L))
        assertFalse(breaker.shouldSkip(Long.MAX_VALUE / 2))
    }

    @Test
    fun failuresBelowThresholdDoNotOpen() {
        breaker.recordResult(success = false, nowMs = 1_000L)
        breaker.recordResult(success = false, nowMs = 2_000L)
        assertFalse(breaker.shouldSkip(3_000L))
    }

    @Test
    fun thirdConsecutiveFailureOpensTheBreaker() {
        breaker.recordResult(success = false, nowMs = 1_000L)
        breaker.recordResult(success = false, nowMs = 2_000L)
        breaker.recordResult(success = false, nowMs = 3_000L)
        assertTrue(breaker.shouldSkip(3_001L))
    }

    @Test
    fun openBreakerSkipsUntilCooldownExpires() {
        repeat(3) { breaker.recordResult(success = false, nowMs = 1_000L) }
        // openUntil = 1_000 + 10_000 = 11_000.
        assertTrue(breaker.shouldSkip(10_999L))
        assertFalse(breaker.shouldSkip(11_000L))
    }

    @Test
    fun failureAfterHalfOpenReopensForFullCooldown() {
        repeat(3) { breaker.recordResult(success = false, nowMs = 1_000L) }
        // Cooldown expired -> probe allowed...
        assertFalse(breaker.shouldSkip(11_000L))
        // ...and failing the probe re-opens from the probe time (failure count stayed >= threshold).
        breaker.recordResult(success = false, nowMs = 12_000L)
        assertTrue(breaker.shouldSkip(12_001L))
        assertTrue(breaker.shouldSkip(21_999L))
        assertFalse(breaker.shouldSkip(22_000L))
    }

    @Test
    fun successClosesImmediatelyAndResetsFailureCount() {
        repeat(3) { breaker.recordResult(success = false, nowMs = 1_000L) }
        assertTrue(breaker.shouldSkip(5_000L))
        breaker.recordResult(success = true, nowMs = 5_500L)
        assertFalse(breaker.shouldSkip(5_501L))
        // After a reset it takes the full threshold again to re-open.
        breaker.recordResult(success = false, nowMs = 6_000L)
        breaker.recordResult(success = false, nowMs = 7_000L)
        assertFalse(breaker.shouldSkip(8_000L))
    }

    @Test
    fun successBeforeThresholdKeepsBreakerClosed() {
        breaker.recordResult(success = false, nowMs = 1_000L)
        breaker.recordResult(success = true, nowMs = 2_000L)
        breaker.recordResult(success = false, nowMs = 3_000L)
        breaker.recordResult(success = false, nowMs = 4_000L)
        assertFalse(breaker.shouldSkip(5_000L))
    }

    @Test
    fun defaultConstantsMatchDocumentedTuning() {
        // 3 consecutive failures open it; 10 minutes of cooldown (owner-device logs showed the
        // anti-bot wall persisting for the whole session).
        org.junit.Assert.assertEquals(3, ExtractorCircuitBreaker.DEFAULT_FAILURE_THRESHOLD)
        org.junit.Assert.assertEquals(600_000L, ExtractorCircuitBreaker.DEFAULT_COOLDOWN_MS)
    }
}
