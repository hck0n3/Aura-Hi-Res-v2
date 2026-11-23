package iad1tya.echo.music.playback.audio

import android.media.AudioDeviceInfo
import androidx.media3.common.C

/**
 * The `AUDIO_TRACK` diagnostic line: one per AudioTrack media3 opens (a new track format, a seek — media3 1.11.1
 * rebuilds the AudioTrack on flush — and each crossfade player). It shows what really reaches the platform: the
 * encoding handed to the AudioTrack, its rate and channel count, whether it was opened in OFFLOAD mode, and the
 * output device type. Nothing about the song or the user goes in it (AGENTS.md rule 4).
 */
internal object AudioTrackDiagnostics {

    /** @param deviceType an `AudioDeviceInfo.TYPE_*`, or null when the output is not known. */
    fun line(encoding: Int, sampleRate: Int, channelConfig: Int, offload: Boolean, deviceType: Int?): String =
        "AUDIO_TRACK enc=${encodingName(encoding)} rate=$sampleRate ch=${Integer.bitCount(channelConfig)} " +
            "offload=$offload device=${deviceTypeName(deviceType)}"

    /** Same shape as the AUDIO_PATH line's names (`PCM_16BIT(2)`), plus the compressed encodings offload uses. */
    fun encodingName(encoding: Int): String {
        val name = when (encoding) {
            C.ENCODING_PCM_8BIT -> "PCM_8BIT"
            C.ENCODING_PCM_16BIT -> "PCM_16BIT"
            C.ENCODING_PCM_24BIT -> "PCM_24BIT"
            C.ENCODING_PCM_32BIT -> "PCM_32BIT"
            C.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
            C.ENCODING_MP3 -> "MP3"
            C.ENCODING_AAC_LC -> "AAC_LC"
            C.ENCODING_AAC_HE_V1 -> "AAC_HE_V1"
            C.ENCODING_AAC_HE_V2 -> "AAC_HE_V2"
            C.ENCODING_AAC_XHE -> "AAC_XHE"
            C.ENCODING_AAC_ELD -> "AAC_ELD"
            C.ENCODING_OPUS -> "OPUS"
            C.ENCODING_AC3 -> "AC3"
            C.ENCODING_E_AC3 -> "E_AC3"
            C.ENCODING_E_AC3_JOC -> "E_AC3_JOC"
            C.ENCODING_AC4 -> "AC4"
            C.ENCODING_DTS -> "DTS"
            C.ENCODING_DTS_HD -> "DTS_HD"
            C.ENCODING_DTS_UHD_P2 -> "DTS_UHD_P2"
            C.ENCODING_DOLBY_TRUEHD -> "DOLBY_TRUEHD"
            else -> "enc"
        }
        return "$name($encoding)"
    }

    /** The `AudioDeviceInfo.TYPE_*` constant's name, `type(n)` for one not listed, `unavailable` for null. */
    fun deviceTypeName(type: Int?): String = when (type) {
        null -> "unavailable"
        AudioDeviceInfo.TYPE_UNKNOWN -> "UNKNOWN"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "BUILTIN_SPEAKER_SAFE"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE_DIGITAL"
        AudioDeviceInfo.TYPE_AUX_LINE -> "AUX_LINE"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE_BROADCAST"
        AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI_ARC"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI_EARC"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_DOCK -> "DOCK"
        AudioDeviceInfo.TYPE_DOCK_ANALOG -> "DOCK_ANALOG"
        AudioDeviceInfo.TYPE_FM -> "FM"
        AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
        AudioDeviceInfo.TYPE_IP -> "IP"
        AudioDeviceInfo.TYPE_BUS -> "BUS"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "REMOTE_SUBMIX"
        AudioDeviceInfo.TYPE_MULTICHANNEL_GROUP -> "MULTICHANNEL_GROUP"
        else -> "type($type)"
    }
}
