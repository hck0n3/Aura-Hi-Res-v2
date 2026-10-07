package iad1tya.echo.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import iad1tya.echo.music.playback.audio.SilenceDetectorAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Locks the per-song silence memory measurements (0.6.135): leading-silence capture (intro skip),
 * EOS trailing-silence snapshot (tail anchor), and their reset semantics. 16-bit mono at 1000 Hz so
 * 1 frame == 1 ms and the duration math is exact in-test.
 */
class SilenceDetectorAudioProcessorTest {

    private val sampleRate = 1000 // 1 frame = 1 ms

    private fun newArmedProcessor(): SilenceDetectorAudioProcessor {
        val proc = SilenceDetectorAudioProcessor(onLongSilence = {})
        proc.configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
        proc.tailDetectEnabled = true
        proc.resetTracking()
        return proc
    }

    /** Feed [frames] mono 16-bit frames of constant [amplitude] and drain the passthrough output. */
    private fun feed(proc: SilenceDetectorAudioProcessor, frames: Int, amplitude: Int) {
        val buf = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { buf.putShort(amplitude.toShort()) }
        buf.flip()
        proc.queueInput(buf)
        proc.output // drain
    }

    @Test
    fun `leading silence finalizes at first loud frame`() {
        val proc = newArmedProcessor()
        assertEquals(-1L, proc.leadingSilenceUsOrNegative())
        feed(proc, frames = 3000, amplitude = 0) // 3s digital-silence intro
        assertEquals(-1L, proc.leadingSilenceUsOrNegative()) // not finalized while still silent
        feed(proc, frames = 500, amplitude = 20000) // music starts
        assertEquals(3_000_000L, proc.leadingSilenceUsOrNegative())
        // Later silence must NOT reopen the leading measurement.
        feed(proc, frames = 4000, amplitude = 0)
        feed(proc, frames = 100, amplitude = 20000)
        assertEquals(3_000_000L, proc.leadingSilenceUsOrNegative())
    }

    @Test
    fun `leading silence is zero when music starts immediately`() {
        val proc = newArmedProcessor()
        feed(proc, frames = 100, amplitude = 20000)
        assertEquals(0L, proc.leadingSilenceUsOrNegative())
    }

    @Test
    fun `trailing silence snapshots at end of stream`() {
        val proc = newArmedProcessor()
        assertEquals(-1L, proc.trailingSilenceUsOrNegative())
        feed(proc, frames = 2000, amplitude = 20000) // music
        feed(proc, frames = 8000, amplitude = 0) // 8s dead tail
        assertEquals(-1L, proc.trailingSilenceUsOrNegative()) // only at EOS
        proc.queueEndOfStream()
        assertEquals(8_000_000L, proc.trailingSilenceUsOrNegative())
    }

    @Test
    fun `trailing silence is zero when track ends loud`() {
        val proc = newArmedProcessor()
        feed(proc, frames = 3000, amplitude = 20000)
        proc.queueEndOfStream()
        assertEquals(0L, proc.trailingSilenceUsOrNegative())
    }

    /** Sink-tap feed: models a FLOAT route (hi-res, or the plan A3 16-bit takeover), where the chain never
     *  runs and the ForwardingAudioSink feeds measureExternal instead — the sink says so via externallyFed. */
    private fun newExternalProcessor(): SilenceDetectorAudioProcessor {
        val proc = SilenceDetectorAudioProcessor(onLongSilence = {})
        proc.externallyFed = true
        proc.tailDetectEnabled = true
        proc.resetTracking()
        return proc
    }

    private fun feedExternal(proc: SilenceDetectorAudioProcessor, frames: Int, amplitude: Int) {
        val buf = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { buf.putShort(amplitude.toShort()) }
        buf.flip()
        proc.measureExternal(buf, C.ENCODING_PCM_16BIT, sampleRate, 1)
    }

    @Test
    fun `external EOS mark takes the same snapshot on the sink-tap path`() {
        val proc = newExternalProcessor()
        feedExternal(proc, frames = 1000, amplitude = 20000)
        feedExternal(proc, frames = 5000, amplitude = 0)
        proc.markEndOfStreamExternal()
        assertEquals(5_000_000L, proc.trailingSilenceUsOrNegative())
    }

    @Test
    fun `external EOS mark no-ops on an ACTIVE chain processor`() {
        // Int pipeline (externallyFed=false, the default): the chain's own queueEndOfStream owns the
        // snapshot. A snapshot of a counter the tap never fed would erase a valid learned tail via the
        // sub-2s clear path.
        val proc = newArmedProcessor()
        feed(proc, frames = 1000, amplitude = 20000)
        feed(proc, frames = 5000, amplitude = 0)
        proc.markEndOfStreamExternal()
        assertEquals(-1L, proc.trailingSilenceUsOrNegative())
    }

    @Test
    fun `stale-active processor still measures when the sink says the tap is the only feed`() {
        // Plan A3: an int16 track configured the chain (isActive=true) and the next track on the SAME
        // player goes float — media3 never re-configures the chain, so isActive stays stale-true. Gating
        // on it killed tail detection (the crossfade anchor) for that track; the sink's flag does not.
        val proc = newArmedProcessor()
        proc.externallyFed = true
        assertTrue(proc.needsExternalMeasure())
        feedExternal(proc, frames = 1000, amplitude = 20000)
        feedExternal(proc, frames = 4000, amplitude = 0)
        assertEquals(4_000_000L, proc.silenceDurationUs())
        proc.markEndOfStreamExternal()
        assertEquals(4_000_000L, proc.trailingSilenceUsOrNegative())
    }

    @Test
    fun `tap is a no-op on the int16 chain so nothing is counted twice`() {
        val proc = newArmedProcessor()
        proc.externallyFed = false
        assertFalse(proc.needsExternalMeasure())
        feedExternal(proc, frames = 4000, amplitude = 0)
        assertEquals(0L, proc.silenceDurationUs())
    }

    @Test
    fun `tap waits for the counting latch`() {
        val proc = SilenceDetectorAudioProcessor(onLongSilence = {})
        proc.externallyFed = true
        assertFalse(proc.needsExternalMeasure()) // never armed: no duplicate() on the audio thread
        proc.tailDetectEnabled = true
        assertTrue(proc.needsExternalMeasure())
    }

    @Test
    fun `int16 to float gapless switch keeps the previous track's EOS snapshot clean`() {
        // Track A on media3's int16 chain (in-chain feed), then a gapless switch to a float route. The
        // sink keeps externallyFed=false (chainDrainPending) until the delegate has drained A's chain,
        // which runs the in-chain queueEndOfStream. B's first (loud) buffer offered to the tap BEFORE that
        // drain must not reach the counter, or A's 5 s tail would be snapshotted as ~0 and erased.
        val proc = newArmedProcessor()
        proc.externallyFed = false
        feed(proc, frames = 1000, amplitude = 20000)
        feed(proc, frames = 5000, amplitude = 0) // A's tail
        // configure(B) on a float route right after the int16 chain: tap still off.
        feedExternal(proc, frames = 20, amplitude = 20000) // B's first buffer at the tap
        proc.queueEndOfStream() // delegate drains A's int16 chain (empty-buffer handleBuffer)
        assertEquals(5_000_000L, proc.trailingSilenceUsOrNegative())
        // Drain done: the tap is B's only feed from here on.
        proc.externallyFed = true
        feedExternal(proc, frames = 20, amplitude = 20000)
        assertEquals(0L, proc.silenceDurationUs())
        assertEquals(5_000_000L, proc.trailingSilenceUsOrNegative()) // A's snapshot untouched by B
    }

    @Test
    fun `counting continues to EOS while notifications are disarmed`() {
        // The fade-start disarm (tailDetectEnabled=false) must NOT freeze the counters: the fading
        // player's trailing measurement runs to end-of-stream so the EOS snapshot is the REAL tail.
        val proc = newArmedProcessor()
        feed(proc, frames = 1000, amplitude = 20000)
        feed(proc, frames = 3500, amplitude = 0) // live tier would have fired around here
        proc.tailDetectEnabled = false // fade started
        feed(proc, frames = 2500, amplitude = 0) // rest of the tail decodes during the blend
        proc.queueEndOfStream()
        assertEquals(6_000_000L, proc.trailingSilenceUsOrNegative())
    }

    @Test
    fun `resetTracking clears both measurements for the next track`() {
        val proc = newArmedProcessor()
        feed(proc, frames = 2000, amplitude = 0)
        feed(proc, frames = 100, amplitude = 20000)
        feed(proc, frames = 4000, amplitude = 0)
        proc.queueEndOfStream()
        assertTrue(proc.leadingSilenceUsOrNegative() > 0)
        assertTrue(proc.trailingSilenceUsOrNegative() > 0)
        proc.resetTracking()
        assertEquals(-1L, proc.leadingSilenceUsOrNegative())
        assertEquals(-1L, proc.trailingSilenceUsOrNegative())
    }

    @Test
    fun `quiet frames above the silence threshold do not count as leading silence`() {
        val proc = newArmedProcessor()
        // ~-30 dBFS: audible (above the ~-42 dBFS silence threshold) — a quiet real intro is MUSIC.
        feed(proc, frames = 3000, amplitude = 1000)
        feed(proc, frames = 100, amplitude = 20000)
        assertEquals(0L, proc.leadingSilenceUsOrNegative())
    }
}
