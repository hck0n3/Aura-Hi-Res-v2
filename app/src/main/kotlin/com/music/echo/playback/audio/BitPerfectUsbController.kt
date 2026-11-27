package iad1tya.echo.music.playback.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Row 902 — asks Android 14+ for a bit-perfect USB output while «Modo Fidelidad» is on and music plays.
 *
 * Event-driven only (AGENTS.md rule 7): it runs when the mode is switched, when playback starts or stops,
 * when the sink configures a new track (once per track, never per buffer) and when an audio device is
 * plugged or unplugged. Nothing polls.
 *
 * The preference is set only while all of these hold: the mode is on, playback is wanted (play pressed,
 * not ended), the current track is PCM, the media route is a USB DAC/headset, and that device offers a
 * bit-perfect mixer with EXACTLY the track's encoding, rate and channels ([BitPerfectMixer.pick]). Any
 * other state clears it, so other apps and system sounds get the normal mixer back the moment music stops.
 *
 * Every outcome is logged once per change, as an enum plus numbers (encoding, rate, channels): never the
 * device's name or anything about the track (AGENTS.md rule 4).
 *
 * Thread-safe: the sink calls [onStreamFormat] on the playback thread, everything else comes from Main.
 */
class BitPerfectUsbController(private val audioManager: AudioManager) {

    private val lock = Any()
    private var fidelityOn = false
    private var playbackActive = false
    private var stream: BitPerfectMixer.StreamFormat? = null
    private var appliedDevice: AudioDeviceInfo? = null
    private var appliedKey: String? = null
    private var lastLog: String? = null

    /** Same usage/content type as the players' own audio attributes (MusicService.createExoPlayer). */
    private val mediaAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    fun setFidelityMode(on: Boolean) = synchronized(lock) {
        if (fidelityOn == on) return@synchronized
        fidelityOn = on
        reevaluate()
    }

    fun setPlaybackActive(active: Boolean) = synchronized(lock) {
        if (playbackActive == active) return@synchronized
        playbackActive = active
        reevaluate()
    }

    /** From the sink's configure: the AudioTrack format of the new track, or null for encoded output. */
    fun onStreamFormat(format: BitPerfectMixer.StreamFormat?) = synchronized(lock) {
        if (stream == format) return@synchronized
        stream = format
        if (fidelityOn) reevaluate()
    }

    fun onAudioDevicesChanged() = synchronized(lock) {
        if (fidelityOn || appliedDevice != null) reevaluate()
    }

    /** Service teardown: never leave the preference behind. */
    fun release() = synchronized(lock) {
        fidelityOn = false
        playbackActive = false
        clearApplied()
    }

    private fun reevaluate() {
        try {
            if (!fidelityOn || !playbackActive) {
                clearApplied()
                return
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                log(BitPerfectMixer.Outcome.API_TOO_OLD, null)
                return
            }
            applyApi34()
        } catch (e: Exception) {
            // Class name only: a message could carry a device description.
            clearApplied()
            log(BitPerfectMixer.Outcome.ERROR, stream, e.javaClass.simpleName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyApi34() {
        val format = stream
        if (format == null) {
            clearApplied()
            log(BitPerfectMixer.Outcome.NO_PCM_STREAM, null)
            return
        }
        val device = audioManager.getAudioDevicesForAttributes(mediaAttributes)
            .firstOrNull { BitPerfectMixer.isUsbOutput(it.type) }
        if (device == null) {
            clearApplied()
            log(BitPerfectMixer.Outcome.NO_USB_OUTPUT, format)
            return
        }
        val supported: List<AudioMixerAttributes> = audioManager.getSupportedMixerAttributes(device)
        val options = supported.map {
            BitPerfectMixer.MixerOption(
                encoding = it.format.encoding,
                sampleRate = it.format.sampleRate,
                channelCount = it.format.channelCount,
                bitPerfect = it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT,
            )
        }
        when (val pick = BitPerfectMixer.pick(format, options)) {
            is BitPerfectMixer.Pick.NoMatch -> {
                clearApplied()
                log(pick.reason, format)
            }
            is BitPerfectMixer.Pick.Match -> {
                val key = "${device.id}:${format.encoding}:${format.sampleRate}:${format.channelCount}"
                if (key == appliedKey) return
                if (audioManager.setPreferredMixerAttributes(mediaAttributes, device, supported[pick.index])) {
                    // A previous preference on ANOTHER device is dropped; on the same device the new one replaces it.
                    val previous = appliedDevice
                    if (previous != null && previous.id != device.id) {
                        runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, previous) }
                    }
                    appliedDevice = device
                    appliedKey = key
                    log(BitPerfectMixer.Outcome.APPLIED, format)
                } else {
                    clearApplied()
                    log(BitPerfectMixer.Outcome.REFUSED, format)
                }
            }
        }
    }

    private fun clearApplied() {
        val device = appliedDevice ?: return
        appliedDevice = null
        appliedKey = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The device may already be gone (unplugged): the platform drops its preference with it.
            runCatching { audioManager.clearPreferredMixerAttributes(mediaAttributes, device) }
        }
        log(BitPerfectMixer.Outcome.CLEARED, null)
    }

    private fun log(outcome: BitPerfectMixer.Outcome, format: BitPerfectMixer.StreamFormat?, detail: String = "") {
        val line = "BIT_PERFECT outcome=$outcome enc=${format?.encoding ?: -1} rate=${format?.sampleRate ?: -1} " +
            "ch=${format?.channelCount ?: -1}" + if (detail.isEmpty()) "" else " error=$detail"
        if (line == lastLog) return
        lastLog = line
        Timber.tag(TAG).i("%s", line)
    }

    private companion object {
        const val TAG = "BitPerfectUsb"
    }
}
