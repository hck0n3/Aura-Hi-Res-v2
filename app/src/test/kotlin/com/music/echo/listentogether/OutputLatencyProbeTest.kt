package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Row 649 — the maths of the microphone latency probe, pinned: the chirp is found where it was put, in the presence of loud music and
 * noise, and nothing is "found" in a recording that does not contain it. (The Android parts — timestamps, routes — are measured on
 * devices, see the registry.)
 */
class OutputLatencyProbeTest {
    private val sr = OutputLatencyProbe.SAMPLE_RATE
    private val ref = OutputLatencyProbe.chirp(sr, 5_000f, 7_000f, 100, 0.35f)

    private fun recording(chirpAt: Int?, chirpGain: Float, musicGain: Float, noise: Float, seed: Long = 1): ShortArray {
        val rnd = Random(seed)
        val n = sr * 19 / 10
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i / sr.toDouble()
            // "music": a few strong tones, one of them near the chirp band's neighbourhood
            val music = (sin(2 * PI * 220 * t) + 0.7 * sin(2 * PI * 880 * t) + 0.5 * sin(2 * PI * 3_100 * t) + 0.3 * sin(2 * PI * 9_000 * t)) * musicGain
            var v = music + rnd.nextGaussian() * noise
            if (chirpAt != null && i >= chirpAt && i < chirpAt + ref.size) v += ref[i - chirpAt] / 32768.0 * chirpGain
            out[i] = (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    @Test
    fun `the chirp is found where it was put`() {
        for (at in intArrayOf(20_000, 33_333, 61_000)) {
            val m = OutputLatencyProbe.matchedPeak(recording(at, 1f, 0.05f, 0.01f), ref, sr, 5_000f, 7_000f, 0, sr * 19 / 10)
            assertNotNull("not found at $at", m)
            assertEquals("chirp at $at", at.toDouble(), m!!.index.toDouble(), 6.0)
        }
    }

    @Test
    fun `loud music in the recording does not move the peak`() {
        val m = OutputLatencyProbe.matchedPeak(recording(40_000, 1f, 0.5f, 0.05f), ref, sr, 5_000f, 7_000f, 0, sr * 19 / 10)
        assertNotNull(m)
        assertEquals(40_000.0, m!!.index.toDouble(), 6.0)
    }

    @Test
    fun `a recording without the chirp finds nothing`() {
        assertNull(OutputLatencyProbe.matchedPeak(recording(null, 0f, 0.3f, 0.05f), ref, sr, 5_000f, 7_000f, 0, sr * 19 / 10))
    }

    @Test
    fun `only the expected window is searched`() {
        // Another device's chirp at 10 000 must not win when the window is 30 000..60 000.
        val rec = recording(10_000, 1f, 0.05f, 0.01f)
        val ours = recording(45_000, 1f, 0.0f, 0.0f)
        for (i in rec.indices) rec[i] = (rec[i] + ours[i]).coerceIn(-32768, 32767).toShort()
        val m = OutputLatencyProbe.matchedPeak(rec, ref, sr, 5_000f, 7_000f, 30_000, 60_000)
        assertNotNull(m)
        assertEquals(45_000.0, m!!.index.toDouble(), 6.0)
    }

    @Test
    fun `two chirps of similar strength in the window are ambiguous`() {
        // Another device chirping at the same moment: two peaks, so no measurement rather than a coin toss.
        val rec = recording(20_000, 1f, 0.05f, 0.01f)
        val other = recording(45_000, 0.9f, 0.0f, 0.0f)
        for (i in rec.indices) rec[i] = (rec[i] + other[i]).coerceIn(-32768, 32767).toShort()
        assertNull(OutputLatencyProbe.matchedPeak(rec, ref, sr, 5_000f, 7_000f, 0, sr * 19 / 10))
    }

    @Test
    fun `a much weaker second chirp does not make the match ambiguous`() {
        val rec = recording(20_000, 1f, 0.05f, 0.01f)
        val other = recording(45_000, 0.15f, 0.0f, 0.0f)
        for (i in rec.indices) rec[i] = (rec[i] + other[i]).coerceIn(-32768, 32767).toShort()
        val m = OutputLatencyProbe.matchedPeak(rec, ref, sr, 5_000f, 7_000f, 0, sr * 19 / 10)
        assertNotNull(m)
        assertEquals(20_000.0, m!!.index.toDouble(), 6.0)
    }

    @Test
    fun `a far-off value is not adopted until a second measurement agrees`() {
        val key = "bt:outlier-${System.nanoTime()}"
        assertEquals(223, OutputLatencyCalibration.record(key, 223))
        // The tablet's real sequence from the owner's test: -22, then 54, then 385 — none of them confirmed by the next.
        assertEquals(223, OutputLatencyCalibration.record(key, -22))
        assertEquals(223, OutputLatencyCalibration.record(key, 54))
        assertEquals(223, OutputLatencyCalibration.record(key, 385))
        // A value close to the one in use still refines it.
        assertEquals(230, OutputLatencyCalibration.record(key, 230)) // median of [223, 230]
    }

    @Test
    fun `two agreeing measurements adopt a new value`() {
        val key = "bt:changed-${System.nanoTime()}"
        assertEquals(100, OutputLatencyCalibration.record(key, 100))
        assertEquals(100, OutputLatencyCalibration.record(key, 400)) // candidate only
        val adopted = OutputLatencyCalibration.record(key, 410)       // agrees with the candidate: the speaker really changed
        assertTrue("adopted $adopted", adopted in 400..410)
    }

    @Test
    fun `the value in use is the median of the last three measurements`() {
        assertEquals(300, OutputLatencyCalibration.median(listOf(290, 300, 900)))
        val key = "bt:test-${System.nanoTime()}"
        assertEquals(310, OutputLatencyCalibration.record(key, 310))
        assertEquals(330, OutputLatencyCalibration.record(key, 330)) // median of [310, 330] = index 1 -> 330
        assertEquals(330, OutputLatencyCalibration.record(key, 900)) // an outlier does not move it
        assertTrue(OutputLatencyCalibration.latencyFor(key) == 330)
    }
}
