package iad1tya.echo.music.playback

import iad1tya.echo.music.playback.CrossfadePlanning.TailDecision
import iad1tya.echo.music.playback.CrossfadePlanning.TailHintAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization locks for the five crossfade decision regions (RONDA 3 FASE B paso 1). Every
 * value here is the CURRENT behavior of MusicService — an owner-tuned transition with a dozen
 * regression-registry rows behind it. Any drift must fail here before it ships.
 */
class CrossfadePlanningTest {

    private val fade = 5_000L // owner config: crossfade ON / 5.0s

    // ── target index ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `repeat one targets the current index - anything else targets next`() {
        assertEquals(3, CrossfadePlanning.crossfadeTargetIndex(true, 3, 4))
        assertEquals(4, CrossfadePlanning.crossfadeTargetIndex(false, 3, 4))
        assertEquals(CrossfadePlanning.INDEX_UNSET, CrossfadePlanning.crossfadeTargetIndex(false, 9, -1))
    }

    // ── region 1: keepPreload ───────────────────────────────────────────────────────────────────

    private fun keepPreload(
        liveTargetMediaId: String? = "next-song",
        timelineVersionUnchanged: Boolean = true,
        secondaryMediaId: String? = "next-song",
        highPerformanceModeHint: Boolean = false,
        crossfadeEnabled: Boolean = true,
        videoMode: Boolean = false,
        durationMs: Long = 200_000L,
        crossfadeDurationMs: Long = fade,
        gaplessBypass: Boolean = false,
    ) = CrossfadePlanning.shouldKeepPreload(
        liveTargetMediaId, timelineVersionUnchanged, secondaryMediaId, highPerformanceModeHint,
        crossfadeEnabled, videoMode, durationMs, crossfadeDurationMs, gaplessBypass
    )

    @Test
    fun `keepPreload holds when every condition lines up`() {
        assertTrue(keepPreload())
    }

    @Test
    fun `keepPreload drops on any single miss`() {
        assertFalse(keepPreload(liveTargetMediaId = null))
        assertFalse(keepPreload(timelineVersionUnchanged = false))
        assertFalse(keepPreload(secondaryMediaId = "other-song"))
        assertFalse(keepPreload(secondaryMediaId = null))
        assertFalse(keepPreload(highPerformanceModeHint = true))
        assertFalse(keepPreload(crossfadeEnabled = false))
        assertFalse(keepPreload(videoMode = true))
        assertFalse(keepPreload(durationMs = CrossfadePlanning.TIME_UNSET))
        assertFalse(keepPreload(durationMs = fade)) // not strictly longer
        assertFalse(keepPreload(gaplessBypass = true))
    }

    // ── region 2: tail hint + trigger ───────────────────────────────────────────────────────────

    @Test
    fun `tail hint passes through when small and sane`() {
        assertEquals(8_000L, CrossfadePlanning.effectiveTailHint(8_000L, 240_000L, fade))
    }

    @Test
    fun `tail hint clamps to two fifths of the duration`() {
        // 240s song → cap 96s; a bogus 200s hint must land exactly on the cap.
        assertEquals(96_000L, CrossfadePlanning.effectiveTailHint(200_000L, 240_000L, fade))
    }

    @Test
    fun `tail hint drops when it would leave under 3s of audible pre-fade play`() {
        // 13s song, 5s fade: the 2/5 cap is 5.2s; the drop threshold is 13−5−3 = 5s of hint.
        assertEquals(0L, CrossfadePlanning.effectiveTailHint(5_200L, 13_000L, fade)) // clamps to cap 5.2s → 2.8s play → drop
        assertEquals(5_000L, CrossfadePlanning.effectiveTailHint(5_000L, 13_000L, fade)) // exactly 3s play → keep
    }

    @Test
    fun `tail hint drops on the repeat-one endless loop edge - hint covering the whole song`() {
        // A bogus learned tail on a short track: 12s song, 30s hint → cap 4.8s → 2.2s play → drop.
        assertEquals(0L, CrossfadePlanning.effectiveTailHint(30_000L, 12_000L, fade))
    }

    @Test
    fun `trigger time anchors at musical end minus fade window`() {
        assertEquals(230_000L, CrossfadePlanning.triggerTimeMs(240_000L, 5_000L, fade))
        assertEquals(240_000L - 8_000L - fade, CrossfadePlanning.triggerTimeMs(240_000L, 8_000L, fade))
        assertEquals(240_000L - fade, CrossfadePlanning.triggerTimeMs(240_000L, 0L, fade))
    }

    @Test
    fun `trigger delay floors at zero - inside the window fires now`() {
        assertEquals(10_000L, CrossfadePlanning.triggerDelayMs(100_000L, 90_000L))
        assertEquals(0L, CrossfadePlanning.triggerDelayMs(100_000L, 100_000L))
        assertEquals(0L, CrossfadePlanning.triggerDelayMs(100_000L, 118_000L)) // near-end seek
    }

    @Test
    fun `preload delay is the trigger delay minus the 15s lead floored at zero`() {
        assertEquals(45_000L, CrossfadePlanning.preloadDelayMs(60_000L))
        assertEquals(0L, CrossfadePlanning.preloadDelayMs(15_000L))
        assertEquals(0L, CrossfadePlanning.preloadDelayMs(0L))
    }

    // ── region 3: READY-wait ────────────────────────────────────────────────────────────────────

    @Test
    fun `ready wait keeps waiting above the 800ms floor while playing the same track`() {
        assertTrue(CrossfadePlanning.readyWaitShouldContinue(801L, true, true))
        assertTrue(CrossfadePlanning.readyWaitShouldContinue(30_000L, true, true))
    }

    @Test
    fun `ready wait stops at the floor on pause or track change`() {
        assertFalse(CrossfadePlanning.readyWaitShouldContinue(800L, true, true))
        assertFalse(CrossfadePlanning.readyWaitShouldContinue(0L, true, true))
        assertFalse(CrossfadePlanning.readyWaitShouldContinue(30_000L, false, true))
        assertFalse(CrossfadePlanning.readyWaitShouldContinue(30_000L, true, false))
    }

    // ── region 4: durOut ────────────────────────────────────────────────────────────────────────

    @Test
    fun `durOut is the configured length when the outgoing has plenty of life left`() {
        assertEquals(fade, CrossfadePlanning.outgoingFadeDurationMs(fade, 60_000L))
        assertEquals(fade, CrossfadePlanning.outgoingFadeDurationMs(fade, null)) // no fading player
    }

    @Test
    fun `durOut caps to remaining minus 250ms on a late start`() {
        // 3s left → ramp must finish inside it: 3000 − 250 = 2750.
        assertEquals(2_750L, CrossfadePlanning.outgoingFadeDurationMs(fade, 3_000L))
    }

    @Test
    fun `durOut never goes under the 600ms floor however late the fade starts`() {
        assertEquals(600L, CrossfadePlanning.outgoingFadeDurationMs(fade, 500L))
        assertEquals(600L, CrossfadePlanning.outgoingFadeDurationMs(fade, 0L))
        assertEquals(600L, CrossfadePlanning.outgoingFadeDurationMs(fade, 850L)) // 850−250=600
    }

    @Test
    fun `durOut with a short configured fade respects the configured value`() {
        assertEquals(2_000L, CrossfadePlanning.outgoingFadeDurationMs(2_000L, 60_000L))
        assertEquals(600L, CrossfadePlanning.outgoingFadeDurationMs(2_000L, 100L))
    }

    // ── region 5: tail tiers ────────────────────────────────────────────────────────────────────

    @Test
    fun `fire window is the fade itself — the quiet tier never jumps the gun on audible endings`() {
        // OWNER DIRECTIVE 2026-09-04 ("los adioses no se cortan"): was fade+4s (~9s with the 5s
        // house fade) — a soft-but-audible outro (piano/reverb) got buried under the ramp up to
        // 9s before the real end. Now the quiet tier may only fire INSIDE the fade window.
        assertEquals(5_000L, CrossfadePlanning.tailFireWindowMs(fade))
    }

    @Test
    fun `silence near the end fires inside the fade window`() {
        // 240s song at 236s → remaining 4s < 5s window → fire even with a short silence run.
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = true, isPlaying = true, durationMs = 240_000L,
            currentPositionMs = 236_000L, silenceDurationMs = 3_500L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Fire, d)
    }

    @Test
    fun `silence far from the end rechecks until 7s persistence`() {
        // remaining 200s, only 3.5s of silence so far → recheck in 7000−3500 = 3500ms.
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = true, isPlaying = true, durationMs = 240_000L,
            currentPositionMs = 40_000L, silenceDurationMs = 3_500L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Recheck(3_500L), d)
    }

    @Test
    fun `silence far from the end with enough persistence fires - skit survived`() {
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = true, isPlaying = true, durationMs = 240_000L,
            currentPositionMs = 40_000L, silenceDurationMs = 7_000L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Fire, d)
    }

    @Test
    fun `silence recheck floors at 250ms`() {
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = true, isPlaying = true, durationMs = 240_000L,
            currentPositionMs = 40_000L, silenceDurationMs = 6_900L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Recheck(250L), d)
    }

    @Test
    fun `silence far from the end while paused holds armed - no recheck loop`() {
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = true, isPlaying = false, durationMs = 240_000L,
            currentPositionMs = 40_000L, silenceDurationMs = 3_500L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Hold, d)
    }

    @Test
    fun `silence with unknown duration fires - nothing measurable to guard`() {
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = true, isPlaying = true, durationMs = CrossfadePlanning.TIME_UNSET,
            currentPositionMs = 0L, silenceDurationMs = 3_500L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Fire, d)
    }

    @Test
    fun `quiet tier inside the fire window fires - the audible segue`() {
        // remaining 4s < 5s window → the mastered fade-out gets the blend.
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = false, isPlaying = true, durationMs = 240_000L,
            currentPositionMs = 236_000L, silenceDurationMs = 2_500L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Fire, d)
    }

    @Test
    fun `quiet tier far from the end defers to a recheck at the window edge`() {
        // remaining 200s, window 5s → recheck in 195s.
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = false, isPlaying = true, durationMs = 240_000L,
            currentPositionMs = 40_000L, silenceDurationMs = 2_500L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Recheck(195_000L), d)
    }

    @Test
    fun `quiet tier recheck floors at 250ms just outside the window`() {
        // remaining = window + 100 → raw delay 100 → floor 250.
        val d = CrossfadePlanning.tailTierDecision(
            silentNow = false, isPlaying = true, durationMs = 240_000L,
            currentPositionMs = 240_000L - 5_100L, silenceDurationMs = 2_500L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Recheck(250L), d)
    }

    @Test
    fun `quiet tier paused or unknown duration holds armed`() {
        val paused = CrossfadePlanning.tailTierDecision(
            silentNow = false, isPlaying = false, durationMs = 240_000L,
            currentPositionMs = 40_000L, silenceDurationMs = 2_500L, crossfadeDurationMs = fade
        )
        val unset = CrossfadePlanning.tailTierDecision(
            silentNow = false, isPlaying = true, durationMs = CrossfadePlanning.TIME_UNSET,
            currentPositionMs = 0L, silenceDurationMs = 2_500L, crossfadeDurationMs = fade
        )
        assertEquals(TailDecision.Hold, paused)
        assertEquals(TailDecision.Hold, unset)
    }

    // ── tail memory classification ──────────────────────────────────────────────────────────────

    @Test
    fun `tail hint measurement classifies - store clear or ignore`() {
        assertEquals(TailHintAction.Store, CrossfadePlanning.classifyTailHint(8_000L, 240_000L))
        assertEquals(TailHintAction.ClearEntry, CrossfadePlanning.classifyTailHint(1_999L, 240_000L))
        assertEquals(TailHintAction.ClearEntry, CrossfadePlanning.classifyTailHint(0L, 240_000L))
        assertEquals(TailHintAction.Ignore, CrossfadePlanning.classifyTailHint(121_000L, 240_000L))
        assertEquals(TailHintAction.Ignore, CrossfadePlanning.classifyTailHint(8_000L, CrossfadePlanning.TIME_UNSET))
    }

    @Test
    fun `tail hint boundary values - 2s stores and exactly half the song stores`() {
        assertEquals(TailHintAction.Store, CrossfadePlanning.classifyTailHint(2_000L, 240_000L))
        assertEquals(TailHintAction.Store, CrossfadePlanning.classifyTailHint(120_000L, 240_000L))
    }
}
