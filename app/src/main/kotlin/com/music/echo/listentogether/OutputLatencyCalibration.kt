package iad1tya.echo.music.listentogether

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Row 649 — what the microphone measured for each OUTPUT (`EqDeviceProfileStore.currentOutputKey`), shared by the Listen Together
 * bridge (which uses it instead of the guessed Bluetooth delay) and the settings row «Calibrar con el micrófono».
 *
 * One measurement can be wrong (another device chirping at the same moment, a loud passage), so the value in use is the MEDIAN of the
 * last [HISTORY] ones; the first measurement of an output is adopted at once so the room is not left on the guess.
 */
object OutputLatencyCalibration {
    private const val HISTORY = 3

    private val _measured = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Output key -> measured milliseconds between the player's position and what is heard. */
    val measured: StateFlow<Map<String, Int>> = _measured.asStateFlow()

    private val history = HashMap<String, ArrayDeque<Int>>()
    private val mutex = Mutex()

    fun hasMicrophone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun latencyFor(outputKey: String): Int? = _measured.value[outputKey]

    /** Pure. The value to use given the recent measurements: their median. */
    fun median(values: List<Int>): Int = values.sorted()[values.size / 2]

    /** Pure. Records a measurement and returns the value now in use for the output. */
    internal fun record(outputKey: String, latencyMs: Int): Int {
        val h = history.getOrPut(outputKey) { ArrayDeque() }
        h.addLast(latencyMs)
        while (h.size > HISTORY) h.removeFirst()
        val now = median(h.toList())
        _measured.value = _measured.value + (outputKey to now)
        return now
    }

    /** Forgets what was measured for an output (a speaker that was reconnected may have another delay). */
    fun forget(outputKey: String) {
        history.remove(outputKey)
        _measured.value = _measured.value - outputKey
    }

    /** Measures the current output once. Null when there is no microphone permission or nothing stood out. */
    suspend fun calibrate(context: Context, outputKey: String): Int? = mutex.withLock {
        if (!hasMicrophone(context)) return null
        val r = OutputLatencyProbe.measure(context)
        if (r == null) {
            Timber.tag("OutputLatency").i("LT_CAL out=%s failed", kindOf(outputKey))
            return null
        }
        val inUse = record(outputKey, r.latencyMs)
        // Numbers only and the KIND of output: a speaker's name can be someone's name.
        Timber.tag("OutputLatency").i("LT_CAL out=%s lat=%d q=%.2f inUse=%d", kindOf(outputKey), r.latencyMs, r.quality, inUse)
        inUse
    }

    private fun kindOf(outputKey: String): String = when {
        outputKey.startsWith("bt:") -> "bt"
        outputKey == "wired" -> "wired"
        outputKey == "usb" -> "usb"
        else -> "speaker"
    }
}
