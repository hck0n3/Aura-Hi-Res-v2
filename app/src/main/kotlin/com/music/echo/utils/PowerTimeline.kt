package iad1tya.echo.music.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import timber.log.Timber
import java.util.Locale

/**
 * A battery-relevant timeline in the log the owner shares (battery audit 2026-10-20).
 *
 * WHY: Samsung's battery screen says "33 %" and nothing else. Whether that is the DSP chain playing for hours with
 * the screen off, the display while the app is open, or a burst of background work can only be told apart with
 * numbers taken at the moments the phone changes state. One INFO line (so it lands in `app.log`, the file Ajustes ▸
 * Registros shares) is written when the screen turns on or off, when playback starts or stops, and every
 * [TICK_MS] while music plays. Each line carries the battery level and the PROCESS CPU time spent since the
 * previous line, so the share of CPU the app used in each phase is a subtraction: `cpu=+18.4s/612s (3.0%)` is
 * "3 % of one core over the last ten minutes, screen off, playing".
 *
 * COST / PRIVACY: no loop and no listener of its own — it piggybacks on events the service already handles and on its
 * existing 5 s playing-tick. A sample is one sticky-broadcast read, one network-capabilities lookup and two
 * PowerManager reads. Nothing about WHAT is playing is recorded: no title, artist, id, URL or account.
 */
object PowerTimeline {
    private const val TAG = "PowerTimeline"

    /** Cadence of the "tick" line while music plays. */
    const val TICK_MS = 15 * 60 * 1000L

    /** Playback flips (a crossfade swap, a focus blip) must not flood the log: non-screen events are rate limited. */
    private const val MIN_EVENT_GAP_MS = 20_000L

    data class Sample(
        val elapsedMs: Long,
        /** How long this PROCESS has been alive (not the device uptime). */
        val processAgeMs: Long,
        val cpuMs: Long,
        val batteryPct: Int,
        val charging: Boolean,
        val screenOn: Boolean,
        val playing: Boolean,
        val network: String,
        val thermalStatus: Int,
        val batterySaver: Boolean,
    )

    @Volatile private var previous: Sample? = null
    @Volatile private var previousEvent: String? = null

    /**
     * Writes one line. Safe from any thread; never throws. [screenOn] / [playing] are passed by the caller because it
     * already knows them at the instant of the event (the PowerManager may not have flipped yet inside a broadcast).
     */
    fun log(context: Context, event: String, playing: Boolean, screenOn: Boolean? = null) {
        runCatching {
            val now = SystemClock.elapsedRealtime()
            val last = previous
            val isScreenEvent = event.startsWith("screen_")
            if (!isScreenEvent && event != "tick" && last != null && now - last.elapsedMs < MIN_EVENT_GAP_MS) return
            val sample = sample(context, now, playing, screenOn)
            val line = formatLine(event, sample, last, previousEvent)
            previous = sample
            previousEvent = event
            Timber.tag(TAG).i("%s", line)
        }
    }

    /** The fields for the shared-log header (absolute, no deltas, nothing about playback). Never throws. */
    fun headerLine(context: Context): String = runCatching {
        val now = sample(context, SystemClock.elapsedRealtime(), playing = false, screenOn = null)
        stateFields(now) + totals(now)
    }.getOrDefault("?")

    private fun sample(context: Context, nowMs: Long, playing: Boolean, screenOn: Boolean?): Sample {
        val app = context.applicationContext
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return Sample(
            elapsedMs = nowMs,
            processAgeMs = (nowMs - android.os.Process.getStartElapsedRealtime()).coerceAtLeast(0L),
            cpuMs = android.os.Process.getElapsedCpuTime(),
            batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            charging = plugged != 0,
            screenOn = screenOn ?: (pm?.isInteractive != false),
            playing = playing,
            network = networkKind(app),
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm?.currentThermalStatus ?: -1 else -1,
            batterySaver = pm?.isPowerSaveMode == true,
        )
    }

    private fun networkKind(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "?"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bt"
            else -> "other"
        }
    }

    /**
     * The whole line, pure so a test can pin the format and the arithmetic. [previous] is the sample of the line
     * before this one ([previousEvent] its name): the CPU share is measured over THAT interval, so it describes the
     * phase that just ended (the interval that began with [previousEvent]).
     */
    internal fun formatLine(event: String, now: Sample, previous: Sample?, previousEvent: String?): String = buildString {
        append("POWER event=").append(event)
        append(" screen=").append(if (now.screenOn) "on" else "off")
        append(" playing=").append(if (now.playing) "yes" else "no")
        append(' ').append(stateFields(now))
        if (previous != null) {
            val wallMs = (now.elapsedMs - previous.elapsedMs).coerceAtLeast(0L)
            val cpuMs = (now.cpuMs - previous.cpuMs).coerceAtLeast(0L)
            append(" | since=").append(previousEvent ?: "?")
            append(" wall=").append(seconds(wallMs)).append("s")
            append(" cpu=+").append(seconds(cpuMs)).append("s")
            append(" (").append(cpuPercent(cpuMs, wallMs)).append("%)")
            if (previous.batteryPct >= 0 && now.batteryPct >= 0) {
                append(" dBatt=").append(now.batteryPct - previous.batteryPct).append("%")
            }
        } else {
            append(" |").append(totals(now))
        }
    }

    private fun stateFields(now: Sample): String =
        "batt=${if (now.batteryPct >= 0) "${now.batteryPct}%" else "?"}" +
            " chg=${if (now.charging) "yes" else "no"}" +
            " net=${now.network}" +
            " thermal=${now.thermalStatus}" +
            " saver=${if (now.batterySaver) "on" else "off"}"

    /** Whole-process totals: the only figure available for the first line of a process (no previous sample to subtract). */
    private fun totals(now: Sample): String =
        " cpuTotal=${seconds(now.cpuMs)}s processAge=${seconds(now.processAgeMs)}s (${cpuPercent(now.cpuMs, now.processAgeMs)}%)"

    /** CPU time as a percentage of ONE core's wall time over the interval (a busy 4-thread process can exceed 100). */
    internal fun cpuPercent(cpuMs: Long, wallMs: Long): String =
        if (wallMs <= 0L) "?" else String.format(Locale.US, "%.1f", cpuMs * 100.0 / wallMs)

    private fun seconds(ms: Long): String = String.format(Locale.US, "%.1f", ms / 1000.0)
}
