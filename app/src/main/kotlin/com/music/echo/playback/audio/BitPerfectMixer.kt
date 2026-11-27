package iad1tya.echo.music.playback.audio

/**
 * Row 902 — bit-perfect USB output on Android 14+ (API 34) for «Modo Fidelidad», as pure decisions.
 *
 * Android 14 added `AudioManager.getSupportedMixerAttributes(device)` and
 * `setPreferredMixerAttributes(attributes, device, mixerAttributes)`: with an `AudioMixerAttributes` whose
 * behaviour is `MIXER_BEHAVIOR_BIT_PERFECT`, the framework opens the USB output with exactly that format and
 * hands it the app's samples without mixing or resampling. It only works when the stream's own PCM format,
 * sample rate and channel count are EXACTLY one of the device's bit-perfect mixer formats, so the pick below
 * is strict: no "close enough" (24-bit for a float stream, 48 kHz for 44.1 kHz) — that would not be
 * bit-perfect, and a track that does not match a bit-perfect mixer is not guaranteed to play at all.
 *
 * The integers are the platform's: `AudioFormat.ENCODING_*` (media3's `C.ENCODING_PCM_16BIT` = 2,
 * `C.ENCODING_PCM_FLOAT` = 4, `C.ENCODING_PCM_24BIT` = 21, `C.ENCODING_PCM_32BIT` = 22 are the same values),
 * `AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT` = 1 and `AudioDeviceInfo.TYPE_USB_DEVICE` = 11 /
 * `TYPE_USB_HEADSET` = 22 — read from android-37 `android.jar` with javap, not guessed.
 */
object BitPerfectMixer {

    /** One entry of `getSupportedMixerAttributes`: its `AudioFormat` and whether its behaviour is bit-perfect. */
    data class MixerOption(
        val encoding: Int,
        val sampleRate: Int,
        val channelCount: Int,
        val bitPerfect: Boolean,
    )

    /** What the AudioTrack of the current track will be opened with (the sink's OUTPUT, not its input). */
    data class StreamFormat(val encoding: Int, val sampleRate: Int, val channelCount: Int)

    /** Every result the controller can log. An enum and numbers only: never a device name (AGENTS.md rule 4). */
    enum class Outcome {
        APPLIED,
        CLEARED,
        API_TOO_OLD,
        NO_USB_OUTPUT,
        NO_PCM_STREAM,
        NO_BIT_PERFECT_MIXER,
        FORMAT_UNSUPPORTED,
        RATE_UNSUPPORTED,
        CHANNELS_UNSUPPORTED,
        REFUSED,
        ERROR,
    }

    sealed class Pick {
        /** Index into the options list handed to [pick]. */
        data class Match(val index: Int) : Pick()
        data class NoMatch(val reason: Outcome) : Pick()
    }

    const val MIXER_BEHAVIOR_BIT_PERFECT = 1
    const val TYPE_USB_DEVICE = 11
    const val TYPE_USB_HEADSET = 22

    /** USB audio outputs. `TYPE_USB_ACCESSORY` (Android Auto heads, docks) is not a DAC and is left out. */
    fun isUsbOutput(deviceType: Int): Boolean = deviceType == TYPE_USB_DEVICE || deviceType == TYPE_USB_HEADSET

    /**
     * The bit-perfect mixer option that matches [stream] exactly, or why there is none. The reason narrows
     * from the coarsest mismatch (no bit-perfect option at all) to the finest (only the channel count differs),
     * so a shared log says what the DAC lacks.
     */
    fun pick(stream: StreamFormat?, options: List<MixerOption>): Pick {
        if (stream == null) return Pick.NoMatch(Outcome.NO_PCM_STREAM)
        val bitPerfect = options.withIndex().filter { it.value.bitPerfect }
        if (bitPerfect.isEmpty()) return Pick.NoMatch(Outcome.NO_BIT_PERFECT_MIXER)
        val sameEncoding = bitPerfect.filter { it.value.encoding == stream.encoding }
        if (sameEncoding.isEmpty()) return Pick.NoMatch(Outcome.FORMAT_UNSUPPORTED)
        val sameRate = sameEncoding.filter { it.value.sampleRate == stream.sampleRate }
        if (sameRate.isEmpty()) return Pick.NoMatch(Outcome.RATE_UNSUPPORTED)
        val exact = sameRate.firstOrNull { it.value.channelCount == stream.channelCount }
            ?: return Pick.NoMatch(Outcome.CHANNELS_UNSUPPORTED)
        return Pick.Match(exact.index)
    }
}
