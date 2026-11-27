package iad1tya.echo.music.eq.audio

import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 918 — «Graves y agudos a volumen bajo». The gains (ISO 226 maths, [LoudnessCompensation]) and the native stage
 * that applies them (`AuraLoudnessCompensation.h`, through its JVM mirror [AuraLoudnessStageMirror]).
 */
class LoudnessCompensationTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private val header by lazy { File(root, "app/src/main/cpp/superpowered/AuraLoudnessCompensation.h").readText() }

    private fun db(v: Double) = 20.0 * log10(v)

    // ---------------------------------------------------------------- gains

    @Test
    fun `the ISO 226 contour meets its own definition at 1 kHz`() {
        for (phon in 20..90 step 5) {
            assertEquals("$phon phon at 1 kHz", phon.toDouble(), LoudnessCompensation.contourSpl(17, phon.toDouble()), 0.05)
        }
        assertEquals(1000.0, LoudnessCompensation.FREQS[17], 0.0)
        // At the reference level there is nothing to compensate.
        for (i in LoudnessCompensation.FREQS.indices) {
            assertEquals(0.0, LoudnessCompensation.differenceDb(i, LoudnessCompensation.REFERENCE_PHON), 1e-12)
        }
        // Lower levels need more bass, and more the lower the frequency.
        assertTrue(LoudnessCompensation.differenceDb(0, 40.0) > LoudnessCompensation.differenceDb(7, 40.0))
        assertTrue(LoudnessCompensation.differenceDb(7, 40.0) > 10.0)
    }

    @Test
    fun `zero at full volume and with the intensity at zero`() {
        assertEquals(LoudnessCompensation.ZERO, LoudnessCompensation.gains(0.0, 1.0))
        assertEquals(LoudnessCompensation.ZERO, LoudnessCompensation.gains(3.0, 1.0))
        assertEquals(LoudnessCompensation.ZERO, LoudnessCompensation.gains(-30.0, 0.0))
        assertEquals(LoudnessCompensation.ZERO, LoudnessCompensation.gains(-30.0, Double.NaN))
        // Exactly zero, not a rounding residue: the native stage then is not run at all.
        assertEquals(0.0, LoudnessCompensation.gains(0.0, 1.0).bassDb, 0.0)
        assertEquals(0.0, LoudnessCompensation.trimDb(LoudnessCompensation.gains(0.0, 1.0), autoHeadroom = true), 0.0)
    }

    @Test
    fun `gains grow monotonically as the volume goes down and never pass the caps`() {
        for (intensity in listOf(0.25, 0.5, 1.0)) {
            var prev = LoudnessCompensation.ZERO
            var v = 0.0
            while (v >= -80.0) {
                val g = LoudnessCompensation.gains(v, intensity)
                assertTrue("bass at $v dB, $intensity", g.bassDb >= prev.bassDb)
                assertTrue("treble at $v dB, $intensity", g.trebleDb >= prev.trebleDb)
                assertTrue(g.bassDb <= LoudnessCompensation.MAX_BASS_DB * intensity + 1e-12)
                assertTrue(g.trebleDb <= LoudnessCompensation.MAX_TREBLE_DB * intensity + 1e-12)
                assertTrue(g.bassDb >= 0.0 && g.trebleDb >= 0.0)
                prev = g
                v -= 0.25
            }
        }
        // Deep down (and muted) both are at the cap.
        val low = LoudnessCompensation.gains(-60.0, 1.0)
        assertEquals(LoudnessCompensation.MAX_BASS_DB, low.bassDb, 0.0)
        assertEquals(LoudnessCompensation.MAX_TREBLE_DB, low.trebleDb, 0.0)
        assertEquals(low, LoudnessCompensation.gains(Double.NEGATIVE_INFINITY, 1.0))
        // A little below full volume it is gentle: a few dB of bass, about a decibel of treble at most.
        val near = LoudnessCompensation.gains(-6.0, 1.0)
        assertTrue("near full volume: $near", near.bassDb in 1.0..5.0 && near.trebleDb <= 1.5)
    }

    @Test
    fun `the intensity scales both shelves linearly`() {
        val full = LoudnessCompensation.gains(-25.0, 1.0)
        val half = LoudnessCompensation.gains(-25.0, 0.5)
        assertEquals(full.bassDb / 2, half.bassDb, 1e-12)
        assertEquals(full.trebleDb / 2, half.trebleDb, 1e-12)
        assertEquals(full, LoudnessCompensation.gains(-25.0, 7.0)) // clamped to 1
    }

    @Test
    fun `the automatic headroom takes off exactly the larger boost, and nothing without it`() {
        val g = LoudnessCompensation.gains(-30.0, 1.0)
        assertEquals(-g.peakDb, LoudnessCompensation.trimDb(g, autoHeadroom = true), 0.0)
        assertEquals(0.0, LoudnessCompensation.trimDb(g, autoHeadroom = false), 0.0)
        assertEquals(g.bassDb, g.peakDb, 0.0)
    }

    @Test
    fun `the system volume index maps to the platform's attenuation`() {
        val f = LoudnessCompensation::volumeDbFor
        assertEquals(0.0, f(15, 15, -40.0, -40.0), 0.0) // full volume
        assertEquals(Double.NEGATIVE_INFINITY, f(0, 15, null, null), 0.0) // muted
        assertEquals(-23.5, f(7, 15, -23.5, -30.0), 0.0) // the current output's own curve
        // Flat curve (absolute Bluetooth volume) or none: the phone speaker's curve at the same index.
        assertEquals(-30.0, f(7, 15, 0.0, -30.0), 0.0)
        assertEquals(-30.0, f(7, 15, null, -30.0), 0.0)
        assertEquals(-30.0, f(7, 15, Double.NaN, -30.0), 0.0)
        // Nothing known (below Android 9): no compensation.
        assertEquals(0.0, f(7, 15, null, null), 0.0)
        assertEquals(0.0, f(7, 0, -20.0, -20.0), 0.0)
    }

    // ---------------------------------------------------------------- native stage (mirror)

    private fun sine(frames: Int, sr: Int, hz: Double, amp: Double) =
        FloatArray(frames * 2) { idx -> (amp * sin(2 * PI * hz * (idx / 2) / sr)).toFloat() }

    private fun amplitude(x: FloatArray, sr: Int, hz: Double, from: Int, to: Int): Double {
        var re = 0.0; var im = 0.0
        for (i in from until to) {
            val v = x[i * 2].toDouble()
            re += v * cos(2 * PI * hz * i / sr)
            im += v * sin(2 * PI * hz * i / sr)
        }
        return 2 * sqrt(re * re + im * im) / (to - from)
    }

    private fun lin(dbValue: Double) = 10.0.pow(dbValue / 20.0)

    /** Steady-state gain (dB) of the stage at [hz] with plateaus [bassDb] / [trebleDb] and [trimDb]. */
    private fun measured(sr: Int, hz: Double, bassDb: Double, trebleDb: Double, trimDb: Double = 0.0): Double {
        val frames = sr // one second: whole cycles for every test frequency
        val input = sine(frames, sr, hz, 0.25)
        val out = input.copyOf()
        val st = AuraLoudnessStageMirror(sr)
        st.setTarget(lin(bassDb), lin(trebleDb), lin(trimDb))
        var off = 0
        while (off < frames) { st.process(out, off, minOf(480, frames - off), 2); off += 480 }
        val from = sr / 2
        return db(amplitude(out, sr, hz, from, frames) / amplitude(input, sr, hz, from, frames))
    }

    @Test
    fun `at unity the stage is not engaged and the samples are never touched`() {
        val st = AuraLoudnessStageMirror(48_000)
        assertFalse(st.engaged())
        st.setTarget(1.0, 1.0, 1.0)
        assertFalse(st.engaged())
        // From the gains the service sends at full volume.
        val g = LoudnessCompensation.gains(0.0, 1.0)
        st.setTarget(lin(g.bassDb), lin(g.trebleDb), lin(LoudnessCompensation.trimDb(g, true)))
        assertFalse(st.engaged())
    }

    @Test
    fun `the measured response is the two shelves the gains were fitted to`() {
        for (sr in listOf(44_100, 48_000, 96_000)) {
            val bass = 8.0
            val treble = 3.0
            for (hz in listOf(25.0, 50.0, 100.0, 200.0, 1000.0, 3000.0)) {
                val expect = LoudnessCompensation.bassShelfDb(hz, bass) + LoudnessCompensation.trebleShelfDb(hz, treble)
                assertEquals("$sr Hz, $hz Hz", expect, measured(sr, hz, bass, treble), 0.15)
            }
            // The mids are left alone, the top gets the treble shelf (bilinear: exact at the corner and below).
            assertEquals("$sr 1 kHz", 0.0, measured(sr, 1000.0, bass, treble), 0.25)
            val at10k = measured(sr, 10_000.0, bass, treble)
            assertEquals("$sr 10 kHz", LoudnessCompensation.trebleShelfDb(10_000.0, treble), at10k, 0.4)
        }
    }

    @Test
    fun `with the automatic headroom the stage never raises anything above 0 dB`() {
        val sr = 48_000
        val g = LoudnessCompensation.gains(-40.0, 1.0)
        val trim = LoudnessCompensation.trimDb(g, autoHeadroom = true)
        var peak = -100.0
        for (hz in listOf(20.0, 30.0, 50.0, 100.0, 300.0, 1000.0, 5000.0, 10_000.0, 15_000.0, 20_000.0)) {
            peak = maxOf(peak, measured(sr, hz, g.bassDb, g.trebleDb, trim))
        }
        assertTrue("peak gain $peak dB", peak <= 0.05)
        // And the bass still stands out from the mids by about what was asked.
        val lift = measured(sr, 25.0, g.bassDb, g.trebleDb, trim) - measured(sr, 1000.0, g.bassDb, g.trebleDb, trim)
        assertTrue("bass lift $lift dB", lift > g.bassDb - 1.0)
    }

    @Test
    fun `a volume step glides in over 10 ms and lands exactly`() {
        val sr = 48_000
        val st = AuraLoudnessStageMirror(sr)
        st.setTarget(lin(8.0), lin(3.0), lin(-8.0))
        val data = sine(sr / 10, sr, 50.0, 0.25)
        st.process(data, 0, 479, 2)
        assertTrue(st.engaged())
        assertTrue(st.cBass < lin(8.0))
        st.process(data, 479, 2, 2)
        assertEquals(lin(8.0), st.cBass, 0.0)
        assertEquals(lin(3.0), st.cTreble, 0.0)
        assertEquals(lin(-8.0), st.cTrim, 0.0)
        // Back to unity: after the glide the stage is idle again and drops out of the chain.
        st.setTarget(1.0, 1.0, 1.0)
        st.process(data, 481, 480, 2)
        assertFalse(st.engaged())
    }

    @Test
    fun `the mirror's constants are the header's and the Kotlin model's`() {
        fun constant(name: String) =
            Regex("""constexpr double $name = ([-0-9.e]+);""").find(header)?.groupValues?.get(1)?.toDouble()
                ?: error("$name missing from AuraLoudnessCompensation.h")
        assertEquals(AuraLoudnessStageMirror.BASS_CORNER_HZ, constant("kLcBassCornerHz"), 0.0)
        assertEquals(AuraLoudnessStageMirror.TREBLE_CORNER_HZ, constant("kLcTrebleCornerHz"), 0.0)
        assertEquals(AuraLoudnessStageMirror.GLIDE_MS, constant("kLcGlideMs"), 0.0)
        assertEquals(LoudnessCompensation.BASS_CORNER_HZ, constant("kLcBassCornerHz"), 0.0)
        assertEquals(LoudnessCompensation.TREBLE_CORNER_HZ, constant("kLcTrebleCornerHz"), 0.0)
        assertTrue(header.contains("frame[ch] = (float) (tr * (x + eb * lp + et * (x - lpt)));"))
        assertTrue(header.contains("bool engaged() const { return glideLeft > 0 || cBass != 1.0 || cTreble != 1.0 || cTrim != 1.0; }"))
        assertTrue(header.contains("if (!engaged()) clearState();"))
    }
}
