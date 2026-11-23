package iad1tya.echo.music.playback

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import androidx.media3.common.C
import iad1tya.echo.music.playback.audio.AudioTrackDiagnostics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AUDIO_TRACK line goes to the log the user shares (AGENTS.md rule 4): it must carry the AudioTrack's format,
 * offload flag and output type, and nothing else.
 */
class AudioTrackDiagnosticsTest {

    @Test
    fun `a 16-bit stereo track on Bluetooth`() {
        assertEquals(
            "AUDIO_TRACK enc=PCM_16BIT(2) rate=48000 ch=2 offload=false device=BLUETOOTH_A2DP",
            AudioTrackDiagnostics.line(
                C.ENCODING_PCM_16BIT, 48_000, AudioFormat.CHANNEL_OUT_STEREO, false, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            ),
        )
    }

    @Test
    fun `an offloaded Opus track on the phone speaker`() {
        assertEquals(
            "AUDIO_TRACK enc=OPUS(20) rate=48000 ch=2 offload=true device=BUILTIN_SPEAKER",
            AudioTrackDiagnostics.line(
                C.ENCODING_OPUS, 48_000, AudioFormat.CHANNEL_OUT_STEREO, true, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            ),
        )
    }

    @Test
    fun `channel count comes from the channel mask`() {
        assertTrue(AudioTrackDiagnostics.line(C.ENCODING_PCM_FLOAT, 96_000, AudioFormat.CHANNEL_OUT_MONO, false, null).contains(" ch=1 "))
        assertTrue(AudioTrackDiagnostics.line(C.ENCODING_PCM_FLOAT, 96_000, AudioFormat.CHANNEL_OUT_5POINT1, false, null).contains(" ch=6 "))
        assertTrue(AudioTrackDiagnostics.line(C.ENCODING_PCM_FLOAT, 96_000, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, false, null).contains(" ch=8 "))
    }

    @Test
    fun `unknown values stay readable instead of failing`() {
        assertEquals("enc(12345)", AudioTrackDiagnostics.encodingName(12345))
        assertEquals("type(999)", AudioTrackDiagnostics.deviceTypeName(999))
        assertEquals("unavailable", AudioTrackDiagnostics.deviceTypeName(null))
        assertEquals("USB_HEADSET", AudioTrackDiagnostics.deviceTypeName(AudioDeviceInfo.TYPE_USB_HEADSET))
    }

    @Test
    fun `the line has exactly the five diagnostic fields`() {
        val shape = Regex("""AUDIO_TRACK enc=\S+ rate=\d+ ch=\d+ offload=(true|false) device=\S+""")
        listOf(
            AudioTrackDiagnostics.line(C.ENCODING_PCM_24BIT, 44_100, AudioFormat.CHANNEL_OUT_STEREO, false, AudioDeviceInfo.TYPE_USB_DEVICE),
            AudioTrackDiagnostics.line(C.ENCODING_AAC_LC, 44_100, AudioFormat.CHANNEL_OUT_STEREO, true, null),
            AudioTrackDiagnostics.line(-1, 0, 0, false, -7),
        ).forEach { assertTrue(it, shape.matches(it)) }
    }

    @Test
    fun `every player logs its AudioTrack and the listener reads nothing about the song`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val f = File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        assertTrue("playback/MusicService.kt moved: update this test", f.isFile)
        val service = f.readText()

        // Registered next to the stats listener, outside the `!isSecondary` gate: the crossfade and video players too.
        assertTrue(
            service.contains(
                "addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))\n" +
                    "                addAnalyticsListener(audioTrackDiagnosticsListener)",
            ),
        )
        val start = service.indexOf("private val audioTrackDiagnosticsListener = object : AnalyticsListener {")
        assertTrue(start >= 0)
        val listener = service.substring(start, service.indexOf("\n    }\n", start))
        assertTrue(listener.contains("Timber.tag(TAG).i("))
        assertTrue(listener.contains("AudioTrackDiagnostics.line("))
        for (userData in listOf("mediaItem", "metadata", "mediaId", "title", "eventTime.")) {
            assertFalse("the AUDIO_TRACK listener must not read $userData", listener.contains(userData))
        }
    }
}
