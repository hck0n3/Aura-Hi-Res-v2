package iad1tya.echo.music.playback.audio

import androidx.media3.common.C
import iad1tya.echo.music.playback.audio.BitPerfectMixer.MixerOption
import iad1tya.echo.music.playback.audio.BitPerfectMixer.Outcome
import iad1tya.echo.music.playback.audio.BitPerfectMixer.Pick
import iad1tya.echo.music.playback.audio.BitPerfectMixer.StreamFormat
import iad1tya.echo.music.playback.audio.SinkDspRouting.Route
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 902 — the bit-perfect USB mixer pick. A wrong "match" is not a softer sound, it is a track that may not
 * play at all (a bit-perfect mixer only takes its own format), so the pick must be exact or nothing.
 */
class BitPerfectMixerTest {

    private val pcm16 = C.ENCODING_PCM_16BIT
    private val float = C.ENCODING_PCM_FLOAT
    private val pcm24 = C.ENCODING_PCM_24BIT
    private val pcm32 = C.ENCODING_PCM_32BIT

    private fun bp(enc: Int, rate: Int, ch: Int = 2) = MixerOption(enc, rate, ch, bitPerfect = true)
    private fun mixed(enc: Int, rate: Int, ch: Int = 2) = MixerOption(enc, rate, ch, bitPerfect = false)

    private val dac = listOf(
        mixed(pcm16, 48_000),
        bp(pcm16, 44_100),
        bp(pcm16, 48_000),
        bp(pcm24, 96_000),
        bp(pcm32, 192_000),
        bp(float, 96_000),
    )

    @Test
    fun `media3 and the platform number the PCM encodings the same way`() {
        // AudioFormat.ENCODING_PCM_16BIT=2, _FLOAT=4, _24BIT_PACKED=21, _32BIT=22 (android-37 android.jar, javap).
        assertEquals(2, C.ENCODING_PCM_16BIT)
        assertEquals(4, C.ENCODING_PCM_FLOAT)
        assertEquals(21, C.ENCODING_PCM_24BIT)
        assertEquals(22, C.ENCODING_PCM_32BIT)
    }

    @Test
    fun `an exact bit-perfect format is picked, never the mixed one`() {
        assertEquals(Pick.Match(1), BitPerfectMixer.pick(StreamFormat(pcm16, 44_100, 2), dac))
        // 16-bit 48 kHz: the mixed option comes first in the list and must be skipped.
        assertEquals(Pick.Match(2), BitPerfectMixer.pick(StreamFormat(pcm16, 48_000, 2), dac))
        assertEquals(Pick.Match(5), BitPerfectMixer.pick(StreamFormat(float, 96_000, 2), dac))
    }

    @Test
    fun `no close-enough match`() {
        // A float stream never goes to a 24- or 32-bit integer mixer.
        assertEquals(Pick.NoMatch(Outcome.FORMAT_UNSUPPORTED), BitPerfectMixer.pick(StreamFormat(float, 44_100, 2), dac.filter { it.encoding != float }))
        // Same encoding, other rate: never resampled.
        assertEquals(Pick.NoMatch(Outcome.RATE_UNSUPPORTED), BitPerfectMixer.pick(StreamFormat(pcm16, 88_200, 2), dac))
        assertEquals(Pick.NoMatch(Outcome.CHANNELS_UNSUPPORTED), BitPerfectMixer.pick(StreamFormat(pcm16, 44_100, 6), dac))
    }

    @Test
    fun `nothing to pick without a bit-perfect option or without PCM`() {
        assertEquals(Pick.NoMatch(Outcome.NO_BIT_PERFECT_MIXER), BitPerfectMixer.pick(StreamFormat(pcm16, 48_000, 2), listOf(mixed(pcm16, 48_000))))
        assertEquals(Pick.NoMatch(Outcome.NO_BIT_PERFECT_MIXER), BitPerfectMixer.pick(StreamFormat(pcm16, 48_000, 2), emptyList()))
        assertEquals(Pick.NoMatch(Outcome.NO_PCM_STREAM), BitPerfectMixer.pick(null, dac))
    }

    @Test
    fun `only USB DACs and headsets count, not accessories`() {
        assertTrue(BitPerfectMixer.isUsbOutput(11)) // TYPE_USB_DEVICE
        assertTrue(BitPerfectMixer.isUsbOutput(22)) // TYPE_USB_HEADSET
        assertFalse(BitPerfectMixer.isUsbOutput(12)) // TYPE_USB_ACCESSORY
        assertFalse(BitPerfectMixer.isUsbOutput(8)) // TYPE_BLUETOOTH_A2DP
        assertFalse(BitPerfectMixer.isUsbOutput(2)) // TYPE_BUILTIN_SPEAKER
        assertEquals(1, BitPerfectMixer.MIXER_BEHAVIOR_BIT_PERFECT)
    }

    @Test
    fun `the stream format the mixer must match is the sink's output, per route`() {
        assertNull(SinkDspRouting.sinkOutputEncoding(Route.PASSTHROUGH))
        assertEquals(pcm16, SinkDspRouting.sinkOutputEncoding(Route.DELEGATE_INT16_CHAIN))
        for (r in listOf(Route.DELEGATE_FLOAT_NO_DSP, Route.FLOAT_TAKEOVER_HIRES, Route.FLOAT_TAKEOVER_16BIT)) {
            assertEquals(r.name, float, SinkDspRouting.sinkOutputEncoding(r))
        }
    }

    @Test
    fun `the controller only logs enums and numbers, and is event-driven`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val src = File(root, "app/src/main/kotlin/com/music/echo/playback/audio/BitPerfectUsbController.kt").readText()
        assertTrue(src.contains("\"BIT_PERFECT outcome=\$outcome enc="))
        // Never the device's name or address.
        assertFalse(src.contains("productName"))
        assertFalse(src.contains(".address"))
        // No timers or loops: only the callers' events drive it.
        for (banned in listOf("delay(", "while (", "Handler(", "postDelayed", "Timer(")) {
            assertFalse(banned, src.contains(banned))
        }
        val service = File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
        for (hook in listOf(
            "bitPerfectUsb.setFidelityMode(on)",
            "bitPerfectUsb.onAudioDevicesChanged() // row 902: a DAC plugged in",
            "bitPerfectUsb.onAudioDevicesChanged() // row 902: unplugged → cleared",
            "runCatching { bitPerfectUsb.release() }",
            "bitPerfectUsb.onStreamFormat(",
        )) {
            assertTrue(hook, service.contains(hook))
        }
        assertEquals(3, Regex("""updateBitPerfectPlayback\(\) // row 902""").findAll(service).count())
    }
}
