package iad1tya.echo.music.eq.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Registry row 919: what kind of thing the music is coming out of. A Bluetooth speaker or soundbar is not a pair of
 * headphones: headphone-only effects (HRTF, crossfeed) must never run on it, and the phone-speaker effects (sub-bass
 * protection, psychoacoustic bass) must never run on anything but the phone's own speaker.
 */
enum class OutputCategory {
    PHONE_SPEAKER,
    HEADPHONES_WIRED,
    HEADPHONES_BT,
    /** Bluetooth / LE Audio speakers, soundbars, hi-fi systems and TVs. */
    SPEAKER_BT,
    CAR,
    USB_DAC,
    /** HDMI / ARC / eARC, analogue or digital line out and docks: a room system. */
    HDMI,
}

/**
 * Pure classification (no Android runtime: only compile-time constants), plus [detect], the one place that reads the
 * live outputs. Every caller is event-driven (audio-device callback, preference change): nothing here polls.
 */
object OutputClassifier {

    /** A Bluetooth output whose class says nothing either way (or cannot be read) stays headphones, as before row 919. */
    val BLUETOOTH_DEFAULT = OutputCategory.HEADPHONES_BT

    /**
     * [BluetoothClass.getDeviceClass] (major + minor, `BluetoothClass.Device.AUDIO_VIDEO_*`, values read with javap
     * from android-37's android.jar) → category, or null when the class is ambiguous (hands-free, portable audio,
     * uncategorised, not an audio/video device) and the name has to decide.
     */
    fun fromBluetoothClass(deviceClass: Int?): OutputCategory? = when (deviceClass) {
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        -> OutputCategory.HEADPHONES_BT
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER,
        BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX,
        BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR,
        -> OutputCategory.SPEAKER_BT
        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> OutputCategory.CAR
        else -> null
    }

    private val HEADPHONE_TOKENS = setOf(
        "headphone", "headphones", "headset", "earphone", "earphones", "earbud", "earbuds", "buds", "pods",
        "airpods", "ear", "tws", "wh", "wf", "qc", "momentum",
    )
    private val CAR_TOKENS = setOf(
        "car", "carkit", "auto", "sync", "uconnect", "mylink", "mbux", "bmw", "audi", "mercedes", "toyota", "honda",
        "mazda", "kia", "hyundai", "nissan", "ford", "chevrolet", "vw", "volkswagen", "peugeot", "renault", "skoda",
        "tesla", "volvo",
    )
    private val SPEAKER_TOKENS = setOf(
        "speaker", "speakers", "soundbar", "boombox", "partybox", "flip", "charge", "clip", "xtreme", "pulse",
        "megaboom", "wonderboom", "boom", "srs", "homepod", "tv",
    )
    private val SPEAKER_PAIRS = setOf(
        "sound bar", "soundlink flex", "soundlink micro", "soundlink mini", "soundlink revolve", "soundlink color",
        "echo dot", "echo studio",
    )

    /**
     * Fallback when the Bluetooth class is not available (no BLUETOOTH_CONNECT) or ambiguous: whole-word hints in the
     * device name. Headphone words win over car and speaker words, so «Bose SoundLink around-ear» stays headphones.
     * Null when nothing matches. The name is only read here, never logged (AGENTS rule 4).
     */
    fun fromName(name: String?): OutputCategory? {
        if (name.isNullOrBlank()) return null
        val tokens = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val pairs = tokens.zipWithNext { a, b -> "$a $b" }.toSet()
        return when {
            tokens.any { it in HEADPHONE_TOKENS } -> OutputCategory.HEADPHONES_BT
            tokens.any { it in CAR_TOKENS } -> OutputCategory.CAR
            tokens.any { it in SPEAKER_TOKENS } || pairs.any { it in SPEAKER_PAIRS } -> OutputCategory.SPEAKER_BT
            else -> null
        }
    }

    /** One output device → its category, or null for a type that is not a music output (mic, telephony…). */
    fun classify(type: Int, bluetoothClass: Int?, name: String?): OutputCategory? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        -> OutputCategory.PHONE_SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        -> OutputCategory.HEADPHONES_WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE -> OutputCategory.USB_DAC
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_DOCK,
        AudioDeviceInfo.TYPE_DOCK_ANALOG,
        -> OutputCategory.HDMI
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        -> OutputCategory.SPEAKER_BT
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEARING_AID,
        -> OutputCategory.HEADPHONES_BT
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        -> fromBluetoothClass(bluetoothClass) ?: fromName(name) ?: BLUETOOTH_DEFAULT
        else -> null
    }

    /** An output device as [route] needs it: its type and its category. */
    data class Candidate(val type: Int, val category: OutputCategory)

    /**
     * Which connected output the music goes to — the same priority the route detection had before row 919
     * (Bluetooth / hearing aids > wired/USB > HDMI/line/dock > LE Audio speaker or broadcast > the phone's speaker),
     * now carrying the category. Every type [classify] knows is in a group, and any other candidate that is not the
     * phone's speaker still wins over it: the phone-speaker effects never run on an output that is not the phone.
     */
    fun route(candidates: List<Candidate>): OutputCategory {
        val priority = listOf(
            listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_BLE_HEARING_AID,
            ),
            listOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
            ),
            listOf(
                AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_AUX_LINE,
                AudioDeviceInfo.TYPE_DOCK, AudioDeviceInfo.TYPE_HDMI_EARC,
                AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_DOCK_ANALOG,
            ),
            listOf(AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST),
        )
        for (group in priority) {
            for (type in group) candidates.firstOrNull { it.type == type }?.let { return it.category }
        }
        return candidates.firstOrNull { it.category != OutputCategory.PHONE_SPEAKER }?.category
            ?: OutputCategory.PHONE_SPEAKER
    }

    /** The spatial stage per category: HRTF / crossfeed only on headphones, M/S width on speakers, off on room systems. */
    fun spatialKind(category: OutputCategory): SpatialOutputKind = when (category) {
        OutputCategory.HEADPHONES_WIRED,
        OutputCategory.HEADPHONES_BT,
        OutputCategory.USB_DAC,
        -> SpatialOutputKind.HEADPHONE
        OutputCategory.PHONE_SPEAKER,
        OutputCategory.SPEAKER_BT,
        -> SpatialOutputKind.SPEAKER
        OutputCategory.CAR,
        OutputCategory.HDMI,
        -> SpatialOutputKind.BYPASS
    }

    /** Whether the AutoEq headphone correction may be offered for this output (it is a headphone measurement). */
    fun isHeadphones(category: OutputCategory): Boolean =
        category == OutputCategory.HEADPHONES_BT || category == OutputCategory.HEADPHONES_WIRED

    /** The category of one live device. Reads the Bluetooth class only with BLUETOOTH_CONNECT granted (API 31+). */
    fun categoryOf(context: Context, device: AudioDeviceInfo): OutputCategory? {
        val type = device.type
        val bluetooth = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        val deviceClass = if (bluetooth) bluetoothClassOf(context, device) else null
        val name = if (bluetooth) device.productName?.toString() else null
        return classify(type, deviceClass, name)
    }

    /** The category of the output the music goes to now. Never throws: no audio service = the phone's speaker. */
    fun detect(context: Context): OutputCategory {
        val am = context.getSystemService<AudioManager>() ?: return OutputCategory.PHONE_SPEAKER
        val devices = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        return route(devices.mapNotNull { d -> categoryOf(context, d)?.let { Candidate(d.type, it) } })
    }

    // BLUETOOTH_CONNECT is checked just below (API 31+); before 31 the class needs only BLUETOOTH, granted at install.
    @SuppressLint("MissingPermission")
    private fun bluetoothClassOf(context: Context, device: AudioDeviceInfo): Int? {
        if (Build.VERSION.SDK_INT < 28) return null
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching {
            val address = device.address.takeIf { it.isNotBlank() } ?: return null
            val adapter = context.getSystemService<BluetoothManager>()?.adapter ?: return null
            adapter.getRemoteDevice(address).bluetoothClass?.deviceClass
        }.getOrNull()
    }
}
