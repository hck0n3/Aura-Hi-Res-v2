package iad1tya.echo.music.playback.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import iad1tya.echo.music.eq.audio.LoudnessCompensation

/**
 * Row 918 — «Graves y agudos a volumen bajo»: follows the SYSTEM music volume and hands the shelf gains of
 * [LoudnessCompensation] to [onGains].
 *
 * Event-driven only (AGENTS.md rule 7): the volume broadcast is registered only while the switch is on, and the gains are
 * recomputed on a volume change, an output change ([onOutputChanged], from the service's audio-device callback) and a
 * settings change. Nothing polls, nothing runs per buffer. Main thread only.
 *
 * The app cannot know the real sound pressure level: it maps the volume INDEX to the attenuation the platform applies
 * for the current output ([AudioManager.getStreamVolumeDb], Android 9+). When that curve is flat (absolute Bluetooth
 * volume, where the headphones attenuate, or a fixed-volume output) the platform's own phone-speaker curve at the same
 * index stands in, so the compensation still follows the user's volume. Below Android 9 it stays at zero.
 */
class LowVolumeLoudness(
    private val context: Context,
    private val onGains: (LoudnessCompensation.Gains) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var enabled = false
    private var intensity = 1.0
    private var registered = false
    private var last: LoudnessCompensation.Gains? = null

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val stream = intent?.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) ?: -1
            if (stream == AudioManager.STREAM_MUSIC) recompute()
        }
    }

    /** The switch (already gated by the caller) and its intensity, 0..1. */
    fun update(on: Boolean, intensity: Float) {
        enabled = on
        this.intensity = intensity.toDouble()
        if (on && !registered) {
            registered = runCatching {
                ContextCompat.registerReceiver(
                    context,
                    volumeReceiver,
                    IntentFilter(VOLUME_CHANGED_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.isSuccess
        } else if (!on && registered) {
            runCatching { context.unregisterReceiver(volumeReceiver) }
            registered = false
        }
        recompute()
    }

    /** The output changed (headphones in or out, Bluetooth connected): each output has its own volume. */
    fun onOutputChanged() {
        if (enabled) recompute()
    }

    fun release() {
        if (registered) runCatching { context.unregisterReceiver(volumeReceiver) }
        registered = false
    }

    private fun recompute() {
        val gains = if (enabled) LoudnessCompensation.gains(currentVolumeDb(), intensity) else LoudnessCompensation.ZERO
        if (gains == last) return
        last = gains
        onGains(gains)
    }

    private fun currentVolumeDb(): Double {
        val am = audioManager ?: return 0.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0.0
        val index = runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val max = runCatching { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        val routed = volumeDb(am, index, routedOutputType(am))
        val speaker = volumeDb(am, index, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        return LoudnessCompensation.volumeDbFor(index, max, routed, speaker)
    }

    private fun volumeDb(am: AudioManager, index: Int, deviceType: Int): Double? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            null
        } else {
            runCatching { am.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, deviceType).toDouble() }.getOrNull()
        }

    /** The media output: what Android routes music to (13+), else the first connected output by the usual priority. */
    private fun routedOutputType(am: AudioManager): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            runCatching { am.getAudioDevicesForAttributes(attrs).firstOrNull()?.type }.getOrNull()?.let { return it }
        }
        val outputs = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type } }.getOrDefault(emptyList())
        return PRIORITY.firstOrNull { it in outputs } ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    }

    companion object {
        /** Not in the public SDK; the same action this app's volume sheet already listens to (AudioDeviceBottomSheet). */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

        private val PRIORITY = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )
    }
}
