package iad1tya.echo.music.eq.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.getSystemService
import iad1tya.echo.music.eq.audio.OutputCategory
import iad1tya.echo.music.eq.audio.OutputClassifier

/**
 * PowerAmp-style "EQ profile per output device": remembers which saved EQ profile the user assigned to
 * each audio output (the phone speaker, each Bluetooth device, wired/USB headphones...) so it can be
 * applied automatically when that output becomes active.
 */
object EqDeviceProfileStore {

    private const val PREFS = "eq_device_profiles"
    /** Master switch for auto-applying assigned profiles on output change. Off = leave EQ alone. */
    private const val KEY_AUTO_APPLY = "__auto_apply_enabled__"

    /** Stable key for the phone's own speaker (so it survives across sessions / devices). */
    const val PHONE_KEY = "__phone__"

    /** Row 919: [category] tells a Bluetooth speaker / car from Bluetooth headphones (same transport, same [isBluetooth]). */
    data class Output(
        val key: String,
        val name: String,
        val isBluetooth: Boolean,
        val category: OutputCategory = OutputCategory.PHONE_SPEAKER,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * When false, [iad1tya.echo.music.playback.MusicService] must not touch EQ on device connect /
     * disconnect. Clearing this flag or a per-device assignment must NEVER disable the master EQ.
     */
    fun isAutoApplyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_APPLY, true)

    fun setAutoApplyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_APPLY, enabled).apply()
    }

    fun assignedProfileId(context: Context, deviceKey: String): String? =
        prefs(context).getString(deviceKey, null)

    /**
     * Bind or clear a profile for [deviceKey]. Passing null only removes the mapping — it does not
     * flip `echo_eq_prefs["enabled"]` or call [iad1tya.echo.music.eq.EqualizerService.disable].
     */
    fun assign(context: Context, deviceKey: String, profileId: String?) {
        prefs(context).edit().apply {
            if (profileId == null) remove(deviceKey) else putString(deviceKey, profileId)
        }.apply()
    }

    // Registry row 915: the automatic AutoEq correction of an output. Separate keys from the saved-profile assignment
    // above (a device can have both: the saved profile is the user's taste, the correction is the headphone's).
    private const val AUTOEQ_PREFIX = "autoeq:"
    private const val OFFERED_PREFIX = "autoeq_offered:"
    private const val KEY_AUTOEQ_MAX_BOOST = "__autoeq_max_boost_db__"

    /** The catalogue line ([iad1tya.echo.music.eq.autoeq.AutoEqOfflineEntry.toLine]) assigned to [deviceKey], or null. */
    fun assignedAutoEqLine(context: Context, deviceKey: String): String? =
        prefs(context).getString(AUTOEQ_PREFIX + deviceKey, null)

    /** Bind (a line) or clear (null) the automatic correction of [deviceKey]. Never touches the EQ itself. */
    fun assignAutoEq(context: Context, deviceKey: String, line: String?) {
        prefs(context).edit().apply {
            if (line == null) remove(AUTOEQ_PREFIX + deviceKey) else putString(AUTOEQ_PREFIX + deviceKey, line)
        }.apply()
    }

    /** True once «Corregir el sonido para …» was shown for [deviceKey] (accepted or dismissed): it is offered once. */
    fun wasAutoEqOffered(context: Context, deviceKey: String): Boolean =
        prefs(context).getBoolean(OFFERED_PREFIX + deviceKey, false)

    fun markAutoEqOffered(context: Context, deviceKey: String) {
        prefs(context).edit().putBoolean(OFFERED_PREFIX + deviceKey, true).apply()
    }

    /** Highest boost the automatic correction may apply (dB). */
    fun autoEqMaxBoostDb(context: Context): Float =
        prefs(context).getFloat(KEY_AUTOEQ_MAX_BOOST, iad1tya.echo.music.eq.autoeq.AutoEqSafety.DEFAULT_MAX_BOOST_DB.toFloat())

    fun setAutoEqMaxBoostDb(context: Context, db: Float) {
        prefs(context).edit().putFloat(KEY_AUTOEQ_MAX_BOOST, db).apply()
    }

    /** All current output devices the user can assign a profile to (phone + connected BT/wired/USB). */
    fun connectedOutputs(context: Context): List<Output> {
        val am = context.getSystemService<AudioManager>() ?: return listOf(Output(PHONE_KEY, "Teléfono", false))
        val result = linkedMapOf<String, Output>()
        result[PHONE_KEY] = Output(PHONE_KEY, "Teléfono (altavoz)", false)
        runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { d ->
                val out = d.toOutput(context) ?: return@forEach
                result[out.key] = out
            }
        }
        return result.values.toList()
    }

    /** Key of the output audio is currently routed to (best-effort priority: BT > wired/USB > speaker). */
    fun currentOutputKey(context: Context): String = currentOutput(context).key

    /** The output audio is currently routed to, with its name (same priority as [currentOutputKey]). */
    fun currentOutput(context: Context): Output {
        val phone = Output(PHONE_KEY, "Teléfono (altavoz)", false)
        val am = context.getSystemService<AudioManager>() ?: return phone
        val devices = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
        val priority = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )
        for (type in priority) {
            devices.firstOrNull { it.type == type }?.toOutput(context)?.let { return it }
        }
        return phone
    }

    private fun AudioDeviceInfo.toOutput(context: Context): Output? {
        val name = productName?.toString()?.takeIf { it.isNotBlank() }
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Output(PHONE_KEY, "Teléfono (altavoz)", false)
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
                Output("bt:${name ?: "device"}", name ?: "Bluetooth", true, OutputClassifier.categoryOf(context, this) ?: OutputClassifier.BLUETOOTH_DEFAULT)
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                Output("wired", name ?: "Auriculares con cable", false, OutputCategory.HEADPHONES_WIRED)
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE ->
                Output("usb", name ?: "USB", false, OutputClassifier.categoryOf(context, this) ?: OutputCategory.USB_DAC)
            else -> null
        }
    }
}
