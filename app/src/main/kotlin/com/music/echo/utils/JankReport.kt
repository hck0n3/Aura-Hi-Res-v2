package iad1tya.echo.music.utils

import android.view.Window
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import iad1tya.echo.music.BuildConfig
import timber.log.Timber
import java.util.Locale

/**
 * Registry row 899 (plan phase 7, «medir con JankStats»): frames and janky frames per screen, as COUNTS only.
 *
 * DEBUG builds only. There is no diagnostics switch in the app to hang a release opt-in on, so a release build never
 * starts JankStats at all (the library ships, R8 is free to drop it). The line goes to Timber at DEBUG level, which
 * `AppLogger` does not persist (INFO+ only): nothing reaches the shared `app.log`.
 *
 * What a line holds (AGENTS rule 4): a screen name taken from the navigation ROUTE TEMPLATE (`album/{albumId}` →
 * `album`), never its arguments, and three numbers. No title, artist, id, path or query.
 *
 * Battery (rule 7): JankStats is a FrameMetrics listener — it is called only for frames that are actually drawn, so an
 * idle screen costs nothing, and tracking is off while the activity is paused. A report is written on a screen change
 * and on pause, never on a timer.
 */
object JankReport {
    private val tally = JankTally()

    @Volatile
    private var screen = JankTally.OTHER

    private var stats: JankStats? = null

    /** Call once from the activity's onCreate, after setContent. No-op in release. */
    fun attach(window: Window) {
        if (!BuildConfig.DEBUG || stats != null) return
        stats = runCatching {
            JankStats.createAndTrack(
                window,
                object : JankStats.OnFrameListener {
                    override fun onFrame(volatileFrameData: FrameData) {
                        tally.record(screen, volatileFrameData.isJank)
                    }
                },
            )
        }.getOrNull()
    }

    fun setTracking(on: Boolean) {
        stats?.isTrackingEnabled = on
    }

    /** The navigation route changed: report the screen that was left. */
    fun onRoute(route: String?) {
        if (stats == null) return
        val next = JankTally.screenOf(route)
        val left = screen
        if (left == next) return
        screen = next
        report(left)
    }

    /** The activity is pausing: report what the current screen has so far. */
    fun flush() {
        if (stats == null) return
        report(screen)
    }

    private fun report(name: String) {
        tally.drain(name)?.let { Timber.tag("Jank").d(it) }
    }
}

/** Pure, for tests: per-screen frame / jank counters. Thread-safe (FrameMetrics may call from its own thread). */
class JankTally {
    private val counts = HashMap<String, IntArray>()

    @Synchronized
    fun record(screen: String, janky: Boolean) {
        val c = counts.getOrPut(screen) { IntArray(2) }
        c[0]++
        if (janky) c[1]++
    }

    /** The report line for [screen] (null when it drew nothing), and its counters start again. */
    @Synchronized
    fun drain(screen: String): String? {
        val c = counts.remove(screen) ?: return null
        return line(screen, c[0], c[1])
    }

    companion object {
        const val OTHER = "other"
        private const val MAX_NAME = 48

        fun line(screen: String, frames: Int, janky: Int): String {
            val pct = if (frames == 0) 0.0 else janky * 100.0 / frames
            return String.format(Locale.US, "screen=%s frames=%d janky=%d (%.1f%%)", screen, frames, janky, pct)
        }

        /**
         * A route TEMPLATE to a screen name: the query and every `{argument}` segment dropped, only `[A-Za-z0-9_/-]`
         * kept, at most [MAX_NAME] characters. Anything else is [OTHER].
         */
        fun screenOf(route: String?): String {
            val path = route?.substringBefore('?') ?: return OTHER
            val name = path.split('/').filter { it.isNotEmpty() && '{' !in it && '}' !in it }.joinToString("/")
            if (name.isEmpty() || name.length > MAX_NAME) return OTHER
            return if (name.all { it.isLetterOrDigit() || it == '_' || it == '/' || it == '-' }) name else OTHER
        }
    }
}
