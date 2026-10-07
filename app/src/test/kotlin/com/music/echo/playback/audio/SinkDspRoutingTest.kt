package iad1tya.echo.music.playback.audio

import androidx.media3.common.C
import iad1tya.echo.music.playback.audio.SinkDspRouting.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan A3 — the sink's route decision. Two things matter and both fail SILENTLY (no error, only sound):
 *  1. hi-res keeps exactly its 0.6.142 behaviour whatever the new switch, tempo or room say;
 *  2. the 16-bit float takeover never runs while tempo/pitch are moved or a Listen Together room is live,
 *     because media3's float branch drops speed and pitch (DefaultAudioSink 1.11.1, lines 1734-1745).
 */
class SinkDspRoutingTest {

    private fun route(
        encoding: Int,
        isRaw: Boolean = true,
        lowEnd: Boolean = false,
        channelMap: Boolean = false,
        allowed: Boolean = true,
        speed: Float = 1f,
        pitch: Float = 1f,
    ) = SinkDspRouting.route(isRaw, encoding, lowEnd, channelMap, allowed, speed, pitch)

    private val hiResEncodings = listOf(
        C.ENCODING_PCM_24BIT,
        C.ENCODING_PCM_32BIT,
        C.ENCODING_PCM_FLOAT,
        C.ENCODING_PCM_24BIT_BIG_ENDIAN,
    )

    @Test
    fun `hi-res takes over regardless of the switch, tempo and room`() {
        for (enc in hiResEncodings) {
            for (allowed in listOf(true, false)) {
                for ((speed, pitch) in listOf(1f to 1f, 1.25f to 1f, 1f to 1.06f, 1.02f to 1f)) {
                    assertEquals(
                        "enc=$enc allowed=$allowed speed=$speed pitch=$pitch",
                        Route.FLOAT_TAKEOVER_HIRES,
                        route(enc, allowed = allowed, speed = speed, pitch = pitch),
                    )
                }
            }
        }
    }

    @Test
    fun `hi-res with a channel map keeps media3's own float branch`() {
        assertEquals(Route.DELEGATE_FLOAT_NO_DSP, route(C.ENCODING_PCM_24BIT, channelMap = true))
    }

    @Test
    fun `low-end devices never take over and keep the int16 chain`() {
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_24BIT, lowEnd = true))
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT, lowEnd = true))
    }

    @Test
    fun `16-bit with the switch OFF stays on the int16 chain (today's path)`() {
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT, allowed = false))
    }

    @Test
    fun `16-bit with the switch ON, default tempo and no room takes over`() {
        assertEquals(Route.FLOAT_TAKEOVER_16BIT, route(C.ENCODING_PCM_16BIT))
    }

    @Test
    fun `16-bit with tempo or pitch moved stays on the int16 chain where Sonic lives`() {
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT, speed = 1.02f))
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT, speed = 0.98f))
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT, pitch = 1.0594631f))
    }

    @Test
    fun `16-bit in a Listen Together room stays on the int16 chain`() {
        // The caller folds "in a room" into float16Allowed; same outcome as the switch being OFF.
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT, allowed = false))
    }

    @Test
    fun `16-bit with a channel map stays on the int16 chain`() {
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT, channelMap = true))
    }

    @Test
    fun `only little-endian 16-bit is taken over`() {
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_8BIT))
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT_BIG_ENDIAN))
    }

    @Test
    fun `encoded passthrough or offload is never touched`() {
        for (enc in listOf(Int.MIN_VALUE, -1, C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT)) {
            assertEquals(Route.PASSTHROUGH, route(enc, isRaw = false))
        }
    }

    @Test
    fun `revert only for the 16-bit takeover and only off the default parameters`() {
        assertTrue(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_16BIT, 1.02f, 1f))
        assertTrue(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_16BIT, 1f, 0.9438743f))
        assertFalse(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_16BIT, 1f, 1f))
        // Never downgrade hi-res to 16 bits behind the user's back.
        assertFalse(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_HIRES, 1.25f, 1f))
        for (r in listOf(Route.DELEGATE_INT16_CHAIN, Route.DELEGATE_FLOAT_NO_DSP, Route.PASSTHROUGH)) {
            assertFalse(r.name, SinkDspRouting.shouldRevert(r, 1.25f, 1.1f))
        }
    }

    @Test
    fun `the sink tap feeds the silence detector exactly when the delegate skips the chain`() {
        assertTrue(SinkDspRouting.chainBypassedByDelegate(Route.FLOAT_TAKEOVER_HIRES))
        assertTrue(SinkDspRouting.chainBypassedByDelegate(Route.FLOAT_TAKEOVER_16BIT))
        assertTrue(SinkDspRouting.chainBypassedByDelegate(Route.DELEGATE_FLOAT_NO_DSP))
        assertFalse(SinkDspRouting.chainBypassedByDelegate(Route.DELEGATE_INT16_CHAIN))
        assertFalse(SinkDspRouting.chainBypassedByDelegate(Route.PASSTHROUGH))
    }
}
