package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of [computeEqFftBars] (HALLAZGO-048, RONDA 3). The math was extracted VERBATIM
 * from the EQ meter's capture listener when the Visualizer moved into [AuraVisualizerHub]; these
 * tests pin the shipped look so the hub refactor cannot drift it: quadratic-log bin spacing with
 * DC skipped, magnitudes normalized by 48 and clamped to 0..1, byte payloads read with sign
 * extension exactly as the listener always did.
 */
class AuraVisualizerHubTest {

    /** Realistic payload: 1024 bytes = 512 complex bins, the meter's 24 bars. */
    private val fftSize = 1024
    private val barCount = 24

    /**
     * Bar 18 spans bins [287, 320) = 33 bins: start = 1 + (18/24)^2 * 510, end = 1 + (19/24)^2 * 510.
     * A bar's value is the AVERAGE mag across its span, then /48 — a single lit bin inside it
     * reads mag / 33 / 48. The peak is the MAX bin mag /48, never averaged.
     */
    private val bar18Span = 33

    private fun fftWithBin(bin: Int, re: Byte, im: Byte): ByteArray {
        val fft = ByteArray(fftSize)
        fft[bin * 2] = re
        fft[bin * 2 + 1] = im
        return fft
    }

    @Test
    fun rejectsPayloadShorterThanOneComplexBin() {
        assertNull(computeEqFftBars(ByteArray(0), barCount))
        assertNull(computeEqFftBars(ByteArray(3), barCount))
    }

    @Test
    fun rejectsNonPositiveBarCount() {
        assertNull(computeEqFftBars(ByteArray(fftSize), 0))
        assertNull(computeEqFftBars(ByteArray(fftSize), -1))
    }

    @Test
    fun silentPayloadYieldsZeroBarsAndPeak() {
        val frame = computeEqFftBars(ByteArray(fftSize), barCount)!!
        assertArrayEquals(FloatArray(barCount), frame.bars, 0.0001f)
        assertEquals(0f, frame.peak, 0.0001f)
    }

    @Test
    fun dcEnergyNeverLeaksIntoBars() {
        // Bin 0 (indices 0/1) is DC — the spacing skips it, so full-scale DC leaves bars and peak
        // at zero instead of lighting up the first bar.
        val frame = computeEqFftBars(fftWithBin(0, 127, 127), barCount)!!
        assertArrayEquals(FloatArray(barCount), frame.bars, 0.0001f)
        assertEquals(0f, frame.peak, 0.0001f)
    }

    @Test
    fun toneAtBin300LightsOnlyItsLogSpacedBar() {
        // With 512 bins and 24 bars, bar 18 spans bins [287, 320): start = 1 + (18/24)^2 * 510.
        // The bar AVERAGES its span, so one lit bin of mag 48 inside 33 bins reads 48/33/48.
        val frame = computeEqFftBars(fftWithBin(300, 48, 0), barCount)!!
        for (b in 0 until barCount) {
            if (b == 18) {
                assertEquals(48f / bar18Span / 48f, frame.bars[b], 0.0001f)
            } else {
                assertEquals(0f, frame.bars[b], 0.0001f)
            }
        }
        // The peak is the max bin mag, never averaged: 48 / 48 = full scale.
        assertEquals(1f, frame.peak, 0.0001f)
    }

    @Test
    fun magnitudeNormalizesBy48() {
        val frame = computeEqFftBars(fftWithBin(300, 24, 0), barCount)!!
        assertEquals(24f / bar18Span / 48f, frame.bars[18], 0.0001f)
        assertEquals(0.5f, frame.peak, 0.0001f)
    }

    @Test
    fun imaginaryComponentContributesViaHypot() {
        // hypot(24, 24) ~= 33.94, averaged across the 33-bin span, then /48.
        val mag = kotlin.math.hypot(24.0, 24.0).toFloat()
        val frame = computeEqFftBars(fftWithBin(300, 24, 24), barCount)!!
        assertEquals(mag / bar18Span / 48f, frame.bars[18], 0.0001f)
    }

    @Test
    fun overFullPeakClampsToOneButBarsStayAveraged() {
        // hypot(127, 127) ~= 179.6: the peak clamps to 1, but the bar still averages its span,
        // so it reads ~0.113 — the traffic-light meter never saturates a whole bar from one bin.
        val mag = kotlin.math.hypot(127.0, 127.0).toFloat()
        val frame = computeEqFftBars(fftWithBin(300, 127, 127), barCount)!!
        assertEquals(mag / bar18Span / 48f, frame.bars[18], 0.0001f)
        assertEquals(1f, frame.peak, 0.0001f)
    }

    @Test
    fun bytesAreSignExtendedAsTheListenerAlwaysDid() {
        // -128 as a Byte is 0x80; the listener reads .toInt() = -128 and hypot(-128, 0) = 128, so
        // a full-scale negative bin lands exactly where a full-scale positive one does: the peak
        // clamps to 1 and the bar averages the span. Pins the byte path against any future
        // "cleanup" that would offset or mask the sample before the magnitude.
        val frame = computeEqFftBars(fftWithBin(300, (-128).toByte(), 0), barCount)!!
        assertEquals(128f / bar18Span / 48f, frame.bars[18], 0.0001f)
        assertEquals(1f, frame.peak, 0.0001f)
    }

    @Test
    fun peakTracksTheLargestBinAcrossBars() {
        val fft = ByteArray(fftSize)
        fft[300 * 2] = 10 // small bin in bar 18
        fft[400 * 2] = 40 // larger bin elsewhere
        val frame = computeEqFftBars(fft, barCount)!!
        assertEquals(40f / 48f, frame.peak, 0.0001f)
        assertTrue(frame.bars.any { it > 0f })
    }

    @Test
    fun factoryStampsAtMsZeroForTheHubToOverwrite() {
        val frame = computeEqFftBars(ByteArray(fftSize), barCount)!!
        assertEquals(0L, frame.atMs)
    }

    @Test
    fun sameInputIsDeterministic() {
        val fft = fftWithBin(300, 33, 17)
        val a = computeEqFftBars(fft, barCount)!!
        val b = computeEqFftBars(fft.copyOf(), barCount)!!
        assertArrayEquals(a.bars, b.bars, 0f)
        assertEquals(a.peak, b.peak, 0f)
    }

    /**
     * HALLAZGO-053 root cause (BETA-012 owner log): Android 14 (API 34) behavior change — the
     * Visualizer API refuses to initialize with ERROR_NO_INIT ("error: -3") unless RECORD_AUDIO
     * is granted, for session-attached and global captures alike. The app only ever requested
     * the permission for voice search, so on the owner's targetSdk-36 build every attach threw
     * and the meter sat on "Sin señal" (367 logged failures). This pins the attach gate: below
     * Android 14 the permission is irrelevant; on 14+ it is mandatory.
     */
    @Test
    fun captureAllowedBelowAndroid14RegardlessOfPermission() {
        assertTrue(visualizerCaptureAllowed(sdkInt = 33, recordAudioGranted = false))
        assertTrue(visualizerCaptureAllowed(sdkInt = 33, recordAudioGranted = true))
        assertTrue(visualizerCaptureAllowed(sdkInt = 21, recordAudioGranted = false))
    }

    @Test
    fun captureBlockedOnAndroid14PlusWithoutPermission() {
        assertFalse(visualizerCaptureAllowed(sdkInt = 34, recordAudioGranted = false))
        assertFalse(visualizerCaptureAllowed(sdkInt = 35, recordAudioGranted = false))
        assertFalse(visualizerCaptureAllowed(sdkInt = 36, recordAudioGranted = false))
    }

    @Test
    fun captureAllowedOnAndroid14PlusWithPermission() {
        assertTrue(visualizerCaptureAllowed(sdkInt = 34, recordAudioGranted = true))
        assertTrue(visualizerCaptureAllowed(sdkInt = 36, recordAudioGranted = true))
    }

    /**
     * HALLAZGO-055: frameworks that refuse the Visualizer outright (owner's S26 Ultra fails
     * even session 0 with error -3) used to be re-attacked every 10 s forever — binder churn,
     * log spam and heat that surfaces as jank minutes later. The watchdog path must stop
     * re-arming after the cap; a fresh session or a permission grant reopens it.
     */
    @Test
    fun rebindAllowedUntilTheFailureCapIsReached() {
        assertTrue(visualizerRebindAllowed(consecutiveFailures = 0, maxFailures = 3))
        assertTrue(visualizerRebindAllowed(consecutiveFailures = 2, maxFailures = 3))
        assertFalse(visualizerRebindAllowed(consecutiveFailures = 3, maxFailures = 3))
        assertFalse(visualizerRebindAllowed(consecutiveFailures = 99, maxFailures = 3))
    }

    /**
     * HALLAZGO-058 (BETA-014 owner log, S26 Ultra): rotating the phone swaps the player between
     * two composition positions (MainActivity's bottomBar branch vs its rail/wide branch —
     * `showRail` flips with orientation), so the rhythm consumer flips off and back on within one
     * frame. The immediate teardown that used to run on "zero consumers" released and re-attached
     * the Visualizer on the live audio chain once per rotation — 10 attach cycles in 12 s in the
     * log, each one an audible micro-cut. The teardown is now deferred by a grace window and
     * cancelled by a returning consumer; these tests pin that decision.
     */
    @Test
    fun teardownIsNotDueBeforeTheGraceWindowElapses() {
        assertFalse(visualizerTeardownDue(elapsedMs = 0, graceMs = 1_500, anyConsumerActive = false))
        assertFalse(visualizerTeardownDue(elapsedMs = 750, graceMs = 1_500, anyConsumerActive = false))
        assertFalse(visualizerTeardownDue(elapsedMs = 1_499, graceMs = 1_500, anyConsumerActive = false))
    }

    @Test
    fun teardownIsDueOnceTheGraceWindowElapsesWithoutConsumers() {
        assertTrue(visualizerTeardownDue(elapsedMs = 1_500, graceMs = 1_500, anyConsumerActive = false))
        assertTrue(visualizerTeardownDue(elapsedMs = 10_000, graceMs = 1_500, anyConsumerActive = false))
    }

    @Test
    fun returningConsumerVetoesTheDeferredTeardown() {
        // The rotation swap: the consumer is back long before the window elapses, but even a late
        // re-registration must veto the teardown — a capture with an active consumer is never due.
        assertFalse(visualizerTeardownDue(elapsedMs = 1_500, graceMs = 1_500, anyConsumerActive = true))
        assertFalse(visualizerTeardownDue(elapsedMs = 60_000, graceMs = 1_500, anyConsumerActive = true))
    }

    @Test
    fun sessionRebindNeededOnlyWhenTheSessionChanged() {
        // Same session (the rotation case): the kept-alive capture is still valid — zero churn.
        assertFalse(visualizerSessionRebindNeeded(boundSession = 602649, targetSession = 602649))
        assertFalse(visualizerSessionRebindNeeded(boundSession = 0, targetSession = 0))
        // Different session (crossfade dual-player swap) or global-fallback mismatch: rebuild.
        assertTrue(visualizerSessionRebindNeeded(boundSession = 602529, targetSession = 602649))
        assertTrue(visualizerSessionRebindNeeded(boundSession = 0, targetSession = 602649))
        assertTrue(visualizerSessionRebindNeeded(boundSession = 602649, targetSession = 0))
    }
}
