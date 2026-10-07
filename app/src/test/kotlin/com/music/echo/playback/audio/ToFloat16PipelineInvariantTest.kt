package iad1tya.echo.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plan A3 — what the 16-bit float takeover in MusicService's ForwardingAudioSink relies on from media3's
 * own converter (ToFloatPcmAudioProcessor, media3 1.11.1):
 *  - same rate and channel count out, PCM_FLOAT, so the sink can forward presentationTimeUs unchanged;
 *  - exactly one float frame per 16-bit frame (output bytes = 2 × input bytes), no latency, no tail;
 *  - a whole input buffer is consumed in one queueInput, which is why the tempo/pitch revert can hand
 *    the renderer's buffer to the int16 chain with its original timestamp.
 */
class ToFloat16PipelineInvariantTest {

    @Suppress("DEPRECATION")
    private fun newPipeline(): Pair<AudioProcessingPipeline, AudioProcessor.AudioFormat> {
        val pipeline = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(ToFloatPcmAudioProcessor()))
        val out = pipeline.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        pipeline.flush()
        return pipeline to out
    }

    private fun pcm16(vararg samples: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buf.putShort(it.toShort()) }
        buf.flip()
        return buf
    }

    @Test
    fun `16-bit stereo becomes float at the same rate and channel count`() {
        val (_, out) = newPipeline()
        assertEquals(C.ENCODING_PCM_FLOAT, out.encoding)
        assertEquals(48_000, out.sampleRate)
        assertEquals(2, out.channelCount)
    }

    @Test
    fun `frames map one to one, the whole buffer is consumed, values stay in range`() {
        val (pipeline, _) = newPipeline()
        val samples = intArrayOf(0, 32767, -32768, 16384, -1, 1)
        val input = pcm16(*samples)
        pipeline.queueInput(input)
        assertFalse("ToFloatPcm consumes a whole buffer at once", input.hasRemaining())
        val output = pipeline.output
        assertEquals(samples.size * 4, output.remaining())
        output.order(ByteOrder.nativeOrder())
        for (s in samples) {
            val f = output.float
            assertEquals("sample $s", s / 32768f, f, 1e-6f)
            assertTrue("sample $s → $f out of [-1, 1]", f >= -1f && f <= 1f)
        }
    }

    @Test
    fun `end of stream leaves no tail`() {
        val (pipeline, _) = newPipeline()
        pipeline.queueInput(pcm16(100, -100, 200, -200))
        val first = pipeline.output
        first.position(first.limit()) // the sink consumed it
        pipeline.queueEndOfStream()
        assertFalse(pipeline.output.hasRemaining())
        assertTrue(pipeline.isEnded)
    }
}
