package iad1tya.echo.music.playback.audio

import androidx.media3.common.C
import iad1tya.echo.music.playback.audio.SinkDspRouting.Route
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 903 — tempo and pitch on Hi-Res files (owner decision H17). media3's float branch drops tempo and pitch
 * (DefaultAudioSink 1.11.1, "SonicAudioProcessor outputs 16-bit"), so a hi-res track could only ever play at
 * 1.0×. With the caller's permission (not «Modo Fidelidad», not a Listen Together room) a hi-res track with
 * tempo/pitch moved goes to media3's int16 chain, where Sonic lives. Without it, hi-res is byte-for-byte the
 * route that shipped ([SinkDspRoutingTest] pins that default).
 */
class HiResTempoRoutingTest {

    private val hiRes = listOf(C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT_BIG_ENDIAN)

    private fun route(
        enc: Int,
        speed: Float = 1f,
        pitch: Float = 1f,
        allowed: Boolean = true,
        lowEnd: Boolean = false,
        channelMap: Boolean = false,
        float16: Boolean = false,
    ) = SinkDspRouting.route(true, enc, lowEnd, channelMap, float16, speed, pitch, hiResTempoAllowed = allowed)

    @Test
    fun `hi-res with tempo or pitch moved goes to the int16 chain when allowed`() {
        for (enc in hiRes) {
            assertEquals(Route.HIRES_INT16_FOR_TEMPO, route(enc, speed = 1.25f))
            assertEquals(Route.HIRES_INT16_FOR_TEMPO, route(enc, pitch = 1.0594631f))
        }
    }

    @Test
    fun `at 1_0 or without permission hi-res keeps its float takeover`() {
        for (enc in hiRes) {
            assertEquals(Route.FLOAT_TAKEOVER_HIRES, route(enc))
            assertEquals(Route.FLOAT_TAKEOVER_HIRES, route(enc, speed = 1.25f, allowed = false))
        }
    }

    @Test
    fun `low-end, channel maps and 16-bit tracks are untouched by the permission`() {
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_24BIT, speed = 1.25f, lowEnd = true))
        assertEquals(Route.DELEGATE_FLOAT_NO_DSP, route(C.ENCODING_PCM_24BIT, speed = 1.25f, channelMap = true))
        assertEquals(Route.DELEGATE_INT16_CHAIN, route(C.ENCODING_PCM_16BIT, speed = 1.25f))
        assertEquals(Route.FLOAT_TAKEOVER_16BIT, route(C.ENCODING_PCM_16BIT, float16 = true))
    }

    @Test
    fun `a hi-res track reverts mid-stream only with permission and off the default parameters`() {
        assertTrue(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_HIRES, 1.25f, 1f, hiResTempoAllowed = true))
        assertFalse(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_HIRES, 1f, 1f, hiResTempoAllowed = true))
        assertFalse(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_HIRES, 1.25f, 1f, hiResTempoAllowed = false))
        assertFalse(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_HIRES, 1.25f, 1f))
        // Already on the int16 chain: Sonic takes the change directly.
        assertFalse(SinkDspRouting.shouldRevert(Route.HIRES_INT16_FOR_TEMPO, 1.5f, 1f, hiResTempoAllowed = true))
        // The 16-bit takeover reverts exactly as before, with or without the new flag.
        assertTrue(SinkDspRouting.shouldRevert(Route.FLOAT_TAKEOVER_16BIT, 1.25f, 1f, hiResTempoAllowed = false))
    }

    @Test
    fun `on that route the delegate runs the chain and opens a 16-bit track`() {
        assertFalse(SinkDspRouting.chainBypassedByDelegate(Route.HIRES_INT16_FOR_TEMPO))
        assertFalse(SinkDspRouting.isTakeover(Route.HIRES_INT16_FOR_TEMPO))
        assertEquals(C.ENCODING_PCM_16BIT, SinkDspRouting.sinkOutputEncoding(Route.HIRES_INT16_FOR_TEMPO))
        // Leaving it for a float route drains the int16 chain first, exactly like DELEGATE_INT16_CHAIN (row 336).
        assertTrue(SinkDspRouting.delegateRunsInt16Chain(Route.HIRES_INT16_FOR_TEMPO))
        assertTrue(SinkDspRouting.delegateRunsInt16Chain(Route.DELEGATE_INT16_CHAIN))
        for (r in listOf(Route.PASSTHROUGH, Route.DELEGATE_FLOAT_NO_DSP, Route.FLOAT_TAKEOVER_HIRES, Route.FLOAT_TAKEOVER_16BIT)) {
            assertFalse(r.name, SinkDspRouting.delegateRunsInt16Chain(r))
        }
    }

    @Test
    fun `the sink only allows it outside Fidelidad and rooms, and converts with media3's own converter`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val service = File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
        assertTrue(service.contains("!fidelityModeHint && !runCatching { listenTogetherManager.isInRoom }.getOrDefault(true)"))
        assertTrue(service.contains("hiResTempoAllowed = hiResTempoAllowed(),"))
        assertTrue(service.contains("androidx.media3.common.audio.ToInt16PcmAudioProcessor()"))
        assertTrue(service.contains("hiResDsp = conversion"))
        assertTrue(service.contains("iad1tya.echo.music.playback.audio.SinkDspRouting.Route.HIRES_INT16_FOR_TEMPO"))
        // 32-bit float output on mid/high tier is unchanged (row 903, point 4).
        assertTrue(service.contains(".setEnableFloatOutput(!lowEnd)"))
    }
}
